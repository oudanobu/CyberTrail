# DEM Pipeline Code Review & Diagnostic Report

This report documents a comprehensive, end-to-end code review of the Digital Elevation Model (DEM) reading, coordinate conversion, and geoprocessing pipeline. It analyzes the interaction between `TerrainAnalyzer.kt`, `GeoTiffReader.kt`, `SRTMProvider.kt`, and the overall `DEMSystem` to address the issue of neighbor sampling falling into identical pixels.

---

## 1. DEM Data Flow Architecture Diagram

The offline geoprocessing architecture consists of four distinct layers: physical storage, provider interface, coordinate analysis, and UI HUD diagnostic presentation.

```
+--------------------------------------------------------------------------+
|                          1. STORAGE LAYER (Disk)                         |
|  - /dem/*.hgt (SRTM-1 / SRTM-3)                                          |
|  - /dem/*.tif / *.tiff (Copernicus GLO-30, ALOS AW3D30)                  |
+------------------------------------+-------------------------------------+
                                     |
                                     v
+--------------------------------------------------------------------------+
|                          2. PROVIDER LAYER (JVM)                         |
|  - SRTMProvider: Bilinear interpolation, parses HGT binary streams       |
|  - CopernicusProvider & ALOSProvider: Wrap GeoTiffReader                 |
|  - GeoTiffReader: Parses TIFF directories, reads raw pixels              |
+------------------------------------+-------------------------------------+
                                     |
                                     v
+--------------------------------------------------------------------------+
|                        3. COORDINATE ANALYSIS (GIS)                      |
|  - DEMSystem / DEMLoader: Direct elevation queries                       |
|  - TerrainAnalyzer:                                                      |
|    * Generates neighbor coordinates (N, S, E, W) using dLat & dLon       |
|    * Computes Slope & Aspect derivatives (dz/dx, dz/dy)                  |
|    * Captures DEMSamplingDiagnostic for active points                    |
+------------------------------------+-------------------------------------+
                                     |
                                     v
+--------------------------------------------------------------------------+
|                          4. PRESENTATION LAYER                           |
|  - MapActivity: Displays HUD diagnostic text panel                       |
|  - Web Console: Reads live diagnostic status streams                     |
+--------------------------------------------------------------------------+
```

---

## 2. Neighborhood Sampling Flow Diagram

When analyzing a location $P(\text{lat}, \text{lon})$, `TerrainAnalyzer.kt` samples the central elevation and four orthogonal neighbor elevations at a fixed step $\Delta = 0.0001^\circ$ (approximately $11.1\text{m}$).

```
                     P_N (lat + dLat, lon)
                               ^
                               |  dLat = 0.0001°
                               |
P_W (lat, lon - dLon) <-------- P ---------> P_E (lat, lon + dLon)
        dLon = 0.0001°         |         dLon = 0.0001°
                               |
                               v  dLat = 0.0001°
                     P_S (lat - dLat, lon)
```

The physical horizontal distance of $0.0001^\circ$ changes based on the coordinate component and the current latitude $\phi$:
- **Latitude component ($y$-axis):** Always constant.
  $$\Delta y = 0.0001^\circ \times 111,120\text{ m/degree} \approx 11.11\text{ meters}$$
- **Longitude component ($x$-axis):** Varies with latitude due to meridian convergence.
  $$\Delta x = 0.0001^\circ \times 111,120 \times \cos(\phi)\text{ meters}$$
  At latitude $\phi = 30^\circ$, $\Delta x \approx 9.62\text{ meters}$. At $\phi = 45^\circ$, $\Delta x \approx 7.86\text{ meters}$.

In the codebase (`TerrainAnalyzer.kt`, line 91), both components are treated as a symmetric isotropic grid with a hardcoded cell spacing of:
$$\text{cellSideM} = 11.1\text{ meters}$$

---

## 3. Coordinate Conversion & Index Mapping Process

For WGS84 Geodetic GeoTIFFs (EPSG:4326), the translation from floating-point latitude and longitude to pixel column (`col`) and row (`row`) coordinates is executed inside `GeoTiffReader.kt`:

