use uuid::Uuid;

#[derive(Debug, Clone, PartialEq)]
pub struct ConflictRecord {
    pub id: Uuid,
}
