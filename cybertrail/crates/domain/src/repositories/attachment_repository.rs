use async_trait::async_trait;
use crate::errors::domain_error::DomainError;
use crate::entities::attachment::Attachment;
use crate::value_objects::identifiers::{TrackId, AttachmentId};
use crate::value_objects::revision::Revision;

#[async_trait]
pub trait AttachmentRepository: Send + Sync {
    /// Save metadata boundary for an attachment.
    async fn save_metadata(&self, attachment: &Attachment) -> Result<(), DomainError>;

    /// Retrieve attachment metadata.
    async fn find_by_id(&self, id: AttachmentId) -> Result<Option<Attachment>, DomainError>;

    /// Retrieve all attachments scoped safely to a track.
    async fn find_by_track_id(&self, track_id: TrackId) -> Result<Vec<Attachment>, DomainError>;

    /// Check if target metadata boundary exists.
    async fn exists(&self, id: AttachmentId) -> Result<bool, DomainError>;

    /// Identify attachments modified beyond a revision point.
    async fn find_by_revision_greater_than(&self, revision: Revision) -> Result<Vec<Attachment>, DomainError>;

    /// Read raw binary payload chunks.
    async fn read_payload(&self, id: AttachmentId) -> Result<Vec<u8>, DomainError>;

    /// Commit raw binary payload.
    async fn write_payload(&self, id: AttachmentId, data: &[u8]) -> Result<(), DomainError>;
}
