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
        // Fallback synchronous method
        val centerElev = demSystem.getSimulatedHeight(lat, lon)
        return AnalysisResult(centerElev, 0.0, 0.0)
    }

    fun analyzeLocationAsync(lat: Double, lon: Double, callback: (AnalysisResult?) -> Unit) {
        Thread {
            try {
                val dLat = 0.0001
                val dLon = 0.0001
                val locs = "$lat,$lon|${lat+dLat},$lon|${lat-dLat},$lon|$lat,${lon+dLon}|$lat,${lon-dLon}"
                val urlStr = "https://api.opentopodata.org/v1/aster30m?locations=$locs"
                val conn = java.net.URL(urlStr).openConnection() as java.net.HttpURLConnection
                conn.requestMethod = "GET"
                conn.connectTimeout = 3000
                conn.readTimeout = 3000
                
                if (conn.responseCode == 200) {
                    val response = conn.inputStream.bufferedReader().readText()
                    val elevations = mutableListOf<Double>()
                    val regex = "\"elevation\":\\s*([0-9.-]+)".toRegex()
                    val matches = regex.findAll(response)
                    
                    for (m in matches) {
                        elevations.add(m.groupValues[1].toDouble())
                    }
                    
                    if (elevations.size >= 5) {
                        val centerElev = elevations[0]
                        val hN = elevations[1]
                        val hS = elevations[2]
                        val hE = elevations[3]
                        val hW = elevations[4]
                        
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

                        callback(AnalysisResult(centerElev, slopeDeg, aspectDeg))
                        return@Thread
                    }
                }
                callback(null)
            } catch (e: Exception) {
                Log.e("TerrainAnalyzer", "Error fetching ASTER DEM", e)
                callback(null)
            }
        }.start()
    }
}
