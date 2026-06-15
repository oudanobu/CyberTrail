use async_trait::async_trait;
use crate::errors::domain_error::DomainError;
use crate::entities::track_point::TrackPoint;
use crate::value_objects::identifiers::TrackId;

#[async_trait]
pub trait TrackPointRepository: Send + Sync {
    /// Batch appends points efficiently.
    async fn append_track_points(&self, points: &[TrackPoint]) -> Result<(), DomainError>;

    /// Streams track points via timestamp cursor.
    async fn stream_track_points(&self, track_id: TrackId, start_timestamp: i64, limit: u32) -> Result<Vec<TrackPoint>, DomainError>;

    /// Gets the count of track points securely without loading them.
    async fn count_track_points(&self, track_id: TrackId) -> Result<u64, DomainError>;

    /// Gets the very first point of a track physically recorded.
    async fn first_track_point(&self, track_id: TrackId) -> Result<Option<TrackPoint>, DomainError>;

    /// Gets the most recent point recorded.
    async fn last_track_point(&self, track_id: TrackId) -> Result<Option<TrackPoint>, DomainError>;

    /// Deletes all points for a Track (cascading equivalent).
    async fn delete_track_points_by_track(&self, track_id: TrackId) -> Result<(), DomainError>;
}
