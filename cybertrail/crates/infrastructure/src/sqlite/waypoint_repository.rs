use async_trait::async_trait;
use rusqlite::{params, OptionalExtension};
use std::str::FromStr;
use tokio::task;

use domain::entities::waypoint::Waypoint;
use domain::errors::domain_error::DomainError;
use domain::repositories::waypoint_repository::WaypointRepository;
use domain::value_objects::identifiers::{TrackId, WaypointId};
use domain::value_objects::coordinate::Coordinate;
use domain::value_objects::altitude::Altitude;
use domain::value_objects::revision::Revision;

use crate::sqlite::connection::SqlitePool;
use crate::sqlite::errors::InfrastructureError;

pub struct SqliteWaypointRepository {
    pool: SqlitePool,
}

impl SqliteWaypointRepository {
    pub fn new(pool: SqlitePool) -> Self {
        Self { pool }
    }
}

#[async_trait]
impl WaypointRepository for SqliteWaypointRepository {
    async fn save(&self, waypoint: &Waypoint) -> Result<(), DomainError> {
        let pool = self.pool.clone();
        
        let id = waypoint.id().to_string();
        let track_id = waypoint.track_id().map(|id| id.to_string());
        let name = waypoint.name().to_string();
        let latitude = waypoint.coordinate().latitude();
        let longitude = waypoint.coordinate().longitude();
        let altitude = waypoint.altitude().map(|a| a.value());
        let notes = waypoint.notes().map(|s| s.to_string());
        let is_deleted = if waypoint.is_deleted() { 1 } else { 0 };
        let revision = waypoint.revision().value();
        let created_at = waypoint.created_at();
        let updated_at = waypoint.updated_at();

        let _ = task::spawn_blocking(move || -> Result<(), InfrastructureError> {
            let conn = pool.get()?;
            conn.execute(
                "INSERT INTO waypoints (
                    id, track_id, name, latitude, longitude, altitude, notes, is_deleted, revision, created_at, updated_at
                ) VALUES (
                    ?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9, ?10, ?11
                )
                ON CONFLICT(id) DO UPDATE SET
                    track_id = excluded.track_id,
                    name = excluded.name,
                    latitude = excluded.latitude,
                    longitude = excluded.longitude,
                    altitude = excluded.altitude,
                    notes = excluded.notes,
                    is_deleted = excluded.is_deleted,
                    revision = excluded.revision,
                    updated_at = excluded.updated_at",
                params![
                    id, track_id, name, latitude, longitude, altitude, notes, is_deleted, revision, created_at, updated_at
                ],
            )?;
            Ok(())
        })
        .await
        .map_err(|e| InfrastructureError::SqliteError(format!("Concurrency error: {}", e)))??;

