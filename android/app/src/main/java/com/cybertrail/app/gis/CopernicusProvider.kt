package com.cybertrail.app.gis

import java.io.File

class CopernicusProvider(private val demDirectory: File) : DEMProvider {
    override fun getElevation(lat: Double, lon: Double): Double? {
        // Future support for Copernicus DEM (.bil / .tif)
        return null
    }
}
