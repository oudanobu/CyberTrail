use argon2::{
    password_hash::{rand_core::OsRng, PasswordHasher, SaltString},
    Argon2,
};
use crate::errors::CryptoError;

pub struct KeyDerivation;

impl KeyDerivation {
    /// Derives a 32-byte Key-Encryption Key (KEK) from a plaintext password using Argon2id.
    /// In a real system, the salt must be persisted (e.g., in SQLite) and passed to decoding.
    pub fn derive_kek(password: &str, salt: &str) -> Result<[u8; 32], CryptoError> {
        let argon2 = Argon2::default();
        let parsed_salt = SaltString::from_b64(salt)
            .map_err(|e| CryptoError::DerivationError(e.to_string()))?;
            
        let hash = argon2.hash_password(password.as_bytes(), &parsed_salt)
            .map_err(|e| CryptoError::DerivationError(e.to_string()))?;
            
        let mut key = [0u8; 32];
        let hash_output = hash.hash.ok_or_else(|| CryptoError::DerivationError("No hash output".to_string()))?;
        let output_bytes = hash_output.as_bytes();
        
        if output_bytes.len() < 32 {
            return Err(CryptoError::DerivationError("Hash output too short".to_string()));
        }
        
        key.copy_from_slice(&output_bytes[..32]);
        Ok(key)
    }

    pub fn generate_salt() -> String {
        SaltString::generate(&mut OsRng).as_str().to_string()
    }
}
