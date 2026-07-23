package com.cybertrail.app.gis.ins

import kotlin.math.cos
import kotlin.math.sin

/**
 * Calculates geographic coordinate updates for Pedestrian Dead Reckoning (PDR)
 */
object PdrPositionEstimator {

    private const val EARTH_RADIUS_METERS = 6378137.0
    private const val RAD_TO_DEG = 180.0 / Math.PI
    private const val DEG_TO_RAD = Math.PI / 180.0

    data class LatLon(
        val latitude: Double,
        val longitude: Double
    )

    /**
     * Compute next position given start position, step length (meters), and heading angle (radians)
     * Heading theta: 0 = North, PI/2 = East, PI = South, 3*PI/2 = West
     */
    fun computeNextPosition(
        startLat: Double,
        startLon: Double,
        stepLengthMeters: Double,
        headingRad: Double
    ): LatLon {
        val deltaNorthMeters = stepLengthMeters * cos(headingRad)
        val deltaEastMeters = stepLengthMeters * sin(headingRad)

        val deltaLatDeg = (deltaNorthMeters / EARTH_RADIUS_METERS) * RAD_TO_DEG
        val latRad = startLat * DEG_TO_RAD
        val cosLat = cos(latRad).coerceAtLeast(0.0001)
        val deltaLonDeg = (deltaEastMeters / (EARTH_RADIUS_METERS * cosLat)) * RAD_TO_DEG

        return LatLon(
            latitude = startLat + deltaLatDeg,
            longitude = startLon + deltaLonDeg
        )
    }

    /**
     * Computes distance in meters between two lat/lon coordinates (Haversine/Equirectangular)
     */
    fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = (lat2 - lat1) * DEG_TO_RAD
        val dLon = (lon2 - lon1) * DEG_TO_RAD
        val meanLat = ((lat1 + lat2) / 2.0) * DEG_TO_RAD

        val x = dLon * cos(meanLat)
        val y = dLat
        return Math.sqrt(x * x + y * y) * EARTH_RADIUS_METERS
    }
}
