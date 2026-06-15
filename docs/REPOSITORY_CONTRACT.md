# CyberTrail Repository Contract
Version: 1.0
Status: Frozen

---

## 1. Core Principles
- **Aggregate Root Orientation**: Repositories operate on aggregate boundaries (`Track`, `Waypoint`, `Attachment`, etc.), not SQL tables.
- **No CRUD**: Repositories actively map intent (`save`, `stream_track_points`) instead of primitive row mutations (`create`, `update`, `delete`).
- **Tombstones**: "Deletions" are technically `save()` operations where the Domain Entity's `is_deleted` flag is set.
- **Asynchronous Execution**: All repositories rely on `async_trait` for concurrent execution across potential network, file IO, or SQLite boundaries.
- **Separation of Geometry**: `TrackPoint` is streamed actively on bounds to preserve Android memory constraints, instead of residing inside the `Track` aggregate in memory.

---

## 2. Rust Trait Definitions

### TrackRepository
Provides lifecycle and stream bounds for the core tracking feature set.
```rust
#[async_trait]
pub trait TrackRepository: Send + Sync {
    /// Save a Track aggregate taking responsibility for metadata boundary.
    async fn save(&self, track: &Track) -> Result<(), DomainError>;

    /// Load a track heavily focused on metadata.
    async fn find_by_id(&self, id: TrackId) -> Result<Option<Track>, DomainError>;

    /// Check if a track exists without loading its full payload.
    async fn exists(&self, id: TrackId) -> Result<bool, DomainError>;

    /// Find tracks modified after a specific revision (used for sync engine resolution).
    async fn find_by_revision_greater_than(&self, revision: Revision) -> Result<Vec<Track>, DomainError>;

    /// Streams or retrieves points for a specific track, bypassing aggregate root memory footprint.
    async fn stream_track_points(&self, track_id: TrackId, limit: Option<usize>, offset: Option<usize>) -> Result<Vec<TrackPoint>, DomainError>;

    /// Efficiently append new track points during active recording loops.
    async fn append_track_points(&self, track_id: TrackId, points: &[TrackPoint]) -> Result<(), DomainError>;
}
```

### WaypointRepository
Handles loosely or tightly bound marker coordinates that have an independent lifecycle from their parent Track.
```rust
#[async_trait]
pub trait WaypointRepository: Send + Sync {
    /// Save a Waypoint aggregate.
    async fn save(&self, waypoint: &Waypoint) -> Result<(), DomainError>;

    /// Load a Waypoint structurally.
    async fn find_by_id(&self, id: WaypointId) -> Result<Option<Waypoint>, DomainError>;

    /// Load all Waypoints structurally bound to a specific Track map representation.
    async fn find_by_track_id(&self, track_id: TrackId) -> Result<Vec<Waypoint>, DomainError>;

    /// Check if a Waypoint exists organically.
    async fn exists(&self, id: WaypointId) -> Result<bool, DomainError>;

    /// Target delta waypoints.
    async fn find_by_revision_greater_than(&self, revision: Revision) -> Result<Vec<Waypoint>, DomainError>;
}
```

### AttachmentRepository
Operates the dichotomy between metadata structures (SQLite) and raw binary blob payloads (Device Filesystem / Encryption stream).
```rust
#[async_trait]
pub trait AttachmentRepository: Send + Sync {
    /// Save metadata boundary for an attachment.
    async fn save_metadata(&self, attachment: &Attachment) -> Result<(), DomainError>;

    /// Retrieve attachment metadata strictly.
    async fn find_by_id(&self, id: AttachmentId) -> Result<Option<Attachment>, DomainError>;

    /// Retrieve all attachments scoped safely to a track geometry.
    async fn find_by_track_id(&self, track_id: TrackId) -> Result<Vec<Attachment>, DomainError>;

    /// Verify target metadata boundary.
    async fn exists(&self, id: AttachmentId) -> Result<bool, DomainError>;

    /// Delta target modified metadata schemas.
    async fn find_by_revision_greater_than(&self, revision: Revision) -> Result<Vec<Attachment>, DomainError>;

    /// Read raw binary payload chunks.
    async fn read_payload(&self, id: AttachmentId) -> Result<Vec<u8>, DomainError>;

    /// Commit raw binary payload against a local stream.
    async fn write_payload(&self, id: AttachmentId, data: &[u8]) -> Result<(), DomainError>;
}
```

### SearchRepository
Ties in with SQLite FTS5 representations without exposing FTS schema structures.
```rust
#[async_trait]
pub trait SearchRepository: Send + Sync {
    /// Execute a full-text search across all domain knowledge representation (Track name, Waypoint Notes).
    async fn search(&self, query: &str, limit: usize) -> Result<Vec<SearchResult>, DomainError>;
}
```

### SyncStateRepository
Independent structural tracking resolving ETags and Revisions without infecting core `Track` representations.
```rust
#[async_trait]
pub trait SyncStateRepository: Send + Sync {
    /// Retrieve sync state bounds.
    async fn get_sync_metadata(&self, entity_id: Uuid) -> Result<Option<SyncMetadata>, DomainError>;

    /// Save sync metadata after network negotiations map correctly.
    async fn save_sync_metadata(&self, metadata: &SyncMetadata) -> Result<(), DomainError>;

    /// Identify unresolved sync conflicts against WebDAV.
    async fn find_conflicts(&self) -> Result<Vec<ConflictRecord>, DomainError>;

    /// Lock down a newly detected conflict delta.
    async fn save_conflict(&self, conflict: &ConflictRecord) -> Result<(), DomainError>;

    /// Release an organically resolved merge layer.
    async fn resolve_conflict(&self, conflict_id: Uuid) -> Result<(), DomainError>;
}
```
