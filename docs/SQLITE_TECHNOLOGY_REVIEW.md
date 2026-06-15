# CyberTrail SQLite Technology Review
Version: 1.0
Status: Frozen

---

## 1. Context & Constraints
CyberTrail is an offline-first hiking application running locally on Android 13+ devices (ARM64) and Debian, with a strict memory limit (`<100MB` idle) and performance targets geared toward minimal battery consumption.

**Key Requirements**:
*   **Target Environments**: Android 13+ (ARM64), Debian.
*   **Workload**: 1,000,000+ TrackPoints (rapid appends via batching or streams), complex FTS5 search queries, WAL (Write-Ahead Logging) mode.
*   **Cross-Compilation**: Must cleanly build via Android NDK / `cargo-ndk`.
*   **Concurrency**: Local embedded database, predominantly single-writer/multi-reader (WAL).

---

## 2. Candidates Analysis

### Candidate A: `rusqlite`
A set of synchronous Rust bindings to the SQLite C API.

*   **Performance**: Extremely fast. Zero-cost abstractions over the C API.
*   **Memory Footprint**: Minimal. Operates safely within the constraints of low-memory devices.
*   **Cross-Compilation**: Excellent. Can use the `bundled` feature to compile SQLite directly from C source via `cc` crate, entirely eliminating system dependency mismatches on Android or Debian.
*   **Concurrency**: Synchronous. Requires delegation to `tokio::task::spawn_blocking()` to prevent stalling the async runtime. Given SQLite's inherent file-locking mechanism, async I/O offers diminishing returns anyway.
*   **FTS5 & WAL**: Full native support via standard PRAGMAs.

### Candidate B: `sqlx` (SQLite)
An async, pure Rust/C-hybrid SQL crate featuring compile-time verified queries.

*   **Performance**: Good, but brings the overhead of an async executor and connection pooling designed more for network databases (PostgreSQL/MySQL).
*   **Memory Footprint**: Heavier compilation footprint due to macros. Runtime footprint is slightly larger due to async state machines and connection pool overhead.
*   **Cross-Compilation**: Good, but compile-time query verification requires a local `DATABASE_URL` present during the Android CI/CD build, adding friction to the build pipeline.
*   **Concurrency**: Async out of the box, but SQLite itself serializes writes. The async benefits are largely theoretical for local files while the overhead is very real.

### Candidate C: `libsql`
A modern fork of SQLite developed by Turso, optimized for edge/cloud synchronization.

*   **Performance**: High, but brings networking capabilities (HTTP/WebSockets) that CyberTrail explicitly bans.
*   **Memory Footprint**: Larger than vanilla SQLite due to the included replication and networking stack.
*   **Cross-Compilation**: Achievable, but introduces unnecessary dependencies.
*   **Verdict**: Total overkill. CyberTrail relies on WebDAV for sync, not a distributed SQL edge network.

### Candidate D: Raw SQLite C API (`libsqlite3-sys`)
Writing raw `unsafe` Rust blocks to interact with the C headers.

*   **Performance**: Maximum possible.
*   **Memory Footprint**: Absolute minimum.
*   **Cross-Compilation**: Same as `rusqlite` (bundled).
*   **Verdict**: Fails the Correctness and Security First mandate. Managing raw pointers, SQLite statement lifecycles, and memory boundaries across JNI and Rust is a recipe for catastrophic memory leaks and segfaults.

---

## 3. Asynchronous execution in a Synchronous World

SQLite limits concurrent writes at the database file level. Wrapping a synchronous call in an async API (like `sqlx` does internally for SQLite) does not magically make disk I/O concurrent. 

In CyberTrail:
1.  **Writes**: Only one thread can write at a time anyway.
2.  **Reads**: WAL mode allows concurrent readers, but disk access time on Android NVMe/UFS storage is consistently in the microseconds.

Using `rusqlite` combined with Tokio's `spawn_blocking` provides the exact same non-blocking guarantees to the UI thread as an async driver, but with significantly less framework overhead, strict predictability, and tighter memory control.

---

## 4. Final Verification & Recommendation

**Recommendation: Proceed with `rusqlite` (+ `tokio::task::spawn_blocking`).**

**Implementation Contract**:
1.  **Dependency**: `rusqlite = { version = "0.31", features = ["bundled", "chrono", "uuid"] }`
2.  **Runtime**: All Repository methods returning `Future` (`async_trait`) will internally offload their `rusqlite` execution to `tokio::task::spawn_blocking` to prevent blocking the Tokio reactor.
3.  **Connection Management**: Use an `r2d2` or `deadpool-sqlite` pool, maintaining a strict 1-writer / N-reader limit to fully leverage WAL mode while avoiding `SQLITE_BUSY` errors.
4.  **NDK**: Use the `bundled` feature to let `cc` build the SQLite engine targeting `aarch64-linux-android`.
