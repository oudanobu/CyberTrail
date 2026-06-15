# CyberTrail Test Audit Plan
Version: 1.0
Phase: 10.1

## 1. Domain Tests (Unit)
The `domain` crate must execute purely in-memory with zero async complexity or infrastructure dependencies.

**Target Suites:**
- `track_tests.rs`: Name length limits, whitespace trimming, time inversion rejections.
- `track_point_tests.rs`: Immutable instantiation, distance calculation between two points (Haversine/Great-Circle formula accuracy to within 0.1%).
- `revision_tests.rs`: Strict monotonic increment validation.
- `conflict_record_tests.rs`: LWW timestamp tie-breaking rules.

## 2. SQLite Integration Tests (Integration)
The `database` and `infrastructure` crates require disk-based validation. Tests use `tempfile::NamedTempFile` to create ephemeral, isolated SQLite `.db` instances per test.

**Target Suites:**
- `test_sqlite_batch_insert()`: Validate 100,000 `TrackPoint` insertions inside a `BEGIN IMMEDIATE` block. Ensure execution time is < 500ms.
- `test_sqlite_wal_persistence()`: Cause a forced panic after `COMMIT` but before graceful shutdown to guarantee WAL flushing protects the data.
- `test_track_tombstone()`: Save a `Track`, trigger `delete(id)`, and query it. Ensure `is_deleted = true` is retrieved successfully and `track_points` remain intact (deferred payload purge).

## 3. Crypto Provider Tests (Unit)
The `crypto` crate validation ensures mathematically sound encryption-decryption loops.

**Target Suites:**
- `test_kek_derivation()`: Ensure Argon2 output with identical passwords and salt string consistently produces the identical 32-byte key.
- `test_aes_gcm_roundtrip()`: Encrypt a 500-point mock chunk, decrypt it, and verify byte-for-byte fidelity.
- `test_aad_swapping_attack()`: Encrypt Chunk `A` with AAD `Track1_Chunk1`. Attempt to decrypt it with AAD `Track1_Chunk2`. The `Aes256GcmProvider::decrypt` MUST return `DecryptionError`.
- `test_recovery_key_format()`: Generate key, alter 1 char, test `decode()`. MUST return `InvalidRecoveryKey`.

## 4. Sync State Machine Tests (Mocked Async)
Mock the `WebDAVClient` using `async-trait`.

**Target Suites:**
- `test_sync_differential()`: Provide local Revision `8`, Mock Remote `10`. Ensure the State Machine extracts elements 9 and 10 and transitions to `DOWNLOADING`.
- `test_sync_retry_backoff()`: Return `500 Internal Server Error` 3 times from the mock WebDAV client. Ensure the State Machine waits exponentially and succeeds on the 4th try without data loss.

## Future Protocol
Once `cargo test` confirms these pass natively, CyberTrail is technically certified "Stable" at the Rust Boundary.
