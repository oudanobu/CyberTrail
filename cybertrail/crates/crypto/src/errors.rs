use thiserror::Error;

#[derive(Error, Debug)]
pub enum CryptoError {
    #[error("Argon2 derivation failed: {0}")]
    DerivationError(String),
    #[error("Encryption failed: {0}")]
    EncryptionError(String),
    #[error("Decryption failed: {0}")]
    DecryptionError(String),
    #[error("Invalid Recovery Key format or checksum")]
    InvalidRecoveryKey,
    #[error("Invalid File Format: {0}")]
    InvalidFileFormat(String),
    #[error("IO Error: {0}")]
    IoError(#[from] std::io::Error),
    #[error("Compression Error: {0}")]
    CompressionError(String),
}
