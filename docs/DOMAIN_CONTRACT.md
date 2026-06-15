# CyberTrail Domain Contract
Version: 1.0
Status: Frozen

---

## 1. Value Objects

Value objects are immutable and enforce validity constraints at instantiation.

*   `TrackId`, `WaypointId`, `TrackPointId`, `EncryptionKeyId`
    *   **Constraint**: Must be a valid UUID v4.
*   `Revision`
    *   **Constraint**: Must be a positive integer (`u64`), monotonically increasing.
*   `Coordinate` (Latitude, Longitude)
    *   **Constraint**: Latitude between -90.0 and +90.0. Longitude between -180.0 and +180.0.
*   `Altitude`
    *   **Constraint**: Between -500.0m and +10,000.0m.
*   `Distance`
    *   **Constraint**: Must be >= 0.0 meters.
*   `Speed`
    *   **Constraint**: Must be >= 0.0 m/s.
*   `Heading`
    *   **Constraint**: Azimuth in degrees, [0.0, 360.0).

---

## 2. Entities

Entities have a distinct identity and mutable state throughout their lifecycle.

### `Track` (Aggregate Root)
*   **Identity**: `TrackId`
*   **Fields**: `name`, `started_at`, `ended_at`, `duration_seconds`, `distance_m`, `ascent_m`, `descent_m`, `avg_speed`, `max_speed`, `max_altitude`, `min_altitude`, `is_deleted` (tombstone).
*   **Invariants**: 
    *   `ended_at` must be >= `started_at`.
    *   Cannot add points or mutate distance if `ended_at` is set (frozen).

### `TrackPoint`
*   **Identity**: `TrackPointId`
*   **Fields**: `track_id`, `coordinate`, `altitude`, `pressure`, `speed`, `heading`, `timestamp`.
*   **Invariants**: Immutable after creation. `timestamp` must be within the parent Track's `started_at` and `ended_at`.

### `Waypoint`
*   **Identity**: `WaypointId`
*   **Fields**: `track_id` (optional), `name`, `coordinate`, `altitude`, `notes`, `is_deleted`, `created_at`, `updated_at`.
*   **Invariants**: Deletion sets `is_deleted = true`, does not destroy record.

### `SyncMetadata`
*   **Identity**: Compound (`entity_id`, `entity_type`)
*   **Fields**: `local_revision`, `remote_revision`, `etag`, `last_sync_at`.
*   **Invariants**: `local_revision` >= `remote_revision`. 

### `ConflictRecord`
*   **Identity**: `ConflictId` (UUID)
*   **Fields**: `entity_id`, `entity_type`, `conflict_data` (Encrypted webdav payload), `created_at`.

---

## 3. Repository Traits

Repositories define the persistence boundaries for Aggregate Roots. No storage or platform specifics leak here.

*   **`TrackRepository`**
    *   `save_track(track: &Track) -> Result<(), DomainError>`
    *   `load_track(id: TrackId) -> Result<Option<Track>, DomainError>`
    *   `append_track_points(points: &[TrackPoint]) -> Result<(), DomainError>`
    *   `soft_delete_track(id: TrackId) -> Result<(), DomainError>`
*   **`WaypointRepository`**
    *   `save_waypoint(wp: &Waypoint) -> Result<(), DomainError>`
    *   `load_waypoint(id: WaypointId) -> Result<Option<Waypoint>, DomainError>`
    *   `soft_delete_waypoint(id: WaypointId) -> Result<(), DomainError>`
*   **`SyncStateRepository`**
    *   `get_sync_metadata(id: Uuid) -> Result<Option<SyncMetadata>, DomainError>`
    *   `update_metadata(meta: &SyncMetadata) -> Result<(), DomainError>`
*   **`SearchRepository`**
    *   `search(query: &str) -> Result<Vec<SearchResult>, DomainError>`

---

## 4. Domain Events

Events emitted when domain state changes. Useful for decoupling Side-Effects (like HUD updates or triggers for Sync).

*   `TrackCreated { track_id: TrackId, timestamp: i64 }`
*   `TrackUpdated { track_id: TrackId, revision: Revision }`
*   `TrackDeleted { track_id: TrackId, revision: Revision }`
*   `TrackPointRecorded { point: TrackPoint }`
*   `WaypointCreated { waypoint_id: WaypointId }`
*   `ConflictDetected { entity_id: Uuid, remote_etag: String }`
*   `SyncCompleted { success_count: u32, fail_count: u32 }`

---

## 5. Domain Errors

Unified error hierarchy representing business logic violations.

*   **`ValidationError`**: Emitted from Value Objects (e.g., `InvalidLatitude`, `InvalidAltitude`).
*   **`IllegalStateError`**: Emitted by Entities (e.g., `TrackAlreadyFinished`, `TrackPointOutOftimestampBounds`).
*   **`ConflictError`**: Emitted when a multi-client collision is un-mergeable.
*   **`EncryptionError`**: Emitted when a key is missing or MAC verification fails.
*   **`RepositoryError`**: Encapsulates data layer access failures.

---

## 6. Target Rust Module Tree

```text
crates/domain/src/
├── lib.rs
├── entities/
│   ├── mod.rs
│   ├── track.rs
│   ├── track_point.rs
│   ├── waypoint.rs
│   ├── sync_metadata.rs
│   └── conflict_record.rs
├── value_objects/
│   ├── mod.rs
│   ├── identifiers.rs   # TrackId, WaypointId
│   ├── coordinate.rs    # Latitude, Longitude
│   ├── kinematics.rs    # Speed, Distance, Heading
│   ├── altitude.rs
│   └── revision.rs
├── repositories/
│   ├── mod.rs
│   ├── track_repo_trait.rs
│   ├── waypoint_repo_trait.rs
│   ├── sync_repo_trait.rs
│   └── search_repo_trait.rs
├── events/
│   ├── mod.rs
│   └── domain_event.rs
└── errors/
    ├── mod.rs
    ├── domain_error.rs
    └── validation_error.rs
```
