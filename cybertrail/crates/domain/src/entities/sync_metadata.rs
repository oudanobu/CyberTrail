use uuid::Uuid;

#[derive(Debug, Clone, PartialEq)]
pub struct SyncMetadata {
    pub entity_id: Uuid,
}
