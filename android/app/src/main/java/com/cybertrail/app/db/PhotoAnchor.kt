package com.cybertrail.app.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "photo_anchors")
data class PhotoAnchor(
    @PrimaryKey val id: String,
    val latitude: Double,
    val longitude: Double,
    val elevation: Double?,
    val timestamp: Long,
    val imagePath: String,
    val thumbnailPath: String?,
    val trackId: Long?,
    val note: String
)

