use jni::objects::{JClass, JString};
use jni::sys::{jboolean, jstring, jlong, jdouble, jint};
use jni::JNIEnv;
use once_cell::sync::OnceCell;
use serde::Serialize;
use std::str::FromStr;
use tokio::runtime::Runtime;

use domain::entities::track::Track;
use domain::value_objects::identifiers::TrackId;
use infrastructure::sqlite::connection::{create_pool, setup_schema, SqlitePool};
use rusqlite::params;
use rusqlite::OptionalExtension;

static DB_POOL: OnceCell<SqlitePool> = OnceCell::new();

fn get_runtime() -> &'static Runtime {
    static RUNTIME: OnceCell<Runtime> = OnceCell::new();
    RUNTIME.get_or_init(|| {
        Runtime::new().expect("Failed to create Tokio runtime")
    })
}

/// Retrieves the current version of the CyberTrail core.
pub fn get_version() -> String {
    "CyberTrail Core 0.1.0".to_string()
}

/// Executes a health check validation on the FFI layer.
pub fn health_check() -> bool {
    true
}

/// Helper function to compute direct Haversine distance between two coordinates in meters.
fn haversine_distance(lat1: f64, lon1: f64, lat2: f64, lon2: f64) -> f64 {
    let r = 6371000.0; // Earth's radius in meters
    let phi1 = lat1.to_radians();
    let phi2 = lat2.to_radians();
    let delta_phi = (lat2 - lat1).to_radians();
    let delta_lambda = (lon2 - lon1).to_radians();

    let a = (delta_phi / 2.0).sin().powi(2)
        + phi1.cos() * phi2.cos() * (delta_lambda / 2.0).sin().powi(2);
    let c = 2.0 * a.sqrt().atan2((1.0 - a).sqrt());

    r * c
}

/// Adds a track point, calculates distance, ascent/descent, and updates active track stats.
fn add_track_point_impl(
    track_id_str: &str,
    lat: f64,
    lon: f64,
    alt_val: Option<f64>,
    timestamp: i64,
) -> Result<bool, Box<dyn std::error::Error>> {
    let pool = DB_POOL.get().ok_or("Database path not initialized")?;
    let mut conn = pool.get()?;

    // Parse Track ID
    let _ = TrackId::from_str(track_id_str)?;

    let last_point_opt = {
        let mut stmt = conn.prepare(
            "SELECT lat_micro, lon_micro, alt_cm, timestamp 
             FROM track_points 
             WHERE track_id = ?1 
             ORDER BY timestamp DESC LIMIT 1"
        )?;

        stmt.query_row(params![track_id_str], |row| {
            let lat_micro: i32 = row.get(0)?;
            let lon_micro: i32 = row.get(1)?;
            let alt_cm: Option<i32> = row.get(2)?;
            let prev_time: i64 = row.get(3)?;
            Ok((lat_micro, lon_micro, alt_cm, prev_time))
        }).optional()?
    };

    let mut delta_dist = 0.0;
    let mut delta_ascent = 0.0;
    let mut delta_descent = 0.0;

    if let Some((prev_lat_micro, prev_lon_micro, prev_alt_cm, _prev_time)) = last_point_opt {
        let prev_lat = (prev_lat_micro as f64) / 1_000_000.0;
        let prev_lon = (prev_lon_micro as f64) / 1_000_000.0;

        // Calculate distance delta via haversine
        delta_dist = haversine_distance(prev_lat, prev_lon, lat, lon);

        // Calculate ascent/descent delta
        if let (Some(p_alt_cm), Some(new_alt)) = (prev_alt_cm, alt_val) {
            let prev_alt = (p_alt_cm as f64) / 100.0;
            let diff = new_alt - prev_alt;
            if diff > 0.0 {
                delta_ascent = diff;
            } else {
                delta_descent = -diff;
            }
        }
    }

    // Convert values for DB storage
    let lat_micro = (lat * 1_000_000.0).round() as i32;
    let lon_micro = (lon * 1_000_000.0).round() as i32;
    let alt_cm = alt_val.map(|v| (v * 100.0).round() as i32);

    let tx = conn.transaction()?;

    // 2. Insert new track point
    tx.execute(
        "INSERT INTO track_points (track_id, timestamp, lat_micro, lon_micro, alt_cm)
         VALUES (?1, ?2, ?3, ?4, ?5)",
        params![track_id_str, timestamp, lat_micro, lon_micro, alt_cm],
    )?;

    // 3. Query existing track to update stats
    let track_row_opt = {
        let mut query_stmt = tx.prepare(
            "SELECT started_at, duration_seconds, distance_m, ascent_m, descent_m, max_altitude_m, min_altitude_m, name, revision, is_deleted 
             FROM tracks 
             WHERE id = ?1 LIMIT 1"
        )?;

        query_stmt.query_row(params![track_id_str], |row| {
            let started_at: i64 = row.get(0)?;
            let _duration: i64 = row.get(1)?;
            let distance_m: f64 = row.get(2)?;
            let ascent_m: f64 = row.get(3)?;
            let descent_m: f64 = row.get(4)?;
            let max_alt: f64 = row.get(5)?;
            let min_alt: f64 = row.get(6)?;
            let name: String = row.get(7)?;
            let revision: i64 = row.get(8)?;
            let is_deleted: i32 = row.get(9)?;
            Ok((started_at, distance_m, ascent_m, descent_m, max_alt, min_alt, name, revision, is_deleted))
        }).optional()?
    };

    if let Some((started_at, orig_distance, orig_ascent, orig_descent, orig_max_alt, orig_min_alt, _name, revision, _is_deleted)) = track_row_opt {
        let new_duration = if timestamp > started_at {
            timestamp - started_at
        } else {
            0
        };

        let new_distance = orig_distance + delta_dist;
        let new_ascent = orig_ascent + delta_ascent;
        let new_descent = orig_descent + delta_descent;

        let mut new_max_alt = orig_max_alt;
        let mut new_min_alt = orig_min_alt;

        if let Some(new_alt) = alt_val {
            if orig_max_alt == 0.0 && orig_min_alt == 0.0 {
                new_max_alt = new_alt;
                new_min_alt = new_alt;
            } else {
                if new_alt > orig_max_alt {
                    new_max_alt = new_alt;
                }
                if new_alt < orig_min_alt {
                    new_min_alt = new_alt;
                }
            }
        }

        let new_revision = revision + 1;

        tx.execute(
            "UPDATE tracks SET
                duration_seconds = ?1,
                distance_m = ?2,
                ascent_m = ?3,
                descent_m = ?4,
                max_altitude_m = ?5,
                min_altitude_m = ?6,
                revision = ?7,
                updated_at = ?8
             WHERE id = ?9",
            params![
                new_duration,
                new_distance,
                new_ascent,
                new_descent,
                new_max_alt,
                new_min_alt,
                new_revision,
                timestamp,
                track_id_str
            ],
        )?;
    }

    tx.commit()?;
    Ok(true)
}

