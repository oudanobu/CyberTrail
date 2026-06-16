package com.cybertrail.app.gis

import android.content.Context
import kotlin.math.*

/**
 * Tactical Terrain Analyzer.
 * Computes high-precision local GIS metrics: Elevation, Slope (Horn's Method), and Aspect.
 */
class TerrainAnalyzer(
    private val context: Context,
    private val demLoader: DEMLoader,
    private val fallbackSystem: DEMSystem
) {

    companion object {
        private const val GEO_SCALE_M = 111000.0 // Approx meters per degree
        private const val GRID_SPACING_M = 10.0 // Horn 3x3 local cell distance in meters
    }

    class AnalysisResult(
        val elevation: Double,
        val slope: Double,  // Degrees
        val aspect: Double  // Degrees (0-360, North is 0, East is 90)
    )

    /**
     * Resolves single-point elevation from active sources, or simulated fallback.
     */
    fun getElevation(lat: Double, lon: Double): Double {
        return demLoader.getElevation(lat, lon) ?: fallbackSystem.getElevation(lat, lon)
    }

    /**
     * Samples a tight 3x3 local neighborhood array to extract complete slope vectors and azimuth aspect.
     */
    fun analyzeLocation(lat: Double, lon: Double): AnalysisResult {
        val latRad = Math.toRadians(lat)
        val latCos = cos(latRad)

        // Calculate degree spacing for target 10m grid
        val dLat = GRID_SPACING_M / GEO_SCALE_M
        val dLon = if (latCos > 0.01) GRID_SPACING_M / (GEO_SCALE_M * latCos) else dLat

        // Sample the local 3x3 elevation points
        // Grid layout:
        // [z00] (NorthWest)   [z01] (North)   [z02] (NorthEast)
        // [z10] (West)        [z11] (Center)  [z12] (East)
        // [z20] (SouthWest)   [z21] (South)   [z22] (SouthEast)
        val z00 = getElevation(lat + dLat, lon - dLon)
        val z01 = getElevation(lat + dLat, lon)
        val z02 = getElevation(lat + dLat, lon + dLon)

        val z10 = getElevation(lat, lon - dLon)
        val z11 = getElevation(lat, lon)
        val z12 = getElevation(lat, lon + dLon)

        val z20 = getElevation(lat - dLat, lon - dLon)
        val z21 = getElevation(lat - dLat, lon)
        val z22 = getElevation(lat - dLat, lon + dLon)

        // Horn's X and Y gradients weighted by distance factor
        val dz_dx = ((z02 + 2.0 * z12 + z22) - (z00 + 2.0 * z10 + z20)) / (8.0 * GRID_SPACING_M)
        val dz_dy = ((z20 + 2.0 * z21 + z22) - (z00 + 2.0 * z01 + z02)) / (8.0 * GRID_SPACING_M)

        val rise_run = sqrt(dz_dx * dz_dx + dz_dy * dz_dy)
        var slopeVal = Math.toDegrees(atan(rise_run))
        if (slopeVal.isNaN()) slopeVal = 0.0

        // Calculate aspect azimuth (direction the slope faces, clockwise from North = 0)
        var aspectVal = -1.0
        if (rise_run > 0.0001) {
            // Standard aspect formula: clockwise angle of steepest slope descent
            val aspectRad = atan2(dz_dy, -dz_dx)
            aspectVal = Math.toDegrees(aspectRad)
            aspectVal = 90.0 - aspectVal
            if (aspectVal < 0.0) {
                aspectVal += 360.0
            }
            aspectVal %= 360.0
        }

        return AnalysisResult(z11, slopeVal, aspectVal)
    }
}
