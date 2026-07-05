package com.cybertrail.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.sqlite.SQLiteDatabase
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.util.Log
import android.annotation.SuppressLint
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.core.view.GravityCompat
import com.cybertrail.app.gis.DEMSystem
import com.mapbox.mapboxsdk.Mapbox
import com.mapbox.mapboxsdk.maps.MapView
import com.mapbox.mapboxsdk.maps.MapboxMap
import com.mapbox.mapboxsdk.maps.OnMapReadyCallback
import com.mapbox.mapboxsdk.maps.Style
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.Priority
import com.mapbox.mapboxsdk.location.LocationComponent
import com.mapbox.mapboxsdk.location.LocationComponentActivationOptions
import com.mapbox.mapboxsdk.location.modes.CameraMode
import com.mapbox.mapboxsdk.location.modes.RenderMode
import com.mapbox.geojson.Feature
import com.mapbox.geojson.FeatureCollection
import com.mapbox.geojson.LineString
import com.mapbox.geojson.Point
import com.mapbox.mapboxsdk.style.layers.LineLayer
import com.mapbox.mapboxsdk.style.layers.PropertyFactory
import com.mapbox.mapboxsdk.style.sources.GeoJsonSource
import com.cybertrail.app.db.AppDatabase
import com.cybertrail.app.db.Track
import com.cybertrail.app.db.TrackPoint
import com.cybertrail.app.db.TrackDao
import com.cybertrail.app.db.PhotoAnchor
import com.cybertrail.app.db.WaypointEntity
import com.cybertrail.app.db.WaypointDao
import java.util.concurrent.Executors
import android.os.Handler
import android.os.Looper
import java.io.File
import android.net.Uri
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import com.cybertrail.app.gis.TrackFileHelper
import androidx.activity.result.contract.ActivityResultContracts
import android.app.Dialog
import android.view.GestureDetector
import java.text.SimpleDateFormat
import java.util.Locale
import android.content.IntentFilter
import android.content.BroadcastReceiver
import android.provider.MediaStore
import android.os.Build
import androidx.core.content.ContextCompat

class MapActivity : AppCompatActivity(), OnMapReadyCallback, LocationListener {

    private lateinit var mapView: MapView
    private var mapboxMap: MapboxMap? = null
    private lateinit var demSystem: DEMSystem
    private var lastTerrainAnalysis: com.cybertrail.app.gis.TerrainAnalyzer.AnalysisResult? = null

    // HUD controls
    private lateinit var hudElevation: TextView
    private lateinit var hudSlope: TextView
    private lateinit var hudAspect: TextView
    
    private lateinit var hudMbtilesPath: TextView
    private lateinit var hudMbtilesStatus: TextView
    private lateinit var hudTileCount: TextView
    private lateinit var hudStyleStatus: TextView
    private lateinit var hudDiagnosticCounters: TextView

    // Counters for diagnostics
    private var renderFrameCount = 0
    private var cameraMoveCount = 0
    private var tileRequestCount = 0
    private var tileFoundCount = 0
    private var tileNotFoundCount = 0
    private var sourceChangedCount = 0
    private var didFinishLoadingStyleCount = 0
    private var didBecomeIdleCount = 0
    private var sourceExists: Boolean? = null
    private var layerExists: Boolean? = null
    private var layerClassString: String? = null
    private var layerSourceId: String? = null
    private var layerVisibilityString: String? = null
    private var cameraZoomFloat: Float? = null
    private var sourceRuntimeClassString: String? = null
    private var styleTileUrlString: String? = null
    private var sourceTypeString: String? = null
    private var sourceObjectString: String? = null
    private var sourceCountInt: Int? = null
    private var layerCountInt: Int? = null
    private var sourceListString: String? = null
    private var layerListString: String? = null
    private var layerMinZoomString: String? = null
    private var layerMaxZoomString: String? = null
    private var sourceMinZoomString: String? = null
    private var sourceMaxZoomString: String? = null
    private var rasterTilesString: String? = null
    private var rasterSchemeString: String? = null
    private var rasterMinZoomString: String? = null
    private var rasterMaxZoomString: String? = null
    private var rasterAttributionString: String? = null
    private var rasterBoundsString: String? = null
    private var rasterSourceAvailableMethodsString: String? = null
    private var isHudExpanded: Boolean = true
    private var finalStyleJsonString: String? = null
    private var layerSourceLayerString: String? = null
    private var forcedZoomApplied: Boolean = false
    private var lastGpsLatitude: Double? = null
    private var lastGpsLongitude: Double? = null
    private val httpRequestsHistory = java.util.concurrent.CopyOnWriteArrayList<String>()
    private var cameraForcedTestResult: String = "Not started"
    private var mbtilesScanResult: String? = null

    private lateinit var locationManager: LocationManager
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var tvPanelLat: TextView
    private lateinit var tvPanelLon: TextView
    private lateinit var tvPanelAlt: TextView
    private lateinit var tvPanelAccuracy: TextView
    private lateinit var tvPanelSpeed: TextView
    private lateinit var tvPanelBearing: TextView
    private lateinit var btnLocate: View

    // Track Recording System Properties
    private lateinit var trackDao: TrackDao
    private lateinit var waypointDao: WaypointDao
    private var navigationTargetWaypoint: WaypointEntity? = null
    private lateinit var panelNavigation: View
    private lateinit var tvNavTitle: TextView
    private lateinit var tvNavDistance: TextView
    private lateinit var tvNavBearing: TextView
    private lateinit var tvNavTime: TextView
    private lateinit var btnNavCancel: View

    private val dbExecutor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var currentTrackId: Long = 0L
    private val currentTrackPoints = mutableListOf<TrackPoint>()
    private val pendingPointsToSave = mutableListOf<TrackPoint>()
    private var trackStatus: String = "STOPPED" // "RECORDING", "PAUSED", "STOPPED"
    private var trackStartTime: Long = 0L
    private var trackTotalSeconds: Long = 0L

    private lateinit var tvTrackStatus: TextView
    private lateinit var tvTrackStats: TextView
    private lateinit var btnTrackStart: View
    private lateinit var btnTrackPause: View
    private lateinit var btnTrackResume: View
    private lateinit var btnTrackStop: View
    private lateinit var btnTrackSave: View

    private lateinit var trackListContainer: LinearLayout
    private lateinit var btnTrackImportGpx: View
    private var loadedTrackId: Long? = null
    private val loadedTrackPoints = mutableListOf<TrackPoint>()
    private var lastDiskSaveTime: Long = 0L

    private lateinit var btnCamera: View
    private var photoFile: File? = null

