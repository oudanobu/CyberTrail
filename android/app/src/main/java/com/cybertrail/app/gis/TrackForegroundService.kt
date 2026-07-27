package com.cybertrail.app.gis

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.cybertrail.app.MapActivity
import com.cybertrail.app.db.AppDatabase
import com.cybertrail.app.db.Track
import com.cybertrail.app.db.TrackPoint
import java.io.File
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors

class TrackForegroundService : Service(), SensorEventListener {

    companion object {
        private const val TAG = "TrackForegroundService"
        private const val NOTIFICATION_ID = 888
        private const val CHANNEL_ID = "track_recording_channel"

        const val ACTION_START = "com.cybertrail.app.action.START"
        const val ACTION_PAUSE = "com.cybertrail.app.action.PAUSE"
        const val ACTION_RESUME = "com.cybertrail.app.action.RESUME"
        const val ACTION_STOP = "com.cybertrail.app.action.STOP"
        
        const val EXTRA_TRACK_ID = "extra_track_id"
        
        // Broadcast actions
        const val ACTION_NEW_POINT = "com.cybertrail.app.action.NEW_POINT"
        const val EXTRA_POINT_LAT = "extra_point_lat"
        const val EXTRA_POINT_LON = "extra_point_lon"
        const val EXTRA_POINT_ALT = "extra_point_alt"
        const val EXTRA_POINT_TIME = "extra_point_time"
        const val EXTRA_POINT_SPEED = "extra_point_speed"
        const val EXTRA_POINT_ACCURACY = "extra_point_accuracy"

        const val ACTION_TICK = "com.cybertrail.app.action.TICK"
        const val EXTRA_DURATION = "extra_duration"
        const val EXTRA_DISTANCE = "extra_distance"
        const val EXTRA_POINTS_COUNT = "extra_points_count"
        const val EXTRA_STEP_COUNT = "extra_step_count"
        const val EXTRA_CURRENT_CADENCE = "extra_current_cadence"
        const val EXTRA_AVG_STEP_LENGTH = "extra_avg_step_length"
        
        @Volatile
        var isRunning = false
            private set
            
        @Volatile
        var trackId: Long = 0L
            private set

        @Volatile
        var currentStatus: String = "STOPPED" // "RECORDING", "PAUSED", "STOPPED"
            private set
            
        val currentPoints = mutableListOf<TrackPoint>()

        @Volatile
        var trackStartTime: Long = 0L
            private set

        @Volatile
        var elapsedSeconds: Long = 0L
            internal set

        @Volatile
        var currentDistanceMeters: Float = 0f
            internal set

        @Volatile
        var currentPointCount: Int = 0
            internal set

        @Volatile
        var totalDistanceMeters: Float = 0f
            private set

        @Volatile
        var activeDurationSeconds: Long = 0L
            private set

        @Volatile
        var totalAscentMeters: Double = 0.0
            private set

        @Volatile
        var sessionStepCount: Int = 0
            internal set

        @Volatile
        var currentCadence: Float = 0f
            internal set

        @Volatile
        var averageStepLength: Float = 0f
            internal set
    }

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private val dbExecutor = Executors.newSingleThreadExecutor()
    private var lastDiskSaveTime = 0L
    private val diskSaveIntervalMs = 15000L // save every 15s to ensure disk-sync and gpx-refresh

    // Step counting hardware & fallback fields
    private var sensorManager: SensorManager? = null
    private var stepCounterSensor: Sensor? = null
    private var isStepCounterRegistered = false

    private var initialHwSteps = -1L
    private var stepsInPreviousSegments = 0

    private var initialInsSteps = -1L
    private var lastHandledInsSteps = -1L

    private val recentStepTimestamps = ConcurrentLinkedQueue<Long>()

