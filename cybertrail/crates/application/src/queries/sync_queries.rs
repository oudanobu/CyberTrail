use async_trait::async_trait;
use crate::errors::application_error::ApplicationError;
use crate::dto::sync_dto::SyncStatusDto;

pub struct GetSyncStatusQuery {}

#[async_trait]
pub trait SyncQueriesHandler: Send + Sync {
    async fn handle_get_sync_status(&self, query: GetSyncStatusQuery) -> Result<SyncStatusDto, ApplicationError>;
}
