//! SQL Schema representations for CyberTrail SQLite database.
//! Contains table creations ensuring WAL, Foreign Keys, and Prepared Statement readiness.

pub const CREATE_TRACKS_TABLE: &str = "
    CREATE TABLE IF NOT EXISTS tracks (
        id TEXT PRIMARY KEY,
        name TEXT NOT NULL,
        start_time INTEGER NOT NULL,
        end_time INTEGER,
        total_distance REAL DEFAULT 0.0
    );
";

pub const CREATE_TRACK_POINTS_TABLE: &str = "
    CREATE TABLE IF NOT EXISTS track_points (
        id TEXT PRIMARY KEY,
        track_id TEXT NOT NULL,
        lat REAL NOT NULL,
        lng REAL NOT NULL,
        altitude REAL,
        timestamp INTEGER NOT NULL,
        FOREIGN KEY(track_id) REFERENCES tracks(id) ON DELETE CASCADE
    );
";

pub const ENABLE_WAL: &str = "PRAGMA journal_mode = WAL;";
pub const ENABLE_FOREIGN_KEYS: &str = "PRAGMA foreign_keys = ON;";
