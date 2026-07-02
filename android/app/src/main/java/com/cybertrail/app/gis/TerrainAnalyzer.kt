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
        val source: String,
        val lat: Double? = null,
        val lon: Double? = null,
        val hN: Double? = null,
        val hS: Double? = null,
        val hE: Double? = null,
        val hW: Double? = null,
        val dzDx: Double? = null,
        val dzDy: Double? = null,
        val aspectRawMath: Double? = null,
        val aspectDownSlopeVectorX: Double? = null,
        val aspectDownSlopeVectorY: Double? = null,
        val aspectGISFinal: Double? = null
    )

    fun analyzeLocation(lat: Double, lon: Double): AnalysisResult {
        val elevation = demSystem.getElevation(lat, lon) ?: return AnalysisResult(null, null, null, "DEM未加载", lat, lon)
        
        // Determine if we have real offline DEM files to customize the label
        val hasOffline = demLoader.hasOfflineDemFiles()
        val source = if (hasOffline) {
            val baseDir = java.io.File(android.os.Environment.getExternalStorageDirectory(), "CyberTrail")
            val demDir = java.io.File(baseDir, "dem")
            val files = demDir.listFiles { _, name -> 
                name.endsWith(".hgt", ignoreCase = true) || 
                name.endsWith(".bil", ignoreCase = true) || 
                name.endsWith(".tif", ignoreCase = true) ||
                name.endsWith(".tiff", ignoreCase = true) ||
                name.endsWith(".img", ignoreCase = true)
            }
            val isSrtm = files?.any { it.name.endsWith(".hgt", ignoreCase = true) } == true
            val isCopernicus = files?.any { it.name.endsWith(".bil", ignoreCase = true) || it.name.endsWith(".tif", ignoreCase = true) || it.name.endsWith(".tiff", ignoreCase = true) } == true
            when {
                isSrtm -> "DEM (SRTM)"
                isCopernicus -> "DEM (Copernicus DEM)"
                else -> "DEM"
            }
        } else {
            "DEM"
        }

        try {
            val dLat = 0.0001
            val dLon = 0.0001
            
            val hN = demSystem.getElevation(lat + dLat, lon) ?: return AnalysisResult(elevation, null, null, source, lat, lon)
            val hS = demSystem.getElevation(lat - dLat, lon) ?: return AnalysisResult(elevation, null, null, source, lat, lon)
            val hE = demSystem.getElevation(lat, lon + dLon) ?: return AnalysisResult(elevation, null, null, source, lat, lon)
            val hW = demSystem.getElevation(lat, lon - dLon) ?: return AnalysisResult(elevation, null, null, source, lat, lon)

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

            val downSlopeX = -dzDx
            val downSlopeY = -dzDy
            val mathAngleDeg = Math.toDegrees(Math.atan2(downSlopeY, downSlopeX))
            var gisAspect = 90.0 - mathAngleDeg
            if (gisAspect < 0.0) {
                gisAspect += 360.0
            }
            if (gisAspect >= 360.0) {
                gisAspect -= 360.0
            }
            val aspectGISFinal = if (dzDx == 0.0 && dzDy == 0.0) -1.0 else gisAspect

            Log.d("MAP_DEBUG", "ElevationSource=$source, Lat=${lat}/Lon=${lon}, Elevation=$elevation, Slope=$slopeDeg, Aspect=$aspectDeg")
            return AnalysisResult(
                elevation, slopeDeg, aspectDeg, source, lat, lon, hN, hS, hE, hW, dzDx, dzDy,
                aspectRawMath = aspectDeg,
                aspectDownSlopeVectorX = downSlopeX,
                aspectDownSlopeVectorY = downSlopeY,
                aspectGISFinal = aspectGISFinal
            )
        } catch (e: Exception) {
            return AnalysisResult(elevation, null, null, source, lat, lon)
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
