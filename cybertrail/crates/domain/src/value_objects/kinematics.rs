use crate::errors::domain_error::DomainError;
use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Copy, PartialEq, PartialOrd, Serialize, Deserialize)]
pub struct Distance(f64);

impl Distance {
    pub fn new(value: f64) -> Result<Self, DomainError> {
        // Handle NaN implicitly by !(... >= ...)
        if value >= 0.0 {
            Ok(Self(value))
        } else {
            Err(DomainError::ValidationError(format!(
                "Distance cannot be negative, got {}",
                value
            )))
        }
    }

    pub fn value(&self) -> f64 {
        self.0
    }

    pub fn zero() -> Self {
        Self(0.0)
    }
}

#[derive(Debug, Clone, Copy, PartialEq, PartialOrd, Serialize, Deserialize)]
pub struct Speed(f64);

impl Speed {
    pub fn new(value: f64) -> Result<Self, DomainError> {
        if value >= 0.0 {
            Ok(Self(value))
        } else {
            Err(DomainError::ValidationError(format!(
                "Speed cannot be negative, got {}",
                value
            )))
        }
    }

    pub fn value(&self) -> f64 {
        self.0
    }

    pub fn zero() -> Self {
        Self(0.0)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_valid_distance() {
        assert!(Distance::new(0.0).is_ok());
        assert!(Distance::new(100.5).is_ok());
        assert_eq!(Distance::zero().value(), 0.0);
    }

    #[test]
    fn test_invalid_distance() {
        let dist = Distance::new(-0.1);
        assert!(matches!(dist, Err(DomainError::ValidationError(_))));
        
        let dist = Distance::new(f64::NAN);
        assert!(matches!(dist, Err(DomainError::ValidationError(_))));
    }

    #[test]
    fn test_valid_speed() {
        assert!(Speed::new(0.0).is_ok());
        assert!(Speed::new(5.0).is_ok());
        assert_eq!(Speed::zero().value(), 0.0);
    }

    #[test]
    fn test_invalid_speed() {
        let spd = Speed::new(-1.0);
        assert!(matches!(spd, Err(DomainError::ValidationError(_))));

        let spd = Speed::new(f64::NAN);
        assert!(matches!(spd, Err(DomainError::ValidationError(_))));
    }
}
