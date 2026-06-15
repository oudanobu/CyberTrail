use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Copy, PartialEq, Eq, PartialOrd, Ord, Serialize, Deserialize)]
pub struct Revision(u64);

impl Revision {
    pub fn new(value: u64) -> Self {
        Self(value)
    }

    pub fn value(&self) -> u64 {
        self.0
    }

    pub fn increment(&self) -> Self {
        Self(self.0 + 1)
    }

    pub fn initial() -> Self {
        Self(1)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_revision_increment() {
        let rev = Revision::new(1);
        let next = rev.increment();
        assert_eq!(next.value(), 2);
    }

    #[test]
    fn test_revision_comparison() {
        let rev1 = Revision::new(1);
        let rev2 = Revision::new(2);
        assert!(rev1 < rev2);
    }
}
