# CyberTrail Delta Sync Protocol
Version: 1.0
Status: Frozen

---

## 1. Core Architecture Principles
- **Local First & Offline First**: Local SQLite is the single source of truth. All reads/writes happen locally. Sync operates strictly in the background as a side-effect.
- **WebDAV Compatible**: Operates over standard HTTP/WebDAV verbs (`PROPFIND`, `GET`, `PUT`, `DELETE`).
- **Delta Sync**: Only modified or new entities are transmitted.

---

## 2. Sync State Machine

The Background Sync Worker operates along the following defined states:

```text
[Idle] ─(Timer/Trigger)─▶ [Scanning] 
                              │
           ┌──────────────────┴──────────────────┐
           ▼                                     ▼
     [Downloading] ◀────────(Diff)────────▶ [Uploading]
           │                                     │
           ├────────────▶[Conflict]◀─────────────┤
           │                 │                   │
           ▼                 ▼                   ▼
       [Failed] ◀────────(Result)────────▶ [Completed]
           │                                     │
           └──────────────(Retry)────────────────┘
```

---

## 3. Revision Model

To ensure minimal data transfer and safe conflict detection, we use a disjoint revision system.

### Variables
- `local_revision` (Integer): Increments locally whenever the user mutates an entity (Updates track name, adds waypoints, or soft-deletes).
- `remote_revision` (Integer): The revision number according to the remote source at the last successful sync.
- `etag` (String): The WebDAV standard `ETag` provided by the remote server.

### Increment Rules
1. **Local Mutation**: `local_revision = local_revision + 1`.
2. **Post-Upload (Success)**: `remote_revision = local_revision`, capture and store new `etag`.
3. **Post-Download (Success)**: `local_revision = remote_revision = ServerRevision`, store new `etag`.

---

## 4. Tombstone Strategy (Deletion Propagation)

**Rule: Never execute SQL `DELETE` on synced entities.**

1. **Local Delete**: User deletes a track. Set `is_deleted = 1` and `local_revision += 1`.
2. **Uploading Tombstone**: Sync Engine identifies `is_deleted == 1` and `local_revision > remote_revision`. It sends a lightweight tombstone structure to WebDAV (or issues an HTTP `DELETE` to the remote path based on V5 design), updates the ETag.
3. **Downloading Tombstone**: Remote signals entity is missing or marked deleted. Sync Engine sets local `is_deleted = 1`. 

---

## 5. WebDAV Mapping

- **Discovery (`PROPFIND`)**: Retrieve a list of remote IDs, ETags, and timestamps.
- **Download (`GET`)**: Fetch the encrypted payload if Remote ETag differs from Local ETag.
- **Upload (`PUT`)**: Push the encrypted layout.
  - Safe Creation: `If-None-Match: *` (Ensures we don't overwrite a newly created file we don't know about).
  - Safe Evolution: `If-Match: "previous-etag"` (Ensures no one else modified the file since our last sync).

---

## 6. Conflict Strategy

If a WebDAV `PUT` encounters a `412 Precondition Failed` (ETag mismatch), a conflict is triggered.

```rust
enum ConflictResolution {
    Manual,       // Pause sync for this entity, alert user.
    Merge,        // Safe generic merge (e.g., union of two Waypoint lists).
    LocalWins,    // Force overwrite remote (`PUT` without `If-Match`).
    RemoteWins,   // Discard local changes, trigger `GET` and update local.
}
```

*V5 Strategy*: For Tracks, default to `LocalWins` if tracking was actively writing records. For Metadata (e.g., Track name), fallback to `Merge` (timestamp-based) or `Manual`.

---

## 7. Sync Data Flow

```text
[ Local Modification ] 
        │
        ▼
[ UPDATE tracks SET name='...', local_revision = local_revision + 1 ]
        │
        ▼
[ Sync Scan Phase ]
  SELECT * FROM sync_metadata WHERE local_revision > remote_revision
        │
        ▼
[ Network Upload Phase ]
  PUT /tracks/{uuid}.json (Header: If-Match "{etag}")
        │
        ├──▶ 201/204: SUCCESS
        │      └─▶ UPDATE sync_metadata SET remote_revision = local_revision, etag = {new_etag}
        │
        └──▶ 412: CONFLICT
               └─▶ Transition state to [Conflict] -> Execute ConflictResolution
```
