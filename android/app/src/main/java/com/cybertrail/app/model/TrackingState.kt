package com.cybertrail.app.model

data class TrackingState(
    val isTracking: Boolean = false,
    val isSimulating: Boolean = false,
    val trackName: String = "",
    val points: Int = 0,
    val distanceMeters: Double = 0.0,
    val durationSeconds: Long = 0
)
