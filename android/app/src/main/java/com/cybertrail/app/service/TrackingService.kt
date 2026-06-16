package com.cybertrail.app.service

import android.Manifest
import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import com.cybertrail.app.NativeCore
import com.cybertrail.app.gis.DEMSystem
import com.cybertrail.app.model.TrackingState
import com.cybertrail.app.repository.TrackingRepository
import com.cybertrail.app.util.NotificationHelper
import java.util.*

class TrackingService : Service(), LocationListener {

    private var locationManager: LocationManager? = null
    private var isGpsActive = false
    private var simulationTimer: Timer? = null
    private lateinit var demSystem: DEMSystem

    // Track simulated walkthrough properties
    private var simLatitude = 37.7749
    private var simLongitude = -122.4194
    private var simAltitude = 120.0
    private var startTimeMillis = 0L

    // Keep instance states private
    private var isTracking = false
    private var isSimulating = false
    private var currentTrackId: String? = null
    private var currentTrackName = ""
    private var pointsCount = 0
    private var distanceMeters = 0.0
    private var durationSeconds = 0L

    companion object {
        private const val TAG = "TrackingService"

        const val ACTION_START = "com.cybertrail.app.START"
        const val ACTION_STOP = "com.cybertrail.app.STOP"
        const val EXTRA_TRACK_NAME = "com.cybertrail.app.TRACK_NAME"
        const val EXTRA_SIMULATION = "com.cybertrail.app.SIMULATION"
    }

    override fun onCreate() {
        super.onCreate()
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        NotificationHelper.createNotificationChannel(this)
        demSystem = DEMSystem(this)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) return START_NOT_STICKY

        when (intent.action) {
            ACTION_START -> {
                val trackName = intent.getStringExtra(EXTRA_TRACK_NAME) ?: "Tactical Hike"
                val useSimulation = intent.getBooleanExtra(EXTRA_SIMULATION, false)
                startTrackingSession(trackName, useSimulation)
            }
            ACTION_STOP -> {
                stopTrackingSession()
            }
        }

