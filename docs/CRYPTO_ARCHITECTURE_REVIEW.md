# CyberTrail Crypto Architecture Review
Version: 1.0
Status: Frozen

---

## 1. Threat Model

**Defended Against (In-Scope)**:
- WebDAV service providers snooping on trajectory data.
- Cloud storage administrators accessing private files.
- Local SQLite database exfiltration (if device is rooted or DB copied via USB).
- SD card/external storage dumping.

**Not Defended Against (Out-of-Scope)**:
- User password compromise (e.g., shoulder surfing, weak passwords).
- Live device compromise (e.g., active malware reading memory/screen when app is unlocked).
- Malicious keyloggers.

---

## 2. Key Hierarchy

**The Chain**:
`Password` -> `Argon2id` -> `Key-Encryption Key (KEK)` -> `AES Key Wrap` -> `Data Encryption Key (DEK)` -> `AES-256-GCM` -> `Payload`

**Why Not Encrypt Everything Directly with the Password?**
If a user changes their password, directly encrypting the payload means we must decrypt and re-encrypt *every single file* (gigabytes of tracks, points, and attachments). By introducing a Data Encryption Key (DEK), changing the password only requires re-wrapping (re-encrypting) the tiny DEK with the new Key-Encryption Key (KEK) derived from the new password. The payload data remains untouched.

---

## 3. Android Keystore Strategy

Where do the cryptographic components live?
- **Master Key / KEK**: Derived from user password at runtime, held only in volatile memory (never written to disk).
- **Data Key (DEK)**: Stored locally in SQLite (and optionally synced), but *always* wrapped (encrypted) by the KEK.
- **Salt / Nonces**: Stored in plaintext alongside the ciphertext or within the local DB sync configuration.

**Strategy Evaluation**:
- *Option A: Pure Android Keystore*. Hardware-backed, but makes multi-device WebDAV sync a nightmare as Keystore keys cannot be exported securely to another device without complex HSM configurations.
- *Option B: Keystore + Wrapped DataKey*. Uses Android Keystore to protect the DEK locally, bypassing the need for a password on app launch, but complicates the "bring-your-own WebDAV" multi-device sync model.
- *Option C: Pure Cryptographic Key Wrap*. The DEK is encrypted using a password-derived KEK. The wrapped DEK is synced via WebDAV. Any device with the password can unwrap the DEK and decrypt the payload. 

**Decision: Option C (Pure Cryptographic Key Wrap)**.
Offline-first synchronization demands that the user solely owns their keys and can hydrate a fresh device via WebDAV entirely offline without relying on Google/Android hardware vendor lock-in. 

---

## 4. Password Change Flow

When a user changes their password:
1. Request current password and new password.
2. Derive `Old_KEK` from current password via Argon2id.
3. Unwrap the `DEK` using `Old_KEK`.
4. Derive `New_KEK` from new password via Argon2id.
5. Wrap the `DEK` using `New_KEK`.
6. Save the newly wrapped DEK to SQLite and sync it to WebDAV.
*Result: Zero payload data (tracks, points, photos) is re-encrypted. The operation takes milliseconds.*

---

## 5. File Encryption Strategy

What gets encrypted?
- **Track Metadata (`metadata.json.enc`)**: Encrypted. Contains private track names and descriptions.
- **Waypoints (`waypoints.json.enc`)**: Encrypted. Contains notes and specific coordinates.
- **TrackPoint Chunks (`points_xxxxx.bin.enc`)**: Encrypted. Protects raw movement patterns and locations.
- **Attachment Payload (`photo.jpg.enc`)**: Encrypted. Protects private media.

What remains plaintext?
- **Directory Structure (UUIDs)**: Folder names are UUIDs (`/tracks/123e4567-e89b-12d3-a456-426614174000/`). Exposing meaningless UUIDs prevents metadata leakage while allowing basic WebDAV `PROPFIND` operations to function without decrypting folder names.
- **Sync Envelope metadata**: Minimal syncing timestamps for collision resolution.

---

## 6. WebDAV Layout Review

**Encrypted Layout**:
```text
/cybertrail_sync/
  ├── keystore.enc               (The wrapped DEK and Recovery Key payload)
  ├── tracks/
  │    ├── {track_id}/
  │    │    ├── metadata.json.enc       
  │    │    ├── points_000001.bin.enc   
  │    │    ├── points_000002.bin.enc   
  │    │    └── waypoints.json.enc      
  ├── attachments/
  │    ├── {attachment_id}.enc
```
**Pros**: Single file extension pattern (`.enc`). Standardized decryption pipeline. Directory UUIDs enable delta-sync without decrypting the whole tree.

---

## 7. Crypto Agility

Algorithms evolve. AES-256-GCM is standard today, but XChaCha20-Poly1305 might be preferred on low-end ARM devices later.
- **Design Requirement**: Every encrypted file must have a header.
- **Header Format**: `[Version (1 byte)] | [Cipher Enum (1 byte)] | [Nonce (12-24 bytes)] | [Ciphertext + MAC tag]`.
- **Cipher Enum**: `0x01` = AES-256-GCM, `0x02` = XChaCha20-Poly1305.
This guarantees forward compatibility without catastrophic migration scripts.

---

## 8. Disaster Recovery (The Recovery Key)

If a user forgets their password, they cannot derive the KEK, cannot unwrap the DEK, and lose access to all synced WebDAV data permanently. 
To prevent this catastrophic failure mode:

**The Recovery Key**:
- Generated *once* during initial E2EE setup.
- Typically a 32-character string formatted for readability: `ABCD-EFGH-IJKL-MNOP-QRST-UVWX-YZ12-3456`.
- High entropy, visually distinct.
- Placed in a separate wrapper: The `DEK` is wrapped *twice*—once by the Password `KEK`, and once by the Recovery Key.

**Recovery Flow**:
Forgot password -> Enter Recovery Key -> Unwrap DEK -> Prompt for new Password -> Re-wrap DEK.
**Firm Policy**: If both the Password *and* the Recovery Key are lost, the data is mathematically unrecoverable. Zero exceptions.

---

## 9. Performance Analysis

**Target**: Android 13, 4GB RAM, generic/older ARM64 CPU.
**Algorithm**: AES-256-GCM.

- **10MB (Typical hike, few photos)**: < 50ms. Barely noticeable.
- **100MB (Long trek, structured chunks)**: ~ 300-500ms. Since we encrypt file-by-file in streams, RAM remains flat. CPU pipeline handles AES instructions efficiently (ARMv8 cryptography extensions).
- **1GB (Massive multi-month archive sync)**: ~ 3-5 seconds. Handled smoothly via async chunked background workers.

*Conclusion*: AES-256-GCM on ARMv8 is heavily hardware-accelerated. Payload streaming ensures memory stays < 100MB even for gigabyte-level encryptions.

---

## 10. Final Recommendation

**Freeze: Crypto Architecture V1.**
The architecture strictly enforces End-to-End Encryption (E2EE) while maintaining high sync performance via DEK-wrapping. It handles disaster recovery locally, is future-proofed with cipher agility, and perfectly aligns with the established WebDAV chunk layout.
