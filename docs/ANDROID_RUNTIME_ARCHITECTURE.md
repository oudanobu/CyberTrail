# CyberTrail Android Runtime Architecture
Version: 1.0
Status: Frozen

This document freezes the multi-threaded host runtime of CyberTrail on Android 13 to Android 16. It details how the Kotlin/Java user-facing interface, the Android-specific sensor sub-systems, and the core Rust-based domain repository interact. It guarantees strict compliance with battery life budgets, reliable background tracking lifecycle persistence, and crash/reboot resilience.

---

## 1. Foreground Service Strategy

Continuous, seconds-level outdoor logging is highly susceptible to eviction by the Android Low Memory Killer (LMK) and battery-saver subsystems. CyberTrail enforces a robust, persistent foreground service architecture.

```text
+--------------------------------------------------------+
| Android User Interface (Compose Activity)              |
+--------------------------------------------------------+
    │ (Binds/Starts)
    ▼
+--------------------------------------------------------+
| TrackingForegroundService                              |
| - foregroundServiceType="location"                     |
| - START_STICKY Lifecycle Management                    |
| - Holds WakeLock (Partial while recording)             |
+--------------------------------------------------------+
    │ (FFI Calls)
    ▼
+--------------------------------------------------------+
| Rust Core Engine (Tokio Runtime + Repository Engine)    |
+--------------------------------------------------------+
```

### 1.1 Declarations & Service Configuration
- **Foreground Service Type**: `foregroundServiceType="location"` is mandated starting with Android 10 and strictly enforced in Android 13+.
- **Lifecycle Intent**: Returns `START_STICKY` inside `onStartCommand`. If killed by an extreme resource constraint, the OS automatically schedules the reconstruction of the service.
- **Notification Requirement**: A low-latency, persistent user notification displaying:
  * Active tracking duration (HH:MM:SS)
  * Current cumulative distance (km, e.g., "12.42 km")
  * Barometric altitude with trend indicator + current battery percentage
  * Actions: "Pause Recording", "Mark Waypoint", "Emergency SOS"

### 1.2 WakeLock Lifecycle
- While actively recording, dry CPU pauses can cause GPS packet loss.
- **Action**: Acquire a `PARTIAL_WAKE_LOCK` with a unique tag `cybertrail:recording_wakelock`.
- **Constraint**: To prevent battery hogging, the Wakelock is strictly released upon pausing or stopping tracking.

---

## 2. GPS Sampling Pipeline

To support long hikes without killing the battery, CyberTrail completely drops standard temporal updates (e.g., writing coordinates every second) in favor of spatial, filter-gated triggers.

```text
[ Raw GPS Sensor Update ]
           │
           ▼
[ Accuracy Threshold Gate ] ── (Dropped if accuracy > 15m)
           │
           ▼
[ G-Forces & Accelerometer Check ]
           │
           ▼
[ Kalman Filter Fusion ]
  - State vector: [lat, lon, v_lat, v_lon]
  - Smooths out GPS multipath reflection jitter
           │
           ▼
[ Distance Gating Window ]
  - Distance since last point >= 5 meters?
  - OR Time elapsed >= 30 seconds fallback?
           │
           ▼
[ TrackPoint Appended to MPSC Channel ]
```

### 2.1 Distance Gating & Temporal-Fallback
- **Core Trigger**: A point is forwarded only when the geo-spatial distance (great-circle calculation) since the last persisted point is **$\ge$ 5 meters**.
- **Temporal Fallback**: If the user halts (e.g., making camp, resting), GPS points have zero spatial variation. Instead of endlessly recording identical points (multipath jitter), the spatial engine stops writing. A temporal watchdog inserts a single state heartbeat point every **30 seconds** to keep track status alive and verify heading accuracy for the Tactical HUD dashboard.

### 2.2 Kalman Filtering & Accuracy Gate
- Any coordinates reporting an horizontal accuracy value $> 15$ meters are rejected immediately prior to entering the filter.
- A client-side, 2D Linear Kalman Filter smooths input velocities and coordinates, keeping trajectories stable inside deep forest canopies or mountain gorges.

---