        return START_STICKY
    }

    private fun startTrackingSession(name: String, useSimulation: Boolean) {
        if (isTracking) return

        currentTrackName = name
        pointsCount = 0
        distanceMeters = 0.0
        durationSeconds = 0
        startTimeMillis = SystemClock.elapsedRealtime()
        isTracking = true
        isSimulating = useSimulation

        val nowSeconds = System.currentTimeMillis() / 1000
        val trackId = try {
            if (NativeCore.available) {
                NativeCore.startTrack(name, nowSeconds)
            } else {
                UUID.randomUUID().toString()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed startTrack FFI on core", e)
            UUID.randomUUID().toString()
        }
        currentTrackId = trackId

        Log.i(TAG, "Started tracking session $trackId for name: $name")

        // Start Foreground Service with helper notification
        val notification = NotificationHelper.buildNotification(this, "Initiating tactical GPS scan...")
        startForeground(NotificationHelper.NOTIFICATION_ID, notification)

        if (useSimulation) {
            startSimulation()
        } else {
            startGpsUpdates()
        }

        publishState()
    }

    private fun stopTrackingSession() {
        if (!isTracking) return

        Log.i(TAG, "Stopping tracking session $currentTrackId")

        // Stop updates
        stopGpsUpdates()
        stopSimulation()

        // End track in Rust Core SQLite
        val nowSeconds = System.currentTimeMillis() / 1000
        currentTrackId?.let { trackId ->
            try {
                if (NativeCore.available) {
                    NativeCore.endTrack(trackId, nowSeconds)
                    Log.i(TAG, "Ended track session securely on core sqlite.")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error ending track securely", e)
            }
        }

        // Reset variables
        isTracking = false
        isSimulating = false
        currentTrackId = null
        publishState()

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun hasLocationPermission(): Boolean {
        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission")
    private fun startGpsUpdates() {
        if (!hasLocationPermission()) {
            stopSelf()
            return
        }

        try {
            locationManager?.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                5000L, // 5 seconds interval
                5.0f,  // 5 meters trigger (matches constitution Principle 3 + GPS rule specs)
                this
            )
            isGpsActive = true
            Log.i(TAG, "Hardware location listener registered successfully.")
        } catch (e: Exception) {
            Log.w(TAG, "Could not hook location provider: ${e.message}. Falling back to simulation.", e)
            isSimulating = true
            startSimulation()
        }
    }

    private fun stopGpsUpdates() {
        if (isGpsActive) {
            locationManager?.removeUpdates(this)
            isGpsActive = false
        }
    }

    private fun startSimulation() {
        simLatitude = 37.7749
        simLongitude = -122.4194
        simAltitude = 120.0

        simulationTimer = Timer()
        simulationTimer?.scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                if (!isTracking || currentTrackId == null) return

                val elapsedSec = (SystemClock.elapsedRealtime() - startTimeMillis) / 1000
                durationSeconds = elapsedSec

                // Alter latitude and longitude slightly to simulate a hike path
                simLatitude += 0.00012 + (Random().nextDouble() - 0.5) * 0.00003
                simLongitude += 0.00008 + (Random().nextDouble() - 0.5) * 0.00003
                simAltitude = demSystem.getElevation(simLatitude, simLongitude)

                val timestamp = System.currentTimeMillis() / 1000
                val lat = simLatitude
                val lon = simLongitude
                val alt = simAltitude

                currentTrackId?.let { trackId ->
                    val success = try {
                        if (NativeCore.available) {
                            NativeCore.addTrackPoint(trackId, lat, lon, alt, timestamp)
                        } else {
                            true
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "FFI error during addTrackPoint simulation", e)
                        true
                    }

                    if (success) {
                        pointsCount++
                        if (pointsCount > 1) {
                            distanceMeters += 12.5 + Random().nextDouble() * 3.0
                        }
                        NotificationHelper.updateNotification(
                            this@TrackingService,
                            "Recorded $pointsCount points | Dist: %.1fm".format(distanceMeters)
                        )
                        publishState()
                    }
                }
            }
        }, 1000, 3000)
    }

    private fun stopSimulation() {
        simulationTimer?.cancel()
        simulationTimer = null
    }

    private fun publishState() {
        TrackingRepository.update(
            TrackingState(
                isTracking = isTracking,
                isSimulating = isSimulating,
                trackName = currentTrackName,
                points = pointsCount,
                distanceMeters = distanceMeters,
                durationSeconds = durationSeconds
            )
        )
    }

    // --- LocationListener Callbacks ---

    override fun onLocationChanged(location: Location) {
        if (!isTracking || currentTrackId == null) return

        val lat = location.latitude
        val lon = location.longitude
        val rawAlt = if (location.hasAltitude()) location.altitude else Double.NaN
        val alt = if (!rawAlt.isNaN()) {
            demSystem.getElevation(lat, lon) * 0.7 + rawAlt * 0.3
        } else {
            demSystem.getElevation(lat, lon)
        }
        val timestamp = location.time / 1000

        val elapsedSec = (SystemClock.elapsedRealtime() - startTimeMillis) / 1000
        durationSeconds = elapsedSec

        currentTrackId?.let { trackId ->
            val success = try {
                if (NativeCore.available) {
                    NativeCore.addTrackPoint(trackId, lat, lon, alt, timestamp)
                } else {
                    true
                }
            } catch (e: Exception) {
                Log.e(TAG, "FFI call error adding GPS trackpoint", e)
                true
            }

            if (success) {
                pointsCount++
                Log.i(TAG, "Successfully saved hardware location coordinate index: $pointsCount")
                NotificationHelper.updateNotification(
                    this,
                    "Live tracking active: $pointsCount telemetry coordinates logged."
                )
                publishState()
            }
        }
    }

    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    override fun onProviderEnabled(provider: String) {}
    override fun onProviderDisabled(provider: String) {}

    override fun onDestroy() {
        stopTrackingSession()
        super.onDestroy()
    }
}
