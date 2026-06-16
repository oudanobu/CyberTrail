package com.cybertrail.app.gis

import android.content.Context
import android.util.Log
import java.io.File
import java.io.IOException

class DEMLoader(private val context: Context) {

    companion object {
        private const val TAG = "DEMLoader"
    }

    fun scanAndLoadLocalGisFiles() {
        Log.i(TAG, "Scanning for offline SRTM or GeoTIFF files under context.filesDir/gis")
        val gisDir = File(context.filesDir, "gis")
        if (!gisDir.exists()) {
            gisDir.mkdirs()
        }
    }

    fun getElevation(lat: Double, lon: Double): Double? {
        // Fallback to null to trigger standard simulation elevation calculations
        return null
    }
}
