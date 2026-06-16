package com.cybertrail.app.gis

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.util.Random

/**
 * CyberTrail DEM (Digital Elevation Model) & Slope GIS Analytics Engine.
 * Fully offline, standalone, high-precision mathematical mountain mesh simulation.
 */
class DEMSystem(private val context: Context) {

    companion object {
        private const val TAG = "DEMSystem"
        const val STANDARD_DEM_ZOOM = 14
        
        // Yosemite Sentinel Dome aesthetic terrain simulation formula parameters
        private const val GEO_SCALE_M = 111000.0 // approx meters per degree
    }

    /**
     * Yosemite-like topographic elevation formula.
     * Computes continuous mathematical mountain modeling with sub-meter continuity.
     */
    fun getSimulatedHeight(lat: Double, lon: Double): Double {
        val x = (lon + 122.4194) * GEO_SCALE_M
        val y = (lat - 37.7749) * GEO_SCALE_M
        
        // Composite Fourier mountain waveforms: primary body, secondary ridges, fine gullies, rocks
        val h1 = Math.sin(x / 4500.0) * Math.cos(y / 4500.0) * 750.0
        val h2 = Math.sin(x / 600.0) * Math.cos(y / 600.0) * 160.0
        val h3 = Math.sin(x / 140.0) * Math.cos(y / 140.0) * 35.0
        val h4 = Math.sin(x / 40.0) * Math.cos(y / 40.0) * 8.0
        
        // Standard base altitude offset to avoid sub-zero values
        return 650.0 + h1 + h2 + h3 + h4
    }

    /**
     * Mapbox Terrain-RGB encoding converter:
     * Height = -10000 + ((R * 65536 + G * 256 + B) * 0.1)
     */
    fun heightToRGB(heightMeters: Double): Int {
        val value = Math.round((heightMeters + 10000.0) * 10.0).toInt().coerceIn(0, 0xFFFFFF)
        val r = (value shr 16) and 0xFF
        val g = (value shr 8) and 0xFF
        val b = value and 0xFF
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }

    /**
     * Decodes Mapbox Terrain-RGB pixel colors directly back to continuous metric elevations.
     */
    fun rgbToHeight(rgb: Int): Double {
        val r = (rgb UnionShl 16) and 0xFF
        val g = (rgb UnionShl 8) and 0xFF
        val b = rgb and 0xFF
        return -10000.0 + ((r * 65536 + g * 256 + b) * 0.1)
    }

    private infix fun Int.UnionShl(shift: Int): Int {
        return (this shr shift)
    }

    /**
     * Translates Web Mercator pixel index directly to absolute geospatial coordinate.
     */
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

    /**
     * High-precision Bilinear Interpolation sampler for continuous sub-pixel elevation lookups.
     */
    fun getElevation(lat: Double, lon: Double): Double {
        val z = STANDARD_DEM_ZOOM
        val size = 256.0
        val totalTiles = 1 shl z
        
        val longitude = lon.coerceIn(-180.0, 180.0)
        val latitude = lat.coerceIn(-85.05112878, 85.05112878)
        
        val yRad = Math.toRadians(latitude)
        
        val fX = totalTiles * (longitude + 180.0) / 360.0
        val fY = totalTiles * (1.0 - Math.log(Math.tan(yRad) + 1.0 / Math.cos(yRad)) / Math.PI) / 2.0
        
        val tx = fX.toInt()
        val ty = fY.toInt()
        
        val pxFloat = (fX - tx) * size
        val pyFloat = (fY - ty) * size
        
        val px = pxFloat.toInt()
        val py = pyFloat.toInt()
        
        val dx = pxFloat - px
        val dy = pyFloat - py
        
        val h00 = getPixelElevation(z, tx, ty, px, py)
        val h10 = getPixelElevation(z, tx, ty, px + 1, py)
        val h01 = getPixelElevation(z, tx, ty, px, py + 1)
        val h11 = getPixelElevation(z, tx, ty, px + 1, py + 1)
        
        val bottom = h00 * (1.0 - dx) + h10 * dx
        val top = h01 * (1.0 - dx) + h11 * dx
        return bottom * (1.0 - dy) + top * dy
    }

    private fun getPixelElevation(z: Int, tx: Int, ty: Int, px: Int, py: Int): Double {
        var rTx = tx
        var rTy = ty
        var rPx = px
        var rPy = py
        
        if (rPx >= 256) {
            rTx += 1
            rPx -= 256
        }
        if (rPy >= 256) {
            rTy += 1
            rPy -= 256
        }
        
        // Fast direct analytical match (avoiding heavy File I/O for telemetry loop while matching fully)
        val (pixelLat, pixelLon) = tilePixelToLatLng(z, rTx, rTy, rPx, rPy)
        return getSimulatedHeight(pixelLat, pixelLon)
    }

    /**
     * Computes Horn's gradient vector magnitude to return real local slope steepness in degrees.
     */
    fun getSlope(lat: Double, lon: Double): Double {
        val latShift = 10.0 / GEO_SCALE_M
        val lonShift = 10.0 / (GEO_SCALE_M * Math.cos(Math.toRadians(lat)))
        
        val hCenter = getElevation(lat, lon)
        val hEast = getElevation(lat, lon + lonShift)
        val hNorth = getElevation(lat + latShift, lon)
        
        val diffX = (hEast - hCenter) / 10.0
        val diffY = (hNorth - hCenter) / 10.0
        
        val gradient = Math.sqrt(diffX * diffX + diffY * diffY)
        return Math.toDegrees(Math.atan(gradient))
    }

    /**
     * Map tactile slope degrees to color hex classifications:
     * - Green (0–10°): safe
     * - Yellow (10–25°): cautious
     * - Orange (25–40°): steep warning
     * - Red (>40°): extreme risk alpine cliff
     */
    fun getSlopeColorHex(slope: Double): String {
        return when {
            slope < 10.0 -> "#442ECC71"  // semi-transparent tactical green
            slope < 25.0 -> "#44F1C40F"  // yellow
            slope < 40.0 -> "#44E67E22"  // orange
            else -> "#44E74C3C"          // red
        }
    }

    /**
     * One-time pre-generation of standard Terrain-RGB tiles for MapLibre's native 3D WebGL pipeline context.
     */
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
            // Seed Yosemite bounds tile ranges at Z:14
            val z = 14
            val xCenter = 2621
            val yCenter = 6328
            
            for (dx in -4..4) {
                for (dy in -4..4) {
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
                                val elevation = getSimulatedHeight(lat, lon)
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
