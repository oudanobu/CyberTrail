# CyberTrail Sync Engine Implementation Review
Version: 1.0
Status: Frozen

---

## 1. Delta Detection

**Mechanism**:
- We rely on a strict monotonic `Revision` counter.
- The `sync_metadata` table in SQLite maps `entity_id` -> `(local_revision, remote__revision)`.
- **Upload Detection**: If an entity's internal `revision` > `sync_metadata.local_revision` (or metadata doesn't exist), it needs uploading.
- **Download Detection**: If the remote `metadata.json` lists a `revision` > `sync_metadata.remote_revision` (or metadata doesn't exist), it needs downloading.

---

## 2. Queue Strategy

**Decision: Persistent SQLite Queue**.
- We **DO NOT** use an in-memory `Vec<PendingOperation>`. If the app is killed by the Android App Standby bucket, memory queues are lost, and offline-first promises are broken.
- The queue is persistent. When `save()` updates an aggregate, the `is_deleted` and `revision` are updated. A background job (e.g., Android WorkManager) queries for entities where `revision > sync_metadata.local_revision` to form an upload queue. This inherently yields a resumable background sync.

---

## 3. Conflict Strategy

**Resolution Path**:
- Since CyberTrail is an "offline-first, single-user" system, true concurrent multi-device editing is rare but technically possible.
- **Track Metadata / Waypoints**: Use Last-Writer-Wins (LWW) based on `updated_at` timestamps, mapping into `LocalWins` or `RemoteWins`.
- **TrackPoints**: Since TrackPoints are strictly **IMMUTABLE** and **APPEND-ONLY**, conflicts for TrackPoints do not exist conceptually. We simply union/append remote chunks and local chunks, ordered by timestamp.

---

## 4. Tombstone Propagation

**Deletion Flow**:
1. **Local Action**: User deletes a Track. `TrackRepository.save()` sets `is_deleted = 1` and bumps `revision`.
2. **Detection**: Sync worker detects the locally modified Track (which requires upload).
3. **Execution**: WebDAV Client uploads the Tombstone metadata to the WebDAV server (updating remote `metadata.json` with `is_deleted: true`).
4. **Completion**: Remote server now reflects the Tombstone. The sync worker marks `sync_metadata.local_revision` as caught up.
5. **Garbage Collection**: The SQLite payload (TrackPoints) and associated Filesystem Attachments are purged asynchronously *only after* sync confirmation.

---

# Phase 8.2B: WebDAV Object Layout Design (Immutable Chunking)

## 1. The Core Sync Limitation

If a track contains 1,000,000 points, syncing a single 30MB `track_123.json` on unstable Edge/3G wilderness networks is impossible. If the connection drops at 99%, the process restarts from 0%, burning battery and data.

## 2. WebDAV Object Layout

Because TrackPoints are immutable, we completely decouple **Track Metadata** from **Track Point Payloads**. 

**Folder Structure (Encrypted at Rest)**:
```text
/cybertrail_sync/
  ├── tracks/
  │    ├── {track_id}/
  │    │    ├── metadata.json       (Track stats, mostly overwrites. Size: <1KB)
  │    │    ├── points_000001.bin   (Immutable chunk: Points 1 to N. Size: ~8KB)
  │    │    ├── points_000002.bin   (Immutable chunk: Points N to M)
  │    │    └── waypoints.json      (Array of waypoints. Size: <10KB)
```

## 3. Upload & Download Mechanism

- **Appending**: When a predefined chunk size (e.g., 500 points) is reached, or a sync flush is triggered, we serialize the points into `points_xxxxx.bin` (using our Fixed Point storage layout, AES-256-GCM encrypted) and `PUT` it to WebDAV.
- **Network Resilience**: If the upload fails, we only retry that specific 8KB binary chunk.
- **Delta Download**: A secondary device simply uses `PROPFIND` on the folder. It sees `points_000003.bin`. It only downloads missing chunks, decrypts them, and appends them to SQLite using `BEGIN IMMEDIATE`.

## 4. Conclusion

By exploiting the **Immutable TrackPoint** architecture designed in Phase 8.1B-2A, we achieve perfect `O(1)` sync bandwidth. Syncing an 8-hour hike consumes exactly the bandwidth of the new points generated since the last successful sync, bypassing exponential retry failures and eliminating points-level conflict resolution.
