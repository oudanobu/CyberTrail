use serde::{Serialize, Deserialize};
use zstd::stream::{encode_all, decode_all};
use crate::aes_gcm_provider::Aes256GcmProvider;
use crate::errors::CryptoError;

const MAGIC: [u8; 4] = *b"CTRL";
const VERSION: u8 = 0x01;
const CIPHER_AES256GCM: u8 = 0x01;

pub struct FileFormat;

impl FileFormat {
    /// Serializes, compresses, and encrypts a payload according to the V1 File Format.
    pub fn encrypt_payload<T: Serialize>(dek: &[u8; 32], payload: &T, aad_context: &str) -> Result<Vec<u8>, CryptoError> {
        let serialized = bincode::serialize(payload)
            .map_err(|e| CryptoError::InvalidFileFormat(e.to_string()))?;
            
        // Maximum compression for backend archive files
        let compressed = encode_all(std::io::Cursor::new(serialized), 3)
            .map_err(|e| CryptoError::CompressionError(e.to_string()))?;
            
        let aad_bytes = aad_context.as_bytes();
        if aad_bytes.len() > u16::MAX as usize {
            return Err(CryptoError::InvalidFileFormat("AAD too long".to_string()));
        }
        
        let (ciphertext_with_mac, nonce) = Aes256GcmProvider::encrypt(dek, &compressed, aad_bytes)?;
        
        // Assemble Binary Header
        let mut out = Vec::with_capacity(24 + aad_bytes.len() + ciphertext_with_mac.len());
        out.extend_from_slice(&MAGIC);
        out.push(VERSION);
        out.push(CIPHER_AES256GCM);
        out.extend_from_slice(&nonce); // 12 bytes
        out.extend_from_slice(&[0u8; 4]); // Padding for future nonces up to 24 bytes, currently AES-GCM uses 12
        
        // Let's pack nonce into bytes 6..21 strictly (16 bytes space)
        // Adjust:
        out.truncate(6); // Back to cipher
        let mut full_nonce = [0u8; 16];
        full_nonce[0..12].copy_from_slice(&nonce);
        out.extend_from_slice(&full_nonce);
        
        let aad_len = aad_bytes.len() as u16;
        out.extend_from_slice(&aad_len.to_le_bytes()); // 22-23
        
        out.extend_from_slice(aad_bytes); // 24-(24+AAD)
        out.extend_from_slice(&ciphertext_with_mac); // MAC is appended to ciphertext by AES GCM
        
        Ok(out)
    }

    /// Decrypts, decompresses, and deserializes a payload.
    pub fn decrypt_payload<T: for<'a> Deserialize<'a>>(dek: &[u8; 32], raw_bytes: &[u8], expected_aad: &str) -> Result<T, CryptoError> {
        if raw_bytes.len() < 24 {
            return Err(CryptoError::InvalidFileFormat("File too short".to_string()));
        }
        
        if raw_bytes[0..4] != MAGIC {
            return Err(CryptoError::InvalidFileFormat("Invalid MAGIC".to_string()));
        }
        
        if raw_bytes[4] != VERSION {
            return Err(CryptoError::InvalidFileFormat("Unsupported Version".to_string()));
        }
        
        if raw_bytes[5] != CIPHER_AES256GCM {
            return Err(CryptoError::InvalidFileFormat("Unsupported Cipher".to_string()));
        }
        
        let mut nonce = [0u8; 12];
        nonce.copy_from_slice(&raw_bytes[6..18]);
        
        let mut aad_len_bytes = [0u8; 2];
        aad_len_bytes.copy_from_slice(&raw_bytes[22..24]);
        let aad_len = u16::from_le_bytes(aad_len_bytes) as usize;
        
        if raw_bytes.len() < 24 + aad_len {
            return Err(CryptoError::InvalidFileFormat("Truncated AAD".to_string()));
        }
        
        let aad = &raw_bytes[24..24+aad_len];
        if aad != expected_aad.as_bytes() {
            return Err(CryptoError::InvalidFileFormat("AAD Mismatch! Potential swapping attack.".to_string()));
        }
        
        let ciphertext = &raw_bytes[24+aad_len..];
        
        let compressed = Aes256GcmProvider::decrypt(dek, ciphertext, &nonce, aad)?;
        
        let serialized = decode_all(std::io::Cursor::new(compressed))
            .map_err(|e| CryptoError::CompressionError(e.to_string()))?;
            
        let payload: T = bincode::deserialize(&serialized)
            .map_err(|e| CryptoError::InvalidFileFormat(e.to_string()))?;
            
        Ok(payload)
    }
}
