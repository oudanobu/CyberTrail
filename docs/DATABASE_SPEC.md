# CyberTrail Database Specification
Version: 1.0
Database Engine: SQLite

---

## Database Philosophy
SQLite是唯一真实数据源。
所有业务数据必须持久化到SQLite。

禁止：
JSON文件数据库
内存数据库作为主存储
第三方数据库

---

## SQLite Configuration
启动时必须执行：
PRAGMA journal_mode = WAL;
PRAGMA foreign_keys = ON;
PRAGMA synchronous = NORMAL;
PRAGMA temp_store = MEMORY;
PRAGMA cache_size = -20000;

---

## Schema Versioning
必须使用Migration。

禁止：
直接修改Schema。

---

## Tables

### tracks
记录一次完整活动。
```sql
CREATE TABLE tracks (
id TEXT PRIMARY KEY,
started_at INTEGER NOT NULL,
ended_at INTEGER,
duration_seconds INTEGER NOT NULL,
distance_m REAL NOT NULL,
ascent_m REAL NOT NULL,
descent_m REAL NOT NULL,
avg_speed REAL NOT NULL,
max_speed REAL NOT NULL,
max_altitude REAL NOT NULL,
min_altitude REAL NOT NULL
);
```

---

### track_points
轨迹点。
```sql
CREATE TABLE track_points (
id INTEGER PRIMARY KEY AUTOINCREMENT,
track_id TEXT NOT NULL,
lat REAL NOT NULL,
lon REAL NOT NULL,
altitude REAL NOT NULL,
pressure REAL,
speed REAL,
heading REAL,
timestamp INTEGER NOT NULL,
FOREIGN KEY(track_id) REFERENCES tracks(id)
);
```

---

### settings
用户配置。
```sql
CREATE TABLE settings (
key TEXT PRIMARY KEY,
value TEXT NOT NULL
);
```

---

## Indexes
```sql
CREATE INDEX idx_track_points_track_id ON track_points(track_id);
CREATE INDEX idx_track_points_timestamp ON track_points(timestamp);
```

---

## Query Rules
禁止：
SELECT *

必须：
显式字段查询。

---

## Data Retention
默认永久保存。
禁止自动删除。

---

## Corruption Recovery
发现数据库损坏：
尝试：
Integrity Check
失败：
进入只读恢复模式
禁止崩溃。
