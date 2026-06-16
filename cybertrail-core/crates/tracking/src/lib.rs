pub struct Tracker;

impl Tracker {
    pub fn new() -> Self {
        Self
    }

    pub fn start(&self) -> i64 {
        1
    }
}

pub fn end_track(_id: i64, _ts: i64) {
    // SQLite write placeholder
}
