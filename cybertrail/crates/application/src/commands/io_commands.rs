use async_trait::async_trait;
use crate::errors::application_error::ApplicationError;
use crate::dto::track_dto::TrackIdDto;

pub struct ImportTrackCommand {
    pub file_path: String,
}

pub struct ExportTrackCommand {
    pub track_id: uuid::Uuid,
    pub dest_path: String,
}

#[async_trait]
pub trait IoCommandsHandler: Send + Sync {
    async fn handle_import_track(&self, cmd: ImportTrackCommand) -> Result<TrackIdDto, ApplicationError>;
    async fn handle_export_track(&self, cmd: ExportTrackCommand) -> Result<(), ApplicationError>;
}
