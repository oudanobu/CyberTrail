package com.cybertrail.app.gis.ins

import kotlin.math.pow

/**
 * Dynamic Step Length Estimator using Weinberg formula:
 * Step Length = K * (accelMax - accelMin) ^ 0.25
 */
class StepLengthEstimator(
    var kFactor: Float = 0.42f // Weinberg model coefficient for typical adult walking
) {

    /**
     * Estimates step length in meters based on step acceleration range
     */
    fun estimateStepLength(accelMax: Float, accelMin: Float): Double {
        val deltaAccel = (accelMax - accelMin).toDouble().coerceAtLeast(0.1)
        var stepLength = kFactor * deltaAccel.pow(0.25)
        
        // Clamp step length to reasonable human walking range [0.4m, 1.2m]
        if (stepLength < 0.4) stepLength = 0.4
        if (stepLength > 1.2) stepLength = 1.2

        return stepLength
    }

    /**
     * Fallback step length estimate when accel values are unavailable
     */
    fun getDefaultStepLength(): Double = 0.70 // 70cm
}
