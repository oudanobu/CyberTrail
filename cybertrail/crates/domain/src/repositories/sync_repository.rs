use async_trait::async_trait;
use uuid::Uuid;
use crate::errors::domain_error::DomainError;
use crate::entities::sync_metadata::SyncMetadata;
use crate::entities::conflict_record::ConflictRecord;

#[async_trait]
pub trait SyncStateRepository: Send + Sync {
    /// Retrieve sync state for a given entity.
    async fn get_sync_metadata(&self, entity_id: Uuid) -> Result<Option<SyncMetadata>, DomainError>;

    /// Save sync metadata after network negotiation.
    async fn save_sync_metadata(&self, metadata: &SyncMetadata) -> Result<(), DomainError>;

    /// Identify unresolved conflicts.
    async fn find_conflicts(&self) -> Result<Vec<ConflictRecord>, DomainError>;

    /// Save a newly detected conflict.
    async fn save_conflict(&self, conflict: &ConflictRecord) -> Result<(), DomainError>;

    /// Clear a conflict record once resolved.
    async fn resolve_conflict(&self, conflict_id: Uuid) -> Result<(), DomainError>;
}
