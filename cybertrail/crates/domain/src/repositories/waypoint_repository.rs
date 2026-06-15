use async_trait::async_trait;
use crate::errors::domain_error::DomainError;
use crate::entities::waypoint::Waypoint;
use crate::value_objects::identifiers::{TrackId, WaypointId};
use crate::value_objects::revision::Revision;

#[async_trait]
pub trait WaypointRepository: Send + Sync {
    /// Save a Waypoint aggregate.
    async fn save(&self, waypoint: &Waypoint) -> Result<(), DomainError>;

    /// Load a Waypoint by its specific ID.
    async fn find_by_id(&self, id: WaypointId) -> Result<Option<Waypoint>, DomainError>;

    /// Load all Waypoints structurally bound to a specific Track.
    async fn find_by_track_id(&self, track_id: TrackId) -> Result<Vec<Waypoint>, DomainError>;

    /// Check if a Waypoint exists.
    async fn exists(&self, id: WaypointId) -> Result<bool, DomainError>;

    /// Find Waypoints modified after a specific revision (used for sync).
    async fn find_by_revision_greater_than(&self, revision: Revision) -> Result<Vec<Waypoint>, DomainError>;
}
