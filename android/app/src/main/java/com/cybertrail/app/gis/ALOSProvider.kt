package com.cybertrail.app.gis

import java.io.File

class ALOSProvider(private val demDirectory: File) : DEMProvider {
    override fun getElevation(lat: Double, lon: Double): Double? {
        // Future support for ALOS DEM (.tif)
        return null
    }
}
