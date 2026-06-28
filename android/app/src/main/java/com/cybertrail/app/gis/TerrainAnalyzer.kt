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
        val elevation = demSystem.getElevation(lat, lon)
        try {
            val dLat = 0.0001
            val dLon = 0.0001
            
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

            // Determine if we have real offline DEM files to customize the label
            val hasOffline = demLoader.hasOfflineDemFiles()
            val source = if (hasOffline) {
                val baseDir = java.io.File(android.os.Environment.getExternalStorageDirectory(), "CyberTrail")
                val demDir = java.io.File(baseDir, "DEM")
                val files = demDir.listFiles { _, name -> 
                    name.endsWith(".hgt", ignoreCase = true) || 
                    name.endsWith(".bil", ignoreCase = true) || 
                    name.endsWith(".tif", ignoreCase = true) ||
                    name.endsWith(".img", ignoreCase = true)
                }
                val isSrtm = files?.any { it.name.endsWith(".hgt", ignoreCase = true) } == true
                val isCopernicus = files?.any { it.name.endsWith(".bil", ignoreCase = true) || it.name.endsWith(".tif", ignoreCase = true) } == true
                when {
                    isSrtm -> "DEM (SRTM)"
                    isCopernicus -> "DEM (Copernicus DEM)"
                    else -> "DEM"
                }
            } else {
                "DEM"
            }

            Log.d("MAP_DEBUG", "ElevationSource=$source, Lat=${lat}/Lon=${lon}, Elevation=$elevation, Slope=$slopeDeg, Aspect=$aspectDeg")
            return AnalysisResult(elevation, slopeDeg, aspectDeg, source)
        } catch (e: Exception) {
            return AnalysisResult(elevation, 0.0, 0.0, "DEM")
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
