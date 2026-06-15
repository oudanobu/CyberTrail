use thiserror::Error;

#[derive(Error, Debug, PartialEq)]
pub enum DomainError {
    #[error("Validation Error: {0}")]
    ValidationError(String),

    #[error("Conflict Error: {0}")]
    ConflictError(String),

    #[error("Encryption Error: {0}")]
    EncryptionError(String),

    #[error("Repository Error: {0}")]
    RepositoryError(String),
    
    #[error("Illegal State Error: {0}")]
    IllegalStateError(String),
}