```
+--------------------------+
|  Input: (lat, lon)       |
+------------+-------------+
             |
             v
+-------------------------------------------------------------------+
|  1. CONTINUOUS PIXEL COORDINATE CALCULATION                       |
|     px = (lon - tiepointX) / scaleX                               |
|     py = (tiepointY - lat) / scaleY                               |
|                                                                   |
|     * tiepointX: Westernmost longitude (minimum X bound)          |
|     * tiepointY: Northernmost latitude (maximum Y bound)          |
|     * scaleX: Grid width in degrees (e.g., 0.00027778° for 30m)   |
|     * scaleY: Grid height in degrees (e.g., 0.00027778° for 30m)  |
+----------------------------+--------------------------------------+
                             |
                             v
+-------------------------------------------------------------------+
|  2. DISCRETE PIXEL TRUNCATION (Nearest Neighbor)                  |
|     col = px.toInt()                                              |
|     row = py.toInt()                                              |
|                                                                   |
|     * Under Kotlin `.toInt()`, positive floats are truncated       |
|       towards zero (effectively floor-rounding).                   |
+----------------------------+--------------------------------------+
                             |
                             v
+-------------------------------------------------------------------+
|  3. BINARY FILE OFFSET CALCULATION                                |
|     offset = getPixelFileOffset(col, row)                         |
|     Seek and read 16-bit short / 32-bit float elevation values.    |
+-------------------------------------------------------------------+
```

---

## 4. Key Findings: The Under-Sampling & Pixel Overlap Trap

The diagnostic results confirm that **neighboring sampling points (North, South, East, West) frequently fall into the exact same pixel as the center point**.

### Why Pixel Overlap Occurs

1. **Resolution vs. Offset Mismatch:**
   - Standard 1-arcsecond DEMs (Copernicus GLO-30, ALOS AW3D30) have a spatial resolution of **$30\text{ meters}$** (corresponding to $\text{scaleX} = \text{scaleY} \approx 0.00027778^\circ$).
   - Standard 3-arcsecond DEMs (SRTM-3) have a spatial resolution of **$90\text{ meters}$** (corresponding to $\text{scaleX} = \text{scaleY} \approx 0.00083333^\circ$).
   - The neighbor sampling step in `TerrainAnalyzer.kt` is hardcoded at **$\Delta = 0.0001^\circ \approx 11.1\text{ meters}$**.

2. **The Nearest-Neighbor Stair-Step Phenomenon:**
   Because `GeoTiffReader.kt` uses **Nearest Neighbor** lookup (`col = px.toInt()`, `row = py.toInt()`), the elevation profile behaves as a series of flat, discrete steps. 
   
   If the step size $\Delta = 0.0001^\circ$ is smaller than the pixel scale ($0.00027778^\circ$), the neighbors will frequently remain on the same stair-step as the center point.

### Mathematical Probability of Overlap
Let the center coordinate index be $px$. The West neighbor is at $px - \delta$ and the East neighbor is at $px + \delta$, where $\delta = \frac{\Delta}{\text{scaleX}}$.
For a 30m DEM:
$$\delta = \frac{0.0001^\circ}{0.00027778^\circ} \approx 0.36\text{ pixels}$$

The probability that the West neighbor, Center point, and East neighbor all truncate to the same integer pixel column index is:
$$P(\text{overlap}) = 1.0 - \delta = 1.0 - 0.36 = 64\%$$

For a 90m DEM (SRTM-3):
$$\delta = \frac{0.0001^\circ}{0.00083333^\circ} \approx 0.12\text{ pixels}$$
$$P(\text{overlap}) = 1.0 - \delta = 1.0 - 0.12 = 88\%$$

### Consequence on Aspect & Slope
When overlap occurs:
- $h_E = h_W = \text{centerElevation} \implies dzDx = 0.0$
- $h_N = h_S = \text{centerElevation} \implies dzDy = 0.0$

Since both derivatives are zero, the slope is computed as $0^\circ$ (perfectly flat) and the aspect is assigned the degenerate fallback of $-1.0$. This is why the slope and aspect values often read as flat, or jump wildly across pixel boundaries.

---

## 5. DEM Interpolation Method Analysis

The codebase currently implements two entirely different interpolation styles:

