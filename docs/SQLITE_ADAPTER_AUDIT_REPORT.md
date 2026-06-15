# CyberTrail Phase 8.1B-1 Audit Report
Version: 1.0
Status: Frozen

---

## 1. Connection Strategy: Why `r2d2`?

**Current Implementation**: `r2d2` with `r2d2_sqlite` allowing max 5 connections.
**Alternative**: `Arc<Mutex<Connection>>` or a Dedicated Writer Channel.

**Analysis**:
SQLite in WAL mode allows *one writer* and *multiple readers* concurrently.
If we use `Arc<Mutex<Connection>>`, we force all reads and writes to be strictly serialized at the application level. This means a background GPS logger (writing `TrackPoints`) would block the UI from reading a `Track` summary or rendering map tiles.

By using `r2d2` with a small pool (e.g., `max_size = 5`) and WAL mode:
- Reader A can borrow connection 1.
- Reader B can borrow connection 2.
- Writer C can borrow connection 3.
- SQLite natively handles the busy state. If Writer C and Writer D attempt to write simultaneously, one will hit the `busy_timeout` (configured to 5000ms), wait, and then execute.

**Is it over-engineered?**
No. In offline-first apps, you often have a background sync engine (WebDAV), a background location service (GPS logger), and a foreground UI. `r2d2` + WAL ensures reads are entirely unblocked by writes, which is critical for smooth Android map panning while recording.

---

## 2. Repository Implementation Review

**Findings**:
- **Separation of Concerns**: The Repositories strictly map DTO/Entities to SQL params. There is zero domain logic inside the repository (`spawn_blocking` closures).
- **Reconstitution**: We correctly introduced `Track::reconstitute` and `Waypoint::reconstitute`. This bypasses domain generation logic (like creating new UUIDs or bumping revision dates unnecessarily) and strictly reconstructs the aggregate from the DB state.
- **Error Mapping**: `rusqlite::Error` is cleanly mapped to `InfrastructureError`, which maps to `ApplicationError`. No internal SQLite errors (like "syntax error") leak into the UI.

**Verdict**: The implementation is clean. Business logic has not leaked.

---

## 3. Transaction Strategy

**Current Implementation**: 
- `save()` uses `ON CONFLICT DO UPDATE` (UPSERT) which is atomic.
- No explicit `BEGIN TRANSACTION` blocks are used for single-entity saves.

**Future Considerations for `TrackPoint`**:
When we implement `AppendTrackPointCommand` (batch writing 50-100 points), we **MUST** wrap the batch in an explicit `BEGIN IMMEDIATE TRANSACTION`. 
- `BEGIN IMMEDIATE` tells SQLite to acquire the write lock immediately, rather than waiting for the first `INSERT`, preventing deadlock scenarios when upgrading read locks to write locks.

---

## 4. WAL Review

**Current Configuration** (in `connection.rs`):
```sql
PRAGMA journal_mode = WAL;
PRAGMA synchronous = NORMAL;
PRAGMA foreign_keys = ON;
PRAGMA busy_timeout = 5000;
```

**Why these values?**
- `journal_mode = WAL`: Allows concurrent readers and a single writer. Huge performance boost for Android.
- `synchronous = NORMAL`: In WAL mode, `NORMAL` is just as safe as `FULL` against application crashes, and mostly safe against OS crashes. It avoids blocking on `fsync` for every commit, vastly improving battery life during background location tracking.
- `foreign_keys = ON`: SQLite disables them by default. Crucial for data integrity (`Waypoint` -> `Track`).
- `busy_timeout = 5000`: Essential for `r2d2`. If a writer is holding the DB lock, another writer will wait up to 5 seconds instead of instantly failing with `SQLITE_BUSY`.

**Verdict**: Technically flawless for a low-power, offline-first mobile environment.

---

## 5. Android Analysis

**Target**: Android 13, ARM64, 4GB RAM, old CPU.

- **Memory**: The `r2d2` pool keeps a maximum of 5 SQLite connections open. This utilizes ~5-10MB of RAM. The `spawn_blocking` threads borrow slices, parse strings, build entities, and are immediately freed.
- **GC / OOM Risk**: Negligible. Because `Track` does not hold `Vec<TrackPoint>`, loading a track is a single row fetch (< 1KB). 
- **Battery**: Grouping writes (to be done in TrackPoint) and using `synchronous=NORMAL` prevents excessive waking of the flash storage controller.

---

## Conclusion
The infrastructure foundation is robust. The usage of `r2d2` is justified by the concurrent read/write requirements of a mapping/logging app. We are ready to proceed to Phase 8.1B-2 (TrackPoint Persistence and Streaming).
