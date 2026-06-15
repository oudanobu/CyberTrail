# CyberTrail Workspace Verification Plan
Version: 1.0
Phase: 11.0 (Milestone Alpha Readiness)
Status: Frozen

---

## 1. Cargo Workspace Audit

Before executing actual builds, the `Cargo.toml` at the project root and within all sub-crates must strictly define the workspace boundaries.

**Workspace Members Configuration:**
```toml
[workspace]
members = [
    "crates/common",
    "crates/domain",
    "crates/application",
    "crates/infrastructure",
    "crates/database",
    "crates/sensors",
    "crates/tracking",
    "crates/crypto",
    "crates/sync",
    "crates/ffi"
]
resolver = "2"
```
**Verification Gates:**
- [ ] Every crate must utilize the workspace resolver (`resolver = "2"`).
- [ ] Shared dependencies (e.g., `tokio`, `serde`, `thiserror`) must be hoisted to `[workspace.dependencies]` to ensure version parity across all crates and drastically reduce compilation time.

---

## 2. Dependency Graph Audit (Anti-Cycle Enforcement)

A Directed Acyclic Graph (DAG) must be strictly maintained. Rust will halt compilation if a cyclic dependency exists, but architectural cycles (where abstractions leak) are equally dangerous.

**The Golden Dependency Matrix:**
- `domain`: Depends on `common`. **Forbidden** to depend on anything else.
- `application`: Depends on `domain`, `common`.
- `database` / `infrastructure`: Depends on `domain` (implementing traits), `application` (DTO mapping), `crypto` (for SQLite DEK access).
- `sync`: Depends on `domain`, `crypto`.
- `ffi`: The ultimate consumer. Depends on `application`, `infrastructure`, `sync`.

**Red Flag Checks:**
- [ ] Verify `domain` does NOT import `database` or `infrastructure`.
- [ ] Verify `application` does NOT import `ffi` or `Android` specifics.
- [ ] Verify `crypto` remains completely agnostic of `database` models (it should only ingest byte slices or generic serialize bounds).

---

## 3. UniFFI Exposure Audit

Bridging memory between Rust's strict lifecycle policies and Kotlin's Garbage Collector requires granular control.

**Permitted Exposures (Exported via UniFFI):**
- Application Commands (e.g., `start_recording`, `pause_recording`, `add_waypoint`).
- Primitives and Flat DTOs (e.g., `TrackSummaryDTO`, `SyncStatusDTO`).
- Parallel Array Buffers for MapLibre/Compose rendering (e.g., `ChunkedPolylineDTO { lats: Vec<f64>, lons: Vec<f64>, times: Vec<i64> }`).

**Forbidden Exposures (Strictly hidden from Kotlin):**
- Domain Entities (`Track`, `TrackPoint`, `ConflictRecord`).
- Rust standard library containers spanning the boundary (`HashMap`, `Arc<Mutex<T>>`).
- Raw Cryptographic Keys or `Aes256Gcm` instances.

*Reasoning: Passing Domain Entities to Kotlin tempts UI developers to mutate state outside of Rust's business logic invariants, destroying the "Rust is the Source of Truth" mandate.*

---

## 4. Milestone Alpha Checklist

This is the exact sequence of validation commands that must execute cleanly on the CI server and developer workstations before any UI code is written.

### Phase 1: Static Analysis
- [ ] **`cargo fmt --all -- --check`**
  - Confirms uniform codebase formatting.
- [ ] **`cargo clippy --workspace --all-targets -- -D warnings`**
  - Confirms zero instances of `.clone()` abuse, nested `Arc<Mutex<_>>` anti-patterns, and unhandled `Result` bindings.

### Phase 2: Compilation & Logic Validation
- [ ] **`cargo check --workspace`**
  - Proves the DAG, trait implementations, async boundaries, and lifetimes are mathematically sound.
- [ ] **`cargo test --workspace`**
  - Executes isolated Domain rules, SQLite batch insertion speeds (< 50ms), and Crypto logic round-trips.

### Phase 3: Android Milestone Alpha
Once Phase 2 passes, we compile the UniFFI bindings and build the Android APK.
- [ ] **Android Build:** `Uniffi bindgen` success & Gradle sync.
- [ ] **Emulator Boot:** SQLite initialization succeeds. WebDAV mounts correctly.
- [ ] **Physical Device Validation (The 1-Hour Test):**
  - Run app on a physical Android device.
  - Turn off screen, walk/drive for 1 continuous hour.
  - Verify SQLite `track_points` volume.
  - Verify battery consumption (< 2%).
  - Verify 0 instances of memory `OOM` crashes or Foreground Service drops.

---
**Verdict:**
CyberTrail is fully structured and architecturally sound. The focus now shifts entirely measured execution. Milestone Alpha strictly prioritizes compilation, integration testing, and the 1-hour physical device integrity test over visual polish or additional complex features.
