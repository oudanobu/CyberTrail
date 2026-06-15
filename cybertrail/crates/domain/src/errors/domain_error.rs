use thiserror::Error;

#[derive(Error, Debug, PartialEq)]
pub enum DomainError {
    #[error("Validation Error: {0}")]
    ValidationError(String),

    #[error("Conflict Error: {0}")]
    Conflict(String),

    #[error("Not Found Error: {0}")]
    NotFound(String),
    
    #[error("Illegal State Error: {0}")]
    IllegalStateError(String),
}
