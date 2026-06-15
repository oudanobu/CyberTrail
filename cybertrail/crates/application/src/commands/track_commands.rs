use uuid::Uuid;
use crate::dto::track_dto::{TrackPointDto, TrackIdDto};
use async_trait::async_trait;
use crate::errors::application_error::ApplicationError;

pub struct CreateTrackCommand {
    pub name: String,
    pub started_at: i64,
}

pub struct AppendTrackPointCommand {
    pub track_id: Uuid,
    pub points: Vec<TrackPointDto>,
}

pub struct RenameTrackCommand {
    pub track_id: Uuid,
    pub new_name: String,
    pub timestamp: i64,
}

pub struct DeleteTrackCommand {
    pub track_id: Uuid,
    pub timestamp: i64,
}

pub struct RestoreTrackCommand {
    pub track_id: Uuid,
    pub timestamp: i64,
}

#[async_trait]
pub trait TrackCommandsHandler: Send + Sync {
    async fn handle_create(&self, cmd: CreateTrackCommand) -> Result<TrackIdDto, ApplicationError>;
    async fn handle_append_points(&self, cmd: AppendTrackPointCommand) -> Result<(), ApplicationError>;
    async fn handle_rename(&self, cmd: RenameTrackCommand) -> Result<(), ApplicationError>;
    async fn handle_delete(&self, cmd: DeleteTrackCommand) -> Result<(), ApplicationError>;
    async fn handle_restore(&self, cmd: RestoreTrackCommand) -> Result<(), ApplicationError>;
}
