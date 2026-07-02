package com.cybertrail.app.gis

import android.content.Context
import android.util.Log
import java.io.File

class DEMLoader(private val context: Context) {

    private val srtmProvider: SRTMProvider
    private val copernicusProvider: CopernicusProvider
    private val alosProvider: ALOSProvider

    init {
        val baseDir = File(android.os.Environment.getExternalStorageDirectory(), "CyberTrail")
        val demDir = File(baseDir, "dem")
        if (!demDir.exists()) {
            demDir.mkdirs()
        }
        srtmProvider = SRTMProvider(demDir)
        copernicusProvider = CopernicusProvider(demDir)
        alosProvider = ALOSProvider(demDir)
    }

    companion object {
        private const val TAG = "DEMLoader"
    }

    fun scanAndLoadLocalGisFiles() {
        val baseDir = File(android.os.Environment.getExternalStorageDirectory(), "CyberTrail")
        val demDir = File(baseDir, "dem")
        Log.i(TAG, "Scanning for offline DEM tiles under unified storage: ${demDir.absolutePath}")
        if (!demDir.exists()) {
            demDir.mkdirs()
        }
        copernicusProvider.refreshReaders()
        alosProvider.refreshReaders()
    }

    fun close() {
        try {
            copernicusProvider.close()
            alosProvider.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing DEMLoader providers", e)
        }
    }

    fun hasOfflineDemFiles(): Boolean {
        val baseDir = File(android.os.Environment.getExternalStorageDirectory(), "CyberTrail")
        val demDir = File(baseDir, "dem")
        if (!demDir.exists()) return false
        val files = demDir.listFiles { _, name -> 
            name.endsWith(".hgt", ignoreCase = true) || 
            name.endsWith(".bil", ignoreCase = true) || 
            name.endsWith(".tif", ignoreCase = true) ||
            name.endsWith(".tiff", ignoreCase = true) ||
            name.endsWith(".img", ignoreCase = true)
        }
        return files != null && files.isNotEmpty()
    }

    fun getElevation(lat: Double, lon: Double): Double? {
        // Try SRTM first (highest priority)
        val srtmElevation = srtmProvider.getElevation(lat, lon)
        if (srtmElevation != null) {
            return srtmElevation
        }
        
        // Try Copernicus
        val copernicusElevation = copernicusProvider.getElevation(lat, lon)
        if (copernicusElevation != null) {
            return copernicusElevation
        }

        // Try ALOS
        return alosProvider.getElevation(lat, lon)
    }
}

