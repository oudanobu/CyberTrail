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

                    Log.d("MAP_DEBUG", "===== STYLE LAYERS START =====")
                    style.layers.forEach {
                        Log.d("MAP_DEBUG", "LAYER=${it.id}")
                    }
                    Log.d("MAP_DEBUG", "===== STYLE LAYERS END =====")

                    val source = style.getSource("offline-mbtiles")
                    val isExist = source != null
                    sourceExists = isExist
                    Log.d("MAP_DEBUG", "SOURCE_EXISTS=$isExist")

                    val offlineLayer = style.getLayer("offline-layer")
                    val isLayerExist = offlineLayer != null
                    layerExists = isLayerExist
                    val clazzName = offlineLayer?.javaClass?.simpleName ?: "null"
                    layerClassString = clazzName
                    Log.d("MAP_DEBUG", "LAYER_EXISTS=$isLayerExist")
                    Log.d("MAP_DEBUG", "LAYER_CLASS=$clazzName")

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
                Log.d("CYBERTRAIL_MAP", "setStyle begin")
                map.setStyle(Style.Builder().fromJson(fallbackStyle)) { style ->
                    Log.d("CYBERTRAIL_MAP", "STYLE_SUCCESS")
                    hudStyleStatus.text = "Style加载: 无离线地图(使用在线OSM备用)"
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
        updateTerrainHud(lat, lon)
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
        val sExists = sourceExists?.toString() ?: "Unknown"
        val lExists = layerExists?.toString() ?: "Unknown"
        val lClass = layerClassString ?: "Unknown"
        hudDiagnosticCounters.text = "RenderFrame: $renderFrameCount\nCameraMove: $cameraMoveCount\nTileRequest: $tileRequestCount\nTileFound: $tileFoundCount\nTileNotFound: $tileNotFoundCount\nSourceExists: $sExists\nLayerExists: $lExists\nLayerClass: $lClass"
    }
}
