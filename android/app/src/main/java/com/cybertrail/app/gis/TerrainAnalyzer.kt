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

            val centerCoords = getPixelCoords(lat, lon) ?: Pair(0, 0)
            val northCoords = getPixelCoords(lat + dLat, lon) ?: Pair(0, 0)
            val southCoords = getPixelCoords(lat - dLat, lon) ?: Pair(0, 0)
            val eastCoords = getPixelCoords(lat, lon + dLon) ?: Pair(0, 0)
            val westCoords = getPixelCoords(lat, lon - dLon) ?: Pair(0, 0)

            val demRes = getDEMResolutionMeters(lat, lon) ?: 30.0

            val diagnostic = DEMSamplingDiagnostic(
                centerPixelX = centerCoords.first,
                centerPixelY = centerCoords.second,
                northPixelX = northCoords.first,
                northPixelY = northCoords.second,
                southPixelX = southCoords.first,
                southPixelY = southCoords.second,
                eastPixelX = eastCoords.first,
                eastPixelY = eastCoords.second,
                westPixelX = westCoords.first,
                westPixelY = westCoords.second,
                centerElevation = elevation,
                northElevation = hN,
                southElevation = hS,
                eastElevation = hE,
                westElevation = hW,
                northIsSamePixelAsCenter = (centerCoords.first == northCoords.first && centerCoords.second == northCoords.second),
                southIsSamePixelAsCenter = (centerCoords.first == southCoords.first && centerCoords.second == southCoords.second),
                eastIsSamePixelAsCenter = (centerCoords.first == eastCoords.first && centerCoords.second == eastCoords.second),
                westIsSamePixelAsCenter = (centerCoords.first == westCoords.first && centerCoords.second == westCoords.second),
                demResolutionMeters = demRes,
                neighborOffsetMeters = cellSideM
            )

            Log.d("MAP_DEBUG", "ElevationSource=$source, Lat=${lat}/Lon=${lon}, Elevation=$elevation, Slope=$slopeDeg, Aspect=$aspectDeg")
            Log.d("DEM_VALIDATION", """
                [DEM Real-time Sampling Diagnostics]
                Lat: $lat, Lon: $lon
                CenterPixelX: ${centerCoords.first}
                CenterPixelY: ${centerCoords.second}
                NorthPixelX: ${northCoords.first}
                NorthPixelY: ${northCoords.second}
                SouthPixelX: ${southCoords.first}
                SouthPixelY: ${southCoords.second}
                EastPixelX: ${eastCoords.first}
                EastPixelY: ${eastCoords.second}
                WestPixelX: ${westCoords.first}
                WestPixelY: ${westCoords.second}
                CenterElevation: $elevation
                NorthElevation: $hN
                SouthElevation: $hS
                EastElevation: $hE
                WestElevation: $hW
                NorthSamePixelAsCenter: ${centerCoords.first == northCoords.first && centerCoords.second == northCoords.second}
                SouthSamePixelAsCenter: ${centerCoords.first == southCoords.first && centerCoords.second == southCoords.second}
                EastSamePixelAsCenter: ${centerCoords.first == eastCoords.first && centerCoords.second == eastCoords.second}
                WestSamePixelAsCenter: ${centerCoords.first == westCoords.first && centerCoords.second == westCoords.second}
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
