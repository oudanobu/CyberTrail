use crate::aes_gcm_provider::Aes256GcmProvider;
use crate::errors::CryptoError;

pub struct KeyWrapService;

impl KeyWrapService {
    /// Wraps the 32-byte DEK using the 32-byte KEK.
    pub fn wrap_dek(dek: &[u8; 32], kek: &[u8; 32]) -> Result<Vec<u8>, CryptoError> {
        // AAD context protects against context swapping
        let aad = b"cybertrail_dek_wrap";
        let (ciphertext, nonce) = Aes256GcmProvider::encrypt(kek, dek, aad)?;
        
        // Final structure: [Nonce (12)] + [Ciphertext + MAC]
        let mut wrapped = Vec::with_capacity(12 + ciphertext.len());
        wrapped.extend_from_slice(&nonce);
        wrapped.extend_from_slice(&ciphertext);
        
        Ok(wrapped)
    }

    /// Unwraps the DEK using the KEK.
    pub fn unwrap_dek(wrapped: &[u8], kek: &[u8; 32]) -> Result<[u8; 32], CryptoError> {
        if wrapped.len() < 12 {
            return Err(CryptoError::DecryptionError("Wrapped DEK too short".to_string()));
        }
        
        let mut nonce = [0u8; 12];
        nonce.copy_from_slice(&wrapped[0..12]);
        let ciphertext = &wrapped[12..];
        
        let aad = b"cybertrail_dek_wrap";
        let dek_vec = Aes256GcmProvider::decrypt(kek, ciphertext, &nonce, aad)?;
        
        if dek_vec.len() != 32 {
            return Err(CryptoError::DecryptionError("Unwrapped DEK length invalid".to_string()));
        }
        
        let mut dek = [0u8; 32];
        dek.copy_from_slice(&dek_vec);
        Ok(dek)
    }
}
