use thiserror::Error;
use domain::errors::domain_error::DomainError;
use rusqlite::Error as RusqliteError;
use application::errors::application_error::ApplicationError;
use r2d2::Error as PoolError;

#[derive(Error, Debug)]
pub enum InfrastructureError {
    #[error("SQLite database error: {0}")]
    SqliteError(String),

    #[error("I/O error: {0}")]
    IoError(String),

    #[error("Mapping error: {0}")]
    MappingError(String),
}

impl From<RusqliteError> for InfrastructureError {
    fn from(error: RusqliteError) -> Self {
        InfrastructureError::SqliteError(error.to_string())
    }
}

impl From<PoolError> for InfrastructureError {
    fn from(error: PoolError) -> Self {
        InfrastructureError::SqliteError(format!("Pool error: {}", error))
    }
}

impl From<InfrastructureError> for ApplicationError {
    fn from(error: InfrastructureError) -> Self {
        match error {
            InfrastructureError::SqliteError(msg) => ApplicationError::Infrastructure(format!("SQLite Error: {}", msg)),
            InfrastructureError::IoError(msg) => ApplicationError::Infrastructure(format!("I/O Error: {}", msg)),
            InfrastructureError::MappingError(msg) => ApplicationError::Infrastructure(format!("Mapping Error: {}", msg)),
        }
    }
}

impl From<InfrastructureError> for DomainError {
    fn from(error: InfrastructureError) -> Self {
        match error {
            InfrastructureError::SqliteError(msg) => DomainError::Conflict(format!("Database error: {}", msg)),
            InfrastructureError::IoError(msg) => DomainError::Conflict(format!("I/O error: {}", msg)),
            InfrastructureError::MappingError(msg) => DomainError::ValidationError(msg),
        }
    }
}
