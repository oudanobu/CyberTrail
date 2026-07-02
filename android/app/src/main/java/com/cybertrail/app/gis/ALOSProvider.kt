package com.cybertrail.app.gis

import android.util.Log
import java.io.File

class ALOSProvider(private val demDirectory: File) : DEMProvider {

    private val readers = ArrayList<GeoTiffReader>()

    init {
        refreshReaders()
    }

    @Synchronized
    fun refreshReaders() {
        for (reader in readers) {
            reader.close()
        }
        readers.clear()

        if (!demDirectory.exists()) return

        // Support standard geotiff / tiff files
        val files = demDirectory.listFiles { _, name ->
            name.endsWith(".tif", ignoreCase = true) || name.endsWith(".tiff", ignoreCase = true)
        }

        if (files != null) {
            for (file in files) {
                try {
                    val reader = GeoTiffReader(file)
                    readers.add(reader)
                    Log.i("ALOSProvider", "Successfully registered ALOS GeoTIFF reader for: ${file.name}")
                } catch (e: Exception) {
                    Log.e("ALOSProvider", "Failed to open ALOS GeoTIFF file: ${file.name}", e)
                }
            }
        }
    }

    override fun getElevation(lat: Double, lon: Double): Double? {
        synchronized(this) {
            for (reader in readers) {
                val elevation = reader.getElevation(lat, lon)
                if (elevation != null) {
                    return elevation
                }
            }
        }
        return null
    }

    fun getReaders(): List<GeoTiffReader> {
        return synchronized(this) {
            ArrayList(readers)
        }
    }

    fun close() {
        synchronized(this) {
            for (reader in readers) {
                reader.close()
            }
            readers.clear()
        }
    }
}
