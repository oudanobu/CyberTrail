package com.cybertrail.app.gis

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.cybertrail.app.MapActivity
import com.cybertrail.app.db.AppDatabase
import com.cybertrail.app.db.Track
import com.cybertrail.app.db.TrackPoint
import java.io.File
import java.util.concurrent.Executors

class TrackService : Service(), LocationListener {

    companion object {
        private const val TAG = "TrackService"
        private const val NOTIFICATION_ID = 888
        private const val CHANNEL_ID = "track_recording_channel"

        const val ACTION_START = "com.cybertrail.app.action.START"
        const val ACTION_PAUSE = "com.cybertrail.app.action.PAUSE"
        const val ACTION_RESUME = "com.cybertrail.app.action.RESUME"
        const val ACTION_STOP = "com.cybertrail.app.action.STOP"
        
        const val EXTRA_TRACK_ID = "extra_track_id"
        
        // Broadcast action to notify MapActivity about new points
        const val ACTION_NEW_POINT = "com.cybertrail.app.action.NEW_POINT"
        const val EXTRA_POINT_LAT = "extra_point_lat"
        const val EXTRA_POINT_LON = "extra_point_lon"
        const val EXTRA_POINT_ALT = "extra_point_alt"
        const val EXTRA_POINT_TIME = "extra_point_time"
        const val EXTRA_POINT_SPEED = "extra_point_speed"
        const val EXTRA_POINT_ACCURACY = "extra_point_accuracy"
        
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
    }

    private lateinit var locationManager: LocationManager
    private val dbExecutor = Executors.newSingleThreadExecutor()
    private var lastSavedPoint: TrackPoint? = null
    private var lastDiskSaveTime = 0L
    private val diskSaveIntervalMs = 60000L

    override fun onCreate() {
        super.onCreate()
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
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
                    synchronized(currentPoints) {
                        currentPoints.clear()
                    }
                    lastSavedPoint = null
                    lastDiskSaveTime = System.currentTimeMillis()
                    
                    // Fetch existing points if any (e.g. on restore)
                    dbExecutor.execute {
                        val db = AppDatabase.getDatabase(applicationContext)
                        val points = db.trackDao().getTrackPoints(id)
                        synchronized(currentPoints) {
                            currentPoints.addAll(points)
                            if (points.isNotEmpty()) {
                                lastSavedPoint = points.last()
                            }
                        }
                    }

                    startForegroundServiceCompat()
                    registerLocationUpdates()
                }
            }
            ACTION_PAUSE -> {
                currentStatus = "PAUSED"
                updateNotification("轨迹记录已暂停")
                unregisterLocationUpdates()
                forceSaveToDisk()
            }
            ACTION_RESUME -> {
                currentStatus = "RECORDING"
                updateNotification("正在记录轨迹中...")
                registerLocationUpdates()
                forceSaveToDisk()
            }
            ACTION_STOP -> {
                currentStatus = "STOPPED"
                isRunning = false
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
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    5000L, // minTime = 5000ms
                    5f,    // minDistance = 5m
                    this
                )
                Log.d(TAG, "Registered GPS location updates with 5s, 5m filters")
            } else {
                locationManager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    5000L,
                    5f,
                    this
                )
                Log.d(TAG, "GPS Provider disabled, using Network location updates")
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "No permission to register location updates", e)
        }
    }

    private fun unregisterLocationUpdates() {
        locationManager.removeUpdates(this)
        Log.d(TAG, "Unregistered location updates")
    }

    override fun onLocationChanged(location: Location) {
        if (currentStatus != "RECORDING") return

        dbExecutor.execute {
            val db = AppDatabase.getDatabase(applicationContext)
            
            // Filter: minTime = 5000ms or minDistance = 5m
            val lastPt = lastSavedPoint
            val timeDiff = location.time - (lastPt?.timestamp ?: 0L)
            val distDiff = lastPt?.let {
                val results = FloatArray(1)
                Location.distanceBetween(it.latitude, it.longitude, location.latitude, location.longitude, results)
                results[0]
            } ?: Float.MAX_VALUE

            if (lastPt == null || timeDiff >= 5000L || distDiff >= 5f) {
                val tp = TrackPoint(
                    trackId = trackId,
                    latitude = location.latitude,
                    longitude = location.longitude,
                    elevation = if (location.hasAltitude()) location.altitude else null,
                    timestamp = location.time,
                    speed = if (location.hasSpeed()) location.speed else 0f,
                    accuracy = if (location.hasAccuracy()) location.accuracy else 0f
                )
                
                db.trackDao().insertTrackPoint(tp)
                
                synchronized(currentPoints) {
                    currentPoints.add(tp)
                }
                lastSavedPoint = tp

                Log.d(TAG, "Recorded point: Lat ${tp.latitude}, Lon ${tp.longitude}")

                // Send broadcast to update active MapActivity
                val intent = Intent(ACTION_NEW_POINT).apply {
                    putExtra(EXTRA_POINT_LAT, tp.latitude)
                    putExtra(EXTRA_POINT_LON, tp.longitude)
                    putExtra(EXTRA_POINT_ALT, tp.elevation)
                    putExtra(EXTRA_POINT_TIME, tp.timestamp)
                    putExtra(EXTRA_POINT_SPEED, tp.speed)
                    putExtra(EXTRA_POINT_ACCURACY, tp.accuracy)
                }
                sendBroadcast(intent)

                // Periodic save to .track file every 60 seconds
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
            track.status = currentStatus
            TrackFileHelper.saveTrackToJson(track, pts)
            Log.d(TAG, "Saved track to disk: ${pts.size} points")
        }
    }

    private fun forceSaveToDisk() {
        dbExecutor.execute {
            saveTrackFile()
        }
    }

    private fun startForegroundServiceCompat() {
        val notification = createNotification("正在记录轨迹中...")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotification(contentText: String): Notification {
        val notificationIntent = Intent(this, MapActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT else PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("CyberTrail 轨迹记录中")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(contentText: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, createNotification(contentText))
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
        locationManager.removeUpdates(this)
        dbExecutor.shutdown()
        Log.d(TAG, "Service destroyed")
    }

    override fun onBind(intent: Intent?): IBinder? = null
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    override fun onProviderEnabled(provider: String) {}
    override fun onProviderDisabled(provider: String) {}
}