    private val takePictureLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && photoFile != null) {
            savePhotoAnchor(photoFile!!)
        } else {
            Toast.makeText(this, "拍照取消", Toast.LENGTH_SHORT).show()
        }
    }

    private lateinit var btnPhotoImport: View
    private lateinit var cbLayerTrack: android.widget.CheckBox
    private lateinit var cbLayerWaypoint: android.widget.CheckBox
    private lateinit var cbLayerPhoto: android.widget.CheckBox
    private lateinit var cbLayerRoute: android.widget.CheckBox

    private var isRouteLayerEnabled = true
    private var activeRoute: com.cybertrail.app.db.RouteEntity? = null
    private val routeWaypoints = mutableListOf<com.cybertrail.app.db.WaypointEntity>()
    private var routeCurrentTargetIndex = 0
    private var isRouteNavigationActive = false
    private var alerted100m = false
    private var alerted50m = false

    // Track Replay UI Controls
    private lateinit var panelTrackReplay: LinearLayout
    private lateinit var tvReplayStatus: TextView
    private lateinit var tvReplayProgress: TextView
    private lateinit var tvReplayLatLon: TextView
    private lateinit var tvReplayEle: TextView
    private lateinit var tvReplayDistance: TextView
    private lateinit var tvReplayClimb: TextView
    private lateinit var tvReplaySpeed: TextView
    private lateinit var tvReplayTime: TextView
    private lateinit var btnReplayPlayPause: TextView
    private lateinit var btnReplayStop: TextView
    private lateinit var btnReplaySpeed1: TextView
    private lateinit var btnReplaySpeed2: TextView
    private lateinit var btnReplaySpeed5: TextView

    // Track Replay State variables
    private var isReplayActive = false
    private var isReplayPlaying = false
    private var replaySpeedMultiplier = 1 // 1, 2, 5
    private var replayPoints: List<TrackPoint> = emptyList()
    private var replayCurrentIndex = 0
    private var replayMarker: com.mapbox.mapboxsdk.annotations.Marker? = null
    private var replayCumulativeClimbs = DoubleArray(0)
    private var replayCumulativeDistances = DoubleArray(0)
    private val replayHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var replayRunnable: Runnable? = null

    private var isTrackLayerEnabled = true
    private var isWaypointLayerEnabled = true
    private var isPhotoLayerEnabled = true

    private val trackServiceReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                com.cybertrail.app.gis.TrackForegroundService.ACTION_NEW_POINT -> {
                    val lat = intent.getDoubleExtra(com.cybertrail.app.gis.TrackForegroundService.EXTRA_POINT_LAT, 0.0)
                    val lon = intent.getDoubleExtra(com.cybertrail.app.gis.TrackForegroundService.EXTRA_POINT_LON, 0.0)
                    val alt = intent.getDoubleExtra(com.cybertrail.app.gis.TrackForegroundService.EXTRA_POINT_ALT, -1000.0)
                    val elevation = if (alt == -1000.0) null else alt
                    val time = intent.getLongExtra(com.cybertrail.app.gis.TrackForegroundService.EXTRA_POINT_TIME, 0L)
                    val speed = intent.getFloatExtra(com.cybertrail.app.gis.TrackForegroundService.EXTRA_POINT_SPEED, 0f)
                    val accuracy = intent.getFloatExtra(com.cybertrail.app.gis.TrackForegroundService.EXTRA_POINT_ACCURACY, 0f)

                    val tp = TrackPoint(
                        trackId = currentTrackId,
                        latitude = lat,
                        longitude = lon,
                        elevation = elevation,
                        timestamp = time,
                        speed = speed,
                        accuracy = accuracy
                    )
                    synchronized(currentTrackPoints) {
                        if (currentTrackPoints.none { it.timestamp == tp.timestamp }) {
                            currentTrackPoints.add(tp)
                        }
                    }
                    drawTrackOnMap()
                    updateTrackStatsUi()
                }
                com.cybertrail.app.gis.TrackForegroundService.ACTION_TICK -> {
                    trackTotalSeconds = intent.getLongExtra(com.cybertrail.app.gis.TrackForegroundService.EXTRA_DURATION, 0L)
                    updateTrackStatsUi()
                }
            }
        }
    }

    private val photoImportLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            importPhotoFromUri(uri)
        }
    }

    private val importGpxLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            importGpxFromUri(uri)
        }
    }

    private val trackSaveIntervalMs = 10000L
    private val saveRunnable = object : Runnable {
        override fun run() {
            savePendingPoints()

            // Sync to disk every 60 seconds during recording to prevent crash data loss
            if (trackStatus == "RECORDING" && System.currentTimeMillis() - lastDiskSaveTime >= 60000L) {
                val currentPointsCopy = synchronized(currentTrackPoints) { ArrayList(currentTrackPoints) }
                val trackId = currentTrackId
                dbExecutor.execute {
                    val track = trackDao.getTrackById(trackId)
                    if (track != null) {
                        TrackFileHelper.saveTrackToJson(track, currentPointsCopy)
                    }
                }
                lastDiskSaveTime = System.currentTimeMillis()
            }

            mainHandler.postDelayed(this, trackSaveIntervalMs)
        }
    }

    private val durationRunnable = object : Runnable {
        override fun run() {
            if (trackStatus == "RECORDING") {
                trackTotalSeconds++
                updateTrackStatsUi()
            }
            mainHandler.postDelayed(this, 1000L)
        }
    }

    companion object {
        private const val TAG = "MapActivity"
    }

    private val downloadReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            Log.i(TAG, "Received MAP_DOWNLOAD_COMPLETED broadcast, refreshing map...")
            runOfflineDiagnostics()
            mapboxMap?.let { map ->
                onMapReady(map)
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // MapLibre requires initialization
        try {
            Mapbox.getInstance(this)
            Mapbox.setConnected(true) // 强制认为处于联网状态，确保断网也能加载本地 LocalTileServer 的瓦片
        } catch (e: Exception) {
            Log.e(TAG, "Mapbox instance init error", e)
        }

        setContentView(R.layout.activity_map)

        // Initialize GIS Engine
        demSystem = DEMSystem(this)

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(downloadReceiver, android.content.IntentFilter("com.cybertrail.app.MAP_DOWNLOAD_COMPLETED"), Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(downloadReceiver, android.content.IntentFilter("com.cybertrail.app.MAP_DOWNLOAD_COMPLETED"))
        }

        // Bind layouts
        val drawerLayout = findViewById<DrawerLayout>(R.id.drawer_layout)
        val drawerView = findViewById<ScrollView>(R.id.drawer_scroll_view)

        // Set drawer width to exactly 80% screen width
        try {
            val displayMetrics = resources.displayMetrics
            val drawerWidth = (displayMetrics.widthPixels * 0.8).toInt()
            val drawerLayoutParams = drawerView.layoutParams
            drawerLayoutParams.width = drawerWidth
            drawerView.layoutParams = drawerLayoutParams
        } catch (e: Exception) {
            Log.e(TAG, "Error setting drawer width dynamically", e)
        }

        val btnMenu = findViewById<TextView>(R.id.btn_menu)
        val btnMenuDrawerClose = findViewById<TextView>(R.id.btn_menu_drawer_close)

        btnMenu.setOnClickListener {
            if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                drawerLayout.closeDrawer(GravityCompat.START)
            } else {
                drawerLayout.openDrawer(GravityCompat.START)
            }
        }

        btnMenuDrawerClose.setOnClickListener {
            drawerLayout.closeDrawer(GravityCompat.START)
        }

        mapView = findViewById(R.id.mapView)

        tvPanelLat = findViewById(R.id.tv_panel_lat)
        tvPanelLon = findViewById(R.id.tv_panel_lon)
        tvPanelAlt = findViewById(R.id.tv_panel_alt)
        tvPanelAccuracy = findViewById(R.id.tv_panel_accuracy)
        tvPanelSpeed = findViewById(R.id.tv_panel_speed)
        tvPanelBearing = findViewById(R.id.tv_panel_bearing)
        btnLocate = findViewById(R.id.btn_locate)
        btnCamera = findViewById(R.id.btn_camera)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        btnLocate.setOnClickListener {
            performLocateAction()
        }

        btnCamera.setOnClickListener {
            checkCameraPermissionAndLaunch()
        }

        hudElevation = findViewById(R.id.hud_elevation)
        hudSlope = findViewById(R.id.hud_slope)
        hudAspect = findViewById(R.id.hud_aspect)
        hudMbtilesPath = findViewById(R.id.hud_mbtiles_path)
        hudMbtilesStatus = findViewById(R.id.hud_mbtiles_status)
        hudTileCount = findViewById(R.id.hud_tile_count)
        hudStyleStatus = findViewById(R.id.hud_style_status)
        hudDiagnosticCounters = findViewById(R.id.hud_diagnostic_counters)
        val panelView = findViewById<LinearLayout>(R.id.hud_diagnostic_panel)
        val titleView = findViewById<TextView>(R.id.hud_diagnostic_title)
        val scrollView = findViewById<ScrollView>(R.id.hud_diagnostic_scroll)

        titleView.setOnClickListener {
            isHudExpanded = !isHudExpanded
            updateDiagnosticHud()
        }

        val btnCopyDiagnostic = findViewById<TextView>(R.id.btn_copy_diagnostic)
        val btnCopyJson = findViewById<TextView>(R.id.btn_copy_json)
        val btnExportDiagnostic = findViewById<TextView>(R.id.btn_export_diagnostic)

        btnCopyDiagnostic.setOnClickListener {
            try {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val clip = android.content.ClipData.newPlainText("Diagnostic Info", hudDiagnosticCounters.text.toString())
                clipboard.setPrimaryClip(clip)
                btnCopyDiagnostic.text = "✅ 诊断信息已复制"
                btnCopyDiagnostic.postDelayed({
                    btnCopyDiagnostic.text = "📋 复制诊断信息"
                }, 2000)
            } catch (e: Exception) {
                Log.e("CYBERTRAIL_MAP", "Failed to copy diagnostic info", e)
            }
        }

        btnCopyJson.setOnClickListener {
            try {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val clip = android.content.ClipData.newPlainText("Style JSON", finalStyleJsonString ?: "")
                clipboard.setPrimaryClip(clip)
                btnCopyJson.text = "✅ Style JSON已复制"
                btnCopyJson.postDelayed({
                    btnCopyJson.text = "📋 复制Style JSON"
                }, 2000)
            } catch (e: Exception) {
                Log.e("CYBERTRAIL_MAP", "Failed to copy style JSON", e)
            }
        }

        btnExportDiagnostic.setOnClickListener {
            try {
                val sdfFile = java.text.SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", java.util.Locale.getDefault())
                val timestampFile = sdfFile.format(java.util.Date())
                val fileName = "CyberTrail_Diagnostic_$timestampFile.txt"
                
                val parentDir = java.io.File("/storage/emulated/0/CyberTrail/diagnostic/")
                if (!parentDir.exists()) {
                    parentDir.mkdirs()
                }
                
                val file = java.io.File(parentDir, fileName)
                
                val currentTimestamp = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
                
                val currentStyle = try {
                    mapboxMap?.style
                } catch (ex: Exception) {
                    null
                }
                val expStyleToJson = try {
                    val toJsonMethod = currentStyle?.javaClass?.methods?.firstOrNull { it.name == "toJson" || it.name == "getJson" }
                    if (toJsonMethod != null) {
                        toJsonMethod.invoke(currentStyle)?.toString() ?: "None"
                    } else {
                        "Style.toJson() method not found (loaded json fallback):\n${finalStyleJsonString ?: "None"}"
                    }
                } catch (ex: Exception) {
                    "Error: ${ex.message}"
                }

                val txtContent = """
========================================
CYBERTRAIL DIAGNOSTIC FILE
Timestamp: $currentTimestamp
========================================

--- DIAGNOSTIC INFORMATION ---
${hudDiagnosticCounters.text}

--- STYLE.TOJSON() ---
$expStyleToJson

--- FINAL STYLE JSON ---
${finalStyleJsonString ?: "None"}
""".trimIndent()

                file.writeText(txtContent)
                
                val absolutePath = file.absolutePath
                android.widget.Toast.makeText(this@MapActivity, "✅ 已导出诊断文件\n路径: $absolutePath", android.widget.Toast.LENGTH_LONG).show()
                
                btnExportDiagnostic.text = "✅ 已导出诊断文件"
                btnExportDiagnostic.postDelayed({
                    btnExportDiagnostic.text = "📤 导出诊断TXT"
                }, 2000)
            } catch (e: Exception) {
                Log.e("CYBERTRAIL_MAP", "Failed to export diagnostic file", e)
                android.widget.Toast.makeText(this@MapActivity, "❌ 导出失败: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
            }
        }

        val btnMbtilesScan = findViewById<TextView>(R.id.btn_mbtiles_scan)
        btnMbtilesScan.setOnClickListener {
            runMbtilesScan()
        }

        var isDragging = false
        var lastX = 0f
        var lastY = 0f

        titleView.setOnLongClickListener {
            isDragging = true
            titleView.alpha = 0.7f
            true
        }

        titleView.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    lastX = event.rawX
                    lastY = event.rawY
                }
                MotionEvent.ACTION_MOVE -> {
                    if (isDragging) {
                        val deltaX = event.rawX - lastX
                        val deltaY = event.rawY - lastY
                        
                        panelView.x += deltaX
                        panelView.y += deltaY
                        
                        lastX = event.rawX
                        lastY = event.rawY
                        
                        updateDiagnosticHud()
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (isDragging) {
                        isDragging = false
                        titleView.alpha = 1.0f
                        
                        // Snap to nearest corner (左上, 右上, 左下, 右下)
                        val parent = panelView.parent as? View
                        if (parent != null) {
                            val pW = parent.width
                            val pH = parent.height
                            val viewW = panelView.width
                            val viewH = panelView.height
                            
                            val curX = panelView.x
                            val curY = panelView.y
                            
                            val margin = 16f * parent.resources.displayMetrics.density
                            
                            val targetX = if (curX + viewW / 2 < pW / 2) {
                                margin
                            } else {
                                pW - viewW - margin
                            }
                            
                            val targetY = if (curY + viewH / 2 < pH / 2) {
                                margin
                            } else {
                                pH - viewH - margin
                            }
                            
                            panelView.animate()
                                .x(targetX)
                                .y(targetY)
                                .setDuration(250)
                                .withEndAction {
                                    updateDiagnosticHud()
                                }
                                .start()
                        }
                    } else {
                        if (event.action == MotionEvent.ACTION_UP) {
                            v.performClick()
                        }
                    }
                }
            }
            true
        }

        scrollView.viewTreeObserver.addOnScrollChangedListener {
            updateDiagnosticHud()
        }

        mapView.onCreate(savedInstanceState)
        Log.d("CYBERTRAIL_MAP", "MapView onCreate")
        mapView.addOnDidFinishRenderingFrameListener {
            renderFrameCount++
            Log.d("CYBERTRAIL_MAP", "Map render frame finished. FRAME_RENDERED=$renderFrameCount")
            updateDiagnosticHud()
        }
        mapView.addOnSourceChangedListener { id ->
            sourceChangedCount++
            Log.d("CYBERTRAIL_MAP", "OnSourceChanged: sourceId=$id (count=$sourceChangedCount)")
            updateDiagnosticHud()
        }
        mapView.addOnDidFinishLoadingStyleListener {
            didFinishLoadingStyleCount++
            Log.d("CYBERTRAIL_MAP", "OnDidFinishLoadingStyle: count=$didFinishLoadingStyleCount")
            updateDiagnosticHud()
        }
        mapView.addOnDidBecomeIdleListener {
            didBecomeIdleCount++
            Log.d("CYBERTRAIL_MAP", "OnDidBecomeIdle: count=$didBecomeIdleCount")
            updateDiagnosticHud()
        }
        mapView.getMapAsync(this)

        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager

        initTrackRecording()
        initTrackReplay()
        initWaypoints()
        runOfflineDiagnostics()
        startGpsTracking()
    }

    private fun runOfflineDiagnostics() {
        val baseDir = java.io.File(android.os.Environment.getExternalStorageDirectory(), "CyberTrail")
        val mapsDirUpper = java.io.File(baseDir, "Maps")
        val mapsDirLower = java.io.File(baseDir, "maps")
        val mapsDir = if (mapsDirUpper.exists()) mapsDirUpper else mapsDirLower
        if (!mapsDir.exists()) {
            mapsDir.mkdirs()
        }
        
        // Prioritize world.mbtiles, then fall back to any other .mbtiles file
        val worldFile = java.io.File(mapsDir, "world.mbtiles")
        val mbtilesFile = if (worldFile.exists()) {
            worldFile
        } else {
            val mbtilesFiles = mapsDir.listFiles { _, name -> name.endsWith(".mbtiles") }
            mbtilesFiles?.firstOrNull()
        }

        if (mbtilesFile == null) {
            hudMbtilesStatus.text = "物理文件: ❌ 未找到离线包 (请进入离线地图管理下载)"
            hudMbtilesPath.text = "路径: 暂无"
            hudTileCount.text = "地图瓦片数: 0 块 (离线包缺失)"
            return
        }

        val absolutePath = mbtilesFile.absolutePath
        hudMbtilesPath.text = "路径: $absolutePath"

        val exists = mbtilesFile.exists()
        hudMbtilesStatus.text = "物理文件: " + if (exists) "✅ 已确认物理存在" else "❌ 未找到离线包"

        var tilesNum = 0
        if (exists) {
            try {
                val db = android.database.sqlite.SQLiteDatabase.openDatabase(absolutePath, null, android.database.sqlite.SQLiteDatabase.OPEN_READONLY)
                val cursor = db.rawQuery("SELECT COUNT(*) FROM tiles", null)
                if (cursor.moveToFirst()) {
                    tilesNum = cursor.getInt(0)
                }
                cursor.close()
                db.close()
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Failed to query tiles count from MBTiles database", e)
            }
        }

        if (tilesNum > 0) {
            hudTileCount.text = "地图瓦片数: $tilesNum 块 (真实读取)"
            Log.i(TAG, "Loaded MBTiles:\n${mbtilesFile.absolutePath}\n${mbtilesFile.length()}\n$tilesNum")
        } else {
            hudTileCount.text = "地图瓦片数: " + if (exists) "读取中/无可用瓦片" else "0 块 (离线包缺失)"
        }
    }

    private var localTileServer: com.cybertrail.app.offline.LocalTileServer? = null

    override fun onMapReady(map: MapboxMap) {
        Log.d("CYBERTRAIL_MAP", "onMapReady")
        this.mapboxMap = map
        hudStyleStatus.text = "Style加载: 正在建立本地地图渲染器..."

        map.setOnMarkerClickListener { marker ->
            val activeId = loadedTrackId ?: currentTrackId
            dbExecutor.execute {
                val anchors = if (activeId != 0L) {
                    trackDao.getPhotoAnchorsForTrack(activeId)
                } else {
                    trackDao.getAllPhotoAnchors()
                }
                val matchingAnchor = anchors.find {
                    Math.abs(it.latitude - marker.position.latitude) < 0.0001 &&
                    Math.abs(it.longitude - marker.position.longitude) < 0.0001
                }
                if (matchingAnchor != null) {
                    runOnUiThread {
                        showPhotoAnchorDialog(matchingAnchor)
                    }
                    return@execute
                }

                val waypoints = waypointDao.getAllWaypoints()
                val matchingWaypoint = waypoints.find {
                    Math.abs(it.latitude - marker.position.latitude) < 0.0001 &&
                    Math.abs(it.longitude - marker.position.longitude) < 0.0001
                }
                if (matchingWaypoint != null) {
                    runOnUiThread {
                        showWaypointDetailDialog(matchingWaypoint)
                    }
                    return@execute
                }
            }
            true
        }

        map.addOnMapClickListener {
            Log.d("CYBERTRAIL_MAP", "Map clicked")
            false
        }

        map.addOnMapLongClickListener { latLng ->
            showCreateWaypointDialog(latLng)
            true
        }

        map.addOnCameraIdleListener {
            Log.d("CYBERTRAIL_MAP", "Map idle")
        }

        drawPhotoAnchorsOnMap()
        drawWaypointsOnMap()

        val baseDir = java.io.File(android.os.Environment.getExternalStorageDirectory(), "CyberTrail")
        val mapsDirUpper = java.io.File(baseDir, "Maps")
        val mapsDirLower = java.io.File(baseDir, "maps")
        val mapsDir = if (mapsDirUpper.exists()) mapsDirUpper else mapsDirLower
        val worldFile = java.io.File(mapsDir, "world.mbtiles")
        val mbtilesFile = if (worldFile.exists()) {
            worldFile
        } else {
            val mbtilesFiles = mapsDir.listFiles { _, name -> name.endsWith(".mbtiles") }
            mbtilesFiles?.firstOrNull()
        }

        try {
            var styleJson = assets.open("style.json").bufferedReader().use { it.readText() }
            
            if (mbtilesFile != null) {
                val absolutePath = mbtilesFile.absolutePath
                
                // 启动本地瓦片服务器
                localTileServer?.stop()
                localTileServer = com.cybertrail.app.offline.LocalTileServer(absolutePath).apply {
                    onTileRequest = {
                        runOnUiThread {
                            tileRequestCount++
                            Log.d("CYBERTRAIL_MAP", "TILE_REQUEST: $tileRequestCount")
                            updateDiagnosticHud()
                        }
                    }
                    onTileFound = {
                        runOnUiThread {
                            tileFoundCount++
                            Log.d("CYBERTRAIL_MAP", "TILE_FOUND: $tileFoundCount")
                            updateDiagnosticHud()
                        }
                    }
                    onTileNotFound = {
                        runOnUiThread {
                            tileNotFoundCount++
                            Log.d("CYBERTRAIL_MAP", "TILE_NOT_FOUND: $tileNotFoundCount")
                            updateDiagnosticHud()
                        }
                    }
                    onRequestLogged = {
                        val path = lastRequestPath
                        if (path != null) {
                            if (httpRequestsHistory.size >= 15) {
                                httpRequestsHistory.removeAt(0)
                            }
                            httpRequestsHistory.add(path)
                        }
                        runOnUiThread {
                            updateDiagnosticHud()
                        }
                    }
                }
                localTileServer?.start()

                styleJson = styleJson.replace("mbtiles://{mbtiles_path}", "http://127.0.0.1:${localTileServer?.port ?: 8080}/{z}/{x}/{y}.png")
                finalStyleJsonString = styleJson
                
                Log.d("MAP_DEBUG", "===== FINAL STYLE JSON START =====")
                Log.d("MAP_DEBUG", styleJson)
                Log.d("MAP_DEBUG", "===== FINAL STYLE JSON END =====")
                
                Log.d("CYBERTRAIL_MAP", "setStyle begin")
                map.setStyle(Style.Builder().fromJson(styleJson)) { style ->
                    Log.d("CYBERTRAIL_MAP", "STYLE_SUCCESS")
                    hudStyleStatus.text = "Style加载: Success (LocalTileServer)"
                    Log.i(TAG, "Style loaded successfully with mbtiles HTTP server.")
                    Log.d(TAG, "STYLE_SUCCESS")

                    Log.d("MAP_DEBUG", "===== STYLE SOURCES START =====")
                    style.sources.forEach {
                        Log.d("MAP_DEBUG", "SOURCE=${it.id}")
                    }
                    Log.d("MAP_DEBUG", "===== STYLE SOURCES END =====")

                    style.sources.forEach {
                        Log.d("MAP_DEBUG", "SOURCE_CLASS=${it.javaClass.name}")
                    }

                    style.getSource("offline-mbtiles")?.let {
                        Log.d("MAP_DEBUG", "SOURCE_RUNTIME_CLASS=${it.javaClass.name}")
                        sourceRuntimeClassString = it.javaClass.name
                    }

                    Log.d("MAP_DEBUG", "===== STYLE LAYERS START =====")
                    style.layers.forEach {
                        Log.d("MAP_DEBUG", "LAYER=${it.id}")
                    }
                    Log.d("MAP_DEBUG", "===== STYLE LAYERS END =====")

                    style.layers.forEach {
                        Log.d("MAP_DEBUG", "LAYER_ID=${it.id}")
                    }

                    val source = style.getSource("offline-mbtiles")
                    val isExist = source != null
                    sourceExists = isExist
                    Log.d("MAP_DEBUG", "SOURCE_EXISTS=$isExist")

                    val sourceType = source?.javaClass?.simpleName ?: "null"
                    sourceTypeString = sourceType
                    Log.d("MAP_DEBUG", "SOURCE_TYPE=$sourceType")
                    Log.d("MAP_DEBUG", "SOURCE_OBJECT=$source")
                    sourceObjectString = source?.toString() ?: "null"

                    val sourceTilesUrl = try {
                        val methods = source?.javaClass?.methods ?: arrayOf()
                        var foundUrl: String? = null
                        for (m in methods) {
                            if ((m.name == "getUri" || m.name == "getUrl") && m.parameterTypes.isEmpty()) {
                                foundUrl = m.invoke(source) as? String
                                break
                            }
                        }
                        foundUrl
                    } catch (e: Exception) {
                        null
                    }
                    Log.d("MAP_DEBUG", "SOURCE_TILES_URL=$sourceTilesUrl")

                    val offlineLayer = style.getLayer("offline-layer")
                    val isLayerExist = offlineLayer != null
                    layerExists = isLayerExist
                    val clazzName = offlineLayer?.javaClass?.simpleName ?: "null"
                    layerClassString = clazzName
                    Log.d("MAP_DEBUG", "LAYER_EXISTS=$isLayerExist")
                    Log.d("MAP_DEBUG", "LAYER_CLASS=$clazzName")

                    if (offlineLayer != null) {
                        val srcId = try {
                            val getter = offlineLayer.javaClass.getMethod("getSourceId")
                            getter.invoke(offlineLayer) as? String
                        } catch (e: Exception) {
                            null
                        }
                        Log.d("MAP_DEBUG", "LAYER_SOURCE=$srcId")
                        layerSourceId = srcId

                        val srcLayer = try {
                            val getter = offlineLayer.javaClass.getMethod("getSourceLayer")
                            getter.invoke(offlineLayer) as? String
                        } catch (e: Exception) {
                            null
                        }
                        Log.d("MAP_DEBUG", "LAYER_SOURCE_LAYER=$srcLayer")
                        layerSourceLayerString = srcLayer

                        val vis = try {
                            offlineLayer.visibility?.value?.toString()
                        } catch (e: Exception) {
                            null
                        }
                        Log.d("MAP_DEBUG", "LAYER_VISIBILITY=$vis")
                        layerVisibilityString = vis
                    }

                    Log.d("MAP_DEBUG", "CAMERA_ZOOM=${map.cameraPosition.zoom}")
                    cameraZoomFloat = map.cameraPosition.zoom.toFloat()

                    val tilesUrlRegex = Regex("\"tiles\"\\s*:\\s*\\[\\s*\"([^\"]+)\"")
                    val match = tilesUrlRegex.find(styleJson)
                    val tileUrl = match?.groupValues?.get(1) ?: "not found in JSON"
                    Log.d("MAP_DEBUG", "STYLE_JSON_TILE_URL=$tileUrl")
                    styleTileUrlString = tileUrl

                    val sCount = style.sources.size
                    sourceCountInt = sCount
                    Log.d("MAP_DEBUG", "SOURCE_COUNT=$sCount")

                    val lCount = style.layers.size
                    layerCountInt = lCount
                    Log.d("MAP_DEBUG", "LAYER_COUNT=$lCount")

                    val sList = style.sources.joinToString(",") { it.id }
                    sourceListString = sList
                    Log.d("MAP_DEBUG", "SOURCE_LIST=$sList")

                    val lList = style.layers.joinToString(",") { it.id }
                    layerListString = lList
                    Log.d("MAP_DEBUG", "LAYER_LIST=$lList")

                    val lMin = try {
                        val method = offlineLayer?.javaClass?.methods?.firstOrNull {
                            it.name.equals("getMinZoom", ignoreCase = true) || it.name.equals("minZoom", ignoreCase = true)
                        }
                        method?.invoke(offlineLayer)?.toString()
                    } catch (e: Exception) {
                        null
                    }
                    layerMinZoomString = lMin ?: "Unknown"
                    Log.d("MAP_DEBUG", "LayerMinZoom: $layerMinZoomString")

                    val lMax = try {
                        val method = offlineLayer?.javaClass?.methods?.firstOrNull {
                            it.name.equals("getMaxZoom", ignoreCase = true) || it.name.equals("maxZoom", ignoreCase = true)
                        }
                        method?.invoke(offlineLayer)?.toString()
                    } catch (e: Exception) {
                        null
                    }
                    layerMaxZoomString = lMax ?: "Unknown"
                    Log.d("MAP_DEBUG", "LayerMaxZoom: $layerMaxZoomString")

                    val sMin = try {
                        val method = source?.javaClass?.methods?.firstOrNull {
                            it.name.equals("getMinZoom", ignoreCase = true) || it.name.equals("getMinzoom", ignoreCase = true) || it.name.equals("minZoom", ignoreCase = true) || it.name.equals("minzoom", ignoreCase = true)
                        }
                        method?.invoke(source)?.toString()
                    } catch (e: Exception) {
                        null
                    }
                    sourceMinZoomString = sMin ?: "Unknown"
                    Log.d("MAP_DEBUG", "SourceMinZoom: $sourceMinZoomString")

                    val sMax = try {
                        val method = source?.javaClass?.methods?.firstOrNull {
                            it.name.equals("getMaxZoom", ignoreCase = true) || it.name.equals("getMaxzoom", ignoreCase = true) || it.name.equals("maxZoom", ignoreCase = true) || it.name.equals("maxzoom", ignoreCase = true)
                        }
                        method?.invoke(source)?.toString()
                    } catch (e: Exception) {
                        null
                    }
                    sourceMaxZoomString = sMax ?: "Unknown"
                    Log.d("MAP_DEBUG", "SourceMaxZoom: $sourceMaxZoomString")

                    // Part 2 & 3: Reflecting RasterSource properties
                    val ms = source?.javaClass?.methods ?: arrayOf()
                    val availableMethods = ms.filter { it.parameterTypes.isEmpty() }
                        .map { "${it.name}:${it.returnType.simpleName}" }
                        .sorted()
                        .joinToString(", ")
                    rasterSourceAvailableMethodsString = availableMethods
                    Log.d("MAP_DEBUG", "RASTER_SOURCE_AVAILABLE_METHODS=$availableMethods")

                    fun invokeNoArgMethod(obj: Any?, vararg names: String): Any? {
                        if (obj == null) return null
                        for (name in names) {
                            try {
                                val method = obj.javaClass.methods.firstOrNull { 
                                    it.name.equals(name, ignoreCase = true) && it.parameterTypes.isEmpty() 
                                }
                                if (method != null) {
                                    return method.invoke(obj)
                                }
                            } catch (e: Exception) {
                                Log.e("MAP_DEBUG", "Failed to invoke method $name", e)
                            }
                        }
                        return null
                    }

                    // 1. Tiles
                    val tilesVal = invokeNoArgMethod(source, "getUri", "getUrl", "getTiles")
                    if (tilesVal != null) {
                        rasterTilesString = if (tilesVal is Array<*>) {
                            tilesVal.joinToString(",")
                        } else if (tilesVal is List<*>) {
                            tilesVal.joinToString(",")
                        } else {
                            tilesVal.toString()
                        }
                        Log.d("MAP_DEBUG", "RASTER_TILES=$rasterTilesString")
                    } else {
                        rasterTilesString = "Cannot read RasterSource field:\ntiles"
                        Log.d("MAP_DEBUG", "RASTER_TILES=Cannot read RasterSource field: tiles")
                    }

                    // 2. Scheme
                    val schemeVal = invokeNoArgMethod(source, "getScheme", "scheme")
                    if (schemeVal != null) {
                        rasterSchemeString = schemeVal.toString()
                        Log.d("MAP_DEBUG", "RASTER_SCHEME=$rasterSchemeString")
                    } else {
                        rasterSchemeString = "Cannot read RasterSource field:\nscheme"
                        Log.d("MAP_DEBUG", "RASTER_SCHEME=Cannot read RasterSource field: scheme")
                    }

                    // 3. MinZoom
                    val rMinZoomVal = invokeNoArgMethod(source, "getMinZoom", "getMinzoom", "minZoom", "minzoom")
                    if (rMinZoomVal != null) {
                        rasterMinZoomString = rMinZoomVal.toString()
                        Log.d("MAP_DEBUG", "RASTER_MIN_ZOOM=$rasterMinZoomString")
                    } else {
                        rasterMinZoomString = "Cannot read RasterSource field:\nminZoom"
                        Log.d("MAP_DEBUG", "RASTER_MIN_ZOOM=Cannot read RasterSource field: minZoom")
                    }

                    // 4. MaxZoom
                    val rMaxZoomVal = invokeNoArgMethod(source, "getMaxZoom", "getMaxzoom", "maxZoom", "maxzoom")
                    if (rMaxZoomVal != null) {
                        rasterMaxZoomString = rMaxZoomVal.toString()
                        Log.d("MAP_DEBUG", "RASTER_MAX_ZOOM=$rasterMaxZoomString")
                    } else {
                        rasterMaxZoomString = "Cannot read RasterSource field:\nmaxZoom"
                        Log.d("MAP_DEBUG", "RASTER_MAX_ZOOM=Cannot read RasterSource field: maxZoom")
                    }

                    // 5. Attribution
                    val attrVal = invokeNoArgMethod(source, "getAttribution", "attribution")
                    if (attrVal != null) {
                        rasterAttributionString = attrVal.toString()
                        Log.d("MAP_DEBUG", "RASTER_ATTRIBUTION=$rasterAttributionString")
                    } else {
                        rasterAttributionString = "Cannot read RasterSource field:\nattribution"
                        Log.d("MAP_DEBUG", "RASTER_ATTRIBUTION=Cannot read RasterSource field: attribution")
                    }

                    // 6. Bounds
                    val boundsVal = invokeNoArgMethod(source, "getBounds", "bounds")
                    if (boundsVal != null) {
                        rasterBoundsString = boundsVal.toString()
                        Log.d("MAP_DEBUG", "RASTER_BOUNDS=$rasterBoundsString")
                    } else {
                        rasterBoundsString = "Cannot read RasterSource field:\nbounds"
                        Log.d("MAP_DEBUG", "RASTER_BOUNDS=Cannot read RasterSource field: bounds")
                    }

                    Log.d("MAP_DEBUG", "===== REFLECTING LAYER ZOOM METHODS =====")
                    offlineLayer?.javaClass?.methods?.forEach { m ->
                        if (m.name.contains("zoom", ignoreCase = true) && m.parameterTypes.isEmpty()) {
                            try {
                                Log.d("MAP_DEBUG", "LAYER_METHOD:${m.name}=${m.invoke(offlineLayer)}")
                            } catch(e: Exception) {}
                        }
                    }
                    Log.d("MAP_DEBUG", "===== REFLECTING SOURCE ZOOM METHODS =====")
                    source?.javaClass?.methods?.forEach { m ->
                        if (m.name.contains("zoom", ignoreCase = true) && m.parameterTypes.isEmpty()) {
                            try {
                                Log.d("MAP_DEBUG", "SOURCE_METHOD:${m.name}=${m.invoke(source)}")
                            } catch(e: Exception) {}
                        }
                    }

                    try {
                        map.moveCamera(com.mapbox.mapboxsdk.camera.CameraUpdateFactory.zoomTo(10.0))
                        forcedZoomApplied = true
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to force camera zoom to 10.0", e)
                    }

                    runCameraForcedTest(map)
                    updateDiagnosticHud()
                    enableLocationComponent(style)
                    drawRouteOnMap()
                }
            } else {
                // Load OSM fallback map
                val fallbackStyle = "{\n" +
                    "  \"version\": 8,\n" +
                    "  \"sources\": {\n" +
                    "    \"osm-fallback\": {\n" +
                    "      \"type\": \"raster\",\n" +
                    "      \"tiles\": [ \"https://a.tile.openstreetmap.org/{z}/{x}/{y}.png\" ],\n" +
                    "      \"tileSize\": 256,\n" +
                    "      \"maxzoom\": 18\n" +
                    "    }\n" +
                    "  },\n" +
                    "  \"layers\": [\n" +
                    "    {\n" +
                    "      \"id\": \"osm-fallback-layer\",\n" +
                    "      \"type\": \"raster\",\n" +
                    "      \"source\": \"osm-fallback\"\n" +
                    "    }\n" +
                    "  ]\n" +
                    "}"
                finalStyleJsonString = fallbackStyle
                Log.d("CYBERTRAIL_MAP", "setStyle begin")
                map.setStyle(Style.Builder().fromJson(fallbackStyle)) { style ->
                    Log.d("CYBERTRAIL_MAP", "STYLE_SUCCESS")
                    hudStyleStatus.text = "Style加载: 无离线地图(使用在线OSM备用)"
                    try {
                        map.moveCamera(com.mapbox.mapboxsdk.camera.CameraUpdateFactory.zoomTo(10.0))
                        forcedZoomApplied = true
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to force camera zoom to 10.0 in fallback callback", e)
                    }
                    runCameraForcedTest(map)
                    updateDiagnosticHud()
                    enableLocationComponent(style)
                    drawRouteOnMap()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load dynamic style JSON", e)
            hudStyleStatus.text = "Style加载: Failed"
        }

        // Bind camera change listener to query elevation at map center
        map.addOnCameraMoveListener {
            cameraMoveCount++
            Log.d("CYBERTRAIL_MAP", "Camera changed. CAMERA_MOVED=$cameraMoveCount")
            cameraZoomFloat = map.cameraPosition.zoom.toFloat()
            updateDiagnosticHud()
            val center = map.cameraPosition.target
            if (center != null) {
                val lat = center.latitude
                val lon = center.longitude
                updateTerrainHud(lat, lon)
            }
        }
    }

    private fun startGpsTracking() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            val hasGps = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
            if (hasGps) {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, this)
            } else {
                locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 1000L, 0f, this)
            }
        }
    }

    private var lastFetchTime = 0L
    private var lastGpsAltitude: Double? = null

    private fun updateTerrainHud(lat: Double, lon: Double) {
        val now = System.currentTimeMillis()
        if (now - lastFetchTime < 1000) return // Throttle
        lastFetchTime = now

        demSystem.terrainAnalyzer.analyzeLocationAsync(lat, lon) { result ->
            runOnUiThread {
                lastTerrainAnalysis = result
                updateDiagnosticHud()
                if (result != null && result.elevation != null) {
                    val sourceStr = if (result.source.startsWith("DEM")) result.source else "DEM"
                    hudElevation.text = "海拔: %.1f m (数据来源: %s)".format(result.elevation, sourceStr)
                    
                    if (result.slope != null) {
                        hudSlope.text = "坡度: %.1f° (数据来源: %s)".format(result.slope, sourceStr)
                        hudSlope.setTextColor(android.graphics.Color.WHITE)
                        hudSlope.setOnClickListener(null)
                    } else {
                        hudSlope.text = "坡度: N/A"
                        hudSlope.setTextColor(0xFFB0BEC5.toInt())
                        hudSlope.setOnClickListener {
                            val intent = Intent(this@MapActivity, com.cybertrail.app.offline.OfflineMapActivity::class.java)
                            startActivity(intent)
                        }
                    }
                    
                    val finalAspect = result.aspectGISFinal
                    if (finalAspect != null && finalAspect >= 0.0) {
                        val aspectDirShort = when {
                            finalAspect < 22.5 || finalAspect >= 337.5 -> "N"
                            finalAspect >= 22.5 && finalAspect < 67.5 -> "NE"
                            finalAspect >= 67.5 && finalAspect < 112.5 -> "E"
                            finalAspect >= 112.5 && finalAspect < 157.5 -> "SE"
                            finalAspect >= 157.5 && finalAspect < 202.5 -> "S"
                            finalAspect >= 202.5 && finalAspect < 247.5 -> "SW"
                            finalAspect >= 247.5 && finalAspect < 292.5 -> "W"
                            finalAspect >= 292.5 && finalAspect < 337.5 -> "NW"
                            else -> "N"
                        }
                        hudAspect.text = "坡向: %.1f° (%s) (数据来源: %s)".format(finalAspect, aspectDirShort, sourceStr)
                        hudAspect.setTextColor(android.graphics.Color.WHITE)
                        hudAspect.setOnClickListener(null)
                    } else {
                        hudAspect.text = "坡向: N/A"
                        hudAspect.setTextColor(0xFFB0BEC5.toInt())
                        hudAspect.setOnClickListener {
                            val intent = Intent(this@MapActivity, com.cybertrail.app.offline.OfflineMapActivity::class.java)
                            startActivity(intent)
                        }
                    }
                } else {
                    hudElevation.text = "海拔: N/A"
                    
                    hudSlope.text = "坡度: N/A"
                    hudSlope.setTextColor(0xFFB0BEC5.toInt())
                    hudSlope.setOnClickListener {
                        val intent = Intent(this@MapActivity, com.cybertrail.app.offline.OfflineMapActivity::class.java)
                        startActivity(intent)
                    }

                    hudAspect.text = "坡向: N/A"
                    hudAspect.setTextColor(0xFFB0BEC5.toInt())
                    hudAspect.setOnClickListener {
                        val intent = Intent(this@MapActivity, com.cybertrail.app.offline.OfflineMapActivity::class.java)
                        startActivity(intent)
                    }
                }
            }
        }

    }

    override fun onLocationChanged(location: Location) {
        val lat = location.latitude
        val lon = location.longitude
        lastGpsAltitude = demSystem.getElevation(lat, lon)
        lastGpsLatitude = lat
        lastGpsLongitude = lon
        
        Log.d("CYBERTRAIL_MAP", "CurrentLatitude: $lat, CurrentLongitude: $lon, Accuracy: ${location.accuracy}, Bearing: ${location.bearing}, Provider: ${location.provider ?: "GPS"}")

        updateLocationPanel(location)
        updateTerrainHud(lat, lon)
        updateDiagnosticHud()
        updateNavigationHud(location)

        // Record Track Point
        recordLocationPoint(location)

        mapboxMap?.let { map ->
            try {
                if (map.locationComponent.isLocationComponentEnabled) {
                    map.locationComponent.forceLocationUpdate(location)
                }
                Unit
            } catch (e: Exception) {
                Log.e(TAG, "Error updating LocationComponent", e)
            }
        }
    }

    private fun performLocateAction() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1001)
            return
        }

        Toast.makeText(this, "正在获取精准定位...", Toast.LENGTH_SHORT).show()

        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            if (location != null) {
                moveToLocation(location)
            } else {
                try {
                    val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000).apply {
                        setMaxUpdates(1)
                    }.build()
                    
                    fusedLocationClient.requestLocationUpdates(locationRequest, object : LocationCallback() {
                        override fun onLocationResult(locationResult: LocationResult) {
                            val newLocation = locationResult.lastLocation
                            if (newLocation != null) {
                                moveToLocation(newLocation)
                            } else {
                                fallbackToSystemLocation()
                            }
                        }
                    }, mainLooper)
                } catch (e: Exception) {
                    Log.e(TAG, "Error requesting FusedLocation, fallback", e)
                    fallbackToSystemLocation()
                }
            }
        }.addOnFailureListener { e ->
            Log.e(TAG, "FusedLocationProvider failed", e)
            fallbackToSystemLocation()
        }
    }

    private fun fallbackToSystemLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return
        }
        val sysLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            ?: locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
        if (sysLocation != null) {
            moveToLocation(sysLocation)
        } else {
            Toast.makeText(this, "无法获取当前位置，请确认已开启GPS或高精度定位服务", Toast.LENGTH_LONG).show()
        }
    }

    private fun moveToLocation(location: Location) {
        val lat = location.latitude
        val lon = location.longitude
        val zoom = 15.0
        val accuracy = location.accuracy
        val bearing = location.bearing
        val provider = location.provider ?: "FusedLocation"

        Log.d("CYBERTRAIL_MAP", "CurrentLatitude: $lat, CurrentLongitude: $lon, Accuracy: $accuracy, Bearing: $bearing, Provider: $provider")

        lastGpsLatitude = lat
        lastGpsLongitude = lon
        lastGpsAltitude = demSystem.getElevation(lat, lon)

        updateLocationPanel(location)
        updateTerrainHud(lat, lon)
        updateDiagnosticHud()

        // Record Track Point
        recordLocationPoint(location)

        mapboxMap?.let { map ->
            try {
                val cameraUpdate = com.mapbox.mapboxsdk.camera.CameraUpdateFactory.newLatLngZoom(
                    com.mapbox.mapboxsdk.geometry.LatLng(lat, lon), zoom
                )
                map.animateCamera(cameraUpdate)
                
                if (map.locationComponent.isLocationComponentEnabled) {
                    map.locationComponent.forceLocationUpdate(location)
                }
                Unit
            } catch (e: Exception) {
                Log.e(TAG, "Error updating map camera or LocationComponent", e)
            }
        }
    }

    private fun updateLocationPanel(location: Location) {
        runOnUiThread {
            tvPanelLat.text = "Lat: %.6f°".format(location.latitude)
            tvPanelLon.text = "Lon: %.6f°".format(location.longitude)
            
            val demAlt = demSystem.getElevation(location.latitude, location.longitude)
            tvPanelAlt.text = "Alt: %.0fm".format(demAlt)
            
            if (location.hasAccuracy()) {
                tvPanelAccuracy.text = "Accuracy: %.0fm".format(location.accuracy)
            } else {
                tvPanelAccuracy.text = "Accuracy: N/A"
            }
            
            if (location.hasSpeed()) {
                tvPanelSpeed.text = "Speed: %.1fm/s".format(location.speed)
            } else {
                tvPanelSpeed.text = "Speed: N/A"
            }
            
            if (location.hasBearing()) {
                tvPanelBearing.text = "Bearing: %.0f°".format(location.bearing)
            } else {
                tvPanelBearing.text = "Bearing: N/A"
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1001 || requestCode == 1002) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startGpsTracking()
                mapboxMap?.style?.let { style ->
                    enableLocationComponent(style)
                }
                if (requestCode == 1001) {
                    performLocateAction()
                }
            } else {
                Toast.makeText(this, "定位权限被拒绝，无法获取当前位置", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    override fun onProviderEnabled(provider: String) {}
    override fun onProviderDisabled(provider: String) {}

    override fun onStart() {
        super.onStart()
        mapView.onStart()
        Log.d("CYBERTRAIL_MAP", "MapView onStart")
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
        Log.d("CYBERTRAIL_MAP", "MapView onResume")
        startGpsTracking()
        
        if (com.cybertrail.app.gis.TrackForegroundService.isRunning) {
            currentTrackId = com.cybertrail.app.gis.TrackForegroundService.trackId
            trackStatus = com.cybertrail.app.gis.TrackForegroundService.currentStatus
            trackStartTime = com.cybertrail.app.gis.TrackForegroundService.trackStartTime
            trackTotalSeconds = com.cybertrail.app.gis.TrackForegroundService.elapsedSeconds
            synchronized(currentTrackPoints) {
                currentTrackPoints.clear()
                currentTrackPoints.addAll(com.cybertrail.app.gis.TrackForegroundService.currentPoints)
            }
            updateTrackUi()
            drawTrackOnMap()
            updateTrackStatsUi()
        }

        val filter = IntentFilter().apply {
            addAction(com.cybertrail.app.gis.TrackForegroundService.ACTION_NEW_POINT)
            addAction(com.cybertrail.app.gis.TrackForegroundService.ACTION_TICK)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(trackServiceReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(trackServiceReceiver, filter)
        }
        handleCenteringIntent(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleCenteringIntent(intent)
    }

    private fun handleCenteringIntent(intent: Intent?) {
        if (intent == null) return
        if (intent.hasExtra("center_latitude") && intent.hasExtra("center_longitude")) {
            val lat = intent.getDoubleExtra("center_latitude", 0.0)
            val lon = intent.getDoubleExtra("center_longitude", 0.0)
            mapboxMap?.animateCamera(
                com.mapbox.mapboxsdk.camera.CameraUpdateFactory.newLatLng(
                    com.mapbox.mapboxsdk.geometry.LatLng(lat, lon)
                ),
                800
            )
            intent.removeExtra("center_latitude")
            intent.removeExtra("center_longitude")
        }
        if (intent.hasExtra("plot_route_id")) {
            val routeId = intent.getStringExtra("plot_route_id")
            if (routeId != null) {
                loadRouteAndPlot(routeId, startNav = true)
            }
            intent.removeExtra("plot_route_id")
        }
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
        Log.d("CYBERTRAIL_MAP", "MapView onPause")
        locationManager.removeUpdates(this)
        
        try {
            unregisterReceiver(trackServiceReceiver)
        } catch (e: Exception) {
            // Ignored
        }
        
        // Immediately save the active track to SQLite and JSON file on pause (going to background)
        if (!com.cybertrail.app.gis.TrackForegroundService.isRunning) {
            forceSaveCurrentTrackToDisk()
        }
    }

    override fun onStop() {
        super.onStop()
        mapView.onStop()
        Log.d("CYBERTRAIL_MAP", "MapView onStop")
        
        // Switched to background / stopped
        if (!com.cybertrail.app.gis.TrackForegroundService.isRunning) {
            forceSaveCurrentTrackToDisk()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mapView.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        super.onDestroy()
        localTileServer?.stop()
        locationManager.removeUpdates(this)
        try {
            mapView.onDestroy()
        } catch (e: Exception) {
            Log.e(TAG, "Error destroying MapView", e)
        }
    }

    override fun onLowMemory() {
        super.onLowMemory()
        mapView.onLowMemory()
    }

    private fun enableLocationComponent(loadedMapStyle: Style) {
        try {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                val locationComponent = mapboxMap?.locationComponent ?: return
                val activationOptions = LocationComponentActivationOptions.builder(this, loadedMapStyle)
                    .useDefaultLocationEngine(true)
                    .build()
                locationComponent.activateLocationComponent(activationOptions)
                locationComponent.isLocationComponentEnabled = true
                locationComponent.cameraMode = CameraMode.NONE
                locationComponent.renderMode = RenderMode.COMPASS
                
                val lastLoc = try {
                    locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                        ?: locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                } catch (e: SecurityException) {
                    null
                }
                lastLoc?.let {
                    locationComponent.forceLocationUpdate(it)
                    updateLocationPanel(it)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to enable LocationComponent: ${e.message}", e)
        }
        
        // Also draw any restored track line when map style is loaded
        drawTrackOnMap()
        drawLoadedTrackOnMap()
    }

    private fun updateDiagnosticHud() {
        val panelView = findViewById<LinearLayout>(R.id.hud_diagnostic_panel)
        val scrollView = findViewById<ScrollView>(R.id.hud_diagnostic_scroll)
        val titleView = findViewById<TextView>(R.id.hud_diagnostic_title)
        val buttonsContainer = findViewById<LinearLayout>(R.id.hud_diagnostic_buttons)

        if (panelView == null || scrollView == null || titleView == null) return

        if (!isHudExpanded) {
            titleView.text = "▶ 诊断信息"
            scrollView.visibility = View.GONE
            buttonsContainer?.visibility = View.GONE
            val params = panelView.layoutParams
            params.height = ViewGroup.LayoutParams.WRAP_CONTENT
            panelView.layoutParams = params
            return
        }

        titleView.text = "▼ 诊断信息"
        scrollView.visibility = View.VISIBLE
        buttonsContainer?.visibility = View.VISIBLE
        val params = panelView.layoutParams
        val density = resources.displayMetrics.density
        params.height = (350 * density).toInt()
        panelView.layoutParams = params

        val server = localTileServer
        val serverRequestCountStr = server?.requestCountTotal?.toString() ?: "0"
        val serverTileRequestCountStr = server?.requestCountTile?.toString() ?: "0"
        val lastRequestPathStr = server?.lastRequestPath ?: "None"
        val lastRequestZStr = server?.lastRequestZ?.toString() ?: "None"
        val lastRequestXStr = server?.lastRequestX?.toString() ?: "None"
        val lastRequestYStr = server?.lastRequestY?.toString() ?: "None"
        val lastRequestTimestampStr = server?.lastRequestTimestamp ?: "None"
        val serverStartedStr = (server?.serverStarted ?: false).toString()
        val serverPortStr = server?.port?.toString() ?: "8080"
        val requestsHistoryStr = if (httpRequestsHistory.isEmpty()) "None" else httpRequestsHistory.joinToString("\n")

        val sExists = sourceExists?.toString() ?: "Unknown"
        val lExists = layerExists?.toString() ?: "Unknown"
        val lClass = layerClassString ?: "Unknown"
        val lSource = layerSourceId ?: "Unknown"
        val lVis = layerVisibilityString ?: "Unknown"
        val srcRunClass = sourceRuntimeClassString ?: "Unknown"
        val sTileUrl = styleTileUrlString ?: "Unknown"
        val srcType = sourceTypeString ?: "Unknown"
        val srcObj = sourceObjectString ?: "Unknown"
        val sCount = sourceCountInt?.toString() ?: "Unknown"
        val lCount = layerCountInt?.toString() ?: "Unknown"
        val sList = sourceListString ?: "Unknown"
        val lList = layerListString ?: "Unknown"
        val lMinZoom = layerMinZoomString ?: "Unknown"
        val lMaxZoom = layerMaxZoomString ?: "Unknown"
        val sMinZoom = sourceMinZoomString ?: "Unknown"
        val sMaxZoom = sourceMaxZoomString ?: "Unknown"
        val rTiles = rasterTilesString ?: "Unknown"
        val rScheme = rasterSchemeString ?: "Unknown"
        val rMinZoom = rasterMinZoomString ?: "Unknown"
        val rMaxZoom = rasterMaxZoomString ?: "Unknown"
        val rAttribution = rasterAttributionString ?: "Unknown"
        val rBounds = rasterBoundsString ?: "Unknown"
        val rMethods = rasterSourceAvailableMethodsString ?: "Unknown"
        val cZoom = cameraZoomFloat?.toString() ?: "Unknown"

        val hudHeight = panelView.height
        val scrollY = scrollView.scrollY

        val lSourceLayer = layerSourceLayerString ?: "Unknown"
        val fStyleJson = finalStyleJsonString ?: "None"

        val map = mapboxMap

        val currentStyle = try {
            map?.style
        } catch (e: Exception) {
            null
        }

        val styleAttachedToMapStr = (currentStyle != null).toString()

        var offlineSourceNullStr = "Unknown"
        var offlineSourceClassStr = "None"
        var offlineSourceIdStr = "None"
        var rasterSourceUrlStr = "None"
        var rasterSourceUriStr = "None"
        var styleLayersSizeStr = "Unknown"
        var styleSourcesSizeStr = "Unknown"
        var layerSourceMatchStr = "Unknown"
        var layerSourceValueStr = "None"
        var styleToJsonStr = "None"

        if (currentStyle != null) {
            val offlineSource = currentStyle.getSource("offline-mbtiles")
            offlineSourceNullStr = (offlineSource == null).toString()
            offlineSourceClassStr = offlineSource?.javaClass?.name ?: "None"
            offlineSourceIdStr = offlineSource?.id ?: "None"

            val isRasterSource = offlineSource != null && offlineSource.javaClass.simpleName.contains("RasterSource")
            if (isRasterSource) {
                rasterSourceUrlStr = try {
                    val m = offlineSource?.javaClass?.getMethod("getUrl")
                    m?.invoke(offlineSource)?.toString() ?: "None"
                } catch (e: Exception) {
                    "None"
                }

                rasterSourceUriStr = try {
                    val m = offlineSource?.javaClass?.getMethod("getUri")
                    m?.invoke(offlineSource)?.toString() ?: "None"
                } catch (e: Exception) {
                    "None"
                }
            }

            styleToJsonStr = try {
                val toJsonMethod = currentStyle.javaClass.methods.firstOrNull { it.name == "toJson" || it.name == "getJson" }
                if (toJsonMethod != null) {
                    toJsonMethod.invoke(currentStyle)?.toString() ?: "None"
                } else {
                    "Style.toJson() method not found (using loaded json fallback)\n$fStyleJson"
                }
            } catch (e: Exception) {
                "Error calling Style.toJson(): ${e.message}"
            }

            styleLayersSizeStr = currentStyle.layers.size.toString()
            styleSourcesSizeStr = currentStyle.sources.size.toString()

            val offlineLayerObj = currentStyle.getLayer("offline-layer")
            val lSrcIdVal = try {
                val getter = offlineLayerObj?.javaClass?.getMethod("getSourceId")
                getter?.invoke(offlineLayerObj) as? String
            } catch (e: Exception) {
                null
            }
            layerSourceMatchStr = if (offlineLayerObj == null) "Unknown" else (lSrcIdVal == "offline-mbtiles").toString()
            layerSourceValueStr = lSrcIdVal ?: "None"
        }

        val liveSourcesList = if (currentStyle != null) {
            try {
                currentStyle.sources.joinToString("\n") { s ->
                    val sId = s.id
                    val sClass = s.javaClass.simpleName
                    val sUri = try { s.javaClass.getMethod("getUri").invoke(s)?.toString() ?: "None" } catch(e: Exception) { "N/A" }
                    val sUrl = try { s.javaClass.getMethod("getUrl").invoke(s)?.toString() ?: "None" } catch(e: Exception) { "N/A" }
                    val sTiles = try {
                        val tilesVal = s.javaClass.getMethod("getTiles").invoke(s)
                        if (tilesVal is Array<*>) tilesVal.joinToString(",")
                        else if (tilesVal is List<*>) tilesVal.joinToString(",")
                        else tilesVal?.toString() ?: "None"
                    } catch(e: Exception) { "N/A" }
                    "  * [Source] id=$sId, type=$sClass, tiles=$sTiles, url=$sUrl, uri=$sUri"
                }
            } catch (e: Exception) {
                "Error building sources list: ${e.message}"
            }
        } else {
            "No Style Loaded"
        }

        val liveLayersList = if (currentStyle != null) {
            try {
                currentStyle.layers.joinToString("\n") { l ->
                    val lId = l.id
                    val lClass = l.javaClass.simpleName
                    val lSrcId = try { l.javaClass.getMethod("getSourceId").invoke(l)?.toString() ?: "None" } catch(e: Exception) { "N/A" }
                    val lSrcLayer = try { l.javaClass.getMethod("getSourceLayer").invoke(l)?.toString() ?: "None" } catch(e: Exception) { "N/A" }
                    val lVis = try { l.visibility.value?.toString() ?: "None" } catch(e: Exception) { "N/A" }
                    val lMin = try { l.minZoom.toString() } catch(e: Exception) { "N/A" }
                    val lMax = try { l.maxZoom.toString() } catch(e: Exception) { "N/A" }
                    "  * [Layer] id=$lId, type=$lClass, source=$lSrcId, source-layer=$lSrcLayer, visibility=$lVis, minzoom=$lMin, maxzoom=$lMax"
                }
            } catch (e: Exception) {
                "Error building layers list: ${e.message}"
            }
        } else {
            "No Style Loaded"
        }

        val styleFullyLoadedStr = try {
            val m = currentStyle?.javaClass?.getMethod("isFullyLoaded")
            if (m != null) {
                m.invoke(currentStyle)?.toString() ?: "None"
            } else {
                val m2 = map?.javaClass?.getMethod("isFullyLoaded")
                m2?.invoke(map)?.toString() ?: "None"
            }
        } catch (e: Exception) {
            "Error: ${e.message}"
        }

        val offlineLayerObjForD = currentStyle?.getLayer("offline-layer")
        
        val rlVisibilityStr = try {
            offlineLayerObjForD?.visibility?.value?.toString() ?: "None"
        } catch (e: Exception) {
            "Error: ${e.message}"
        }

        val rlOpacityStr = try {
            val getOpacityMethod = offlineLayerObjForD?.javaClass?.methods?.firstOrNull { it.name == "getRasterOpacity" }
            if (getOpacityMethod != null) {
                val opacityVal = getOpacityMethod.invoke(offlineLayerObjForD)
                val getValueMethod = opacityVal?.javaClass?.methods?.firstOrNull { it.name == "getValue" }
                if (getValueMethod != null) {
                    getValueMethod.invoke(opacityVal)?.toString() ?: "None"
                } else {
                    opacityVal?.toString() ?: "None"
                }
            } else {
                "Method getRasterOpacity not found"
            }
        } catch (e: Exception) {
            "Error: ${e.message}"
        }

        val rlRenderPassStr = try {
            val getRenderPassMethod = offlineLayerObjForD?.javaClass?.methods?.firstOrNull { it.name == "getRenderPass" }
            if (getRenderPassMethod != null) {
                getRenderPassMethod.invoke(offlineLayerObjForD)?.toString() ?: "None"
            } else {
                "Method getRenderPass not found"
            }
        } catch (e: Exception) {
            "Error: ${e.message}"
        }

        val sourceCacheStatusStr = try {
            val mbtilesSource = currentStyle?.getSource("offline-mbtiles")
            if (mbtilesSource != null) {
                val methods = mbtilesSource.javaClass.methods.map { "${it.name}(${it.parameterTypes.joinToString(",") { p -> p.simpleName }}): ${it.returnType.simpleName}" }
                val relevantMethods = methods.filter { it.contains("cache", ignoreCase = true) || it.contains("stat", ignoreCase = true) || it.contains("tile", ignoreCase = true) }
                if (relevantMethods.isNotEmpty()) {
                    relevantMethods.joinToString("\n")
                } else {
                    "No cache/tile/stat methods found on source. List of first 15:\n" + methods.take(15).joinToString("\n")
                }
            } else {
                "Source offline-mbtiles is null"
            }
        } catch (e: Exception) {
            "Error: ${e.message}"
        }

        val currentLatitudeStr = if (lastGpsLatitude != null) "$lastGpsLatitude (GPS)" else if (map != null) "${map.cameraPosition?.target?.latitude} (CameraCenter)" else "Unknown"
        val currentLongitudeStr = if (lastGpsLongitude != null) "$lastGpsLongitude (GPS)" else if (map != null) "${map.cameraPosition?.target?.longitude} (CameraCenter)" else "Unknown"

        val cCenterStr = if (map != null) "${map.cameraPosition?.target}" else "None"
        val cBoundsStr = try {
            map?.projection?.visibleRegion?.latLngBounds?.toString() ?: "None"
        } catch (e: Exception) {
            "Error: ${e.message}"
        }

        val mapWidthStr = "${mapView.width} px"
        val mapHeightStr = "${mapView.height} px"

        val mapViewVisibilityStr = when (mapView.visibility) {
            View.VISIBLE -> "VISIBLE"
            View.INVISIBLE -> "INVISIBLE"
            View.GONE -> "GONE"
            else -> "UNKNOWN (${mapView.visibility})"
        }

        val mapLibreIsRenderedStr = (map != null && renderFrameCount > 0).toString()

        val mapPaddingStr = try {
            if (map != null) {
                val getPaddingMethod = map.javaClass.methods.firstOrNull { 
                    it.name == "getPadding" && it.parameterTypes.isEmpty() 
                }
                if (getPaddingMethod != null) {
                    val pad = getPaddingMethod.invoke(map)
                    when (pad) {
                        is DoubleArray -> pad.joinToString(", ")
                        is IntArray -> pad.joinToString(", ")
                        is FloatArray -> pad.joinToString(", ")
                        is Array<*> -> pad.joinToString(", ")
                        else -> pad?.toString() ?: "None"
                    }
                } else {
                    val pField = map.javaClass.declaredFields.firstOrNull { it.name == "padding" }
                    if (pField != null) {
                        pField.isAccessible = true
                        val pad = pField.get(map)
                        when (pad) {
                            is DoubleArray -> pad.joinToString(", ")
                            is IntArray -> pad.joinToString(", ")
                            is FloatArray -> pad.joinToString(", ")
                            is Array<*> -> pad.joinToString(", ")
                            else -> pad?.toString() ?: "None"
                        }
                    } else {
                        "getPadding method not found"
                    }
                }
            } else {
                "None"
            }
        } catch (e: Exception) {
            "Error: ${e.message}"
        }

        fun findRenderSurfaceSize(view: ViewGroup): String {
            for (i in 0 until view.childCount) {
                val child = view.getChildAt(i)
                val className = child.javaClass.simpleName
                if (className.contains("Surface") || className.contains("Texture") || className.contains("Canvas")) {
                    return "${child.width}x${child.height} ($className)"
                }
                if (child is ViewGroup) {
                    val res = findRenderSurfaceSize(child)
                    if (res != "Unknown") return res
                }
            }
            return "Unknown"
        }

        val renderSurfaceSizeStr = findRenderSurfaceSize(mapView).let {
            if (it == "Unknown") "${mapView.width}x${mapView.height} (Fallback)" else it
        }

        val mapboxMapCameraPositionTargetStr = map?.cameraPosition?.target?.toString() ?: "None"
        val mapboxMapProjectionVisibleRegionLatLngBoundsStr = try {
            map?.projection?.visibleRegion?.latLngBounds?.toString() ?: "None"
        } catch (e: Exception) {
            "Error: ${e.message}"
        }

        val scanSection = if (mbtilesScanResult != null) {
            mbtilesScanResult + "\n\n========================================\n\n"
        } else {
            ""
        }

        // Complete geoprocessing and file loading diagnostics
        val hasDem = demSystem.demLoader.hasOfflineDemFiles()
        val demDir = java.io.File(java.io.File(android.os.Environment.getExternalStorageDirectory(), "CyberTrail"), "dem")
        val demFiles = demDir.listFiles { _, name -> 
            name.endsWith(".hgt", ignoreCase = true) || 
            name.endsWith(".bil", ignoreCase = true) || 
            name.endsWith(".tif", ignoreCase = true) || 
            name.endsWith(".tiff", ignoreCase = true) || 
            name.endsWith(".img", ignoreCase = true) 
        }
        val demFilePath = demFiles?.firstOrNull()?.absolutePath ?: ""
        val elevationSource = if (hasDem) "DEM" else if (lastGpsAltitude != null) "GPS" else "Fallback"
        val slopeSource = if (hasDem) "DEM" else "Unavailable"

        var demDiagnosticSection = "--- OFFLINE GEOPROCESSING DIAGNOSTICS ---\n" +
                "DEMLoaded=$hasDem\n" +
                "DEMFilePath=$demFilePath\n" +
                "ElevationSource=$elevationSource\n" +
                "SlopeSource=$slopeSource\n\n"

        // Live Grid Diagnostics
        demDiagnosticSection += "========================================\n" +
                "[DEM 调试面板 / DEM Diagnostic Grid]\n" +
                "========================================\n"
        val terrain = lastTerrainAnalysis
        if (terrain != null && terrain.elevation != null) {
            val aspectDir = when {
                terrain.aspectGISFinal == null || terrain.aspectGISFinal < 0.0 -> "N/A"
                terrain.aspectGISFinal < 22.5 || terrain.aspectGISFinal >= 337.5 -> "N (北)"
                terrain.aspectGISFinal >= 22.5 && terrain.aspectGISFinal < 67.5 -> "NE (东北)"
                terrain.aspectGISFinal >= 67.5 && terrain.aspectGISFinal < 112.5 -> "E (东)"
                terrain.aspectGISFinal >= 112.5 && terrain.aspectGISFinal < 157.5 -> "SE (东南)"
                terrain.aspectGISFinal >= 157.5 && terrain.aspectGISFinal < 202.5 -> "S (南)"
                terrain.aspectGISFinal >= 202.5 && terrain.aspectGISFinal < 247.5 -> "SW (西南)"
                terrain.aspectGISFinal >= 247.5 && terrain.aspectGISFinal < 292.5 -> "W (西)"
                terrain.aspectGISFinal >= 292.5 && terrain.aspectGISFinal < 337.5 -> "NW (西北)"
                else -> "N/A"
            }
            demDiagnosticSection += "当前点：\n" +
                    "  Lat: ${terrain.lat ?: 0.0}°\n" +
                    "  Lon: ${terrain.lon ?: 0.0}°\n\n" +
                    "中心高程: Center=${"%.1f".format(terrain.elevation)}m\n" +
                    "北侧高程: North=${if (terrain.hN != null) "%.1f".format(terrain.hN) else "N/A"}m\n" +
                    "南侧高程: South=${if (terrain.hS != null) "%.1f".format(terrain.hS) else "N/A"}m\n" +
                    "东侧高程: East=${if (terrain.hE != null) "%.1f".format(terrain.hE) else "N/A"}m\n" +
                    "西侧高程: West=${if (terrain.hW != null) "%.1f".format(terrain.hW) else "N/A"}m\n\n" +
                    "dzDx=${if (terrain.dzDx != null) "%.5f".format(terrain.dzDx) else "N/A"}\n" +
                    "dzDy=${if (terrain.dzDy != null) "%.5f".format(terrain.dzDy) else "N/A"}\n\n" +
                    "DownSlopeX=${if (terrain.aspectDownSlopeVectorX != null) "%.5f".format(terrain.aspectDownSlopeVectorX) else "N/A"}\n" +
                    "DownSlopeY=${if (terrain.aspectDownSlopeVectorY != null) "%.5f".format(terrain.aspectDownSlopeVectorY) else "N/A"}\n" +
                    "AspectDownSlopeVectorX=${if (terrain.aspectDownSlopeVectorX != null) "%.5f".format(terrain.aspectDownSlopeVectorX) else "N/A"}\n" +
                    "AspectDownSlopeVectorY=${if (terrain.aspectDownSlopeVectorY != null) "%.5f".format(terrain.aspectDownSlopeVectorY) else "N/A"}\n\n" +
                    "AspectRawMath=${if (terrain.aspectRawMath != null) "%.1f".format(terrain.aspectRawMath) else "N/A"}°\n\n" +
                    "AspectGISFinal=${if (terrain.aspectGISFinal != null) "%.1f".format(terrain.aspectGISFinal) else "N/A"}°\n\n" +
                    "坡度: Slope=${if (terrain.slope != null) "%.1f".format(terrain.slope) else "N/A"}°\n" +
                    "坡向: Aspect=${if (terrain.aspectGISFinal != null && terrain.aspectGISFinal >= 0.0) "%.1f".format(terrain.aspectGISFinal) else "N/A"}° ($aspectDir)\n\n"
            
            val diag = terrain.samplingDiagnostic
            if (diag != null) {
                demDiagnosticSection += "=== DEMSamplingDiagnostic ===\n" +
                        "CenterPixelX: ${diag.centerPixelX}\n" +
                        "CenterPixelY: ${diag.centerPixelY}\n" +
                        "NorthPixelX: ${diag.northPixelX}\n" +
                        "NorthPixelY: ${diag.northPixelY}\n" +
                        "SouthPixelX: ${diag.southPixelX}\n" +
                        "SouthPixelY: ${diag.southPixelY}\n" +
                        "EastPixelX: ${diag.eastPixelX}\n" +
                        "EastPixelY: ${diag.eastPixelY}\n" +
                        "WestPixelX: ${diag.westPixelX}\n" +
                        "WestPixelY: ${diag.westPixelY}\n\n" +
                        "CenterElevation: ${"%.1f".format(diag.centerElevation)}m\n" +
                        "NorthElevation: ${"%.1f".format(diag.northElevation)}m\n" +
                        "SouthElevation: ${"%.1f".format(diag.southElevation)}m\n" +
                        "EastElevation: ${"%.1f".format(diag.eastElevation)}m\n" +
                        "WestElevation: ${"%.1f".format(diag.westElevation)}m\n\n" +
                        "NorthSamePixelAsCenter: ${diag.northIsSamePixelAsCenter}\n" +
                        "SouthSamePixelAsCenter: ${diag.southIsSamePixelAsCenter}\n" +
                        "EastSamePixelAsCenter: ${diag.eastIsSamePixelAsCenter}\n" +
                        "WestSamePixelAsCenter: ${diag.westIsSamePixelAsCenter}\n\n" +
                        "DEMResolutionMeters: ${"%.2f".format(diag.demResolutionMeters)}m\n" +
                        "NeighborOffsetMeters: ${"%.2f".format(diag.neighborOffsetMeters)}m\n\n"
                
                val allDifferent = !diag.northIsSamePixelAsCenter && !diag.southIsSamePixelAsCenter && !diag.eastIsSamePixelAsCenter && !diag.westIsSamePixelAsCenter
                demDiagnosticSection += "邻域采样像元重合判定 / Cell Overlap Confirmation:\n" +
                        "  [North/South/East/West落入不同像元]: $allDifferent\n\n"
            }
        } else {
            demDiagnosticSection += "当前无活跃的测地学分析点。\n\n"
        }

        // Reader Metadata Section
        demDiagnosticSection += "========================================\n" +
                "[GeoTIFF 元数据 / GeoTIFF Reader Metadata]\n" +
                "========================================\n"
        val copReaders = demSystem.demLoader.getCopernicusReaders()
        val alosReaders = demSystem.demLoader.getAlosReaders()
        
        if (copReaders.isEmpty() && alosReaders.isEmpty()) {
            demDiagnosticSection += "无加载的 GeoTIFF 读取器实例。\n\n"
        } else {
            for ((idx, r) in copReaders.withIndex()) {
                demDiagnosticSection += "读取器 [Copernicus #${idx + 1}]:\n" + r.getMetadata() + "\n\n"
            }
            for ((idx, r) in alosReaders.withIndex()) {
                demDiagnosticSection += "读取器 [ALOS #${idx + 1}]:\n" + r.getMetadata() + "\n\n"
            }
        }
        demDiagnosticSection += "========================================\n\n"

        hudDiagnosticCounters.text = scanSection +
                demDiagnosticSection +
                "--- CAMERA FORCED TEST ---\n" +
                cameraForcedTestResult + "\n" +
                "========================================\n\n" +
                "--- NATIVE PIPELINE DIAGNOSTICS ---\n" +
                "onSourceChanged Count: $sourceChangedCount\n" +
                "onDidFinishLoadingStyle Count: $didFinishLoadingStyleCount\n" +
                "onDidBecomeIdle Count: $didBecomeIdleCount\n" +
                "style.isFullyLoaded: $styleFullyLoadedStr\n" +
                "Native Source Count: $styleSourcesSizeStr\n" +
                "Native Layer Count: $styleLayersSizeStr\n" +
                "RasterLayer Visibility: $rlVisibilityStr\n" +
                "RasterLayer Opacity: $rlOpacityStr\n" +
                "RasterLayer RenderPass: $rlRenderPassStr\n" +
                "Source Cache Status:\n$sourceCacheStatusStr\n" +
                "========================================\n\n" +
                "RenderFrame: $renderFrameCount\n" +
                "CameraMove: $cameraMoveCount\n" +
                "SourceChangedCount: $sourceChangedCount\n" +
                "DidFinishLoadingStyleCount: $didFinishLoadingStyleCount\n" +
                "DidBecomeIdleCount: $didBecomeIdleCount\n" +
                "TileRequest: $tileRequestCount\n" +
                "TileFound: $tileFoundCount\n" +
                "TileNotFound: $tileNotFoundCount\n\n" +
                "ServerRequestCount: $serverRequestCountStr\n" +
                "ServerTileRequestCount: $serverTileRequestCountStr\n" +
                "LastRequestPath: $lastRequestPathStr\n" +
                "LastRequestZ: $lastRequestZStr\n" +
                "LastRequestX: $lastRequestXStr\n" +
                "LastRequestY: $lastRequestYStr\n" +
                "LastRequestTimestamp: $lastRequestTimestampStr\n" +
                "ServerStarted: $serverStartedStr\n" +
                "ServerPort: $serverPortStr\n\n" +
                "RequestHistoryList:\n$requestsHistoryStr\n\n" +
                "CurrentLatitude: $currentLatitudeStr\n" +
                "CurrentLongitude: $currentLongitudeStr\n\n" +
                "CameraCenter: $cCenterStr\n" +
                "CameraBounds: $cBoundsStr\n\n" +
                "MapWidth: $mapWidthStr\n" +
                "MapHeight: $mapHeightStr\n\n" +
                "MapViewVisibility: $mapViewVisibilityStr\n\n" +
                "MapLibreIsRendered: $mapLibreIsRenderedStr\n\n" +
                "MapPadding: $mapPaddingStr\n\n" +
                "RenderSurfaceSize: $renderSurfaceSizeStr\n\n" +
                "mapboxMap.cameraPosition.target: $mapboxMapCameraPositionTargetStr\n" +
                "mapboxMap.projection.visibleRegion.latLngBounds: $mapboxMapProjectionVisibleRegionLatLngBoundsStr\n\n" +
                "Offline Layer Config:\n" +
                "source=$lSource\n" +
                "source-layer=$lSourceLayer\n" +
                "minzoom=$lMinZoom\n" +
                "maxzoom=$lMaxZoom\n" +
                "visibility=$lVis\n\n" +
                "SourceExists: $sExists\n" +
                "LayerExists: $lExists\n" +
                "LayerClass: $lClass\n" +
                "LayerSource: $lSource\n" +
                "LayerVisibility: $lVis\n\n" +
                "SourceType:\n$srcType\n\n" +
                "SourceObject:\n$srcObj\n\n" +
                "SourceRuntimeClass:\n$srcRunClass\n\n" +
                "StyleTileUrl:\n$sTileUrl\n\n" +
                "SourceCount: $sCount\n" +
                "LayerCount: $lCount\n\n" +
                "SourceList (Real-time Config):\n$liveSourcesList\n\n" +
                "LayerList (Real-time Config):\n$liveLayersList\n\n" +
                "LayerMinZoom: $lMinZoom\n" +
                "LayerMaxZoom: $lMaxZoom\n\n" +
                "SourceMinZoom: $sMinZoom\n" +
                "SourceMaxZoom: $sMaxZoom\n\n" +
                "RasterTiles:\n$rTiles\n\n" +
                "RasterMinZoom:\n$rMinZoom\n\n" +
                "RasterMaxZoom:\n$rMaxZoom\n\n" +
                "RasterScheme:\n$rScheme\n\n" +
                "RasterAttribution:\n$rAttribution\n\n" +
                "RasterBounds:\n$rBounds\n\n" +
                "RasterSourceAvailableMethods:\n$rMethods\n\n" +
                "CameraZoom: $cZoom\n\n" +
                "FINAL_STYLE_JSON:\n$fStyleJson\n\n" +
                "StyleAttachedToMap: $styleAttachedToMapStr\n\n" +
                "OfflineSourceNull: $offlineSourceNullStr\n" +
                "OfflineSourceClass: $offlineSourceClassStr\n" +
                "OfflineSourceId: $offlineSourceIdStr\n\n" +
                "RasterSourceUrl: $rasterSourceUrlStr\n" +
                "RasterSourceUri: $rasterSourceUriStr\n\n" +
                "style.layers.size: $styleLayersSizeStr\n" +
                "style.sources.size: $styleSourcesSizeStr\n\n" +
                "LayerSourceMatch: $layerSourceMatchStr\n" +
                "LayerSourceValue: $layerSourceValueStr\n\n" +
                "style.toJson():\n$styleToJsonStr\n\n" +
                "ForcedZoomApplied: $forcedZoomApplied\n\n" +
                "HUDHeight: ${hudHeight}px\n" +
                "ScrollY: $scrollY"
    }

    private fun runCameraForcedTest(map: MapboxMap) {
        val buildLog = StringBuilder()
        
        val beforeCenter = map.cameraPosition?.target
        val beforeZoom = map.cameraPosition?.zoom
        
        buildLog.append("BEFORE_FORCE_CAMERA:\n")
        buildLog.append("CameraCenter=${beforeCenter?.latitude ?: "None"},${beforeCenter?.longitude ?: "None"}\n")
        buildLog.append("CameraZoom=${beforeZoom ?: "None"}\n\n")
        
        Log.d("CYBERTRAIL_CAMERA_TEST", "BEFORE_FORCE_CAMERA:\nCameraCenter=${beforeCenter?.latitude ?: "None"},${beforeCenter?.longitude ?: "None"}\nCameraZoom=${beforeZoom ?: "None"}")

        try {
            map.setMinZoomPreference(1.0)
            map.setMaxZoomPreference(18.0)
            
            map.moveCamera(
                com.mapbox.mapboxsdk.camera.CameraUpdateFactory.newLatLngZoom(
                    com.mapbox.mapboxsdk.geometry.LatLng(40.123665, 124.389216),
                    5.0
                )
            )
        } catch (e: Exception) {
            buildLog.append("ERROR_FORCE_CAMERA: ${e.message}\n\n")
            Log.e("CYBERTRAIL_CAMERA_TEST", "Error executing force camera", e)
        }

        cameraForcedTestResult = buildLog.toString()
        runOnUiThread {
            updateDiagnosticHud()
        }

        // Run network tests in worker thread to prevent NetworkOnMainThreadException
        var testConnectionResult = "Not started"
        var manualTileRequestResult = "Not started"
        
        kotlin.concurrent.thread {
            // 9. HTTP Test Endpoint /test
            try {
                val url = java.net.URL("http://127.0.0.1:8080/test")
                val conn = url.openConnection() as java.net.HttpURLConnection
                conn.requestMethod = "GET"
                conn.connectTimeout = 3000
                conn.readTimeout = 3000
                val code = conn.responseCode
                val body = try {
                    conn.inputStream.bufferedReader().use { it.readText() }
                } catch (e: Exception) {
                    "Error reading: ${e.message}"
                }
                testConnectionResult = "Result: SUCCESS\nResponseCode: $code\nResponseBody: $body"
            } catch (e: Exception) {
                testConnectionResult = "Result: FAILED\nError: ${e.message}"
            }

            // 10. Manual Tile Request Test (http://127.0.0.1:8080/5/27/12.png)
            try {
                val url = java.net.URL("http://127.0.0.1:8080/5/27/12.png")
                val conn = url.openConnection() as java.net.HttpURLConnection
                conn.requestMethod = "GET"
                conn.connectTimeout = 3000
                conn.readTimeout = 3000
                val code = conn.responseCode
                val length = conn.contentLength
                val contentType = conn.contentType
                manualTileRequestResult = "Result: SUCCESS\nHTTP状态码: $code\n返回长度: $length\nContent-Type: $contentType"
            } catch (e: Exception) {
                manualTileRequestResult = "Result: FAILED\nError: ${e.message}"
            }
        }

        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        handler.postDelayed({
            val afterCenter = map.cameraPosition?.target
            val afterZoom = map.cameraPosition?.zoom
            val bounds = try {
                map.projection?.visibleRegion?.latLngBounds?.toString() ?: "None"
            } catch (e: Exception) {
                "Error: ${e.message}"
            }
            val visibleRegion = try {
                map.projection?.visibleRegion?.toString() ?: "None"
            } catch (e: Exception) {
                "Error: ${e.message}"
            }
            
            val server = localTileServer
            val sReqCount = server?.requestCountTotal ?: 0
            val sTileReqCount = server?.requestCountTile ?: 0
            
            val buildLogAfter = StringBuilder()
            buildLogAfter.append("AFTER_FORCE_CAMERA:\n")
            buildLogAfter.append("CameraCenter=${afterCenter?.latitude ?: "None"},${afterCenter?.longitude ?: "None"}\n")
            buildLogAfter.append("CameraZoom=${afterZoom ?: "None"}\n\n")
            
            buildLogAfter.append("CameraBounds=$bounds\n")
            buildLogAfter.append("VisibleRegion=$visibleRegion\n\n")
            
            buildLogAfter.append("ServerRequestCount=$sReqCount\n")
            buildLogAfter.append("ServerTileRequestCount=$sTileReqCount\n")
            buildLogAfter.append("TileRequest=$tileRequestCount\n")
            buildLogAfter.append("TileFound=$tileFoundCount\n")
            buildLogAfter.append("TileNotFound=$tileNotFoundCount\n\n")

            val currentStyle = try {
                map.style
            } catch (ex: Exception) {
                null
            }

            // Checklist 2: StyleFullyLoaded
            val styleFullyLoadedStr = try {
                val m = currentStyle?.javaClass?.getMethod("isFullyLoaded")
                if (m != null) {
                    m.invoke(currentStyle)?.toString() ?: "None"
                } else {
                    val m2 = map.javaClass.getMethod("isFullyLoaded")
                    m2.invoke(map)?.toString() ?: "None"
                }
            } catch (e: Exception) {
                "Error: ${e.message}"
            }

            // Checklist 3: MBTILES SOURCE & LAYER EXISTS
            val mbtilesSource = currentStyle?.getSource("offline-mbtiles")
            val offlineLayerObj = currentStyle?.getLayer("offline-layer")
            val sourceExistsBool = (mbtilesSource != null).toString()
            val layerExistsBool = (offlineLayerObj != null).toString()

            // Checklist 4: offline-layer properties
            val rlPropertiesStr = getLayerProperties(offlineLayerObj)

            // Checklist 5: MapboxMap & MapView States
            val mapAndMapViewStatesStr = getMapStates(map, mapView)

            // Checklist 6: Native Tile Pyramid Status
            val tilePyramidStatusStr = getNativeTilePyramidStatus(map, mapView)

            // Checklist 7: offline-mbtiles Source callable methods
            val sourceAllMethodsStr = getSourceAllMethods(mbtilesSource)

            // Checklist 8: offline-mbtiles Source declared fields
            val sourceAllFieldsStr = getSourceAllDeclaredFields(mbtilesSource)

            buildLogAfter.append("========================================\n")
            buildLogAfter.append("MAPLIBRE NATIVE PIPELINE FULL DIAGNOSTICS\n")
            buildLogAfter.append("========================================\n\n")

            buildLogAfter.append("1. EVENTS COUNTERS:\n")
            buildLogAfter.append("SourceChangedCount=$sourceChangedCount\n")
            buildLogAfter.append("DidFinishLoadingStyleCount=$didFinishLoadingStyleCount\n")
            buildLogAfter.append("DidBecomeIdleCount=$didBecomeIdleCount\n\n")

            buildLogAfter.append("2. STYLE LOADING STATUS:\n")
            buildLogAfter.append("StyleFullyLoaded=$styleFullyLoadedStr\n\n")

            buildLogAfter.append("3. MBTILES SOURCE & LAYER EXISTS:\n")
            buildLogAfter.append("offline-mbtiles Source Exists=$sourceExistsBool\n")
            buildLogAfter.append("offline-layer Layer Exists=$layerExistsBool\n\n")

            buildLogAfter.append("4. OFFLINE-LAYER PROPERTIES:\n")
            buildLogAfter.append("$rlPropertiesStr\n")

            buildLogAfter.append("5. MAPBOXMAP & MAPVIEW STATES:\n")
            buildLogAfter.append("$mapAndMapViewStatesStr\n")

            buildLogAfter.append("6. NATIVE TILE PYRAMID STATUS:\n")
            buildLogAfter.append("$tilePyramidStatusStr\n")

            buildLogAfter.append("7. OFFLINE-MBTILES SOURCE METHODS:\n")
            buildLogAfter.append("$sourceAllMethodsStr\n\n")

            buildLogAfter.append("8. OFFLINE-MBTILES SOURCE DECLARED FIELDS:\n")
            buildLogAfter.append("$sourceAllFieldsStr\n\n")

            buildLogAfter.append("9. LOCAL HTTP TEST CONNECTION:\n")
            buildLogAfter.append("$testConnectionResult\n\n")

            buildLogAfter.append("10. MANUAL TILE HTTP REQUEST TEST:\n")
            buildLogAfter.append("$manualTileRequestResult\n")
            buildLogAfter.append("========================================\n\n")
            
            if (sReqCount == 0) {
                val projCenter = try {
                    val latlng = map.projection?.fromScreenLocation(
                        android.graphics.PointF(
                            mapView.width.toFloat() / 2f,
                            mapView.height.toFloat() / 2f
                        )
                    )
                    "${latlng?.latitude ?: "None"},${latlng?.longitude ?: "None"}"
                } catch (e: Exception) {
                    "Error: ${e.message}"
                }
                
                val projBounds = try {
                    val topLeft = map.projection?.fromScreenLocation(android.graphics.PointF(0f, 0f))
                    val bottomRight = map.projection?.fromScreenLocation(
                        android.graphics.PointF(
                            mapView.width.toFloat(),
                            mapView.height.toFloat()
                        )
                    )
                    "TopLeft: $topLeft, BottomRight: $bottomRight"
                } catch (e: Exception) {
                    "Error: ${e.message}"
                }
                
                buildLogAfter.append("ProjectionCenter=$projCenter\n")
                buildLogAfter.append("ProjectionBounds=$projBounds\n\n")
            }
            
            synchronized(this) {
                cameraForcedTestResult = buildLog.toString() + buildLogAfter.toString()
            }
            
            Log.d("CYBERTRAIL_CAMERA_TEST", "AFTER_FORCE_CAMERA:\n" + buildLogAfter.toString())
            
            runOnUiThread {
                updateDiagnosticHud()
            }
        }, 3000)
    }

    private fun getLayerProperties(layer: Any?): String {
        if (layer == null) return "Layer is null"
        val sb = StringBuilder()
        try {
            val methods = layer.javaClass.methods
            for (m in methods) {
                if (m.parameterTypes.isEmpty() && m.returnType != Void.TYPE) {
                    val name = m.name
                    if (name.startsWith("get") || name.startsWith("is") || name == "id" || name == "visibility") {
                        try {
                            val value = m.invoke(layer)
                            sb.append("  Method ${name}(): ${value?.toString()}\n")
                        } catch (e: Exception) {
                            sb.append("  Method ${name}(): Error (${e.message})\n")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            sb.append("Error getting layer properties: ${e.message}\n")
        }
        return sb.toString()
    }

    private fun getMapStates(map: Any?, mapView: Any?): String {
        val sb = StringBuilder()
        if (map != null) {
            sb.append("MapboxMap methods:\n")
            val methods = map.javaClass.methods
            for (m in listOf("isDestroyed", "isFullyLoaded", "getRenderMode", "getStyle", "getCameraPosition")) {
                val matching = methods.firstOrNull { it.name.equals(m, ignoreCase = true) && it.parameterTypes.isEmpty() }
                if (matching != null) {
                    try {
                        sb.append("  ${matching.name}(): ${matching.invoke(map)}\n")
                    } catch (e: Exception) {
                        sb.append("  ${matching.name}(): Error (${e.message})\n")
                    }
                } else {
                    sb.append("  ${m} not found on MapboxMap\n")
                }
            }
        }
        if (mapView != null) {
            sb.append("MapView methods:\n")
            val methods = mapView.javaClass.methods
            for (m in listOf("isDestroyed", "getVisibility", "getWidth", "getHeight", "isFullyLoaded")) {
                val matching = methods.firstOrNull { it.name.equals(m, ignoreCase = true) && it.parameterTypes.isEmpty() }
                if (matching != null) {
                    try {
                        sb.append("  ${matching.name}(): ${matching.invoke(mapView)}\n")
                    } catch (e: Exception) {
                        sb.append("  ${matching.name}(): Error (${e.message})\n")
                    }
                } else {
                    sb.append("  ${m} not found on MapView\n")
                }
            }
        }
        return sb.toString()
    }

    private fun getNativeTilePyramidStatus(map: Any?, mapView: Any?): String {
        val sb = StringBuilder()
        val objectsToScan = listOfNotNull(map, mapView)
        for (obj in objectsToScan) {
            sb.append("Scanning ${obj.javaClass.simpleName} for tile properties:\n")
            try {
                val methods = obj.javaClass.methods
                for (m in methods) {
                    val name = m.name.lowercase()
                    if (name.contains("tile") || name.contains("pyramid") || name.contains("render")) {
                        if (m.parameterTypes.isEmpty() && m.returnType != Void.TYPE) {
                            try {
                                sb.append("  Method ${m.name}(): ${m.invoke(obj)}\n")
                            } catch (e: Exception) {
                                sb.append("  Method ${m.name}(): Error (${e.message})\n")
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                sb.append("Error scanning: ${e.message}\n")
            }
        }
        return sb.toString()
    }

    private fun getSourceAllMethods(source: Any?): String {
        if (source == null) return "Source is null"
        val sb = StringBuilder()
        try {
            val methods = source.javaClass.methods
            for (m in methods) {
                sb.append("method: ${m.name}(${m.parameterTypes.joinToString(",") { p -> p.simpleName }}): ${m.returnType.simpleName}\n")
            }
        } catch (e: Exception) {
            sb.append("Error listing methods: ${e.message}\n")
        }
        return sb.toString()
    }

    private fun getSourceAllDeclaredFields(source: Any?): String {
        if (source == null) return "Source is null"
        val sb = StringBuilder()
        try {
            var klass: Class<*>? = source.javaClass
            while (klass != null) {
                sb.append("Class: ${klass.name}\n")
                val fields = klass.declaredFields
                for (f in fields) {
                    try {
                        f.isAccessible = true
                        val value = f.get(source)
                        sb.append("  fieldName: ${f.name} = $value (Type: ${f.type.simpleName})\n")
                    } catch (e: Exception) {
                        sb.append("  fieldName: ${f.name} = Error accessing field: ${e.message}\n")
                    }
                }
                klass = klass.superclass
            }
        } catch (e: Exception) {
            sb.append("Error listing fields: ${e.message}\n")
        }
        return sb.toString()
    }

    private fun runMbtilesScan() {
        val resultLog = StringBuilder()
        resultLog.append("=== MBTILES COVERAGE SCAN ===\n\n")

        val baseDir = java.io.File(android.os.Environment.getExternalStorageDirectory(), "CyberTrail")
        val mapsDirUpper = java.io.File(baseDir, "Maps")
        val mapsDirLower = java.io.File(baseDir, "maps")
        val mapsDir = if (mapsDirUpper.exists()) mapsDirUpper else mapsDirLower
        val worldFile = java.io.File(mapsDir, "world.mbtiles")
        val mbtilesFile = if (worldFile.exists()) {
            worldFile
        } else {
            val mbtilesFiles = mapsDir.listFiles { _, name -> name.endsWith(".mbtiles") }
            mbtilesFiles?.firstOrNull()
        }
        val mbtilesPath = localTileServer?.mbtilesPath ?: mbtilesFile?.absolutePath

        if (mbtilesPath == null) {
            resultLog.append("Error: 没有检测到离线 MBTiles 数据库文件！\n")
            resultLog.append("Dandong Tile Exists = false\n")
            resultLog.append("“当前数据库仅包含世界概览层，不包含丹东离线影像数据”\n")
            runOnUiThread {
                mbtilesScanResult = resultLog.toString()
                isHudExpanded = true
                updateDiagnosticHud()
            }
            return
        }

        resultLog.append("1. 打开当前加载的 MBTiles 文件:\n$mbtilesPath\n\n")

        val loadedMaps = localTileServer?.getLoadedMaps() ?: emptyList()
        if (loadedMaps.isNotEmpty()) {
            resultLog.append("1.1 本地 TileServer 已就绪，共装载并监控 ${loadedMaps.size} 个离线包:\n")
            var hasPbf = false
            loadedMaps.forEach { mapName ->
                val format = localTileServer?.getMapFormat(mapName) ?: "unknown"
                resultLog.append("  • 离线层 [ $mapName ] (瓦片格式元数据 format: ${format.toUpperCase()})\n")
                if (format.toLowerCase() == "pbf") {
                    hasPbf = true
                    resultLog.append("    ⚠️⚠️⚠️ [异常警报]: 该瓦片库使用的是 VECTOR PBF (矢量) 瓦片格式！\n")
                    resultLog.append("    ⚠️⚠️⚠️ [根本死锁]: CyberTrail 内置极简 Raster 渲染底座，无法解析并绘制 pb/gzip 高精密几何矢量瓦片。\n")
                    resultLog.append("    ⚠️⚠️⚠️ [解决对策]: MapFactory 编译底图时，输出配置必须选择 RASTER PNG/JPEG 离线格式！\n")
                }
            }
            if (hasPbf) {
                resultLog.append("----------------------------------------\n")
                resultLog.append("❌ 【重点诊断结论】:\n")
                resultLog.append("检测到地图包列表中含有 Vector MBTiles (format = pbf / Compression = GZIP) 矢量瓦片，而本系统的 style.json 使用的是 Raster Source 和 Raster Layer 栅格渲染管线，两者天然物理不兼容！这解释了为什么放大或重命名后地图完全空白！\n")
                resultLog.append("----------------------------------------\n")
            }
            resultLog.append("\n")
        }

        var db: android.database.sqlite.SQLiteDatabase? = null
        try {
            db = android.database.sqlite.SQLiteDatabase.openDatabase(
                mbtilesPath, null, android.database.sqlite.SQLiteDatabase.OPEN_READONLY
            )

            // 4. 输出 metadata 表全部内容：SELECT name, value FROM metadata;
            resultLog.append("4. 元数据表 (metadata) 全部内容:\n")
            resultLog.append("[SQL] SELECT name, value FROM metadata;\n")
            var bounds: String? = null
            var center: String? = null
            try {
                val cursorAll = db.rawQuery("SELECT name, value FROM metadata", null)
                while (cursorAll.moveToNext()) {
                    val n = cursorAll.getString(0)
                    val v = cursorAll.getString(1)
                    resultLog.append("  * $n = $v\n")
                    if (n == "bounds") bounds = v
                    if (n == "center") center = v
                }
                cursorAll.close()
            } catch (e: Exception) {
                resultLog.append("  读取 metadata error: ${e.message}\n")
            }
            resultLog.append("\n")

            // 2. 输出 SELECT MIN(zoom_level), MAX(zoom_level) FROM tiles;
            resultLog.append("2. 实际存储层级极值:\n")
            resultLog.append("[SQL] SELECT MIN(zoom_level), MAX(zoom_level) FROM tiles;\n")
            var actualMinZoom: Int? = null
            var actualMaxZoom: Int? = null
            try {
                val cursorMinMax = db.rawQuery("SELECT MIN(zoom_level), MAX(zoom_level) FROM tiles", null)
                if (cursorMinMax.moveToFirst()) {
                    if (!cursorMinMax.isNull(0)) actualMinZoom = cursorMinMax.getInt(0)
                    if (!cursorMinMax.isNull(1)) actualMaxZoom = cursorMinMax.getInt(1)
                }
                cursorMinMax.close()
                resultLog.append("  * MIN(zoom_level): ${actualMinZoom ?: "None"}\n")
                resultLog.append("  * MAX(zoom_level): ${actualMaxZoom ?: "None"}\n")
            } catch (e: Exception) {
                resultLog.append("  查询 zoom_level 极值 error: ${e.message}\n")
            }
            resultLog.append("\n")

            // 3. 输出每个 zoom 层的瓦片数量
            resultLog.append("3. 每个 zoom 层的瓦片数量:\n")
            resultLog.append("[SQL] SELECT zoom_level, COUNT(*) FROM tiles GROUP BY zoom_level ORDER BY zoom_level;\n")
            try {
                val cursorCount = db.rawQuery(
                    "SELECT zoom_level, COUNT(*) FROM tiles GROUP BY zoom_level ORDER BY zoom_level", null
                )
                while (cursorCount.moveToNext()) {
                    val z = cursorCount.getInt(0)
                    val count = cursorCount.getLong(1)
                    resultLog.append("  * Zoom $z: $count 张瓦片\n")
                }
                cursorCount.close()
            } catch (e: Exception) {
                resultLog.append("  查询瓦片数量 error: ${e.message}\n")
            }
            resultLog.append("\n")

            // 5. 如果 metadata 中存在 bounds：判断丹东坐标 (40.123665, 124.389216) 是否位于 bounds 内
            val targetLat = 40.123665
            val targetLon = 124.389216
            resultLog.append("5. 数据库覆盖范围 (bounds) 与丹东坐标判定:\n")
            if (bounds != null) {
                resultLog.append("  * bounds = $bounds\n")
                try {
                    val parts = bounds.split(",").map { it.trim().toDouble() }
                    if (parts.size >= 4) {
                        val minLon = parts[0]
                        val minLat = parts[1]
                        val maxLon = parts[2]
                        val maxLat = parts[3]
                        val latOk = targetLat in minLat..maxLat
                        val lonOk = targetLon in minLon..maxLon
                        if (latOk && lonOk) {
                            resultLog.append("  * 判定结果: YES (丹东坐标 $targetLat, $targetLon 位于 bounds 范围之内)\n")
                        } else {
                            resultLog.append("  * 判定结果: NO (丹东坐标 $targetLat, $targetLon 不在 bounds 范围之内)\n")
                        }
                    } else {
                        resultLog.append("  * 判定结果: 无法判定 (bounds 格式不正确, 非 4 组数字)\n")
                    }
                } catch (e: Exception) {
                    resultLog.append("  * 判定解析 error: ${e.message}\n")
                }
            } else {
                resultLog.append("  * metadata 中不存在 bounds 字段，无法直接分析坐标包含关系\n")
            }
            resultLog.append("\n")

            // 6. 分别计算丹东坐标在 z12, z13, z14 对应 tile x/y，并用 SQL 查询验证
            resultLog.append("6. 丹东瓦片索引计算与精确匹配验证:\n")
            var dandongExists = false
            for (z in listOf(12, 13, 14)) {
                val n = 1 shl z
                // 计算标准 XYZ 瓦片编号
                val x = Math.floor((targetLon + 180.0) / 360.0 * n).toInt()
                val latRad = Math.toRadians(targetLat)
                val y = Math.floor((1.0 - Math.log(Math.tan(latRad) + 1.0 / Math.cos(latRad)) / Math.PI) / 2.0 * n).toInt()
                // TMS y 坐标
                val tmsY = n - 1 - y

                resultLog.append("  * Zoom $z:\n")
                resultLog.append("    - 标准 XYZ 坐标: X=$x, Y=$y\n")
                resultLog.append("    - 对应 TMS 坐标: X=$x, Y=$tmsY\n")

                var countXYZ = 0L
                try {
                    val cursorXYZ = db.rawQuery(
                        "SELECT COUNT(*) FROM tiles WHERE zoom_level = ? AND tile_column = ? AND tile_row = ?",
                        arrayOf(z.toString(), x.toString(), y.toString())
                    )
                    if (cursorXYZ.moveToFirst()) {
                        countXYZ = cursorXYZ.getLong(0)
                    }
                    cursorXYZ.close()
                } catch (e: Exception) {}

                var countTMS = 0L
                try {
                    val cursorTMS = db.rawQuery(
                        "SELECT COUNT(*) FROM tiles WHERE zoom_level = ? AND tile_column = ? AND tile_row = ?",
                        arrayOf(z.toString(), x.toString(), tmsY.toString())
                    )
                    if (cursorTMS.moveToFirst()) {
                        countTMS = cursorTMS.getLong(0)
                    }
                    cursorTMS.close()
                } catch (e: Exception) {}

                val xyzExists = countXYZ > 0
                val tmsExists = countTMS > 0
                if (xyzExists || tmsExists) {
                    dandongExists = true
                }

                resultLog.append("    [SQL] SELECT COUNT(*) FROM tiles WHERE zoom_level=$z AND tile_column=$x AND tile_row=$y (XYZ) -> $countXYZ 张\n")
                resultLog.append("    [SQL] SELECT COUNT(*) FROM tiles WHERE zoom_level=$z AND tile_column=$x AND tile_row=$tmsY (TMS) -> $countTMS 张\n")
            }
            resultLog.append("\n")

            // 7. 输出最终结论
            resultLog.append("7. 最终诊断结论:\n")
            resultLog.append("  A. 当前 MBTiles 是否包含丹东区域: ${if (dandongExists) "Dandong Tile Exists = true" else "Dandong Tile Exists = false"}\n")
            val zoomsRangeStr = if (actualMinZoom != null && actualMaxZoom != null) {
                "$actualMinZoom 到 $actualMaxZoom"
            } else {
                "未知"
            }
            resultLog.append("  B. 当前 MBTiles 实际包含的 zoom 层级: $zoomsRangeStr\n")
            resultLog.append("  C. 结论提示: ")
            if (!dandongExists) {
                resultLog.append("“当前数据库仅包含世界概览层，不包含丹东离线影像数据”\n")
            } else {
                resultLog.append("“当前数据库包含丹东区域离线影像数据”\n")
            }

        } catch (e: Exception) {
            resultLog.append("Database diagnostic error: ${e.message}\n")
        } finally {
            try {
                db?.close()
            } catch (e: Exception) {}
        }

        runOnUiThread {
            mbtilesScanResult = resultLog.toString()
            isHudExpanded = true
            updateDiagnosticHud()
            android.widget.Toast.makeText(this@MapActivity, "MBTiles Scan Done!", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    private fun initTrackRecording() {
        trackDao = AppDatabase.getDatabase(this).trackDao()

        tvTrackStatus = findViewById(R.id.tv_track_status)
        tvTrackStats = findViewById(R.id.tv_track_stats)
        btnTrackStart = findViewById(R.id.btn_track_start)
        btnTrackPause = findViewById(R.id.btn_track_pause)
        btnTrackResume = findViewById(R.id.btn_track_resume)
        btnTrackStop = findViewById(R.id.btn_track_stop)
        btnTrackSave = findViewById(R.id.btn_track_save)

        btnTrackStart.setOnClickListener { startRecordingTrack() }
        btnTrackPause.setOnClickListener { pauseRecordingTrack() }
        btnTrackResume.setOnClickListener { resumeRecordingTrack() }
        btnTrackStop.setOnClickListener { stopRecordingTrack() }
        btnTrackSave.setOnClickListener { manuallySaveTrack() }

        // Bind Track Manager UI
        trackListContainer = findViewById(R.id.drawer_track_list_container)
        val btnOpenTrackManager: TextView = findViewById(R.id.btn_open_track_manager)
        btnOpenTrackManager.setOnClickListener {
            val intent = Intent(this, TrackManagerActivity::class.java)
            startActivity(intent)
        }
        btnTrackImportGpx = findViewById(R.id.btn_track_import_gpx)
        btnTrackImportGpx.setOnClickListener {
            try {
                importGpxLauncher.launch("*/*")
            } catch (e: Exception) {
                Toast.makeText(this, "无法启动文件选择器: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }

        btnPhotoImport = findViewById(R.id.btn_photo_import)
        btnPhotoImport.setOnClickListener {
            try {
                photoImportLauncher.launch("image/*")
            } catch (e: Exception) {
                Toast.makeText(this, "无法启动照片选择器: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }

        cbLayerTrack = findViewById(R.id.cb_layer_track)
        cbLayerWaypoint = findViewById(R.id.cb_layer_waypoint)
        cbLayerPhoto = findViewById(R.id.cb_layer_photo)
        cbLayerRoute = findViewById(R.id.cb_layer_route)

        cbLayerTrack.setOnCheckedChangeListener { _, isChecked ->
            isTrackLayerEnabled = isChecked
            mapboxMap?.let { map ->
                try {
                    val style = map.style
                    if (style != null) {
                        val trackL = style.getLayer("track-layer")
                        val loadedTrackL = style.getLayer("loaded-track-layer")
                        val vis = if (isChecked) com.mapbox.mapboxsdk.style.layers.Property.VISIBLE else com.mapbox.mapboxsdk.style.layers.Property.NONE
                        trackL?.setProperties(com.mapbox.mapboxsdk.style.layers.PropertyFactory.visibility(vis))
                        loadedTrackL?.setProperties(com.mapbox.mapboxsdk.style.layers.PropertyFactory.visibility(vis))
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error toggling track layer visibility", e)
                }
            }
        }

        cbLayerWaypoint.setOnCheckedChangeListener { _, isChecked ->
            isWaypointLayerEnabled = isChecked
            drawWaypointsOnMap()
        }

        cbLayerPhoto.setOnCheckedChangeListener { _, isChecked ->
            isPhotoLayerEnabled = isChecked
            drawPhotoAnchorsOnMap()
        }

        cbLayerRoute.setOnCheckedChangeListener { _, isChecked ->
            isRouteLayerEnabled = isChecked
            drawRouteOnMap()
        }

        // Load track history list on startup
        refreshTrackList()

        // Restore track on startup with option dialog
        if (com.cybertrail.app.gis.TrackForegroundService.isRunning) {
            currentTrackId = com.cybertrail.app.gis.TrackForegroundService.trackId
            trackStatus = com.cybertrail.app.gis.TrackForegroundService.currentStatus
            trackStartTime = com.cybertrail.app.gis.TrackForegroundService.trackStartTime
            trackTotalSeconds = com.cybertrail.app.gis.TrackForegroundService.elapsedSeconds
            synchronized(currentTrackPoints) {
                currentTrackPoints.clear()
                currentTrackPoints.addAll(com.cybertrail.app.gis.TrackForegroundService.currentPoints)
            }
            updateTrackUi()
            drawTrackOnMap()
            updateTrackStatsUi()
        } else {
            dbExecutor.execute {
                val uncompletedFiles = TrackFileHelper.scanUncompletedTrackFiles()
                if (uncompletedFiles.isNotEmpty()) {
                    val file = uncompletedFiles.first()
                    val parsed = TrackFileHelper.readTrackFromJson(file)
                    if (parsed != null) {
                        val track = parsed.first
                        val points = parsed.second
                        val photoAnchors = parsed.third
                        
                        runOnUiThread {
                            AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                                .setTitle("发现未正常结束的轨迹记录")
                                .setMessage("系统检测到上次有未正常结束的轨迹记录，是否恢复并继续记录？\n\n轨迹名称: ${track.name ?: "未命名轨迹"}\n点数: ${points.size}")
                                .setCancelable(false)
                                .setPositiveButton("恢复") { _, _ ->
                                    dbExecutor.execute {
                                        // Make sure it exists/updated in Room
                                        val existing = trackDao.getTrackById(track.id)
                                        if (existing == null) {
                                            trackDao.insertTrack(track)
                                            trackDao.insertTrackPoints(points)
                                            for (anchor in photoAnchors) {
                                                trackDao.insertPhotoAnchor(anchor)
                                            }
                                        } else {
                                            existing.status = track.status
                                            trackDao.updateTrack(existing)
                                        }
                                        
                                        runOnUiThread {
                                            currentTrackId = track.id
                                            trackStatus = track.status
                                            trackStartTime = track.startTime
                                            trackTotalSeconds = (System.currentTimeMillis() - track.startTime) / 1000L
                                            if (trackTotalSeconds < 0) trackTotalSeconds = 0L

                                            currentTrackPoints.clear()
                                            currentTrackPoints.addAll(points)

                                            updateTrackUi()
                                            drawTrackOnMap()
                                            drawPhotoAnchorsOnMap()

                                            // Restart background runnables
                                            mainHandler.removeCallbacks(saveRunnable)
                                            mainHandler.postDelayed(saveRunnable, trackSaveIntervalMs)

                                            mainHandler.removeCallbacks(durationRunnable)
                                            mainHandler.post(durationRunnable)

                                            Toast.makeText(this, "轨迹记录已恢复，继续记录中", Toast.LENGTH_SHORT).show()
                                            refreshTrackList()
                                        }
                                    }
                                }
                                .setNegativeButton("丢弃") { _, _ ->
                                    dbExecutor.execute {
                                        // Delete from disk & database
                                        TrackFileHelper.deleteTrackJsonFile(track.id)
                                        trackDao.deleteTrackById(track.id)
                                        runOnUiThread {
                                            Toast.makeText(this, "未完成的轨迹记录已丢弃", Toast.LENGTH_SHORT).show()
                                            refreshTrackList()
                                        }
                                    }
                                }
                                .show()
                        }
                    }
                } else {
                    // Fallback: Check if active track exists in local Room DB just in case
                    val activeTrack = trackDao.getActiveTrack()
                    if (activeTrack != null) {
                        val points = trackDao.getTrackPoints(activeTrack.id)
                        runOnUiThread {
                            currentTrackId = activeTrack.id
                            trackStatus = activeTrack.status
                            trackStartTime = activeTrack.startTime
                            trackTotalSeconds = (System.currentTimeMillis() - activeTrack.startTime) / 1000L
                            if (trackTotalSeconds < 0) trackTotalSeconds = 0L

                            currentTrackPoints.clear()
                            currentTrackPoints.addAll(points)

                            updateTrackUi()
                            drawTrackOnMap()

                            mainHandler.removeCallbacks(saveRunnable)
                            mainHandler.postDelayed(saveRunnable, trackSaveIntervalMs)

                            mainHandler.removeCallbacks(durationRunnable)
                            mainHandler.post(durationRunnable)
                        }
                    }
                }
            }
        }
    }

    private fun startRecordingTrack() {
        if (trackStatus != "STOPPED") return

        // Request POST_NOTIFICATIONS permission first on API 33+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1004)
                return
            }
        }

        val startTime = System.currentTimeMillis()
        val newTrack = Track(startTime = startTime, status = "RECORDING")

        dbExecutor.execute {
            val id = trackDao.insertTrack(newTrack)
            runOnUiThread {
                currentTrackId = id
                trackStatus = "RECORDING"
                trackStartTime = startTime
                trackTotalSeconds = 0L
                currentTrackPoints.clear()
                pendingPointsToSave.clear()

                Toast.makeText(this, "开始记录轨迹", Toast.LENGTH_SHORT).show()

                // Start Foreground Service
                val serviceIntent = Intent(this, com.cybertrail.app.gis.TrackForegroundService::class.java).apply {
                    action = com.cybertrail.app.gis.TrackForegroundService.ACTION_START
                    putExtra(com.cybertrail.app.gis.TrackForegroundService.EXTRA_TRACK_ID, id)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(serviceIntent)
                } else {
                    startService(serviceIntent)
                }

                updateTrackUi()
                drawTrackOnMap()
            }
        }
    }

    private fun pauseRecordingTrack() {
        if (trackStatus != "RECORDING") return

        val serviceIntent = Intent(this, com.cybertrail.app.gis.TrackForegroundService::class.java).apply {
            action = com.cybertrail.app.gis.TrackForegroundService.ACTION_PAUSE
        }
        startService(serviceIntent)

        trackStatus = "PAUSED"
        Toast.makeText(this, "轨迹记录已暂停", Toast.LENGTH_SHORT).show()
        updateTrackUi()
    }

    private fun resumeRecordingTrack() {
        if (trackStatus != "PAUSED") return

        val serviceIntent = Intent(this, com.cybertrail.app.gis.TrackForegroundService::class.java).apply {
            action = com.cybertrail.app.gis.TrackForegroundService.ACTION_RESUME
        }
        startService(serviceIntent)

        trackStatus = "RECORDING"
        Toast.makeText(this, "恢复轨迹记录", Toast.LENGTH_SHORT).show()
        updateTrackUi()
    }

    private fun stopRecordingTrack() {
        if (trackStatus == "STOPPED") return

        val trackId = currentTrackId
        val serviceIntent = Intent(this, com.cybertrail.app.gis.TrackForegroundService::class.java).apply {
            action = com.cybertrail.app.gis.TrackForegroundService.ACTION_STOP
        }
        startService(serviceIntent)

        val endTime = System.currentTimeMillis()
        dbExecutor.execute {
            val track = trackDao.getTrackById(trackId)
            if (track != null) {
                track.status = "STOPPED"
                track.endTime = endTime
                trackDao.updateTrack(track)

                // Save remaining pending points and generate track json
                val allPoints = trackDao.getTrackPoints(trackId)
                val anchors = trackDao.getPhotoAnchorsForTrack(trackId)
                TrackFileHelper.saveTrackToJson(track, allPoints, anchors)

                runOnUiThread {
                    trackStatus = "STOPPED"
                    Toast.makeText(this, "轨迹记录已结束，已成功保存至离线存储", Toast.LENGTH_LONG).show()

                    // Update final UI
                    tvTrackStatus.text = "🛤️ 轨迹: 已结束"
                    btnTrackStart.visibility = View.VISIBLE
                    btnTrackPause.visibility = View.GONE
                    btnTrackResume.visibility = View.GONE
                    btnTrackStop.visibility = View.GONE

                    // Refresh track history list
                    refreshTrackList()
                }
            }
        }
    }

    private fun savePendingPoints() {
        val pointsToInsert = synchronized(pendingPointsToSave) {
            val copy = ArrayList(pendingPointsToSave)
            pendingPointsToSave.clear()
            copy
        }

        if (pointsToInsert.isNotEmpty()) {
            dbExecutor.execute {
                try {
                    trackDao.insertTrackPoints(pointsToInsert)
                    Log.d("CYBERTRAIL_TRACK", "Successfully saved ${pointsToInsert.size} track points to database.")
                } catch (e: Exception) {
                    Log.e(TAG, "Error saving track points to DB", e)
                }
            }
        }
    }

    private fun recordLocationPoint(location: Location) {
        if (trackStatus != "RECORDING") return
        if (com.cybertrail.app.gis.TrackForegroundService.isRunning) return

        val tp = TrackPoint(
            trackId = currentTrackId,
            latitude = location.latitude,
            longitude = location.longitude,
            elevation = demSystem.getElevation(location.latitude, location.longitude),
            timestamp = location.time,
            speed = if (location.hasSpeed()) location.speed else 0f,
            accuracy = if (location.hasAccuracy()) location.accuracy else 0f
        )

        synchronized(currentTrackPoints) {
            currentTrackPoints.add(tp)
        }

        synchronized(pendingPointsToSave) {
            pendingPointsToSave.add(tp)
        }

        drawTrackOnMap()
        updateTrackStatsUi()
    }

    private fun drawTrackOnMap() {
        val map = mapboxMap ?: return
        val style = try { map.style } catch (e: Exception) { null } ?: return

        val points = synchronized(currentTrackPoints) {
            currentTrackPoints.map { Point.fromLngLat(it.longitude, it.latitude) }
        }
        val lineString = if (points.size >= 2) {
            LineString.fromLngLats(points)
        } else if (points.size == 1) {
            LineString.fromLngLats(listOf(points[0], points[0]))
        } else {
            null
        }

        runOnUiThread {
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
                        PropertyFactory.lineColor(android.graphics.Color.RED),
                        PropertyFactory.lineWidth(4f),
                        PropertyFactory.lineCap(com.mapbox.mapboxsdk.style.layers.Property.LINE_CAP_ROUND),
                        PropertyFactory.lineJoin(com.mapbox.mapboxsdk.style.layers.Property.LINE_JOIN_ROUND)
                    )
                    style.addLayer(layer)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error drawing track on map", e)
            }
        }
    }

    private fun updateTrackUi() {
        runOnUiThread {
            when (trackStatus) {
                "STOPPED" -> {
                    tvTrackStatus.text = "🛤️ 轨迹: 停止记录"
                    tvTrackStatus.setTextColor(0xFF94A3B8.toInt())
                    btnTrackStart.visibility = View.VISIBLE
                    btnTrackPause.visibility = View.GONE
                    btnTrackResume.visibility = View.GONE
                    btnTrackStop.visibility = View.GONE
                    btnTrackSave.visibility = View.GONE
                }
                "RECORDING" -> {
                    tvTrackStatus.text = "🛤️ 轨迹: ● 正在记录"
                    tvTrackStatus.setTextColor(0xFFEF4444.toInt())
                    btnTrackStart.visibility = View.GONE
                    btnTrackPause.visibility = View.VISIBLE
                    btnTrackResume.visibility = View.GONE
                    btnTrackStop.visibility = View.VISIBLE
                    btnTrackSave.visibility = View.VISIBLE
                }
                "PAUSED" -> {
                    tvTrackStatus.text = "🛤️ 轨迹: ⏸ 已暂停"
                    tvTrackStatus.setTextColor(0xFFF59E0B.toInt())
                    btnTrackStart.visibility = View.GONE
                    btnTrackPause.visibility = View.GONE
                    btnTrackResume.visibility = View.VISIBLE
                    btnTrackStop.visibility = View.VISIBLE
                    btnTrackSave.visibility = View.VISIBLE
                }
            }
            updateTrackStatsUi()
        }
    }

    private fun updateTrackStatsUi() {
        runOnUiThread {
            val pointCount = synchronized(currentTrackPoints) { currentTrackPoints.size }
            val hrs = trackTotalSeconds / 3600
            val mins = (trackTotalSeconds % 3600) / 60
            val secs = trackTotalSeconds % 60
            val timeStr = "%02d:%02d:%02d".format(hrs, mins, secs)
            tvTrackStats.text = "点数: $pointCount | 时间: $timeStr"
        }
    }

    private fun importGpxFromUri(uri: Uri) {
        dbExecutor.execute {
            try {
                val tempFile = File(cacheDir, "temp_import.gpx")
                contentResolver.openInputStream(uri)?.use { input ->
                    tempFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }

                val importedTrack = TrackFileHelper.importGpx(tempFile, trackDao)
                runOnUiThread {
                    if (importedTrack != null) {
                        Toast.makeText(this, "成功导入轨迹: ${importedTrack.name ?: "未命名"}", Toast.LENGTH_LONG).show()
                        refreshTrackList()
                    } else {
                        Toast.makeText(this, "导入失败，GPX文件损坏或无轨迹点", Toast.LENGTH_LONG).show()
                    }
                    try { tempFile.delete() } catch(e: Exception) {}
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error importing GPX from URI", e)
                runOnUiThread {
                    Toast.makeText(this, "导入失败: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun refreshTrackList() {
        dbExecutor.execute {
            try {
                val tracks = trackDao.getAllTracks()
                
                // For each track, load points count and distance
                val trackItems = tracks.map { track ->
                    val points = trackDao.getTrackPoints(track.id)
                    val distMeters = calculateDistance(points)
                    TrackItemData(track, points.size, distMeters)
                }

                runOnUiThread {
                    trackListContainer.removeAllViews()
                    if (trackItems.isEmpty()) {
                        val tvEmpty = TextView(this).apply {
                            text = "暂无历史轨迹记录"
                            setTextColor(0xFF94A3B8.toInt())
                            textSize = 12f
                            gravity = android.view.Gravity.CENTER
                            setPadding(0, (16 * resources.displayMetrics.density).toInt(), 0, (16 * resources.displayMetrics.density).toInt())
                        }
                        trackListContainer.addView(tvEmpty)
                        return@runOnUiThread
                    }

                    val density = resources.displayMetrics.density
                    val sdfDate = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())

                    for (item in trackItems) {
                        val track = item.track
                        
                        // Parent Layout
                        val itemLayout = LinearLayout(this).apply {
                            orientation = LinearLayout.VERTICAL
                            val pad = (10 * density).toInt()
                            setPadding(pad, pad, pad, pad)
                            
                            val marginParams = LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT
                            ).apply {
                                setMargins(0, 0, 0, (10 * density).toInt())
                            }
                            layoutParams = marginParams
                            
                            // Visual glassmorphism look
                            background = android.graphics.drawable.GradientDrawable().apply {
                                setColor(0x13FFFFFF) // semi transparent dark/gray
                                setStroke((1 * density).toInt(), 0x22FFFFFF)
                                cornerRadius = 6 * density
                            }
                        }

                        // Title and Status
                        val titleLayout = LinearLayout(this).apply {
                            orientation = LinearLayout.HORIZONTAL
                            layoutParams = LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT
                            )
                        }

                        val tvName = TextView(this).apply {
                            text = track.name ?: "轨迹 #${track.id}"
                            setTextColor(android.graphics.Color.WHITE)
                            textSize = 12f
                            setTypeface(null, android.graphics.Typeface.BOLD)
                            val p = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                            layoutParams = p
                        }
                        titleLayout.addView(tvName)

                        if (loadedTrackId == track.id) {
                            val tvLoaded = TextView(this).apply {
                                text = "● 已加载"
                                setTextColor(0xFF22D3EE.toInt()) // Cyan
                                textSize = 10f
                                setTypeface(null, android.graphics.Typeface.BOLD)
                            }
                            titleLayout.addView(tvLoaded)
                        }
                        itemLayout.addView(titleLayout)

                        // Stats lines
                        val dateStr = sdfDate.format(java.util.Date(track.startTime))
                        val kmStr = "%.2f km".format(item.distanceMeters / 1000f)
                        val tvStats = TextView(this).apply {
                            text = "时间: $dateStr\n点数: ${item.pointCount} | 距离: $kmStr"
                            setTextColor(0xFFCBD5E1.toInt()) // Slate-300
                            textSize = 10f
                            setLineSpacing(2f, 1f)
                            val p = LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT
                            ).apply {
                                setMargins(0, (4 * density).toInt(), 0, (6 * density).toInt())
                            }
                            layoutParams = p
                        }
                        itemLayout.addView(tvStats)

                        // Action Buttons Row 1
                        val btnLayout1 = LinearLayout(this).apply {
                            orientation = LinearLayout.HORIZONTAL
                            layoutParams = LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT
                            ).apply {
                                setMargins(0, 0, 0, (4 * density).toInt())
                            }
                        }

                        // Map show button
                        val mapBtnText = if (loadedTrackId == track.id) "👁 隐藏" else "👁 查看"
                        val mapBtnColor = if (loadedTrackId == track.id) 0xFF0F172A.toInt() else 0xFF22D3EE.toInt()
                        val mapBtnBg = if (loadedTrackId == track.id) 0xFF22D3EE.toInt() else 0x1A22D3EE
                        val mapBtn = createActionButton(mapBtnText, mapBtnColor, mapBtnBg) {
                            toggleLoadedTrackOnMap(track)
                        }
                        btnLayout1.addView(mapBtn)

                        // Rename button
                        val renameBtn = createActionButton("✏️ 重命名", 0xFFF59E0B.toInt(), 0x1AF59E0B) {
                            showRenameTrackDialog(track)
                        }
                        btnLayout1.addView(renameBtn)

                        // Export button
                        val exportBtn = createActionButton("📤 GPX", 0xFF10B981.toInt(), 0x1A10B981) {
                            exportTrackToGpx(track)
                        }
                        btnLayout1.addView(exportBtn)

                        // Action Buttons Row 2
                        val btnLayout2 = LinearLayout(this).apply {
                            orientation = LinearLayout.HORIZONTAL
                            layoutParams = LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT
                            )
                        }

                        // Replay button
                        val replayBtnColor = 0xFF0F172A.toInt()
                        val replayBtnBg = 0xFF22D3EE.toInt() // Cyan theme
                        val replayBtn = createActionButton("▶ 回放", replayBtnColor, replayBtnBg) {
                            startReplay(track)
                        }
                        btnLayout2.addView(replayBtn)

                        // Delete button
                        val deleteBtn = createActionButton("🗑 删除", 0xFFEF4444.toInt(), 0x1AEF4444) {
                            showDeleteTrackDialog(track)
                        }
                        btnLayout2.addView(deleteBtn)

                        itemLayout.addView(btnLayout1)
                        itemLayout.addView(btnLayout2)
                        trackListContainer.addView(itemLayout)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error refreshing track list", e)
            }
        }
    }

    private fun createActionButton(text: String, textColor: Int, bgColor: Int, onClick: () -> Unit): TextView {
        val density = resources.displayMetrics.density
        return TextView(this).apply {
            this.text = text
            this.setTextColor(textColor)
            this.textSize = 9f
            this.gravity = android.view.Gravity.CENTER
            this.setPadding(0, (5 * density).toInt(), 0, (5 * density).toInt())
            
            val params = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                setMargins((2 * density).toInt(), 0, (2 * density).toInt(), 0)
            }
            this.layoutParams = params
            this.background = android.graphics.drawable.GradientDrawable().apply {
                setColor(bgColor)
                cornerRadius = 4 * density
            }
            this.setOnClickListener { onClick() }
        }
    }

    private val photoMarkers = mutableListOf<com.mapbox.mapboxsdk.annotations.Marker>()

    private fun checkCameraPermissionAndLaunch() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 1002)
        } else {
            launchCamera()
        }
    }

    private fun launchCamera() {
        try {
            val storageDir = File("/storage/emulated/0/CyberTrail/Photos")
            if (!storageDir.exists()) {
                storageDir.mkdirs()
            }
            val timeStamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US).format(java.util.Date())
            val file = File(storageDir, "photo_${timeStamp}.jpg")
            photoFile = file
            
            val authority = "com.cybertrail.app.fileprovider"
            val uri = androidx.core.content.FileProvider.getUriForFile(this, authority, file)
            
            takePictureLauncher.launch(uri)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch camera", e)
            Toast.makeText(this, "启动相机失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun generateThumbnail(originalFile: File, anchorId: String): String? {
        try {
            val options = android.graphics.BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            android.graphics.BitmapFactory.decodeFile(originalFile.absolutePath, options)

            val targetSize = 256
            var inSampleSize = 1
            val longestDim = Math.max(options.outWidth, options.outHeight)
            if (longestDim > targetSize) {
                inSampleSize = longestDim / targetSize
            }

            val decodeOptions = android.graphics.BitmapFactory.Options().apply {
                this.inSampleSize = inSampleSize
            }
            val bitmap = android.graphics.BitmapFactory.decodeFile(originalFile.absolutePath, decodeOptions) ?: return null

            val width = bitmap.width
            val height = bitmap.height
            val scale = targetSize.toFloat() / Math.max(width, height)
            val finalWidth = (width * scale).toInt()
            val finalHeight = (height * scale).toInt()

            val scaledBitmap = android.graphics.Bitmap.createScaledBitmap(bitmap, finalWidth, finalHeight, true)
            
            val thumbFile = File(TrackFileHelper.getThumbnailsDirectory(), "${anchorId}.webp")
            val fos = java.io.FileOutputStream(thumbFile)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                scaledBitmap.compress(android.graphics.Bitmap.CompressFormat.WEBP_LOSSY, 85, fos)
            } else {
                scaledBitmap.compress(android.graphics.Bitmap.CompressFormat.WEBP, 85, fos)
            }
            fos.close()
            
            bitmap.recycle()
            if (scaledBitmap != bitmap) {
                scaledBitmap.recycle()
            }
            return thumbFile.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Error generating thumbnail for ${originalFile.name}", e)
            return null
        }
    }

    private fun savePhotoAnchor(file: File) {
        val activeId = currentTrackId
        val boundTrackId = if (activeId != 0L) activeId else null

        val lat = lastGpsLatitude ?: 0.0
        val lon = lastGpsLongitude ?: 0.0
        val alt = lastGpsAltitude
        val time = System.currentTimeMillis()

        if (lat == 0.0 && lon == 0.0) {
            Toast.makeText(this, "暂无 GPS 信号，无法创建照片锚点", Toast.LENGTH_SHORT).show()
            return
        }

        val anchorId = java.util.UUID.randomUUID().toString()
        dbExecutor.execute {
            val thumbPath = generateThumbnail(file, anchorId)
            val anchor = PhotoAnchor(
                id = anchorId,
                trackId = boundTrackId,
                latitude = lat,
                longitude = lon,
                elevation = alt,
                timestamp = time,
                imagePath = file.absolutePath,
                thumbnailPath = thumbPath,
                note = ""
            )
            trackDao.insertPhotoAnchor(anchor)
            
            if (boundTrackId != null) {
                val track = trackDao.getTrackById(boundTrackId)
                val points = trackDao.getTrackPoints(boundTrackId)
                val anchors = trackDao.getPhotoAnchorsForTrack(boundTrackId)
                if (track != null) {
                    TrackFileHelper.saveTrackToJson(track, points, anchors)
                }
            }

            runOnUiThread {
                Toast.makeText(this, "📷 照片锚点记录成功！", Toast.LENGTH_SHORT).show()
                drawPhotoAnchorsOnMap()
            }
        }
    }

    private fun createPhotoIconBitmap(): com.mapbox.mapboxsdk.annotations.Icon {
        val density = resources.displayMetrics.density
        val size = (32 * density).toInt()
        val bitmap = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)

        val paint = android.graphics.Paint().apply {
            isAntiAlias = true
        }

        // Outer Cyber Green circle ring
        paint.color = 0xFF10B981.toInt()
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)

        // Inner Slate circle
        paint.color = 0xFF0F172A.toInt()
        canvas.drawCircle(size / 2f, size / 2f, (size / 2f) - (2 * density), paint)

        // Center emoji
        paint.color = android.graphics.Color.WHITE
        paint.textSize = 14 * density
        paint.textAlign = android.graphics.Paint.Align.CENTER
        
        val fontMetrics = paint.fontMetrics
        val y = (size / 2f) - (fontMetrics.ascent + fontMetrics.descent) / 2f
        canvas.drawText("📷", size / 2f, y, paint)

        val iconFactory = com.mapbox.mapboxsdk.annotations.IconFactory.getInstance(this)
        return iconFactory.fromBitmap(bitmap)
    }

    private fun drawPhotoAnchorsOnMap() {
        val map = mapboxMap ?: return
        runOnUiThread {
            try {
                for (marker in photoMarkers) {
                    map.removeMarker(marker)
                }
                photoMarkers.clear()

                if (!isPhotoLayerEnabled) {
                    return@runOnUiThread
                }

                val activeId = loadedTrackId ?: currentTrackId
                if (activeId != 0L) {
                    dbExecutor.execute {
                        val anchors = trackDao.getPhotoAnchorsForTrack(activeId)
                        val photoIcon = createPhotoIconBitmap()
                        runOnUiThread {
                            for (anchor in anchors) {
                                val markerOptions = com.mapbox.mapboxsdk.annotations.MarkerOptions()
                                    .position(com.mapbox.mapboxsdk.geometry.LatLng(anchor.latitude, anchor.longitude))
                                    .icon(photoIcon)
                                    .title("📷 ${File(anchor.imagePath).name}")
                                    .snippet(anchor.note.ifEmpty { "点击查看照片" })
                                val marker = map.addMarker(markerOptions)
                                photoMarkers.add(marker)
                            }
                        }
                    }
                } else {
                    dbExecutor.execute {
                        val anchors = trackDao.getAllPhotoAnchors()
                        val photoIcon = createPhotoIconBitmap()
                        runOnUiThread {
                            for (anchor in anchors) {
                                val markerOptions = com.mapbox.mapboxsdk.annotations.MarkerOptions()
                                    .position(com.mapbox.mapboxsdk.geometry.LatLng(anchor.latitude, anchor.longitude))
                                    .icon(photoIcon)
                                    .title("📷 ${File(anchor.imagePath).name}")
                                    .snippet(anchor.note.ifEmpty { "点击查看照片" })
                                val marker = map.addMarker(markerOptions)
                                photoMarkers.add(marker)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error drawing photo anchors on map", e)
            }
        }
    }

    private fun initWaypoints() {
        waypointDao = AppDatabase.getDatabase(this).waypointDao()

        panelNavigation = findViewById(R.id.panel_navigation)
        tvNavTitle = findViewById(R.id.tv_nav_title)
        tvNavDistance = findViewById(R.id.tv_nav_distance)
        tvNavBearing = findViewById(R.id.tv_nav_bearing)
        tvNavTime = findViewById(R.id.tv_nav_time)
        btnNavCancel = findViewById(R.id.btn_nav_cancel)

        btnNavCancel.setOnClickListener {
            stopNavigation()
        }

        val btnOpenWaypointManager: View = findViewById(R.id.btn_open_waypoint_manager)
        btnOpenWaypointManager.setOnClickListener {
            val intent = Intent(this, WaypointManagerActivity::class.java)
            startActivity(intent)
        }

        val btnOpenRouteManager: View = findViewById(R.id.btn_open_route_manager)
        btnOpenRouteManager.setOnClickListener {
            val intent = Intent(this, RouteManagerActivity::class.java)
            startActivity(intent)
        }
    }

    private fun getEmojiForIconType(iconType: String): String {
        return when (iconType) {
            "NORMAL" -> "📍"
            "CAMP" -> "⛺"
            "WATER" -> "🚰"
            "SUMMIT" -> "🏔"
            "DANGER" -> "⚠"
            "PHOTO" -> "📷"
            "PARKING" -> "🚗"
            else -> "📍"
        }
    }

    private fun createWaypointIconBitmap(iconType: String): com.mapbox.mapboxsdk.annotations.Icon {
        val density = resources.displayMetrics.density
        val size = (32 * density).toInt()
        val bitmap = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)

        val paint = android.graphics.Paint().apply {
            isAntiAlias = true
        }

        // Outer Cyber Green circle ring
        paint.color = 0xFFF59E0B.toInt() // Amber for Waypoints
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)

        // Inner Slate circle
        paint.color = 0xFF0F172A.toInt()
        canvas.drawCircle(size / 2f, size / 2f, (size / 2f) - (2 * density), paint)

        // Center emoji
        paint.color = android.graphics.Color.WHITE
        paint.textSize = 14 * density
        paint.textAlign = android.graphics.Paint.Align.CENTER
        
        val fontMetrics = paint.fontMetrics
        val y = (size / 2f) - (fontMetrics.ascent + fontMetrics.descent) / 2f
        canvas.drawText(getEmojiForIconType(iconType), size / 2f, y, paint)

        val iconFactory = com.mapbox.mapboxsdk.annotations.IconFactory.getInstance(this)
        return iconFactory.fromBitmap(bitmap)
    }

    private val waypointMarkers = mutableListOf<com.mapbox.mapboxsdk.annotations.Marker>()

    private fun drawWaypointsOnMap() {
        val map = mapboxMap ?: return
        runOnUiThread {
            try {
                for (marker in waypointMarkers) {
                    map.removeMarker(marker)
                }
                waypointMarkers.clear()

                if (!isWaypointLayerEnabled) {
                    return@runOnUiThread
                }

                dbExecutor.execute {
                    val waypoints = waypointDao.getAllWaypoints()
                    runOnUiThread {
                        for (wp in waypoints) {
                            val markerIcon = createWaypointIconBitmap(wp.iconType)
                            val markerOptions = com.mapbox.mapboxsdk.annotations.MarkerOptions()
                                .position(com.mapbox.mapboxsdk.geometry.LatLng(wp.latitude, wp.longitude))
                                .icon(markerIcon)
                                .title(wp.name)
                                .snippet(wp.description ?: "点击查看详情")
                            val marker = map.addMarker(markerOptions)
                            waypointMarkers.add(marker)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error drawing waypoints on map", e)
            }
        }
    }

    private fun showCreateWaypointDialog(latLng: com.mapbox.mapboxsdk.geometry.LatLng) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_create_waypoint, null)
        val tvCoords: TextView = dialogView.findViewById(R.id.tv_waypoint_coords)
        val etName: EditText = dialogView.findViewById(R.id.et_waypoint_name)
        val etDesc: EditText = dialogView.findViewById(R.id.et_waypoint_desc)
        val spIcon: android.widget.Spinner = dialogView.findViewById(R.id.sp_waypoint_icon)

        // Get elevation from demSystem
        val elevation = demSystem.getElevation(latLng.latitude, latLng.longitude)
        tvCoords.text = String.format(
            java.util.Locale.US,
            "纬度: %.6f | 经度: %.6f\n估算海拔: %.1f m",
            latLng.latitude,
            latLng.longitude,
            elevation
        )

        // Setup spinner items
        val spinnerItems = listOf(
            "📍 默认 (NORMAL)",
            "⛺ 营地 (CAMP)",
            "🚰 水源 (WATER)",
            "🏔 山峰 (SUMMIT)",
            "⚠ 危险 (DANGER)",
            "📷 照片 (PHOTO)",
            "🚗 车位 (PARKING)"
        )
        val iconTypes = listOf("NORMAL", "CAMP", "WATER", "SUMMIT", "DANGER", "PHOTO", "PARKING")

        val adapter = android.widget.ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            spinnerItems
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spIcon.adapter = adapter

        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setView(dialogView)
            .setPositiveButton("保存") { dialog, _ ->
                val name = etName.text.toString().trim().ifEmpty { "未命名航点" }
                val desc = etDesc.text.toString().trim().ifEmpty { null }
                val iconType = iconTypes[spIcon.selectedItemPosition]
                val now = System.currentTimeMillis()
                val id = java.util.UUID.randomUUID().toString()

                val waypoint = com.cybertrail.app.db.WaypointEntity(
                    id = id,
                    name = name,
                    latitude = latLng.latitude,
                    longitude = latLng.longitude,
                    elevation = elevation,
                    description = desc,
                    iconType = iconType,
                    favorite = false,
                    createTime = now
                )

                dbExecutor.execute {
                    waypointDao.insertWaypoint(waypoint)
                    runOnUiThread {
                        Toast.makeText(this@MapActivity, "航点保存成功！", Toast.LENGTH_SHORT).show()
                        drawWaypointsOnMap()
                    }
                }
                dialog.dismiss()
            }
            .setNegativeButton("取消") { dialog, _ ->
                dialog.dismiss()
            }
            .create()
            .show()
    }

    private fun showWaypointDetailDialog(waypoint: com.cybertrail.app.db.WaypointEntity) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_waypoint_detail, null)
        val tvEmoji: TextView = dialogView.findViewById(R.id.tv_detail_icon_emoji)
        val tvName: TextView = dialogView.findViewById(R.id.tv_detail_name)
        val tvDesc: TextView = dialogView.findViewById(R.id.tv_detail_desc)
        val tvCoords: TextView = dialogView.findViewById(R.id.tv_detail_coords)
        val tvEle: TextView = dialogView.findViewById(R.id.tv_detail_ele)
        val tvTime: TextView = dialogView.findViewById(R.id.tv_detail_time)
        val btnFav: TextView = dialogView.findViewById(R.id.btn_detail_favorite)

        val btnNavigate: TextView = dialogView.findViewById(R.id.btn_detail_navigate)
        val btnEdit: TextView = dialogView.findViewById(R.id.btn_detail_edit)
        val btnDelete: TextView = dialogView.findViewById(R.id.btn_detail_delete)
        val btnClose: TextView = dialogView.findViewById(R.id.btn_detail_close)

        // Bind data
        tvEmoji.text = getEmojiForIconType(waypoint.iconType)
        tvName.text = waypoint.name
        tvDesc.text = waypoint.description ?: "无备注信息"
        tvCoords.text = String.format(java.util.Locale.US, "%.6f, %.6f", waypoint.latitude, waypoint.longitude)
        tvEle.text = waypoint.elevation?.let { String.format(java.util.Locale.US, "%.1f m", it) } ?: "-- m"
        
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
        tvTime.text = sdf.format(java.util.Date(waypoint.createTime))

        // Favorite state update UI helper
        fun updateFavoriteButtonState(isFav: Boolean) {
            btnFav.text = if (isFav) "★" else "☆"
        }
        updateFavoriteButtonState(waypoint.favorite)

        var dialog: AlertDialog? = null

        btnFav.setOnClickListener {
            waypoint.favorite = !waypoint.favorite
            updateFavoriteButtonState(waypoint.favorite)
            dbExecutor.execute {
                waypointDao.updateWaypoint(waypoint)
                runOnUiThread {
                    Toast.makeText(this@MapActivity, if (waypoint.favorite) "已加入收藏" else "已取消收藏", Toast.LENGTH_SHORT).show()
                }
            }
        }

        btnNavigate.setOnClickListener {
            startNavigation(waypoint)
            dialog?.dismiss()
        }

        btnEdit.setOnClickListener {
            dialog?.dismiss()
            showEditWaypointDialog(waypoint)
        }

        btnDelete.setOnClickListener {
            AlertDialog.Builder(this@MapActivity, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("确认删除")
                .setMessage("确定要删除此航点吗？")
                .setPositiveButton("删除") { _, _ ->
                    dbExecutor.execute {
                        waypointDao.deleteWaypointById(waypoint.id)
                        runOnUiThread {
                            Toast.makeText(this@MapActivity, "航点已删除", Toast.LENGTH_SHORT).show()
                            if (navigationTargetWaypoint?.id == waypoint.id) {
                                stopNavigation()
                            }
                            drawWaypointsOnMap()
                        }
                    }
                    dialog?.dismiss()
                }
                .setNegativeButton("取消", null)
                .show()
        }

        btnClose.setOnClickListener {
            dialog?.dismiss()
        }

        dialog = AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setView(dialogView)
            .create()
        dialog.show()
    }

    private fun showEditWaypointDialog(waypoint: com.cybertrail.app.db.WaypointEntity) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_create_waypoint, null)
        val tvTitle: TextView = dialogView.findViewById(R.id.tv_waypoint_coords)
        val etName: EditText = dialogView.findViewById(R.id.et_waypoint_name)
        val etDesc: EditText = dialogView.findViewById(R.id.et_waypoint_desc)
        val spIcon: android.widget.Spinner = dialogView.findViewById(R.id.sp_waypoint_icon)

        // Setup title
        ((dialogView as? ViewGroup)?.getChildAt(0) as? TextView)?.text = "✏ 编辑航点"
        tvTitle.text = String.format(
            java.util.Locale.US,
            "纬度: %.6f | 经度: %.6f\n估算海拔: %.1f m",
            waypoint.latitude,
            waypoint.longitude,
            waypoint.elevation ?: 0.0
        )

        etName.setText(waypoint.name)
        etDesc.setText(waypoint.description ?: "")

        val spinnerItems = listOf(
            "📍 默认 (NORMAL)",
            "⛺ 营地 (CAMP)",
            "🚰 水源 (WATER)",
            "🏔 山峰 (SUMMIT)",
            "⚠ 危险 (DANGER)",
            "📷 照片 (PHOTO)",
            "🚗 车位 (PARKING)"
        )
        val iconTypes = listOf("NORMAL", "CAMP", "WATER", "SUMMIT", "DANGER", "PHOTO", "PARKING")

        val adapter = android.widget.ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            spinnerItems
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spIcon.adapter = adapter

        val idx = iconTypes.indexOf(waypoint.iconType).coerceAtLeast(0)
        spIcon.setSelection(idx)

        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setView(dialogView)
            .setPositiveButton("保存") { dialog, _ ->
                val name = etName.text.toString().trim().ifEmpty { "未命名航点" }
                val desc = etDesc.text.toString().trim().ifEmpty { null }
                val iconType = iconTypes[spIcon.selectedItemPosition]

                val updatedWaypoint = waypoint.copy(
                    name = name,
                    description = desc,
                    iconType = iconType
                )

                dbExecutor.execute {
                    waypointDao.updateWaypoint(updatedWaypoint)
                    runOnUiThread {
                        Toast.makeText(this@MapActivity, "航点已更新！", Toast.LENGTH_SHORT).show()
                        drawWaypointsOnMap()
                        if (navigationTargetWaypoint?.id == updatedWaypoint.id) {
                            navigationTargetWaypoint = updatedWaypoint
                            val lastLoc = getLastKnownLocation()
                            updateNavigationHud(lastLoc)
                        }
                    }
                }
                dialog.dismiss()
            }
            .setNegativeButton("取消") { dialog, _ ->
                dialog.dismiss()
            }
            .create()
            .show()
    }

    private fun startNavigation(waypoint: com.cybertrail.app.db.WaypointEntity) {
        navigationTargetWaypoint = waypoint
        panelNavigation.visibility = View.VISIBLE
        val lastLoc = getLastKnownLocation()
        updateNavigationHud(lastLoc)
        
        mapboxMap?.let { map ->
            val bounds = com.mapbox.mapboxsdk.geometry.LatLngBounds.Builder()
            lastLoc?.let {
                bounds.include(com.mapbox.mapboxsdk.geometry.LatLng(it.latitude, it.longitude))
            }
            bounds.include(com.mapbox.mapboxsdk.geometry.LatLng(waypoint.latitude, waypoint.longitude))
            
            try {
                map.animateCamera(
                    com.mapbox.mapboxsdk.camera.CameraUpdateFactory.newLatLngBounds(bounds.build(), 150),
                    800
                )
            } catch (e: Exception) {
                map.animateCamera(
                    com.mapbox.mapboxsdk.camera.CameraUpdateFactory.newLatLng(
                        com.mapbox.mapboxsdk.geometry.LatLng(waypoint.latitude, waypoint.longitude)
                    ),
                    400
                )
            }
        }
        Toast.makeText(this, "开始导航至: ${waypoint.name}", Toast.LENGTH_SHORT).show()
    }

    private fun stopNavigation() {
        navigationTargetWaypoint = null
        isRouteNavigationActive = false
        activeRoute = null
        routeWaypoints.clear()
        panelNavigation.visibility = View.GONE
        drawRouteOnMap()
        Toast.makeText(this, "导航已结束", Toast.LENGTH_SHORT).show()
    }

    private fun drawRouteOnMap() {
        val style = mapboxMap?.style ?: return
        
        if (!isRouteLayerEnabled || activeRoute == null || routeWaypoints.isEmpty()) {
            runOnUiThread {
                try {
                    val source = style.getSource("route-source") as? GeoJsonSource
                    source?.setGeoJson(FeatureCollection.fromFeatures(emptyArray()))
                } catch (e: Exception) {
                    Log.e(TAG, "Error clearing route from map", e)
                }
            }
            return
        }

        val points = routeWaypoints.map {
            Point.fromLngLat(it.longitude, it.latitude)
        }

        val lineString = if (points.size >= 2) {
            LineString.fromLngLats(points)
        } else {
            null
        }

        runOnUiThread {
            try {
                var source = style.getSource("route-source") as? GeoJsonSource
                if (source == null) {
                    source = GeoJsonSource("route-source")
                    style.addSource(source)
                }

                if (lineString != null) {
                    source.setGeoJson(FeatureCollection.fromFeature(Feature.fromGeometry(lineString)))
                } else {
                    source.setGeoJson(FeatureCollection.fromFeatures(emptyArray()))
                }

                var layer = style.getLayer("route-layer") as? LineLayer
                if (layer == null) {
                    layer = LineLayer("route-layer", "route-source")
                    layer.setProperties(
                        PropertyFactory.lineColor(android.graphics.Color.BLUE),
                        PropertyFactory.lineWidth(5f),
                        PropertyFactory.lineCap(com.mapbox.mapboxsdk.style.layers.Property.LINE_CAP_ROUND),
                        PropertyFactory.lineJoin(com.mapbox.mapboxsdk.style.layers.Property.LINE_JOIN_ROUND)
                    )
                    style.addLayer(layer)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error drawing route on map", e)
            }
        }
    }

    private fun loadRouteAndPlot(routeId: String, startNav: Boolean = false) {
        dbExecutor.execute {
            val route = AppDatabase.getDatabase(this).routeDao().getRouteById(routeId)
            if (route != null) {
                val waypointDao = AppDatabase.getDatabase(this).waypointDao()
                val wpIds = route.getWaypointIdList()
                val tempWaypoints = mutableListOf<WaypointEntity>()
                for (id in wpIds) {
                    val wp = waypointDao.getWaypointById(id)
                    if (wp != null) {
                        tempWaypoints.add(wp)
                    }
                }

                runOnUiThread {
                    activeRoute = route
                    routeWaypoints.clear()
                    routeWaypoints.addAll(tempWaypoints)
                    
                    if (routeWaypoints.isNotEmpty()) {
                        val bounds = com.mapbox.mapboxsdk.geometry.LatLngBounds.Builder()
                        for (wp in routeWaypoints) {
                            bounds.include(com.mapbox.mapboxsdk.geometry.LatLng(wp.latitude, wp.longitude))
                        }
                        try {
                            mapboxMap?.animateCamera(
                                com.mapbox.mapboxsdk.camera.CameraUpdateFactory.newLatLngBounds(bounds.build(), 150),
                                800
                            )
                        } catch (e: Exception) {
                            mapboxMap?.animateCamera(
                                com.mapbox.mapboxsdk.camera.CameraUpdateFactory.newLatLng(
                                    com.mapbox.mapboxsdk.geometry.LatLng(routeWaypoints[0].latitude, routeWaypoints[0].longitude)
                                ),
                                800
                            )
                        }
                    }

                    drawRouteOnMap()

                    if (startNav && routeWaypoints.size >= 2) {
                        AlertDialog.Builder(this@MapActivity, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                            .setTitle("🗺️ 路线已载入")
                            .setMessage("检测到路线「${route.name}」，是否现在开启路线导航？")
                            .setPositiveButton("开启导航") { _, _ ->
                                startRouteNavigation()
                            }
                            .setNegativeButton("仅在地图查看", null)
                            .show()
                    } else if (startNav) {
                        Toast.makeText(this@MapActivity, "该路线航点不足，无法启动导航", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun startRouteNavigation() {
        if (routeWaypoints.size < 2) return
        isRouteNavigationActive = true
        routeCurrentTargetIndex = 0
        resetArrivalAlertFlags()
        panelNavigation.visibility = View.VISIBLE
        
        val firstWp = routeWaypoints[0]
        Toast.makeText(this, "路线导航已开启，首个目标航点: ${firstWp.name}", Toast.LENGTH_LONG).show()

        val lastLoc = getLastKnownLocation()
        updateNavigationHud(lastLoc)
    }

    private fun checkRouteWaypointArrival(distanceMeters: Float, targetWp: WaypointEntity) {
        if (!isRouteNavigationActive) return

        if (distanceMeters <= 10f) {
            triggerArrivalAlert("已到达航点: ${targetWp.name}")
            moveToNextRouteWaypoint()
        } else if (distanceMeters <= 50f && !alerted50m) {
            alerted50m = true
            triggerArrivalAlert("距离航点 ${targetWp.name} 仅 50米")
        } else if (distanceMeters <= 100f && !alerted100m) {
            alerted100m = true
            triggerArrivalAlert("距离航点 ${targetWp.name} 仅 100米")
        }
    }

    private fun triggerArrivalAlert(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        
        try {
            val v = getSystemService(android.content.Context.VIBRATOR_SERVICE) as? android.os.Vibrator
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                v?.vibrate(android.os.VibrationEffect.createOneShot(500, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                v?.vibrate(500)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        try {
            val notification = android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION)
            val r = android.media.RingtoneManager.getRingtone(applicationContext, notification)
            r.play()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun moveToNextRouteWaypoint() {
        routeCurrentTargetIndex++
        if (routeCurrentTargetIndex >= routeWaypoints.size) {
            isRouteNavigationActive = false
            runOnUiThread {
                panelNavigation.visibility = View.GONE
                AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                    .setTitle("🏁 路线已完成！")
                    .setMessage("恭喜您，已成功到达路线的终点航点！")
                    .setPositiveButton("确定", null)
                    .show()
            }
        } else {
            resetArrivalAlertFlags()
            runOnUiThread {
                val nextWp = routeWaypoints[routeCurrentTargetIndex]
                Toast.makeText(this, "正在导向下一个航点: ${nextWp.name}", Toast.LENGTH_SHORT).show()
                val lastLoc = getLastKnownLocation()
                updateNavigationHud(lastLoc)
            }
        }
    }

    private fun resetArrivalAlertFlags() {
        alerted100m = false
        alerted50m = false
    }

    private fun getArrowForBearing(bearing: Float): String {
        return when {
            bearing >= 337.5 || bearing < 22.5 -> "⬆"
            bearing >= 22.5 && bearing < 67.5 -> "↗"
            bearing >= 67.5 && bearing < 112.5 -> "➔"
            bearing >= 112.5 && bearing < 157.5 -> "↘"
            bearing >= 157.5 && bearing < 202.5 -> "⬇"
            bearing >= 202.5 && bearing < 247.5 -> "↙"
            bearing >= 247.5 && bearing < 292.5 -> "⬅"
            bearing >= 292.5 && bearing < 337.5 -> "↖"
            else -> "⬆"
        }
    }

    private fun getLastKnownLocation(): Location? {
        val lastLat = lastGpsLatitude
        val lastLon = lastGpsLongitude
        if (lastLat != null && lastLon != null) {
            return Location("").apply {
                latitude = lastLat
                longitude = lastLon
            }
        }
        return null
    }

    private fun updateNavigationHud(currentLocation: Location?) {
        val target = if (isRouteNavigationActive) {
            if (routeCurrentTargetIndex < routeWaypoints.size) {
                routeWaypoints[routeCurrentTargetIndex]
            } else {
                null
            }
        } else {
            navigationTargetWaypoint
        }

        if (target == null) {
            return
        }

        if (currentLocation == null) {
            tvNavDistance.text = "-- m"
            tvNavBearing.text = "--°"
            tvNavTime.text = "-- 分钟"
            return
        }

        val results = FloatArray(1)
        Location.distanceBetween(
            currentLocation.latitude, currentLocation.longitude,
            target.latitude, target.longitude,
            results
        )
        val distanceMeters = results[0]
        val distanceKm = distanceMeters / 1000.0

        val targetLoc = Location("").apply {
            latitude = target.latitude
            longitude = target.longitude
        }
        var bearing = currentLocation.bearingTo(targetLoc)
        if (bearing < 0) {
            bearing += 360f
        }

        val directionStr = getDirectionString(bearing)
        val arrowStr = getArrowForBearing(bearing)

        val walkingSpeedKmh = 4.0
        val timeHours = distanceKm / walkingSpeedKmh
        val timeMinutes = (timeHours * 60).toInt().coerceAtLeast(1)

        if (isRouteNavigationActive) {
            val routeName = activeRoute?.name ?: "路线"
            val remainingWps = routeWaypoints.size - routeCurrentTargetIndex
            tvNavTitle.text = "🧭 $routeName (剩余航点：$remainingWps) ➔ ${target.name}"
            
            // Trigger arrival warning checks on background/foreground thread
            checkRouteWaypointArrival(distanceMeters, target)
        } else {
            tvNavTitle.text = "🧭 正在导航至: ${target.name}"
        }

        tvNavDistance.text = if (distanceKm < 1.0) {
            String.format(java.util.Locale.US, "%.0f m", distanceMeters)
        } else {
            String.format(java.util.Locale.US, "%.2f km", distanceKm)
        }
        tvNavBearing.text = String.format(java.util.Locale.US, "%s %.0f° %s", arrowStr, bearing, directionStr)
        tvNavTime.text = "$timeMinutes 分钟"
    }

    private fun getDirectionString(bearing: Float): String {
        return when {
            bearing >= 337.5 || bearing < 22.5 -> "北"
            bearing >= 22.5 && bearing < 67.5 -> "东北"
            bearing >= 67.5 && bearing < 112.5 -> "东"
            bearing >= 112.5 && bearing < 157.5 -> "东南"
            bearing >= 157.5 && bearing < 202.5 -> "南"
            bearing >= 202.5 && bearing < 247.5 -> "西南"
            bearing >= 247.5 && bearing < 292.5 -> "西"
            bearing >= 292.5 && bearing < 337.5 -> "西北"
            else -> ""
        }
    }

    private fun showPhotoAnchorDialog(anchor: PhotoAnchor) {
        val dialog = AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .create()

        val density = resources.displayMetrics.density
        val pad = (16 * density).toInt()
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(0xE60F172A.toInt())
                setStroke((1 * density).toInt(), 0xFF10B981.toInt())
                cornerRadius = 8 * density
            }
        }

        val tvTitle = TextView(this).apply {
            text = "📷 照片航点信息"
            setTextColor(0xFF10B981.toInt())
            textSize = 16f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, (12 * density).toInt())
        }
        container.addView(tvTitle)

        val imageView = android.widget.ImageView(this).apply {
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (180 * density).toInt()
            ).apply {
                setMargins(0, 0, 0, (12 * density).toInt())
            }
            layoutParams = params
            scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
            
            val path = anchor.thumbnailPath ?: anchor.imagePath
            val file = File(path)
            if (file.exists()) {
                val bitmap = android.graphics.BitmapFactory.decodeFile(file.absolutePath)
                setImageBitmap(bitmap)
            } else {
                val orig = File(anchor.imagePath)
                if (orig.exists()) {
                    val bitmap = android.graphics.BitmapFactory.decodeFile(orig.absolutePath)
                    setImageBitmap(bitmap)
                } else {
                    setImageResource(android.R.drawable.ic_menu_gallery)
                }
            }
        }
        container.addView(imageView)

        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
        val dateStr = sdf.format(java.util.Date(anchor.timestamp))
        val eleStr = if (anchor.elevation != null) "%.1f m".format(anchor.elevation) else "无"
        val noteDisplay = if (anchor.note.isNotEmpty()) anchor.note else "无"

        val tvMeta = TextView(this).apply {
            text = "文件名: ${File(anchor.imagePath).name}\n时间: $dateStr\n经度: ${anchor.longitude}\n纬度: ${anchor.latitude}\n海拔: $eleStr\n备注: $noteDisplay"
            setTextColor(android.graphics.Color.WHITE)
            textSize = 11f
            typeface = android.graphics.Typeface.MONOSPACE
            setLineSpacing(4f, 1f)
            setPadding(0, 0, 0, (16 * density).toInt())
        }
        container.addView(tvMeta)

        val actionLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, (12 * density).toInt(), 0, 0)
            }
        }

        val btnViewOriginal = TextView(this).apply {
            text = "🔎 原图"
            setTextColor(0xFF0F172A.toInt())
            textSize = 11f
            setTypeface(null, android.graphics.Typeface.BOLD)
            gravity = android.view.Gravity.CENTER
            setPadding((10 * density).toInt(), (8 * density).toInt(), (10 * density).toInt(), (8 * density).toInt())
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(0xFF10B981.toInt())
                cornerRadius = 4 * density
            }
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                setMargins(0, 0, (6 * density).toInt(), 0)
            }
            setOnClickListener {
                dialog.dismiss()
                showFullScreenImage(anchor.imagePath)
            }
        }

        val btnEditNote = TextView(this).apply {
            text = "✏️ 备注"
            setTextColor(0xFF0F172A.toInt())
            textSize = 11f
            setTypeface(null, android.graphics.Typeface.BOLD)
            gravity = android.view.Gravity.CENTER
            setPadding((10 * density).toInt(), (8 * density).toInt(), (10 * density).toInt(), (8 * density).toInt())
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(0xFFF59E0B.toInt())
                cornerRadius = 4 * density
            }
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                setMargins(0, 0, (6 * density).toInt(), 0)
            }
            setOnClickListener {
                dialog.dismiss()
                showEditNoteDialog(anchor)
            }
        }

        val btnDelete = TextView(this).apply {
            text = "🗑 删除"
            setTextColor(android.graphics.Color.WHITE)
            textSize = 11f
            setTypeface(null, android.graphics.Typeface.BOLD)
            gravity = android.view.Gravity.CENTER
            setPadding((10 * density).toInt(), (8 * density).toInt(), (10 * density).toInt(), (8 * density).toInt())
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(0xFFEF4444.toInt())
                cornerRadius = 4 * density
            }
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                setMargins(0, 0, (6 * density).toInt(), 0)
            }
            setOnClickListener {
                dialog.dismiss()
                showDeleteAnchorConfirmDialog(anchor)
            }
        }

        val btnClose = TextView(this).apply {
            text = "✖ 关闭"
            setTextColor(android.graphics.Color.WHITE)
            textSize = 11f
            setTypeface(null, android.graphics.Typeface.BOLD)
            gravity = android.view.Gravity.CENTER
            setPadding((10 * density).toInt(), (8 * density).toInt(), (10 * density).toInt(), (8 * density).toInt())
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(0xFF475569.toInt())
                cornerRadius = 4 * density
            }
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener {
                dialog.dismiss()
            }
        }

        actionLayout.addView(btnViewOriginal)
        actionLayout.addView(btnEditNote)
        actionLayout.addView(btnDelete)
        actionLayout.addView(btnClose)
        container.addView(actionLayout)

        dialog.setView(container)
        dialog.show()
    }

    private fun showFullScreenImage(imagePath: String) {
        val file = File(imagePath)
        if (!file.exists()) {
            Toast.makeText(this, "照片不存在", Toast.LENGTH_SHORT).show()
            return
        }

        val dialog = Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        val density = resources.displayMetrics.density

        val frame = android.widget.FrameLayout(this).apply {
            setBackgroundColor(android.graphics.Color.BLACK)
        }

        val imageView = android.widget.ImageView(this).apply {
            layoutParams = android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT
            )
            scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
            try {
                val bitmap = android.graphics.BitmapFactory.decodeFile(file.absolutePath)
                setImageBitmap(bitmap)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load original photo", e)
            }
        }
        frame.addView(imageView)

        val btnClose = TextView(this).apply {
            text = "✕"
            setTextColor(android.graphics.Color.WHITE)
            textSize = 24f
            gravity = android.view.Gravity.CENTER
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(0x80000000.toInt())
                shape = android.graphics.drawable.GradientDrawable.OVAL
            }
            val size = (40 * density).toInt()
            layoutParams = android.widget.FrameLayout.LayoutParams(size, size).apply {
                gravity = android.view.Gravity.TOP or android.view.Gravity.END
                setMargins((16 * density).toInt(), (16 * density).toInt(), (16 * density).toInt(), (16 * density).toInt())
            }
            setOnClickListener {
                dialog.dismiss()
            }
        }
        frame.addView(btnClose)

        var scale = 1.0f
        var translationX = 0f
        var translationY = 0f
        var mode = 0
        var startX = 0f
        var startY = 0f
        var startScale = 1.0f
        var initialSpacing = 1f

        val gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean {
                scale = if (scale > 1.0f) 1.0f else 2.5f
                translationX = 0f
                translationY = 0f
                imageView.scaleX = scale
                imageView.scaleY = scale
                imageView.translationX = translationX
                imageView.translationY = translationY
                return true
            }

            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                dialog.dismiss()
                return true
            }
        })

        imageView.setOnTouchListener { _, event ->
            if (gestureDetector.onTouchEvent(event)) {
                return@setOnTouchListener true
            }

            val action = event.action and MotionEvent.ACTION_MASK
            when (action) {
                MotionEvent.ACTION_DOWN -> {
                    mode = 1
                    startX = event.x - translationX
                    startY = event.y - translationY
                }
                MotionEvent.ACTION_POINTER_DOWN -> {
                    initialSpacing = getSpacing(event)
                    if (initialSpacing > 10f) {
                        mode = 2
                        startScale = scale
                    }
                }
                MotionEvent.ACTION_MOVE -> {
                    if (mode == 1 && scale > 1.0f) {
                        translationX = event.x - startX
                        translationY = event.y - startY
                        imageView.translationX = translationX
                        imageView.translationY = translationY
                    } else if (mode == 2) {
                        val newSpacing = getSpacing(event)
                        if (newSpacing > 10f) {
                            val factor = newSpacing / initialSpacing
                            scale = startScale * factor
                            if (scale < 0.8f) scale = 0.8f
                            if (scale > 5.0f) scale = 5.0f
                            imageView.scaleX = scale
                            imageView.scaleY = scale
                        }
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                    if (scale < 1.0f) {
                        scale = 1.0f
                        translationX = 0f
                        translationY = 0f
                        imageView.scaleX = 1f
                        imageView.scaleY = 1f
                        imageView.translationX = 0f
                        imageView.translationY = 0f
                    }
                    mode = 0
                }
            }
            true
        }

        dialog.setContentView(frame)
        dialog.show()
    }

    private fun getSpacing(event: MotionEvent): Float {
        if (event.pointerCount < 2) return 0f
        val x = event.getX(0) - event.getX(1)
        val y = event.getY(0) - event.getY(1)
        return Math.sqrt((x * x + y * y).toDouble()).toFloat()
    }

    private fun showEditNoteDialog(anchor: PhotoAnchor) {
        val density = resources.displayMetrics.density
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((16 * density).toInt(), (8 * density).toInt(), (16 * density).toInt(), (8 * density).toInt())
        }

        val input = EditText(this).apply {
            setText(anchor.note)
            setHint("输入备注信息...")
            setTextColor(android.graphics.Color.WHITE)
            setHintTextColor(0x80FFFFFF.toInt())
        }
        container.addView(input)

        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("编辑照片备注")
            .setView(container)
            .setPositiveButton("保存") { dialog, _ ->
                val newNote = input.text.toString().trim()
                dbExecutor.execute {
                    val updatedAnchor = anchor.copy(note = newNote)
                    trackDao.updatePhotoAnchor(updatedAnchor)
                    
                    val trackId = anchor.trackId
                    if (trackId != null) {
                        val track = trackDao.getTrackById(trackId)
                        val points = trackDao.getTrackPoints(trackId)
                        val anchors = trackDao.getPhotoAnchorsForTrack(trackId)
                        if (track != null) {
                            TrackFileHelper.saveTrackToJson(track, points, anchors)
                        }
                    }
                    
                    runOnUiThread {
                        Toast.makeText(this, "备注更新成功！", Toast.LENGTH_SHORT).show()
                        drawPhotoAnchorsOnMap()
                    }
                }
                dialog.dismiss()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showDeleteAnchorConfirmDialog(anchor: PhotoAnchor) {
        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("删除照片锚点")
            .setMessage("确定要删除此照片锚点吗？这不会删除您的原始照片文件，但会从地图和轨迹中移除该锚点。")
            .setPositiveButton("确定") { dialog, _ ->
                dbExecutor.execute {
                    trackDao.deletePhotoAnchor(anchor.id)
                    
                    anchor.thumbnailPath?.let { path ->
                        val file = File(path)
                        if (file.exists()) {
                            file.delete()
                        }
                    }

                    val trackId = anchor.trackId
                    if (trackId != null) {
                        val track = trackDao.getTrackById(trackId)
                        val points = trackDao.getTrackPoints(trackId)
                        val anchors = trackDao.getPhotoAnchorsForTrack(trackId)
                        if (track != null) {
                            TrackFileHelper.saveTrackToJson(track, points, anchors)
                        }
                    }

                    runOnUiThread {
                        Toast.makeText(this, "锚点删除成功", Toast.LENGTH_SHORT).show()
                        drawPhotoAnchorsOnMap()
                    }
                }
                dialog.dismiss()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun parseGpsCoordinate(raw: String?, ref: String?): Double? {
        if (raw.isNullOrBlank()) return null
        try {
            val parts = raw.split(",")
            if (parts.size < 3) return null
            
            fun parseRational(part: String): Double {
                val subParts = part.trim().split("/")
                if (subParts.size == 1) {
                    return subParts[0].toDouble()
                } else if (subParts.size == 2) {
                    val denom = subParts[1].toDouble()
                    if (denom == 0.0) return 0.0
                    return subParts[0].toDouble() / denom
                }
                return 0.0
            }
            
            val degrees = parseRational(parts[0])
            val minutes = parseRational(parts[1])
            val seconds = parseRational(parts[2])
            
            var result = degrees + (minutes / 60.0) + (seconds / 3600.0)
            if (ref != null && (ref.trim().equals("S", ignoreCase = true) || ref.trim().equals("W", ignoreCase = true))) {
                result = -result
            }
            return result
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing raw GPS: $raw", e)
            return null
        }
    }

    private fun importPhotoFromUri(uri: Uri) {
        try {
            val contentResolver = this.contentResolver
            val mimeType = contentResolver.getType(uri) ?: ""
            val extension = when {
                mimeType.contains("png") -> "png"
                mimeType.contains("webp") -> "webp"
                mimeType.contains("gif") -> "gif"
                else -> "jpg"
            }

            val anchorId = java.util.UUID.randomUUID().toString()
            val destFile = File(TrackFileHelper.getPhotosDirectory(), "photo_${anchorId}.${extension}")

            val finalUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                try {
                    MediaStore.setRequireOriginal(uri)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to setRequireOriginal for URI: $uri", e)
                    uri
                }
            } else {
                uri
            }

            contentResolver.openInputStream(finalUri)?.use { input ->
                java.io.FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }

            if (!destFile.exists()) {
                Toast.makeText(this, "照片复制失败", Toast.LENGTH_SHORT).show()
                return
            }

            var lat: Double? = null
            var lon: Double? = null
            var timestamp: Long = System.currentTimeMillis()
            var elevation: Double? = null

            try {
                // Read EXIF from the saved destination file, which supports random seeking and is highly compatible
                val exifInterface = androidx.exifinterface.media.ExifInterface(destFile.absolutePath)
                
                val rawLat = exifInterface.getAttribute(androidx.exifinterface.media.ExifInterface.TAG_GPS_LATITUDE)
                val rawLon = exifInterface.getAttribute(androidx.exifinterface.media.ExifInterface.TAG_GPS_LONGITUDE)
                val latRef = exifInterface.getAttribute(androidx.exifinterface.media.ExifInterface.TAG_GPS_LATITUDE_REF)
                val lonRef = exifInterface.getAttribute(androidx.exifinterface.media.ExifInterface.TAG_GPS_LONGITUDE_REF)

                Log.d(TAG, "读取文件: ${destFile.absolutePath}")
                Log.d(TAG, "TAG_GPS_LATITUDE: $rawLat")
                Log.d(TAG, "TAG_GPS_LONGITUDE: $rawLon")
                Log.d(TAG, "TAG_GPS_LATITUDE_REF: $latRef")
                Log.d(TAG, "TAG_GPS_LONGITUDE_REF: $lonRef")

                val latLong = exifInterface.latLong
                Log.d(TAG, "ExifInterface.latLong: ${latLong?.joinToString(", ")}")

                if (latLong != null && latLong.size >= 2) {
                    lat = latLong[0]
                    lon = latLong[1]
                    Log.d(TAG, "lat=$lat")
                    Log.d(TAG, "lon=$lon")
                } else {
                    // Try our custom robust fallback parser
                    val manualLat = parseGpsCoordinate(rawLat, latRef)
                    val manualLon = parseGpsCoordinate(rawLon, lonRef)
                    if (manualLat != null && manualLon != null) {
                        lat = manualLat
                        lon = manualLon
                        Log.d(TAG, "lat=$lat")
                        Log.d(TAG, "lon=$lon")
                    } else {
                        Log.d(TAG, "GPS Parse Failed")
                    }
                }

                val alt = exifInterface.getAltitude(0.0)
                if (alt != 0.0) {
                    elevation = alt
                }

                val dateStr = exifInterface.getAttribute(androidx.exifinterface.media.ExifInterface.TAG_DATETIME_ORIGINAL)
                    ?: exifInterface.getAttribute(androidx.exifinterface.media.ExifInterface.TAG_DATETIME)
                if (dateStr != null) {
                    val exSdf = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US)
                    exSdf.parse(dateStr)?.let {
                        timestamp = it.time
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error reading EXIF from file ${destFile.absolutePath}", e)
                Log.d(TAG, "GPS Parse Failed")
            }

            val thumbPath = generateThumbnail(destFile, anchorId)

            if (lat != null && lon != null) {
                createAndSavePhotoAnchor(
                    id = anchorId,
                    latitude = lat!!,
                    longitude = lon!!,
                    elevation = elevation,
                    timestamp = timestamp,
                    imagePath = destFile.absolutePath,
                    thumbnailPath = thumbPath
                )
            } else {
                showNoGpsPhotoAnchorOptionsDialog(
                    id = anchorId,
                    imagePath = destFile.absolutePath,
                    thumbnailPath = thumbPath,
                    timestamp = timestamp,
                    elevation = elevation
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error importing photo from Uri", e)
            Toast.makeText(this, "照片导入失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showNoGpsPhotoAnchorOptionsDialog(
        id: String,
        imagePath: String,
        thumbnailPath: String?,
        timestamp: Long,
        elevation: Double?
    ) {
        val options = mutableListOf<String>()
        val actions = mutableListOf<() -> Unit>()

        val latestPt = synchronized(currentTrackPoints) { currentTrackPoints.lastOrNull() }
        if (latestPt != null && (trackStatus == "RECORDING" || trackStatus == "PAUSED")) {
            options.add("A. 关联到当前轨迹最新位置 (${String.format("%.6f", latestPt.latitude)}, ${String.format("%.6f", latestPt.longitude)})")
            actions.add {
                createAndSavePhotoAnchor(id, latestPt.latitude, latestPt.longitude, latestPt.elevation ?: elevation, timestamp, imagePath, thumbnailPath)
            }
        } else {
            options.add("A. 关联到当前轨迹最新位置 (不可用: 当前未记录轨迹)")
            actions.add {
                Toast.makeText(this, "当前无正在记录的轨迹", Toast.LENGTH_SHORT).show()
            }
        }

        val center = mapboxMap?.cameraPosition?.target
        if (center != null) {
            options.add("B. 关联到当前地图中心点 (${String.format("%.6f", center.latitude)}, ${String.format("%.6f", center.longitude)})")
            actions.add {
                createAndSavePhotoAnchor(id, center.latitude, center.longitude, elevation, timestamp, imagePath, thumbnailPath)
            }
        }

        options.add("C. 手动输入经纬度坐标")
        actions.add {
            showManualCoordinatesDialog(id, imagePath, thumbnailPath, timestamp, elevation)
        }

        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("照片未包含 GPS 坐标，请选择定位方式：")
            .setItems(options.toTypedArray()) { dialog, which ->
                actions[which].invoke()
                dialog.dismiss()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showManualCoordinatesDialog(
        id: String,
        imagePath: String,
        thumbnailPath: String?,
        timestamp: Long,
        elevation: Double?
    ) {
        val density = resources.displayMetrics.density
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((16 * density).toInt(), (8 * density).toInt(), (16 * density).toInt(), (8 * density).toInt())
        }

        val etLat = EditText(this).apply {
            setHint("纬度 (Latitude, e.g., 39.9042)")
            setInputType(android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL or android.text.InputType.TYPE_NUMBER_FLAG_SIGNED)
            setTextColor(android.graphics.Color.WHITE)
            setHintTextColor(0x80FFFFFF.toInt())
        }
        val etLon = EditText(this).apply {
            setHint("经度 (Longitude, e.g., 116.4074)")
            setInputType(android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL or android.text.InputType.TYPE_NUMBER_FLAG_SIGNED)
            setTextColor(android.graphics.Color.WHITE)
            setHintTextColor(0x80FFFFFF.toInt())
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = (10 * density).toInt()
            }
            layoutParams = lp
        }

        container.addView(etLat)
        container.addView(etLon)

        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("手动输入经纬度坐标")
            .setView(container)
            .setPositiveButton("确定") { dialog, _ ->
                val latVal = etLat.text.toString().trim().toDoubleOrNull()
                val lonVal = etLon.text.toString().trim().toDoubleOrNull()
                if (latVal == null || lonVal == null || latVal < -90.0 || latVal > 90.0 || lonVal < -180.0 || lonVal > 180.0) {
                    Toast.makeText(this, "请输入合法的经纬度坐标！", Toast.LENGTH_SHORT).show()
                } else {
                    createAndSavePhotoAnchor(id, latVal, lonVal, elevation, timestamp, imagePath, thumbnailPath)
                }
                dialog.dismiss()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun createAndSavePhotoAnchor(
        id: String,
        latitude: Double,
        longitude: Double,
        elevation: Double?,
        timestamp: Long,
        imagePath: String,
        thumbnailPath: String?
    ) {
        val activeTrackId = loadedTrackId ?: currentTrackId
        val boundTrackId = if (activeTrackId != 0L) activeTrackId else null

        val anchor = PhotoAnchor(
            id = id,
            trackId = boundTrackId,
            latitude = latitude,
            longitude = longitude,
            elevation = elevation,
            timestamp = timestamp,
            imagePath = imagePath,
            thumbnailPath = thumbnailPath,
            note = ""
        )

        dbExecutor.execute {
            trackDao.insertPhotoAnchor(anchor)
            
            if (boundTrackId != null) {
                val track = trackDao.getTrackById(boundTrackId)
                val points = trackDao.getTrackPoints(boundTrackId)
                val anchors = trackDao.getPhotoAnchorsForTrack(boundTrackId)
                if (track != null) {
                    TrackFileHelper.saveTrackToJson(track, points, anchors)
                }
            }

            runOnUiThread {
                Toast.makeText(this, "照片锚点已成功保存并在地图上展示！", Toast.LENGTH_SHORT).show()
                drawPhotoAnchorsOnMap()
            }
        }
    }

    private fun toggleLoadedTrackOnMap(track: Track) {
        if (loadedTrackId == track.id) {
            // Toggle Off
            loadedTrackId = null
            synchronized(loadedTrackPoints) {
                loadedTrackPoints.clear()
            }
            drawLoadedTrackOnMap()
            drawPhotoAnchorsOnMap()
            refreshTrackList()
            Toast.makeText(this, "隐藏轨迹: ${track.name ?: "未命名"}", Toast.LENGTH_SHORT).show()
        } else {
            // Toggle On
            loadedTrackId = track.id
            dbExecutor.execute {
                val points = trackDao.getTrackPoints(track.id)
                synchronized(loadedTrackPoints) {
                    loadedTrackPoints.clear()
                    loadedTrackPoints.addAll(points)
                }
                drawLoadedTrackOnMap()
                drawPhotoAnchorsOnMap()
                runOnUiThread {
                    refreshTrackList()
                    Toast.makeText(this, "加载轨迹: ${track.name ?: "未命名"}", Toast.LENGTH_SHORT).show()
                    
                    // Auto zoom map to loaded track
                    if (points.isNotEmpty()) {
                        try {
                            val boundsBuilder = com.mapbox.mapboxsdk.geometry.LatLngBounds.Builder()
                            for (pt in points) {
                                boundsBuilder.include(com.mapbox.mapboxsdk.geometry.LatLng(pt.latitude, pt.longitude))
                            }
                            mapboxMap?.easeCamera(
                                com.mapbox.mapboxsdk.camera.CameraUpdateFactory.newLatLngBounds(boundsBuilder.build(), 50),
                                1500
                            )
                        } catch (e: Exception) {
                            Log.e(TAG, "Error fitting camera to loaded track bounds", e)
                        }
                    }
                }
            }
        }
    }

    private fun drawLoadedTrackOnMap() {
        val map = mapboxMap ?: return
        val style = try { map.style } catch (e: Exception) { null } ?: return

        val points = synchronized(loadedTrackPoints) {
            loadedTrackPoints.map { Point.fromLngLat(it.longitude, it.latitude) }
        }
        val lineString = if (points.size >= 2) {
            LineString.fromLngLats(points)
        } else if (points.size == 1) {
            LineString.fromLngLats(listOf(points[0], points[0]))
        } else {
            null
        }

        runOnUiThread {
            try {
                var source = style.getSource("loaded-track-source") as? GeoJsonSource
                if (source == null) {
                    source = GeoJsonSource("loaded-track-source")
                    style.addSource(source)
                }

                if (lineString != null) {
                    source.setGeoJson(FeatureCollection.fromFeature(Feature.fromGeometry(lineString)))
                } else {
                    source.setGeoJson(FeatureCollection.fromFeatures(emptyArray()))
                }

                var layer = style.getLayer("loaded-track-layer") as? LineLayer
                if (layer == null) {
                    layer = LineLayer("loaded-track-layer", "loaded-track-source")
                    layer.setProperties(
                        PropertyFactory.lineColor(android.graphics.Color.CYAN),
                        PropertyFactory.lineWidth(4f),
                        PropertyFactory.lineCap(com.mapbox.mapboxsdk.style.layers.Property.LINE_CAP_ROUND),
                        PropertyFactory.lineJoin(com.mapbox.mapboxsdk.style.layers.Property.LINE_JOIN_ROUND)
                    )
                    style.addLayer(layer)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error drawing loaded track on map", e)
            }
        }
    }

    private fun showRenameTrackDialog(track: Track) {
        val density = resources.displayMetrics.density
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((16 * density).toInt(), (8 * density).toInt(), (16 * density).toInt(), (8 * density).toInt())
        }
        
        val input = EditText(this).apply {
            setText(track.name ?: "轨迹 #${track.id}")
            setSelectAllOnFocus(true)
            setTextColor(android.graphics.Color.WHITE)
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(0x13FFFFFF)
                setStroke((1 * density).toInt(), 0x22FFFFFF)
                cornerRadius = 4 * density
            }
            setPadding((12 * density).toInt(), (10 * density).toInt(), (12 * density).toInt(), (10 * density).toInt())
        }
        container.addView(input)

        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("重命名轨迹")
            .setView(container)
            .setPositiveButton("确定") { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isNotEmpty()) {
                    dbExecutor.execute {
                        track.name = newName
                        trackDao.updateTrack(track)
                        
                        // Also update .track file on disk
                        val points = trackDao.getTrackPoints(track.id)
                        TrackFileHelper.saveTrackToJson(track, points)
                        
                        runOnUiThread {
                            Toast.makeText(this, "重命名成功", Toast.LENGTH_SHORT).show()
                            refreshTrackList()
                        }
                    }
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showDeleteTrackDialog(track: Track) {
        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("确认删除")
            .setMessage("确定要彻底删除轨迹“${track.name ?: "轨迹 #${track.id}"}”吗？此操作将同时清除存储卡上的关联缓存。")
            .setPositiveButton("删除") { _, _ ->
                dbExecutor.execute {
                    trackDao.deleteTrackById(track.id)
                    TrackFileHelper.deleteTrackJsonFile(track.id)
                    
                    if (loadedTrackId == track.id) {
                        loadedTrackId = null
                        synchronized(loadedTrackPoints) {
                            loadedTrackPoints.clear()
                        }
                        drawLoadedTrackOnMap()
                    }
                    
                    runOnUiThread {
                        Toast.makeText(this, "删除成功", Toast.LENGTH_SHORT).show()
                        refreshTrackList()
                    }
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun exportTrackToGpx(track: Track) {
        dbExecutor.execute {
            // 后续 GPX 导出功能必须直接读取 .track 文件生成标准 GPX
            val trackFile = File(TrackFileHelper.getTracksDirectory(), "track_${track.id}.track")
            val file = if (trackFile.exists()) {
                TrackFileHelper.exportTrackFileToGpx(trackFile)
            } else {
                // Fallback to database
                val points = trackDao.getTrackPoints(track.id)
                TrackFileHelper.exportToGpx(track, points)
            }
            runOnUiThread {
                if (file != null) {
                    Toast.makeText(this, "GPX 导出成功！\n文件存放在: ${file.absolutePath}", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this, "导出 GPX 失败", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun manuallySaveTrack() {
        if (trackStatus == "STOPPED") {
            Toast.makeText(this, "当前未在记录轨迹", Toast.LENGTH_SHORT).show()
            return
        }
        Toast.makeText(this, "正在将当前轨迹保存至磁盘...", Toast.LENGTH_SHORT).show()
        dbExecutor.execute {
            forceSaveCurrentTrackToDisk()
            runOnUiThread {
                Toast.makeText(this, "轨迹手动保存成功！\n文件存放在: /storage/emulated/0/CyberTrail/Tracks/", Toast.LENGTH_LONG).show()
                refreshTrackList()
            }
        }
    }

    private fun forceSaveCurrentTrackToDisk() {
        val trackId = currentTrackId
        if (trackId == 0L) return

        // 1. Save remaining pending points synchronously to DB
        val remainingPoints = synchronized(pendingPointsToSave) {
            val copy = ArrayList(pendingPointsToSave)
            pendingPointsToSave.clear()
            copy
        }
        
        if (remainingPoints.isNotEmpty()) {
            try {
                trackDao.insertTrackPoints(remainingPoints)
                Log.d("CYBERTRAIL_TRACK", "forceSaveCurrentTrackToDisk: Inserted ${remainingPoints.size} pending points to DB")
            } catch (e: Exception) {
                Log.e(TAG, "Error inserting remaining points inside forceSaveCurrentTrackToDisk", e)
            }
        }

        // 2. Read all points from DB and save to .track file
        val track = trackDao.getTrackById(trackId)
        if (track != null) {
            track.status = trackStatus // ensure the correct status (e.g. RECORDING or PAUSED) is set
            val allPoints = trackDao.getTrackPoints(trackId)
            val file = TrackFileHelper.saveTrackToJson(track, allPoints)
            if (file != null) {
                Log.d("CYBERTRAIL_TRACK", "forceSaveCurrentTrackToDisk: Successfully force-saved track $trackId to disk: ${file.absolutePath}")
            }
        }
    }

    private fun calculateDistance(points: List<TrackPoint>): Float {
        var totalDist = 0f
        for (i in 0 until points.size - 1) {
            val results = FloatArray(1)
            Location.distanceBetween(
                points[i].latitude, points[i].longitude,
                points[i + 1].latitude, points[i + 1].longitude,
                results
            )
            totalDist += results[0]
        }
        return totalDist
    }

    private data class TrackItemData(
        val track: Track,
        val pointCount: Int,
        val distanceMeters: Float
    )

    private fun initTrackReplay() {
        panelTrackReplay = findViewById(R.id.panel_track_replay)
        tvReplayStatus = findViewById(R.id.tv_replay_status)
        tvReplayProgress = findViewById(R.id.tv_replay_progress)
        tvReplayLatLon = findViewById(R.id.tv_replay_latlon)
        tvReplayEle = findViewById(R.id.tv_replay_ele)
        tvReplayDistance = findViewById(R.id.tv_replay_distance)
        tvReplayClimb = findViewById(R.id.tv_replay_climb)
        tvReplaySpeed = findViewById(R.id.tv_replay_speed)
        tvReplayTime = findViewById(R.id.tv_replay_time)
        btnReplayPlayPause = findViewById(R.id.btn_replay_play_pause)
        btnReplayStop = findViewById(R.id.btn_replay_stop)
        btnReplaySpeed1 = findViewById(R.id.btn_replay_speed_1)
        btnReplaySpeed2 = findViewById(R.id.btn_replay_speed_2)
        btnReplaySpeed5 = findViewById(R.id.btn_replay_speed_5)

        btnReplayPlayPause.setOnClickListener { toggleReplayPlayPause() }
        btnReplayStop.setOnClickListener { stopReplay() }
        btnReplaySpeed1.setOnClickListener {
            replaySpeedMultiplier = 1
            updateReplaySpeedUi()
        }
        btnReplaySpeed2.setOnClickListener {
            replaySpeedMultiplier = 2
            updateReplaySpeedUi()
        }
        btnReplaySpeed5.setOnClickListener {
            replaySpeedMultiplier = 5
            updateReplaySpeedUi()
        }
    }

    private fun startReplay(track: Track) {
        if (trackStatus == "RECORDING") {
            Toast.makeText(this, "当前正在记录轨迹，请先结束记录再进行回放", Toast.LENGTH_SHORT).show()
            return
        }

        // Close drawer so map is fully visible
        val drawer = findViewById<androidx.drawerlayout.widget.DrawerLayout>(R.id.drawer_layout)
        drawer.closeDrawers()

        Toast.makeText(this, "正在初始化回放系统...", Toast.LENGTH_SHORT).show()

        dbExecutor.execute {
            val points = trackDao.getTrackPoints(track.id)
            if (points.isEmpty()) {
                runOnUiThread {
                    Toast.makeText(this, "该轨迹无有效定位点，无法回放", Toast.LENGTH_SHORT).show()
                }
                return@execute
            }

            val sortedPoints = points.sortedBy { it.timestamp }
            val size = sortedPoints.size
            val dists = DoubleArray(size)
            val climbs = DoubleArray(size)

            var totalDist = 0.0
            var totalClimb = 0.0
            dists[0] = 0.0
            climbs[0] = 0.0

            for (i in 1 until size) {
                val stepDist = getDistanceBetweenPoints(sortedPoints[i - 1], sortedPoints[i]).toDouble()
                totalDist += stepDist
                dists[i] = totalDist

                val currentElevation = sortedPoints[i].elevation ?: 0.0
                val previousElevation = sortedPoints[i - 1].elevation ?: 0.0
                val eleDiff = currentElevation - previousElevation
                if (eleDiff > 0.0) {
                    totalClimb += eleDiff
                }
                climbs[i] = totalClimb
            }

            runOnUiThread {
                isReplayActive = true
                isReplayPlaying = true
                replaySpeedMultiplier = 1
                replayPoints = sortedPoints
                replayCurrentIndex = 0
                replayCumulativeDistances = dists
                replayCumulativeClimbs = climbs

                findViewById<View>(R.id.panel_track_control).visibility = View.GONE
                panelTrackReplay.visibility = View.VISIBLE

                if (loadedTrackId != track.id) {
                    toggleLoadedTrackOnMap(track)
                }

                val firstPt = sortedPoints[0]
                val firstLatLng = com.mapbox.mapboxsdk.geometry.LatLng(firstPt.latitude, firstPt.longitude)
                mapboxMap?.easeCamera(
                    com.mapbox.mapboxsdk.camera.CameraUpdateFactory.newLatLngZoom(firstLatLng, 15.0),
                    1000
                )

                replayMarker?.let { mapboxMap?.removeMarker(it) }
                val markerOptions = com.mapbox.mapboxsdk.annotations.MarkerOptions()
                    .position(firstLatLng)
                    .icon(createReplayIconBitmap())
                    .title("ReplayMarker")
                replayMarker = mapboxMap?.addMarker(markerOptions)

                updateReplaySpeedUi()
                updateReplayControlsUi()

                startReplayTicks()
            }
        }
    }

    private fun startReplayTicks() {
        replayRunnable?.let { replayHandler.removeCallbacks(it) }
        val runnable = object : Runnable {
            override fun run() {
                if (!isReplayActive) return

                if (isReplayPlaying) {
                    val size = replayPoints.size
                    if (replayCurrentIndex < size) {
                        val pt = replayPoints[replayCurrentIndex]

                        val latLng = com.mapbox.mapboxsdk.geometry.LatLng(pt.latitude, pt.longitude)
                        replayMarker?.position = latLng

                        mapboxMap?.moveCamera(
                            com.mapbox.mapboxsdk.camera.CameraUpdateFactory.newLatLng(latLng)
                        )

                        tvReplayProgress.text = "进度: ${replayCurrentIndex + 1} / $size"
                        tvReplayLatLon.text = "位置: %.6f, %.6f".format(pt.latitude, pt.longitude)
                        tvReplayEle.text = "海拔: %.1fm".format(pt.elevation ?: 0.0)
                        
                        val distKm = replayCumulativeDistances[replayCurrentIndex] / 1000.0
                        val totalDistKm = replayCumulativeDistances[size - 1] / 1000.0
                        tvReplayDistance.text = "已播: %.2fkm / %.2fkm".format(distKm, totalDistKm)
                        
                        tvReplayClimb.text = "累计爬升: +%.0fm".format(replayCumulativeClimbs[replayCurrentIndex])
                        tvReplaySpeed.text = "速度: %.1f m/s".format(pt.speed)

                        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
                        tvReplayTime.text = "时间: ${sdf.format(java.util.Date(pt.timestamp))}"

                        checkAndTriggerPhotoAnchorForTimestamp(pt.timestamp)

                        replayCurrentIndex += replaySpeedMultiplier
                        if (replayCurrentIndex >= size) {
                            replayCurrentIndex = size - 1
                            isReplayPlaying = false
                            Toast.makeText(this@MapActivity, "回放已完成", Toast.LENGTH_SHORT).show()
                            updateReplayControlsUi()
                        }
                    }
                }

                replayHandler.postDelayed(this, 200L)
            }
        }
        replayRunnable = runnable
        replayHandler.post(runnable)
    }

    private fun stopReplay() {
        isReplayActive = false
        isReplayPlaying = false
        replayRunnable?.let { replayHandler.removeCallbacks(it) }
        replayRunnable = null

        replayMarker?.let {
            mapboxMap?.removeMarker(it)
            replayMarker = null
        }

        panelTrackReplay.visibility = View.GONE
        findViewById<View>(R.id.panel_track_control).visibility = View.VISIBLE

        Toast.makeText(this, "回放已停止", Toast.LENGTH_SHORT).show()
    }

    private fun toggleReplayPlayPause() {
        if (!isReplayActive) return
        val size = replayPoints.size
        if (replayCurrentIndex >= size - 1) {
            replayCurrentIndex = 0
        }
        isReplayPlaying = !isReplayPlaying
        updateReplayControlsUi()
    }

    private fun updateReplayControlsUi() {
        if (isReplayPlaying) {
            btnReplayPlayPause.text = "⏸ 暂停"
            btnReplayPlayPause.setTextColor(0xFFF59E0B.toInt())
            btnReplayPlayPause.setBackgroundResource(R.drawable.track_btn_amber_bg)
            tvReplayStatus.text = "👁 轨迹回放: 播放中"
        } else {
            btnReplayPlayPause.text = "▶ 播放"
            btnReplayPlayPause.setTextColor(0xFF10B981.toInt())
            btnReplayPlayPause.setBackgroundResource(R.drawable.track_btn_green_bg)
            tvReplayStatus.text = "👁 轨迹回放: 暂停"
        }
    }

    private fun updateReplaySpeedUi() {
        val density = resources.displayMetrics.density
        val activeBg = 0xFF38BDF8.toInt()
        val activeText = 0xFF0F172A.toInt()
        val inactiveBg = 0x1A38BDF8
        val inactiveText = 0xFF38BDF8.toInt()

        btnReplaySpeed1.background = android.graphics.drawable.GradientDrawable().apply {
            setColor(if (replaySpeedMultiplier == 1) activeBg else inactiveBg)
            cornerRadius = 4 * density
        }
        btnReplaySpeed1.setTextColor(if (replaySpeedMultiplier == 1) activeText else inactiveText)

        btnReplaySpeed2.background = android.graphics.drawable.GradientDrawable().apply {
            setColor(if (replaySpeedMultiplier == 2) activeBg else inactiveBg)
            cornerRadius = 4 * density
        }
        btnReplaySpeed2.setTextColor(if (replaySpeedMultiplier == 2) activeText else inactiveText)

        btnReplaySpeed5.background = android.graphics.drawable.GradientDrawable().apply {
            setColor(if (replaySpeedMultiplier == 5) activeBg else inactiveBg)
            cornerRadius = 4 * density
        }
        btnReplaySpeed5.setTextColor(if (replaySpeedMultiplier == 5) activeText else inactiveText)
    }

    private fun createReplayIconBitmap(): com.mapbox.mapboxsdk.annotations.Icon {
        val density = resources.displayMetrics.density
        val size = (24 * density).toInt()
        val bitmap = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)

        val paint = android.graphics.Paint().apply {
            isAntiAlias = true
        }

        paint.color = 0xFF22D3EE.toInt()
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)

        paint.color = 0xFF1E3A8A.toInt()
        canvas.drawCircle(size / 2f, size / 2f, (size / 2f) - (2 * density), paint)

        paint.color = 0xFF3B82F6.toInt()
        canvas.drawCircle(size / 2f, size / 2f, (size / 2f) - (4 * density), paint)

        val iconFactory = com.mapbox.mapboxsdk.annotations.IconFactory.getInstance(this)
        return iconFactory.fromBitmap(bitmap)
    }

    private fun getDistanceBetweenPoints(pt1: TrackPoint, pt2: TrackPoint): Float {
        val results = FloatArray(1)
        Location.distanceBetween(pt1.latitude, pt1.longitude, pt2.latitude, pt2.longitude, results)
        return results[0]
    }

    private fun checkAndTriggerPhotoAnchorForTimestamp(timestamp: Long) {
        // Reserved for Phase 3 Photo Popup feature.
        // Queries PhotoAnchor at/near the given timestamp, and triggers popup when found.
        Log.d(TAG, "Replay timeline reached timestamp: $timestamp, checking for associated PhotoAnchor...")
    }
}
