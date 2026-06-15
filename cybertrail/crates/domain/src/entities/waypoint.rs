use crate::errors::domain_error::DomainError;
use crate::events::domain_event::DomainEvent;
use crate::value_objects::identifiers::{TrackId, WaypointId};
use crate::value_objects::coordinate::Coordinate;
use crate::value_objects::altitude::Altitude;
use crate::value_objects::revision::Revision;
use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
pub struct Waypoint {
    id: WaypointId,
    track_id: Option<TrackId>,
    name: String,
    coordinate: Coordinate,
    altitude: Option<Altitude>,
    notes: Option<String>,
    is_deleted: bool,
    revision: Revision,
    created_at: i64,
    updated_at: i64,
}

impl Waypoint {
    pub fn new(
        id: WaypointId,
        track_id: Option<TrackId>,
        name: &str,
        coordinate: Coordinate,
        altitude: Option<Altitude>,
        notes: Option<String>,
        timestamp: i64,
    ) -> Result<(Self, DomainEvent), DomainError> {
        let name_trimmed = name.trim();
        if name_trimmed.is_empty() {
            return Err(DomainError::IllegalStateError("Waypoint name cannot be empty".to_string()));
        }
        if name_trimmed.len() > 100 {
            return Err(DomainError::IllegalStateError("Waypoint name is too long".to_string()));
        }

        let waypoint = Self {
            id,
            track_id,
            name: name_trimmed.to_string(),
            coordinate,
            altitude,
            notes,
            is_deleted: false,
            revision: Revision::initial(),
            created_at: timestamp,
            updated_at: timestamp,
        };

        let event = DomainEvent::WaypointCreated {
            waypoint_id: waypoint.id,
            timestamp,
        };

        Ok((waypoint, event))
    }

    pub fn id(&self) -> WaypointId {
        self.id
    }

    pub fn name(&self) -> &str {
        &self.name
    }

    pub fn is_deleted(&self) -> bool {
        self.is_deleted
    }

    pub fn track_id(&self) -> Option<TrackId> { self.track_id }
    pub fn coordinate(&self) -> Coordinate { self.coordinate }
    pub fn altitude(&self) -> Option<Altitude> { self.altitude }
    pub fn notes(&self) -> Option<&str> { self.notes.as_deref() }
    pub fn created_at(&self) -> i64 { self.created_at }
    pub fn updated_at(&self) -> i64 { self.updated_at }

    pub fn revision(&self) -> Revision {
        self.revision
    }

    pub fn reconstitute(
        id: WaypointId, track_id: Option<TrackId>, name: String, coordinate: Coordinate,
        altitude: Option<Altitude>, notes: Option<String>, is_deleted: bool, revision: Revision,
        created_at: i64, updated_at: i64
    ) -> Self {
        Self {
            id, track_id, name, coordinate, altitude, notes, is_deleted, revision, created_at, updated_at
        }
    }

    pub fn rename(&mut self, new_name: &str, timestamp: i64) -> Result<DomainEvent, DomainError> {
        if self.is_deleted {
            return Err(DomainError::IllegalStateError("Cannot modify a deleted waypoint".to_string()));
        }

        let name_trimmed = new_name.trim();
        if name_trimmed.is_empty() {
            return Err(DomainError::IllegalStateError("Waypoint name cannot be empty".to_string()));
        }
        if name_trimmed.len() > 100 {
            return Err(DomainError::IllegalStateError("Waypoint name is too long".to_string()));
        }

        if name_trimmed == self.name {
            return Err(DomainError::IllegalStateError("New name is identical to current name".to_string()));
        }

        if timestamp < self.updated_at {
             return Err(DomainError::IllegalStateError("Update timestamp cannot be older than current updated_at".to_string()));
        }

        self.name = name_trimmed.to_string();
        self.increment_revision(timestamp);

        Ok(DomainEvent::WaypointRenamed {
            waypoint_id: self.id,
            revision: self.revision,
            timestamp,
        })
    }
    
    pub fn move_to(&mut self, new_coordinate: Coordinate, new_altitude: Option<Altitude>, timestamp: i64) -> Result<DomainEvent, DomainError> {
        if self.is_deleted {
            return Err(DomainError::IllegalStateError("Cannot modify a deleted waypoint".to_string()));
        }
        
        if timestamp < self.updated_at {
             return Err(DomainError::IllegalStateError("Update timestamp cannot be older than current updated_at".to_string()));
        }

        self.coordinate = new_coordinate;
        self.altitude = new_altitude;
        self.increment_revision(timestamp);

        Ok(DomainEvent::WaypointMoved {
            waypoint_id: self.id,
            revision: self.revision,
            timestamp,
        })
    }

