package com.cybertrail.app.gis.ins

import kotlin.math.abs

/**
 * Kalman Filter Engine for fusing GNSS position measurements and PDR dead reckoning updates.
 * Manages accumulated drift correction upon GPS recovery.
 */
class KalmanFusionEngine {

    // State variables
    @Volatile var fusedLat: Double = 0.0
        private set
    @Volatile var fusedLon: Double = 0.0
        private set

    // Accumulated drift vector (in meters)
    @Volatile var driftNorthMeters: Double = 0.0
        private set
    @Volatile var driftEastMeters: Double = 0.0
        private set

    // Estimation variance (error covariance P)
    private var varianceLat: Double = 25.0 // ~5m std dev initial
    private var varianceLon: Double = 25.0

    // Process noise Q (PDR motion uncertainty grow per step)
    private val processNoisePerStep: Double = 0.8 // m^2 per step

    private var isInitialized = false

    fun isInitialized(): Boolean = isInitialized

    fun initialize(lat: Double, lon: Double) {
        fusedLat = lat
        fusedLon = lon
        driftNorthMeters = 0.0
        driftEastMeters = 0.0
        varianceLat = 25.0
        varianceLon = 25.0
        isInitialized = true
    }

    /**
     * Prediction step: invoked on each detected PDR step
     */
    fun predictPdrStep(stepLengthMeters: Double, headingRad: Double): PdrPositionEstimator.LatLon {
        if (!isInitialized) return PdrPositionEstimator.LatLon(fusedLat, fusedLon)

        // Project position forward using PDR kinematics
        val newPos = PdrPositionEstimator.computeNextPosition(fusedLat, fusedLon, stepLengthMeters, headingRad)
        fusedLat = newPos.latitude
        fusedLon = newPos.longitude

        // Increase estimation variance (uncertainty grows during dead reckoning)
        varianceLat += processNoisePerStep / (111319.5 * 111319.5)
        varianceLon += processNoisePerStep / (111319.5 * 111319.5)

        return newPos
    }

    /**
     * Update step: invoked when GNSS location measurement is received
     * Fuses measurement and computes Kalman gain K = P / (P + R)
     */
    fun updateGpsMeasurement(
        gpsLat: Double,
        gpsLon: Double,
        gpsAccuracyMeters: Float
    ): PdrPositionEstimator.LatLon {
        if (!isInitialized) {
            initialize(gpsLat, gpsLon)
            return PdrPositionEstimator.LatLon(fusedLat, fusedLon)
        }

        val rNoise = (gpsAccuracyMeters.toDouble() * gpsAccuracyMeters.toDouble()).coerceAtLeast(1.0)
        val rLat = rNoise / (111319.5 * 111319.5)
        val rLon = rNoise / (111319.5 * 111319.5)

        // Kalman Gain
        val kGainLat = varianceLat / (varianceLat + rLat)
        val kGainLon = varianceLon / (varianceLon + rLon)

        // Measure error (innovation)
        val innovLat = gpsLat - fusedLat
        val innovLon = gpsLon - fusedLon

        // Compute accumulated drift before correction
        val cosLat = Math.cos(fusedLat * Math.PI / 180.0)
        driftNorthMeters = innovLat * 111319.5
        driftEastMeters = innovLon * (111319.5 * cosLat)

        // Update state estimate smoothly
        fusedLat += kGainLat * innovLat
        fusedLon += kGainLon * innovLon

        // Update covariance
        varianceLat *= (1.0 - kGainLat)
        varianceLon *= (1.0 - kGainLon)

        return PdrPositionEstimator.LatLon(fusedLat, fusedLon)
    }

    /**
     * Smoothly decay/clear drift vector after GPS recovery realignment
     */
    fun resetDrift() {
        driftNorthMeters = 0.0
        driftEastMeters = 0.0
    }
}