## 3. TrackPoint Buffer Design

To minimize expensive context-switching and file-system flush operations, coordinate inputs are accumulated into memory before triggering database commits.

```text
[ Android GPS Sensor Callbacks ]
               │ (JNI Boundary)
               ▼
   [ Rust: send(TrackPoint) ]
               │
               ▼
+──────────────────────────────────────────────────+
| tokio::sync::mpsc::channel (capacity: 256)       |
+──────────────────────────────────────────────────+
               │ (Buffered Streams)
               ▼
+──────────────────────────────────────────────────+
| Buffer Aggregator Worker                         |
| - Accumulates up to 100 points                   |
| - OR flushes every 30 seconds                    |
+──────────────────────────────────────────────────+
               │ (Batched vector)
               ▼
   [ SQLite Batch Writer Task ]
```

- **MPSC Queue**: Cross-JNI JNI-to-Rust points are inserted into a bounded, async `tokio::sync::mpsc::channel` with a performance-safe container capacity of `256`.
- **Flush Threshold Gating**:
  * **Size Trigger**: Accumulation of `100` track points.
  * **Time Trigger**: Time elapsed since last flush $\ge 30$ seconds.
  * **Event Trigger**: User pauses, finishes, or manual waypoint creation.

---

## 4. SQLite Writer Thread Design

SQLite is fundamentally single-writer; multiple concurrent writes on different threads cause `SQLITE_BUSY` or locking starvation.

- **Dedicated Database Core**: Access to SQLite is restricted to a dedicated pool managed via `r2d2`. All disk-bound transactions are executed asynchronously using `tokio::task::spawn_blocking` to decouple transactional processing from sensor callback event cycles.
- **Write Optimization Protocol**:
  ```sql
  BEGIN IMMEDIATE TRANSACTION;
  -- Batch insertion loops
  INSERT INTO track_points (track_id, timestamp, lat_micro, lon_micro, alt_cm) VALUES (?, ?, ?, ?, ?);
  COMMIT;
  ```
- **Pragmas Config**: To prevent hardware wear-and-tear and support continuous execution on mid-tier SD-Cards:
  - `journal_mode = WAL`
  - `synchronous = NORMAL`
  - `foreign_keys = ON`

---

## 5. Battery Optimization Strategy

### 5.1 OEM Background Restrictions (The "Don't Kill My App" War)
High-end Chinese Android OEMs (Xiaomi MIUI/HyperOS, OPPO/Realme ColorOS, Vivo Funtouch, Huawei HarmonyOS) apply non-standard background task management policies, aggressively terminating background processes.

To guarantee uninterrupted recording, CyberTrail uses a unified, multi-tier compliance layout:

1. **System Introspections**:
   - Detect if API `PowerManager.isIgnoringBatteryOptimizations` is false.
   - If false, render a diagnostic dialog during onboarding that launches `Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`.
2. **Dynamic OEM Instruction Guide**:
   - Offer customized instructions prompting users to turn on "Autostart", allow "No background limits", and lock the task card inside the Recent Apps stack.
3. **Deep Sleep Heartbeats**:
   - Android Doze mode can cut off networks and restrict sensor hardware. Acquiring the `PARTIAL_WAKE_LOCK` keeps the CPU core ready to handle location interrupts even with the screen fully locked.

---

## 6. Doze & App Standby Analysis

*   **App Standby Buckets**: CyberTrail uses active background services, placing it into the `ACTIVE` bucket during recording journeys.
*   **Doze Mode Gating**:
    - During Doze Mode, standard networks and general location hooks suspend.
    - Since we have an active `FOREGROUND_SERVICE` and explicit user-granted `ACCESS_BACKGROUND_LOCATION` paired with background exclusion settings, Doze Location Restrictions are physically bypassed for this app.
    - Periodic heartbeats inside the Sync State Machine use `AlarmManager.setAndAllowWhileIdle()` to perform routine WebDAV status sync checks.

---

## 7. Android 13~16 Compatibility Matrix

Permissions and hardware APIs undergo radical changes across modern Android releases. This matrix ensures native API portability.

