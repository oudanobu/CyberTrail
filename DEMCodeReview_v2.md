# DEM Pipeline Architecture & Sampling Code Review (v2)

本报告针对本项目的 Digital Elevation Model (DEM) 读取链路、坐标映射机制、邻域采样以及坡向（Aspect）/ 坡度（Slope）计算逻辑，进行了全方位的、更深层级的代码审查与数学论证。

报告重点回答为什么在实际运行中，**North / South / East / West 采样点会大概率与 Center 采样点重合落入同一个 DEM 像元**，从而导致坡度为 $0^\circ$、坡向退化为 $-1.0$ 的根本原因，并给出具体的物理与坐标数学纠偏建议。

---

## 1. DEM 核心数据流拓扑图

系统的数据读取与处理自底向上共分为四层，从磁盘二进制字节到 UI 诊断界面，数据流向如下所示：

```
+-------------------------------------------------------------------------------+
|                           1. STORAGE LAYER (物理存储层)                        |
|  - SRTM 数据: `/dem/*.hgt` (16-bit signed, big-endian binary)                 |
|  - Copernicus/ALOS/LiDAR 数据: `/dem/*.tif`, `/dem/*.tiff` (无损 LZW/Raw)      |
+---------------------------------------+---------------------------------------+
                                        |
                                        v
+-------------------------------------------------------------------------------+
|                          2. PROVIDER LAYER (引擎解析层)                        |
|  - SRTMProvider: 解析 HGT 字节，**原生支持 Bilinear 双线性插值**                  |
|  - CopernicusProvider / ALOSProvider: 实例化 GeoTiffReader                    |
|  - GeoTiffReader: 自研 TIFF 标记解析，**目前仅支持 Nearest Neighbor 最近邻**      |
+---------------------------------------+---------------------------------------+
                                        |
                                        v
+-------------------------------------------------------------------------------+
|                       3. GEOPROCESSING LAYER (测地分析层)                     |
|  - DEMSystem: 分发坐标高程查询请求                                              |
|  - TerrainAnalyzer:                                                           |
|    - 设定采样微步长 $dLat, dLon = 0.0001^\circ$ (固定偏移量，约 11.1 米)        |
|    - 差分求导计算 $dz/dx$ 和 $dz/dy$                                           |
|    - 计算物理坡度 (Slope) 与 GIS 顺时针坡向 (Aspect)                          |
+---------------------------------------+---------------------------------------+
                                        |
                                        v
+-------------------------------------------------------------------------------+
|                        4. PRESENTATION LAYER (展示与终端)                      |
|  - MapActivity: 获取 `AnalysisResult` 中的 `DEMSamplingDiagnostic` 数据        |
|  - HUD 终端: 实时输出 5 点像素坐标 (PixelX, PixelY) 及像元重合判定               |
+-------------------------------------------------------------------------------+
```

---

## 2. GeoTIFF 经纬度 → Pixel 坐标转换流程

对于 WGS84 (EPSG:4326) 地理坐标系下的 GeoTIFF 影像，高程查询由 `GeoTiffReader.kt` 的 `getPixelCoords` 函数完成：

```
              输入: Geographic Coordinates (lat, lon)
                                |
                                v
         +----------------------------------------------+
         | 1. 获取 TIFF 基准点及缩放比例:                |
         |    - tiepointX (最西端经度, 元数据 Tag 33922) |
         |    - tiepointY (最北端纬度, 元数据 Tag 33922) |
         |    - scaleX (东西向像元分辨率, Tag 33550)    |
         |    - scaleY (南北向像元分辨率, Tag 33550)    |
         +----------------------+-----------------------+
                                |
                                v
         +----------------------------------------------+
         | 2. 计算连续型像素座标 (Continuous Float):    |
         |    px = (lon - tiepointX) / scaleX           |
         |    py = (tiepointY - lat) / scaleY           |
         +----------------------+-----------------------+
                                |
                                v
         +----------------------------------------------+
         | 3. 最近邻离散整型化 (Nearest Truncation):    |
         |    col = px.toInt()                          |
         |    row = py.toInt()                          |
         |    (等价于在正数区间内执行向下取整: floor)     |
         +----------------------+-----------------------+
                                |
                                v
                 输出整型像素网格索引: (col, row)
```

---

## 3. 邻域采样微差求导流程图

`TerrainAnalyzer.kt` 采用中心有限差分法（Central Finite Difference）生成邻域，其处理流程如下：

```
                      P_N (lat + 0.0001°, lon)
                                 ^
                                 |  dLat = 0.0001° (~11.1m)
                                 |
P_W (lat, lon - 0.0001°) <---- P_Center ----> P_E (lat, lon + 0.0001°)
   dLon = 0.0001° (~11.1m)       |         dLon = 0.0001° (~11.1m)
                                 |
                                 v  dLat = 0.0001° (~11.1m)
                      P_S (lat - 0.0001°, lon)
```

```
                              [开始：analyzeLocation(lat, lon)]
                                              |
                                              v
                            [1. 设定 dLat = 0.0001, dLon = 0.0001]
                                              |
                            [2. 获取 Center 及其 4 邻域高程]
                               (hN, hS, hE, hW, hCenter)
                                              |
                     +------------------------+------------------------+
                     |                                                 |
                     v                                                 v
         [3. 差分斜率求导(dz/dx, dz/dy)]                  [4. 捕获像素和分辨率级诊断]
         dzDx = (hE - hW) / (2 * 11.1)                    - CenterPixel(X, Y)
         dzDy = (hN - hS) / (2 * 11.1)                    - 4邻域Pixel(X, Y)
                     |                                    - North/South/East/WestIsSamePixelAsCenter
                     v                                                 |
         [5. 坡度与坡向融合计算]                                         |
         Slope = atan(sqrt(dzDx^2 + dzDy^2))                           |
         Aspect = 90° - atan2(-dzDy, -dzDx)                            |
                     |                                                 |
                     +------------------------+------------------------+
                                              |
                                              v
                               [6. 组装并返回 AnalysisResult]
```

---

## 4. 核心问题深度剖析

### 问题 1：邻域采样的“同像元重合”陷阱 (The Overlap Trap)

在 `TerrainAnalyzer.kt` 中，邻域点是通过**硬编码的固定经纬度偏移量**生成的：
*   具体代码位置：`TerrainAnalyzer.kt` 第 83–84 行
    ```kotlin
    val dLat = 0.0001
    val dLon = 0.0001
    ```
*   该偏移量对应的地表物理距离约等于 **11.1 米**。

然而，我们常用的 DEM 栅格分辨率通常为：
*   **1 角秒 (1-arcsecond) DEM (如 Copernicus GLO-30, ALOS AW3D30):** 空间分辨率约 **30 米**（其在 EPSG:4326 下的 `scaleX = scaleY ≈ 0.00027778°`）。
*   **3 角秒 (3-arcsecond) DEM (如 SRTM-3):** 空间分辨率约 **90 米**（其在 EPSG:4326 下的 `scaleX = scaleY ≈ 0.00083333°`）。

#### 【数学论证：为什么一定会重合？】
以 30 米 DEM 为例。设定当前中心点落在某个像元的中心，其像素坐标为 `(col, row)`。
要让东侧邻域点 $P_E(lon + 0.0001^\circ)$ 落入下一个相邻像素，经度增加量 $dLon$ 必须跨越当前像元的边界。

在 `GeoTiffReader.kt` 的 `getPixelCoords` 中，像素坐标的变化量为：
$$\Delta px = \frac{dLon}{\text{scaleX}} = \frac{0.0001^\circ}{0.00027778^\circ} \approx 0.36 \text{ pixels}$$

由于 `GeoTiffReader.kt` 使用的是**最近邻截断法** (`col = px.toInt()`)，只有当浮点坐标跨越整数边界时，像元索引才会改变。

**概率学分析：**
设 $px$ 的小数部分为 $\{px\} \in [0, 1)$。
*   要想 $col(lon) = \lfloor px \rfloor$ 与 $col(lon + 0.0001) = \lfloor px + 0.36 \rfloor$ 落在**不同像元**，必须满足：
    $$\{px\} \ge 1.0 - 0.36 = 0.64$$
*   这意味着，**只有 36% 的概率**东/西采样点能落入不同像元！
*   **有高达 64% 的概率**，东侧或西侧采样点截断后的整型像素坐标与中心点**完全一致**。
*   如果是 90 米分辨率的 SRTM-3 影像，其像素变化量 $\Delta px \approx 0.12$，同像元重合的概率更是高达 **88%**！

#### 【重合的严重后果】
一旦发生重合（例如东、西采样点算出的 `PixelX` 与中心点相同，北、南采样点算出的 `PixelY` 与中心点相同）：
1.  从 DEM 得到的邻域高程全部等于中心高程：
    $$h_E = h_W = h_S = h_N = h_{Center}$$
2.  坡向求导偏导数变为 0：
    $$dzDx = \frac{h_E - h_W}{2 \times 11.1} = 0.0, \quad dzDy = \frac{h_N - h_S}{2 \times 11.1} = 0.0$$
3.  算出来的坡度（Slope）为 $0.0^\circ$（绝对水平），而坡向（Aspect）因分母为 0 且偏导全 0，被直接判定为平地（$-1.0$ 或 null）。
4.  这造成了明显的“锯齿状阶梯现象”：只有在极少数跨越像元边界的带状区域内，才能算出非 0 坡度，其余大片区域高程全部被最近邻离散化，坡度全零。

---

### 问题 2：DEM 分辨率来源与后续采样脱节

*   **元数据读取存在，但未用于采样决策：**
    `GeoTiffReader.kt` 的确能完美通过 IFD Tag 33550 读取 `scaleX` 和 `scaleY`（即 `PixelScale`）。
    然而，`TerrainAnalyzer.kt` 在进行 5 点高程采样时，**完全忽视了当前 DEM 的真实分辨率**。
    它自顾自地使用固定的 `dLat = 0.0001 / dLon = 0.0001` 进行请求，导致了上述的重合问题。
*   **不支持动态自适应步长：**
    高精度的 LiDAR DEM 分辨率可能高达 1 米（`scaleX ≈ 0.000009°`），而普通卫星 DEM 分辨率为 30 米或 90 米。如果一套固定步长（11.1米）打天下：
    - 在 1 米 DEM 上：11.1 米的步长属于**超大步长超采样**（跨越了 11 个像元），计算出的坡度已被过度平滑，失去了高精度微地形起伏的细节。
    - 在 90 米 DEM 上：11.1 米的步长属于**极度欠采样**，必然发生像元重合，高概率输出无效坡向。

---

### 问题 3：经纬度地理距离的纬度缩减变形 (`cos(latitude)` 缺失)

在 `TerrainAnalyzer.kt` 第 91–93 行：
```kotlin
val cellSideM = 11.1 // approx meters per 0.0001 degree
val dzDx = (hE - hW) / (2.0 * cellSideM)
val dzDy = (hN - hS) / (2.0 * cellSideM)
```
这里将东西向（$x$ 轴）和南北向（$y$ 轴）的差分地表距离全部固定为 `cellSideM = 11.1` 米。

根据大地测量学原理，地球赤道周长约 40,075 公里，纬线周长随纬度增加按余弦缩减：
*   **南北方向（纬度 $1^\circ$）：** 地表实际物理距离不随纬度剧烈变化，始终恒定为：
    $$1^\circ \text{ Latitude} \approx 111,120 \text{ 米} \implies 0.0001^\circ \approx 11.11 \text{ 米}$$
*   **东西方向（经度 $1^\circ$）：** 地表物理距离受纬度 $\phi$ 调制：
    $$1^\circ \text{ Longitude} \approx 111,120 \times \cos(\phi) \text{ 米} \implies 0.0001^\circ \approx 11.11 \times \cos(\phi) \text{ 米}$$

#### 【误差定量评估（若不进行 cos 修正）】
设我们在北纬 $45^\circ$ 进行采样，此时 $\cos(45^\circ) \approx 0.7071$。
*   真实的经度向 $0.0001^\circ$ 地表物理距离为：
    $$\Delta x = 11.11 \times 0.7071 \approx 7.85 \text{ 米}$$
*   然而，代码依然将其当作固定的 `cellSideM = 11.1` 米代入计算。
*   **对 $dz/dx$ 的影响：**
    计算公式原本为：
    $$dzDx_{true} = \frac{h_E - h_W}{2 \times 7.85}$$
    代码实际计算为：
    $$dzDx_{calc} = \frac{h_E - h_W}{2 \times 11.1}$$
    因此，算出来的偏导数被**人为缩小**了：
    $$dzDx_{calc} = 0.7071 \times dzDx_{true}$$

#### 【由此导致的坡度和坡向误差】
1.  **坡度（Slope）系统性偏小：**
    由于东西偏导 $dzDx$ 被强行压缩，综合坡度合成值 $\sqrt{dzDx^2 + dzDy^2}$ 将小于真实值，导致计算出的陡坡地带坡度变缓。
2.  **坡向（Aspect）严重偏向南北极：**
    由于东西向梯度 $dzDx$ 被系统性缩减，而南北向梯度 $dzDy$ 保持正常，合成的坡降向量会被强行向 $y$ 轴（南北向）拉拢。
    - **例：** 一个原本是正东北向（$45^\circ$）的真实坡面：
      真实梯度 $dzDx = -0.1$, $dzDy = -0.1 \implies \theta = \text{atan2}(-0.1, -0.1) = 135^\circ \implies Aspect = 90^\circ - 135^\circ = 315^\circ$（即西北坡方向/或按其定义的东北坡）。
      未修正时，代码算出的东西梯度变小：$dzDx_{calc} = -0.0707$, $dzDy_{calc} = -0.1$。
      合成角度变为 $\text{atan2}(-0.1, -0.0707) \approx 125.2^\circ \implies Aspect = 324.8^\circ$。
      这产生了高达 **$10^\circ$ 的 compass 倾斜误差**，且越往高纬度，东西向距离缩水越严重，误差呈非线性指数级放大。

---

### 问题 4：GeoTIFF 核心插值缺陷 (Nearest Neighbor 限制)

目前 `GeoTiffReader.kt` 仅实现了最简单的最近邻像素截断：
```kotlin
val col = px.toInt()
val row = py.toInt()
```
*   这相当于在空间上把高程场视作一个个拼贴的“平顶马赛克地砖”。
*   在“地砖”内部，任何微小的位置移动（小于 30 米像元范围）都不会引起高程值变化。
*   由于缺乏插值，该高程表面是**不连续、不可微（Non-differentiable）**的。在像元交界处，高程值会发生断崖式突变。
*   在突变边界上，会算出一个极大且不合常理的瞬时坡度；而在像元内部，坡度则死锁为 $0$。

#### 【对比 SRTMProvider】
相较之下，`SRTMProvider.kt` 则是教科书级的优秀实现。它在第 45–68 行原生使用了 **Bilinear（双线性）插值**：
*   不仅能读取当前的四个邻位像素 `(h00, h01, h10, h11)`，而且通过浮点小数残差 `weightRow` 和 `weightCol` 进行了连续加权投影。
*   即便采样步长极小（如 $0.0001^\circ$），插值算出来的高程也是连续变化的，这就使得 $h_N, h_S, h_E, h_W$ 始终互不相同且平滑过渡。
*   **结论：** 同样的 $0.0001^\circ$ 步长下，SRTM 能算处流畅连续的坡向图，而 Copernicus / ALOS Tiff 却大面积死锁，插值方式的代差是决定性原因。

---

## 5. 综合审查诊断对比表

| 审查维度 | SRTMProvider (HGT) | GeoTiffReader (TIF) | 理想 GIS 引擎标准 | 本项目现状评估 |
| :--- | :--- | :--- | :--- | :--- |
| **元数据分辨率读取** | 根据文件大小推断 (30m / 90m) | 从 ModelPixelScaleTag 动态读取 | 必须动态获取，不允许硬编码 | **通过** (GeoTiffReader 可提取 PixelScale) |
| **邻域采样微差步长** | 固定 $0.0001^\circ$ (约 11.1 米) | 固定 $0.0001^\circ$ (约 11.1 米) | 应当自适应像元缩放尺度 $1.0 \times \text{PixelScale}$ | 🔴 **不通过** (由于步长小于像元尺度，造成高概率采样重合) |
| **空间插值算法** | **Bilinear (双线性)** | **Nearest Neighbor (最近邻)** | 推荐 Bilinear / Bicubic 双三次 | 🔴 **不通过** (GeoTiffReader 的最近邻截断导致表面不可微) |
| **同像元采样概率** | 0% (插值保证了连续亚像素斜率) | **30m 下 64%，90m 下 88%** | 0% (采用连续插值或相邻像素格采样) | 🔴 **严重缺陷** (Copernicus/ALOS 表现为大面积坡度归零) |
| **经纬度地表距离修正** | 无偏导纬度修正 | 无偏导纬度修正 | 必须引入余弦因子 $\cos(\text{latitude})$ | 🔴 **不通过** (高纬度地区坡度变平、坡向朝南北极畸变) |

---

## 6. 系统重构与修复建议 (Fix Proposals)

在通过本审查并准备进入下一步公式优化前，我们建议对 DEM 管道进行如下三步重构：

### 建议 A：为 GeoTiffReader 升级双线性插值 (Bilinear)
将最近邻像素截断升级为双线性亚像素插值。
在 `GeoTiffReader.kt` 中重构 `getElevation`：

```kotlin
@Synchronized
fun getElevationBilinear(lat: Double, lon: Double): Double? {
    if (!initialized || raf == null) return null
    val (px, py) = getPixelCoords(lat, lon)
    
    // px, py 是连续的浮点像素坐标
    val col0 = Math.floor(px).toInt()
    val row0 = Math.floor(py).toInt()
    val col1 = col0 + 1
    val row1 = row0 + 1
    
    // 边界安全防护
    if (col0 !in 0 until imageWidth - 1 || row0 !in 0 until imageHeight - 1) {
        return getElevationNearest(lat, lon) // 降级为最近邻
    }
    
    val tx = px - col0
    val ty = py - row0
    
    val h00 = readPixelValueDirect(col0, row0) ?: return null
    val h01 = readPixelValueDirect(col1, row0) ?: return null
    val h10 = readPixelValueDirect(col0, row1) ?: return null
    val h11 = readPixelValueDirect(col1, row1) ?: return null
    
    // 双线性合成
    val top = h00 * (1.0 - tx) + h01 * tx
    val bottom = h10 * (1.0 - tx) + h11 * tx
    return top * (1.0 - ty) + bottom * ty
}
```

### 建议 B：采样步长与像元分辨率深度绑定 (Grid-Aware Step)
在 `TerrainAnalyzer.kt` 中，差分偏移量 $dLat$ 和 $dLon$ 应从当前 DEM 的 Resolution 动态推导，使其始终至少跨越 1.0 个像元：

```kotlin
// 自适应推算
val resolutionDeg = getDEMResolutionDegrees(lat, lon) ?: 0.00027778 // 默认30m
val dLat = resolutionDeg
val dLon = resolutionDeg
```
*   如此一来，在 30 米 DEM 下，采样点间距恰好是 30 米；在 90 米 DEM 下，采样点间距恰好是 90 米。
*   这样不仅完全断绝了“同像元采样”的物质土壤，也符合经典 GIS 空间分析中求算 3x3 栅格梯度的标准算子。

### 建议 C：偏导分母距离引入 `cos(latitude)` 动态地表换算
重构梯度和物理距离计算，抹平高低纬度形变：

```kotlin
val latRad = Math.toRadians(lat)
val dLat = 0.00027778 // 例如固定取 1 arcsecond
val dLon = 0.00027778

// 动态换算实际地表米数
val cellSideLatM = dLat * 111120.0
val cellSideLonM = dLon * 111120.0 * Math.cos(latRad)

// 偏导求算
val dzDx = (hE - hW) / (2.0 * cellSideLonM)
val dzDy = (hN - hS) / (2.0 * cellSideLatM)
```

---
*《DEM 采样链路审查报告第二版》编制完成，完全锁定当前模块，等待最终架构重構指令。*
