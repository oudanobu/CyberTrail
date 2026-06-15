use async_trait::async_trait;
use rusqlite::{params, OptionalExtension};
use std::str::FromStr;
use tokio::task;

use domain::entities::track::Track;
use domain::errors::domain_error::DomainError;
use domain::repositories::track_repository::TrackRepository;
use domain::value_objects::identifiers::TrackId;
use domain::value_objects::kinematics::{Distance, Speed};
use domain::value_objects::revision::Revision;

use crate::sqlite::connection::SqlitePool;
use crate::sqlite::errors::InfrastructureError;

pub struct SqliteTrackRepository {
    pool: SqlitePool,
}

impl SqliteTrackRepository {
    pub fn new(pool: SqlitePool) -> Self {
        Self { pool }
    }
}

#[async_trait]
impl TrackRepository for SqliteTrackRepository {
    async fn save(&self, track: &Track) -> Result<(), DomainError> {
        let pool = self.pool.clone();
        
        let id = track.id().to_string();
        let name = track.name().to_string();
        let started_at = track.started_at();
        let ended_at = track.ended_at();
        let duration_seconds = track.duration_seconds();
        let distance_m = track.distance_m().value();
        let ascent_m = track.ascent_m().value();
        let descent_m = track.descent_m().value();
        let avg_speed_ms = track.avg_speed().value();
        let max_speed_ms = track.max_speed().value();
        let max_altitude_m = track.max_altitude();
        let min_altitude_m = track.min_altitude();
        let is_deleted = if track.is_deleted() { 1 } else { 0 };
        let revision = track.revision().value();
        let updated_at = track.updated_at();

        let _ = task::spawn_blocking(move || -> Result<(), InfrastructureError> {
            let conn = pool.get()?;
            conn.execute(
                "INSERT INTO tracks (
                    id, name, started_at, ended_at, duration_seconds, distance_m, ascent_m, descent_m,
                    avg_speed_ms, max_speed_ms, max_altitude_m, min_altitude_m, is_deleted, revision, updated_at
                ) VALUES (
                    ?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9, ?10, ?11, ?12, ?13, ?14, ?15
                )
                ON CONFLICT(id) DO UPDATE SET
                    name = excluded.name,
                    ended_at = excluded.ended_at,
                    duration_seconds = excluded.duration_seconds,
                    distance_m = excluded.distance_m,
                    ascent_m = excluded.ascent_m,
                    descent_m = excluded.descent_m,
                    avg_speed_ms = excluded.avg_speed_ms,
                    max_speed_ms = excluded.max_speed_ms,
                    max_altitude_m = excluded.max_altitude_m,
                    min_altitude_m = excluded.min_altitude_m,
                    is_deleted = excluded.is_deleted,
                    revision = excluded.revision,
                    updated_at = excluded.updated_at",
                params![
                    id, name, started_at, ended_at, duration_seconds, distance_m, ascent_m, descent_m,
                    avg_speed_ms, max_speed_ms, max_altitude_m, min_altitude_m, is_deleted, revision, updated_at
                ],
            )?;
            Ok(())
        })
        .await
        .map_err(|e| InfrastructureError::SqliteError(format!("Concurrency error: {}", e)))??;