    private val serviceHandler = Handler(Looper.getMainLooper())
    private val timerRunnable = object : Runnable {
        override fun run() {
            if (currentStatus == "RECORDING") {
                elapsedSeconds++
                activeDurationSeconds = elapsedSeconds
                
                processInsStepUpdate()
                updateStepStats()
                updateNotificationWithStats()

                // Broadcast tick
                val intent = Intent(ACTION_TICK).apply {
                    putExtra(EXTRA_DURATION, elapsedSeconds)
                    putExtra(EXTRA_DISTANCE, currentDistanceMeters)
                    putExtra(EXTRA_POINTS_COUNT, currentPointCount)
                    putExtra(EXTRA_STEP_COUNT, sessionStepCount)
                    putExtra(EXTRA_CURRENT_CADENCE, currentCadence)
                    putExtra(EXTRA_AVG_STEP_LENGTH, averageStepLength)
                }
                sendBroadcast(intent)
            }
            serviceHandler.postDelayed(this, 1000L)
        }
    }

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            val location = locationResult.lastLocation ?: return
            handleNewLocation(location)
        }
    }

    private lateinit var demSystem: DEMSystem
    private lateinit var insPdrManager: com.cybertrail.app.gis.ins.InsPdrManager
    private lateinit var positionManager: PositionManager

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        demSystem = DEMSystem(applicationContext)

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        stepCounterSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        Log.i(TAG, "Hardware TYPE_STEP_COUNTER available: ${stepCounterSensor != null}")
        
        insPdrManager = com.cybertrail.app.gis.ins.InsPdrManager(
            applicationContext,
            object : com.cybertrail.app.gis.ins.InsPdrManager.InsPdrListener {
                override fun onPositionUpdated(
                    lat: Double,
                    lon: Double,
                    elevation: Double?,
                    slope: Double?,
                    aspect: Double?,
                    navState: com.cybertrail.app.gis.ins.NavState,
                    source: String,
                    headingDeg: Float,
                    stepCount: Long,
                    driftMeters: Double
                ) {
                    processInsStepUpdate()
                    positionManager.onInsPdrPositionUpdate(lat, lon, elevation)
                    handlePositionUpdateFromIns(lat, lon, elevation, source)
                }

                override fun onNavStateChanged(
                    newState: com.cybertrail.app.gis.ins.NavState,
                    oldState: com.cybertrail.app.gis.ins.NavState
                ) {
                    Log.i(TAG, "NavState changed from $oldState to $newState")
                }
            }
        )

        positionManager = PositionManager(
            applicationContext,
            object : PositionManager.PositionListener {
                override fun onPositionUpdated(location: CyberLocation) {
                    Log.d(TAG, "PositionManager update: Lat=${location.latitude}, Lon=${location.longitude}, Source=${location.source}")
                }

                override fun onLocationSourceChanged(newSource: LocationSource, oldSource: LocationSource) {
                    Log.i(TAG, "Location source shifted from $oldSource to $newSource")
                }
            },
            insPdrManager
        )

        insPdrManager.start()
        positionManager.startLocationUpdates()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        val id = intent?.getLongExtra(EXTRA_TRACK_ID, 0L) ?: 0L
        
        Log.d(TAG, "onStartCommand action: $action, trackId: $id")

        when (action) {
            ACTION_START -> {
                if (id != 0L) {
                    trackId = id
                    isRunning = true
                    currentStatus = "RECORDING"
                    trackStartTime = System.currentTimeMillis()
                    elapsedSeconds = 0L
                    currentDistanceMeters = 0f
                    currentPointCount = 0
                    totalDistanceMeters = 0f
                    activeDurationSeconds = 0L
                    totalAscentMeters = 0.0

                    sessionStepCount = 0
                    currentCadence = 0f
                    averageStepLength = 0f
                    stepsInPreviousSegments = 0
                    initialHwSteps = -1L
                    initialInsSteps = insPdrManager.totalStepCount
                    lastHandledInsSteps = initialInsSteps
                    recentStepTimestamps.clear()
                    
                    synchronized(currentPoints) {
                        currentPoints.clear()
                    }
                    lastDiskSaveTime = System.currentTimeMillis()
                    
                    dbExecutor.execute {
                        val db = AppDatabase.getDatabase(applicationContext)
                        val points = db.trackDao().getTrackPoints(id)
                        synchronized(currentPoints) {
                            currentPoints.addAll(points)
                        }
                        
                        // Recalculate distance and ascent
                        var computedDist = 0f
                        var computedAscent = 0.0
                        if (points.size >= 2) {
                            for (i in 1 until points.size) {
                                val results = FloatArray(1)
                                Location.distanceBetween(
                                    points[i - 1].latitude, points[i - 1].longitude,
                                    points[i].latitude, points[i].longitude,
                                    results
                                )
                                computedDist += results[0]

                                val currentEle = points[i].elevation
                                val prevEle = points[i - 1].elevation
                                if (currentEle != null && prevEle != null) {
                                    val diff = currentEle - prevEle
                                    if (diff > 0.0) {
                                        computedAscent += diff
                                    }
                                }
                            }
                        }
                        currentDistanceMeters = computedDist
                        totalDistanceMeters = computedDist
                        totalAscentMeters = computedAscent
                        currentPointCount = points.size

                        val track = db.trackDao().getTrackById(id)
                        if (track != null) {
                            trackStartTime = track.startTime
                            val calculatedElapsed = (System.currentTimeMillis() - track.startTime) / 1000L
                            elapsedSeconds = if (calculatedElapsed < 0) 0L else calculatedElapsed
                            activeDurationSeconds = elapsedSeconds
                        }
                    }

                    startForegroundServiceCompat()
                    registerLocationUpdates()
                    registerStepCounterListener()
                    
                    serviceHandler.removeCallbacks(timerRunnable)
                    serviceHandler.postDelayed(timerRunnable, 1000L)
                }
            }
            ACTION_PAUSE -> {
                currentStatus = "PAUSED"
                stepsInPreviousSegments = sessionStepCount
                initialHwSteps = -1L
                initialInsSteps = -1L
                recentStepTimestamps.clear()
                unregisterStepCounterListener()

                updateNotificationWithStats()
                unregisterLocationUpdates()
                forceSaveToDisk()
            }
            ACTION_RESUME -> {
                currentStatus = "RECORDING"
                initialHwSteps = -1L
                initialInsSteps = insPdrManager.totalStepCount
                lastHandledInsSteps = initialInsSteps
                registerStepCounterListener()

                updateNotificationWithStats()
                registerLocationUpdates()
                forceSaveToDisk()
            }
            ACTION_STOP -> {
                currentStatus = "STOPPED"
                isRunning = false
                unregisterStepCounterListener()
                stepsInPreviousSegments = sessionStepCount

                val finalAvgCadence = if (activeDurationSeconds > 0) (sessionStepCount * 60f / activeDurationSeconds) else 0f
                val finalAvgStepLen = if (sessionStepCount > 0) (currentDistanceMeters / sessionStepCount) else 0f

                dbExecutor.execute {
                    val db = AppDatabase.getDatabase(applicationContext)
                    val track = db.trackDao().getTrackById(trackId)
                    if (track != null) {
                        track.status = "STOPPED"
                        track.endTime = System.currentTimeMillis()
                        track.stepCount = sessionStepCount
                        track.averageCadence = finalAvgCadence
                        track.averageStepLength = finalAvgStepLen
                        db.trackDao().updateTrack(track)
                    }
                    saveTrackFile()
                }

                serviceHandler.removeCallbacks(timerRunnable)
                unregisterLocationUpdates()
                stopForeground(true)
                stopSelf()
            }
        }

        return START_STICKY
    }

    private fun registerStepCounterListener() {
        if (stepCounterSensor != null && !isStepCounterRegistered) {
            try {
                isStepCounterRegistered = sensorManager?.registerListener(
                    this,
                    stepCounterSensor,
                    SensorManager.SENSOR_DELAY_UI
                ) ?: false
                Log.d(TAG, "Registered TYPE_STEP_COUNTER listener: $isStepCounterRegistered")
            } catch (e: Exception) {
                Log.e(TAG, "Error registering TYPE_STEP_COUNTER listener", e)
            }
        }
    }

    private fun unregisterStepCounterListener() {
        if (isStepCounterRegistered) {
            try {
                sensorManager?.unregisterListener(this)
                isStepCounterRegistered = false
                Log.d(TAG, "Unregistered TYPE_STEP_COUNTER listener")
            } catch (e: Exception) {
                Log.e(TAG, "Error unregistering TYPE_STEP_COUNTER listener", e)
            }
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || event.sensor.type != Sensor.TYPE_STEP_COUNTER) return
        if (currentStatus != "RECORDING") return

        val totalHw = event.values[0].toLong()
        if (initialHwSteps < 0L) {
            initialHwSteps = totalHw
        }

        val currentSegmentSteps = (totalHw - initialHwSteps).coerceAtLeast(0L).toInt()
        val newTotalSteps = stepsInPreviousSegments + currentSegmentSteps
        val addedSteps = newTotalSteps - sessionStepCount

        if (addedSteps > 0) {
            val now = System.currentTimeMillis()
            for (i in 0 until addedSteps) {
                recentStepTimestamps.add(now)
            }
            sessionStepCount = newTotalSteps
            updateStepStats()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun processInsStepUpdate() {
        if (stepCounterSensor != null) return // Hardware step counter takes priority
        if (currentStatus != "RECORDING") return

        val totalIns = insPdrManager.totalStepCount
        if (initialInsSteps < 0L) {
            initialInsSteps = totalIns
            lastHandledInsSteps = totalIns
        }

        val currentSegmentSteps = (totalIns - initialInsSteps).coerceAtLeast(0L).toInt()
        val newTotalSteps = stepsInPreviousSegments + currentSegmentSteps
        val addedSteps = (totalIns - lastHandledInsSteps).coerceAtLeast(0L).toInt()

        if (addedSteps > 0) {
            lastHandledInsSteps = totalIns
            val now = System.currentTimeMillis()
            for (i in 0 until addedSteps) {
                recentStepTimestamps.add(now)
            }
            sessionStepCount = newTotalSteps
            updateStepStats()
        }
    }

    private fun updateStepStats() {
        val now = System.currentTimeMillis()
        val cutoff = now - 10_000L
        while (recentStepTimestamps.peek()?.let { it < cutoff } == true) {
            recentStepTimestamps.poll()
        }

        val recentCount = recentStepTimestamps.size
        currentCadence = if (recentCount > 0) {
            (recentCount * 60f / 10f)
        } else if (elapsedSeconds > 0) {
            (sessionStepCount * 60f / elapsedSeconds)
        } else {
            0f
        }

        averageStepLength = if (sessionStepCount > 0) {
            (currentDistanceMeters / sessionStepCount)
        } else {
            0f
        }
    }

    private fun registerLocationUpdates() {
        try {
            val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 3000L).apply {
                setMinUpdateIntervalMillis(1500L)
                setMinUpdateDistanceMeters(1f)
            }.build()

            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
            Log.d(TAG, "Registered FusedLocationProviderClient location updates")
        } catch (e: SecurityException) {
            Log.e(TAG, "No location permissions to request updates", e)
        } catch (e: Exception) {
            Log.e(TAG, "Error registering location updates", e)
        }
    }

    private fun unregisterLocationUpdates() {
        try {
            fusedLocationClient.removeLocationUpdates(locationCallback)
            Log.d(TAG, "Unregistered FusedLocationProviderClient location updates")
        } catch (e: Exception) {
            Log.e(TAG, "Error removing location updates", e)
        }
    }

    private fun handleNewLocation(location: Location) {
        if (currentStatus != "RECORDING") return
        insPdrManager.onGpsLocation(location)
    }

    private fun handlePositionUpdateFromIns(lat: Double, lon: Double, elevationVal: Double?, source: String) {
        if (currentStatus != "RECORDING") return

        dbExecutor.execute {
            val lastPt = synchronized(currentPoints) { currentPoints.lastOrNull() }
            val nowMs = System.currentTimeMillis()
            val timeDiff = nowMs - (lastPt?.timestamp ?: 0L)
            
            val distDiff = lastPt?.let {
                val results = FloatArray(1)
                Location.distanceBetween(it.latitude, it.longitude, lat, lon, results)
                results[0]
            } ?: 0f

            if (lastPt == null || timeDiff >= 1500L || distDiff >= 1.5f) {
                if (lastPt != null) {
                    totalDistanceMeters += distDiff
                    currentDistanceMeters = totalDistanceMeters
                    
                    val lastEle = lastPt.elevation
                    if (elevationVal != null && lastEle != null) {
                        val eleDiff = elevationVal - lastEle
                        if (eleDiff > 0.0) {
                            totalAscentMeters += eleDiff
                        }
                    }
                }

                val tp = TrackPoint(
                    trackId = trackId,
                    latitude = lat,
                    longitude = lon,
                    elevation = elevationVal,
                    timestamp = nowMs,
                    speed = 0f,
                    accuracy = if (source == "GPS") 5f else 12f,
                    provider = source
                )

                val db = AppDatabase.getDatabase(applicationContext)
                db.trackDao().insertTrackPoint(tp)

                synchronized(currentPoints) {
                    currentPoints.add(tp)
                    currentPointCount = currentPoints.size
                }

                Log.d(TAG, "Recorded $source point: Lat $lat, Lon $lon, Elev $elevationVal")

                val intent = Intent(ACTION_NEW_POINT).apply {
                    putExtra(EXTRA_POINT_LAT, tp.latitude)
                    putExtra(EXTRA_POINT_LON, tp.longitude)
                    putExtra(EXTRA_POINT_ALT, tp.elevation)
                    putExtra(EXTRA_POINT_TIME, tp.timestamp)
                    putExtra(EXTRA_POINT_SPEED, tp.speed)
                    putExtra(EXTRA_POINT_ACCURACY, tp.accuracy)
                }
                sendBroadcast(intent)

                val now = System.currentTimeMillis()
                if (now - lastDiskSaveTime >= diskSaveIntervalMs) {
                    saveTrackFile()
                    lastDiskSaveTime = now
                }
            }
        }
    }

    private fun saveTrackFile() {
        val tId = trackId
        if (tId == 0L) return
        val db = AppDatabase.getDatabase(applicationContext)
        val track = db.trackDao().getTrackById(tId)
        if (track != null) {
            val pts = synchronized(currentPoints) { ArrayList(currentPoints) }
            val anchors = db.trackDao().getPhotoAnchorsForTrack(tId)
            track.status = currentStatus
            track.stepCount = sessionStepCount
            track.averageCadence = if (activeDurationSeconds > 0) (sessionStepCount * 60f / activeDurationSeconds) else 0f
            track.averageStepLength = if (sessionStepCount > 0) (currentDistanceMeters / sessionStepCount) else 0f
            TrackFileHelper.saveTrackToJson(track, pts, anchors)
            Log.d(TAG, "Saved track to disk: ${pts.size} points, $sessionStepCount steps")
        }
    }

    private fun forceSaveToDisk() {
        dbExecutor.execute {
            saveTrackFile()
        }
    }

    private fun startForegroundServiceCompat() {
        val notification = createNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotification(): Notification {
        val notificationIntent = Intent(this, MapActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT else PendingIntent.FLAG_UPDATE_CURRENT
        )

        val titleText = if (currentStatus == "RECORDING") "CyberTrail 正在记录轨迹" else "CyberTrail 轨迹记录已暂停"
        val formattedDist = currentDistanceMeters / 1000f
        val formattedTime = formatDuration(elapsedSeconds)

        val detailText = "时间：%s\n距离：%.2f km\n轨迹点：%d\n步数：%d 步 | 步频：%.0f spm".format(
            formattedTime,
            formattedDist,
            currentPointCount,
            sessionStepCount,
            currentCadence
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(titleText)
            .setContentText("时间：%s | 距离：%.2f km | 步数：%d 步".format(formattedTime, formattedDist, sessionStepCount))
            .setStyle(NotificationCompat.BigTextStyle().bigText(detailText))
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotificationWithStats() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, createNotification())
    }

    private fun formatDuration(seconds: Long): String {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return "%02d:%02d:%02d".format(h, m, s)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "CyberTrail Track Recording Channel",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Channel for background track recording service"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterStepCounterListener()
        insPdrManager.stop()
        positionManager.stopLocationUpdates()
        unregisterLocationUpdates()
        dbExecutor.shutdown()
        Log.d(TAG, "Service destroyed")
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
