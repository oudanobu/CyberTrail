package com.cybertrail.app.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

@Dao
interface TrackDao {
    @Insert
    fun insertTrack(track: Track): Long

    @Update
    fun updateTrack(track: Track)

    @Query("SELECT * FROM tracks WHERE id = :trackId LIMIT 1")
    fun getTrackById(trackId: Long): Track?

    @Query("SELECT * FROM tracks WHERE status IN ('RECORDING', 'PAUSED') ORDER BY id DESC LIMIT 1")
    fun getActiveTrack(): Track?

    @Insert
    fun insertTrackPoint(point: TrackPoint): Long

    @Insert
    fun insertTrackPoints(points: List<TrackPoint>)

    @Query("SELECT * FROM track_points WHERE trackId = :trackId ORDER BY timestamp ASC")
    fun getTrackPoints(trackId: Long): List<TrackPoint>

    @Query("SELECT * FROM tracks ORDER BY id DESC")
    fun getAllTracks(): List<Track>
}