        Ok(())
    }

    async fn find_by_id(&self, id: TrackId) -> Result<Option<Track>, DomainError> {
        let pool = self.pool.clone();
        let id_str = id.to_string();

        let track = task::spawn_blocking(move || -> Result<Option<Track>, InfrastructureError> {
            let conn = pool.get()?;
            let mut stmt = conn.prepare("SELECT * FROM tracks WHERE id = ?1")?;
            
            let track_opt = stmt.query_row(params![id_str], |row| {
                let id_str: String = row.get("id")?;
                let name: String = row.get("name")?;
                let started_at: i64 = row.get("started_at")?;
                let ended_at: Option<i64> = row.get("ended_at")?;
                let duration_seconds: i64 = row.get("duration_seconds")?;
                let distance_m: f64 = row.get("distance_m")?;
                let ascent_m: f64 = row.get("ascent_m")?;
                let descent_m: f64 = row.get("descent_m")?;
                let avg_speed_ms: f64 = row.get("avg_speed_ms")?;
                let max_speed_ms: f64 = row.get("max_speed_ms")?;
                let max_altitude_m: f64 = row.get("max_altitude_m")?;
                let min_altitude_m: f64 = row.get("min_altitude_m")?;
                let is_deleted: i32 = row.get("is_deleted")?;
                let revision: i64 = row.get("revision")?;
                let updated_at: i64 = row.get("updated_at")?;

                Ok((id_str, name, started_at, ended_at, duration_seconds, distance_m, ascent_m, descent_m, avg_speed_ms, max_speed_ms, max_altitude_m, min_altitude_m, is_deleted, revision, updated_at))
            }).optional()?;

            if let Some((id_str, name, started_at, ended_at, duration_seconds, distance_m, ascent_m, descent_m, avg_speed_ms, max_speed_ms, max_altitude_m, min_altitude_m, is_deleted, revision, updated_at)) = track_opt {
                let id = TrackId::from_str(&id_str).map_err(|e| InfrastructureError::MappingError(e.to_string()))?;
                let dist = Distance::new(distance_m).map_err(|e| InfrastructureError::MappingError(e.to_string()))?;
                let asc = Distance::new(ascent_m).map_err(|e| InfrastructureError::MappingError(e.to_string()))?;
                let desc = Distance::new(descent_m).map_err(|e| InfrastructureError::MappingError(e.to_string()))?;
                let avg_spd = Speed::new(avg_speed_ms).map_err(|e| InfrastructureError::MappingError(e.to_string()))?;
                let max_spd = Speed::new(max_speed_ms).map_err(|e| InfrastructureError::MappingError(e.to_string()))?;
                let rev = Revision::new(revision as u64);

                Ok(Some(Track::reconstitute(
                    id, name, started_at, ended_at, duration_seconds, dist, asc, desc, avg_spd, max_spd, max_altitude_m, min_altitude_m, is_deleted != 0, rev, updated_at
                )))
            } else {
                Ok(None)
            }
        })
        .await
        .map_err(|e| InfrastructureError::SqliteError(format!("Concurrency error: {}", e)))??;

        Ok(track)
    }

    async fn exists(&self, id: TrackId) -> Result<bool, DomainError> {
        let pool = self.pool.clone();
        let id_str = id.to_string();

        let exists = task::spawn_blocking(move || -> Result<bool, InfrastructureError> {
            let conn = pool.get()?;
            let mut stmt = conn.prepare("SELECT 1 FROM tracks WHERE id = ?1 LIMIT 1")?;
            let exists = stmt.exists(params![id_str])?;
            Ok(exists)
        })
        .await
        .map_err(|e| InfrastructureError::SqliteError(format!("Concurrency error: {}", e)))??;

        Ok(exists)
    }

    async fn find_by_revision_greater_than(&self, rev: Revision) -> Result<Vec<Track>, DomainError> {
        let pool = self.pool.clone();
        let min_rev = rev.value();

        let tracks = task::spawn_blocking(move || -> Result<Vec<Track>, InfrastructureError> {
            let conn = pool.get()?;
            let mut stmt = conn.prepare("SELECT * FROM tracks WHERE revision > ?1 ORDER BY revision ASC")?;
            
            let track_iter = stmt.query_map(params![min_rev], |row| {
                let id_str: String = row.get("id")?;
                let name: String = row.get("name")?;
                let started_at: i64 = row.get("started_at")?;
                let ended_at: Option<i64> = row.get("ended_at")?;
                let duration_seconds: i64 = row.get("duration_seconds")?;
                let distance_m: f64 = row.get("distance_m")?;
                let ascent_m: f64 = row.get("ascent_m")?;
                let descent_m: f64 = row.get("descent_m")?;
                let avg_speed_ms: f64 = row.get("avg_speed_ms")?;
                let max_speed_ms: f64 = row.get("max_speed_ms")?;
                let max_altitude_m: f64 = row.get("max_altitude_m")?;
                let min_altitude_m: f64 = row.get("min_altitude_m")?;
                let is_deleted: i32 = row.get("is_deleted")?;
                let revision: i64 = row.get("revision")?;
                let updated_at: i64 = row.get("updated_at")?;

                Ok((id_str, name, started_at, ended_at, duration_seconds, distance_m, ascent_m, descent_m, avg_speed_ms, max_speed_ms, max_altitude_m, min_altitude_m, is_deleted, revision, updated_at))
            })?;

            let mut tracks = Vec::new();
            for item in track_iter {
                let (id_str, name, started_at, ended_at, duration_seconds, distance_m, ascent_m, descent_m, avg_speed_ms, max_speed_ms, max_altitude_m, min_altitude_m, is_deleted, revision, updated_at) = item?;
                
                let id = TrackId::from_str(&id_str).map_err(|e| InfrastructureError::MappingError(e.to_string()))?;
                let dist = Distance::new(distance_m).map_err(|e| InfrastructureError::MappingError(e.to_string()))?;
                let asc = Distance::new(ascent_m).map_err(|e| InfrastructureError::MappingError(e.to_string()))?;
                let desc = Distance::new(descent_m).map_err(|e| InfrastructureError::MappingError(e.to_string()))?;
                let avg_spd = Speed::new(avg_speed_ms).map_err(|e| InfrastructureError::MappingError(e.to_string()))?;
                let max_spd = Speed::new(max_speed_ms).map_err(|e| InfrastructureError::MappingError(e.to_string()))?;
                let rev = Revision::new(revision as u64);

                tracks.push(Track::reconstitute(
                    id, name, started_at, ended_at, duration_seconds, dist, asc, desc, avg_spd, max_spd, max_altitude_m, min_altitude_m, is_deleted != 0, rev, updated_at
                ));
            }
            Ok(tracks)
        })
        .await
        .map_err(|e| InfrastructureError::SqliteError(format!("Concurrency error: {}", e)))??;

        Ok(tracks)
    }
}
