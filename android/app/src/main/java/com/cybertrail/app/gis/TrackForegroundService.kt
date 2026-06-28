package com.cybertrail.app.gis

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
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
import java.util.concurrent.Executors

class TrackForegroundService : Service() {

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
    }

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private val dbExecutor = Executors.newSingleThreadExecutor()
    private var lastDiskSaveTime = 0L
    private val diskSaveIntervalMs = 15000L // save every 15s to ensure disk-sync and gpx-refresh

    private val serviceHandler = Handler(Looper.getMainLooper())
    private val timerRunnable = object : Runnable {
        override fun run() {
            if (currentStatus == "RECORDING") {
                elapsedSeconds++
                activeDurationSeconds = elapsedSeconds
                
                updateNotificationWithStats()

                // Broadcast tick
                val intent = Intent(ACTION_TICK).apply {
                    putExtra(EXTRA_DURATION, elapsedSeconds)
                    putExtra(EXTRA_DISTANCE, currentDistanceMeters)
                    putExtra(EXTRA_POINTS_COUNT, currentPointCount)
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

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        demSystem = DEMSystem(applicationContext)
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
                    
                    serviceHandler.removeCallbacks(timerRunnable)
                    serviceHandler.postDelayed(timerRunnable, 1000L)
                }
            }
            ACTION_PAUSE -> {
                currentStatus = "PAUSED"
                updateNotificationWithStats()
                unregisterLocationUpdates()
                forceSaveToDisk()
            }
            ACTION_RESUME -> {
                currentStatus = "RECORDING"
                updateNotificationWithStats()
                registerLocationUpdates()
                forceSaveToDisk()
            }
            ACTION_STOP -> {
                currentStatus = "STOPPED"
                isRunning = false
                serviceHandler.removeCallbacks(timerRunnable)
                unregisterLocationUpdates()
                forceSaveToDisk()
                stopForeground(true)
                stopSelf()
            }
        }

        return START_STICKY
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

        dbExecutor.execute {
            val lastPt = synchronized(currentPoints) { currentPoints.lastOrNull() }
            val timeDiff = location.time - (lastPt?.timestamp ?: 0L)
            
            val distDiff = lastPt?.let {
                val results = FloatArray(1)
                Location.distanceBetween(it.latitude, it.longitude, location.latitude, location.longitude, results)
                results[0]
            } ?: 0f

            if (lastPt == null || timeDiff >= 2000L || distDiff >= 2f) {
                val elevationVal = demSystem.getElevation(location.latitude, location.longitude)
                
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
                    latitude = location.latitude,
                    longitude = location.longitude,
                    elevation = elevationVal,
                    timestamp = location.time,
                    speed = if (location.hasSpeed()) location.speed else 0f,
                    accuracy = if (location.hasAccuracy()) location.accuracy else 0f
                )

                val db = AppDatabase.getDatabase(applicationContext)
                db.trackDao().insertTrackPoint(tp)

                synchronized(currentPoints) {
                    currentPoints.add(tp)
                    currentPointCount = currentPoints.size
                }

                Log.d(TAG, "Recorded point: Lat ${tp.latitude}, Lon ${tp.longitude}")

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
            TrackFileHelper.saveTrackToJson(track, pts, anchors)
            Log.d(TAG, "Saved track to disk: ${pts.size} points")
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

        val detailText = "时间：%s\n距离：%.2f km\n轨迹点：%d".format(
            formattedTime,
            formattedDist,
            currentPointCount
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(titleText)
            .setContentText("时间：%s | 距离：%.2f km | 轨迹点：%d".format(formattedTime, formattedDist, currentPointCount))
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
        unregisterLocationUpdates()
        dbExecutor.shutdown()
        Log.d(TAG, "Service destroyed")
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