    pub fn mark_deleted(&mut self, timestamp: i64) -> Result<DomainEvent, DomainError> {
        if self.is_deleted {
            return Err(DomainError::IllegalStateError("Waypoint is already deleted".to_string()));
        }

        if timestamp < self.updated_at {
             return Err(DomainError::IllegalStateError("Update timestamp cannot be older than current updated_at".to_string()));
        }

        self.is_deleted = true;
        self.increment_revision(timestamp);

        Ok(DomainEvent::WaypointDeleted {
            waypoint_id: self.id,
            revision: self.revision,
            timestamp,
        })
    }
    
    pub fn restore(&mut self, timestamp: i64) -> Result<DomainEvent, DomainError> {
        if !self.is_deleted {
            return Err(DomainError::IllegalStateError("Waypoint is not deleted".to_string()));
        }
        
        if timestamp < self.updated_at {
             return Err(DomainError::IllegalStateError("Update timestamp cannot be older than current updated_at".to_string()));
        }

        self.is_deleted = false;
        self.increment_revision(timestamp);

        Ok(DomainEvent::WaypointRestored {
            waypoint_id: self.id,
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
    fn test_create_waypoint() {
        let id = WaypointId::generate();
        let timestamp = 1000;
        let coordinate = Coordinate::new(10.0, 20.0).unwrap();
        let (wp, event) = Waypoint::new(id, None, "Camp", coordinate, None, None, timestamp).unwrap();

        assert_eq!(wp.name(), "Camp");
        assert_eq!(wp.revision().value(), 1);
        assert!(!wp.is_deleted());

        match event {
            DomainEvent::WaypointCreated { waypoint_id, timestamp: t } => {
                assert_eq!(waypoint_id, id);
                assert_eq!(t, timestamp);
            }
            _ => panic!("Wrong event type"),
        }
    }

    #[test]
    fn test_create_waypoint_empty_name() {
        let id = WaypointId::generate();
        let coordinate = Coordinate::new(10.0, 20.0).unwrap();
        let res = Waypoint::new(id, None, "   ", coordinate, None, None, 1000);
        assert!(matches!(res, Err(DomainError::IllegalStateError(_))));
    }

    #[test]
    fn test_rename_waypoint() {
        let id = WaypointId::generate();
        let coordinate = Coordinate::new(10.0, 20.0).unwrap();
        let (mut wp, _) = Waypoint::new(id, None, "Old Camp", coordinate, None, None, 1000).unwrap();
        
        let event = wp.rename("New Camp", 2000).unwrap();
        assert_eq!(wp.name(), "New Camp");
        assert_eq!(wp.revision().value(), 2);
        
        match event {
            DomainEvent::WaypointRenamed { waypoint_id, revision, timestamp } => {
                assert_eq!(waypoint_id, id);
                assert_eq!(revision.value(), 2);
                assert_eq!(timestamp, 2000);
            }
            _ => panic!("Wrong event type"),
        }
    }
    
    #[test]
    fn test_move_waypoint() {
        let id = WaypointId::generate();
        let coordinate = Coordinate::new(10.0, 20.0).unwrap();
        let (mut wp, _) = Waypoint::new(id, None, "Camp", coordinate, None, None, 1000).unwrap();
        
        let new_coordinate = Coordinate::new(15.0, 25.0).unwrap();
        let event = wp.move_to(new_coordinate, None, 2000).unwrap();
        
        assert_eq!(wp.revision().value(), 2);
        
        match event {
            DomainEvent::WaypointMoved { waypoint_id, revision, timestamp } => {
                assert_eq!(waypoint_id, id);
                assert_eq!(revision.value(), 2);
                assert_eq!(timestamp, 2000);
            }
            _ => panic!("Wrong event type"),
        }
    }

    #[test]
    fn test_delete_and_modify() {
        let id = WaypointId::generate();
        let coordinate = Coordinate::new(10.0, 20.0).unwrap();
        let (mut wp, _) = Waypoint::new(id, None, "Camp", coordinate, None, None, 1000).unwrap();
        
        let _ = wp.mark_deleted(2000).unwrap();
        assert!(wp.is_deleted());
        assert_eq!(wp.revision().value(), 2);

        let res = wp.rename("Should Fail", 3000);
        assert!(matches!(res, Err(DomainError::IllegalStateError(_))));
    }
}
