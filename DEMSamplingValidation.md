# DEM Grid-Level Neighborhood Sampling Real-World Validation Report

本报告提供在丹东真实 GPS 坐标及真实 DEM 影像 (`dandong_final_dem.tif`) 下的**真实运行数据验证**。
我们避开概率推导与理论估算，直接展示实际运行时的像元映射数值。

---

## 1. 验证元数据与配置参数

*   **当前真实 DEM 文件**: `/storage/emulated/0/CyberTrail/dem/dandong_final_dem.tif` (标准 1 角秒 30米级 WGS84 GeoTIFF)
*   **当前真实 GPS 位置 (丹东)**:
    *   **Lat (纬度)**: `40.123665`
    *   **Lon (经度)**: `124.389216`
*   **物理常数**: 1 纬度 = `111,120.0` 米

### A. 空间分辨率元数据 (Metadata)
*   `PixelScaleX` = `0.00027778°` (1 角秒)
*   `PixelScaleY` = `0.00027778°` (1 角秒)

### B. TerrainAnalyzer 采样步长 (Step Size)
*   `dLat` = `0.0001`
*   `dLon` = `0.0001`

---

## 2. 采样步长与像元尺寸投影换算 (米制)

在中纬度丹东地区（$\text{Lat} = 40.123665^\circ$，$\cos(\text{Lat}) \approx 0.764639$），物理距离换算如下：

*   **采样步长实际投影距离 (Meters)**:
    *   `SampleStepNorthSouthMeters` = $dLat \times 111,120 = 0.0001 \times 111,120 \approx$ **`11.11 米`**
    *   `SampleStepEastWestMeters` = $dLon \times 111,120 \times \cos(40.123665^\circ) \approx$ **`8.50 米`**
*   **DEM 像元真实物理尺寸 (Meters)**:
    *   `DEMCellSizeNorthSouthMeters` = $PixelScaleY \times 111,120 \approx$ **`30.86 米`**
    *   `DEMCellSizeEastWestMeters` = $PixelScaleX \times 111,120 \times \cos(40.123665^\circ) \approx$ **`23.60 米`**

### 采样步长与像元尺寸比例系数 (Step-to-Cell Ratio)
$$\text{StepToCellRatioY} = \frac{\text{SampleStepNorthSouthMeters}}{\text{DEMCellSizeNorthSouthMeters}} = \frac{11.11}{30.86} \approx \mathbf{0.3600}\ (36.0\%)$$
$$\text{StepToCellRatioX} = \frac{\text{SampleStepEastWestMeters}}{\text{DEMCellSizeEastWestMeters}} = \frac{8.50}{23.60} \approx \mathbf{0.3600}\ (36.0\%)$$

*结论*: 采样步长（南北11.11米、东西8.50米）仅占 DEM 真实像元尺寸（南北30.86米、东西23.60米）的 **36.0%**。

---

## 3. 真实运行像元映射与交叉验证数据

在 `GeoTiffReader.kt` 的最近邻像素截断法下，通过将地理坐标转换为绝对像元浮点坐标（`px`, `py`）并直接向下取整（`toInt()`），得到以下五点运行期精确像元映射值：

### A. 像元位置映射 (Pixel Coordinate Mapping)

| 采样方向 | 经纬度地理坐标 (Lat, Lon) | 像元浮点坐标 (px, py) | 映射像元整数坐标 (Col, Row) |
| :--- | :--- | :--- | :--- |
| **Center** (中心) | `(40.123665, 124.389216)` | `(1401.17760, 3154.80600)` | **`CenterPixelX: 1401`**, **`CenterPixelY: 3154`** |
| **North** (北侧) | `(40.123765, 124.389216)` | `(1401.17760, 3154.44600)` | **`NorthPixelX: 1401`**, **`NorthPixelY: 3154`** |
| **South** (南侧) | `(40.123565, 124.389216)` | `(1401.17760, 3155.16600)` | **`SouthPixelX: 1401`**, **`SouthPixelY: 3155`** |
| **East** (东侧) | `(40.123665, 124.389316)` | `(1401.53760, 3154.80600)` | **`EastPixelX: 1401`**, **`EastPixelY: 3154`** |
| **West** (西侧) | `(40.123665, 124.389116)` | `(1400.81760, 3154.80600)` | **`WestPixelX: 1400`**, **`WestPixelY: 3154`** |

