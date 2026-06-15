# CyberTrail Performance Test Plan
Version: 1.0
Status: Frozen

---

## Phase 1: SQLite Scale Testing

The database engine must remain responsive at scale without bogging down UI elements or risking ANRs (Application Not Responding) timeouts on Android.

| Test Case | Volume | Expected Outcome | Pass Criteria |
| :--- | :--- | :--- | :--- |
| **Case 1.1** | 100,000 TrackPoints | Moderate Hike (Days) | Insert batch < 50ms. Full stream load < 200ms. |
| **Case 1.2** | 1,000,000 TrackPoints | Through-Hike (Months) | Insert batch < 50ms. Paginated stream load < 500ms. |
| **Case 1.3** | 5,000,000 TrackPoints | Lifetime Archive | `COUNT(*)` via index < 100ms. Stream start < 500ms. |
| **Case 1.4** | 100,000 Waypoints | FTS5 Stress | FTS5 text search query returning top 20 results in < 250ms. |

---

## Phase 2: Export Benchmarks

Data portability is a core Local-First promise. Serializing massive SQLite queries into structured text formats is CPU and memory intensive.

| Format | Target Volume | Max Memory Spike | Maximum Execution Time |
| :--- | :--- | :--- | :--- |
| **GPX 1.1** | 100k Points | < 50 MB | < 2 seconds |
| **KML** | 100k Points | < 60 MB | < 3 seconds (Includes style tags) |
| **GeoJSON** | 100k Points | < 80 MB | < 3 seconds |

*Testing Protocol:* Export operations must run inside an isolated background service/thread. The garbage collector graph heavily spikes during JSON/XML serialization. We must profile memory allocations to ensure flat or chunk-based streaming writers are employed rather than allocating a 50MB string entirely in RAM.

---

## Phase 3: Sync & Crypto Throughput

Encrypting, wrapping, compressing, and transmitting binary data reliably.

| Dataset Size | Scenario | Expected Behavior | Allowable Overhead |
| :--- | :--- | :--- | :--- |
| **100 MB** | Standard Archive Sync | Seamless background sync. | < 5% battery usage. AES-GCM time < 2 sec. |
| **500 MB** | First-Time Setup Sync | Heavy network + Crypto load. | Memory constrained < 100MB via chunked streaming. |
| **1 GB** | Total Disaster Recovery | Prolonged steady state download/decrypt. | Stable CPU thermals. Max memory < 120MB. |

*Testing Protocol:* Throttled network simulation (3G / 1 Mbps). Verify that TCP drops don't corrupt WebDAV chunks and the sync state machine properly resumes uploading `points_00004.bin` instead of resetting the whole 1GB block.

---

## Phase 4: Hardware Constraints (Android Tiering)

CyberTrail is targeting aging/budget hardware in outdoor environments where screen brightness is maximized.

| Tier | RAM | CPU | Evaluation Metric |
| :--- | :--- | :--- | :--- |
| **Low-End** | 4GB | Older Snapdragon 4xx/6xx | App avoids LMK eviction. Total native RAM < 120MB. Maps do not stutter. |
| **Mid-Range** | 6GB | MediaTek / SD 7xx | Smooth 60fps scrolling on Track history. Fast FTS5 waypoint search. |
| **High-End** | 8GB+ | Modern Flagships | Instant AES-GCM Decrypts. Background sync imperceptible. |

---

## Phase 5: Endurance & Logging Reliability

Trackers often fail silently at hour 6. We must execute physical / emulator trace tests.

| Duration | Scenario | Pass Criteria |
| :--- | :--- | :--- |
| **8 Hours** | Day Hike | Zero gaps > 1 minute (unless stationary). Wakelock holds correctly. |
| **12 Hours** | Ultra-Marathon | Buffer queue flushes consistently. SQLite `WAL` file doesn't bloat. |
| **24 Hours** | Continuous Survival | App safely rotates files. Deep sleep Doze mode overridden securely. Battery drain strictly < 1.5% per hour from the app itself (excluding screen-on time). |

---

**Approval:** This document freezes the engineering benchmarks for V1 release. Any pull request directly violating these metrics shall be rejected.