#[derive(Serialize)]
struct TrackJson {
    id: String,
    name: String,
    started_at: i64,
    ended_at: Option<i64>,
    duration_seconds: i64,
    distance_m: f64,
    ascent_m: f64,
    descent_m: f64,
    points_count: i32,
}

#[derive(Serialize)]
struct TrackPointJson {
    timestamp: i64,
    latitude: f64,
    longitude: f64,
    altitude: Option<f64>,
}

/// JNI bridge to retrieve cybertrail version from Java/Kotlin
#[no_mangle]
pub extern "system" fn Java_com_cybertrail_app_NativeCore_getVersion<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
) -> jstring {
    let version = get_version();
    match env.new_string(&version) {
        Ok(output) => output.into_raw(),
        Err(_) => std::ptr::null_mut(),
    }
}

/// JNI bridge to perform health check from Java/Kotlin
#[no_mangle]
pub extern "system" fn Java_com_cybertrail_app_NativeCore_healthCheck<'local>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
) -> jboolean {
    if health_check() {
        1
    } else {
        0
    }
}

/// JNI bridge to initialize the SQLite database and pool
#[no_mangle]
pub extern "system" fn Java_com_cybertrail_app_NativeCore_initDatabase<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    db_path: JString<'local>,
) -> jboolean {
    let db_path: String = match env.get_string(&db_path) {
        Ok(s) => s.into(),
        Err(_) => return 0,
    };

    let pool = match create_pool(&db_path) {
        Ok(p) => p,
        Err(_) => return 0,
    };

    // Setup schema
    if let Ok(conn) = pool.get() {
        if setup_schema(&conn).is_err() {
            return 0;
        }
    } else {
        return 0;
    }

    if DB_POOL.set(pool).is_err() {
        // Already initialized, which is perfectly safe
    }

    1
}

