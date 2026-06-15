use crate::errors::domain_error::DomainError;
use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Copy, PartialEq, Serialize, Deserialize)]
pub struct Coordinate {
    latitude: f64,
    longitude: f64,
}

impl Coordinate {
    pub fn new(latitude: f64, longitude: f64) -> Result<Self, DomainError> {
        if !(-90.0..=90.0).contains(&latitude) {
            return Err(DomainError::ValidationError(format!(
                "Latitude must be between -90.0 and 90.0, got {}",
                latitude
            )));
        }

        if !(-180.0..=180.0).contains(&longitude) {
            return Err(DomainError::ValidationError(format!(
                "Longitude must be between -180.0 and 180.0, got {}",
                longitude
            )));
        }

        Ok(Self {
            latitude,
            longitude,
        })
    }

    pub fn latitude(&self) -> f64 {
        self.latitude
    }

    pub fn longitude(&self) -> f64 {
        self.longitude
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_valid_coordinate() {
        assert!(Coordinate::new(0.0, 0.0).is_ok());
        assert!(Coordinate::new(90.0, 180.0).is_ok());
        assert!(Coordinate::new(-90.0, -180.0).is_ok());
    }

    #[test]
    fn test_invalid_latitude() {
        let coord = Coordinate::new(90.1, 0.0);
        assert!(matches!(coord, Err(DomainError::ValidationError(_))));

        let coord = Coordinate::new(-90.1, 0.0);
        assert!(matches!(coord, Err(DomainError::ValidationError(_))));
    }

    #[test]
    fn test_invalid_longitude() {
        let coord = Coordinate::new(0.0, 180.1);
        assert!(matches!(coord, Err(DomainError::ValidationError(_))));

        let coord = Coordinate::new(0.0, -180.1);
        assert!(matches!(coord, Err(DomainError::ValidationError(_))));
    }
}
