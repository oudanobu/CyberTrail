use async_trait::async_trait;
use crate::errors::application_error::ApplicationError;
use crate::dto::search_dto::SearchResultDto;

pub struct SearchQuery {
    pub query: String,
    pub limit: u32,
}

#[async_trait]
pub trait SearchQueriesHandler: Send + Sync {
    async fn handle_global_search(&self, query: SearchQuery) -> Result<Vec<SearchResultDto>, ApplicationError>;
}
