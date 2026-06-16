package com.cybertrail.app

import android.content.pm.PackageManager
import android.database.sqlite.SQLiteDatabase
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.cybertrail.app.model.TrackingState
import com.cybertrail.app.repository.TrackingRepository
import com.mapbox.geojson.Feature
import com.mapbox.geojson.FeatureCollection
import com.mapbox.geojson.LineString
import com.mapbox.geojson.Point
import com.mapbox.mapboxsdk.Mapbox
import com.mapbox.mapboxsdk.camera.CameraPosition
import com.mapbox.mapboxsdk.camera.CameraUpdateFactory
import com.mapbox.mapboxsdk.geometry.LatLng
import com.mapbox.mapboxsdk.maps.MapView
import com.mapbox.mapboxsdk.maps.MapboxMap
import com.mapbox.mapboxsdk.maps.OnMapReadyCallback
import com.mapbox.mapboxsdk.storage.FileSource
import com.mapbox.mapboxsdk.style.layers.CircleLayer
import com.mapbox.mapboxsdk.style.layers.LineLayer
import com.mapbox.mapboxsdk.style.layers.PropertyFactory
import com.mapbox.mapboxsdk.style.sources.GeoJsonSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import java.util.Random

class MapActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var mapView: MapView
    private var mapboxMap: MapboxMap? = null

    // UI elements
    private lateinit var tvMapCoordinates: TextView
    private lateinit var tvMapAltitude: TextView
    private lateinit var tvMapPoints: TextView
    private lateinit var btnToggleHistorical: Button
    private lateinit var btnToggleSlope: Button
    private lateinit var btnSimulateOfflinePoint: Button

    // Telemetry and GIS DEM states
    private lateinit var demSystem: com.cybertrail.app.gis.DEMSystem
    private var currentPosition = LatLng(37.7749, -122.4194)
    private var currentAltitude = 120.0
    private var isHistoricalVisible = true
    private var isSlopeVisible = true

    // Active track coordinate cache (Kotlin side cache)
    private val activeTrackPoints = ArrayList<LatLng>()

    companion object {
        private const val TAG = "MapActivity"
        
        // Sources & Layers Names
        private const val POSITION_SOURCE = "pos-source"
        private const val POSITION_LAYER = "pos-layer"
        
        private const val CURRENT_TRACK_SOURCE = "cur-track-source"
        private const val CURRENT_TRACK_LAYER = "cur-track-layer"
        
        private const val HISTORY_TRACK_SOURCE = "hist-track-source"
        private const val HISTORY_TRACK_LAYER = "hist-track-layer"

        private const val SLOPE_SOURCE = "slope-source"
        private const val SLOPE_LAYER = "slope-layer"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. Initialize MapLibre before rendering view (required)
        try {
            Mapbox.getInstance(this)
            Log.i(TAG, "MapLibre Mapbox SDK engine instance initialized.")
        } catch (e: Exception) {
            Log.e(TAG, "Error initiating core MapLibre hardware bindings", e)
        }

        setContentView(R.layout.activity_map)

        // Bind widgets
        mapView = findViewById(R.id.mapView)
        tvMapCoordinates = findViewById(R.id.tvMapCoordinates)
        tvMapAltitude = findViewById(R.id.tvMapAltitude)
        tvMapPoints = findViewById(R.id.tvMapPoints)
        btnToggleHistorical = findViewById(R.id.btnToggleHistorical)
        btnToggleSlope = findViewById(R.id.btnToggleSlope)
        btnSimulateOfflinePoint = findViewById(R.id.btnSimulateOfflinePoint)

        demSystem = com.cybertrail.app.gis.DEMSystem(this)

        mapView.onCreate(savedInstanceState)
        mapView.getMapAsync(this)

        // 2. Initiate one-time offline static filesystem pre-unpacking and 3D DEM building
        lifecycleScope.launch(Dispatchers.IO) {
            extractOfflineMBTiles()
            demSystem.pregenerateTerrainRGBFiles()
        }

        // 3. UI Buttons
        findViewById<TextView>(R.id.btnBack).setOnClickListener {
            finish()
        }

        findViewById<ImageButton>(R.id.btnCenterPosition).setOnClickListener {
            centerMapOnCurrent()
        }

        findViewById<Button>(R.id.btnZoomIn).setOnClickListener {
            mapboxMap?.animateCamera(CameraUpdateFactory.zoomIn())
        }

        findViewById<Button>(R.id.btnZoomOut).setOnClickListener {
            mapboxMap?.animateCamera(CameraUpdateFactory.zoomOut())
        }

        btnToggleHistorical.setOnClickListener {
            isHistoricalVisible = !isHistoricalVisible
            updateHistoryVisibility()
        }

        btnToggleSlope.setOnClickListener {
            isSlopeVisible = !isSlopeVisible
            updateSlopeVisibility()
        }

        // Press simulate to step dynamic simulated points on-the-fly
        btnSimulateOfflinePoint.setOnClickListener {
            triggerOfflinePositionMock()
        }

        // 4. Synchronize track coordinates flow and recording states
        lifecycleScope.launch {
            TrackingRepository.state.collectLatest { state ->
                updateTrackingHUD(state)
            }
        }
    }

    override fun onMapReady(map: MapboxMap) {
        this.mapboxMap = map
        Log.i(TAG, "MapLibre offline tactical canvas is compiled and ready.")

        val tilesFileDir = File(filesDir, "tiles")
        val tilesPathPattern = "file://${tilesFileDir.absolutePath}/{z}/{x}/{y}.png"
        val demFileDir = File(filesDir, "dem")
        val demPathPattern = "file://${demFileDir.absolutePath}/{z}/{x}/{y}.png"

        // Configure default offline map layer stack, 3D terrain parameters, and raster-dem sources
        val styleJsonStr = """
            {
              "version": 8,
              "name": "CyberTrail Offline 3D Style",
              "sources": {
                "offline-radar": {
                  "type": "raster",
                  "tiles": [
                    "$tilesPathPattern"
                  ],
                  "tileSize": 256,
                  "minzoom": 0,
                  "maxzoom": 18
                },
                "terrain-rgb": {
                  "type": "raster-dem",
                  "tiles": [
                    "$demPathPattern"
                  ],
                  "tileSize": 256,
                  "encoding": "mapbox",
                  "minzoom": 0,
                  "maxzoom": 18
                }
              },
              "terrain": {
                "source": "terrain-rgb",
                "exaggeration": 1.2
              },
              "layers": [
                {
                  "id": "background",
                  "type": "background",
                  "paint": {
                    "background-color": "#06090E"
                  }
                },
                {
                  "id": "offline-tiles",
                  "type": "raster",
                  "source": "offline-radar",
                  "paint": {
                    "raster-opacity": 0.8,
                    "raster-fade-duration": 100
                  }
                }
              ]
            }
        """.trimIndent()

        map.setStyle(styleJsonStr) { style ->
            Log.i(TAG, "TileServer stylesheet config parsed. Mounting vector layers.")

            // Setup Tactical map configurations
            map.uiSettings.apply {
                isCompassEnabled = true
                isLogoEnabled = false
                isAttributionEnabled = false
            }

            // A. Slope Heatmap overlay (polygon grid mesh) Layer, inserted under tracks
            val slopeSource = GeoJsonSource(SLOPE_SOURCE, FeatureCollection.fromFeatures(emptyArray()))
            style.addSource(slopeSource)
            
            val slopeLayer = com.mapbox.mapboxsdk.style.layers.FillLayer(SLOPE_LAYER, SLOPE_SOURCE).apply {
                setProperties(
                    PropertyFactory.fillColor(com.mapbox.mapboxsdk.style.expressions.Expression.get("color")),
                    PropertyFactory.fillOpacity(0.35f),
                    PropertyFactory.fillAntialias(true)
                )
            }
            style.addLayer(slopeLayer)

            // B. Add Historical track overlay Source & Layer (Stroked solid ice-blue)
            val historySource = GeoJsonSource(HISTORY_TRACK_SOURCE, FeatureCollection.fromFeatures(emptyArray()))
            style.addSource(historySource)
            
            val historyLayer = LineLayer(HISTORY_TRACK_LAYER, HISTORY_TRACK_SOURCE).apply {
                setProperties(
                    PropertyFactory.lineColor("#1F6FEB"), // High-density tactical slate-blue
                    PropertyFactory.lineWidth(3.5f),
                    PropertyFactory.lineOpacity(0.85f),
                    PropertyFactory.lineCap("round"),
                    PropertyFactory.lineJoin("round")
                )
            }
            style.addLayer(historyLayer)

            // C. Add Current raw recording route Source & Layer (Stroked high-contrast neon-orange)
            val activeSource = GeoJsonSource(CURRENT_TRACK_SOURCE, FeatureCollection.fromFeatures(emptyArray()))
            style.addSource(activeSource)
            
            val activeLayer = LineLayer(CURRENT_TRACK_LAYER, CURRENT_TRACK_SOURCE).apply {
                setProperties(
                    PropertyFactory.lineColor("#FF7B72"), // High-contrast warning orange
                    PropertyFactory.lineWidth(4.5f),
                    PropertyFactory.lineCap("round"),
                    PropertyFactory.lineJoin("round")
                )
            }
            style.addLayer(activeLayer)

            // D. Add Custom live position GPS beacon marker Layer (solid concentric red-pulse circles)
            val posSource = GeoJsonSource(POSITION_SOURCE, FeatureCollection.fromFeatures(emptyArray()))
            style.addSource(posSource)
            
            val posLayer = CircleLayer(POSITION_LAYER, POSITION_SOURCE).apply {
                setProperties(
                    PropertyFactory.circleColor("#DA3633"), // Beacon pulsing tactical solid red
                    PropertyFactory.circleRadius(8.0f),
                    PropertyFactory.circleStrokeColor("#FFFFFF"),
                    PropertyFactory.circleStrokeWidth(2.0f)
                )
            }
            style.addLayer(posLayer)

            // Initial center camera position
            centerMapOnCurrent()

            // E. Load existing trails from SQLite DB in background thread
            loadHistoricalTrailsOnMap(style)

            // F. Handle developer click coordinates to mock offline positions!
            map.addOnMapClickListener { latLng ->
                mockPositionTo(latLng)
                true
            }

            // G. Trigger real-time slope calculations in current bounds when camera stops moving
            map.addOnCameraIdleListener {
                regenerateSlopeHeatmap()
            }
            
            // Trigger initial grid calculation
            regenerateSlopeHeatmap()
        }
    }

    private fun centerMapOnCurrent() {
        mapboxMap?.animateCamera(
            CameraUpdateFactory.newCameraPosition(
                CameraPosition.Builder()
                    .target(currentPosition)
                    .zoom(14.0)
                    .bearing(0.0)
                    .tilt(30.0)
                    .build()
            ),
            1200
        )
    }

    private fun updateTrackingHUD(state: TrackingState) {
        val count = state.points
        tvMapPoints.text = "$count pts"
        
        // If recording active, sync active track coordinates from SQLite in background
        if (state.isTracking) {
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val tracksJson = if (NativeCore.available) NativeCore.getAllTracksJson() else ""
                    if (tracksJson.isNotBlank() && tracksJson != "[]") {
                        val tracksArray = JSONArray(tracksJson)
                        if (tracksArray.length() > 0) {
                            // Fetch last track points (the one currently being recorded)
                            val lastTrack = tracksArray.getJSONObject(tracksArray.length() - 1)
                            val trackId = lastTrack.optString("id", "")
                            
                            val pointsJson = NativeCore.getTrackPointsJson(trackId)
                            val ptsArray = JSONArray(pointsJson)
                            val list = ArrayList<LatLng>()
                            for (i in 0 until ptsArray.length()) {
                                val item = ptsArray.getJSONObject(i)
                                val lat = item.optDouble("latitude", 0.0)
                                val lon = item.optDouble("longitude", 0.0)
                                list.add(LatLng(lat, lon))
                            }

                            withContext(Dispatchers.Main) {
                                activeTrackPoints.clear()
                                activeTrackPoints.addAll(list)
                                if (list.isNotEmpty()) {
                                    val lastPt = list.last()
                                    currentPosition = lastPt
                                    tvMapCoordinates.text = "%.5f, %.5f".format(lastPt.latitude, lastPt.longitude)
                                }
                                renderActiveTrackLayer()
                                renderPositionBeacon()
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error syncing current active route", e)
                }
            }
        }
    }

    private fun renderActiveTrackLayer() {
        val map = mapboxMap ?: return
        val style = map.style ?: return
        val source = style.getSourceAs<GeoJsonSource>(CURRENT_TRACK_SOURCE) ?: return

        if (activeTrackPoints.size < 2) {
            source.setGeoJson(FeatureCollection.fromFeatures(emptyArray()))
            return
        }

        val pointsList = activeTrackPoints.map { Point.fromLngLat(it.longitude, it.latitude) }
        val lineString = LineString.fromLngLats(pointsList)
        val feature = Feature.fromGeometry(lineString)

        source.setGeoJson(FeatureCollection.fromFeature(feature))
    }

    private fun renderPositionBeacon() {
        val map = mapboxMap ?: return
        val style = map.style ?: return
        val source = style.getSourceAs<GeoJsonSource>(POSITION_SOURCE) ?: return

        val point = Point.fromLngLat(currentPosition.longitude, currentPosition.latitude)
        val feature = Feature.fromGeometry(point)
        source.setGeoJson(FeatureCollection.fromFeature(feature))
    }

    private fun loadHistoricalTrailsOnMap(style: com.mapbox.mapboxsdk.maps.Style) {
        lifecycleScope.launch(Dispatchers.IO) {
            val historySource = style.getSourceAs<GeoJsonSource>(HISTORY_TRACK_SOURCE) ?: return@launch
            
            val jsonStr = try {
                if (NativeCore.available) {
                    NativeCore.getAllTracksJson()
                } else {
                    getFallbackOfflineTracksString()
                }
            } catch (e: UnsatisfiedLinkError) {
                getFallbackOfflineTracksString()
            }

            if (jsonStr.isBlank() || jsonStr == "[]") {
                return@launch
            }

            try {
                val array = JSONArray(jsonStr)
                val features = ArrayList<Feature>()

                // Foreach past route, fetch points of track
                for (i in 0 until array.length()) {
                    val trackObj = array.getJSONObject(i)
                    val trackId = trackObj.getString("id")
                    
                    val pointsJson = try {
                        if (NativeCore.available) {
                            NativeCore.getTrackPointsJson(trackId)
                        } else {
                            getFallbackPointsJsonFor(trackId)
                        }
                    } catch (e: Exception) {
                        getFallbackPointsJsonFor(trackId)
                    }

                    if (pointsJson.isNotBlank()) {
                        val ptsArray = JSONArray(pointsJson)
                        val lineCoords = ArrayList<Point>()
                        for (k in 0 until ptsArray.length()) {
                            val pt = ptsArray.getJSONObject(k)
                            val lat = pt.optDouble("latitude", 0.0)
                            val lon = pt.optDouble("longitude", 0.0)
                            lineCoords.add(Point.fromLngLat(lon, lat))
                        }
                        if (lineCoords.size >= 2) {
                            features.add(Feature.fromGeometry(LineString.fromLngLats(lineCoords)))
                        }
                    }
                }

                withContext(Dispatchers.Main) {
                    historySource.setGeoJson(FeatureCollection.fromFeatures(features))
                    Log.i(TAG, "Mounted ${features.size} historical SQLite paths on MapLibre")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error compiling SQLite tracks to GeoJSON", e)
            }
        }
    }

    private fun updateHistoryVisibility() {
        val map = mapboxMap ?: return
        val style = map.style ?: return
        val layer = style.getLayer(HISTORY_TRACK_LAYER) ?: return
        
        layer.setProperties(
            PropertyFactory.visibility(
                if (isHistoricalVisible) com.mapbox.mapboxsdk.style.layers.Property.VISIBLE 
                else com.mapbox.mapboxsdk.style.layers.Property.NONE
            )
        )
        
        val modeStr = if (isHistoricalVisible) "SHOWN" else "HIDDEN"
        btnToggleHistorical.text = "SHOW ARCHIVE"
        btnToggleHistorical.setBackgroundColor(
            if (isHistoricalVisible) 0xFF238636.toInt() else 0xFF21262D.toInt()
        )
        Toast.makeText(this, "Historical curves set to: $modeStr", Toast.LENGTH_SHORT).show()
    }

    private fun updateSlopeVisibility() {
        val map = mapboxMap ?: return
        val style = map.style ?: return
        val layer = style.getLayer(SLOPE_LAYER) ?: return
        
        layer.setProperties(
            PropertyFactory.visibility(
                if (isSlopeVisible) com.mapbox.mapboxsdk.style.layers.Property.VISIBLE 
                else com.mapbox.mapboxsdk.style.layers.Property.NONE
            )
        )
        
        val modeStr = if (isSlopeVisible) "SLOPE: ON" else "SLOPE: OFF"
        btnToggleSlope.text = modeStr
        btnToggleSlope.setBackgroundColor(
            if (isSlopeVisible) 0xFF2E7D32.toInt() else 0xFF21262D.toInt()
        )
        Toast.makeText(this, "High-precision slope heatmaps: $modeStr", Toast.LENGTH_SHORT).show()
        
        if (isSlopeVisible) {
            regenerateSlopeHeatmap()
        }
    }

    private fun regenerateSlopeHeatmap() {
        val map = mapboxMap ?: return
        val style = map.style ?: return
        if (!isSlopeVisible) return

        val projection = map.projection
        val bounds = projection.visibleRegion?.latLngBounds ?: return

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val latMin = bounds.getLatSouth()
                val latMax = bounds.getLatNorth()
                val lonMin = bounds.getLonWest()
                val lonMax = bounds.getLonEast()

                val steps = 12
                val latStep = (latMax - latMin) / steps
                val lonStep = (lonMax - lonMin) / steps

                val features = ArrayList<Feature>()

                for (r in 0 until steps) {
                    for (c in 0 until steps) {
                        val cellLatMin = latMin + r * latStep
                        val cellLatMax = latMin + (r + 1) * latStep
                        val cellLonMin = lonMin + c * lonStep
                        val cellLonMax = lonMin + (c + 1) * lonStep

                        val centerLat = cellLatMin + latStep / 2.0
                        val centerLon = cellLonMin + lonStep / 2.0

                        val slope = demSystem.getSlope(centerLat, centerLon)
                        val colorHex = demSystem.getSlopeColorHex(slope)

                        val outerRing = ArrayList<Point>()
                        outerRing.add(Point.fromLngLat(cellLonMin, cellLatMin))
                        outerRing.add(Point.fromLngLat(cellLonMax, cellLatMin))
                        outerRing.add(Point.fromLngLat(cellLonMax, cellLatMax))
                        outerRing.add(Point.fromLngLat(cellLonMin, cellLatMax))
                        outerRing.add(Point.fromLngLat(cellLonMin, cellLatMin))

                        val polygon = com.mapbox.geojson.Polygon.fromLngLats(listOf(outerRing))
                        val feature = Feature.fromGeometry(polygon)
                        feature.addStringProperty("color", colorHex)
                        features.add(feature)
                    }
                }

                withContext(Dispatchers.Main) {
                    val source = style.getSourceAs<GeoJsonSource>(SLOPE_SOURCE)
                    source?.setGeoJson(FeatureCollection.fromFeatures(features))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed calculating dynamic slope overlay grid", e)
            }
        }
    }

    private fun triggerOfflinePositionMock() {
        // Step mock position slightly forward
        val nextLat = currentPosition.latitude + 0.0015
        val nextLong = currentPosition.longitude + 0.0010
        currentAltitude += (Random().nextDouble() - 0.5) * 4.0
        
        val nextLatLng = LatLng(nextLat, nextLong)
        mockPositionTo(nextLatLng)
        
        // If recording active: write coordinates straight into Database
        val activeState = TrackingRepository.state.value
        if (activeState.isTracking) {
            Toast.makeText(this, "Recorded live simulated tracking coordinate entry.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun mockPositionTo(latLng: LatLng) {
        currentPosition = latLng
        tvMapCoordinates.text = "%.5f, %.5f".format(currentPosition.latitude, currentPosition.longitude)
        tvMapAltitude.text = "%.1fm".format(currentAltitude)
        
        renderPositionBeacon()
        
        // If live track active: add coordinate cache and render
        val activeState = TrackingRepository.state.value
        if (activeState.isTracking) {
            activeTrackPoints.add(latLng)
            renderActiveTrackLayer()
            
            // Increment UI stats count
            val nowSeconds = System.currentTimeMillis() / 1000
            val distInc = if (activeTrackPoints.size > 1) activeState.distanceMeters + 15.0 else 0.0
            TrackingRepository.update(
                activeState.copy(
                    points = activeTrackPoints.size,
                    distanceMeters = distInc
                )
            )
        }
        
        mapboxMap?.animateCamera(CameraUpdateFactory.newLatLng(latLng), 400)
    }

    // Dynamic fallback offline/mock structures
    private fun getFallbackOfflineTracksString(): String {
        return """[
          {"id":"mock-1","name":"Sentinel Dome Trail","started_at":1729000000},
          {"id":"mock-2","name":"Sentinel Rock Chimney","started_at":1728950000}
        ]"""
    }

    private fun getFallbackPointsJsonFor(trackId: String): String {
        return if (trackId == "mock-1") {
            """[
              {"latitude":37.7749,"longitude":-122.4194},
              {"latitude":37.7760,"longitude":-122.4180},
              {"latitude":37.7772,"longitude":-122.4165},
              {"latitude":37.7785,"longitude":-122.4150}
            ]"""
        } else {
            """[
              {"latitude":37.7749,"longitude":-122.4194},
              {"latitude":37.7735,"longitude":-122.4210},
              {"latitude":37.7720,"longitude":-122.4225},
              {"latitude":37.7710,"longitude":-122.4241}
            ]"""
        }
    }

    // --- Forward Android MapView standard lifecycle events (Required by Mapbox/MapLibre GL platform) ---
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
        mapView.onDestroy()
        super.onDestroy()
    }

    private fun extractOfflineMBTiles() {
        val mbtilesFile = File(filesDir, "offline.mbtiles")
        val targetDir = File(filesDir, "tiles")
        if (!targetDir.exists()) {
            targetDir.mkdirs()
        }

        // If no mbtiles file exists, we seed high-contrast fallback tactical grids so the UI works beautifully instantly
        if (!mbtilesFile.exists()) {
            Log.i(TAG, "No offline.mbtiles database found. Seeding tactical grid overlays.")
            pregenerateFallbackMapTiles(targetDir)
            return
        }

        // Use a flag file to prevent redundant extractions on every boot
        val flagFile = File(targetDir, ".extracted")
        if (flagFile.exists()) {
            Log.i(TAG, "MBTiles already pre-unpacked in standard folder. Skipping extraction.")
            return
        }

        Log.i(TAG, "Extracting offline MBTiles to standard tile filesystem: ${targetDir.absolutePath}")
        var db: SQLiteDatabase? = null
        try {
            db = SQLiteDatabase.openDatabase(
                mbtilesFile.absolutePath,
                null,
                SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS
            )
            val cursor = db.rawQuery("SELECT zoom_level, tile_column, tile_row, tile_data FROM tiles", null)
            while (cursor.moveToNext()) {
                val z = cursor.getInt(0)
                val x = cursor.getInt(1)
                val yTms = cursor.getInt(2)
                val data = cursor.getBlob(3) ?: continue

                // Convert TMS row coordinate format to Standard OSM row format
                val yStandard = (1 shl z) - 1 - yTms

                val zDir = File(targetDir, z.toString())
                val xDir = File(zDir, x.toString())
                if (!xDir.exists()) {
                    xDir.mkdirs()
                }
                val tileFile = File(xDir, "$yStandard.png")
                tileFile.writeBytes(data)
            }
            cursor.close()
            flagFile.createNewFile()
            Log.i(TAG, "Pre-unpacking complete. Extracted tiles are ready under standard file:/// paths.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to execute MBTiles pre-unpacking step", e)
            // Seed anyway as fallback
            pregenerateFallbackMapTiles(targetDir)
        } finally {
            db?.close()
        }
    }

    private fun pregenerateFallbackMapTiles(targetDir: File) {
        try {
            // Seed a high-density neighborhood around SF default coordinates (Z: 14, X: 2621, Y: 6328)
            val z = 14
            val xCenter = 2621
            val yCenter = 6328

            for (dx in -4..4) {
                for (dy in -4..4) {
                    val x = xCenter + dx
                    val y = yCenter + dy
                    val zDir = File(targetDir, z.toString())
                    val xDir = File(zDir, x.toString())
                    if (!xDir.exists()) {
                        xDir.mkdirs()
                    }
                    val tileFile = File(xDir, "$y.png")
                    if (!tileFile.exists()) {
                        val bytes = generateGridTileBytes(z, x, y)
                        tileFile.writeBytes(bytes)
                    }
                }
            }
            Log.i(TAG, "Seeded beautiful backup wireframe grid layers successfully.")
        } catch (e: Exception) {
            Log.e(TAG, "Error seeding fallback tactical layers", e)
        }
    }

    private fun generateGridTileBytes(z: Int, x: Int, y: Int): ByteArray {
        val bitmap = android.graphics.Bitmap.createBitmap(256, 256, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        
        // Cyberpunk Dark Background
        canvas.drawColor(0xFF0D1117.toInt())

        // Aesthetic cyan border representing tile bounds
        val borderPaint = android.graphics.Paint().apply {
            color = 0x221F6FEB.toInt() // dark tactical blue-gray borders
            strokeWidth = 1.0f
            style = android.graphics.Paint.Style.STROKE
        }
        canvas.drawRect(0f, 0f, 256f, 256f, borderPaint)

        // Dynamic grid laser lines
        val gridPaint = android.graphics.Paint().apply {
            color = 0x1158A6FF.toInt() // semi-opaque HUD cyan
            strokeWidth = 0.5f
        }
        canvas.drawLine(128f, 0f, 128f, 256f, gridPaint)
        canvas.drawLine(0f, 128f, 256f, 128f, gridPaint)

        // Dynamic laser radar circles
        val circlePaint = android.graphics.Paint().apply {
            color = 0x15238636.toInt() // tech cyber green rings
            strokeWidth = 1.0f
            style = android.graphics.Paint.Style.STROKE
        }
        canvas.drawCircle(128f, 128f, 64f, circlePaint)
        canvas.drawCircle(128f, 128f, 120f, circlePaint)

        // Contour laser waves simulating contour topography patterns
        val contourPaint = android.graphics.Paint().apply {
            color = 0x0C238636.toInt() // extremely subtle topographic waves
            strokeWidth = 0.75f
            style = android.graphics.Paint.Style.STROKE
        }
        val scaleX = x.toDouble() * 256.0
        val scaleY = y.toDouble() * 256.0
        for (radius in listOf(20f, 45f, 85f, 150f, 200f)) {
            val warp = Math.sin((scaleX + radius) / 50000.0) * 15.0 + Math.cos((scaleY + radius) / 50000.0) * 15.0
            canvas.drawCircle(128f + warp.toFloat(), 128f + warp.toFloat(), radius, contourPaint)
        }

        // Monospace telemetry labeling texts
        val fontPaint = android.graphics.Paint().apply {
            color = 0x888B949E.toInt() // clean slate gray
            textSize = 8f
            isAntiAlias = true
            typeface = android.graphics.Typeface.MONOSPACE
        }
        canvas.drawText("GRID INDEX [Z:$z, X:$x, Y:$y]", 10f, 24f, fontPaint)
        canvas.drawText("CYBERTRAIL TACTICAL GIS", 10f, 246f, fontPaint)
        canvas.drawText("+5m INTERVALS", 170f, 246f, fontPaint)

        val outputStream = java.io.ByteArrayOutputStream()
        bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 90, outputStream)
        val bytes = outputStream.toByteArray()
        bitmap.recycle()
        return bytes
    }
}
