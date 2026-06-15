# CyberTrail Domain Model & Database Schema Blueprint
Version: 1.0 (Foundation for V5 Sync & Encryption)

*Note: I noticed your prompt referenced "Sovereign Notes," but we are building **CyberTrail**. I have applied the exact professional software engineering methodology you provided (Tombstones, detached Sync/Crypto metadata, FTS5 search) directly to the CyberTrail domain to ensure future-proof stability.*

---

## 1. Core Principles
- **Local First & Offline First**: SQLite is the single source of truth.
- **Future-Proof for V5 (Sovereign Sync)**: Incorporate Tombstones (Soft Deletion) and detached synchronization metadata now, so V1 doesn't need data migration when V5 Sync arrives.
- **End-to-End Encryption Ready**: Encryption architecture is mapped, keeping crypto material out of core domain tables.

---

## 2. Domain Model

### Aggregate Roots
**`Track`**
- **Lifecycle**: Created at `start_tracking()`. Mutated during recording (updating distance, duration). Frozen at `stop_tracking()`. Can be soft-deleted.
- **Ownership**: Owns `TrackPoint` and `Waypoint` entities linked to this session.

### Entities
**`TrackPoint`**
- **Lifecycle**: Immutable once recorded. Written in high-frequency batches.
- **Relationships**: Strictly bound to one `Track`.

**`Waypoint`**
- **Lifecycle**: Created actively by the user during tracking, or decoupled from tracking. Can be soft-deleted.

### Value Objects
- `Latitude` / `Longitude` (WGS84, strictly bounded)
- `Altitude` (Meters)
- `Distance` (Meters, positive only)
- `Speed` (m/s)
- `Heading` (Azimuth 0-360)

---

## 3. SQLite Schema (DDL)

### A. Core Tables

```sql
-- Tracks (Aggregate Root)
CREATE TABLE tracks (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    started_at INTEGER NOT NULL,
    ended_at INTEGER,
    duration_seconds INTEGER NOT NULL DEFAULT 0,
    distance_m REAL NOT NULL DEFAULT 0.0,
    ascent_m REAL NOT NULL DEFAULT 0.0,
    descent_m REAL NOT NULL DEFAULT 0.0,
    avg_speed REAL NOT NULL DEFAULT 0.0,
    max_speed REAL NOT NULL DEFAULT 0.0,
    max_altitude REAL NOT NULL DEFAULT 0.0,
    min_altitude REAL NOT NULL DEFAULT 0.0,
    
    -- Tombstone for future Sync
    is_deleted INTEGER NOT NULL DEFAULT 0, 
    updated_at INTEGER NOT NULL
);

-- Track Points (High Frequency Data)
-- Note: Points are immutable, no triggers or updated_at needed.
CREATE TABLE track_points (
    id TEXT PRIMARY KEY,
    track_id TEXT NOT NULL,
    lat REAL NOT NULL,
    lng REAL NOT NULL,
    altitude REAL NOT NULL,
    pressure REAL,
    speed REAL,
    heading REAL,
    timestamp INTEGER NOT NULL,
    
    FOREIGN KEY(track_id) REFERENCES tracks(id) ON DELETE CASCADE
);

-- Waypoints (POIs)
CREATE TABLE waypoints (
    id TEXT PRIMARY KEY,
    track_id TEXT, -- Optional, can be independent
    name TEXT NOT NULL,
    lat REAL NOT NULL,
    lng REAL NOT NULL,
    altitude REAL,
    notes TEXT,
    
    is_deleted INTEGER NOT NULL DEFAULT 0,
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL,
    
    FOREIGN KEY(track_id) REFERENCES tracks(id) ON DELETE CASCADE
);
```

### B. Search Architecture (FTS5)

To meet the `<200ms` search target for history tracking, we use FTS5 for text fields.

```sql
-- FTS5 Virtual Table for Track and Waypoint searching
CREATE VIRTUAL TABLE search_index USING fts5(
    entity_id UNINDEXED, -- References Track or Waypoint ID
    entity_type UNINDEXED, -- 'TRACK' or 'WAYPOINT'
    content, -- The searchable text (Track Name, Waypoint Name, Notes)
    tokenize='unicode61' -- Supports multi-language
);

-- Triggers to auto-update FTS5 on Waypoint/Track insert, update, or soft-delete
```

### C. Future Synchronization Metadata (V5 Prep)

We decouple sync logic from the domain tables. This means no WebDAV or ETags leak into the `Track` struct.

```sql
CREATE TABLE sync_metadata (
    entity_id TEXT NOT NULL,
    entity_type TEXT NOT NULL, -- 'TRACK', 'WAYPOINT'
    local_revision INTEGER NOT NULL DEFAULT 1,
    remote_revision INTEGER,
    etag TEXT,
    last_sync_at INTEGER,
    
    PRIMARY KEY (entity_id, entity_type)
);

CREATE TABLE sync_conflicts (
    id TEXT PRIMARY KEY,
    entity_id TEXT NOT NULL,
    conflict_data BLOB NOT NULL, -- Serialized remote state
    created_at INTEGER NOT NULL
);
```

### D. Encryption Architecture (V5 Prep)

```sql
CREATE TABLE encryption_metadata (
    entity_id TEXT PRIMARY KEY, -- Maps to a Track or Attachment
    key_id TEXT NOT NULL,       -- References Master Key generation
    algorithm TEXT NOT NULL,    -- e.g., 'AES256-GCM'
    nonce BLOB NOT NULL,
    mac BLOB NOT NULL
);
```

### E. Indexes
```sql
CREATE INDEX idx_trackpoints_track_time ON track_points(track_id, timestamp);
CREATE INDEX idx_tracks_deleted ON tracks(is_deleted);
CREATE INDEX idx_sync_metadata_revisions ON sync_metadata(local_revision, remote_revision);
```

---

## 4. Delta Sync Protocol & Tombstones (Design)

**Resolution Strategy (V5):**
1. Device A updates `Track` name. Output: `local_revision += 1`, `updated_at = now()`.
2. Sync Engine queries: `SELECT * FROM tracks JOIN sync_metadata WHERE local_revision > remote_revision`.
3. If Remote WebDAV ETag changed: Conflict detected.
4. **Resolution**: `merge_conflict` (Appends tracking logic, or keeps latest user-edited text).
5. **Deletion**: Deleting a track sets `is_deleted = 1` (Tombstone). Sync engine pushes the tombstone. Other devices receive it and hide the track.

---

## 5. Data Volume & Capacity Evaluation

**Scenario**: 10,000 Tracks / 50,000 Waypoints / Intense Tracking

*   **1 Track (8 hours, 5m sampling)**: ~4,000 track points.
*   **Total Points**: ~40,000,000 rows in `track_points`.
*   **Storage Indexing Size**: ~1.2 GB SQLite File size.

**Memory & Performance Analysis**:
*   `idx_trackpoints_track_time` ensures point querying for drawing the Height Profile chart is an `O(log N)` lookup.
*   **RAM Limits**: The system reads points in `LIMIT/OFFSET` chunks for history, preventing the 4GB RAM threshold from breaching. During active tracking, points are collected in memory `VecDeque<TrackPoint>` (max 50) and flushed asynchronously via Tokio threads.
*   **Search**: FTS5 allows `<200ms` instantaneous text retrieval across 10,000 tracks via a specialized inverted index.
