package com.cybertrail.app.gis

import android.content.Context
import android.content.SharedPreferences
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.util.Log
import com.cybertrail.app.gis.ins.InsPdrManager
import com.cybertrail.app.gis.ins.NavState

/**
 * Location Source Enum ordered by priority:
 * GPS > NETWORK > CELL > LAST_FIX > INS > MANUAL
 */
enum class LocationSource(val priority: Int, val displayName: String) {
    GPS(1, "GPS"),
    NETWORK(2, "Network"),
    CELL(3, "Cell"),
    LAST_FIX(4, "Last Fix"),
    INS(5, "INS / PDR"),
    MANUAL(6, "Manual")
}

data class CyberLocation(
    val latitude: Double,
    val longitude: Double,
    val altitude: Double? = null,
    val accuracy: Float = 0f,
    val source: LocationSource,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * PositionManager orchestrates location fixes across all Android location providers,
 * Last Fix caching in SharedPreferences, INS/PDR fallback, and Manual position overrides.
 */
class PositionManager(
    private val context: Context,
    private val listener: PositionListener? = null,
    private val insPdrManager: InsPdrManager? = null
) : LocationListener {

    interface PositionListener {
        fun onPositionUpdated(location: CyberLocation)
        fun onLocationSourceChanged(newSource: LocationSource, oldSource: LocationSource)
    }

    companion object {
        private const val TAG = "PositionManager"
        private const val PREFS_NAME = "cybertrail_last_fix"
        private const val KEY_LAT = "last_lat"
        private const val KEY_LON = "last_lon"
        private const val KEY_ALT = "last_alt"
        private const val KEY_TIME = "last_time"
        private const val GPS_TIMEOUT_MS = 6000L
    }

    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    @Volatile
    var currentLocation: CyberLocation? = null
        private set

    @Volatile
    var currentSource: LocationSource = LocationSource.LAST_FIX
        private set

    private var isStarted = false

    init {
        // ① App startup: Immediately load Last Fix from storage
        loadLastFixFromCache()
    }

    /**
     * Immediately returns cached Last Fix or a safe default if no fix exists
     */
    fun getLastFix(): CyberLocation {
        val cached = currentLocation
        if (cached != null) return cached

        val savedLat = prefs.getFloat(KEY_LAT, 40.12345f).toDouble()
        val savedLon = prefs.getFloat(KEY_LON, 124.38910f).toDouble()
        val savedAlt = prefs.getFloat(KEY_ALT, 0f).toDouble()
        val savedTime = prefs.getLong(KEY_TIME, System.currentTimeMillis())

        return CyberLocation(
            latitude = savedLat,
            longitude = savedLon,
            altitude = savedAlt,
            accuracy = 20f,
            source = LocationSource.LAST_FIX,
            timestamp = savedTime
        )
    }

    fun startLocationUpdates() {
        if (isStarted) return
        isStarted = true

        try {
            // Register Android GPS Provider
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    1000L,
                    1f,
                    this
                )
            }

            // ⑤ Register Android Network Provider
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    2000L,
                    5f,
                    this
                )
            }

            // Fetch last known location from system as fallback
            val lastGps = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            val lastNetwork = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)

            val bestSystemLoc = when {
                lastGps != null && (lastNetwork == null || lastGps.time >= lastNetwork.time) -> lastGps
                lastNetwork != null -> lastNetwork
                else -> null
            }

            if (bestSystemLoc != null) {
                val src = if (bestSystemLoc.provider == LocationManager.GPS_PROVIDER) LocationSource.GPS else LocationSource.NETWORK
                updatePosition(
                    CyberLocation(
                        latitude = bestSystemLoc.latitude,
                        longitude = bestSystemLoc.longitude,
                        altitude = bestSystemLoc.altitude,
                        accuracy = bestSystemLoc.accuracy,
                        source = src,
                        timestamp = bestSystemLoc.time
                    )
                )
            } else {
                // Instantly emit Last Fix so map enters immediately without waiting for GPS
                val lastFixLoc = getLastFix()
                updatePosition(lastFixLoc)
            }

        } catch (e: SecurityException) {
            Log.e(TAG, "Location permission missing", e)
            val lastFixLoc = getLastFix()
            updatePosition(lastFixLoc)
        }
    }

    fun stopLocationUpdates() {
        if (!isStarted) return
        isStarted = false
        try {
            locationManager.removeUpdates(this)
        } catch (e: SecurityException) {
            Log.e(TAG, "Error stopping location updates", e)
        }
    }

    /**
     * ④ Manual Position: User taps waypoint, taps map, or inputs Lat/Lon
     * INS dead reckoning continues from this new coordinate.
     */
    fun setManualPosition(latitude: Double, longitude: Double, altitude: Double? = null) {
        val manualLoc = CyberLocation(
            latitude = latitude,
            longitude = longitude,
            altitude = altitude,
            accuracy = 1f,
            source = LocationSource.MANUAL,
            timestamp = System.currentTimeMillis()
        )
        updatePosition(manualLoc)
        Log.i(TAG, "Manual location set: Lat=$latitude, Lon=$longitude")
    }

    /**
     * Process PDR step updates from InsPdrManager when GPS is unavailable
     */
    fun onInsPdrPositionUpdate(lat: Double, lon: Double, alt: Double?) {
        // Only accept INS if current source priority is lower than GPS/Network, or in INS_ONLY mode
        if (currentSource == LocationSource.INS || currentSource == LocationSource.LAST_FIX || currentSource == LocationSource.MANUAL) {
            val insLoc = CyberLocation(
                latitude = lat,
                longitude = lon,
                altitude = alt,
                accuracy = 10f,
                source = LocationSource.INS,
                timestamp = System.currentTimeMillis()
            )
            updatePosition(insLoc)
        }
    }

    override fun onLocationChanged(location: Location) {
        val src = when (location.provider) {
            LocationManager.GPS_PROVIDER -> LocationSource.GPS
            LocationManager.NETWORK_PROVIDER -> LocationSource.NETWORK
            "cell" -> LocationSource.CELL
            else -> LocationSource.GPS
        }

        val cyberLoc = CyberLocation(
            latitude = location.latitude,
            longitude = location.longitude,
            altitude = location.altitude,
            accuracy = location.accuracy,
            source = src,
            timestamp = location.time
        )

        // Evaluate priority state machine
        if (shouldAcceptNewLocation(cyberLoc)) {
            updatePosition(cyberLoc)
            insPdrManager?.onGpsLocation(location)
        }
    }

    private fun shouldAcceptNewLocation(newLoc: CyberLocation): Boolean {
        val current = currentLocation ?: return true

        // Higher priority source always takes precedence
        if (newLoc.source.priority < current.source.priority) {
            return true
        }

        // Same source priority: accept if newer
        if (newLoc.source.priority == current.source.priority) {
            return newLoc.timestamp >= current.timestamp
        }

        // Lower priority source: accept if current location is stale (> 6 seconds old)
        val timeSinceCurrent = System.currentTimeMillis() - current.timestamp
        return timeSinceCurrent > GPS_TIMEOUT_MS
    }

    private fun updatePosition(newLoc: CyberLocation) {
        val oldSource = currentSource
        currentLocation = newLoc
        currentSource = newLoc.source

        // Save to persistent storage cache
        saveLastFixToCache(newLoc)

        if (oldSource != newLoc.source) {
            listener?.onLocationSourceChanged(newLoc.source, oldSource)
        }

        listener?.onPositionUpdated(newLoc)
    }

    private fun loadLastFixFromCache() {
        if (prefs.contains(KEY_LAT) && prefs.contains(KEY_LON)) {
            val lat = prefs.getFloat(KEY_LAT, 0f).toDouble()
            val lon = prefs.getFloat(KEY_LON, 0f).toDouble()
            val alt = prefs.getFloat(KEY_ALT, 0f).toDouble()
            val time = prefs.getLong(KEY_TIME, System.currentTimeMillis())

            currentLocation = CyberLocation(
                latitude = lat,
                longitude = lon,
                altitude = alt,
                accuracy = 15f,
                source = LocationSource.LAST_FIX,
                timestamp = time
            )
            currentSource = LocationSource.LAST_FIX
        }
    }

    private fun saveLastFixToCache(loc: CyberLocation) {
        prefs.edit()
            .putFloat(KEY_LAT, loc.latitude.toFloat())
            .putFloat(KEY_LON, loc.longitude.toFloat())
            .putFloat(KEY_ALT, (loc.altitude ?: 0.0).toFloat())
            .putLong(KEY_TIME, loc.timestamp)
            .apply()
    }

    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    override fun onProviderEnabled(provider: String) {}
    override fun onProviderDisabled(provider: String) {}
}
