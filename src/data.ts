/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import { RuleSection, CrateNode, CodeTemplate, TrackPoint } from "./types";

export const CONSTITUTION_RULES: RuleSection[] = [
  {
    id: "rules-1",
    title: "1. Workspace Architecture",
    category: "Structure",
    summary: "The project must adopt a strict Cargo Workspace separating domains into tactical modules.",
    details: "Crates must be separated into common, domain, application, database, sensors, tracking, altitude, navigation, ar_anchor, rendering, infrastructure, and ffi. Single-crate monoliths are forbidden.",
    forbidden: ["Single-crate monolithic projects", "Mixing AR logic with basic tracking operations"],
    correctExample: "cybertrail/\n├── crates/\n│   ├── domain/\n│   ├── application/\n│   └── sensors/",
    incorrectExample: "cybertrail/\n└── src/\n    ├── main.rs\n    └── all_gps_and_ui_logic.rs"
  },
  {
    id: "rules-2",
    title: "2. Dependency Law",
    category: "Dependency",
    summary: "Dependencies must flow unidirectional downwards: UI -> Application -> Domain <- Infrastructure.",
    details: "Strict blocking of UI importing SQL drivers, or Domain modules being aware of Android environments.",
    forbidden: ["ui -> database", "ui -> sql", "domain -> sqlite", "domain -> android"],
    correctExample: "// database Cargo.toml\n[dependencies]\ndomain = { path = \"../domain\" }",
    incorrectExample: "// domain Cargo.toml\n[dependencies]\nandroid_logger = \"...\""
  },
  {
    id: "rules-3",
    title: "3. Domain Purity Constraint",
    category: "Structure",
    summary: "Domain houses pure business models and Traits without any infrastructure dependencies.",
    details: "Contains entities like Track, TrackPoint, Anchor, AltitudePoint. Defines interfaces like TrackRepository without tying to SQLite.",
    forbidden: ["Importing sqlx in Domain", "Importing FFI structures into domain core"],
    correctExample: "pub struct TrackPoint {\n    pub lat: Latitude,\n    pub lon: Longitude,\n}",
    incorrectExample: "pub struct TrackPoint {\n    pub db_row_id: i64, // ❌ DB abstraction leak\n}"
  },
  {
    id: "rules-4",
    title: "4. Application Coordinator",
    category: "Structure",
    summary: "Application handles UseCases and orchestration, not direct underlying data caching.",
    details: "Expected Use Cases: StartTrackingUseCase, StopTrackingUseCase. Connects sensors output with domain persistence.",
    forbidden: ["Application manually managing SQLite connection strings without Repositories"],
    correctExample: "pub struct StartTrackingUseCase { tracker: TrackEngine }",
    incorrectExample: "pub struct TrackingManager { db_pool: SqlitePool }"
  },
  {
    id: "rules-5",
    title: "5. Sensor & Altitude Engines",
    category: "State",
    summary: "Hardware signals must be decoupled into unified Event streams.",
    details: "Sensors crate outputs generic SensorEvent::Gps(...), Motion(...). Altitude crate fuses GPS + BARO to smooth VSpeed.",
    forbidden: ["Directly embedding Android LocationManager inside domain calculation functions"],
    correctExample: "pub enum SensorEvent { Gps(LocationData), Pressure(f32) }",
    incorrectExample: "fn handle_android_intent(intent: &Intent) { ... }"
  },
  {
    id: "rules-6",
    title: "6. Cyberpunk Rendering Strategy",
    category: "Structure",
    summary: "Tactical UI relies on raw Canvas/OpenGL rather than commercial maps.",
    details: "Display track polylines, altitude charts, and scanning animations purely offline. Google Map integrations are forbidden as the main tracker view.",
    forbidden: ["Embedding Google Maps SDK as the main offline tracker view"],
    correctExample: "render_trajectory_line(canvas: &mut Canvas, track: &Track)",
    incorrectExample: "google_map.addPolyline(...)"
  },
  {
    id: "rules-7",
    title: "7. SQLite Hardening",
    category: "Performance",
    summary: "The Database must employ WAL, Foreign Keys, and Prepared Statements.",
    details: "To ensure high offline write-throughput for GPS points, PRAGMA journal_mode=WAL is required.",
    forbidden: ["String interpolated SQLite queries", "Disabled foreign keys in tracks-to-points schemas"],
    correctExample: "sqlx::query(\"INSERT INTO track_points (id) VALUES (?)\").bind(id)",
    incorrectExample: "db.execute(format!(\"INSERT INTO {} VALUES ({})\", table, id))"
  },
  {
    id: "rules-8",
    title: "8. FFI Boundary Strictness",
    category: "Contract",
    summary: "Android ↔ Rust occurs exclusively over a strict set of C-ABI endpoints.",
    details: "Use #[repr(C)] for data transfers. The ffi crate guarantees safety. Android views never read Rust internal structures.",
    forbidden: ["Leaking Box<T> directly to JVM without extern \"C\" lifecycle managers"],
    correctExample: "#[no_mangle]\npub extern \"C\" fn start_tracking() -> i32 { 0 }",
    incorrectExample: "pub fn start_tracking_internal() -> Result<...> { }"
  },
  {
    id: "rules-9",
    title: "9. MVP Performance Constraints",
    category: "Performance",
    summary: "The footprint must remain under 20MB APK size, 100MB RAM, and 1s boot time.",
    details: "Avoid compiling ar_anchor (ARCore) logic in Phase 1 MVP. Rely on tight memory allocations and efficient Rust binaries.",
    forbidden: ["Including heavy AR dependencies in the Phase 1 build target"],
    correctExample: "[dependencies]\n# AR engine omitted for MVP size",
    incorrectExample: "[dependencies]\narcore_bindings = \"1.0\" # Bloats the application"
  }
];

