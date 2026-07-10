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

    fun getCopernicusReaders(): List<GeoTiffReader> = copernicusProvider.getReaders()
    fun getAlosReaders(): List<GeoTiffReader> = alosProvider.getReaders()

    fun getPixelCoords(lat: Double, lon: Double): Pair<Double, Double>? {
        val srtmCoords = srtmProvider.getPixelCoords(lat, lon)
        if (srtmCoords != null) {
            return srtmCoords
        }
        for (reader in copernicusProvider.getReaders()) {
            val elev = reader.getElevation(lat, lon)
            if (elev != null) {
                return reader.getPixelCoords(lat, lon)
            }
        }
        for (reader in alosProvider.getReaders()) {
            val elev = reader.getElevation(lat, lon)
            if (elev != null) {
                return reader.getPixelCoords(lat, lon)
            }
        }
        return null
    }

    fun getResolutionMeters(lat: Double, lon: Double): Double? {
        val srtmRes = srtmProvider.getResolutionMeters(lat, lon)
        if (srtmRes != null) {
            return srtmRes
        }
        for (reader in copernicusProvider.getReaders()) {
            val elev = reader.getElevation(lat, lon)
            if (elev != null) {
                return reader.getResolutionMeters()
            }
        }
        for (reader in alosProvider.getReaders()) {
            val elev = reader.getElevation(lat, lon)
            if (elev != null) {
                return reader.getResolutionMeters()
            }
        }
        return null
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

