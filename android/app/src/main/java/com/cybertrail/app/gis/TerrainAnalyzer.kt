package com.cybertrail.app.gis

import android.content.Context
import android.util.Log

class TerrainAnalyzer(
    private val context: Context,
    private val demLoader: DEMLoader,
    private val demSystem: DEMSystem
) {

    data class DEMSamplingDiagnostic(
        val centerPixelX: Int,
        val centerPixelY: Int,
        val northPixelX: Int,
        val northPixelY: Int,
        val southPixelX: Int,
        val southPixelY: Int,
        val eastPixelX: Int,
        val eastPixelY: Int,
        val westPixelX: Int,
        val westPixelY: Int,
        val centerElevation: Double,
        val northElevation: Double,
        val southElevation: Double,
        val eastElevation: Double,
        val westElevation: Double,
        val northIsSamePixelAsCenter: Boolean,
        val southIsSamePixelAsCenter: Boolean,
        val eastIsSamePixelAsCenter: Boolean,
        val westIsSamePixelAsCenter: Boolean,
        val demResolutionMeters: Double,
        val neighborOffsetMeters: Double
    )

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
        val aspectGISFinal: Double? = null,
        val samplingDiagnostic: DEMSamplingDiagnostic? = null
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
            val activeDEM = demLoader.getActiveDEMInfo(lat, lon) ?: return AnalysisResult(elevation, null, null, source, lat, lon)
            val centerCoords = demLoader.getPixelCoords(lat, lon) ?: return AnalysisResult(elevation, null, null, source, lat, lon)

            val cx = centerCoords.first.toInt()
            val cy = centerCoords.second.toInt()

            val provider = activeDEM.provider

            val hN = demLoader.getElevationByPixel(provider, cx, cy - 1, lat, lon) ?: elevation
            val hS = demLoader.getElevationByPixel(provider, cx, cy + 1, lat, lon) ?: elevation
            val hE = demLoader.getElevationByPixel(provider, cx + 1, cy, lat, lon) ?: elevation
            val hW = demLoader.getElevationByPixel(provider, cx - 1, cy, lat, lon) ?: elevation

            val dx = activeDEM.pixelSizeXMeters
            val dy = activeDEM.pixelSizeYMeters

            val dzDx = (hE - hW) / (2.0 * dx)
            val dzDy = (hN - hS) / (2.0 * dy)

            val riseRun = Math.sqrt(dzDx * dzDx + dzDy * dzDy)
            val slopeDeg = Math.toDegrees(Math.atan(riseRun))

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

            // Save old math aspect for diagnostics
            var rawAspectRad = Math.atan2(dzDy, -dzDx)
            if (rawAspectRad < 0.0) {
                rawAspectRad += 2.0 * Math.PI
            }
            val aspectRawMathDeg = Math.toDegrees(rawAspectRad)

            // The main aspect property should point to the correct GIS-based final aspect (null if flat)
            val aspectDeg = if (dzDx == 0.0 && dzDy == 0.0) null else gisAspect

            val demRes = getDEMResolutionMeters(lat, lon) ?: dx

            val diagnostic = DEMSamplingDiagnostic(
                centerPixelX = cx,
                centerPixelY = cy,
                northPixelX = cx,
                northPixelY = cy - 1,
                southPixelX = cx,
                southPixelY = cy + 1,
                eastPixelX = cx + 1,
                eastPixelY = cy,
                westPixelX = cx - 1,
                westPixelY = cy,
                centerElevation = elevation,
                northElevation = hN,
                southElevation = hS,
                eastElevation = hE,
                westElevation = hW,
                northIsSamePixelAsCenter = false,
                southIsSamePixelAsCenter = false,
                eastIsSamePixelAsCenter = false,
                westIsSamePixelAsCenter = false,
                demResolutionMeters = demRes,
                neighborOffsetMeters = dx
            )

            Log.d("MAP_DEBUG", "ElevationSource=$source, Lat=${lat}/Lon=${lon}, Elevation=$elevation, Slope=$slopeDeg, Aspect=$aspectDeg")
            Log.d("DEM_VALIDATION", """
                [DEM Real-time Sampling Diagnostics]
                Lat: $lat, Lon: $lon
                CenterPixelX: $cx
                CenterPixelY: $cy
                NorthPixelX: $cx
                NorthPixelY: ${cy - 1}
                SouthPixelX: $cx
                SouthPixelY: ${cy + 1}
                EastPixelX: ${cx + 1}
                EastPixelY: $cy
                WestPixelX: ${cx - 1}
                WestPixelY: $cy
                CenterElevation: $elevation
                NorthElevation: $hN
                SouthElevation: $hS
                EastElevation: $hE
                WestElevation: $hW
                NorthSamePixelAsCenter: false
                SouthSamePixelAsCenter: false
                EastSamePixelAsCenter: false
                WestSamePixelAsCenter: false
            """.trimIndent())
            
            return AnalysisResult(
                elevation, slopeDeg, aspectDeg, source, lat, lon, hN, hS, hE, hW, dzDx, dzDy,
                aspectRawMath = aspectRawMathDeg,
                aspectDownSlopeVectorX = downSlopeX,
                aspectDownSlopeVectorY = downSlopeY,
                aspectGISFinal = aspectGISFinal,
                samplingDiagnostic = diagnostic
            )
        } catch (e: Exception) {
            return AnalysisResult(elevation, null, null, source, lat, lon)
        }
    }


    fun getPixelCoords(lat: Double, lon: Double): Pair<Int, Int>? {
        val coords = demLoader.getPixelCoords(lat, lon) ?: return null
        return Pair(coords.first.toInt(), coords.second.toInt())
    }

    fun getDEMResolutionMeters(lat: Double, lon: Double): Double? {
        return demLoader.getResolutionMeters(lat, lon)
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
