# CyberTrail Development Roadmap
Version: 1.0

---

## Vision
**CyberTrail**
Offline Tactical Hiking System
Android 13+
Local First
Offline First
Cyberpunk Tactical UI

---

## V1 — Tactical Recorder
**目标：**
完成可靠的离线轨迹记录系统。

### Features
**GPS Tracking**
支持：
- 距离采样
- Kalman滤波
- 轨迹压缩

**Altitude System**
支持：
- GPS高度
- BARO高度
- 海拔校准

输出：
- 当前海拔
- 累计爬升
- 累计下降
- VSpeed

**Local Database**
SQLite
支持：
- 保存轨迹
- 查询轨迹
- 删除轨迹

**HUD**
显示：
- Heading
- Altitude
- Distance
- Speed
- Ascent
- Descent

**Height Profile**
显示：
实时高度曲线

**History**
查看：
历史轨迹
统计信息

### V1 Success Criteria
连续记录：
8小时+
无崩溃

启动：
<1秒

内存：
<100MB

APK：
<20MB

---

## V2 — Visual Anchor System
**目标：**
实现AR视觉重定位。

### Features
Visual Anchor
Anchor Matching
Local Relocalization
Anchor Database

支持：
建筑物
岩石
路牌
人工地标

禁止：
Cloud Anchor
在线识别

---

## V3 — Tactical Map
**目标：**
构建纯离线战术地图。

### Features
Vector Terrain
Track Overlay
Waypoint Layer
Danger Zone Layer
Anchor Layer

地图全部本地存储。

---

## V4 — Expedition Toolkit
**目标：**
增强户外能力。

### Features
Emergency Beacon
Weather Import
GPX Import
GPX Export
Route Planning

---

## V5 — Sovereign Expedition Platform
**目标：**
形成完整探险平台。

### Features
Multi Device Sync
Encrypted Backup
Desktop Client
Linux Support
WebDAV Sync
End-to-End Encryption

---

## Development Rule
必须：
按版本顺序开发。

禁止：
开发未来版本功能。

例如：
V1阶段禁止：
AR
WebDAV
同步
账号系统
AI分析
路径规划

如发现功能属于未来版本：
必须拒绝实现。
