use serde::{Deserialize, Serialize};
use uuid::Uuid;

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct TrackDto {
    pub id: Uuid,
    pub name: String,
    pub started_at: i64,
    pub ended_at: Option<i64>,
    pub duration_seconds: i64,
    pub distance_m: f64,
    pub ascent_m: f64,
    pub descent_m: f64,
    pub avg_speed_ms: f64,
    pub max_speed_ms: f64,
    pub max_altitude_m: f64,
    pub min_altitude_m: f64,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct TrackSummaryDto {
    pub id: Uuid,
    pub name: String,
    pub started_at: i64,
    pub distance_m: f64,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct TrackPointDto {
    pub track_id: Uuid,
    pub point_id: Uuid,
    pub lat: f64,
    pub lon: f64,
    pub altitude: Option<f64>,
    pub timestamp: i64,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct TrackIdDto {
    pub id: Uuid,
}