export const CRATE_NODES: CrateNode[] = [
  {
    id: "common",
    label: "common",
    description: "Shared types like Math utilities, Time bindings, Uuid. Universal library.",
    dependencies: [],
    forbiddenDeps: ["domain", "application", "ui"],
    cargoPath: "crates/common"
  },
  {
    id: "domain",
    label: "domain",
    description: "Contains pure tracking entities, coordinates, altitudes, and Repository interfaces.",
    dependencies: ["common"],
    forbiddenDeps: ["database", "sensors", "rendering", "infrastructure", "ui", "ffi"],
    cargoPath: "crates/domain"
  },
  {
    id: "application",
    label: "application",
    description: "Coordinates Start/Stop commands and routes sensor streams.",
    dependencies: ["domain", "common"],
    forbiddenDeps: ["database", "ui", "ffi"],
    cargoPath: "crates/application"
  },
  {
    id: "database",
    label: "database",
    description: "SQLite tracking persistence engine with WAL enforcement.",
    dependencies: ["domain", "common"],
    forbiddenDeps: ["ui", "ffi", "rendering"],
    cargoPath: "crates/database"
  },
  {
    id: "sensors",
    label: "sensors",
    description: "Bridges underlying OS hardware outputs (GPS/Barometer) into agnostic SensorEvents.",
    dependencies: ["common"],
    forbiddenDeps: ["ui", "domain"],
    cargoPath: "crates/sensors"
  },
  {
    id: "tracking",
    label: "tracking",
    description: "Core algorithms for track decimation, distance filtering, and point compression.",
    dependencies: ["domain", "common"],
    forbiddenDeps: ["database", "ui"],
    cargoPath: "crates/tracking"
  },
  {
    id: "altitude",
    label: "altitude",
    description: "Kalman/Fusion algorithms merging GPS Z-coords with Barometric pressure arrays.",
    dependencies: ["domain", "common"],
    forbiddenDeps: ["ui", "database"],
    cargoPath: "crates/altitude"
  },
  {
    id: "rendering",
    label: "rendering",
    description: "Standalone OpenGL/Canvas plotting for local offline maps.",
    dependencies: ["domain", "common"],
    forbiddenDeps: ["database", "ui"],
    cargoPath: "crates/rendering"
  },
  {
    id: "infrastructure",
    label: "infrastructure",
    description: "Binds the Database, Sensors, and Renderers into the concrete backend traits.",
    dependencies: ["domain", "database", "sensors", "rendering"],
    forbiddenDeps: ["ui"],
    cargoPath: "crates/infrastructure"
  },
  {
    id: "ffi",
    label: "ffi",
    description: "Exposes C-compatible safe boundaries for Android NDK.",
    dependencies: ["application", "infrastructure", "common"],
    forbiddenDeps: ["ui"],
    cargoPath: "crates/ffi"
  },
  {
    id: "ui",
    label: "ui (Android)",
    description: "Jetpack Compose Application. Connects purely over FFI.",
    dependencies: ["ffi"],
    forbiddenDeps: ["database", "sqlx", "sensors", "domain"],
    cargoPath: "android/"
  }
];

