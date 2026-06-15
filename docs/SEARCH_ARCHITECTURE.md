# CyberTrail Search Architecture
Version: 1.0
Status: Frozen

---

## 1. Search Pipeline

### A. Creation & Update Flow (Indexing)
1. **Domain Mutation**: User saves a Track, updates a Waypoint note, or adds an Attachment metadata.
2. **SQLite Transaction**: The core domain table (e.g., `tracks`, `waypoints`) is updated.
3. **Trigger Execution**: An asynchronous or synchronous SQLite trigger automatically updates the `search_index` FTS5 virtual table.
4. **Encryption Consideration**: Search indexes must be built *before* payload encryption or operate on decrypted local copies. The index itself is stored in the local SQLite database.

### B. Deletion Flow (Tombstoning)
1. User deletes a Track or Waypoint.
2. `is_deleted` is set to `1` in the domain table.
3. **Trigger Execution**: The SQLite trigger issues a `DELETE FROM search_index WHERE entity_id = ?`. This ensures deleted items are immediately removed from search results.

---

## 2. FTS5 Schema

### A. Virtual Table Structure
```sql
CREATE VIRTUAL TABLE search_index USING fts5(
    entity_id UNINDEXED,   -- UUID of the Track, Waypoint, Note, etc.
    entity_type UNINDEXED, -- 'TRACK', 'WAYPOINT', 'ATTACHMENT', 'TAG'
    title,                 -- Track Name, Waypoint Name, Attachment Title
    content,               -- Track Description, Waypoint Note, Tag Names
    tokenize='unicode61 remove_diacritics 1' -- Multi-language padding
);
```

### B. Triggers
```sql
-- Example: After Insert on Waypoints
CREATE TRIGGER after_waypoint_insert AFTER INSERT ON waypoints
BEGIN
  INSERT INTO search_index(rowid, entity_id, entity_type, title, content)
  VALUES (new.rowid, new.id, 'WAYPOINT', new.name, new.notes);
END;

-- Example: After Update on Waypoints
CREATE TRIGGER after_waypoint_update AFTER UPDATE ON waypoints
BEGIN
  DELETE FROM search_index WHERE entity_id = old.id;
  INSERT INTO search_index(rowid, entity_id, entity_type, title, content)
  VALUES (new.rowid, new.id, 'WAYPOINT', new.name, new.notes);
END;
```

---

## 3. Ranking Strategy

- **BM25 Algorithm**: FTS5 utilizes the `bm25()` function by default for relevance scoring.
- **Custom Weighting**:
  - `title` field matched -> **High Weight** (e.g., 10.0)
  - `content` field matched -> **Normal Weight** (e.g., 1.0)
- **Time/Favorite Boost**: We inject an `ORDER BY rank, created_at DESC` bias so recent records appear higher when relevancy is tied.

---

## 4. Memory & Performance Analysis

**Scenario**: 10,000 Tracks, 100,000 Waypoints, 50,000 Tags/Attachments.

- **Data Size**: ~160,000 rows in the `search_index` FTS5 table.
- **Speed**: Typical queries (e.g., `SELECT * FROM search_index WHERE search_index MATCH 'camp*'`) will execute in `<50ms`.
- **Memory Overhead**: FTS5 uses a background merge process. By setting `PRAGMA cache_size = -20000;` (as defined in `DATABASE_SPEC`), SQLite will utilize up to 20MB of RAM for caching, ensuring lightning-fast text lookups without breaching the Android 100MB idle memory limit target.
