# CyberTrail TrackPoint Compression Review
Version: 1.0
Status: Frozen

---

## 1. REAL vs Fixed Point Representation

Coordinates (Latitude/Longitude) are traditionally stored as floating-point numbers (`REAL` in SQLite, occupying 8 bytes). 
An alternative GIS strategy is to store them as Fixed Point `INTEGER` values scaled by 1,000,000 or 10,000,000.

**Methodology**:
- **REAL**: Standard 64-bit IEEE 754 precision float (`f64`), stored directly in SQLite as an 8-byte `REAL` type.
- **Fixed Point (Micro-degrees)**: `value * 1,000,000` stored as an `INTEGER`. For example, `35.689487` becomes `35689487`. Precision is retained up to ~11 centimeters, which easily out-resolves consumer GPS hardware (typically 3-10 meter variance).

---

## 2. SQLite Page Utilization & Storage Footprint

SQLite stores integers using variable-length encoding (Varints, 1-9 bytes). A number like `180,000,000` (max longitude in micro-degrees) requires 28 bits, which fits into a 4-byte or 5-byte Varint instead of the strict 8 bytes consumed by `REAL`.

**Storage per TrackPoint row (Base estimate)**:
- **`REAL` Scheme**: Timestamp (4-byte Varint) + Lat (8 bytes) + Lon (8 bytes) + Altitude (8 bytes) = ~28 bytes payload (+ SQLite row overhead).
- **Fixed Point Scheme**: Timestamp (4-byte) + Lat (4-5 bytes) + Lon (4-5 bytes) + Altitude Int (2-3 bytes) = ~14-17 bytes payload.

**Data Scale Footprint Assessment**:
*Assuming typical SQLite page utilization (4KB pages).*

| Scale | REAL (8-byte precision) | Fixed Point (Varint 32-bit) | Savings |
|-------|--------------------------|-----------------------------|----------|
| **1M Points** | ~45 MB | ~28 MB | **~38%** |
| **10M Points** | ~450 MB | ~280 MB | **~38%** |

*Note: Storing 10 Million points on Android using REAL occupies nearly half a gigabyte for raw payload and B-Tree overhead. Fixed point knocks this down significantly.*

---

## 3. Android ARM64 Benchmark Estimation

On modern ARM64 chips via JNI / Rust:
- **Floating Point Math (f64)**: Excellent via hardware FPU.
- **Integer Math (i32/i64)**: Slightly faster in raw ALU pipeline and takes up less space in CPU Cache (L1/L2) meaning better cache locality sequentially scrolling through `Vec<TrackPoint>`.
- **Deserialization**: SQLite mapping `INTEGER` bounds -> Rust `i32` -> arithmetic manipulation (`/ 1_000_000.0`) adds a negligible ALU operation but drastically speeds up disk I/O. Disk fetch speed and NVMe caching limits the theoretical throughput far more than the division op.

---

## 4. Export Costs (GPX & GeoJSON)

**The Cost of Struct Formatting**:
When generating a `.gpx` or `.json` string, we must convert our internal representation back into a text string (e.g. `lat="35.689487"`).
- **REAL formatting**: `format!("{:.6}", lat)` goes through floating-point string conversion algorithms (like Ryu), which are heavily optimized but fundamentally complex.
- **Fixed point string formatting**: Requires either `(lat as f64) / 1_000_000.0` and then `f64` stringification, or string manipulation mapping characters directly (`35689487` -> `"35.689487"`).
- **Verdict**: The export process will encounter an extra CPU cast operation `(i32 -> f64) / 10^6` locally. However, GPX export is a batch operation triggered rarely by the user, while Database streaming/reading is immediate and frequent. The I/O latency won over formatting costs.

---

## 5. Final Recommendation & Storage Format V1

Given the constraints of offline-first mobile databases holding dense spatial streams, we adopt **Fixed Point Storage**. 

**TrackPoint Storage Engine V1 Specifications**:

1. **Latitude/Longitude**: Scaled by `1,000,000` (Micro-degrees). Stored as `INTEGER` in SQLite. Mapped to `i32` in Rust.
   - Example: `45.123456` => `45123456`
2. **Altitude**: Stored as `INTEGER` (Meters * 10 or Centimeters). E.g., `1245.5 m` => `12455`). Default multiplier `10` or `100` mapped to `i32`.
3. **Timestamp**: Epoch milliseconds stored as `INTEGER`.

**Schema Adjustment**:
```sql
CREATE TABLE track_points (
    track_id TEXT NOT NULL,
    timestamp INTEGER NOT NULL,
    lat_micro INTEGER NOT NULL,
    lon_micro INTEGER NOT NULL,
    alt_cm INTEGER,
    PRIMARY KEY (track_id, timestamp),
    FOREIGN KEY(track_id) REFERENCES tracks(id)
) WITHOUT ROWID, STRICT;
```

With this decision frozen, the SQLite payload for TrackPoints is optimally compressed, the B-Tree density is doubled, and spatial integrity up to typical consumer GPS tolerances is structurally enforced.
