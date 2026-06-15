# CyberTrail FFI Boundary Design
Version: 1.0
Phase: 10.2
Status: Frozen

---

## 1. Boundary Philosophy

The FFI (Foreign Function Interface) boundary sits exclusively between the `Android Runtime (Kotlin)` and the `Rust Core`. 

**Strict Rule:** Rust is the absolute source of truth. Kotlin ONLY handles UI, Location acquisition (Android Location Services), and Map rendering. Kotlin never calculates distances, never serializes JSON, and never directly queries SQLite.

## 2. Technology Selection

**Decision: Mozilla's `uniffi` (UniFFI)**
We use `uniffi-rs` rather than manual `JNI` using `cbindgen` or `jni-rs`.
*Why?* UniFFI automatically generates memory-safe bindings, complex object marshalling, and Kotlin `data class` generators directly from Rust structs/enums via an interface definition file (`.udl` or proc-macros). This eliminates the massive surface area for memory leaks and `NewGlobalRef` crashes inherent to manual JNI coding.

## 3. Data Transfer Objects (DTOs)

To prevent `Garbage Collection` overhead in Android caused by complex nested objects, FFI boundaries must use primitive types or flattened structs wherever possible, specifically for high-velocity `TrackPoint` data loops.

### A. The Input Boundary (Kotlin -> Rust)
*Executed every 5 meters.*
```rust
// In Rust via UniFFI
#[uniffi::export]
fn ingest_raw_location(
    track_id: String, 
    timestamp_ms: i64, 
    latitude: f64, 
    longitude: f64, 
    accuracy_m: f32, 
    altitude_m: f64
) -> Result<(), FFIError> {
    // 1. Rust applies Accuracy Gate (>50m rejection)
    // 2. Rust converts f64 lat/lon to `lat_micro` i32.
    // 3. Rust passes to Kalman filter, then to MPSC Buffer.
}
```

### B. The Output Boundary (Rust -> Kotlin)
*Executed during Map rendering.*
```rust
#[derive(uniffi::Record)]
pub struct TrackSummaryDTO {
    pub id: String,
    pub name: String,
    pub distance_km: f64,
    pub duration_seconds: i64,
    pub ascent_m: f64,
}

#[uniffi::export]
fn get_track_history(limit: u32, offset: u32) -> Result<Vec<TrackSummaryDTO>, FFIError> {
    // Queries SQLite, maps to DTO, sends back to Kotlin.
}
```

## 4. Bulk Point Retrieval (Polyline Rendering)

If an Android UI requests 10,000 points off-screen, creating 10,000 Kotlin Objects via JNI will cause significant garbage collection stuttering during map pans.

**Solution: Parallel Array Mapping**
```rust
#[derive(uniffi::Record)]
pub struct ChunkedPolylineDTO {
    pub times: Vec<i64>,
    pub lats: Vec<f64>,
    pub lons: Vec<f64>,
}
```
This forces JNI to hand over three contiguous memory buffers instead of a collection of objects, guaranteeing lightning-fast MapBox/Google Maps source updates on Android.

## 5. Async Execution Bridging

UniFFI natively supports bridging Rust `async fn` to Kotlin `suspend fun`.
All heavy database or WebDAV tasks MUST be async across the boundary:

```rust
#[uniffi::export]
async fn force_sync_now() -> Result<SyncStatusDTO, FFIError> { ... }
```
In Kotlin:
```kotlin
viewModelScope.launch {
    try {
        val result = CyberTrailRust.forceSyncNow()
        _uiState.value = UiState.Synced(result)
    } catch (e: FFIException) {
        // ...
    }
}
```

## 6. FFI Error Handling

We define a flat Enum for errors mapping to Kotlin sealed classes/exceptions.
```rust
#[derive(uniffi::Error, Debug)]
pub enum FFIError {
    DatabaseError(String),
    CryptoError(String),
    NetworkError(String),
    IllegalState(String),
}
```
This keeps crash tracing highly readable in Android's Logcat and Crashlytics (without leaking private payload structures).
