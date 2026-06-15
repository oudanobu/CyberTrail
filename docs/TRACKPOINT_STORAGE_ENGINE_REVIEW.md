# CyberTrail TrackPoint Storage Engine Review
Version: 1.0
Status: Frozen

---

## 1. Immutability Policy (The Core Invariant)

**Decision: TrackPoints are STRICTLY IMMUTABLE.**
- **Append-Only**: Once a point is recorded, its latitude, longitude, timestamp, and altitude cannot be edited.
- **Why?**
  - **Performance**: Updates to millions of rows cause index fragmentation and VACUUM churn.
  - **Sync**: Immutability makes WebDAV sync simple; points only ever need to be appended. Conflict resolution over individual points is eliminated.
  - **Caching**: Maps and elevation profiles can aggressively cache chunks of trajectory without cache invalidation logic for historical points.
  - **Rule**: You may delete a track, but you may never update point #15,237.

---

## 2. Table Design & Indexing

**Table Schema**:
```sql
CREATE TABLE track_points (
    track_id TEXT NOT NULL,
    timestamp INTEGER NOT NULL,
    latitude REAL NOT NULL,
    longitude REAL NOT NULL,
    altitude REAL,
    PRIMARY KEY (track_id, timestamp),
    FOREIGN KEY(track_id) REFERENCES tracks(id)
) WITHOUT ROWID, STRICT;
```

**Why `PRIMARY KEY (track_id, timestamp)` without a separate `id`?**
- A separate `id UUID` for millions of points adds 36 bytes (or 16 bytes raw) *per point*, which for 1M points is roughly 16-36MB of pure useless index overhead.
- We never query "Select point with ID XYZ". The access pattern is 100% "Get points for track X ordered by time".
- Using SQLite's `WITHOUT ROWID` optimization paired with a compound Primary Key ensures that all point data is clustered contiguously on disk, ordered exactly how we want to read it.

**Indexes to Avoid**:
- DO NOT create spatial indexes (R-Tree) on lat/lon unless bounding-box map searching becomes a requirement. It doubles the size of the table and write time.
- DO NOT index altitude.

---

## 3. Insert Strategy

**Comparison**:
- **Single INSERT**: Emitting `INSERT` every 1-5 seconds. Causes continuous disk wakeups and SQLite transaction overhead. Decimates battery and SSD lifespan.
- **Batch Transaction INSERT**: Buffering points in RAM (Application Layer) and flushing them every 30-60 seconds via a single `BEGIN IMMEDIATE` statement.

**Decision: Batch Transaction INSERT**.
The background logger pushes `TrackPoint` instances into a bounded async channel. The consumer collects them and writes in chunks of 50-100 points.

---

## 4. Streaming Strategy

**Comparison**:
- **OFFSET/LIMIT**: `SELECT ... LIMIT 1000 OFFSET 50000`. Catastrophic. SQLite still has to scan and discard the first 50,000 rows. The deeper the pagination, the slower the query.
- **Cursor Streaming / Timestamp Window**: `SELECT ... WHERE track_id = X AND timestamp > LAST_SEEN_TIMESTAMP ORDER BY timestamp ASC LIMIT 1000`.

**Decision: Timestamp Window**.
Because the Primary Key is `(track_id, timestamp)`, accessing data strictly via ranges `timestamp > ? AND timestamp <= ?` allows SQLite's B-Tree to seek instantly. Map rendering will use Timestamp bounds or monotonic limits to fetch chunks continuously.

---

## 5. GPX Export Strategy

**Comparison**:
- **Load All at Once**: 50,000 points into a `Vec<TrackPoint>` triggers heavy allocation, copies to String formatting, and causes OOM limits.
- **Cursor Streaming**: Read in chunks, serialize to a write buffer, and stream directly into Android `FileOutputStream` via FFI/Rust bindings.

**Decision: Cursor Streaming**.
The `ExportTrackCommand` will open a read stream from the DB, convert chunks to XML bytes, and flush to disk locally to keep the max memory footprint completely flat regardless of track length.

---

## 6. Memory & Performance Analysis

**Target**: Android 13, ARM64, 4GB RAM, generic/older CPU.

- **10k points (Typical Sunday hike, ~3 hours at 1s rate)**:
  - RAM overhead when reading chunked: < 1MB.
  - DB space: ~ 10000 * 32 bytes = 320 KB.
- **100k points (Multi-day trek)**:
  - RAM overhead: Still < 1MB (thanks to streaming).
  - DB space: ~ 3.2 MB.
- **1M points (Extreme data retention)**:
  - RAM overhead: < 1MB streaming limit.
  - DB space: ~ 32 MB.

**Conclusion**: Disk size is virtually irrelevant to modern Android. The only threat is reading those 32MB into active application objects simultaneously. The proposed streaming design neutralizes the OOM risk entirely.

---

## 7. Final Recommendation

**Freeze TrackPoint Storage Engine V1.**
Proceed to Implementation (Phase 8.1B-2B), sticking strictly to:
1. `WITHOUT ROWID` table with compound PK.
2. Timestamp-based paging, completely banning `OFFSET`.
3. Batch-driven inserts with `BEGIN IMMEDIATE`.
4. Absolute point immutability at the Domain and Database layers.