1. **Bilinear Interpolation (`SRTMProvider.kt`):**
   - Implements continuous bilinear sampling across four bounding pixels:
     ```kotlin
     val r0 = rowFloat.toInt().coerceIn(0, size - 2)
     val r1 = r0 + 1
     val c0 = colFloat.toInt().coerceIn(0, size - 2)
     val c1 = c0 + 1
     val weightRow = rowFloat - r0
     val weightCol = colFloat - c0
     val top = h00 * (1.0 - weightCol) + h01 * weightCol
     val bottom = h10 * (1.0 - weightCol) + h11 * weightCol
     return top * (1.0 - weightRow) + bottom * weightRow
     ```
   - **Effect:** Even with a small coordinate offset of $0.0001^\circ$, the fractional weights (`weightRow`, `weightCol`) change smoothly. This guarantees distinct, non-zero elevation values for the neighbor points, resulting in stable, continuous slope and aspect gradients.

2. **Nearest Neighbor (`GeoTiffReader.kt`):**
   - Directly casts `px` and `py` to integer indices:
     ```kotlin
     val col = px.toInt()
     val row = py.toInt()
     ```
   - **Effect:** The elevation model behaves as a grid of discrete flat squares. This causes severe under-sampling issues when the step size is smaller than the pixel size.

---

## 6. Discovered Problems & Potential Bugs

### 1. Hardcoded Spatial Offset & Under-Sampling (Critical)
The fixed geographic step $\Delta = 0.0001^\circ$ is too small for $30\text{m}$ and $90\text{m}$ resolution rasters, causing $64\%$ to $88\%$ of sampling points to register identical elevations under Nearest Neighbor interpolation.

### 2. Longitude Distortion with Latitude (Medium)
The calculation of the derivative $dz/dx$ assumes that $0.0001^\circ$ longitude is always exactly $11.1\text{m}$ (`cellSideM = 11.1`). 
- At higher or lower latitudes, this assumption breaks down.
- For example, in Northern regions (e.g., $45^\circ\text{N}$), $0.0001^\circ$ longitude is only $7.86\text{m}$.
- Treating it as $11.1\text{m}$ artificially dampens the $dz/dx$ gradient component, distorting both the slope magnitude and the compass heading of the aspect.

### 3. Truncation Bias in Coordinate Translation (Low)
Using Kotlin's `.toInt()` on floating-point pixel positions introduces a top-left truncation bias. Standard GIS nearest-neighbor operations typically round coordinates to the nearest pixel center:
$$\text{col} = \lfloor px + 0.5 \rfloor$$
This half-pixel shift is not currently accounted for.

---

## 7. Recommendations & Fix Proposals

To make the geoprocessing pipeline robust, accurate, and correct across all DEM formats, we recommend the following changes:

### Recommendation A: Dynamic Sampling Offset (Grid-Aware Step)
Instead of hardcoding $dLat = dLon = 0.0001^\circ$, the step size should match the physical grid spacing of the active DEM. 
- A standard practice is to set the offset to **$1.0 \times \text{pixelScale}$** (or $1.0\text{ arcsecond}$ for 30m DEMs).
- This guarantees that North, South, East, and West evaluation points always land exactly in the adjacent pixels, completely avoiding the same-pixel truncation trap.

### Recommendation B: Implement Bilinear Interpolation in `GeoTiffReader.kt`
Upgrading the elevation query inside `GeoTiffReader.kt` from Nearest Neighbor to Bilinear Interpolation will eliminate the stair-step artifacts.
By reading the $2 \times 2$ pixel neighborhood surrounding $(px, py)$ and performing weighted average interpolation (similar to `SRTMProvider`), we can obtain smooth, highly precise gradients even for sub-pixel positions.

### Recommendation C: Dynamic Latitude Correction for cellSideM
Calculate the longitudinal distance dynamically based on the target latitude:
```kotlin
val latRad = Math.toRadians(lat)
val cellSideLatM = dLat * 111120.0
val cellSideLonM = dLon * 111120.0 * Math.cos(latRad)
```
Then compute the partial derivatives accurately:
```kotlin
val dzDx = (hE - hW) / (2.0 * cellSideLonM)
val dzDy = (hN - hS) / (2.0 * cellSideLatM)
```
This ensures perfect aspect compass angles and slope magnitudes at any latitude.

---
*Report compiled and verified on July 5, 2026.*
