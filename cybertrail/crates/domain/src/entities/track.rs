use crate::errors::domain_error::DomainError;
use crate::events::domain_event::DomainEvent;
use crate::value_objects::identifiers::TrackId;
use crate::value_objects::kinematics::{Distance, Speed};
use crate::value_objects::revision::Revision;
use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
pub struct Track {
    id: TrackId,
    name: String,
    started_at: i64,
    ended_at: Option<i64>,
    duration_seconds: i64,
    distance_m: Distance,
    ascent_m: Distance,
    descent_m: Distance,
    avg_speed: Speed,
    max_speed: Speed,
    max_altitude: f64,
    min_altitude: f64,
    is_deleted: bool,
    revision: Revision,
    updated_at: i64,
}

impl Track {
    pub fn new(id: TrackId, name: &str, started_at: i64) -> Result<(Self, DomainEvent), DomainError> {
        let name_trimmed = name.trim();
        if name_trimmed.is_empty() {
            return Err(DomainError::IllegalStateError("Track name cannot be empty".to_string()));
        }
        if name_trimmed.len() > 100 {
            return Err(DomainError::IllegalStateError("Track name is too long".to_string()));
        }

        let track = Self {
            id,
            name: name_trimmed.to_string(),
            started_at,
            ended_at: None,
            duration_seconds: 0,
            distance_m: Distance::zero(),
            ascent_m: Distance::zero(),
            descent_m: Distance::zero(),
            avg_speed: Speed::zero(),
            max_speed: Speed::zero(),
            max_altitude: 0.0,
            min_altitude: 0.0,
            is_deleted: false,
            revision: Revision::initial(),
            updated_at: started_at,
        };

        let event = DomainEvent::TrackCreated {
            track_id: track.id,
            timestamp: started_at,
        };

        Ok((track, event))
    }

    pub fn id(&self) -> TrackId {
        self.id
    }

    pub fn name(&self) -> &str {
        &self.name
    }

    pub fn is_deleted(&self) -> bool {
        self.is_deleted
    }

    pub fn started_at(&self) -> i64 { self.started_at }
    pub fn ended_at(&self) -> Option<i64> { self.ended_at }
    pub fn duration_seconds(&self) -> i64 { self.duration_seconds }
    pub fn distance_m(&self) -> Distance { self.distance_m }
    pub fn ascent_m(&self) -> Distance { self.ascent_m }
    pub fn descent_m(&self) -> Distance { self.descent_m }
    pub fn avg_speed(&self) -> Speed { self.avg_speed }
    pub fn max_speed(&self) -> Speed { self.max_speed }
    pub fn max_altitude(&self) -> f64 { self.max_altitude }
    pub fn min_altitude(&self) -> f64 { self.min_altitude }
    pub fn updated_at(&self) -> i64 { self.updated_at }

    pub fn revision(&self) -> Revision {
        self.revision
    }

    pub fn reconstitute(
        id: TrackId, name: String, started_at: i64, ended_at: Option<i64>,
        duration_seconds: i64, distance_m: Distance, ascent_m: Distance, descent_m: Distance,
        avg_speed: Speed, max_speed: Speed, max_altitude: f64, min_altitude: f64,
        is_deleted: bool, revision: Revision, updated_at: i64
    ) -> Self {
        Self {
            id, name, started_at, ended_at, duration_seconds, distance_m, ascent_m, descent_m,
            avg_speed, max_speed, max_altitude, min_altitude, is_deleted, revision, updated_at
        }
    }

