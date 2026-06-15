use rusqlite::Connection;
use r2d2::Pool;
use r2d2_sqlite::SqliteConnectionManager;
use std::path::Path;

pub type SqlitePool = Pool<SqliteConnectionManager>;

pub fn create_pool<P: AsRef<Path>>(db_path: P) -> Result<SqlitePool, r2d2::Error> {
    let manager = SqliteConnectionManager::file(db_path)
        .with_init(|conn| {
            // WAL mode mapping
            conn.execute_batch(
                "
                PRAGMA journal_mode = WAL;
                PRAGMA synchronous = NORMAL;
                PRAGMA foreign_keys = ON;
                PRAGMA busy_timeout = 5000;
                ",
            )?;
            Ok(())
        });

    Pool::builder()
        .max_size(5) // Multi-reader / single-writer supported via WAL and driver
        .build(manager)
}

pub fn create_in_memory_pool() -> Result<SqlitePool, r2d2::Error> {
    let manager = SqliteConnectionManager::memory()
        .with_init(|conn| {
            conn.execute_batch("PRAGMA foreign_keys = ON;")?;
            Ok(())
        });
        
    Pool::builder()
        .max_size(1) // In-memory DBs in SQLite typically don't share across connections unless shared cache is used
        .build(manager)
}

pub fn setup_schema(conn: &Connection) -> Result<(), rusqlite::Error> {
    conn.execute_batch(
        "
        CREATE TABLE IF NOT EXISTS tracks (
            id TEXT PRIMARY KEY,
            name TEXT NOT NULL,
            started_at INTEGER NOT NULL,
            ended_at INTEGER,
            duration_seconds INTEGER NOT NULL,
            distance_m REAL NOT NULL,
            ascent_m REAL NOT NULL,
            descent_m REAL NOT NULL,
            avg_speed_ms REAL NOT NULL,
            max_speed_ms REAL NOT NULL,
            max_altitude_m REAL NOT NULL,
            min_altitude_m REAL NOT NULL,
            is_deleted INTEGER NOT NULL,
            revision INTEGER NOT NULL,
            updated_at INTEGER NOT NULL
        ) STRICT;

        CREATE TABLE IF NOT EXISTS waypoints (
            id TEXT PRIMARY KEY,
            track_id TEXT,
            name TEXT NOT NULL,
            latitude REAL NOT NULL,
            longitude REAL NOT NULL,
            altitude REAL,
            notes TEXT,
            is_deleted INTEGER NOT NULL,
            revision INTEGER NOT NULL,
            created_at INTEGER NOT NULL,
            updated_at INTEGER NOT NULL,
            FOREIGN KEY(track_id) REFERENCES tracks(id)
        ) STRICT;

        CREATE TABLE IF NOT EXISTS track_points (
            track_id TEXT NOT NULL,
            timestamp INTEGER NOT NULL,
            lat_micro INTEGER NOT NULL,
            lon_micro INTEGER NOT NULL,
            alt_cm INTEGER,
            PRIMARY KEY (track_id, timestamp),
            FOREIGN KEY(track_id) REFERENCES tracks(id)
        ) WITHOUT ROWID, STRICT;
        "
    )
}