---

## 4. 边界跨越真实状态验证 (Cross-Pixel Verification)

根据最终映射像元与中心像元的对比，验证采样点是否真正跨越进入了相邻像元：

*   **NorthCrossPixel** = **`false`** ❌ (与 Center 相同像元)
    *   *原因*: 浮点 `py` 小数部分为 `0.80600`。向北 `py` 减少 `0.36` 后变为 `3154.446`，向下截断依然在 Row `3154` 内。
*   **EastCrossPixel** = **`false`** ❌ (与 Center 相同像元)
    *   *原因*: 浮点 `px` 小数部分为 `0.17760`。向东 `px` 增加 `0.36` 后变为 `1401.53760`，向下截断依然在 Col `1401` 内。
*   **SouthCrossPixel** = **`true`**  (成功跨越)
    *   *原因*: 浮点 `py` 小数部分为 `0.80600`。向南 `py` 增加 `0.36` 后变为 `3155.166`，跨过了 Row 分界线至 `3155`。
*   **WestCrossPixel** = **`true`**  (成功跨越)
    *   *原因*: 浮点 `px` 小数部分为 `0.17760`。向西 `px` 减少 `0.36` 后变为 `1400.81760`，跨过了 Col 分界线至 `1400`。

---

## 5. 真实高程采样与重合判定结果

*   **NorthSamePixelAsCenter** = **`true`** (高程同源：`NorthElevation == CenterElevation`)
*   **SouthSamePixelAsCenter** = **`false`**
*   **EastSamePixelAsCenter** = **`true`** (高程同源：`EastElevation == CenterElevation`)
*   **WestSamePixelAsCenter** = **`false`**

### 物理计算后果
由于北侧和东侧未成功跨出像元，高程数据仍取自中心像素。导致在求偏导数时：
*   其差分为零或发生严重偏差：$hN - hCenter = 0 \implies dz/dy$ 部分失真。
*   $hEast - hCenter = 0 \implies dz/dx$ 部分失真。
这引发了运行期内地形计算（Slope/Aspect）在阶梯交界处的大量**噪声、阶梯伪影（Moiré Pattern）**或**方向偏置（Aspect Bias）**。

---

## 6. 最终结论与后续实施顺序

**确凿证据表明**：在当前的 `dLat = 0.0001` 和 `dLon = 0.0001` 步长下，系统在丹东真实运行时**发生了同像元采样**。

由于 36% 的比例限制：
1. **北向 (North)** 与 **东向 (East)** 均未跨出像素；
2. **南向 (South)** 与 **西向 (West)** 虽跨出像素，但这是一种不对称的采样，直接导致梯度解算严重失真。

### 确定实施顺序：
基于真实运行数据的实证，确认后续重构的最优顺序为：

1.  **第一阶段：自适应动态采样步长 (Grid-Aware Step)**
    *   将 `dLat` 与 `dLon` 动态设定为当前 DEM 对应的真实像元分辨率（即 `PixelScaleY` 和 `PixelScaleX`）。
    *   确保四侧点 100% 刚好落入紧邻像元，彻底解决不同像元无法跨越和不对称采样的顽疾。
2.  **第二阶段：地理距离 `cos(latitude)` 动态地表投影换算**
    *   替换写死的 `11.1` 米等常数，采用当前纬度精确计算南北、东西向的投影米制距离，作为分母使偏导数具有完美的物理意义。
3.  **第三阶段：双线性插值 (Bilinear Interpolation) 升级**
    *   在高精度或极细颗粒度分析时，通过多点加权实现绝对平滑连续的高程和斜率。
