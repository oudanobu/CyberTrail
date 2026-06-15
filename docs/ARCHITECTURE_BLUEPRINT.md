# CyberTrail Architecture Blueprint
Version: 1.0

*Note: Adapted to CyberTrail's Offline Tactical Hiking System structure based on the provided architectural prompt methodology.*

## 1. System Layers (Clean Architecture)

### UI Layer (Android Jetpack Compose)
- **Tracking UI**: Real-time tactical HUD, live metric readouts, active route rendering.
- **History UI**: List of historical tracks, track statistics, height profile charts.
- **Settings UI**: Application and tracking configurations (sampling rates, units).

### Application Layer (Use Cases)
- **Tracking Engine (`tracking`, `altitude`)**: Coordinates sensor data ingestion, applies Kalman filtering, executes distance-based sampling, and manages altitude fusion.
- **Track UseCases**: `StartTrackingUseCase`, `StopTrackingUseCase`, `PauseTrackingUseCase`.
- **History UseCases**: `GetTrackHistoryUseCase`, `GetTrackStatsUseCase`.
- **Settings UseCases**: `UpdateSettingsUseCase`.

### Domain Layer (Core Business Logic)
- **Entities**: `Track`, `TrackPoint`, `AltitudePoint`, `Waypoint`, `Anchor`.
- **Value Objects**: `Latitude`, `Longitude`, `Altitude`, `Distance`, `Speed`, `Heading`.
- **Repository Traits**: `TrackRepository`, `SettingsRepository`, `WaypointRepository`.
- **Domain Events**: `TrackStarted`, `TrackPointRecorded`, `AltitudeThresholdCrossed`.

### Infrastructure Layer (Adapters)
- **Database (`SQLite/WAL`)**: Implements `TrackRepository` and `SettingsRepository`.
- **Sensors (`GPS`, `Barometer`)**: Hardware bridging to emit unified `SensorEvent`s.
- **Rendering**: Canvas/OpenGL engine implementation for HUD layout calculations.
- **FFI**: The C-ABI boundary bridging Rust and Android JVM.

---

## 2. Module Relations & Trait Boundaries

```text
    [ Android UI (Kotlin/Compose) ]
                   │
                   ▼ (JNI Boundary)
             [ FFI Crate ]
                   │
                   ▼
      [ Application (Use Cases) ] ──▶ [ Domain (Traits/Entities) ]
                   │                               ▲
                   ▼                               │ (Implements Trait)
        [ Infrastructure ] ────────────────────────┘
        ├─ Database Crate (SQLite)
        ├─ Sensors Crate (GPS, Baro)
        └─ Rendering Crate
```

---

## 3. Data Flows

### A. Tracking & Persistence Flow
1. **Sensors** trigger OS callbacks -> mapped to `SensorEvent`.
2. Passed via **FFI** to **Application (Tracking Engine)**.
3. **Tracking Engine** applies Distance Check and Kalman Filter.
4. If valid, constructs a `TrackPoint` (Domain Entity).
5. Calls `TrackRepository::insert_track_points` (Trait).
6. **Infrastructure (SQLite)** writes to WAL using Prepared Statements.

### B. Altitude Fusion Flow
1. **GPS** emits absolute altitude.
2. **Barometer** emits relative pressure changes.
3. **Application (Altitude Engine)** fuses inputs to generate `AltitudePoint`.
4. Saves to database and dispatches `DomainEvent::SignificantAltitudeChange`.

### C. Tactical Rendering Flow
1. Tracking Engine emits `DomainEvent::TrackPointRecorded`.
2. **Rendering Engine** receives point, updates internal geometry matrices.
3. Emits low-level draw commands back to **Android UI (Canvas)**.

### D. History Search Flow
1. User opens History Panel.
2. **UI** calls `GetTrackHistoryUseCase`.
3. UseCase calls `TrackRepository::get_all_tracks`.
4. **SQLite** executes indexed `SELECT` and returns `Vec<Track>`.
5. FFI marshals data back to Kotlin `data class`.

---

## 4. Workspace Directory Structure

```text
cybertrail/
├── Cargo.toml
├── crates/
│   ├── common/         # Shared utilities (Result, Error, GeoMath)
│   ├── domain/         # Entities, Value Objects, Traits (0% external deps)
│   ├── application/    # Use Cases & Process Coordination
│   ├── database/       # SQLite implementation of Repositories (WAL, Migrations)
│   ├── sensors/        # Android hardware sensor definitions
│   ├── tracking/       # Distance filtering, logic & stats
│   ├── altitude/       # Barometric/GPS Altitude fusion engine
│   ├── navigation/     # Heading & Azimuth path math
│   ├── rendering/      # Graphical HUD generation layer
│   ├── infrastructure/ # External system adapters
│   └── ffi/            # Android JNI / C-ABI bridge
└── android/            # Android Studio Native Project (Kotlin/Compose)
```