export const CODE_TEMPLATES: CodeTemplate[] = [
  {
    name: "Perfect Tracking Coordinator",
    description: "Exemplifies clean CyberTrail Application workflows isolating logic from DB.",
    category: "Repository Contract",
    isCompliant: true,
    code: `// crates/application/src/start_tracking.rs
use domain::track::{TrackEngine, TrackId};
use domain::repository::TrackRepository;
use common::error::Result;

pub struct StartTrackingUseCase<R: TrackRepository> {
    repository: R,
    engine: TrackEngine,
}

impl<R: TrackRepository> StartTrackingUseCase<R> {
    pub async fn execute(&self) -> Result<TrackId> {
        let new_track = self.engine.initialize_blank();
        self.repository.save_track(&new_track).await?;
        Ok(new_track.id)
    }
}`
  },
  {
    name: "Violation: Direct Android Imports",
    description: "Directly linking Android SDK features inside the abstract Domain logic.",
    category: "Domain Purity",
    isCompliant: false,
    code: `// crates/domain/src/location.rs

// ❌ VIOLATION: Using platform-specific hardware imports in pure domain!
use android_sdk::LocationManager;

pub struct TrackPoint {
    pub lat: f64,
    pub lon: f64,
    // Android leakage
    pub os_provider: LocationManager,
}

impl TrackPoint {
    pub fn get_location(&self) -> f64 {
        self.os_provider.getLastKnownLocation()
    }
}`
  },
  {
    name: "Perfect Sensor Union Event",
    description: "Creates an agnostic integration path for sensors into the system.",
    category: "Sensors",
    isCompliant: true,
    code: `// crates/sensors/src/event.rs

#[derive(Debug, Clone)]
pub struct GpsData {
    pub lat: f64,
    pub lon: f64,
    pub accuracy_m: f32,
}

pub enum SensorEvent {
    Gps(GpsData),
    Pressure(f32),
    Motion(f32, f32, f32),
}
`
  },
  {
    name: "Violation: Google Map Rendering",
    description: "Uses a commercial Google Maps dependency in the render engine.",
    category: "Rendering",
    isCompliant: false,
    code: `// crates/rendering/src/hud.rs

// ❌ VIOLATION: CyberTrail requires full offline Cyberpunk Canvas graphics.
// Utilizing Google Maps SDK breaks the tactical standalone contract.
use google_maps_api::MapView;

pub fn render_track(map: &mut MapView, points: &[TrackPoint]) {
    map.add_polyline(points);
}`
  }
];

export function generateMockPoints(): TrackPoint[] {
  const points: TrackPoint[] = [];
  let currentLat = 47.6062;
  let currentLng = -122.3321;
  let currentAlt = 1500;
  
  for (let i = 0; i < 500; i++) {
    currentLat += (Math.random() - 0.45) * 0.001;
    currentLng += (Math.random() - 0.5) * 0.001;
    currentAlt += (Math.random() - 0.4) * 2;
    
    points.push({
      id: `PT-${i.toString().padStart(4, '0')}`,
      lat: parseFloat(currentLat.toFixed(5)),
      lng: parseFloat(currentLng.toFixed(5)),
      altitude: parseFloat(currentAlt.toFixed(2)),
      timestamp: new Date(Date.now() - (500 - i) * 1000).toISOString(),
      speed: parseFloat((Math.random() * 5).toFixed(1))
    });
  }
  
  return points;
}
