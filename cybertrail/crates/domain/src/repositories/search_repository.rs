use async_trait::async_trait;
use crate::errors::domain_error::DomainError;
use crate::entities::search_result::SearchResult;

#[async_trait]
pub trait SearchRepository: Send + Sync {
    /// Execute a full-text search across all domain knowledge representation.
    async fn search(&self, query: &str, limit: usize) -> Result<Vec<SearchResult>, DomainError>;
}
