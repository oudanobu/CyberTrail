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
                val txtContent = """
========================================
CYBERTRAIL DIAGNOSTIC FILE
Timestamp: $currentTimestamp
========================================

--- DIAGNOSTIC INFORMATION ---
${hudDiagnosticCounters.text}

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

        hudDiagnosticCounters.text = "RenderFrame: $renderFrameCount\n" +
                "CameraMove: $cameraMoveCount\n" +
                "TileRequest: $tileRequestCount\n" +
                "TileFound: $tileFoundCount\n" +
                "TileNotFound: $tileNotFoundCount\n\n" +
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
                "SourceList:\n$sList\n\n" +
                "LayerList:\n$lList\n\n" +
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
                "ForcedZoomApplied: $forcedZoomApplied\n\n" +
                "HUDHeight: ${hudHeight}px\n" +
                "ScrollY: $scrollY"
    }
}
