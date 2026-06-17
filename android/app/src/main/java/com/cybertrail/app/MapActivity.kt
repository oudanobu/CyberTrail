package com.cybertrail.app

import android.Manifest
import android.content.Context
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

    private lateinit var locationManager: LocationManager

    companion object {
        private const val TAG = "MapActivity"
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

        // Bind layouts
        mapView = findViewById(R.id.mapView)
        hudElevation = findViewById(R.id.hud_elevation)
        hudSlope = findViewById(R.id.hud_slope)
        hudAspect = findViewById(R.id.hud_aspect)
        hudMbtilesPath = findViewById(R.id.hud_mbtiles_path)
        hudMbtilesStatus = findViewById(R.id.hud_mbtiles_status)
        hudTileCount = findViewById(R.id.hud_tile_count)
        hudStyleStatus = findViewById(R.id.hud_style_status)

        mapView.onCreate(savedInstanceState)
        mapView.getMapAsync(this)

        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager

        runOfflineDiagnostics()
        startGpsTracking()
    }

    private fun runOfflineDiagnostics() {
        val mapsDir = File(filesDir, "maps")
        if (!mapsDir.exists()) {
            mapsDir.mkdirs()
        }
        val mbtilesFile = File(mapsDir, "yosemite.mbtiles")
        
        // Copy mbtiles from assets if it doesn't exist
        if (!mbtilesFile.exists()) {
            try {
                assets.open("yosemite.mbtiles").use { inputStream ->
                    mbtilesFile.outputStream().use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
                Log.i(TAG, "Successfully copied yosemite.mbtiles from assets to internal storage.")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to copy yosemite.mbtiles from assets.", e)
            }
        }

        val absolutePath = mbtilesFile.absolutePath

        hudMbtilesPath.text = "路径: $absolutePath"

        val exists = mbtilesFile.exists()
        hudMbtilesStatus.text = "物理文件: " + if (exists) "✅ 已确认物理存在" else "❌ 未找到离线包"

        var tilesNum = 0
        if (exists) {
            try {
                val db = SQLiteDatabase.openDatabase(absolutePath, null, SQLiteDatabase.OPEN_READONLY)
                val cursor = db.rawQuery("SELECT COUNT(*) FROM tiles", null)
                if (cursor.moveToFirst()) {
                    tilesNum = cursor.getInt(0)
                }
                cursor.close()
                db.close()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to query tiles count from MBTiles database", e)
            }
        }

        // Display read block
        if (tilesNum > 0) {
            hudTileCount.text = "地图瓦片数: $tilesNum 块 (真实读取)"
        } else {
            hudTileCount.text = "地图瓦片数: " + if (exists) "读取中/无可用瓦片" else "0 块 (离线包缺失)"
        }
    }

    override fun onMapReady(map: MapboxMap) {
        this.mapboxMap = map
        hudStyleStatus.text = "Style加载: 正在建立本地地图渲染器..."

        // Load offline custom style
        val offlineStyleUri = "asset://style.json"
        
        map.setStyle(Style.Builder().fromUri(offlineStyleUri)) { style ->
            hudStyleStatus.text = "Style加载: Success"
            Log.i(TAG, "Style loaded successfully.")
        }

        // Bind camera change listener to query elevation at map center
        map.addOnCameraMoveListener {
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

    override fun onResume() {
        super.onResume();
        mapView.onResume();
    }

    override fun onPause() {
        super.onPause();
        mapView.onPause();
    }

    override fun onStop() {
        super.onStop();
        mapView.onStop();
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState);
        mapView.onSaveInstanceState(outState);
    }

    override fun onDestroy() {
        super.onDestroy()
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
}
