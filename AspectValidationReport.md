# Aspect Validation Report

This report documents the verification of the GIS-based Aspect (slope direction) algorithm against the eight standard compass test cases.

## 1. Core Algorithm Description & Equations

The terrain analyzer uses a 4-point differential grid centered at a given latitude/longitude to calculate the partial derivatives of elevation with respect to the horizontal coordinates ($x$ and $y$).

Given elevations at neighboring cells:
- $h_N$ (North: $\text{lat} + \Delta \text{lat}$)
- $h_S$ (South: $\text{lat} - \Delta \text{lat}$)
- $h_E$ (East: $\text{lon} + \Delta \text{lon}$)
- $h_W$ (West: $\text{lon} - \Delta \text{lon}$)

The horizontal and vertical gradient components (derivatives $dz/dx$ and $dz/dy$) are defined as:
$$dzDx = \frac{h_E - h_W}{2 \times \text{cellSideM}}$$
$$dzDy = \frac{h_N - h_S}{2 \times \text{cellSideM}}$$

Where $\text{cellSideM} = 11.1$ meters.

### Down Slope Vector
The steepest downhill vector is in the opposite direction of the gradient:
$$\text{DownSlopeX} = -dzDx$$
$$\text{DownSlopeY} = -dzDy$$

### Mathematical Angle vs. GIS Aspect Angle
The standard mathematical angle $\theta$ (in degrees) is derived from the downhill vector using `atan2`:
$$\theta = \text{atan2}(\text{DownSlopeY}, \text{DownSlopeX}) \times \frac{180}{\pi}$$

In standard GIS conventions, aspect measures the clockwise degrees from True North ($0^\circ$):
- $0^\circ = \text{North}$
- $90^\circ = \text{East}$
- $180^\circ = \text{South}$
- $270^\circ = \text{West}$

The mathematical angle starts at the positive $x$-axis (East) and goes counter-clockwise. Therefore, the transformation to GIS Aspect is:
$$\text{gisAspect} = 90.0^\circ - \theta$$

If the resulting angle is negative, $360.0^\circ$ is added. If it is greater than or equal to $360.0^\circ$, $360.0^\circ$ is subtracted.

For a completely flat cell ($dzDx = 0$ and $dzDy = 0$), the aspect is defined as $-1.0$ (flat/no direction).

---

## 2. Eight-Direction Verification Results

| Case ID | Inputs (N, S, E, W) | dzDx | dzDy | DownSlopeX | DownSlopeY | AspectRawMath | AspectGISFinal | Expected | Diff | Status |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| Case1 | N=10.0, S=10.0, E=9.0, W=11.0 | -0.09009 | 0.00000 | 0.09009 | -0.00000 | 0.0° | 90.0° | 90.0° | 0.0° | **Pass** |
| Case2 | N=9.0, S=11.0, E=10.0, W=10.0 | 0.00000 | -0.09009 | -0.00000 | 0.09009 | 270.0° | 0.0° | 0.0° | 0.0° | **Pass** |
| Case3 | N=10.0, S=10.0, E=11.0, W=9.0 | 0.09009 | 0.00000 | -0.09009 | -0.00000 | 180.0° | 270.0° | 270.0° | 0.0° | **Pass** |
| Case4 | N=11.0, S=9.0, E=10.0, W=10.0 | 0.00000 | 0.09009 | -0.00000 | -0.09009 | 90.0° | 180.0° | 180.0° | 0.0° | **Pass** |
| Case5 | N=9.0, S=11.0, E=9.0, W=11.0 | -0.09009 | -0.09009 | 0.09009 | 0.09009 | 315.0° | 45.0° | 45.0° | 0.0° | **Pass** |
| Case6 | N=9.0, S=11.0, E=11.0, W=9.0 | 0.09009 | -0.09009 | -0.09009 | 0.09009 | 225.0° | 315.0° | 315.0° | 0.0° | **Pass** |
| Case7 | N=11.0, S=9.0, E=9.0, W=11.0 | -0.09009 | 0.09009 | 0.09009 | -0.09009 | 45.0° | 135.0° | 135.0° | 0.0° | **Pass** |
| Case8 | N=11.0, S=9.0, E=11.0, W=9.0 | 0.09009 | 0.09009 | -0.09009 | -0.09009 | 135.0° | 225.0° | 225.0° | 0.0° | **Pass** |

## 3. Overall Verification Status

### **STATUS: ALL TESTS PASSED**

The implementation of the GIS-based Aspect calculation inside `TerrainAnalyzer` complies 100% with standard GIS conventions across all 8 cardinal and intercardinal compass directions.
