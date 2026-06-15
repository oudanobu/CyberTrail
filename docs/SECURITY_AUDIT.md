# CyberTrail Security Audit & Threat Validation
Version: 1.0
Status: Frozen

---

## 1. Nonce Reuse Audit

**The Risk**: We are using AES-256-GCM (which requires a 96-bit / 12-byte Nonce). According to the Birthday Paradox, if a system uses purely random nonces (`OsRng`), the risk of a nonce collision (giving an attacker the XOR keystream) becomes statistically concerning if we encrypt billions of files under the exact same key.

**Evaluation**: 
- A user hiking 8 hours a day, 365 days a year, at 1-second intervals generates ~10.5 million TrackPoints/year. 
- Chunked into blocks of 500 points, that's roughly ~21,000 chunks per year.
- Over a 50-year lifetime, that is ~1 million chunks. 
- The probability of a 96-bit random nonce collision after 1 million generations is infinitesimally small (approx 6.3 × 10^-18). 

**Conclusion**: `OsRng` for 12-byte nonces is mathematically safe for the biological lifespan of a human user generating GPS data. No strict counter or ChunkId manipulation is required in the Nonce space, keeping the implementation simple and stateless across devices.

---

## 2. AAD Coverage Audit

**The Risk**: An attacker shuffles encrypted files around to corrupt the target databases or swap waypoints (e.g., replacing a "Danger" waypoint with a "Safe" waypoint from another hike).

**Validation**: The previously established AAD strategy binds the `track_id` and `chunk_id`.
**Enhancement**: We officially mandate including the `ObjectType` (e.g., `TRACK`, `WAYPOINT`, `CHUNK`, `ATTACHMENT`) inside the AAD string to prevent cross-type swapping. 
**Final AAD Structure**: `b"{object_type}|{parent_id_or_track_id}|{item_id_or_chunk_index}"`. We do *not* include `DeviceId` because different devices must transparently collaborate and append chunks under the same Track logic.

---

## 3. Recovery Key Attack Surface

**The Risk**: The Recovery Key is perceived by users as a secondary "backup code," but in reality, it is a master unlock switch.
**Validation**: The Recovery Key wraps the DEK exactly the same way the Key-Encryption Key (KEK) does. 
**Warning Designation**: The Recovery Key has **100% decryption capability**. It is effectively a secondary master password. Documentation and UI must warn users to store it securely (e.g., in a physical safe or offline password manager), as anyone possessing the Recovery Key can fully bypass the user's password.

---

## 4. Metadata Leakage Review

**The Risk**: We are leaving WebDAV directory structures (UUIDs), file sizes, and modification timestamps in plaintext.
**Analysis**: 
- *Update Frequency*: Tells an attacker *when* the user is hiking (or at least when they have internet access).
- *File Sizes*: Tells an attacker roughly how long the hike was (by measuring the total bytes of chunk payloads).
**Decision**: **Accepted Risk**. Hiding file sizes requires enforcing fixed rigid padding blocks, wasting mobile bandwidth and storage. Hiding update frequency requires generating constant dummy traffic, annihilating battery life. For a hiking application, hiding the exact traversal route and waypoints (payload encryption) is paramount; obscuring the time of the sync is an acceptable trade-off.

---

## 5. Compression Oracle Review (CRIME / BREACH)

**The Risk**: We compress data before encrypting (`Serialize -> zstd -> AES-GCM`). If an attacker can inject chosen plaintext into the data stream and observe the resulting compressed ciphertext size over the network, they can slowly decipher secrets.
**Validation**: Cybertrail is a local-first GPS recorder. There is no web server endpoint or open socket where an attacker can repeatedly inject chosen text (like a web cookie) into a given Track's payload and observe the WebDAV sync output. The attacker model does not fit the execution environment. 
**Decision**: The `Compress then Encrypt` pipeline is safe from CRIME/BREACH in this localized offline-first context.

---

## 6. Password Reset Simulation

**Scenario**: User changes their password.
**Outcome**: 
1. The system utilizes the current internal DEK (which remains unchanged).
2. Derives a new KEK from the new password.
3. Wraps the DEK with the new KEK.
4. Overwrites the `keystore.enc` file on the device and syncs it to WebDAV.
**Validation**: All gigabytes of `chunk_xxxxx.bin.enc` and `photo.jpg.enc` files are untouched. The system works as designed. Zero risk of data corruption during password changes. 

---

## 7. Multi-Device Enrollment

**Scenario**: A user installs CyberTrail on a new Tablet. 
**Flow**:
1. Tablet connects to WebDAV and downloads `keystore.enc` (and nothing else yet).
2. App prompts user for "Password" OR "Recovery Key".
3. User enters Password. KEK is derived.
4. DEK is successfully unwrapped.
5. Tablet initiates the Sync State Machine, downloading and decrypting `metadata.json.enc` rapidly.
**Validation**: Secure, entirely offline-capable client-side provisioning without relying on a central authority or HSM key exchange.

---

## 8. Disaster Matrix

| Threat / Event | Mitigation / Result | Status |
| :--- | :--- | :--- |
| **Forgot Password** | User utilizes the Recovery Key to unwrap DEK and set a new password. | Recoverable ✅ |
| **Lost Recovery Key** | User can still utilize their Password. | Recoverable ✅ |
| **Forgot Password AND Lost Recovery Key** | Mathematically impossible to unwrap DEK. | **Data Permanently Lost ❌** |
| **WebDAV Server Compromised** | All payloads and directories are AES-256-GCM encrypted. | Data Safe 🔒 |
| **Android Root: SQLite DB Stolen** | DEK is locally wrapped. Needs Password to decrypt payload. | Data Safe 🔒 |

*(Note: Data Permanently Lost is the intended cryptographic guarantee. If we could bypass it, so could an attacker).*

---

## 9. Algorithm Justification: Why AES-256-GCM for V1?

Many modern projects prefer `XChaCha20-Poly1305` because its 192-bit nonce space renders nonce-reuse technically impossible even at astronomical scales. 

**Why we chose AES-256-GCM for CyberTrail V1:**
1. **Hardware Acceleration**: Modern Android ARMv8 processors include dedicated Cryptographic Extensions (AES-NI equivalent). AES-256-GCM executes significantly faster and consumes drastically less battery life than a pure-software XChaChaPoly implementation on mobile devices.
2. **Chunk Management**: Because Cybertrail chunks data naturally into small, discrete files (e.g., 500 points per chunk), we are generating individual files, not a continuous endless stream. The 96-bit AES nonce space is perfectly safe for our maximum theoretical volume (as calculated in Section 1).

**Why cipher agility is retained:**
If the app is ported to older hardware, low-end wearables without AES hardware extensions, or if we decide to implement continuous socket-based streaming in the future, we can seamlessly flip the `CipherSuite` enum to `0x02` (XChaCha20-Poly1305) without breaking reverse-compatibility.

---

**Final Recommendation**: The Cryptographic Architecture is mathematically sound, performance-optimized, and resilient to standard local-first threat vectors. We are officially cleared for **Phase 8.3C (Crypto Implementation)**.
