use serde::{Deserialize, Serialize};
use uuid::Uuid;

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct SearchResultDto {
    pub entity_id: Uuid,
    pub entity_type: String, // "Track" or "Waypoint"
    pub snippet: String,
}
