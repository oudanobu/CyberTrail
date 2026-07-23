package com.cybertrail.app.gis.ins

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.sqrt

/**
 * Step Detector for Pedestrian Dead Reckoning (PDR)
 * Uses Accelerometer and Linear Acceleration with dynamic peak detection
 */
class StepDetector(
    private val listener: StepListener
) : SensorEventListener {

    interface StepListener {
        fun onStepDetected(stepCount: Long, stepIntervalMs: Long, accelMax: Float, accelMin: Float)
    }

    private var stepCount: Long = 0L
    private var lastStepTimeMs: Long = 0L
    private val minStepIntervalMs: Long = 250L // Minimum time between consecutive steps (~240 steps/min max)
    
    // Dynamic peak detection threshold
    private var stepThreshold: Float = 11.2f // Accelerometer magnitude peak threshold (~1.14 g)
    private var isPeakFound = false
    private var currentAccelMax: Float = 0f
    private var currentAccelMin: Float = 20f

    private val windowSize = 5
    private val accelHistory = FloatArray(windowSize)
    private var historyIndex = 0

    override fun onSensorChanged(event: SensorEvent?) {
        event ?: return

        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]

            // Calculate total acceleration magnitude
            val magnitude = sqrt(x * x + y * y + z * z)

            // Smooth magnitude using moving average
            accelHistory[historyIndex] = magnitude
            historyIndex = (historyIndex + 1) % windowSize
            var smoothedMag = 0f
            for (v in accelHistory) {
                smoothedMag += v
            }
            smoothedMag /= windowSize

            // Track min and max in peak cycle
            if (smoothedMag > currentAccelMax) {
                currentAccelMax = smoothedMag
            }
            if (smoothedMag < currentAccelMin) {
                currentAccelMin = smoothedMag
            }

            val currentTimeMs = System.currentTimeMillis()

            // Peak detection logic
            if (smoothedMag > stepThreshold && !isPeakFound) {
                isPeakFound = true
            }

            // Peak confirmed when magnitude drops below dynamic threshold after crossing
            if (isPeakFound && smoothedMag < (stepThreshold - 0.5f)) {
                if (currentTimeMs - lastStepTimeMs >= minStepIntervalMs) {
                    stepCount++
                    val interval = if (lastStepTimeMs > 0) currentTimeMs - lastStepTimeMs else 500L
                    
                    listener.onStepDetected(stepCount, interval, currentAccelMax, currentAccelMin)
                    lastStepTimeMs = currentTimeMs

                    // Dynamic threshold adaptation
                    stepThreshold = 0.7f * stepThreshold + 0.3f * (0.5f * (currentAccelMax + currentAccelMin))
                    if (stepThreshold < 10.2f) stepThreshold = 10.2f
                    if (stepThreshold > 14.0f) stepThreshold = 14.0f
                }

                // Reset peak cycle trackers
                isPeakFound = false
                currentAccelMax = 0f
                currentAccelMin = 20f
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    fun getStepCount(): Long = stepCount

    fun reset() {
        stepCount = 0L
        lastStepTimeMs = 0L
        isPeakFound = false
        currentAccelMax = 0f
        currentAccelMin = 20f
    }
}
