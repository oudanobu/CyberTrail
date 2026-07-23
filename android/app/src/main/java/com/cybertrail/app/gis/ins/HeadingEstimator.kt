package com.cybertrail.app.gis.ins

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.PI
import kotlin.math.atan2

/**
 * Heading Estimator using TYPE_ROTATION_VECTOR, Accelerometer + Magnetometer, and Gyroscope
 * Yields azimuth angle (heading) in degrees (0..360) and radians
 */
class HeadingEstimator : SensorEventListener {

    private val rotationMatrix = FloatArray(9)
    private val orientationAngles = FloatArray(3)

    private val gravityValues = FloatArray(3)
    private val geomagneticValues = FloatArray(3)
    private var hasGravity = false
    private var hasGeomagnetic = false

    @Volatile
    var headingDegrees: Float = 0f
        private set

    @Volatile
    var headingRadians: Float = 0f
        private set

    private var filterAlpha: Float = 0.25f // Low pass filter smoothing factor

    override fun onSensorChanged(event: SensorEvent?) {
        event ?: return

        when (event.sensor.type) {
            Sensor.TYPE_ROTATION_VECTOR -> {
                SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                SensorManager.getOrientation(rotationMatrix, orientationAngles)
                
                var azimuthRad = orientationAngles[0]
                if (azimuthRad < 0) azimuthRad += (2 * PI).toFloat()
                
                headingRadians = smoothAngle(headingRadians, azimuthRad, filterAlpha)
                headingDegrees = Math.toDegrees(headingRadians.toDouble()).toFloat()
            }
            Sensor.TYPE_ACCELEROMETER, Sensor.TYPE_GRAVITY -> {
                System.arraycopy(event.values, 0, gravityValues, 0, 3)
                hasGravity = true
                calculateHeadingFromAccelMag()
            }
            Sensor.TYPE_MAGNETIC_FIELD -> {
                System.arraycopy(event.values, 0, geomagneticValues, 0, 3)
                hasGeomagnetic = true
                calculateHeadingFromAccelMag()
            }
        }
    }

    private fun calculateHeadingFromAccelMag() {
        if (hasGravity && hasGeomagnetic) {
            val success = SensorManager.getRotationMatrix(rotationMatrix, null, gravityValues, geomagneticValues)
            if (success) {
                SensorManager.getOrientation(rotationMatrix, orientationAngles)
                var azimuthRad = orientationAngles[0]
                if (azimuthRad < 0) azimuthRad += (2 * PI).toFloat()

                headingRadians = smoothAngle(headingRadians, azimuthRad, filterAlpha)
                headingDegrees = Math.toDegrees(headingRadians.toDouble()).toFloat()
            }
        }
    }

    /**
     * Angular low-pass filter handling wrap-around at 0 / 2PI
     */
    private fun smoothAngle(currentRad: Float, targetRad: Float, alpha: Float): Float {
        var diff = targetRad - currentRad
        while (diff < -PI) diff += (2 * PI).toFloat()
        while (diff > PI) diff -= (2 * PI).toFloat()
        return currentRad + alpha * diff
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
