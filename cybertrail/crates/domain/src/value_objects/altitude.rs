use crate::errors::domain_error::DomainError;
use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Copy, PartialEq, PartialOrd, Serialize, Deserialize)]
pub struct Altitude(f64);

impl Altitude {
    pub fn new(value: f64) -> Result<Self, DomainError> {
        if (-500.0..=12000.0).contains(&value) {
            Ok(Self(value))
        } else {
            Err(DomainError::ValidationError(format!(
                "Altitude must be between -500.0 and 12000.0, got {}",
                value
            )))
        }
    }

    pub fn value(&self) -> f64 {
        self.0
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_valid_altitude() {
        assert!(Altitude::new(0.0).is_ok());
        assert!(Altitude::new(-500.0).is_ok());
        assert!(Altitude::new(12000.0).is_ok());
        assert!(Altitude::new(8848.0).is_ok()); // Mt Everest
    }

    #[test]
    fn test_invalid_altitude() {
        let alt = Altitude::new(-500.1);
        assert!(matches!(alt, Err(DomainError::ValidationError(_))));

        let alt = Altitude::new(12000.1);
        assert!(matches!(alt, Err(DomainError::ValidationError(_))));
    }
}
