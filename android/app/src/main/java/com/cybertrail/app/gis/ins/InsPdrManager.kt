package com.cybertrail.app.gis.ins

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.cybertrail.app.gis.DEMLoader
import com.cybertrail.app.gis.DEMSystem
import com.cybertrail.app.gis.TerrainAnalyzer

/**
 * Main Coordinator for CyberTrail Inertial Navigation System (INS / PDR)
 * Fuses GNSS + IMU Sensors (Accelerometer, Gyroscope, Magnetometer, Rotation Vector, Linear Acceleration, Gravity)
 * Implements navigation state transitions: NORMAL -> HYBRID -> INS_ONLY -> GPS_RECOVERY
 * Real-time re-queries DEM elevation, slope, and aspect on every PDR position update.
 */
class InsPdrManager(
    private val context: Context,
    private val listener: InsPdrListener? = null,
    private val demSystem: DEMSystem = DEMSystem(context),
    private val demLoader: DEMLoader = demSystem.demLoader,
    private val terrainAnalyzer: TerrainAnalyzer = demSystem.terrainAnalyzer
) : StepDetector.StepListener, SensorEventListener {

    interface InsPdrListener {
        fun onPositionUpdated(
            lat: Double,
            lon: Double,
            elevation: Double?,
            slope: Double?,
            aspect: Double?,
            navState: NavState,
            source: String,
            headingDeg: Float,
            stepCount: Long,
            driftMeters: Double
        )

        fun onNavStateChanged(newState: NavState, oldState: NavState)
    }

    companion object {
        private const val TAG = "InsPdrManager"
        private const val GPS_LOST_TIMEOUT_MS = 4000L // 4 seconds without GPS triggers INS_ONLY state
        private const val GPS_ACCURACY_THRESHOLD_METERS = 15.0f
    }

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    private val stepDetector = StepDetector(this)
    private val headingEstimator = HeadingEstimator()
    private val stepLengthEstimator = StepLengthEstimator()
    private val kalmanEngine = KalmanFusionEngine()

    @Volatile var currentNavState: NavState = NavState.NORMAL
        private set

    @Volatile var lastGpsTimeMs: Long = 0L
        private set

    @Volatile var totalStepCount: Long = 0L
        private set

    @Volatile var currentHeadingDeg: Float = 0f
        private set

    @Volatile var lastElevation: Double? = null
        private set

    @Volatile var lastSlope: Double? = null
        private set

    @Volatile var lastAspect: Double? = null
        private set

    private val mainHandler = Handler(Looper.getMainLooper())
    private var isStarted = false

    // Timeout checker for GPS loss
    private val gpsCheckRunnable = object : Runnable {
        override fun run() {
            if (!isStarted) return
            val timeSinceGps = System.currentTimeMillis() - lastGpsTimeMs
            if (lastGpsTimeMs > 0 && timeSinceGps > GPS_LOST_TIMEOUT_MS) {
                if (currentNavState == NavState.NORMAL || currentNavState == NavState.HYBRID) {
                    transitionNavState(NavState.INS_ONLY)
                    Log.w(TAG, "GPS signal lost (>4s timeout). NavState transitioned to INS_ONLY.")
                }
            }
            mainHandler.postDelayed(this, 2000L)
        }
    }

    fun start() {
        if (isStarted) return
        isStarted = true

        registerSensors()
        mainHandler.postDelayed(gpsCheckRunnable, 2000L)
        Log.d(TAG, "CyberTrail INS/PDR Manager started.")
    }

    fun stop() {
        if (!isStarted) return
        isStarted = false

        unregisterSensors()
        mainHandler.removeCallbacks(gpsCheckRunnable)
        Log.d(TAG, "CyberTrail INS/PDR Manager stopped.")
    }

    private fun registerSensors() {
        val sensorsToRegister = listOf(
            Sensor.TYPE_ACCELEROMETER,
            Sensor.TYPE_GYROSCOPE,
            Sensor.TYPE_MAGNETIC_FIELD,
            Sensor.TYPE_ROTATION_VECTOR,
            Sensor.TYPE_LINEAR_ACCELERATION,
            Sensor.TYPE_GRAVITY
        )

        for (sensorType in sensorsToRegister) {
            val sensor = sensorManager.getDefaultSensor(sensorType)
            if (sensor != null) {
                sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME)
            } else {
                Log.w(TAG, "Sensor type $sensorType not available on device.")
            }
        }
    }

    private fun unregisterSensors() {
        sensorManager.unregisterListener(this)
    }

    /**
     * Process GNSS Location update received from FusedLocationProvider / LocationManager
     */
    fun onGpsLocation(location: Location) {
        lastGpsTimeMs = System.currentTimeMillis()
        val accuracy = location.accuracy

        val previousState = currentNavState

        if (previousState == NavState.INS_ONLY) {
            // Recovered from GPS loss
            transitionNavState(NavState.GPS_RECOVERY)
            Log.i(TAG, "GPS signal recovered! Entering GPS_RECOVERY for smooth drift correction.")

            // Perform Kalman update and smooth drift correction
            val fusedPos = kalmanEngine.updateGpsMeasurement(location.latitude, location.longitude, accuracy)
            queryDemAndNotify(fusedPos.latitude, fusedPos.longitude, "GPS+INS")

            // Transition back to NORMAL or HYBRID after drift alignment
            mainHandler.postDelayed({
                if (currentNavState == NavState.GPS_RECOVERY) {
                    kalmanEngine.resetDrift()
                    val nextState = if (accuracy <= GPS_ACCURACY_THRESHOLD_METERS) NavState.NORMAL else NavState.HYBRID
                    transitionNavState(nextState)
                }
            }, 1000L)
        } else {
            val nextState = if (accuracy <= GPS_ACCURACY_THRESHOLD_METERS) NavState.NORMAL else NavState.HYBRID
            if (currentNavState != nextState && currentNavState != NavState.GPS_RECOVERY) {
                transitionNavState(nextState)
            }

            val fusedPos = kalmanEngine.updateGpsMeasurement(location.latitude, location.longitude, accuracy)
            val sourceStr = if (currentNavState == NavState.HYBRID) "GPS+INS" else "GPS"
            queryDemAndNotify(fusedPos.latitude, fusedPos.longitude, sourceStr)
        }
    }

    /**
     * Callback when a step is detected by StepDetector
     */
    override fun onStepDetected(stepCount: Long, stepIntervalMs: Long, accelMax: Float, accelMin: Float) {
        totalStepCount = stepCount
        val headingRad = headingEstimator.headingRadians
        currentHeadingDeg = headingEstimator.headingDegrees

        val stepLengthMeters = stepLengthEstimator.estimateStepLength(accelMax, accelMin)

        // Predict new position via Kalman Filter
        val newPos = kalmanEngine.predictPdrStep(stepLengthMeters, headingRad.toDouble())

        val sourceStr = when (currentNavState) {
            NavState.INS_ONLY -> "INS"
            NavState.HYBRID -> "GPS+INS"
            NavState.GPS_RECOVERY -> "GPS+INS"
            else -> "GPS"
        }

        // Re-query DEM immediately so elevation, slope, aspect refresh in real-time even without GPS
        queryDemAndNotify(newPos.latitude, newPos.longitude, sourceStr)
    }

    /**
     * Query local DEM file system for Elevation, Slope, and Aspect at current (lat, lon)
     */
    private fun queryDemAndNotify(lat: Double, lon: Double, source: String) {
        val elev = demSystem.getElevation(lat, lon)
        lastElevation = elev

        // Calculate terrain slope and aspect from DEM 3x3 matrix
        var slope: Double? = null
        var aspect: Double? = null

        val hgt = demSystem.getElevation(lat, lon)
        if (hgt != null) {
            val res = terrainAnalyzer.analyzeLocation(lat, lon)
            slope = res.slope
            aspect = res.aspect
        }

        lastSlope = slope
        lastAspect = aspect

        val driftMeters = Math.sqrt(
            kalmanEngine.driftNorthMeters * kalmanEngine.driftNorthMeters +
            kalmanEngine.driftEastMeters * kalmanEngine.driftEastMeters
        )

        listener?.onPositionUpdated(
            lat = lat,
            lon = lon,
            elevation = elev,
            slope = slope,
            aspect = aspect,
            navState = currentNavState,
            source = source,
            headingDeg = currentHeadingDeg,
            stepCount = totalStepCount,
            driftMeters = driftMeters
        )
    }

    private fun transitionNavState(newState: NavState) {
        val oldState = currentNavState
        if (oldState != newState) {
            currentNavState = newState
            listener?.onNavStateChanged(newState, oldState)
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        stepDetector.onSensorChanged(event)
        headingEstimator.onSensorChanged(event)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    fun getFusedLatitude(): Double = kalmanEngine.fusedLat
    fun getFusedLongitude(): Double = kalmanEngine.fusedLon
}
