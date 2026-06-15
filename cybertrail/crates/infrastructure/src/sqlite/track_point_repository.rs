use async_trait::async_trait;
use rusqlite::params;
use std::str::FromStr;
use tokio::task;

use domain::entities::track_point::TrackPoint;
use domain::errors::domain_error::DomainError;
use domain::repositories::track_point_repository::TrackPointRepository;
use domain::value_objects::identifiers::TrackId;
use domain::value_objects::coordinate::Coordinate;
use domain::value_objects::altitude::Altitude;

use crate::sqlite::connection::SqlitePool;
use crate::sqlite::errors::InfrastructureError;

pub struct SqliteTrackPointRepository {
    pool: SqlitePool,
}

impl SqliteTrackPointRepository {
    pub fn new(pool: SqlitePool) -> Self {
        Self { pool }
    }
}

#[async_trait]
impl TrackPointRepository for SqliteTrackPointRepository {
    async fn append_track_points(&self, points: &[TrackPoint]) -> Result<(), DomainError> {
        if points.is_empty() {
            return Ok(());
        }

        let pool = self.pool.clone();
        
        // Clone into independent struct for crossing thread boundary
        struct PointData {
            track_id: String,
            timestamp: i64,
            lat_micro: i32,
            lon_micro: i32,
            alt_cm: Option<i32>,
        }

        let mut data = Vec::with_capacity(points.len());
        for p in points {
            let lat_micro = (p.coordinate().latitude() * 1_000_000.0).round() as i32;
            let lon_micro = (p.coordinate().longitude() * 1_000_000.0).round() as i32;
            let alt_cm = p.altitude().map(|a| (a.value() * 100.0).round() as i32);
            
            data.push(PointData {
                track_id: p.track_id().to_string(),
                timestamp: p.timestamp(),
                lat_micro,
                lon_micro,
                alt_cm,
            });
        }

        let _ = task::spawn_blocking(move || -> Result<(), InfrastructureError> {
            let mut conn = pool.get()?;
            let tx = conn.transaction_with_behavior(rusqlite::TransactionBehavior::Immediate)?;

            {
                let mut stmt = tx.prepare_cached(
                    "INSERT OR IGNORE INTO track_points (track_id, timestamp, lat_micro, lon_micro, alt_cm)
                     VALUES (?1, ?2, ?3, ?4, ?5)"
                )?;

                for d in data {
                    stmt.execute(params![
                        d.track_id,
                        d.timestamp,
                        d.lat_micro,
                        d.lon_micro,
                        d.alt_cm,
                    ])?;
                }
            }

            tx.commit()?;
            Ok(())
        })
        .await
        .map_err(|e| InfrastructureError::Concurrency(e.to_string()))??;

        Ok(())
    }

    async fn stream_track_points(&self, track_id: TrackId, start_timestamp: i64, limit: u32) -> Result<Vec<TrackPoint>, DomainError> {
        let pool = self.pool.clone();
        let track_id_str = track_id.to_string();

        let track_points = task::spawn_blocking(move || -> Result<Vec<TrackPoint>, InfrastructureError> {
            let conn = pool.get()?;
            let mut stmt = conn.prepare(
                "SELECT timestamp, lat_micro, lon_micro, alt_cm 
                 FROM track_points 
                 WHERE track_id = ?1 AND timestamp > ?2 
                 ORDER BY timestamp ASC 
                 LIMIT ?3"
            )?;
            
            let tp_iter = stmt.query_map(params![track_id_str, start_timestamp, limit], |row| {
                let timestamp: i64 = row.get(0)?;
                let lat_micro: i32 = row.get(1)?;
                let lon_micro: i32 = row.get(2)?;
                let alt_cm: Option<i32> = row.get(3)?;

                Ok((timestamp, lat_micro, lon_micro, alt_cm))
            })?;

            let mut points = Vec::new();
            let tid = TrackId::from_str(&track_id_str).map_err(|e| InfrastructureError::Mapping(e.to_string()))?;

            for item in tp_iter {
                let (timestamp, lat_micro, lon_micro, alt_cm) = item?;
                
                let lat = (lat_micro as f64) / 1_000_000.0;
                let lon = (lon_micro as f64) / 1_000_000.0;
                let coord = Coordinate::new(lat, lon).map_err(|e| InfrastructureError::Mapping(e.to_string()))?;
                
                let alt = alt_cm.map(|cm| {
                    Altitude::new((cm as f64) / 100.0).unwrap_or_else(|_| Altitude::new(0.0).unwrap())
                });

                points.push(TrackPoint::new(tid.clone(), timestamp, coord, alt));
            }

            Ok(points)
        })
        .await
        .map_err(|e| InfrastructureError::Concurrency(e.to_string()))??;

        Ok(track_points)
    }