        Ok(())
    }

    async fn find_by_id(&self, id: WaypointId) -> Result<Option<Waypoint>, DomainError> {
        let pool = self.pool.clone();
        let id_str = id.to_string();

        let waypoint = task::spawn_blocking(move || -> Result<Option<Waypoint>, InfrastructureError> {
            let conn = pool.get()?;
            let mut stmt = conn.prepare("SELECT * FROM waypoints WHERE id = ?1")?;
            
            let wp_opt = stmt.query_row(params![id_str], |row| {
                let id_str: String = row.get("id")?;
                let track_id_str: Option<String> = row.get("track_id")?;
                let name: String = row.get("name")?;
                let latitude: f64 = row.get("latitude")?;
                let longitude: f64 = row.get("longitude")?;
                let altitude: Option<f64> = row.get("altitude")?;
                let notes: Option<String> = row.get("notes")?;
                let is_deleted: i32 = row.get("is_deleted")?;
                let revision: i64 = row.get("revision")?;
                let created_at: i64 = row.get("created_at")?;
                let updated_at: i64 = row.get("updated_at")?;

                Ok((id_str, track_id_str, name, latitude, longitude, altitude, notes, is_deleted, revision, created_at, updated_at))
            }).optional()?;

            if let Some((id_str, track_id_str, name, latitude, longitude, altitude_opt, notes, is_deleted, revision, created_at, updated_at)) = wp_opt {
                let id = WaypointId::from_str(&id_str).map_err(|e| InfrastructureError::MappingError(e.to_string()))?;
                let track_id = match track_id_str {
                    Some(s) => Some(TrackId::from_str(&s).map_err(|e| InfrastructureError::MappingError(e.to_string()))?),
                    None => None,
                };
                let coord = Coordinate::new(latitude, longitude).map_err(|e| InfrastructureError::MappingError(e.to_string()))?;
                let alt = match altitude_opt {
                    Some(a) => Some(Altitude::new(a).map_err(|e| InfrastructureError::MappingError(e.to_string()))?),
                    None => None,
                };
                let rev = Revision::new(revision as u64);

                Ok(Some(Waypoint::reconstitute(
                    id, track_id, name, coord, alt, notes, is_deleted != 0, rev, created_at, updated_at
                )))
            } else {
                Ok(None)
            }
        })
        .await
        .map_err(|e| InfrastructureError::SqliteError(format!("Concurrency error: {}", e)))??;

        Ok(waypoint)
    }

    async fn find_by_track_id(&self, target_track_id: TrackId) -> Result<Vec<Waypoint>, DomainError> {
        let pool = self.pool.clone();
        let track_id_str = target_track_id.to_string();

        let waypoints = task::spawn_blocking(move || -> Result<Vec<Waypoint>, InfrastructureError> {
            let conn = pool.get()?;
            let mut stmt = conn.prepare("SELECT * FROM waypoints WHERE track_id = ?1 ORDER BY created_at ASC")?;
            
            let wp_iter = stmt.query_map(params![track_id_str], |row| {
                let id_str: String = row.get("id")?;
                let track_id_str: Option<String> = row.get("track_id")?;
                let name: String = row.get("name")?;
                let latitude: f64 = row.get("latitude")?;
                let longitude: f64 = row.get("longitude")?;
                let altitude: Option<f64> = row.get("altitude")?;
                let notes: Option<String> = row.get("notes")?;
                let is_deleted: i32 = row.get("is_deleted")?;
                let revision: i64 = row.get("revision")?;
                let created_at: i64 = row.get("created_at")?;
                let updated_at: i64 = row.get("updated_at")?;

                Ok((id_str, track_id_str, name, latitude, longitude, altitude, notes, is_deleted, revision, created_at, updated_at))
            })?;

            let mut waypoints = Vec::new();
            for item in wp_iter {
                let (id_str, track_id_str, name, latitude, longitude, altitude_opt, notes, is_deleted, revision, created_at, updated_at) = item?;
                
                let id = WaypointId::from_str(&id_str).map_err(|e| InfrastructureError::MappingError(e.to_string()))?;
                let track_id = match track_id_str {
                    Some(s) => Some(TrackId::from_str(&s).map_err(|e| InfrastructureError::MappingError(e.to_string()))?),
                    None => None,
                };
                let coord = Coordinate::new(latitude, longitude).map_err(|e| InfrastructureError::MappingError(e.to_string()))?;
                let alt = match altitude_opt {
                    Some(a) => Some(Altitude::new(a).map_err(|e| InfrastructureError::MappingError(e.to_string()))?),
                    None => None,
                };
                let rev = Revision::new(revision as u64);

                waypoints.push(Waypoint::reconstitute(
                    id, track_id, name, coord, alt, notes, is_deleted != 0, rev, created_at, updated_at
                ));
            }
            Ok(waypoints)
        })
        .await
        .map_err(|e| InfrastructureError::SqliteError(format!("Concurrency error: {}", e)))??;

        Ok(waypoints)
    }

    async fn exists(&self, id: WaypointId) -> Result<bool, DomainError> {
        let pool = self.pool.clone();
        let id_str = id.to_string();

        let exists = task::spawn_blocking(move || -> Result<bool, InfrastructureError> {
            let conn = pool.get()?;
            let mut stmt = conn.prepare("SELECT 1 FROM waypoints WHERE id = ?1 LIMIT 1")?;
            let exists = stmt.exists(params![id_str])?;
            Ok(exists)
        })
        .await
        .map_err(|e| InfrastructureError::SqliteError(format!("Concurrency error: {}", e)))??;

        Ok(exists)
    }

    async fn find_by_revision_greater_than(&self, rev: Revision) -> Result<Vec<Waypoint>, DomainError> {
        let pool = self.pool.clone();
        let min_rev = rev.value();

        let waypoints = task::spawn_blocking(move || -> Result<Vec<Waypoint>, InfrastructureError> {
            let conn = pool.get()?;
            let mut stmt = conn.prepare("SELECT * FROM waypoints WHERE revision > ?1 ORDER BY revision ASC")?;
            
            let wp_iter = stmt.query_map(params![min_rev], |row| {
                let id_str: String = row.get("id")?;
                let track_id_str: Option<String> = row.get("track_id")?;
                let name: String = row.get("name")?;
                let latitude: f64 = row.get("latitude")?;
                let longitude: f64 = row.get("longitude")?;
                let altitude: Option<f64> = row.get("altitude")?;
                let notes: Option<String> = row.get("notes")?;
                let is_deleted: i32 = row.get("is_deleted")?;
                let revision: i64 = row.get("revision")?;
                let created_at: i64 = row.get("created_at")?;
                let updated_at: i64 = row.get("updated_at")?;

                Ok((id_str, track_id_str, name, latitude, longitude, altitude, notes, is_deleted, revision, created_at, updated_at))
            })?;

            let mut waypoints = Vec::new();
            for item in wp_iter {
                let (id_str, track_id_str, name, latitude, longitude, altitude_opt, notes, is_deleted, revision, created_at, updated_at) = item?;
                
                let id = WaypointId::from_str(&id_str).map_err(|e| InfrastructureError::MappingError(e.to_string()))?;
                let track_id = match track_id_str {
                    Some(s) => Some(TrackId::from_str(&s).map_err(|e| InfrastructureError::MappingError(e.to_string()))?),
                    None => None,
                };
                let coord = Coordinate::new(latitude, longitude).map_err(|e| InfrastructureError::MappingError(e.to_string()))?;
                let alt = match altitude_opt {
                    Some(a) => Some(Altitude::new(a).map_err(|e| InfrastructureError::MappingError(e.to_string()))?),
                    None => None,
                };
                let rev = Revision::new(revision as u64);

                waypoints.push(Waypoint::reconstitute(
                    id, track_id, name, coord, alt, notes, is_deleted != 0, rev, created_at, updated_at
                ));
            }
            Ok(waypoints)
        })
        .await
        .map_err(|e| InfrastructureError::SqliteError(format!("Concurrency error: {}", e)))??;

        Ok(waypoints)
    }
}
