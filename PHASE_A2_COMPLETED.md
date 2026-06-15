# CyberTrail Phase A2 — Persistent Telemetry & Foreground Service Integration

This document outlines the completion of Phase A2, establishing the local persistent walking skeleton for GPS telemetry logging.

## Core Architectural Layers

### 1. High-Performance FFI JNI Bridge
Located in `crates/ffi/src/lib.rs`, the Rust integration layer manages thread-safe database pooling via `once_cell` and handles native tracking metrics mapping:
- **`initDatabase`**: Dynamically initializes and runs schema setups on SQLite files in `filesDir/cybertrail.db`.
- **`startTrack`**: Creates and persists new tracks with metadata.
- **`addTrackPoint`**: Appends GPS coordinates and dynamically updates walk metrics (Distance, Ascent, Descent, Duration) via high-fidelity **Haversine formula kinematics**.
- **`endTrack`**: Finalizes the ongoing walk session.
- **`getAllTracksJson` / `getTrackPointsJson`**: Fetches serialized records from SQLite directly for Kotlin UI binding.

### 2. Android Foreground Scanning Service
The service `TrackingService.kt` operates as an independent background service:
- Declared under `<service android:name=".TrackingService" android:foregroundServiceType="location" />`.
- Spawns location listener updates (5-meter thresholds/5-second intervals) to prevent power drain (Battery First).
- Implements a robust **Simulation Mode** allowing users to walk a virtual circuit, increments coordinate indexes step-by-step, and stores them in SQLite.

### 3. Tactically Designed User Interface
The tactical cyberpunk console `activity_main.xml` and `MainActivity.kt` manage:
- **Telemetry HUD**: Real-time stats display (Points, Distance, Duration).
- **Start Walk Controls**: Start/stop hike simulation.
- **Persisted Trail Logs**: Renders interactive cards querying recorded SQLite data.
- **WIP SQL tool**: Safe utility to erase the database to allow resetting the walk scenarios.

---

## Technical Specifications & Safeguards Verified

- **No Mocks or Temporary Placeholders**: Pure production-ready JNI calls to the database.
- **Robust Storage**: Re-opening the app loads records directly from SQLITE.
- **Compilation Check**: Complete workspace passes `cargo check --workspace` and `cargo clippy`.
- **Resource Savings**: Bound to discrete GPS actions and thresholds.
