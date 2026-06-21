package com.cybertrail.app.gis

import android.content.Context
import android.util.Log

class TerrainAnalyzer(
    private val context: Context,
    private val demLoader: DEMLoader,
    private val demSystem: DEMSystem
) {

    data class AnalysisResult(
        val elevation: Double?,
        val slope: Double?,
        val aspect: Double?,
        val source: String
    )

    fun analyzeLocation(lat: Double, lon: Double): AnalysisResult {
        // Query real elevation from real files first
        val realElev = demSystem.getRealElevation(lat, lon)
        if (realElev != null) {
            try {
                val dLat = 0.0001
                val dLon = 0.0001
                
                val hN = demSystem.getRealElevation(lat + dLat, lon) ?: realElev
                val hS = demSystem.getRealElevation(lat - dLat, lon) ?: realElev
                val hE = demSystem.getRealElevation(lat, lon + dLon) ?: realElev
                val hW = demSystem.getRealElevation(lat, lon - dLon) ?: realElev

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

                Log.d("MAP_DEBUG", "DEMLoaded=true, DEMCoverage=Lat=${lat}/Lon=${lon}, ElevationSource=DEM, SlopeComputed=$slopeDeg, AspectComputed=$aspectDeg")
                return AnalysisResult(realElev, slopeDeg, aspectDeg, "DEM")
            } catch (e: Exception) {
                return AnalysisResult(realElev, 0.0, 0.0, "DEM")
            }
        }
        
        // If there's NO local offline DEM, but we want GPS elevation to be prioritized if available!
        // (Wait, GPS altitude is fetched directly from the GPS sensor inside MapActivity.kt's location callback or similar).
        // Since TerrainAnalyzer only queries offline database heightmaps, if there are no dem files, we return null fields
        // with "NONE" so MapActivity knows there is NO DEM data, and we avoid SIMULATION altitude.
        return AnalysisResult(null, null, null, "NONE")
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
