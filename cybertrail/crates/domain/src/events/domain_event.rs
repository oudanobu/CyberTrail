use crate::value_objects::identifiers::{TrackId, WaypointId};
use crate::value_objects::revision::Revision;

#[derive(Debug, Clone, PartialEq)]
pub enum DomainEvent {
    TrackCreated { track_id: TrackId, timestamp: i64 },
    TrackRenamed { track_id: TrackId, revision: Revision, timestamp: i64 },
    TrackDeleted { track_id: TrackId, revision: Revision, timestamp: i64 },
    TrackRestored { track_id: TrackId, revision: Revision, timestamp: i64 },
    
    WaypointCreated { waypoint_id: WaypointId, timestamp: i64 },
    WaypointRenamed { waypoint_id: WaypointId, revision: Revision, timestamp: i64 },
    WaypointMoved { waypoint_id: WaypointId, revision: Revision, timestamp: i64 },
    WaypointDeleted { waypoint_id: WaypointId, revision: Revision, timestamp: i64 },
    WaypointRestored { waypoint_id: WaypointId, revision: Revision, timestamp: i64 },
}
