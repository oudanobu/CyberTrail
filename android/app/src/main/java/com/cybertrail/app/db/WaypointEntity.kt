package com.cybertrail.app.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "waypoints")
data class WaypointEntity(
    @PrimaryKey val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val elevation: Double?,
    val description: String?,
    val iconType: String, // "NORMAL", "CAMP", "WATER", "SUMMIT", "DANGER", "PHOTO", "PARKING"
    var favorite: Boolean = false,
    val createTime: Long,
    val photoRef: String? = null
)
