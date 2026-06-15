use rand::RngCore;
use rand::rngs::OsRng;
use base32::Alphabet;
use crate::errors::CryptoError;
use sha2::{Sha256, Digest};

pub struct RecoveryKey;

impl RecoveryKey {
    /// Generates a 32-character recovery key.
    /// Format: XXXX-XXXX-XXXX-XXXX-XXXX-XXXX-XXXX-XXXX (without dashes internally, added for display)
    pub fn generate() -> String {
        let mut raw = [0u8; 16]; // 128 bits
        OsRng.fill_bytes(&mut raw);
        
        // Compute checksum (first 4 bytes of SHA256)
        let mut hasher = Sha256::new();
        hasher.update(&raw);
        let hash = hasher.finalize();
        
        let mut combined = Vec::with_capacity(20);
        combined.extend_from_slice(&raw);
        combined.extend_from_slice(&hash[0..4]);
        
        // Base32 encode (Crockford or RFC4648 without padding, base32 crate uses RFC4648)
        base32::encode(Alphabet::RFC4648 { padding: false }, &combined)
    }

    /// Validates and decodes a recovery key into raw bytes for encryption/decryption (KEK equivalent).
    pub fn decode(key: &str) -> Result<[u8; 32], CryptoError> {
        let clean = key.replace("-", "").to_uppercase();
        
        let bytes = base32::decode(Alphabet::RFC4648 { padding: false }, &clean)
            .ok_or(CryptoError::InvalidRecoveryKey)?;
            
        if bytes.len() != 20 {
            return Err(CryptoError::InvalidRecoveryKey);
        }
        
        let raw = &bytes[0..16];
        let checksum = &bytes[16..20];
        
        let mut hasher = Sha256::new();
        hasher.update(raw);
        let hash = hasher.finalize();
        
        if checksum != &hash[0..4] {
            return Err(CryptoError::InvalidRecoveryKey);
        }
        
        // Stretch the 16 bytes into 32 bytes to act as a KEK
        let mut stretched = [0u8; 32];
        let mut hasher32 = Sha256::new();
        hasher32.update(raw);
        let hash32 = hasher32.finalize();
        stretched.copy_from_slice(&hash32);
        
        Ok(stretched)
    }
}
