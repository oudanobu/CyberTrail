package com.cybertrail.app

import android.util.Log

object NativeCore {
    private const val TAG = "NativeCore"

    var available = false
        private set

    init {
        try {
            System.loadLibrary("ffi")
            available = true
            Log.i(TAG, "Native library 'libffi.so' successfully loaded.")
        } catch (e: UnsatisfiedLinkError) {
            available = false
            Log.w(TAG, "Warning: libffi.so could not be loaded; falling back to mock layers.", e)
        } catch (e: Exception) {
            available = false
            Log.w(TAG, "Exception loading library", e)
        }
    }

    external fun getVersion(): String
    external fun healthCheck(): Boolean
    
    external fun initDatabase(dbPath: String): Boolean
    external fun startTrack(name: String, startedAt: Long): String?
    external fun addTrackPoint(trackId: String, latitude: Double, longitude: Double, altitude: Double, timestamp: Long): Boolean
    external fun endTrack(trackId: String, endedAt: Long): Boolean
    external fun getAllTracksJson(): String
    external fun getTrackPointsJson(trackId: String): String
    external fun deleteTrack(trackId: String): Boolean
}