/// JNI bridge to start a new record track
#[no_mangle]
pub extern "system" fn Java_com_cybertrail_app_NativeCore_startTrack<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    name: JString<'local>,
    started_at: jlong,
) -> jstring {
    let name: String = match env.get_string(&name) {
        Ok(s) => s.into(),
        Err(_) => return std::ptr::null_mut(),
    };

    let pool = match DB_POOL.get() {
        Some(p) => p,
        None => return std::ptr::null_mut(),
    };

    let conn = match pool.get() {
        Ok(c) => c,
        Err(_) => return std::ptr::null_mut(),
    };

    let track_id = TrackId::generate();
    let (track, _) = match Track::new(track_id, &name, started_at) {
        Ok(t) => t,
        Err(_) => return std::ptr::null_mut(),
    };

    let id_str = track.id().to_string();
    let name_str = track.name().to_string();
    let started = track.started_at();
    let ended = track.ended_at();
    let duration = track.duration_seconds();
    let distance = track.distance_m().value();
    let ascent = track.ascent_m().value();
    let descent = track.descent_m().value();
    let avg_speed = track.avg_speed().value();
    let max_speed = track.max_speed().value();
    let max_alt = track.max_altitude();
    let min_alt = track.min_altitude();
    let is_deleted = if track.is_deleted() { 1 } else { 0 };
    let revision = track.revision().value();
    let updated = track.updated_at();

    let save_res = conn.execute(
        "INSERT INTO tracks (
            id, name, started_at, ended_at, duration_seconds, distance_m, ascent_m, descent_m,
            avg_speed_ms, max_speed_ms, max_altitude_m, min_altitude_m, is_deleted, revision, updated_at
        ) VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9, ?10, ?11, ?12, ?13, ?14, ?15)",
        params![
            id_str, name_str, started, ended, duration, distance, ascent, descent,
            avg_speed, max_speed, max_alt, min_alt, is_deleted, revision, updated
        ],
    );

    match save_res {
        Ok(_) => {
            let id_str = track_id.to_string();
            match env.new_string(&id_str) {
                Ok(output) => output.into_raw(),
                Err(_) => std::ptr::null_mut(),
            }
        }
        Err(_) => std::ptr::null_mut(),
    }
}

/// JNI bridge to add a Track point to a track
#[no_mangle]
pub extern "system" fn Java_com_cybertrail_app_NativeCore_addTrackPoint<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    track_id: JString<'local>,
    latitude: jdouble,
    longitude: jdouble,
    altitude: jdouble,
    timestamp: jlong,
) -> jboolean {
    let track_id_str: String = match env.get_string(&track_id) {
        Ok(s) => s.into(),
        Err(_) => return 0,
    };

    let alt_val = if altitude.is_nan() { None } else { Some(altitude) };

    match add_track_point_impl(&track_id_str, latitude, longitude, alt_val, timestamp) {
        Ok(res) => if res { 1 } else { 0 },
        Err(_) => 0,
    }
}

/// JNI bridge to end a track session
#[no_mangle]
pub extern "system" fn Java_com_cybertrail_app_NativeCore_endTrack<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    track_id: JString<'local>,
    ended_at: jlong,
) -> jboolean {
    let track_id_str: String = match env.get_string(&track_id) {
        Ok(s) => s.into(),
        Err(_) => return 0,
    };

    let pool = match DB_POOL.get() {
        Some(p) => p,
        None => return 0,
    };

    let conn = match pool.get() {
        Ok(c) => c,
        Err(_) => return 0,
    };

    let update_res = conn.execute(
        "UPDATE tracks SET ended_at = ?1, updated_at = ?1, revision = revision + 1 WHERE id = ?2",
        params![ended_at, track_id_str],
    );

    match update_res {
        Ok(_) => 1,
        Err(_) => 0,
    }
}

