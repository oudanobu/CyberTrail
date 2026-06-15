# CyberTrail Architecture Constitution v1.0

## Project
- **Name:** CyberTrail
- **Subtitle:** Offline Tactical Hiking System
- **Target:** Android 13+
- **Paradigm:** Local First, Offline First
- **UI:** Cyberpunk Tactical UI

---

## Mission
构建一款完全离线运行的登山、徒步、探险记录系统。

**目标：**
- Android 13+
- ARM64
- 4GB RAM设备可运行
- 不依赖服务器
- 不依赖云服务
- 不依赖Google账号
- 不依赖在线地图

**核心能力：**
- GPS轨迹记录
- 气压计高度融合
- 海拔剖面分析
- Tactical HUD显示
- SQLite本地存储
- 后续支持AR视觉锚点

---

## Core Principles

### Principle 1: Local First
本地数据库永远是真实数据源。
任何网络能力均不得成为核心功能依赖。

### Principle 2: Offline First
断网状态下：
必须保证：
- 记录轨迹
- 查看轨迹
- 保存数据
- 搜索数据
全部正常工作。

### Principle 3: Battery First
优先考虑续航。
禁止：
- 1秒GPS刷新
- 无意义后台任务
- 持续WakeLock
- 高频数据库写入

### Principle 4: Correctness First
优先级：
Correctness » Security » Reliability » Maintainability » Performance » Development Speed

---

## Architecture
严格遵守：
`UI` ↓ `Application` ↓ `Domain` ↑ `Infrastructure`

**禁止：**
- UI → SQLite
- UI → SQL
- UI → Sensor
- UI → Android Location API
- Domain → Android
- Domain → SQLite
- Domain → Infrastructure

**允许：**
- Infrastructure → Domain
- Application → Domain
- Application → Repository Trait

**禁止循环依赖。**

---

## Cargo Workspace
必须采用Workspace。

**结构：**
```text
crates/
├── common
├── domain
├── application
├── database
├── sensors
├── tracking
├── altitude
├── navigation
├── rendering
├── infrastructure
└── ffi
```

**禁止：** 单crate巨石项目。

---

## Module Responsibilities

### `common`
共享基础模块。
- **允许:** Error, Result, Time, Uuid, GeoMath
- **禁止:** 业务逻辑。

### `domain`
纯业务层。
- **包含:** Track, TrackPoint, Waypoint, Anchor, Value Objects, Repository Traits, Domain Events
- **禁止:** Android, SQLite, ARCore, Tokio Runtime, 网络库

### `application`
Use Case层。
- **负责:** 协调业务流程。
- **禁止:** 直接访问SQLite。

### `sensors`
统一采集。
- **包含:** GPS, Barometer, Accelerometer, Gyroscope, Magnetometer
- **统一输出:** SensorEvent

### `tracking`
- **负责:** 距离采样, 轨迹过滤, 轨迹统计, 轨迹压缩

### `altitude`
- **负责:** GPS高度, BARO高度, 融合, 累计爬升, 累计下降, VSpeed

### `navigation`
- **负责:** 距离, 方位角, 航向
- *V1不实现路径规划*。

### `rendering`
- **负责:** HUD, 轨迹线, 高度曲线, 战术扫描UI
- **禁止:** 直接操作数据库。

### `database`
- **负责:** SQLite, Migration, Prepared Statement, Repository实现

### `infrastructure`
- **负责:** Repository实现, 传感器实现, 系统适配器

### `ffi`
- **负责:** Rust与Android桥接。唯一跨语言边界。

---

## Database Rules
**SQLite Only**
**必须:**
- WAL模式
- Foreign Key开启
- Migration管理
- Prepared Statement

**禁止:**
- `SELECT *`
必须显式字段。

---

## Tracking Rules
**GPS采样采用:**
- 距离触发，而非时间触发
- 默认: 5米采样

**必须支持:**
- Kalman Filter
- 轨迹去抖动

**禁止:** 每秒固定记录。

---

## Altitude Rules
- **GPS高度:** 用于绝对高度
- **BARO高度:** 用于相对高度

**必须支持:**
- 海拔校准
- Altitude Zeroing

---

## Memory Rules
**禁止:**无限增长容器
- HashMap持续增长
- Vec无限增长

**缓存必须定义:**
- max_size
- ttl
- eviction policy

---

## Logging Rules
**统一使用:** `tracing`
**允许:** trace, debug, info, warn, error
**禁止:** `println!`, `eprintln!`, `dbg!`

---

## Security Rules
**禁止:**
- 硬编码密钥
- 日志输出密钥
- 日志输出敏感数据

**禁止自行实现:**
- AES
- SHA
- Argon2
- 随机数生成器
*必须使用成熟库。*

---

## Rust Rules
**必须:** Result, Option, Trait, Generics, Iterator
**禁止:** `unwrap()`, `expect()`, `panic!()`, `Rc<RefCell>`, 全局可变状态, `unsafe` (例外: 测试代码)。

---

## Async Rules
**禁止:**
- 持有MutexGuard跨await
- 同步阻塞Tokio Runtime

**优先:** `tokio::fs`, `tokio::task`

---

## Testing Rules
**每个模块必须提供:** Unit Test, Integration Test
**覆盖:** 正常路径, 错误路径, 边界条件
**目标覆盖率:** 80%以上

---

## Performance Targets
- **APK:** <20MB
- **启动:** <1秒
- **空闲内存:** <100MB
- **搜索:** <200ms
- **8小时连续轨迹记录:** 稳定运行

---

## V1 Scope
**只允许开发:**
GPS, Barometer, SQLite, Track Recording, Track Statistics, Altitude Profile, Cyberpunk HUD, Track History, Settings

**禁止开发:**
AR Anchor, 地图下载, 在线同步, 用户账号, 云服务, AI功能, 路径规划

---

## AI Output Contract
**禁止输出:**
`TODO`, `FIXME`, `unimplemented!()`, `todo!()`, `panic!()`, 占位代码, 伪代码, 教学示例

**必须输出:**
生产级代码, 完整错误处理, 完整注释, 完整测试, 可编译实现

输出代码前必须进行：
- Borrow Checker审计
- 生命周期审计
- Send/Sync审计
- Clippy审计
- 错误传播审计

**所有代码必须符合本宪法。如出现冲突，以本文件为最高规则。**
