package com.cybertrail.app.db

import androidx.room.*

@Dao
interface WaypointDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertWaypoint(waypoint: WaypointEntity)

    @Update
    fun updateWaypoint(waypoint: WaypointEntity)

    @Query("SELECT * FROM waypoints WHERE id = :id LIMIT 1")
    fun getWaypointById(id: String): WaypointEntity?

    @Query("SELECT * FROM waypoints ORDER BY createTime DESC")
    fun getAllWaypoints(): List<WaypointEntity>

    @Query("DELETE FROM waypoints WHERE id = :id")
    fun deleteWaypointById(id: String)
}