    async fn count_track_points(&self, track_id: TrackId) -> Result<u64, DomainError> {
        let pool = self.pool.clone();
        let track_id_str = track_id.to_string();

        let count = task::spawn_blocking(move || -> Result<u64, InfrastructureError> {
            let conn = pool.get()?;
            let mut stmt = conn.prepare("SELECT COUNT(*) FROM track_points WHERE track_id = ?1")?;
            let count: i64 = stmt.query_row(params![track_id_str], |row| row.get(0))?;
            Ok(count as u64)
        })
        .await
        .map_err(|e| InfrastructureError::Concurrency(e.to_string()))??;

        Ok(count)
    }

    async fn first_track_point(&self, track_id: TrackId) -> Result<Option<TrackPoint>, DomainError> {
        let pool = self.pool.clone();
        let track_id_str = track_id.to_string();

        let point = task::spawn_blocking(move || -> Result<Option<TrackPoint>, InfrastructureError> {
            let conn = pool.get()?;
            let mut stmt = conn.prepare(
                "SELECT timestamp, lat_micro, lon_micro, alt_cm 
                 FROM track_points 
                 WHERE track_id = ?1 
                 ORDER BY timestamp ASC 
                 LIMIT 1"
            )?;
            
            let tp_opt = stmt.query_row(params![track_id_str], |row| {
                let timestamp: i64 = row.get(0)?;
                let lat_micro: i32 = row.get(1)?;
                let lon_micro: i32 = row.get(2)?;
                let alt_cm: Option<i32> = row.get(3)?;
                Ok((timestamp, lat_micro, lon_micro, alt_cm))
            });

            match tp_opt {
                Ok((timestamp, lat_micro, lon_micro, alt_cm)) => {
                    let tid = TrackId::from_str(&track_id_str).map_err(|e| InfrastructureError::Mapping(e.to_string()))?;
                    let lat = (lat_micro as f64) / 1_000_000.0;
                    let lon = (lon_micro as f64) / 1_000_000.0;
                    let coord = Coordinate::new(lat, lon).map_err(|e| InfrastructureError::Mapping(e.to_string()))?;
                    let alt = alt_cm.map(|cm| {
                        Altitude::new((cm as f64) / 100.0).unwrap_or_else(|_| Altitude::new(0.0).unwrap())
                    });
                    Ok(Some(TrackPoint::new(tid, timestamp, coord, alt)))
                },
                Err(rusqlite::Error::QueryReturnedNoRows) => Ok(None),
                Err(e) => Err(InfrastructureError::Database(e)),
            }
        })
        .await
        .map_err(|e| InfrastructureError::Concurrency(e.to_string()))??;

        Ok(point)
    }

    async fn last_track_point(&self, track_id: TrackId) -> Result<Option<TrackPoint>, DomainError> {
        let pool = self.pool.clone();
        let track_id_str = track_id.to_string();

        let point = task::spawn_blocking(move || -> Result<Option<TrackPoint>, InfrastructureError> {
            let conn = pool.get()?;
            let mut stmt = conn.prepare(
                "SELECT timestamp, lat_micro, lon_micro, alt_cm 
                 FROM track_points 
                 WHERE track_id = ?1 
                 ORDER BY timestamp DESC 
                 LIMIT 1"
            )?;
            
            let tp_opt = stmt.query_row(params![track_id_str], |row| {
                let timestamp: i64 = row.get(0)?;
                let lat_micro: i32 = row.get(1)?;
                let lon_micro: i32 = row.get(2)?;
                let alt_cm: Option<i32> = row.get(3)?;
                Ok((timestamp, lat_micro, lon_micro, alt_cm))
            });

            match tp_opt {
                Ok((timestamp, lat_micro, lon_micro, alt_cm)) => {
                    let tid = TrackId::from_str(&track_id_str).map_err(|e| InfrastructureError::Mapping(e.to_string()))?;
                    let lat = (lat_micro as f64) / 1_000_000.0;
                    let lon = (lon_micro as f64) / 1_000_000.0;
                    let coord = Coordinate::new(lat, lon).map_err(|e| InfrastructureError::Mapping(e.to_string()))?;
                    let alt = alt_cm.map(|cm| {
                        Altitude::new((cm as f64) / 100.0).unwrap_or_else(|_| Altitude::new(0.0).unwrap())
                    });
                    Ok(Some(TrackPoint::new(tid, timestamp, coord, alt)))
                },
                Err(rusqlite::Error::QueryReturnedNoRows) => Ok(None),
                Err(e) => Err(InfrastructureError::Database(e)),
            }
        })
        .await
        .map_err(|e| InfrastructureError::Concurrency(e.to_string()))??;

        Ok(point)
    }

    async fn delete_track_points_by_track(&self, track_id: TrackId) -> Result<(), DomainError> {
        let pool = self.pool.clone();
        let track_id_str = track_id.to_string();

        let _ = task::spawn_blocking(move || -> Result<(), InfrastructureError> {
            let conn = pool.get()?;
            conn.execute(
                "DELETE FROM track_points WHERE track_id = ?1",
                params![track_id_str],
            )?;
            Ok(())
        })
        .await
        .map_err(|e| InfrastructureError::Concurrency(e.to_string()))??;

        Ok(())
    }
}
