use uuid::Uuid;
use async_trait::async_trait;
use crate::errors::application_error::ApplicationError;

pub enum ResolutionStrategy {
    KeepLocal,
    KeepRemote,
}

pub struct ResolveConflictCommand {
    pub conflict_id: Uuid,
    pub resolution_strategy: ResolutionStrategy,
}

#[async_trait]
pub trait SyncCommandsHandler: Send + Sync {
    async fn handle_resolve_conflict(&self, cmd: ResolveConflictCommand) -> Result<(), ApplicationError>;
}
