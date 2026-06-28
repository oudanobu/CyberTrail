package com.cybertrail.app.gis

interface DEMProvider {
    /**
     * Returns elevation in meters at the given latitude and longitude,
     * or null if the coordinates are out of bounds or no DEM data is available.
     */
    fun getElevation(lat: Double, lon: Double): Double?
}
