# CyberTrail Encryption Architecture
Version: 1.0
Status: Frozen

---

## 1. Zero-Knowledge Principle
- The WebDAV synchronization target is completely "blind".
- The server CANNOT read Track logs, Waypoints, Notes, or Attachments.
- All symmetric encryption occurs locally on the Android device before the payload enters the Sync Engine loop.

---

## 2. Key Hierarchy & Derivation

We employ a two-tier key architecture to support fast key rotation, password changes, and minimizing compromised payload vectors.

### A. Master Key (Key Encryption Key)
Generated from the user's password using the **Argon2id** key derivation function.
- **Salt**: 256-bit randomly generated, stored locally.
- **Purpose**: Exclusively used to encrypt/decrypt Data Keys. Never used to encrypt track data directly.

### B. Data Keys (Content Encryption Key)
Generated randomly (CSPRNG) for every major entity or entity grouping.
- **Purpose**: Used within an **AES-256-GCM** authenticated cipher scheme to encrypt/decrypt actual application data payloads.
- **Storage**: The Data Key is wrapped (encrypted) by the Master Key and stored in SQLite `encryption_metadata`.

---

## 3. Cryptographic Operations & Standards

- **Key Derivation**: `Argon2id` (Provides memory-hard and CPU-hard resistance to brute force).
- **Payload Encryption**: `AES-256-GCM` (Galois/Counter Mode).
  - *Why*: Provides Authenticated Encryption with Associated Data (AEAD). It prevents blind modification of the encrypted WebDAV payload by generating an authentication MAC.
- **Initialization Vectors (Nonce)**: 96-bit (12 bytes) uniquely generated per encryption cycle. Must never be reused with the same Data Key.

---

## 4. Metadata vs. Payload Security

**Unencrypted (Plaintext synced via WebDAV):**
- Domain UUIDs (To allow the sync engine to identify files).
- ETags and Sync Revisions.
- Entity Types (Identifier signaling if a chunk is a Track or Waypoint).

**Encrypted (Wrapped by AES-256-GCM):**
- Track Names, Duration, Distances, Stats.
- High-Frequency Track Points (lat, lng, altitude).
- Waypoint coordinates and notes.
- Attachments (Images, GPX exports).

---

## 5. Lifecycle Management

### Password Change Operation
We do NOT need to re-encrypt gigabytes of tracking logs or high-frequency data.
1. User provides Old Password and New Password.
2. Un-wrap all SQLite `encryption_metadata` Data Keys using the Old Master Key.
3. Generate New Master Key via `Argon2id` + New Password.
4. Wrap all Data Keys with the New Master Key.
5. Save updated wrapped Data Keys back to `encryption_metadata`.
6. Sync engine pushes the updated metadata. (Payload remains untouched).

### Recovery Strategy
If a password is lost, the Master Key is lost, and all Data Keys are inaccessible.
- During initialization, provide a **Recovery Phrase** (24-word mnemonic or raw 256-bit Hex Key).
- The Recovery Phrase serves as a mathematical bypass to unwrap Data Keys directly, allowing the user to set a new password.

---

## 6. End-to-End Data Flow

```text
[ User Completes Track ]
           │
           ▼
[ Domain issues Track entity + Points to Serializer ]
           │
           ▼
[ Crypto Engine ] 
   1. Fetches/Generates Data Key for {uuid}.
   2. Generates 96-bit Nonce.
   3. Encrypts Payload via AES-256-GCM.
           │
           ▼
[ Sync Engine ]
   Takes (UUID, Nonce, MAC, Encrypted Payload)
           │
           ▼
[ WebDAV Client (PUT) ]
```
