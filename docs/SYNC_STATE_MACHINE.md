# CyberTrail Sync State Machine
Version: 1.0
Status: Frozen

---

## 1. Sync Lifecycle

The sync process is a deterministic state machine managed entirely via the local database.

*   `IDLE`: No active sync. Awaiting manual trigger or scheduled WorkManager task.
*   `SCANNING`: Connecting to the WebDAV remote to fetch directory listings (`PROPFIND`).
*   `DIFFING`: Comparing the remote `PROPFIND` tree against the local `sync_metadata` table to determine required uploads and downloads.
*   `UPLOADING`: Pushing local changes (`metadata.json`, `points_xxxxx.bin`, attachments) to the WebDAV server.
*   `DOWNLOADING`: Pulling remote changes into temporary local staging files.
*   `CONFLICT_DETECTION`: Examining staged downloads against current local entities based on `revision` and `updated_at`.
*   `CONFLICT_RESOLUTION`: Applying the LWW or Delete-Wins rules to determine the surviving data.
*   `COMMIT`: Using `BEGIN IMMEDIATE` to atomically merge the resolved staged data into the main domain tables, and updating `sync_metadata`.
*   `ERROR`: Terminal failure state for the current session (e.g., Auth failure, Max retries exceeded).

---

## 2. Persistent State

If the Android OS kills the app in the background, memory structures (`Vec`, `Channels`) are wiped. All critical sync state must be written to SQLite.

**Table: `sync_sessions`**
- `session_id`: UUID
- `state`: String (e.g., "UPLOADING")
- `started_at`: Integer (Timestamp)
- `last_remote_scan`: Integer (Timestamp)
- `pending_uploads`: Integer (Items left)
- `pending_downloads`: Integer (Items left)
- `last_error_code`: String

**Table: `sync_metadata` (Per Entity)**
- `entity_id`: String
- `entity_type`: String (Track, Waypoint, Attachment)
- `local_revision`: Integer
- `remote_revision`: Integer
- `sync_status`: String (Synced, PendingUpload, PendingDownload)

*Why?* When the app wakes up, the Sync Worker selects the last active `sync_sessions` row. If it was interrupted in `UPLOADING`, it queries `sync_metadata` for `sync_status = 'PendingUpload'` and resumes instantly.

---

## 3. Retry Strategy

Network conditions on trails are terrible. We enforce a robust, exponential failover algorithm.

| Failure Type | Handling Strategy | Backoff |
| :--- | :--- | :--- |
| **Network Timeout/DNS/SSL**| Transient. Network dropped. | Exponential: 5s, 15s, 45s, 135s. Max 4 retries. |
| **WebDAV 500 (Server Error)**| Remote infrastructure failure. | Exponential: 10s, 30s, 90s. Max 3 retries. |
| **WebDAV 404 (Not Found)**| Usually means remote directory was deleted. | Immediate transition to `DIFFING` to resync tree state. |
| **ETag Mismatch / 412**| Concurrency conflict detected on upload. | Dump current upload, drop to `SCANNING` to re-fetch truth. |
| **Auth Failure (401/403)**| Password changed or expired. | Abort immediately. Transition session to `ERROR`. |

*If max retries are exceeded, the session writes the error to `sync_sessions`, transitions to `ERROR`, and schedules the next Android generic sync window (e.g., 15 minutes later via WorkManager).*

---

## 4. Cancellation Strategy

Sync can be interrupted via: User Pausing, App Swiping, or Android WorkManager Timeout (10 minutes limit).

- **In-flight IO**: The Rust `reqwest` or `hyper` Future executing the Upload/Download chunk is dropped (`Drop` trait). The TCP socket closes instantly.
- **Staging Cleanup**: Any `.tmp` file currently being downloaded is orphaned safely. The next sync will overwrite it.
- **SQLite Safety**: Because we only write to `tracks`, `track_points`, and `sync_metadata` during the `COMMIT` phase inside a `BEGIN IMMEDIATE` transaction, interrupting the process leaves the local database untouched.

---

## 5. Recovery Strategy

**Rule: Memory is temporary, SQLite is permanent.**

Upon Boot/Restart:
1. The Sync Engine boots and reads `sync_sessions`.
2. Identifies any session not in `IDLE` or `ERROR`.
3. If it aborted during `DOWNLOADING`, it deletes wildcard `*.tmp` files globally.
4. Resumes from the `SCANNING` phase (safest baseline to guarantee freshness rather than relying on a potentially stale saved tree).

---

## 6. Chunk Sync Ordering

Entities map to physical WebDAV files. They must be synced in a sequence that guarantees relational integrity.

**Order of Operations:**
1. **Track Metadata (`metadata.json`)**: Creates the structural parent on the remote. Smallest file, defines the structure.
2. **Waypoints (`waypoints.json`)**: Critical contextual data. Small payload.
3. **TrackPoint Chunks (`points_xxxxx.bin`)**: The bulk payload. Transferred chronologically.
4. **Attachment Metadata`: Structural context for media.
5. **Attachment Payload (`photo.jpg`)**: Largest payload. Synced last so that an interrupted 50MB photo upload doesn\'t block GPS trajectory syncs.

*Why?* If sync fails at step 3, the remote at least has the Track metadata and the Waypoints, ensuring the UI can render the general existence of the route.

---

## 7. Memory Analysis

**Target**: Android 13, 4GB RAM. Peak sync memory footprint target: `< 100MB`.

- **10k Points Sync**:
  - `PROPFIND` XML: ~ 100 KB
  - `points_0001.bin` decrypt buffer: 8 KB
  - Active RAM usage: ~ 5 MB
- **100k Points Sync**:
  - `PROPFIND` XML (Tree scaling): ~ 500 KB
  - `points_xxxxx.bin` streams: 8 KB (Processed one chunk at a time)
  - Active RAM usage: ~ 5.5 MB
- **1M Points Sync**:
  - `PROPFIND` XML (Assuming 2000 chunks of 500 points): ~ 5 MB
  - `points_xxxxx.bin` streams: Chunked sequentially. Max 1 channel buffer (8 KB).
  - Active RAM usage: ~ 11 MB

**Conclusion**: Since WebDAV mapping acts on discrete directories, and TrackPoints are strictly chunked (files are never larger than a few kilobytes), memory consumption during sync is virtually flat (`O(1)` in terms of payload payload, and small linear growth for `PROPFIND` directory xml parsing). We are easily within the 100MB limit.

---

## 8. Final Recommendation

**Freeze: Sync Engine V1 State Machine.**

The lifecycle guarantees fault-tolerance across unreliable wilderness internet, Android\'s aggressive lifecycle management, and massive point clusters. 
The system state relies perfectly on SQLite, fulfilling the offline-first mandate.
Proceed to Phase 8.3A (Crypto Architecture Review).
