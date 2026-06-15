use async_trait::async_trait;
use crate::errors::domain_error::DomainError;
use crate::entities::track::Track;
use crate::value_objects::identifiers::TrackId;
use crate::value_objects::revision::Revision;

#[async_trait]
pub trait TrackRepository: Send + Sync {
    /// Save a Track aggregate taking responsibility for metadata boundary.
    async fn save(&self, track: &Track) -> Result<(), DomainError>;

    /// Load a track by its identifier.
    async fn find_by_id(&self, id: TrackId) -> Result<Option<Track>, DomainError>;

    /// Check if a track exists without loading its full payload.
    async fn exists(&self, id: TrackId) -> Result<bool, DomainError>;

    /// Find tracks modified after a specific revision (used for sync).
    async fn find_by_revision_greater_than(&self, revision: Revision) -> Result<Vec<Track>, DomainError>;
}
