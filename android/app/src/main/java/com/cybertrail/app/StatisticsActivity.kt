package com.cybertrail.app

import android.location.Location
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.cybertrail.app.db.AppDatabase
import com.cybertrail.app.db.TrackDao
import java.util.Locale
import java.util.concurrent.Executors

class StatisticsActivity : AppCompatActivity() {

    private val dbExecutor = Executors.newSingleThreadExecutor()
    private lateinit var trackDao: TrackDao

    // TextViews from layout
    private lateinit var tvTotalDist: TextView
    private lateinit var tvTotalAscent: TextView
    private lateinit var tvTotalTracks: TextView
    private lateinit var tvTotalPhotos: TextView
    private lateinit var tvLongestTrack: TextView
    private lateinit var tvHighestElevation: TextView
    private lateinit var tvAvgTripDist: TextView
    private lateinit var btnBack: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_statistics)

        trackDao = AppDatabase.getDatabase(this).trackDao()

        // Bind Views
        btnBack = findViewById(R.id.btn_stats_back)
        tvTotalDist = findViewById(R.id.tv_stats_total_dist)
        tvTotalAscent = findViewById(R.id.tv_stats_total_ascent)
        tvTotalTracks = findViewById(R.id.tv_stats_total_tracks)
        tvTotalPhotos = findViewById(R.id.tv_stats_total_photos)
        tvLongestTrack = findViewById(R.id.tv_stats_longest_track)
        tvHighestElevation = findViewById(R.id.tv_stats_highest_elevation)
        tvAvgTripDist = findViewById(R.id.tv_stats_avg_trip_dist)

        btnBack.setOnClickListener { finish() }

        loadStatistics()
    }

    private fun loadStatistics() {
        dbExecutor.execute {
            try {
                val tracks = trackDao.getAllTracks()
                val totalTracksCount = tracks.size

                var totalDistanceKm = 0.0
                var totalAscentM = 0.0
                var longestDistanceKm = 0.0
                var overallHighestElevationM = 0.0
                var totalPhotosCount = 0

                for (track in tracks) {
                    val points = trackDao.getTrackPoints(track.id)
                    val photos = trackDao.getPhotoAnchorsForTrack(track.id)
                    totalPhotosCount += photos.size

                    // 1. Calculate Track Distance
                    var trackDistanceMeters = 0.0
                    for (i in 1 until points.size) {
                        val results = FloatArray(1)
                        Location.distanceBetween(
                            points[i - 1].latitude, points[i - 1].longitude,
                            points[i].latitude, points[i].longitude,
                            results
                        )
                        trackDistanceMeters += results[0]
                    }
                    val trackDistKm = trackDistanceMeters / 1000.0
                    totalDistanceKm += trackDistKm

                    if (trackDistKm > longestDistanceKm) {
                        longestDistanceKm = trackDistKm
                    }

                    // 2. Calculate Cumulative Ascent
                    var trackAscent = 0.0
                    for (i in 1 until points.size) {
                        val prevEle = points[i - 1].elevation
                        val currEle = points[i].elevation
                        if (prevEle != null && currEle != null) {
                            val diff = currEle - prevEle
                            if (diff > 0.0) {
                                trackAscent += diff
                            }
                        }
                    }
                    totalAscentM += trackAscent

                    // 3. Track Highest Elevation
                    val trackMaxEle = points.mapNotNull { it.elevation }.maxOrNull() ?: 0.0
                    if (trackMaxEle > overallHighestElevationM) {
                        overallHighestElevationM = trackMaxEle
                    }
                }

                val avgTripDistKm = if (totalTracksCount > 0) totalDistanceKm / totalTracksCount else 0.0

                runOnUiThread {
                    tvTotalDist.text = String.format(Locale.US, "%.2f km", totalDistanceKm)
                    tvTotalAscent.text = String.format(Locale.US, "%.1f m", totalAscentM)
                    tvTotalTracks.text = "$totalTracksCount 条"
                    tvTotalPhotos.text = "$totalPhotosCount 张"
                    tvLongestTrack.text = String.format(Locale.US, "%.2f km", longestDistanceKm)
                    tvHighestElevation.text = String.format(Locale.US, "%.1f m", overallHighestElevationM)
                    tvAvgTripDist.text = String.format(Locale.US, "%.2f km", avgTripDistKm)
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this@StatisticsActivity, "统计加载失败", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
