use uuid::Uuid;
use async_trait::async_trait;
use crate::errors::application_error::ApplicationError;
use crate::dto::waypoint_dto::WaypointDto;

pub struct GetWaypointQuery {
    pub waypoint_id: Uuid,
}

pub struct ListWaypointsQuery {
    pub track_id: Option<Uuid>,
}

#[async_trait]
pub trait WaypointQueriesHandler: Send + Sync {
    async fn handle_get_waypoint(&self, query: GetWaypointQuery) -> Result<WaypointDto, ApplicationError>;
    async fn handle_list_waypoints(&self, query: ListWaypointsQuery) -> Result<Vec<WaypointDto>, ApplicationError>;
}
