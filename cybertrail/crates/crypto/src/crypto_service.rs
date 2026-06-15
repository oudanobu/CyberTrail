use async_trait::async_trait;
use crate::errors::CryptoError;
use serde::{Serialize, Deserialize};

#[async_trait]
pub trait CryptoService: Send + Sync {
    /// Attempts to unwrap the internal DEK using a user-provided password
    async fn unlock_with_password(&self, password: &str) -> Result<(), CryptoError>;
    
    /// Attempts to unwrap the internal DEK using a recovery key
    async fn unlock_with_recovery_key(&self, recovery_key: &str) -> Result<(), CryptoError>;

    /// Encrypts an object into the Cybertrail V1 File Format
    async fn encrypt<T: Serialize + Send + Sync>(&self, payload: &T, aad_context: &str) -> Result<Vec<u8>, CryptoError>;
    
    /// Decrypts a file using the V1 File Format
    async fn decrypt<T: for<'a> Deserialize<'a> + Send + Sync>(&self, bytes: &[u8], aad_context: &str) -> Result<T, CryptoError>;

    /// Changes the password (re-wraps the DEK, no payload changes)
    async fn change_password(&self, current_password: &str, new_password: &str) -> Result<(), CryptoError>;
    
    /// Generate a new DEK and initialize the cryptographic setup (returns Recovery Key)
    async fn initialize_vault(&self, new_password: &str) -> Result<String, CryptoError>;
}
