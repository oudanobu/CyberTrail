package com.cybertrail.app.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tracks")
data class Track(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startTime: Long,
    var endTime: Long? = null,
    var status: String = "RECORDING", // "RECORDING", "PAUSED", "STOPPED"
    var name: String? = null,
    var favorite: Boolean = false,
    var stepCount: Int = 0,
    var averageCadence: Float = 0f,
    var averageStepLength: Float = 0f
)
