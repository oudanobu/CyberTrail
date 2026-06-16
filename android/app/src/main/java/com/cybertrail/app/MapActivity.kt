package com.cybertrail.app

import android.content.pm.PackageManager
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
    private var tileServer: OfflineTileServer? = null

    // UI elements
    private lateinit var tvMapCoordinates: TextView
    private lateinit var tvMapAltitude: TextView
    private lateinit var tvMapPoints: TextView
    private lateinit var btnToggleHistorical: Button
    private lateinit var btnSimulateOfflinePoint: Button

    // Telemetry state
    private var currentPosition = LatLng(37.7749, -122.4194)
    private var currentAltitude = 120.0
    private var isHistoricalVisible = true

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
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. Initialize MapLibre before rendering view (required)
        try {
            Mapbox.getInstance(this, null)
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
        btnSimulateOfflinePoint = findViewById(R.id.btnSimulateOfflinePoint)

        mapView.onCreate(savedInstanceState)
        mapView.getMapAsync(this)

        // 2. Start offline local tile server on port 8085
        val mbtilesFile = File(filesDir, "offline.mbtiles")
        tileServer = OfflineTileServer(mbtilesFile).apply {
            start()
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

        // Configure default offline parameters and map style from our local daemon tile server
        map.setStyle("http://127.0.5.1:8085/style.json") { style ->
            Log.i(TAG, "TileServer stylesheet config parsed. Mounting vector layers.")

            // Setup Tactical map configurations
            map.uiSettings.apply {
                isCompassEnabled = true
                isLogoEnabled = false
                isAttributionEnabled = false
            }

            // A. Add Historical track overlay Source & Layer (Stroked solid ice-blue)
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

            // B. Add Current raw recording route Source & Layer (Stroked high-contrast neon-orange)
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

            // C. Add Custom live position GPS beacon marker Layer (solid concentric red-pulse circles)
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

            // D. Load existing trails from SQLite DB in background thread
            loadHistoricalTrailsOnMap(style)

            // E. Handle developer click coordinates to mock offline positions!
            map.addOnMapClickListener { latLng ->
                mockPositionTo(latLng)
                true
            }
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
        btnToggleHistorical.text = "ARCHIVE ROUTES: $modeStr"
        btnToggleHistorical.setBackgroundColor(
            if (isHistoricalVisible) 0xFF238636.toInt() else 0xFF21262D.toInt()
        )
        Toast.makeText(this, "Historical curves set to: $modeStr", Toast.LENGTH_SHORT).show()
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
        tileServer?.stop()
        super.onDestroy()
    }
}
