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
import java.io.File

class MapActivity : AppCompatActivity(), OnMapReadyCallback, LocationListener {

    private lateinit var mapView: MapView
    private var mapboxMap: MapboxMap? = null
    private lateinit var demSystem: DEMSystem

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

        runOfflineDiagnostics()
        startGpsTracking()
    }

    private fun runOfflineDiagnostics() {
        val baseDir = java.io.File(android.os.Environment.getExternalStorageDirectory(), "CyberTrail")
        val mapsDir = java.io.File(baseDir, "maps")
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

        map.addOnMapClickListener {
            Log.d("CYBERTRAIL_MAP", "Map clicked")
            false
        }

        map.addOnCameraIdleListener {
            Log.d("CYBERTRAIL_MAP", "Map idle")
        }

        val baseDir = java.io.File(android.os.Environment.getExternalStorageDirectory(), "CyberTrail")
        val mapsDir = java.io.File(baseDir, "maps")
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

    private fun updateTerrainHud(lat: Double, lon: Double) {
        val now = System.currentTimeMillis()
        if (now - lastFetchTime < 1000) return // Throttle
        lastFetchTime = now

        demSystem.terrainAnalyzer.analyzeLocationAsync(lat, lon) { result ->
            if (result != null) {
                runOnUiThread {
                    hudElevation.text = "海拔: %.1f m".format(result.elevation)
                    hudSlope.text = "坡度: %.1f°".format(result.slope)
                    hudAspect.text = "坡向: %.1f°".format(result.aspect)
                }
            } else {
                runOnUiThread {
                    hudElevation.text = "海拔: 获取中..."
                    hudSlope.text = "坡度: 获取中..."
                    hudAspect.text = "坡向: 获取中..."
                }
            }
        }
    }

    override fun onLocationChanged(location: Location) {
        val lat = location.latitude
        val lon = location.longitude
        lastGpsLatitude = lat
        lastGpsLongitude = lon
        updateTerrainHud(lat, lon)
        updateDiagnosticHud()
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
        super.onResume();
        mapView.onResume();
        Log.d("CYBERTRAIL_MAP", "MapView onResume")
    }

    override fun onPause() {
        super.onPause();
        mapView.onPause();
        Log.d("CYBERTRAIL_MAP", "MapView onPause")
    }

    override fun onStop() {
        super.onStop();
        mapView.onStop();
        Log.d("CYBERTRAIL_MAP", "MapView onStop")
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState);
        mapView.onSaveInstanceState(outState);
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
        super.onLowMemory();
        mapView.onLowMemory();
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

        hudDiagnosticCounters.text = scanSection +
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
        val mapsDir = java.io.File(baseDir, "maps")
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
}
