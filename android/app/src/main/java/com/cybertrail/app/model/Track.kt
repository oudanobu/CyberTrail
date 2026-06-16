package com.cybertrail.app.model

data class Track(
    val id: String,
    val name: String,
    val startedAt: Long,
    val durationSeconds: Long,
    val distanceMeters: Double,
    val ascentMeters: Double,
    val descentMeters: Double,
    val pointsCount: Int
)
