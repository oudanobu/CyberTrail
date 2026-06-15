use serde::{Deserialize, Serialize};
use uuid::Uuid;

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct WaypointDto {
    pub id: Uuid,
    pub track_id: Option<Uuid>,
    pub name: String,
    pub lat: f64,
    pub lon: f64,
    pub altitude: Option<f64>,
    pub notes: Option<String>,
    pub created_at: i64,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct WaypointIdDto {
    pub id: Uuid,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct CoordinateDto {
    pub lat: f64,
    pub lon: f64,
}
