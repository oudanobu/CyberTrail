# CyberTrail Crypto File Format & Metadata
Version: 1.0
Status: Frozen

---

## 1. Encrypted Header Layout

To ensure future decipherability across all platforms (Android, Linux, Web), every encrypted file on WebDAV relies on a strict binary header.

**Format Pattern (Little-Endian for numerics):**
| Bytes | Field | Description |
| :--- | :--- | :--- |
| `0-3` | **Magic** | `CTRL` (CyberTRaiL) to identify the file type. |
| `4-4` | **Version** | `0x01` (Header structural version). |
| `5-5` | **Cipher** | `0x01` (See Cipher Enum below). |
| `6-21`| **Nonce** | 12 bytes for AES-GCM, up to 24 bytes for XChaCha20. Padded with `0x00` if unused. |
| `22-23`| **AAD Length** | `u16` size of the Additional Authenticated Data string. |
| `24-(24+AAD_Len)` | **AAD Payload** | The UTF-8 AAD context string (e.g., `track_id\|chunk_0001`). |
| `(24+AAD_Len)-...` | **MAC Tag** | 16 bytes (Auth Tag for GCM/Poly1305). |
| `...` | **Ciphertext** | The encrypted, compressed payload bytes. |

---

## 2. Cipher Enum (Crypto Agility)

Hardcoding algorithms assumes cryptographic supremacy that doesn't exist. We include a Cipher registry for future migrations.

```rust
enum CipherSuite {
    Aes256Gcm = 0x01,
    XChaCha20Poly1305 = 0x02,
}
```
*Current V1 Default: `Aes256Gcm` (0x01), taking advantage of modern ARMv8 hardware acceleration on Android.*

---

## 3. AAD Strategy (Additional Authenticated Data)

**Problem**: What if an attacker duplicates an encrypted `points_0001.bin.enc` from Track A and overrides `points_0001.bin.enc` in Track B? The decryption would mathematically succeed, corrupting Track B's data silently.

**Solution**: Contextual AAD.
All encryption and decryption operations **MUST** bind the data to its logical location properties via AAD.
- **Track Metadata AAD**: `b"metadata\|{track_id}"`
- **TrackPoint Chunk AAD**: `b"chunk\|{track_id}\|{chunk_index}"`
- **Attachment AAD**: `b"attachment\|{attachment_id}"`

If an attacker physically shuffles files, the MAC verification will hard-fail because the context declared in the AAD payload won't match the expected local execution context, thereby thwarting replay/swap attacks.

---

## 4. Metadata File Format

**Decision: Whole-File Encryption.**
We **do not** use field-level encryption (e.g., leaving a JSON structure but encrypting only the values). Exposing JSON keys can still leak behavioral intent and domain structures.

`metadata.json.enc` encapsulates a standard JSON payload:
```json
{
  "name": "Mt. Fuji Summit",
  "started_at": 1698240000,
  "distance_m": 12500.5,
  "revision": 3
}
```
The entire string is serialized, compressed, encrypted, and saved as a secure, opaque binary blob.

---

## 5. Chunk Format (Serialization)

**Decision: `bincode` for TrackPoints.**
When serializing `TrackPoint` arrays into chunks (`points_xxxxx.bin.enc`):
- We strictly avoid JSON or GeoJSON. They are heavy, repetitive text formats lacking native fixed-point efficiency.
- We avoid MessagePack or CBOR as they carry structural self-describing overhead unnecessary for static, predefined arrays.
- We use **`bincode`**: It tightly packs the rigid Rust struct defined in Phase 8.1B-2A (`timestamp`, `lat_micro`, `lon_micro`, `alt_cm`) into raw contiguous bytes, maximizing density.

---

## 6. Compression Ordering

**Mandatory Pipeline:**
`Serialize (bincode / JSON)` -> `Compress (zstd)` -> `Encrypt (AES-GCM)`

- *Why?* Encryption transforms plaintext into high-entropy pseudo-random bytes. High entropy data is mathematically incompressible. We **must** compress the predictable serialized struct byte-stream *first* to achieve massive storage savings, then encrypt the minimized output. 
- *Algorithm*: `zstd` (Zstandard) offers an optimal balance of fast compression and highly battery-efficient decompression for mobile usage, far outperforming standard Deflate/Gzip.

---

## 7. Nonce Strategy

**Decision: Cryptographically Secure Pseudo-Random Number Generator (CSPRNG).**
- Each encryption invocation generates a completely fresh, random 12-byte Nonce via `OsRng`.
- **Why not Chunk-Derived (e.g., hashing contents)?** If a chunk or metadata file is ever modified (e.g. metadata rename), reusing a Nonce with the same key is a catastrophic failure in AES-GCM (exposing the XOR keystream). By strictly utilizing random nonces, we guarantee an effectively zero collision probability across the Data Encryption Key's (DEK) lifecycle.
- The Nonce is safely stored in plaintext in the Encrypted Header Layout (Bytes 6-21).

---

## 8. Recovery Key Format

**Decision: Human-Readable 32-Character String with Checksum.**
We prioritize a format users can reliably write down on physical paper without transcription errors.

- **Format**: `XXXX-XXXX-XXXX-XXXX-XXXX-XXXX-XXXX-XXXX`
- **Alphabet**: Base32 (e.g., RFC 4648 without padding or Crockford's), specifically omitting visually ambiguous characters (e.g., '0' vs 'O', '1' vs 'I').
- **Checksum**: The final chunk (e.g., the last 4 characters) acts as a localized checksum of the preceding payload. 
- **UX Requirement**: Before executing key-recovery, the UI must dynamically validate the Recovery Key string checksum offline to instantly alert the user of typos without needing a failed decryption attempt.

---

**Final Verdict**: With these decisions permanently frozen, the Cybertrail crypto-format guarantees files written to WebDAV are fully immutable, versioned, space-optimized via zstd+bincode, inherently resistant to context-swapping via AAD, and structurally unambiguous for future multi-platform clients.
