use crate::errors::domain_error::DomainError;
use serde::{Deserialize, Serialize};
use std::fmt::{Display, Formatter};
use std::str::FromStr;
use uuid::Uuid;

#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash, Serialize, Deserialize)]
pub struct TrackId(Uuid);

impl TrackId {
    pub fn generate() -> Self {
        Self(Uuid::now_v7())
    }

    pub fn value(&self) -> Uuid {
        self.0
    }
}

impl Display for TrackId {
    fn fmt(&self, f: &mut Formatter<'_>) -> std::fmt::Result {
        write!(f, "{}", self.0)
    }
}

impl FromStr for TrackId {
    type Err = DomainError;

    fn from_str(s: &str) -> Result<Self, Self::Err> {
        let uuid = Uuid::parse_str(s)
            .map_err(|e| DomainError::ValidationError(format!("Invalid TrackId: {}", e)))?;
        Ok(Self(uuid))
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash, Serialize, Deserialize)]
pub struct WaypointId(Uuid);

impl WaypointId {
    pub fn generate() -> Self {
        Self(Uuid::now_v7())
    }

    pub fn value(&self) -> Uuid {
        self.0
    }
}

impl Display for WaypointId {
    fn fmt(&self, f: &mut Formatter<'_>) -> std::fmt::Result {
        write!(f, "{}", self.0)
    }
}

impl FromStr for WaypointId {
    type Err = DomainError;

    fn from_str(s: &str) -> Result<Self, Self::Err> {
        let uuid = Uuid::parse_str(s)
            .map_err(|e| DomainError::ValidationError(format!("Invalid WaypointId: {}", e)))?;
        Ok(Self(uuid))
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash, Serialize, Deserialize)]
pub struct AttachmentId(Uuid);

impl AttachmentId {
    pub fn generate() -> Self {
        Self(Uuid::now_v7())
    }

    pub fn value(&self) -> Uuid {
        self.0
    }
}

impl Display for AttachmentId {
    fn fmt(&self, f: &mut Formatter<'_>) -> std::fmt::Result {
        write!(f, "{}", self.0)
    }
}

impl FromStr for AttachmentId {
    type Err = DomainError;

    fn from_str(s: &str) -> Result<Self, Self::Err> {
        let uuid = Uuid::parse_str(s)
            .map_err(|e| DomainError::ValidationError(format!("Invalid AttachmentId: {}", e)))?;
        Ok(Self(uuid))
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_track_id_generate() {
        let id = TrackId::generate();
        assert_eq!(id.value().get_version_num(), 7);
    }

    #[test]
    fn test_track_id_parsing() {
        let id = TrackId::generate();
        let s = id.to_string();
        let parsed = TrackId::from_str(&s).unwrap();
        assert_eq!(id, parsed);

        let invalid = TrackId::from_str("invalid-uuid");
        assert!(matches!(invalid, Err(DomainError::ValidationError(_))));
    }

    #[test]
    fn test_waypoint_id_generate() {
        let id = WaypointId::generate();
        assert_eq!(id.value().get_version_num(), 7);
    }
}
