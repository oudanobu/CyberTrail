# CyberTrail Application Contract
Version: 1.0
Status: Frozen

---

## 1. Core Principles
- **Separation of Concerns**: The Application layer coordinates the execution of domain logic, orchestrates Repositories, and maps results to DTOs. It contains no business rules and no infrastructure details.
- **CQS (Command Query Separation)**: Strict segregation between `Command` (mutates state, emits events, returns nothing or just an ID) and `Query` (safe, no side effects, read-only).
- **FFI Boundary**: The UseCases define the precise boundary that the Android FFI will consume. The FFI layer maps these DTOs to JNI/Kotlin representations.
- **Transaction Boundary**: The UseCase acts as the boundary for transactions, ensuring domain invariants are fully persisted or rolled back.

---

## 2. Command UseCases

Commands mutate the domain state. They fetch the Aggregate from the repository, call domain methods, and save it back.

### `CreateTrack`
- **Responsibility**: Initializes a new recording session.
- **Input DTO**: `CreateTrackCommand { name: String, started_at: i64 }`
- **Output DTO**: `TrackIdDto { id: Uuid }`
- **Dependencies**: `TrackRepository`
- **Domain Event**: `TrackCreated`
- **Error Chain**: `ValidationError` -> `ApplicationError`
- **Boundary**: Android FFI (User taps "Record")

### `AppendTrackPoint`
- **Responsibility**: Receives a batch of GPS points and appends them to an active track.
- **Input DTO**: `AppendTrackPointCommand { track_id: Uuid, points: Vec<TrackPointDto> }`
- **Output DTO**: `()`
- **Dependencies**: `TrackRepository`
- **Domain Event**: `TrackPointRecorded`
- **Error Chain**: `TrackNotFound` | `InvalidCoordinate` -> `ApplicationError`
- **Boundary**: Android FFI (Background Location Service)

### `RenameTrack`
- **Responsibility**: Updates the track's name.
- **Input DTO**: `RenameTrackCommand { track_id: Uuid, new_name: String, timestamp: i64 }`
- **Output DTO**: `()`
- **Dependencies**: `TrackRepository`
- **Domain Event**: `TrackRenamed`
- **Error Chain**: `TrackNotFound` | `IllegalStateError` -> `ApplicationError`
- **Boundary**: Android FFI

### `DeleteTrack` (Soft Delete)
- **Responsibility**: Marks a track as deleted.
- **Input DTO**: `DeleteTrackCommand { track_id: Uuid, timestamp: i64 }`
- **Output DTO**: `()`
- **Dependencies**: `TrackRepository`
- **Domain Event**: `TrackDeleted`
- **Error Chain**: `TrackNotFound` | `IllegalStateError` -> `ApplicationError`
- **Boundary**: Android FFI

### `RestoreTrack`
- **Responsibility**: Unmarks a deleted track.
- **Input DTO**: `RestoreTrackCommand { track_id: Uuid, timestamp: i64 }`
- **Output DTO**: `()`
- **Dependencies**: `TrackRepository`
- **Domain Event**: `TrackRestored`
- **Error Chain**: `TrackNotFound` | `IllegalStateError` -> `ApplicationError`
- **Boundary**: Android FFI

### `CreateWaypoint`
- **Responsibility**: Marks a POI or camp, optionally bound to an active track.
- **Input DTO**: `CreateWaypointCommand { track_id: Option<Uuid>, name: String, coordinate: CoordinateDto, altitude: Option<f64>, notes: Option<String>, timestamp: i64 }`
- **Output DTO**: `WaypointIdDto { id: Uuid }`
- **Dependencies**: `WaypointRepository`
- **Domain Event**: `WaypointCreated`
- **Error Chain**: `ValidationError` -> `ApplicationError`
- **Boundary**: Android FFI

### `RenameWaypoint`
- **Responsibility**: Updates a waypoint's name.
- **Input DTO**: `RenameWaypointCommand { waypoint_id: Uuid, new_name: String, timestamp: i64 }`
- **Output DTO**: `()`
- **Dependencies**: `WaypointRepository`
- **Domain Event**: `WaypointRenamed`
- **Error Chain**: `WaypointNotFound` | `IllegalStateError` -> `ApplicationError`
- **Boundary**: Android FFI

### `MoveWaypoint`
- **Responsibility**: Relocates a waypoint.
- **Input DTO**: `MoveWaypointCommand { waypoint_id: Uuid, new_coordinate: CoordinateDto, new_altitude: Option<f64>, timestamp: i64 }`
- **Output DTO**: `()`
- **Dependencies**: `WaypointRepository`
- **Domain Event**: `WaypointMoved`
- **Error Chain**: `WaypointNotFound` | `ValidationError` -> `ApplicationError`
- **Boundary**: Android FFI

### `DeleteWaypoint`
- **Responsibility**: Soft deletes a waypoint.
- **Input DTO**: `DeleteWaypointCommand { waypoint_id: Uuid, timestamp: i64 }`
- **Output DTO**: `()`
- **Dependencies**: `WaypointRepository`
- **Domain Event**: `WaypointDeleted`
- **Error Chain**: `WaypointNotFound` | `IllegalStateError` -> `ApplicationError`
- **Boundary**: Android FFI

