package com.cybertrail.app.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "routes")
data class RouteEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String?,
    val createTime: Long,
    var favorite: Boolean = false,
    val distanceMeters: Double,
    val estimatedTimeMinutes: Int,
    val waypointIds: String // Comma-separated waypoint IDs
) {
    fun getWaypointIdList(): List<String> {
        if (waypointIds.trim().isEmpty()) return emptyList()
        return waypointIds.split(",")
    }
}
