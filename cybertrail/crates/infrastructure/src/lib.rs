pub mod sqlite;

#[cfg(test)]
mod tests {
    use super::sqlite::connection::{create_in_memory_pool, setup_schema};
    use super::sqlite::track_repository::SqliteTrackRepository;
    use super::sqlite::waypoint_repository::SqliteWaypointRepository;
    use super::sqlite::track_point_repository::SqliteTrackPointRepository;
    use domain::repositories::track_repository::TrackRepository;
    use domain::repositories::waypoint_repository::WaypointRepository;
    use domain::repositories::track_point_repository::TrackPointRepository;
    use domain::entities::track::Track;
    use domain::entities::waypoint::Waypoint;
    use domain::entities::track_point::TrackPoint;
    use domain::value_objects::identifiers::{TrackId, WaypointId};
    use domain::value_objects::coordinate::Coordinate;
    use domain::value_objects::altitude::Altitude;
    
    #[tokio::test]
    async fn test_track_repository_lifecycle() {
        let pool = create_in_memory_pool().unwrap();
        {
            let conn = pool.get().unwrap();
            setup_schema(&conn).unwrap();
        }
        
        let repo = SqliteTrackRepository::new(pool);
        
        let track_id = TrackId::generate();
        let (mut track, _) = Track::new(track_id, "Test Hike", 1000).unwrap();
        
        // Save
        repo.save(&track).await.unwrap();
        
        // Exists
        assert!(repo.exists(track_id).await.unwrap());
        
        // Find
        let loaded_track = repo.find_by_id(track_id).await.unwrap().unwrap();
        assert_eq!(loaded_track.name(), "Test Hike");
        assert_eq!(loaded_track.revision().value(), 1);
        
        // Update
        track.rename("Modified Hike", 2000).unwrap();
        repo.save(&track).await.unwrap();
        
        let loaded_track = repo.find_by_id(track_id).await.unwrap().unwrap();
        assert_eq!(loaded_track.name(), "Modified Hike");
        assert_eq!(loaded_track.revision().value(), 2);
    }
    
    #[tokio::test]
    async fn test_waypoint_repository_lifecycle() {
        let pool = create_in_memory_pool().unwrap();
        {
            let conn = pool.get().unwrap();
            setup_schema(&conn).unwrap();
        }
        
        let track_repo = SqliteTrackRepository::new(pool.clone());
        let wp_repo = SqliteWaypointRepository::new(pool.clone());
        
        // Save a track first due to foreign key
        let track_id = TrackId::generate();
        let (track, _) = Track::new(track_id, "Test Hike", 1000).unwrap();
        track_repo.save(&track).await.unwrap();
        
        // Create waypoint
        let wp_id = WaypointId::generate();
        let coord = Coordinate::new(45.0, 90.0).unwrap();
        let (mut wp, _) = Waypoint::new(wp_id, Some(track_id), "Camp 1", coord, None, None, 1000).unwrap();
        
        // Save
        wp_repo.save(&wp).await.unwrap();
        
        assert!(wp_repo.exists(wp_id).await.unwrap());
        
        let loaded_wp = wp_repo.find_by_id(wp_id).await.unwrap().unwrap();
        assert_eq!(loaded_wp.name(), "Camp 1");
        
        let by_track = wp_repo.find_by_track_id(track_id).await.unwrap();
        assert_eq!(by_track.len(), 1);
        
        // Update
        wp.rename("Camp Alpha", 2000).unwrap();
        wp_repo.save(&wp).await.unwrap();
        
        let loaded_wp = wp_repo.find_by_id(wp_id).await.unwrap().unwrap();
        assert_eq!(loaded_wp.name(), "Camp Alpha");
        assert_eq!(loaded_wp.revision().value(), 2);
    }

    #[tokio::test]
    async fn test_track_point_repository_lifecycle() {
        let pool = create_in_memory_pool().unwrap();
        {
            let conn = pool.get().unwrap();
            setup_schema(&conn).unwrap();
        }

        let track_repo = SqliteTrackRepository::new(pool.clone());
        let tp_repo = SqliteTrackPointRepository::new(pool.clone());

        // Save a track first due to foreign key
        let track_id = TrackId::generate();
        let (track, _) = Track::new(track_id, "Test Hike", 1000).unwrap();
        track_repo.save(&track).await.unwrap();

        let coord1 = Coordinate::new(45.123456, 90.123456).unwrap();
        let alt1 = Altitude::new(100.5).unwrap();
        let tp1 = TrackPoint::new(track_id, 1000, coord1, Some(alt1));
        
        let coord2 = Coordinate::new(45.123500, 90.123500).unwrap();
        let alt2 = Altitude::new(101.0).unwrap();
        let tp2 = TrackPoint::new(track_id, 2000, coord2, Some(alt2));

        tp_repo.append_track_points(&[tp1, tp2]).await.unwrap();

        let count = tp_repo.count_track_points(track_id).await.unwrap();
        assert_eq!(count, 2);

        let first = tp_repo.first_track_point(track_id).await.unwrap().unwrap();
        assert_eq!(first.timestamp(), 1000);

        let last = tp_repo.last_track_point(track_id).await.unwrap().unwrap();
        assert_eq!(last.timestamp(), 2000);

        let stream = tp_repo.stream_track_points(track_id, 0, 100).await.unwrap();
        assert_eq!(stream.len(), 2);

        tp_repo.delete_track_points_by_track(track_id).await.unwrap();
        let count = tp_repo.count_track_points(track_id).await.unwrap();
        assert_eq!(count, 0);
    }
}
