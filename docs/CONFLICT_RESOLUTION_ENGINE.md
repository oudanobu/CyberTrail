# CyberTrail Conflict Resolution Engine
Version: 1.0
Status: Frozen

---

## 1. Conflict Taxonomy

In a multi-device offline-first system, concurrent modifications are inevitable. CyberTrail categorizes conflicts into four primary types.

| Type | Scenario | Example |
|---|---|---|
| **Type A (Metadata)** | Simultaneous edits to the same entity fields. | Device A renames Track to "Fuji". Device B renames it to "Mt Fuji". |
| **Type B (Waypoint)** | Simultaneous edits to waypoint details/notes. | Device A updates notes with "Camp". Device B updates with "Water Source". |
| **Type C (Delete vs Modify)** | One device modifies, the other deletes. | Device A renames the Track; Device B deletes the Track. |
| **Type D (Attachment)** | Simultaneous photo/attachment replacement. | Device A replaces `photo.jpg`. Device B replaces `photo.jpg`. |
| **Type E (TrackPoint)** | Points appended or modified concurrently. | **Impossible.** TrackPoints are strictly Immutable and Append-Only. |

---

## 2. Conflict Policy Matrix

CyberTrail V1 strictly avoids interactive/manual merging to maintain a seamless background sync experience.

| Entity | Conflict Strategy | Rationale |
|---|---|---|
| **Track Metadata** | Last-Writer-Wins (LWW) | Simple, deterministic. Avoids complex text-merging algorithms for simple fields. |
| **Waypoint** | Last-Writer-Wins (LWW) | Applies to coordinates, names, and notes. Overwrite occurs based on physical clock timestamp. |
| **Attachment** | Last-Writer-Wins (LWW) | Binary files cannot be merged. The latest upload overwrites the remote file. |
| **Track (Delete vs Modify)** | **Delete Wins** | A tombstoned entity (`is_deleted = 1`) strictly overrides any concurrent metadata/waypoint modifications. This prevents "zombie tracks" from resurrecting when a secondary offline device syncs an old rename event. |
| **TrackPoints** | Append Union | No conflicts exist. Remote and local chunks are concatenated and queried by `ORDER BY timestamp`. |

---

## 3. Revision Model Review

Currently, the domain relies heavily on monotonic `Revision` counters (`local_revision`, `remote_revision`). 

**The Tie-Breaker Problem**:
If Device A and Device B both pull Revision `8` of a Track, and concurrently edit it, both will submit an update to the server as Revision `9`. When syncing, how do we determine the "winner"?

**Decision: Dual-Factor Conflict Resolution (Revision + Timestamp)**
- Comparisons prioritize `Revision` count for internal state progression.
- If a collision occurs (remote metadata revision == local update revision), the system evaluates the `updated_at` (Unix Milliseconds) timestamp.
- The state with the highest `updated_at` value wins.
- *Caveat*: Relying purely on client clocks is risky due to time drift, but since WebDAV lacks advanced vector clocks, `updated_at` Tie-Breaking is the most robust stateless fallback.

---

## 4. Tombstone Lifetime (Garbage Collection)

If a Track is deleted, `is_deleted` becomes `true`. But how long does the WebDAV server and SQLite DB retain this tombstone?
- **Immediate Deletion**: If purged immediately, offline devices connecting a month later will never see the `"DELETE"` event. They might assume the Track is just "missing" and re-upload their cached copy (Resurrection).
- **Infinite Retention**: Clutters the SQLite DB and sync traversal directory forever.

**Decision: 90-Day Tombstone Retention Policy**
- Tombstones are retained as `is_deleted = 1` for **90 days** from their `updated_at` deletion timestamp.
- A local asynchronous SQLite cleanup macro and WebDAV pruning task will continuously sweep and hard-delete entries older than 90 days.
- If a device has been completely offline for > 90 days, it is forced to do a "Hard Reset Sync" where remote state takes absolute precedence, purging unsynced local state.

---

## 5. Merge Policy

**Decision: Strict LWW. No Semantic Merging in V1.**
- We do **not** attempt Operational Transformation (OT) or CRDTs for fields like `Waypoint.notes` or `Track.name`.
- Implementing OT/CRDTs over WebDAV for basic text strings creates catastrophic complexity.
- If Device A writes "Water" and Device B writes "Camp", the result is whichever `updated_at` is later. It will *not* result in "Water Camp".

---

## 6. Recovery Simulation

To validate the Conflict Engine, we simulate four extreme edge cases.

### Case 1: The 7-Day Offline Device
- **Scenario**: Tablet goes off-grid for 7 days. Phone is active and syncing. Both devices edit Track "Alpha".
- **Resolution**: Tablet connects. It compares its local `updated_at` timestamps against the remote. Since the Phone's edits happened 3 days ago, and the Tablet's occurred 5 days ago, the Tablet downloads the Phone's metadata via LWW. The Tablet's TrackPoint chunks from those 7 days are simply Appended without conflict.

### Case 2: Partial Upload Disconnect
- **Scenario**: Phone uploads `metadata.json` and `points_0001.bin`, network drops before `points_0002.bin`.
- **Resolution**: The WebDAV server sits in a valid but incomplete state. When the Phone reconnects, it queries the remote directory, sees `points_0001.bin` exists, and strictly uploads `points_0002.bin`. 

### Case 3: Android Background Kill
- **Scenario**: App Standby kills the app midway through resolving a collision while executing `sqlite3`.
- **Resolution**: SQLite's WAL mode and `BEGIN IMMEDIATE` guarantees no partial entity writes occurred. The sync cycle simply restarts upon the next Android WorkManager trigger.

### Case 4: Simultaneous Waypoint Edit
- **Scenario**: User renames Waypoint X on Phone and Tablet simultaneously without internet. 
- **Resolution**: The sync engine pulls the remote `waypoints.json`. It compares the `updated_at` for Waypoint X. The version created 4 milliseconds later wins, and the remote JSON is overwritten and flushed to the loser device.
