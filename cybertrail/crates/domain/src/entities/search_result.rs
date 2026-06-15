use uuid::Uuid;

#[derive(Debug, Clone, PartialEq)]
pub struct SearchResult {
    pub entity_id: Uuid,
    pub snippet: String,
}
