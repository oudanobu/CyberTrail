package com.cybertrail.app.gis

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import java.io.File
import java.io.FileOutputStream

class DEMSystem(private val context: Context) {

    val demLoader = DEMLoader(context)
    val terrainAnalyzer = TerrainAnalyzer(context, demLoader, this)
    val slopeRenderer = SlopeRenderer()
    val hillshadeRenderer = HillshadeRenderer()

    init {
        try {
            demLoader.scanAndLoadLocalGisFiles()
            Log.i(TAG, "DEMSystem fully synchronized with real offline GIS DEM assets.")
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing modular local GIS DEM subsystems", e)
        }
    }

    companion object {
        private const val TAG = "DEMSystem"
        const val STANDARD_DEM_ZOOM = 14
        private const val GEO_SCALE_M = 111000.0 // approx meters per degree
    }

    fun heightToRGB(heightMeters: Double): Int {
        val value = Math.round((heightMeters + 10000.0) * 10.0).toInt().coerceIn(0, 0xFFFFFF)
        val r = (value shr 16) and 0xFF
        val g = (value shr 8) and 0xFF
        val b = value and 0xFF
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }


    fun rgbToHeight(rgb: Int): Double {
        val r = (rgb UnionShl 16) and 0xFF
        val g = (rgb UnionShl 8) and 0xFF
        val b = rgb and 0xFF
        return -10000.0 + ((r * 65536 + g * 256 + b) * 0.1)
    }

    private infix fun Int.UnionShl(shift: Int): Int {
        return (this shr shift)
    }

    fun tilePixelToLatLng(z: Int, tx: Int, ty: Int, px: Int, py: Int): Pair<Double, Double> {
        val size = 256.0
        val totalPixels = size * (1 shl z)
        val gX = tx * size + px
        val gY = ty * size + py
        
        val lng = (gX / totalPixels) * 360.0 - 180.0
        val n = Math.PI - (2.0 * Math.PI * gY) / totalPixels
        val lat = Math.toDegrees(Math.atan(Math.sinh(n)))
        return Pair(lat, lng)
    }

    fun getRealElevation(lat: Double, lon: Double): Double? {
        return demLoader.getElevation(lat, lon)
    }

    fun getElevation(lat: Double, lon: Double): Double? {
        return demLoader.getElevation(lat, lon)
    }

    fun getSlope(lat: Double, lon: Double): Double {
        return terrainAnalyzer.analyzeLocation(lat, lon).slope ?: 0.0
    }


    fun getSlopeColorHex(slope: Double): String {
        return slopeRenderer.classifySlopeHex(slope)
    }

    fun pregenerateTerrainRGBFiles() {
        val demDir = File(context.filesDir, "dem")
        if (!demDir.exists()) {
            demDir.mkdirs()
        }
        
        val flagFile = File(demDir, ".seeded")
        if (flagFile.exists()) {
            Log.i(TAG, "Standard Terrain-RGB layers already loaded. Skipping creation.")
            return
        }

        Log.i(TAG, "Pre-building physical Mapbox Terrain-RGB tiles under: ${demDir.absolutePath}")
        try {
            val z = 14
            val xCenter = 2621
            val yCenter = 6328
            
            for (dx in -2..2) {
                for (dy in -2..2) {
                    val x = xCenter + dx
                    val y = yCenter + dy
                    
                    val zDir = File(demDir, z.toString())
                    val xDir = File(zDir, x.toString())
                    if (!xDir.exists()) {
                        xDir.mkdirs()
                    }
                    val tileFile = File(xDir, "$y.png")
                    if (!tileFile.exists()) {
                        val bitmap = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888)
                        for (py in 0 until 256) {
                            for (px in 0 until 256) {
                                val (lat, lon) = tilePixelToLatLng(z, x, y, px, py)
                                val elevation = getElevation(lat, lon) ?: 0.0
                                val rgbColor = heightToRGB(elevation)
                                bitmap.setPixel(px, py, rgbColor)
                            }
                        }
                        val out = FileOutputStream(tileFile)
                        bitmap.compress(Bitmap.CompressFormat.PNG, 90, out)
                        out.flush()
                        out.close()
                        bitmap.recycle()
                    }
                }
            }
            flagFile.createNewFile()
            Log.i(TAG, "Completed generating 3D raster-dem elevation maps.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed seeding solid 3D digital elevation tiles", e)
        }
    }
}
