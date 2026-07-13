# CyberTrail DEM 文件要求说明

Version: 1.0

---

## 什么是 DEM

DEM（Digital Elevation Model，数字高程模型）用于提供：

- 海拔（Elevation）
- 坡度（Slope）
- 坡向（Aspect）

CyberTrail 不内置任何 DEM 数据。

用户需要自行准备并导入 DEM 文件。

---

## 支持格式

当前支持：

- GeoTIFF (*.tif)
- GeoTIFF (*.tiff)

要求：

- 单波段高程栅格
- 高程单位：米 (Meters)

---

## 坐标系要求

推荐：

- EPSG:4326 (WGS84)

即：

- 经纬度坐标

例如：

- Lat: 40.123
- Lon: 124.389

---

## 推荐 DEM 数据源

### Copernicus DEM GLO-30

推荐等级：★★★★★

特点：
- 全球覆盖
- 30m 分辨率
- 免费
- 数据稳定

适合：
- 登山
- 徒步
- 越野

### ALOS AW3D30

推荐等级：★★★★★

特点：
- 30m 分辨率
- 亚洲地区效果优秀

适合：
- 中国
- 日本
- 东亚地区

### SRTM

推荐等级：★★★★☆

特点：
- 全球覆盖
- 分辨率较低

适合：
- 大范围导航
- 概览地图

### LiDAR DEM

推荐等级：★★★★★

特点：
- 1m
- 5m
- 10m
- 高精度

适合：
- 山地
- 林区
- 专业测绘

---

## 分辨率要求

CyberTrail 不限制 DEM 分辨率。

支持：
- 1m
- 5m
- 10m
- 30m
- 90m

以及未来任何合法分辨率。

系统将自动读取 DEM 元数据中的实际像元尺寸。

例如：
- Pixel Size = 1m
- 或：Pixel Size = 30.87m

系统不会假设：
- 固定 30m
- 固定 90m

---

## DEM 覆盖范围要求

DEM 必须覆盖用户活动区域。

例如，丹东用户：
- 39N - 42N
- 123E - 126E

如果 DEM 不覆盖当前位置：
- Elevation = N/A
- Slope = N/A
- Aspect = N/A

属于正常现象。

---

## 文件存放位置

Android：
`/storage/emulated/0/CyberTrail/dem/`

示例：
- `dandong_dem.tif`
- `china_dem.tif`
- `liaoning_dem.tif`

---

## 推荐裁剪方式

推荐仅保留需要区域。

例如：
- 辽宁 DEM
- 丹东 DEM
- 元宝区 DEM
- 振安区 DEM

避免使用：
- 全球 DEM
- 全国 DEM

以减少：
- 存储占用
- 内存占用
- 读取时间

---

## 开发者说明

CyberTrail DEM 引擎必须：

1. **动态读取**：`ModelPixelScaleTag` 获取真实像元尺寸。
2. **禁止硬编码**：禁止 `dLat = 0.0001`、`dLon = 0.0001` 等硬编码采样距离。
3. **基于实际像元尺寸**：坡度与坡向计算必须基于 DEM 实际像元尺寸，而非固定假设（如 30m、90m）。
4. **即插即用**：未来任何用户提供的 GeoTIFF DEM（1m、5m、10m、30m、90m）均应无需修改代码直接支持。

---

## 推荐配置

- 普通户外用户：Copernicus GLO-30
- 专业用户：LiDAR 1m / LiDAR 5m
- 推荐格式：GeoTIFF (EPSG:4326), 单波段高程, 单位：米
