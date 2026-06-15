use uuid::Uuid;
use async_trait::async_trait;
use crate::errors::application_error::ApplicationError;
use crate::dto::track_dto::{TrackDto, TrackSummaryDto, TrackPointDto};

pub enum TrackOrder {
    DateDesc,
    DateAsc,
}

pub struct GetTrackQuery {
    pub track_id: Uuid,
}

pub struct ListTracksQuery {
    pub limit: u32,
    pub offset: u32,
    pub order_by: TrackOrder,
}

pub struct StreamTrackPointsQuery {
    pub track_id: Uuid,
    pub limit: Option<usize>,
    pub offset: Option<usize>,
}

#[async_trait]
pub trait TrackQueriesHandler: Send + Sync {
    async fn handle_get_track(&self, query: GetTrackQuery) -> Result<TrackDto, ApplicationError>;
    async fn handle_list_tracks(&self, query: ListTracksQuery) -> Result<Vec<TrackSummaryDto>, ApplicationError>;
    async fn handle_stream_points(&self, query: StreamTrackPointsQuery) -> Result<Vec<TrackPointDto>, ApplicationError>;
}
