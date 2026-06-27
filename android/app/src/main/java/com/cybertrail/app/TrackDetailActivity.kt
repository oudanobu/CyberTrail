package com.cybertrail.app

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.location.Location
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.cybertrail.app.db.AppDatabase
import com.cybertrail.app.db.PhotoAnchor
import com.cybertrail.app.db.Track
import com.cybertrail.app.db.TrackDao
import com.cybertrail.app.db.TrackPoint
import com.cybertrail.app.gis.TrackFileHelper
import com.cybertrail.app.gis.TrackProfileChartView
import com.cybertrail.app.offline.LocalTileServer
import com.mapbox.geojson.Feature
import com.mapbox.geojson.FeatureCollection
import com.mapbox.geojson.LineString
import com.mapbox.geojson.Point
import com.mapbox.mapboxsdk.Mapbox
import com.mapbox.mapboxsdk.annotations.Marker
import com.mapbox.mapboxsdk.annotations.MarkerOptions
import com.mapbox.mapboxsdk.camera.CameraUpdateFactory
import com.mapbox.mapboxsdk.geometry.LatLng
import com.mapbox.mapboxsdk.geometry.LatLngBounds
import com.mapbox.mapboxsdk.maps.MapView
import com.mapbox.mapboxsdk.maps.MapboxMap
import com.mapbox.mapboxsdk.maps.Style
import com.mapbox.mapboxsdk.style.layers.LineLayer
import com.mapbox.mapboxsdk.style.layers.Property
import com.mapbox.mapboxsdk.style.layers.PropertyFactory
import com.mapbox.mapboxsdk.style.sources.GeoJsonSource
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executors

class TrackDetailActivity : AppCompatActivity() {

    private val TAG = "TrackDetailActivity"

    // Core views
    private lateinit var mapView: MapView
    private lateinit var chartView: TrackProfileChartView
    private lateinit var tvTitle: TextView
    private lateinit var btnBack: TextView
    private lateinit var btnExport: TextView
    private lateinit var btnLocateTrack: TextView

    // Replay panel views
    private lateinit var tvReplayStatus: TextView
    private lateinit var tvReplayProgress: TextView
    private lateinit var btnReplayStart: TextView
    private lateinit var btnReplayPause: TextView
    private lateinit var btnReplayStop: TextView
    private lateinit var btnReplayRewind: TextView
    private lateinit var sbReplayTimeline: SeekBar

    // Speed buttons
    private lateinit var btnSpeed05: TextView
    private lateinit var btnSpeed1: TextView
    private lateinit var btnSpeed2: TextView
    private lateinit var btnSpeed5: TextView
    private lateinit var btnSpeed10: TextView

    // Chart toggles
    private lateinit var btnChartEle: TextView
    private lateinit var btnChartSpeed: TextView
    private lateinit var btnChartDist: TextView

    // Photo popup
    private lateinit var cardPhotoPopup: View
    private lateinit var imgPhotoPopup: ImageView
    private lateinit var tvPhotoPopupDesc: TextView

    // Database & Data
    private lateinit var trackDao: TrackDao
    private val dbExecutor = Executors.newSingleThreadExecutor()
    private var trackId: Long = 0
    private var currentTrack: Track? = null
    private val trackPointsList = mutableListOf<TrackPoint>()
    private val photoAnchorsList = mutableListOf<PhotoAnchor>()
    private val cumulativeDistances = mutableListOf<Float>()

    // Mapbox states
    private var mapboxMap: MapboxMap? = null
    private var localTileServer: LocalTileServer? = null
    private var isMapLoaded = false
    private var replayMarker: Marker? = null
    private val photoMarkersList = mutableListOf<Marker>()

    // Replay controller state
    private val replayHandler = Handler(Looper.getMainLooper())
    private var isReplaying = false
    private var replayIndex = 0
    private var speedMultiplier = 1.0f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize Mapbox before layout inflation
        try {
            Mapbox.getInstance(this)
            Mapbox.setConnected(true)
        } catch (e: Exception) {
            Log.e(TAG, "Mapbox init error", e)
        }

        setContentView(R.layout.activity_track_detail)