### `RestoreWaypoint`
- **Responsibility**: Unmarks a deleted waypoint.
- **Input DTO**: `RestoreWaypointCommand { waypoint_id: Uuid, timestamp: i64 }`
- **Output DTO**: `()`
- **Dependencies**: `WaypointRepository`
- **Domain Event**: `WaypointRestored`
- **Error Chain**: `WaypointNotFound` | `IllegalStateError` -> `ApplicationError`
- **Boundary**: Android FFI

### `ResolveConflict`
- **Responsibility**: Resolves a WebDAV sync conflict for an entity.
- **Input DTO**: `ResolveConflictCommand { conflict_id: Uuid, resolution_strategy: ResolutionStrategy }`
- **Output DTO**: `()`
- **Dependencies**: `SyncStateRepository`, `TrackRepository`, `WaypointRepository`
- **Domain Event**: `ConflictResolved`
- **Error Chain**: `ConflictNotFound` | `SyncError` -> `ApplicationError`
- **Boundary**: Sync Engine / Android FFI (User Manual Resolution)

### `ImportTrack` / `ExportTrack`
- **Responsibility**: Transforms Domain entities to/from GPX or GeoJSON.
- **Input DTO**: `Import/ExportCommand` (File paths or byte streams)
- **Output DTO**: `TrackIdDto` or Byte Stream
- **Dependencies**: `TrackRepository`
- **Domain Event**: `TrackCreated` (for import)
- **Error Chain**: `IOError` | `ParseError` -> `ApplicationError`
- **Boundary**: Android FFI

---

## 3. Query UseCases

Queries bypass the Domain Model's strict invariants where possible and read optimized projections or map straight to DTOs.

### `GetTrack`
- **Responsibility**: Fetches basic track metadata.
- **Input DTO**: `GetTrackQuery { track_id: Uuid }`
- **Output DTO**: `TrackDto`
- **Dependencies**: `TrackRepository`

### `ListTracks`
- **Responsibility**: Paginates or lists tracks.
- **Input DTO**: `ListTracksQuery { limit: u32, offset: u32, order_by: TrackOrder }`
- **Output DTO**: `Vec<TrackSummaryDto>`
- **Dependencies**: `TrackRepository`

### `StreamTrackPoints`
- **Responsibility**: Yields geometry data for a track to render on the map or HUD.
- **Input DTO**: `StreamTrackPointsQuery { track_id: Uuid, limit: Option<usize>, offset: Option<usize> }`
- **Output DTO**: `Vec<TrackPointDto>` (or a stream response)
- **Dependencies**: `TrackRepository`

### `GetWaypoint` / `ListWaypoints`
- **Responsibility**: Retrieves Waypoints (standalone or by track).
- **Input DTO**: `GetWaypointQuery { waypoint_id: Uuid }` / `ListWaypointsQuery { track_id: Option<Uuid> }`
- **Output DTO**: `WaypointDto` / `Vec<WaypointDto>`
- **Dependencies**: `WaypointRepository`

### `SearchTracks` / `SearchWaypoints` / `GlobalSearch`
- **Responsibility**: Performs FTS5 queries against domain data.
- **Input DTO**: `SearchQuery { query: String, limit: u32 }`
- **Output DTO**: `Vec<SearchResultDto>`
- **Dependencies**: `SearchRepository`
- **Boundary**: Search API / Android FFI

### `GetSyncStatus`
- **Responsibility**: Inspects local vs remote revisions and conflict states.
- **Input DTO**: `GetSyncStatusQuery {}`
- **Output DTO**: `SyncStatusDto { pending_uploads: u32, conflicts: Vec<ConflictDto> }`
- **Dependencies**: `SyncStateRepository`
- **Boundary**: Sync Engine / Android FFI

---

## 4. Application Module Tree

```text
crates/application/src/
├── lib.rs
├── commands/
│   ├── mod.rs
│   ├── track_commands.rs       # Create, Rename, Delete, AppendPoints
│   ├── waypoint_commands.rs    # Create, Rename, Move, Delete
│   ├── sync_commands.rs        # ResolveConflict
│   └── io_commands.rs          # Import, Export
├── queries/
│   ├── mod.rs
│   ├── track_queries.rs        # GetTrack, ListTracks, StreamPoints
│   ├── waypoint_queries.rs     # GetWaypoint, ListWaypoints
│   ├── search_queries.rs       # GlobalSearch
│   └── sync_queries.rs         # GetSyncStatus
├── dto/
│   ├── mod.rs
│   ├── track_dto.rs
│   ├── waypoint_dto.rs
│   ├── search_dto.rs
│   └── sync_dto.rs
├── errors/
│   ├── mod.rs
│   └── application_error.rs    # DomainError -> ApplicationError mapping
└── ports/
    ├── mod.rs
    └── repository_interfaces.rs # Optional: Re-exports from Domain, or Application-specific projections
```