/// JNI bridge to get all tracks as JSON string
#[no_mangle]
pub extern "system" fn Java_com_cybertrail_app_NativeCore_getAllTracksJson<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
) -> jstring {
    let pool = match DB_POOL.get() {
        Some(p) => p,
        None => return std::ptr::null_mut(),
    };

    let conn = match pool.get() {
        Ok(c) => c,
        Err(_) => return std::ptr::null_mut(),
    };

    let mut stmt = match conn.prepare(
        "SELECT t.id, t.name, t.started_at, t.ended_at, t.duration_seconds, t.distance_m, t.ascent_m, t.descent_m,
                (SELECT COUNT(*) FROM track_points WHERE track_id = t.id) as pts_count
         FROM tracks t
         WHERE t.is_deleted = 0
         ORDER BY t.started_at DESC"
    ) {
        Ok(s) => s,
        Err(_) => return std::ptr::null_mut(),
    };

    let track_rows = stmt.query_map([], |row| {
        let id: String = row.get(0)?;
        let name: String = row.get(1)?;
        let started_at: i64 = row.get(2)?;
        let ended_at: Option<i64> = row.get(3)?;
        let duration_seconds: i64 = row.get(4)?;
        let distance_m: f64 = row.get(5)?;
        let ascent_m: f64 = row.get(6)?;
        let descent_m: f64 = row.get(7)?;
        let points_count: i32 = row.get(8)?;

        Ok(TrackJson {
            id,
            name,
            started_at,
            ended_at,
            duration_seconds,
            distance_m,
            ascent_m,
            descent_m,
            points_count,
        })
    });

    let mut list = Vec::new();
    if let Ok(rows) = track_rows {
        for row in rows {
            if let Ok(t) = row {
                list.push(t);
            }
        }
    }

    let json_str = match serde_json::to_string(&list) {
        Ok(s) => s,
        Err(_) => return std::ptr::null_mut(),
    };

    match env.new_string(&json_str) {
        Ok(output) => output.into_raw(),
        Err(_) => std::ptr::null_mut(),
    }
}

/// JNI bridge to get all track points for a track as JSON string
#[no_mangle]
pub extern "system" fn Java_com_cybertrail_app_NativeCore_getTrackPointsJson<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    track_id: JString<'local>,
) -> jstring {
    let track_id_str: String = match env.get_string(&track_id) {
        Ok(s) => s.into(),
        Err(_) => return std::ptr::null_mut(),
    };

    let pool = match DB_POOL.get() {
        Some(p) => p,
        None => return std::ptr::null_mut(),
    };

    let conn = match pool.get() {
        Ok(c) => c,
        Err(_) => return std::ptr::null_mut(),
    };

    let mut stmt = match conn.prepare(
        "SELECT timestamp, lat_micro, lon_micro, alt_cm 
         FROM track_points 
         WHERE track_id = ?1 
         ORDER BY timestamp ASC"
    ) {
        Ok(s) => s,
        Err(_) => return std::ptr::null_mut(),
    };

    let pt_rows = stmt.query_map(params![track_id_str], |row| {
        let timestamp: i64 = row.get(0)?;
        let lat_micro: i32 = row.get(1)?;
        let lon_micro: i32 = row.get(2)?;
        let alt_cm: Option<i32> = row.get(3)?;

        let latitude = (lat_micro as f64) / 1_000_000.0;
        let longitude = (lon_micro as f64) / 1_000_000.0;
        let altitude = alt_cm.map(|v| (v as f64) / 100.0);

        Ok(TrackPointJson {
            timestamp,
            latitude,
            longitude,
            altitude,
        })
    });

    let mut list = Vec::new();
    if let Ok(rows) = pt_rows {
        for row in rows {
            if let Ok(t) = row {
                list.push(t);
            }
        }
    }

    let json_str = match serde_json::to_string(&list) {
        Ok(s) => s,
        Err(_) => return std::ptr::null_mut(),
    };

    match env.new_string(&json_str) {
        Ok(output) => output.into_raw(),
        Err(_) => std::ptr::null_mut(),
    }
}

/// JNI bridge to delete a track (mark is_deleted = 1)
#[no_mangle]
pub extern "system" fn Java_com_cybertrail_app_NativeCore_deleteTrack<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    track_id: JString<'local>,
) -> jboolean {
    let track_id_str: String = match env.get_string(&track_id) {
        Ok(s) => s.into(),
        Err(_) => return 0,
    };

    let pool = match DB_POOL.get() {
        Some(p) => p,
        None => return 0,
    };

    let conn = match pool.get() {
        Ok(c) => c,
        Err(_) => return 0,
    };

    let delete_res = conn.execute(
        "UPDATE tracks SET is_deleted = 1, revision = revision + 1 WHERE id = ?1",
        params![track_id_str],
    );

    match delete_res {
        Ok(_) => 1,
        Err(_) => 0,
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_get_version() {
        assert_eq!(get_version(), "CyberTrail Core 0.1.0");
    }

    #[test]
    fn test_health_check() {
        assert!(health_check());
    }

    #[test]
    fn test_haversine() {
        let dist = haversine_distance(39.9, 116.4, 39.9, 116.401);
        assert!(dist > 0.0);
    }
}