| Target Core | Permissions Mandated | System Gating Policies |
| :--- | :--- | :--- |
| **Android 13** (API 33) | `ACCESS_FINE_LOCATION`, `POST_NOTIFICATIONS` | Explicit notifications opt-in is required on launch. Undergoes basic Foreground Service restrictions. |
| **Android 14** (API 34) | Above + `FOREGROUND_SERVICE_LOCATION` | **Critical**: Location FGS requires listing the type physically inside the AndroidManifest, paired with a declared foreground-compatible system permission. |
| **Android 15** (API 35) | Above + Boot Receiver Permissions | Native restrictions on background services start from boot. Services require immediate context wrapper setup. |
| **Android 16** (API 36) | Above + Precise Background Access | System checks fine-grain sensor intervals aggressively. |

```xml
<!-- AndroidManifest.xml Standard Gating Requirements -->
<manifest ...>
    <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
    <uses-permission android:name="android.permission.ACCESS_BACKGROUND_LOCATION" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_LOCATION" />
    <uses-permission android:name="android.permission.WAKE_LOCK" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

    <application ...>
        <service
            android:name=".services.TrackingForegroundService"
            android:foregroundServiceType="location"
            android:exported="false" />
    </application>
</manifest>
```

---

## 8. Offline Failure Recovery Strategy

When the physical host fails (severe battery thermal shutdown, system crash, user force-stops the task stack, or runtime crash), tracking data must never be corrupted.

1. **State Handshakes**:
   - The SQLite database maintains a single-row `active_record_state`:
     * `state`: enum `[Recording, Paused, Idle]`
     * `active_track_id`: UUID of the track currently being built
2. **Reboot Hook Listener**:
   - A `BOOT_COMPLETED` BroadcastReceiver registers on boot.
   - If a reboot is captured, the application reads `active_record_state`.
   - If the state was `Recording`, it invokes a background notification warning the user: *"CyberTrail recorded a system interruption during your last hike. Restart recording to continue your path."*
3. **Recovery Append Operation**:
   - When resumed, the system continues logging under the *same* `active_track_id` without corrupting past points. The time gap is visually represented in charts and elevation profiles.

---

## 9. Memory Budget Analysis

Target Platform: ARM64 Device, 4GB RAM typical hardware limit.

```text
+-------------------------------------------------------------+
| System Ram Allocation Profile (Target: <100MB Total JVM/Rust)|
+-------------------------------------------------------------+
| JVM Context (UI / Maps / Orchestration Layers):   45.0 MB   |
| Rust Core Static Payload and Tokio Engine:        12.0 MB   |
| Decryption / Sync Temporary Buffers:               2.0 MB   |
| Point Buffer Queue Cache:                          1.0 MB   |
| SQLite Core WAL Caching Allocation:                4.0 MB   |
+-------------------------------------------------------------+
```

- **Garbage Collection Optimization**:
  - The FFI boundary passes primitive parameters (`lat_micro`, `lon_micro`, `alt_cm`, `timestamp`) as flat arrays/buffers to eliminate string parsing serialization and stop GC-allocation spikes in Android.
- **Resource Cleanup**: Once a track session is stopped, all Rust-held memory channels are dropped, releasing allocation space back to the Linux kernel immediately.

---

## 10. Recommended Runtime Architecture V1 Summary

To confirm success, the entire pipeline is structured sequentially:

- **Location callback triggers** inside Android JNI boundary -> checks accuracy.
- **Kalman filter verifies coordinates** -> checks 5-meter spatial difference.
- **Point pushed** into the async Rust `mpsc` channel.
- **Aggregator batcher gathers outputs** -> flushes database using `BEGIN IMMEDIATE` after 30 seconds or 100 points.
- **Foreground service maintains CPU runtime** via `PARTIAL_WAKE_LOCK` and persistent sticky notifications, bypassing background sleep optimization limits.

With these ten requirements established, CyberTrail's runtime engine guarantees high-integrity track logging with maximum power safety on any standard Android environment.
toolSummary: "Architecture specification for the Android runtime and background logging system."
toolAction: "Creating docs/ANDROID_RUNTIME_ARCHITECTURE.md"
