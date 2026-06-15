use thiserror::Error;
use domain::errors::domain_error::DomainError;

#[derive(Error, Debug, PartialEq)]
pub enum ApplicationError {
    #[error("Domain Error: {0}")]
    Domain(#[from] DomainError),
    
    #[error("Not Found: {0}")]
    NotFound(String),
    
    #[error("Unauthorized: {0}")]
    Unauthorized(String),
    
    #[error("Infrastructure Error: {0}")]
    Infrastructure(String),
}
