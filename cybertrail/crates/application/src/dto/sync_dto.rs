use serde::{Deserialize, Serialize};
use uuid::Uuid;

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct SyncStatusDto {
    pub pending_uploads: u32,
    pub conflicts: Vec<ConflictDto>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ConflictDto {
    pub conflict_id: Uuid,
    pub entity_id: Uuid,
}
