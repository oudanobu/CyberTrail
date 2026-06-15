# CyberTrail Persistence Mapping Review
Version: 1.0
Status: Frozen

---

## 1. Entity → Table Mapping

| Domain Entity | SQLite Table |
|---|---|
| `Track` | `tracks` |
| `Waypoint` | `waypoints` |
| `TrackPoint` | `track_points` |
| `Attachment` | `attachments` |
| `SyncMetadata` | `sync_metadata` |
| `ConflictRecord` | `conflict_records` |

---

## 2. Value Object Mapping

Value Objects are flattened into primitive columns. We avoid JSON serialization for core domain scalars to maintain query capability.

| Value Object | SQLite Representation | Why? |
|---|---|---|
| `TrackId`, `WaypointId` | `TEXT(UUIDv7)` | UUIDv7 strings preserve chronologically sorted characteristics. Standard length (36 chars). |
| `Coordinate` | `latitude REAL`, `longitude REAL` | Spatial extensions are not strictly necessary unless bounding boxes are required. Splitting enables simpler R-Tree integration if needed later. |
| `Altitude` | `REAL` (Nullable for point/waypoint without altitude) | Standard floating point representing meters. |
| `Distance`, `Speed` | `REAL` | Values are stored in SI units (meters, m/s). |
| `Revision` | `INTEGER` | Monotonically increasing revision numbers stored losslessly. |

---

## 3. Tombstone Mapping (Soft Deletes)

To support local-first sync and conflict resolution, row deletion is strictly forbidden. 

- **Field Generation**: Core entities (`tracks`, `waypoints`, `attachments`) contain `is_deleted INTEGER DEFAULT 0` (Boolean 0 or 1).
- **Modification Triggers**: `save()` calls on marked entities will flip `is_deleted = 1` and bump the `revision`.
- **Propagation**: 
  - Deleting a `Track` **does not** automatically SQL `CASCADE` delete its `Waypoints` or `TrackPoints`. 
  - `Waypoints` implicitly fall out of scope when the parent Track is tombstoned, but their sync metadata requires they remain intact until the deletion is synced over WebDAV. 

---

## 4. FTS5 Mapping (Search Integration)

Full-Text Search uses virtual tables and external content configurations to avoid duplicating database size.

- **`search_index` (FTS5 Virtual Table)**
  - Columns: `entity_id` (Unindexed), `entity_type` (Unindexed), `snippet` (Indexed)
- **Indexed Fields**:
  - `Track.name`
  - `Waypoint.name`, `Waypoint.notes`
- **Ignored Fields**:
  - Coordinates, timestamps, altitudes, track points entirely (zero FTS overhead).
- **Triggers**: Content row creation, updates, and tombstones must have paired SQLite Triggers to seamlessly handle insertion and removal into the FTS5 virtual table.

---

## 5. TrackPoint Storage Strategy

Mapping `TrackPoint` correctly is the cornerstone of SQLite performance for CyberTrail.

- **The Problem with Aggregate Inclusion**: A 40-kilometer hike contains ~50,000 points. Loading 50k points into Android RAM just to change the track name triggers OOMs.
- **Relational Mapping**: 
  - `track_points` table: `(track_id TEXT, timestamp INTEGER, lat REAL, lon REAL, altitude REAL, ...)`
  - **Primary Key / Indexing**: Compound index `(track_id, timestamp)` allows insanely fast `O(log n)` range queries required for chart streaming and map rendering.
- **Access Pattern**: Handled via `TrackRepository::stream_track_points(track_id, limit, offset)`. Reading uses `ORDER BY timestamp ASC LIMIT X OFFSET Y`, delivering stable chunks of geometry directly to rendering abstractions without passing through the `Track` root.

---

## 6. Attachment Strategy

Attachments hold photos or large serialized structures distinct from SQLite strings.

- **Metadata Separation**: The SQLite table `attachments` only stores metadata: `(attachment_id, track_id, mime_type, byte_size, revision)`.
- **Payload Delegation**: The actual BLOB payload is absolutely **banned** from SQLite to prevent database fragmentation and `VACUUM` overhead.
- **Payload Storage**: Stored as AES-256-GCM encrypted chunks in the Android App's private filesystem (`/data/user/0/com.cybertrail/files/attachments/<id>.bin`).

---

## 7. Migration Strategy

Given offline-first constraints, migration must be resilient and strictly forward-moving.

- **V1 (Initial Release)**: Creation of core `tracks`, `waypoints`, `track_points`, `sync_metadata`, plus FTS5 triggers.
- **V2, V3 Upgrades**: 
  - Handled programmatically via `PRAGMA user_version`.
  - Adding fields: `ALTER TABLE ... ADD COLUMN ... DEFAULT ...`
  - Changes requiring table rewrites are performed within strict `BEGIN EXCLUSIVE TRANSACTION` bounds to prevent partial DB corruption if the user force-closes the app.
