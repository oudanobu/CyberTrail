package com.cybertrail.app.gis

import android.content.Context
import android.util.Log

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
        try {
            val dLat = 0.0001
            val dLon = 0.0001
            
            // Query 5 locations centered around the point from offline DEM systems
            val centerElev = demSystem.getElevation(lat, lon)
            val hN = demSystem.getElevation(lat + dLat, lon)
            val hS = demSystem.getElevation(lat - dLat, lon)
            val hE = demSystem.getElevation(lat, lon + dLon)
            val hW = demSystem.getElevation(lat, lon - dLon)

            val cellSideM = 11.1 // approx meters per 0.0001 degree
            val dzDx = (hE - hW) / (2.0 * cellSideM)
            val dzDy = (hN - hS) / (2.0 * cellSideM)

            val riseRun = Math.sqrt(dzDx * dzDx + dzDy * dzDy)
            val slopeDeg = Math.toDegrees(Math.atan(riseRun))

            var aspectRad = Math.atan2(dzDy, -dzDx)
            if (aspectRad < 0.0) {
                aspectRad += 2.0 * Math.PI
            }
            val aspectDeg = Math.toDegrees(aspectRad)

            return AnalysisResult(centerElev, slopeDeg, aspectDeg)
        } catch (e: Exception) {
            val elev = demSystem.getSimulatedHeight(lat, lon)
            return AnalysisResult(elev, 0.0, 0.0)
        }
    }

    fun analyzeLocationAsync(lat: Double, lon: Double, callback: (AnalysisResult?) -> Unit) {
        Thread {
            try {
                val result = analyzeLocation(lat, lon)
                callback(result)
            } catch (e: Exception) {
                Log.e("TerrainAnalyzer", "Error analyzing local terrain offline", e)
                callback(null)
            }
        }.start()
    }
}