    pub fn rename(&mut self, new_name: &str, timestamp: i64) -> Result<DomainEvent, DomainError> {
        if self.is_deleted {
            return Err(DomainError::IllegalStateError("Cannot modify a deleted track".to_string()));
        }

        let name_trimmed = new_name.trim();
        if name_trimmed.is_empty() {
            return Err(DomainError::IllegalStateError("Track name cannot be empty".to_string()));
        }
        if name_trimmed.len() > 100 {
            return Err(DomainError::IllegalStateError("Track name is too long".to_string()));
        }

        if name_trimmed == self.name {
            // No actual change, could return an error or Ok, let's just err or ignore.
            // But we follow strict mutability changes.
            return Err(DomainError::IllegalStateError("New name is identical to current name".to_string()));
        }

        if timestamp < self.updated_at {
             return Err(DomainError::IllegalStateError("Update timestamp cannot be older than current updated_at".to_string()));
        }

        self.name = name_trimmed.to_string();
        self.increment_revision(timestamp);

        Ok(DomainEvent::TrackRenamed {
            track_id: self.id,
            revision: self.revision,
            timestamp,
        })
    }

    pub fn mark_deleted(&mut self, timestamp: i64) -> Result<DomainEvent, DomainError> {
        if self.is_deleted {
            return Err(DomainError::IllegalStateError("Track is already deleted".to_string()));
        }

        if timestamp < self.updated_at {
             return Err(DomainError::IllegalStateError("Update timestamp cannot be older than current updated_at".to_string()));
        }

        self.is_deleted = true;
        self.increment_revision(timestamp);

        Ok(DomainEvent::TrackDeleted {
            track_id: self.id,
            revision: self.revision,
            timestamp,
        })
    }
    
    pub fn restore(&mut self, timestamp: i64) -> Result<DomainEvent, DomainError> {
        if !self.is_deleted {
            return Err(DomainError::IllegalStateError("Track is not deleted".to_string()));
        }
        
        if timestamp < self.updated_at {
             return Err(DomainError::IllegalStateError("Update timestamp cannot be older than current updated_at".to_string()));
        }

        self.is_deleted = false;
        self.increment_revision(timestamp);

        Ok(DomainEvent::TrackRestored {
            track_id: self.id,
            revision: self.revision,
            timestamp,
        })
    }

    fn increment_revision(&mut self, timestamp: i64) {
        self.revision = self.revision.increment();
        self.updated_at = timestamp;
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_create_track() {
        let id = TrackId::generate();
        let timestamp = 1000;
        let (track, event) = Track::new(id, "My Hike", timestamp).unwrap();

        assert_eq!(track.name(), "My Hike");
        assert_eq!(track.revision().value(), 1);
        assert!(!track.is_deleted());

        match event {
            DomainEvent::TrackCreated { track_id, timestamp: t } => {
                assert_eq!(track_id, id);
                assert_eq!(t, timestamp);
            }
            _ => panic!("Wrong event type"),
        }
    }

    #[test]
    fn test_create_track_empty_name() {
        let id = TrackId::generate();
        let res = Track::new(id, "   ", 1000);
        assert!(matches!(res, Err(DomainError::IllegalStateError(_))));
    }

    #[test]
    fn test_rename_track() {
        let id = TrackId::generate();
        let (mut track, _) = Track::new(id, "Old Hike", 1000).unwrap();
        
        let event = track.rename("New Hike", 2000).unwrap();
        assert_eq!(track.name(), "New Hike");
        assert_eq!(track.revision().value(), 2);
        
        match event {
            DomainEvent::TrackRenamed { track_id, revision, timestamp } => {
                assert_eq!(track_id, id);
                assert_eq!(revision.value(), 2);
                assert_eq!(timestamp, 2000);
            }
            _ => panic!("Wrong event type"),
        }
    }

    #[test]
    fn test_delete_and_modify() {
        let id = TrackId::generate();
        let (mut track, _) = Track::new(id, "Hike", 1000).unwrap();
        
        let _ = track.mark_deleted(2000).unwrap();
        assert!(track.is_deleted());
        assert_eq!(track.revision().value(), 2);

        let res = track.rename("Should Fail", 3000);
        assert!(matches!(res, Err(DomainError::IllegalStateError(_))));
    }
}
