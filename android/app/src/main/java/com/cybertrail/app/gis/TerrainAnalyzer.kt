package com.cybertrail.app.gis

import android.content.Context

class TerrainAnalyzer(
    private val context: Context,
    private val demLoader: DEMLoader,
    private val demSystem: DEMSystem
) {

    data class AnalysisResult(
        val elevation: Double,
        val slope: Double,
        val aspect: Double
    )

    fun analyzeLocation(lat: Double, lon: Double): AnalysisResult {
        // Compute Horn's algorithm or finite-difference gradients over 3x3 elevation cells
        val dLat = 0.0001
        val dLon = 0.0001

        val centerElev = demSystem.getSimulatedHeight(lat, lon)
        
        // Sampling neighbouring heights
        val hN  = demSystem.getSimulatedHeight(lat + dLat, lon)
        val hS  = demSystem.getSimulatedHeight(lat - dLat, lon)
        val hE  = demSystem.getSimulatedHeight(lat, lon + dLon)
        val hW  = demSystem.getSimulatedHeight(lat, lon - dLon)

        // Gradient calculation in meters
        val cellSideM = 11.1 // approx meters per 0.0001 degree
        val dzDx = (hE - hW) / (2.0 * cellSideM)
        val dzDy = (hN - hS) / (2.0 * cellSideM)

        val riseRun = Math.sqrt(dzDx * dzDx + dzDy * dzDy)
        val slopeDeg = Math.toDegrees(Math.atan(riseRun))

        // Calculate aspect slope direction in degrees
        var aspectRad = Math.atan2(dzDy, -dzDx) // standard mathematical direction
        if (aspectRad < 0.0) {
            aspectRad += 2.0 * Math.PI
        }
        val aspectDeg = Math.toDegrees(aspectRad)

        return AnalysisResult(centerElev, slopeDeg, aspectDeg)
    }
}
