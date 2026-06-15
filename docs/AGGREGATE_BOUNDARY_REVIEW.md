# CyberTrail Aggregate Boundary Review
Version: 1.0
Status: Frozen

---

## 1. Candidate A: Track as a Fat Aggregate
**Design**:
`Track` is the sole Aggregate Root, containing `Vec<TrackPoint>` and `Vec<Waypoint>`.
All mutations (adding points, adding waypoints) happen via the `Track`.

**Pros**:
- Simplest conceptual model.
- Transaction consistency is guaranteed by the structure. No orphaned points.

**Cons**:
- **Android 13 Memory Usage**: A 10-hour hike with 5m interval sampling might generate ~7,000 points. If a user has a multi-day expedition with 100,000 points, loading the `Track` aggregate means loading all 100,000 points into memory. This will quickly breach the `<100MB` idle memory target and likely cause OOM (Out Of Memory) crashes.
- **SQLite Query Pressure**: Updating a `Track` (e.g., changing its name) requires serializing/deserializing the entire JSON payload or performing complex ORM multi-table mapping every single time.
- **Sync Pressure**: Changing the track name would require syncing the entire payload containing thousands of points, killing Delta Sync efficiency. 
- **Search Index Complexity**: Indexing a track would require pulling massive amounts of unrelated point data.

---

## 2. Candidate B: All Independent Aggregates
**Design**:
`Track`, `TrackPoint`, `Waypoint`, `SyncMetadata`, and `ConflictRecord` are all independent Aggregate Roots.

**Pros**:
- **Memory Efficiency**: You can load a `Track` without its points. Points can be queried via stream/pagination.
- **Sync Efficiency**: Granular sync. You can sync a `Waypoint` or `Track` metadata independently.

**Cons**:
- **Transaction Consistency**: `TrackPoint` has no business meaning outside of a `Track`. Making it an independent Aggregate Root violates DDD principles, as its lifecycle is strictly bound to the `Track`. Dealing with orphaned `TrackPoints` becomes a major headache.
- **Sync Pressure**: Treating 1,000,000 `TrackPoint`s as individual aggregates means 1,000,000 WebDAV files, 1,000,000 HTTP requests, and massive `SyncMetadata` table bloat.

---

## 3. Candidate C: Recommended Design - Stream/Batch Separated Aggregate

**Design**:
- **`Track` (Aggregate Root)**: Holds scalar metadata (duration, distance, max altitude, name) and structural boundaries (`started_at`, `ended_at`).
- **`TrackPoint` (Aggregate Value / Stream Entity)**: Structurally bound to the `Track`, but NOT held in memory via a `Vec<TrackPoint>` on the `Track` struct. Accessed via a dedicated stream/batch query in the Repository (`track_repository.get_points_stream(track_id)`).
- **`Waypoint` (Aggregate Root)**: Independent. Users can create POIs (Points of Interest) that are associated with a track but have their own lifecycle (can be synced, edited, or deleted without modifying the `Track` itself).
- **`SyncMetadata` & `ConflictRecord`**: Independent structural tracking for WebDAV operations, completely decoupled from the domain business logic.

**Analysis Against Constraints (10,000 Tracks, 1,000,000 TrackPoints, 50,000 Waypoints)**:
- **Android 13 Memory Occupancy**: Excellent. Editing a `Track` only loads ~150 bytes of metadata. Rendering the HUD only needs a sliding window (e.g. last 50 points). Total memory footprint remains comfortably `< 100MB`.
- **SQLite Pressure**: 1,000,000 rows in `track_points` with an index on `(track_id, timestamp)` is extremely fast (`O(log n)`). `SELECT` queries use limits/offsets instead of table scans.
- **Delta Sync Efficiency**: Syncing a `Track` only uploads its metadata. `TrackPoint`s are chunked into separate static, immutable files (e.g., `track_id_points_001.bin`) ensuring WebDAV `PUT` operations are bound to small chunks, avoiding WebDAV timeouts on poor cellular connections in the woods.
- **Search Index**: FTS5 only indexes `Track` names and `Waypoint` notes. `TrackPoint`s are never indexed, keeping SQLite blazing fast.

---

## 4. Final Verification & Recommendation

**Recommendation: Proceed with Candidate C.**

The `Track` aggregate defines the *boundary of consistency*, but it relies on the `TrackRepository` to stream its geometry data (`TrackPoint`s) lazily. 

- `Track` -> Aggregate Root (Lazy Geometry)
- `Waypoint` -> Aggregate Root (Associated or Independent)
- `TrackPoint` -> Streamed Entity (Lifecycle bound to Track, accessed via streaming)

This protects Android memory limits, optimizes battery usage by avoiding huge object allocations, and perfectly lines up with the Delta Sync chunking architecture.
