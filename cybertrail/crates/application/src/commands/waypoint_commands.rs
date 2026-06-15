use uuid::Uuid;
use crate::dto::waypoint_dto::{CoordinateDto, WaypointIdDto};
use async_trait::async_trait;
use crate::errors::application_error::ApplicationError;

pub struct CreateWaypointCommand {
    pub track_id: Option<Uuid>,
    pub name: String,
    pub coordinate: CoordinateDto,
    pub altitude: Option<f64>,
    pub notes: Option<String>,
    pub timestamp: i64,
}

pub struct RenameWaypointCommand {
    pub waypoint_id: Uuid,
    pub new_name: String,
    pub timestamp: i64,
}

pub struct MoveWaypointCommand {
    pub waypoint_id: Uuid,
    pub new_coordinate: CoordinateDto,
    pub new_altitude: Option<f64>,
    pub timestamp: i64,
}

pub struct DeleteWaypointCommand {
    pub waypoint_id: Uuid,
    pub timestamp: i64,
}

pub struct RestoreWaypointCommand {
    pub waypoint_id: Uuid,
    pub timestamp: i64,
}

#[async_trait]
pub trait WaypointCommandsHandler: Send + Sync {
    async fn handle_create(&self, cmd: CreateWaypointCommand) -> Result<WaypointIdDto, ApplicationError>;
    async fn handle_rename(&self, cmd: RenameWaypointCommand) -> Result<(), ApplicationError>;
    async fn handle_move(&self, cmd: MoveWaypointCommand) -> Result<(), ApplicationError>;
    async fn handle_delete(&self, cmd: DeleteWaypointCommand) -> Result<(), ApplicationError>;
    async fn handle_restore(&self, cmd: RestoreWaypointCommand) -> Result<(), ApplicationError>;
}
