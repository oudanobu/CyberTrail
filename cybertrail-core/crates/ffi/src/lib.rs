use tracking::Tracker;

#[no_mangle]
pub extern "C" fn start_track() -> i64 {
    let tracker = Tracker::new();
    tracker.start()
}

#[no_mangle]
pub extern "C" fn end_track(id: i64, timestamp: i64) {
    tracking::end_track(id, timestamp);
}
