use thiserror::Error;
use domain::errors::domain_error::DomainError;
use rusqlite::Error as RusqliteError;
use application::errors::application_error::ApplicationError;
use r2d2::Error as PoolError;

#[derive(Error, Debug)]
pub enum InfrastructureError {
    #[error("Database error: {0}")]
    Database(#[from] RusqliteError),

    #[error("Connection pool error: {0}")]
    Pool(#[from] PoolError),

    #[error("Domain mapping error: {0}")]
    Mapping(String),

    #[error("Concurrency error: {0}")]
    Concurrency(String),
}

impl From<InfrastructureError> for ApplicationError {
    fn from(error: InfrastructureError) -> Self {
        match error {
            InfrastructureError::Database(err) => ApplicationError::Infrastructure(format!("SQLite Error: {}", err)),
            InfrastructureError::Pool(err) => ApplicationError::Infrastructure(format!("Pool Error: {}", err)),
            InfrastructureError::Mapping(msg) => ApplicationError::Infrastructure(format!("Mapping Error: {}", msg)),
            InfrastructureError::Concurrency(msg) => ApplicationError::Infrastructure(format!("Concurrency Error: {}", msg)),
        }
    }
}

impl From<InfrastructureError> for DomainError {
    fn from(error: InfrastructureError) -> Self {
        DomainError::InfrastructureError(error.to_string())
    }
}
