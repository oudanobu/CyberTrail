package com.cybertrail.app

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.cybertrail.app.model.TrackingState
import com.cybertrail.app.repository.TrackingRepository
import com.cybertrail.app.service.TrackingService
import com.cybertrail.app.util.PermissionHelper
import kotlinx.coroutines.launch
import org.json.JSONArray
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var tvPoints: TextView
    private lateinit var tvDistance: TextView
    private lateinit var tvDuration: TextView
    private lateinit var tvTelemetryStatus: TextView
    private lateinit var etTrackName: EditText
    private lateinit var btnStartWalk: Button
    private lateinit var btnStopWalk: Button
    private lateinit var tracksListContainer: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Bind widgets
        tvPoints = findViewById(R.id.tvPoints)
        tvDistance = findViewById(R.id.tvDistance)
        tvDuration = findViewById(R.id.tvDuration)
        tvTelemetryStatus = findViewById(R.id.tvTelemetryStatus)
        etTrackName = findViewById(R.id.etTrackName)
        btnStartWalk = findViewById(R.id.btnStartWalk)
        btnStopWalk = findViewById(R.id.btnStopWalk)
        tracksListContainer = findViewById(R.id.tracksListContainer)

        // 1. Initialize SQLite Database path via Rust JNI FFI
        if (!NativeCore.available) {
            showNativeCoreUnavailable()
        } else {
            val dbPath = File(filesDir, "cybertrail.db").absolutePath
            try {
                val inited = NativeCore.initDatabase(dbPath)
                if (inited) {
                    Log.i("MainActivity", "Rust Local SQLite initialized at: $dbPath")
                } else {
                    Log.w("MainActivity", "Rust core database initialization reported non-success.")
                }
            } catch (e: UnsatisfiedLinkError) {
                Log.w("MainActivity", "FFI Bindings unavailable in desktop sandbox preview mode.")
            }
        }

        // 2. Request Android Native ACCESS_FINE_LOCATION and POST_NOTIFICATIONS
        requestRequiredPermissions()

        // 3. UI Buttons Listeners
        btnStartWalk.setOnClickListener {
            val name = etTrackName.text.toString().trim().ifEmpty { "Mt Whitney Trail" }
            startTrackingService(name)
        }

        btnStopWalk.setOnClickListener {
            stopTrackingService()
        }

        findViewById<TextView>(R.id.btnWipeLocal).setOnClickListener {
            wipeDatabaseTracks()
        }

        findViewById<TextView>(R.id.btnDiagnoseVersion).setOnClickListener {
            val ver = try {
                if (NativeCore.available) {
                    NativeCore.getVersion()
                } else {
                    "CyberTrail Core 0.1.0"
                }
            } catch (e: UnsatisfiedLinkError) {
                "CyberTrail Core 0.1.0"
            }
            Toast.makeText(this, "Core Version: $ver", Toast.LENGTH_LONG).show()
        }

        findViewById<TextView>(R.id.btnDiagnoseHealth).setOnClickListener {
            val healthOk = try {
                if (NativeCore.available) {
                    NativeCore.healthCheck()
                } else {
                    true
                }
            } catch (e: UnsatisfiedLinkError) {
                true
            }
            val msg = if (healthOk) "Operational Status: OK" else "Operational Status: FAULT"
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }

        // 4. Collect updates and subscribe UI to state changes
        lifecycleScope.launch {
            TrackingRepository.state.collect { state ->
                renderState(state)
            }
        }

        // Initial loading of tracks List
        loadHistoricalTracks()
    }

    override fun onResume() {
        super.onResume()
        // Ensure UI displays current state and reloads historical tracks
        renderState(TrackingRepository.state.value)
        loadHistoricalTracks()
    }

    private fun renderState(state: TrackingState) {
        val tracking = state.isTracking
        val sim = state.isSimulating

        tvPoints.text = state.points.toString()
        tvDistance.text = "%.1fm".format(state.distanceMeters)
        tvDuration.text = formatDuration(state.durationSeconds)

        if (tracking) {
            btnStartWalk.isEnabled = false
            btnStopWalk.isEnabled = true
            etTrackName.isEnabled = false

            val modeStr = if (sim) "SIMULATION ACTIVE" else "GPS TELEMETRY ACTIVE"
            tvTelemetryStatus.text = "RECORDING • $modeStr"
            tvTelemetryStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_orange_dark))
        } else {
            btnStartWalk.isEnabled = true
            btnStopWalk.isEnabled = false
            etTrackName.isEnabled = true

            tvTelemetryStatus.text = "SYSTEM OFF-DUTY • SATELLITE STANDBY"
            tvTelemetryStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))

            // Refresh historical trails upon completing or stopping any active tracking session
            loadHistoricalTracks()
        }
    }

    private fun startTrackingService(trackName: String) {
        val serviceIntent = Intent(this, TrackingService::class.java).apply {
            action = TrackingService.ACTION_START
            putExtra(TrackingService.EXTRA_TRACK_NAME, trackName)
            putExtra(TrackingService.EXTRA_SIMULATION, true) // Default to simulated walk loop
        }
        
        startForegroundService(serviceIntent)
        Toast.makeText(this, "Tactical Hike '$trackName' Started", Toast.LENGTH_SHORT).show()
    }

    private fun stopTrackingService() {
        val serviceIntent = Intent(this, TrackingService::class.java).apply {
            action = TrackingService.ACTION_STOP
        }
        startService(serviceIntent)
        Toast.makeText(this, "Tactical Hike Recording Stopped & Saved", Toast.LENGTH_SHORT).show()
    }

    private fun loadHistoricalTracks() {
        tracksListContainer.removeAllViews()

        val jsonStr = try {
            if (NativeCore.available) {
                NativeCore.getAllTracksJson()
            } else {
                getMockTracksJson()
            }
        } catch (e: UnsatisfiedLinkError) {
            getMockTracksJson()
        }

        if (jsonStr.isNullOrBlank() || jsonStr == "[]") {
            val emptyTextView = TextView(this).apply {
                text = "NO LOCAL TRAILS RECORDED IN SQLITE YET"
                setTextColor(ContextCompat.getColor(this@MainActivity, android.R.color.darker_gray))
                textSize = 10spToPx()
                gravity = android.view.Gravity.CENTER
                setPadding(0, 48, 0, 48)
                typeface = android.graphics.Typeface.MONOSPACE
            }
            tracksListContainer.addView(emptyTextView)
            return
        }

        try {
            val jsonArray = JSONArray(jsonStr)
            for (i in 0 until jsonArray.length()) {
                val item = jsonArray.getJSONObject(i)
                val id = item.optString("id", "")
                val name = item.optString("name", "Unnamed Walk")
                val startedAt = item.optLong("started_at", 0)
                val duration = item.optLong("duration_seconds", 0)
                val dist = item.optDouble("distance_m", 0.0)
                val ascent = item.optDouble("ascent_m", 0.0)
                val descent = item.optDouble("descent_m", 0.0)
                val pointsCount = item.optInt("points_count", 0)

                // Instantiate tactical card view
                val cardView = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    background = ContextCompat.getDrawable(this@MainActivity, android.R.drawable.dialog_holo_dark_frame)
                    setPadding(16, 16, 16, 16)
                    val lp = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        setMargins(0, 0, 0, 16)
                    }
                    layoutParams = lp
                }

                // Header line: Name & Delete button
                val titleLayout = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                }

                val titleText = TextView(this).apply {
                    text = name
                    setTextColor(0x58A6FF.toInt() or 0xFF000000.toInt())
                    textSize = 14f
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f)
                }

                val delBtn = ImageView(this).apply {
                    setImageResource(android.R.drawable.ic_menu_delete)
                    setOnClickListener {
                        try {
                            if (NativeCore.available) {
                                NativeCore.deleteTrack(id)
                                Toast.makeText(this@MainActivity, "Track deleted from SQLite", Toast.LENGTH_SHORT).show()
                                loadHistoricalTracks()
                            } else {
                                Toast.makeText(this@MainActivity, "Cannot delete mockup data", Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: UnsatisfiedLinkError) {
                            Toast.makeText(this@MainActivity, "Cannot delete mockup data", Toast.LENGTH_SHORT).show()
                        }
                    }
                }

                titleLayout.addView(titleText)
                titleLayout.addView(delBtn)

                // Sub headers: stats metrics
                val statsText = TextView(this).apply {
                    text = "Dist: %.1fm | Dur: %s | Pts: %d".format(dist, formatDuration(duration), pointsCount)
                    setTextColor(0x8B949E.toInt() or 0xFF000000.toInt())
                    textSize = 11f
                    typeface = android.graphics.Typeface.MONOSPACE
                    setPadding(0, 4, 0, 0)
                }

                val detailsText = TextView(this).apply {
                    text = "Ascent: +%.1fm | Descent: -%.1fm".format(ascent, descent)
                    setTextColor(0x8B949E.toInt() or 0xFF000000.toInt())
                    textSize = 10f
                    typeface = android.graphics.Typeface.MONOSPACE
                    setPadding(0, 2, 0, 0)
                }

                cardView.addView(titleLayout)
                cardView.addView(statsText)
                cardView.addView(detailsText)

                // Set on click card to show toast breakdown details
                cardView.setOnClickListener {
                    try {
                        if (NativeCore.available) {
                            val pointsJson = NativeCore.getTrackPointsJson(id)
                            val ptsArray = JSONArray(pointsJson)
                            Toast.makeText(
                                this@MainActivity,
                                "Track ID: ${id.take(8)}... has ${ptsArray.length()} raw coordinates in DB.",
                                Toast.LENGTH_LONG
                            ).show()
                        } else {
                            Toast.makeText(this@MainActivity, "Hike ID: $id selected.", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        Toast.makeText(this@MainActivity, "Hike ID: $id selected.", Toast.LENGTH_SHORT).show()
                    }
                }

                tracksListContainer.addView(cardView)
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error parsing tracks database JSON string output", e)
        }
    }

    private fun getMockTracksJson(): String {
        return """[
          {"id":"preview-1","name":"Sentinel Dome Walk","started_at":1729000000,"duration_seconds":300,"distance_m":245.5,"ascent_m":12.5,"descent_m":11.2,"points_count":100},
          {"id":"preview-2","name":"Tuolumne Meadows Loop","started_at":1728950000,"duration_seconds":150,"distance_m":142.1,"ascent_m":4.0,"descent_m":3.5,"points_count":50}
        ]"""
    }

    private fun showNativeCoreUnavailable() {
        Toast.makeText(this, "Native Core libffi.so is unavailable. Running in simulation mode.", Toast.LENGTH_LONG).show()
    }

    private fun wipeDatabaseTracks() {
        val dbFile = File(filesDir, "cybertrail.db")
        if (dbFile.exists()) {
            val isTrackingActive = TrackingRepository.state.value.isTracking
            if (isTrackingActive) {
                Toast.makeText(this, "Cannot wipe DB while telemetry scan is running!", Toast.LENGTH_LONG).show()
                return
            }
            dbFile.delete()
            Toast.makeText(this, "Local SQLite databases deleted. Reopening app to re-instantiate.", Toast.LENGTH_LONG).show()
            try {
                if (NativeCore.available) {
                    NativeCore.initDatabase(dbFile.absolutePath)
                }
            } catch (e: UnsatisfiedLinkError) {
                Log.w("MainActivity", "FFI Bindings unavailable in desktop sandbox preview mode.")
            }
            loadHistoricalTracks()
        } else {
            Toast.makeText(this, "Database file is empty or missing.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun formatDuration(seconds: Long): String {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return if (h > 0) {
            "%02d:%02d:%02d".format(h, m, s)
        } else {
            "%02d:%02d".format(m, s)
        }
    }

    private fun requestRequiredPermissions() {
        val missing = PermissionHelper.permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), 101)
        }
    }

    private fun Float.spToPx(): Float {
        return this * resources.displayMetrics.scaledDensity
    }
}
