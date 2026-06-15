use aes_gcm::{
    aead::{Aead, AeadCore, KeyInit, OsRng},
    Aes256Gcm, Nonce, Key,
};
use rand::RngCore;
use crate::errors::CryptoError;

pub struct Aes256GcmProvider;

impl Aes256GcmProvider {
    pub fn encrypt(key: &[u8; 32], plaintext: &[u8], aad: &[u8]) -> Result<(Vec<u8>, [u8; 12]), CryptoError> {
        let cipher = Aes256Gcm::new(Key::<Aes256Gcm>::from_slice(key));
        let nonce = Aes256Gcm::generate_nonce(&mut OsRng); // 96-bits; 12 bytes
        
        let payload = aes_gcm::aead::Payload {
            msg: plaintext,
            aad,
        };
        
        let ciphertext = cipher.encrypt(&nonce, payload)
            .map_err(|e| CryptoError::EncryptionError(e.to_string()))?;
            
        let mut nonce_arr = [0u8; 12];
        nonce_arr.copy_from_slice(nonce.as_slice());
        
        Ok((ciphertext, nonce_arr))
    }

    pub fn decrypt(key: &[u8; 32], ciphertext: &[u8], nonce: &[u8; 12], aad: &[u8]) -> Result<Vec<u8>, CryptoError> {
        let cipher = Aes256Gcm::new(Key::<Aes256Gcm>::from_slice(key));
        let nonce_obj = Nonce::from_slice(nonce);
        
        let payload = aes_gcm::aead::Payload {
            msg: ciphertext,
            aad,
        };
        
        cipher.decrypt(nonce_obj, payload)
            .map_err(|e| CryptoError::DecryptionError(e.to_string()))
    }
    
    pub fn generate_data_key() -> [u8; 32] {
        let mut key = [0u8; 32];
        OsRng.fill_bytes(&mut key);
        key
    }
}
