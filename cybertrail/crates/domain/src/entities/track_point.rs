use crate::value_objects::identifiers::TrackId;
use crate::value_objects::coordinate::Coordinate;
use crate::value_objects::altitude::Altitude;

#[derive(Debug, Clone, PartialEq)]
pub struct TrackPoint {
    track_id: TrackId,
    timestamp: i64,
    coordinate: Coordinate,
    altitude: Option<Altitude>,
}

impl TrackPoint {
    pub fn new(track_id: TrackId, timestamp: i64, coordinate: Coordinate, altitude: Option<Altitude>) -> Self {
        Self {
            track_id,
            timestamp,
            coordinate,
            altitude,
        }
    }

    pub fn track_id(&self) -> TrackId { self.track_id }
    pub fn timestamp(&self) -> i64 { self.timestamp }
    pub fn coordinate(&self) -> Coordinate { self.coordinate }
    pub fn altitude(&self) -> Option<Altitude> { self.altitude }
}