        trackId = intent.getLongExtra("TRACK_ID", 0)
        if (trackId == 0L) {
            Toast.makeText(this, "无效的轨迹ID", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        trackDao = AppDatabase.getDatabase(this).trackDao()

        initViews(savedInstanceState)
        loadTrackData()
    }

    private fun initViews(savedInstanceState: Bundle?) {
        mapView = findViewById(R.id.detail_map_view)
        mapView.onCreate(savedInstanceState)

        chartView = findViewById(R.id.profile_chart_view)
        tvTitle = findViewById(R.id.tv_detail_title)
        btnBack = findViewById(R.id.btn_detail_back)
        btnExport = findViewById(R.id.btn_detail_export)
        btnLocateTrack = findViewById(R.id.btn_locate_track)

        tvReplayStatus = findViewById(R.id.tv_detail_replay_status)
        tvReplayProgress = findViewById(R.id.tv_detail_replay_progress)
        btnReplayStart = findViewById(R.id.btn_replay_start)
        btnReplayPause = findViewById(R.id.btn_replay_pause)
        btnReplayStop = findViewById(R.id.btn_replay_stop)
        btnReplayRewind = findViewById(R.id.btn_replay_rewind)
        sbReplayTimeline = findViewById(R.id.sb_replay_timeline)

        btnSpeed05 = findViewById(R.id.btn_speed_0_5)
        btnSpeed1 = findViewById(R.id.btn_speed_1)
        btnSpeed2 = findViewById(R.id.btn_speed_2)
        btnSpeed5 = findViewById(R.id.btn_speed_5)
        btnSpeed10 = findViewById(R.id.btn_speed_10)

        btnChartEle = findViewById(R.id.btn_toggle_chart_ele)
        btnChartSpeed = findViewById(R.id.btn_toggle_chart_speed)
        btnChartDist = findViewById(R.id.btn_toggle_chart_dist)

        cardPhotoPopup = findViewById(R.id.card_photo_popup)
        imgPhotoPopup = findViewById(R.id.img_photo_popup_thumbnail)
        tvPhotoPopupDesc = findViewById(R.id.tv_photo_popup_desc)

        // Title and Back navigation
        btnBack.setOnClickListener { finish() }

        // Setup replay listeners
        btnReplayStart.setOnClickListener { startReplay() }
        btnReplayPause.setOnClickListener { pauseReplay() }
        btnReplayStop.setOnClickListener { stopReplay() }
        btnReplayRewind.setOnClickListener {
            jumpToReplayIndex(0)
            if (isReplaying) {
                pauseReplay()
            }
        }

        // Timeline scrubbing listener
        sbReplayTimeline.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser && trackPointsList.isNotEmpty()) {
                    jumpToReplayIndex(progress)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Setup speed selection listeners
        val speedBtnList = listOf(btnSpeed05, btnSpeed1, btnSpeed2, btnSpeed5, btnSpeed10)
        btnSpeed05.setOnClickListener { selectSpeedMultiplier(0.5f, btnSpeed05, speedBtnList) }
        btnSpeed1.setOnClickListener { selectSpeedMultiplier(1.0f, btnSpeed1, speedBtnList) }
        btnSpeed2.setOnClickListener { selectSpeedMultiplier(2.0f, btnSpeed2, speedBtnList) }
        btnSpeed5.setOnClickListener { selectSpeedMultiplier(5.0f, btnSpeed5, speedBtnList) }
        btnSpeed10.setOnClickListener { selectSpeedMultiplier(10.0f, btnSpeed10, speedBtnList) }

        // Setup profile chart toggles
        val chartBtnList = listOf(btnChartEle, btnChartSpeed, btnChartDist)
        btnChartEle.setOnClickListener { selectChartType(TrackProfileChartView.ChartType.ELEVATION, btnChartEle, chartBtnList) }
        btnChartSpeed.setOnClickListener { selectChartType(TrackProfileChartView.ChartType.SPEED, btnChartSpeed, chartBtnList) }
        btnChartDist.setOnClickListener { selectChartType(TrackProfileChartView.ChartType.DISTANCE, btnChartDist, chartBtnList) }

        btnLocateTrack.setOnClickListener { fitTrackOnMap() }

        // Mapbox click events
        mapView.getMapAsync { map ->
            this.mapboxMap = map
            setupMapStyle(map)
        }
    }

    private fun loadTrackData() {
        dbExecutor.execute {
            val track = trackDao.getTrackById(trackId)
            if (track == null) {
                runOnUiThread {
                    Toast.makeText(this, "轨迹数据不存在", Toast.LENGTH_SHORT).show()
                    finish()
                }
                return@execute
            }

            currentTrack = track
            val points = trackDao.getTrackPoints(trackId)
            val photos = trackDao.getPhotoAnchorsForTrack(trackId)

            trackPointsList.clear()
            trackPointsList.addAll(points)

            photoAnchorsList.clear()
            photoAnchorsList.addAll(photos)

            // Calculate metrics
            val totalDistanceMeters = calculateDistanceAndCumulative()
            val totalDurationSec = calculateDurationSeconds(track, points)

            val elevations = points.mapNotNull { it.elevation }
            val maxElevation = elevations.maxOrNull() ?: 0.0
            val minElevation = elevations.minOrNull() ?: 0.0
            val avgElevation = if (elevations.isNotEmpty()) elevations.average() else 0.0

            val speeds = points.map { it.speed }
            val maxSpeed = speeds.maxOrNull() ?: 0.0
            val avgSpeed = if (totalDurationSec > 0) totalDistanceMeters / totalDurationSec else 0.0

            // Calculate Ascent and Descent
            var totalAscent = 0.0
            var totalDescent = 0.0
            for (i in 1 until points.size) {
                val prevEle = points[i - 1].elevation
                val currEle = points[i].elevation
                if (prevEle != null && currEle != null) {
                    val diff = currEle - prevEle
                    if (diff > 0.0) {
                        totalAscent += diff
                    } else {
                        totalDescent += Math.abs(diff)
                    }
                }
            }

            // Populate Profile Chart
            val chartPoints = points.mapIndexed { idx, pt ->
                val distKm = if (idx < cumulativeDistances.size) cumulativeDistances[idx] else 0f
                TrackProfileChartView.ChartPoint(distKm, (pt.elevation ?: 0.0).toFloat(), pt.speed.toFloat())
            }

            runOnUiThread {
                tvTitle.text = track.name ?: "未命名轨迹"
                sbReplayTimeline.max = if (points.size > 1) points.size - 1 else 100
                sbReplayTimeline.progress = 0
                
                // Update 13 stats
                updateStatCard(R.id.card_stat_distance, "📐 总距离", String.format(Locale.US, "%.2f km", totalDistanceMeters / 1000f))
                updateStatCard(R.id.card_stat_duration, "⏱️ 总时长", formatDuration(totalDurationSec))
                updateStatCard(R.id.card_stat_points, "📍 轨迹点数", "${points.size}")
                updateStatCard(R.id.card_stat_ascent, "累计爬升", String.format(Locale.US, "+ %.1f m", totalAscent))
                updateStatCard(R.id.card_stat_descent, "累计下降", String.format(Locale.US, "- %.1f m", totalDescent))
                updateStatCard(R.id.card_stat_photos, "照片数量", "${photos.size} 张")
                updateStatCard(R.id.card_stat_max_ele, "最高海拔", String.format(Locale.US, "%.1f m", maxElevation))
                updateStatCard(R.id.card_stat_min_ele, "最低海拔", String.format(Locale.US, "%.1f m", minElevation))
                updateStatCard(R.id.card_stat_avg_speed, "平均速度", String.format(Locale.US, "%.2f m/s", avgSpeed))
                updateStatCard(R.id.card_stat_max_speed, "最高速度", String.format(Locale.US, "%.2f m/s", maxSpeed))
                updateStatCard(R.id.card_stat_avg_ele, "平均海拔", String.format(Locale.US, "%.1f m", avgElevation))
                updateStatCard(R.id.card_stat_start_time, "开始时间", formatDateTime(track.startTime))
                updateStatCard(R.id.card_stat_end_time, "结束时间", track.endTime?.let { formatDateTime(it) } ?: "--:--")

                // Set Chart Data
                chartView.setData(chartPoints)

                // Setup export click
                btnExport.setOnClickListener {
                    showExportOptions(track, points, photos)
                }

                // If map ready, draw track
                drawTrackOnMap()
                drawPhotoMarkersOnMap()
                fitTrackOnMap()
            }
        }
    }

    private fun updateStatCard(cardId: Int, label: String, value: String) {
        val card: View = findViewById(cardId)
        card.findViewById<TextView>(R.id.tv_stat_label).text = label
        card.findViewById<TextView>(R.id.tv_stat_value).text = value
    }

    private fun calculateDistanceAndCumulative(): Float {
        var totalDist = 0f
        cumulativeDistances.clear()
        cumulativeDistances.add(0f)

        for (i in 1 until trackPointsList.size) {
            val results = FloatArray(1)
            Location.distanceBetween(
                trackPointsList[i - 1].latitude, trackPointsList[i - 1].longitude,
                trackPointsList[i].latitude, trackPointsList[i].longitude,
                results
            )
            totalDist += results[0]
            cumulativeDistances.add(totalDist / 1000f) // Store in km
        }
        return totalDist
    }

    private fun calculateDurationSeconds(track: Track, points: List<TrackPoint>): Long {
        if (track.endTime != null) {
            val dur = (track.endTime!! - track.startTime) / 1000L
            return if (dur > 0) dur else 0L
        }
        if (points.isNotEmpty()) {
            val dur = (points.last().timestamp - points.first().timestamp) / 1000L
            return if (dur > 0) dur else 0L
        }
        return 0L
    }

    private fun formatDuration(seconds: Long): String {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return String.format(Locale.US, "%02d:%02d:%02d", h, m, s)
    }

    private fun formatDateTime(timestamp: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    private fun selectSpeedMultiplier(multiplier: Float, activeBtn: TextView, allBtns: List<TextView>) {
        this.speedMultiplier = multiplier
        for (btn in allBtns) {
            if (btn == activeBtn) {
                btn.setTextColor(0xFF38BDF8.toInt())
                btn.setBackgroundColor(0x2238BDF8)
            } else {
                btn.setTextColor(0xFFFFFFFF.toInt())
                btn.setBackgroundColor(0x1AFFFFFF)
            }
        }
    }

    private fun selectChartType(type: TrackProfileChartView.ChartType, activeBtn: TextView, allBtns: List<TextView>) {
        chartView.setChartType(type)
        val activeColor = when (type) {
            TrackProfileChartView.ChartType.ELEVATION -> 0xFF22D3EE.toInt()
            TrackProfileChartView.ChartType.SPEED -> 0xFFF59E0B.toInt()
            TrackProfileChartView.ChartType.DISTANCE -> 0xFF10B981.toInt()
        }
        val activeBg = when (type) {
            TrackProfileChartView.ChartType.ELEVATION -> 0x2222D3EE
            TrackProfileChartView.ChartType.SPEED -> 0x22F59E0B
            TrackProfileChartView.ChartType.DISTANCE -> 0x2210B981
        }

        for (btn in allBtns) {
            if (btn == activeBtn) {
                btn.setTextColor(activeColor)
                btn.setBackgroundColor(activeBg)
            } else {
                btn.setTextColor(0xFFFFFFFF.toInt())
                btn.setBackgroundColor(0x1AFFFFFF)
            }
        }
    }

    // Mapbox setup
    private fun setupMapStyle(map: MapboxMap) {
        val baseDir = File(android.os.Environment.getExternalStorageDirectory(), "CyberTrail")
        val mapsDirUpper = File(baseDir, "Maps")
        val mapsDirLower = File(baseDir, "maps")
        val mapsDir = if (mapsDirUpper.exists()) mapsDirUpper else mapsDirLower
        val worldFile = File(mapsDir, "world.mbtiles")
        val mbtilesFile = if (worldFile.exists()) {
            worldFile
        } else {
            val mbtilesFiles = mapsDir.listFiles { _, name -> name.endsWith(".mbtiles") }
            mbtilesFiles?.firstOrNull()
        }

        try {
            var styleJson = assets.open("style.json").bufferedReader().use { it.readText() }

            if (mbtilesFile != null) {
                // Spin up local server on 8085 to avoid port collision
                localTileServer?.stop()
                localTileServer = LocalTileServer(mbtilesFile.absolutePath, 8085).apply {
                    start()
                }
                styleJson = styleJson.replace("mbtiles://{mbtiles_path}", "http://127.0.0.1:8085/{z}/{x}/{y}.png")
            }

            map.setStyle(Style.Builder().fromJson(styleJson)) { style ->
                isMapLoaded = true
                drawTrackOnMap()
                drawPhotoMarkersOnMap()
                fitTrackOnMap()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed setup map style", e)
        }

        // Set photo click listener for photo anchors
        map.setOnMarkerClickListener { marker ->
            val matchingAnchor = photoAnchorsList.find {
                Math.abs(it.latitude - marker.position.latitude) < 0.0001 &&
                Math.abs(it.longitude - marker.position.longitude) < 0.0001
            }
            if (matchingAnchor != null) {
                showPhotoDialog(matchingAnchor)
            }
            true
        }
    }

    private fun drawTrackOnMap() {
        val map = mapboxMap ?: return
        if (!isMapLoaded) return
        val style = try { map.style } catch (e: Exception) { null } ?: return

        val pts = trackPointsList.map { Point.fromLngLat(it.longitude, it.latitude) }
        val lineString = if (pts.size >= 2) {
            LineString.fromLngLats(pts)
        } else if (pts.size == 1) {
            LineString.fromLngLats(listOf(pts[0], pts[0]))
        } else {
            null
        }

        try {
            var source = style.getSource("track-source") as? GeoJsonSource
            if (source == null) {
                source = GeoJsonSource("track-source")
                style.addSource(source)
            }

            if (lineString != null) {
                source.setGeoJson(FeatureCollection.fromFeature(Feature.fromGeometry(lineString)))
            } else {
                source.setGeoJson(FeatureCollection.fromFeatures(emptyArray()))
            }

            var layer = style.getLayer("track-layer") as? LineLayer
            if (layer == null) {
                layer = LineLayer("track-layer", "track-source")
                layer.setProperties(
                    PropertyFactory.lineColor(0xFF64748B.toInt()), // Slate Gray for un-traversed background track
                    PropertyFactory.lineWidth(6f),
                    PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                    PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND)
                )
                style.addLayer(layer)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error drawing track", e)
        }
    }

    private fun drawTraversedTrackOnMap(index: Int) {
        val map = mapboxMap ?: return
        if (!isMapLoaded) return
        val style = try { map.style } catch (e: Exception) { null } ?: return

        val limit = if (index < 0) 0 else if (index >= trackPointsList.size) trackPointsList.size else index + 1
        val subPoints = trackPointsList.take(limit)
        val pts = subPoints.map { Point.fromLngLat(it.longitude, it.latitude) }
        val lineString = if (pts.size >= 2) {
            LineString.fromLngLats(pts)
        } else if (pts.size == 1) {
            LineString.fromLngLats(listOf(pts[0], pts[0]))
        } else {
            null
        }

        try {
            var source = style.getSource("traversed-source") as? GeoJsonSource
            if (source == null) {
                source = GeoJsonSource("traversed-source")
                style.addSource(source)
            }

            if (lineString != null) {
                source.setGeoJson(FeatureCollection.fromFeature(Feature.fromGeometry(lineString)))
            } else {
                source.setGeoJson(FeatureCollection.fromFeatures(emptyArray()))
            }

            var layer = style.getLayer("traversed-layer") as? LineLayer
            if (layer == null) {
                layer = LineLayer("traversed-layer", "traversed-source")
                layer.setProperties(
                    PropertyFactory.lineColor(0xFF3B82F6.toInt()), // Beautiful Blue for traversed track
                    PropertyFactory.lineWidth(6f),
                    PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                    PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND)
                )
                style.addLayer(layer)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error drawing traversed track", e)
        }
    }

    private fun drawPhotoMarkersOnMap() {
        val map = mapboxMap ?: return
        if (!isMapLoaded) return

        // Clear existing markers
        for (marker in photoMarkersList) {
            map.removeMarker(marker)
        }
        photoMarkersList.clear()

        // Create standard custom small camera icon or circle for markers
        val iconFactory = com.mapbox.mapboxsdk.annotations.IconFactory.getInstance(this)
        val cameraIcon = try {
            val bitmap = BitmapFactory.decodeResource(resources, android.R.drawable.ic_menu_camera)
            val scaled = Bitmap.createScaledBitmap(bitmap, 48, 48, true)
            iconFactory.fromBitmap(scaled)
        } catch (e: Exception) {
            null
        }

        for (anchor in photoAnchorsList) {
            val markerOptions = MarkerOptions()
                .position(LatLng(anchor.latitude, anchor.longitude))
                .title("📷照片")
                .snippet(anchor.note.ifEmpty { "点击查看完整大图" })
            
            if (cameraIcon != null) {
                markerOptions.icon(cameraIcon)
            }

            val marker = map.addMarker(markerOptions)
            photoMarkersList.add(marker)
        }
    }

    private fun fitTrackOnMap() {
        val map = mapboxMap ?: return
        if (trackPointsList.isEmpty()) return

        val latLngs = trackPointsList.map { LatLng(it.latitude, it.longitude) }
        try {
            val bounds = LatLngBounds.Builder().includes(latLngs).build()
            map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 50), 1000)
        } catch (e: Exception) {
            Log.e(TAG, "Fit bounds error", e)
        }
    }

    // Replay controls
    private val replayRunnable = object : Runnable {
        override fun run() {
            if (!isReplaying) return
            val pts = trackPointsList
            if (pts.isEmpty()) return

            if (replayIndex >= pts.size) {
                stopReplay()
                Toast.makeText(this@TrackDetailActivity, "回放播毕！", Toast.LENGTH_SHORT).show()
                return
            }

            jumpToReplayIndex(replayIndex)
            replayIndex++

            val delay = (600f / speedMultiplier).toLong()
            replayHandler.postDelayed(this, delay)
        }
    }

    private fun jumpToReplayIndex(index: Int) {
        if (trackPointsList.isEmpty()) return
        
        // Clamp index
        val targetIndex = index.coerceIn(0, trackPointsList.size - 1)
        replayIndex = targetIndex

        val pt = trackPointsList[targetIndex]
        val latLng = LatLng(pt.latitude, pt.longitude)

        // Move/Create marker
        if (replayMarker == null) {
            val iconFactory = com.mapbox.mapboxsdk.annotations.IconFactory.getInstance(this)
            val dotIcon = try {
                val size = 32
                val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
                val canvas = android.graphics.Canvas(bmp)
                val p = android.graphics.Paint().apply {
                    color = 0xFFEF4444.toInt() // Beautiful Red dot for current position as requested
                    isAntiAlias = true
                }
                canvas.drawCircle(size / 2f, size / 2f, size / 2f, p)
                p.color = android.graphics.Color.WHITE
                canvas.drawCircle(size / 2f, size / 2f, size / 4f, p)
                iconFactory.fromBitmap(bmp)
            } catch (e: Exception) {
                null
            }

            val options = MarkerOptions().position(latLng).title("当前位置")
            if (dotIcon != null) {
                options.icon(dotIcon)
            }
            replayMarker = mapboxMap?.addMarker(options)
        } else {
            replayMarker?.position = latLng
        }

        // Animate map camera smoothly
        mapboxMap?.animateCamera(CameraUpdateFactory.newLatLng(latLng), 100)

        // Draw traversed track portion
        drawTraversedTrackOnMap(targetIndex)

        // Check for photos nearby (Photo Linkage)
        checkPhotoLinkage(pt)

        // Update Progress text
        val currentDistKm = if (targetIndex < cumulativeDistances.size) cumulativeDistances[targetIndex] else 0f
        tvReplayProgress.text = String.format(
            Locale.US,
            "进度: %d / %d | 海拔: %s m | 距离: %.2f km",
            targetIndex + 1,
            trackPointsList.size,
            pt.elevation?.let { String.format(Locale.US, "%.1f", it) } ?: "--",
            currentDistKm
        )

        // Sync SeekBar progress without triggering recursive updates
        sbReplayTimeline.progress = targetIndex
    }

    private fun startReplay() {
        if (trackPointsList.isEmpty()) {
            Toast.makeText(this, "无轨迹点数据，无法回放", Toast.LENGTH_SHORT).show()
            return
        }
        if (isReplaying) return

        isReplaying = true
        tvReplayStatus.text = "👁 轨迹回放: 播放中"
        
        // Resume from current index, or reset if completed
        if (replayIndex >= trackPointsList.size) {
            replayIndex = 0
        }
        replayHandler.post(replayRunnable)
    }

    private fun pauseReplay() {
        if (!isReplaying) return
        isReplaying = false
        tvReplayStatus.text = "👁 轨迹回放: 已暂停"
        replayHandler.removeCallbacks(replayRunnable)
    }

    private fun stopReplay() {
        isReplaying = false
        replayHandler.removeCallbacks(replayRunnable)
        replayIndex = 0
        tvReplayStatus.text = "👁 轨迹回放: 停止"
        tvReplayProgress.text = "进度: 0 / 0 | 海拔: -- m | 距离: 0.00 km"
        sbReplayTimeline.progress = 0
        
        replayMarker?.let {
            mapboxMap?.removeMarker(it)
            replayMarker = null
        }
        drawTraversedTrackOnMap(-1) // Clear traversed track overlay
        cardPhotoPopup.visibility = View.GONE
    }

    // Photo linkage search
    private fun checkPhotoLinkage(pt: TrackPoint) {
        // Find if there is any photo taken nearby (< 30 meters)
        val nearbyPhoto = photoAnchorsList.find { anchor ->
            val results = FloatArray(1)
            Location.distanceBetween(
                pt.latitude, pt.longitude,
                anchor.latitude, anchor.longitude,
                results
            )
            results[0] < 30f // Within 30 meters threshold
        }

        if (nearbyPhoto != null) {
            cardPhotoPopup.visibility = View.VISIBLE
            tvPhotoPopupDesc.text = nearbyPhoto.note.ifEmpty { "回放相册联动" }

            // Scale down load to avoid OOM
            val bitmap = loadScaledBitmap(nearbyPhoto.imagePath, 200, 200)
            if (bitmap != null) {
                imgPhotoPopup.setImageBitmap(bitmap)
            } else {
                imgPhotoPopup.setImageDrawable(null)
            }

            cardPhotoPopup.setOnClickListener {
                showPhotoDialog(nearbyPhoto)
            }
        }
    }

    private fun loadScaledBitmap(filePath: String, reqWidth: Int, reqHeight: Int): Bitmap? {
        val file = File(filePath)
        if (!file.exists()) return null

        try {
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeFile(filePath, options)

            // Calculate scale
            var sampleSize = 1
            if (options.outHeight > reqHeight || options.outWidth > reqWidth) {
                val halfHeight = options.outHeight / 2
                val halfWidth = options.outWidth / 2
                while (halfHeight / sampleSize >= reqHeight && halfWidth / sampleSize >= reqWidth) {
                    sampleSize *= 2
                }
            }

            options.apply {
                inJustDecodeBounds = false
                inSampleSize = sampleSize
            }
            return BitmapFactory.decodeFile(filePath, options)
        } catch (e: Exception) {
            Log.e(TAG, "Scale bitmap load failed", e)
        }
        return null
    }

    private fun showPhotoDialog(photo: PhotoAnchor) {
        val imgView = ImageView(this).apply {
            val bitmap = loadScaledBitmap(photo.imagePath, 800, 800)
            if (bitmap != null) {
                setImageBitmap(bitmap)
            } else {
                setImageResource(android.R.drawable.ic_menu_gallery)
            }
            setPadding(16, 16, 16, 16)
        }

        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(photo.note.ifEmpty { "轨迹关联相片" })
            .setView(imgView)
            .setPositiveButton("确定", null)
            .show()
    }

    private fun showExportOptions(track: Track, points: List<TrackPoint>, photos: List<PhotoAnchor>) {
        val formats = arrayOf("GPX", "KML", "GeoJSON", "CyberTrail JSON")
        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("选择导出格式")
            .setItems(formats) { _, which ->
                val format = when (which) {
                    0 -> "GPX"
                    1 -> "KML"
                    2 -> "GEOJSON"
                    else -> "JSON"
                }
                dbExecutor.execute {
                    val file = TrackFileHelper.exportTrack(track, points, photos, format)
                    runOnUiThread {
                        if (file != null) {
                            Toast.makeText(this@TrackDetailActivity, "导出成功! 文件: ${file.name}", Toast.LENGTH_LONG).show()
                        } else {
                            Toast.makeText(this@TrackDetailActivity, "导出失败", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            .show()
    }

    // Lifecycle events
    override fun onStart() {
        super.onStart()
        mapView.onStart()
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
        pauseReplay()
    }

    override fun onStop() {
        super.onStop()
        mapView.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mapView.onSaveInstanceState(outState)
    }

    override fun onLowMemory() {
        super.onLowMemory()
        mapView.onLowMemory()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopReplay()
        try {
            mapView.onDestroy()
        } catch (e: Exception) {}
        localTileServer?.stop()
    }
}
