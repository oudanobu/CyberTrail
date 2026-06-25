package com.cybertrail.app.gis

import android.content.Context
import android.util.Log
import com.cybertrail.app.db.Track
import com.cybertrail.app.db.TrackPoint
import com.cybertrail.app.db.TrackDao
import com.cybertrail.app.db.PhotoAnchor
import org.json.JSONArray
import org.json.JSONObject
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import javax.xml.parsers.DocumentBuilderFactory

object TrackFileHelper {
    private const val TAG = "TrackFileHelper"
    private const val BASE_PATH = "/storage/emulated/0/CyberTrail"
    private const val TRACKS_PATH = "$BASE_PATH/Tracks"
    private const val EXPORT_PATH = "$BASE_PATH/Export"

    fun ensureDirectories() {
        try {
            val baseDir = File(BASE_PATH)
            if (!baseDir.exists()) baseDir.mkdirs()

            val tracksDir = File(TRACKS_PATH)
            if (!tracksDir.exists()) tracksDir.mkdirs()

            val exportDir = File(EXPORT_PATH)
            if (!exportDir.exists()) exportDir.mkdirs()
        } catch (e: Exception) {
            Log.e(TAG, "Error ensuring directories exist", e)
        }
    }

    fun getTracksDirectory(): File = File(TRACKS_PATH)
    fun getExportDirectory(): File = File(EXPORT_PATH)

    /**
     * Saves a track to disk as a .track (JSON) file under CyberTrail/Tracks/
     */
    fun saveTrackToJson(track: Track, points: List<TrackPoint>, photoAnchors: List<PhotoAnchor> = emptyList()): File? {
        ensureDirectories()
        try {
            val json = JSONObject()
            val trackJson = JSONObject().apply {
                put("id", track.id)
                put("startTime", track.startTime)
                put("endTime", track.endTime ?: JSONObject.NULL)
                put("status", track.status)
                put("name", track.name ?: JSONObject.NULL)
                
                // Photo anchors
                val photoArray = JSONArray()
                for (anchor in photoAnchors) {
                    val pJson = JSONObject().apply {
                        put("id", anchor.id)
                        put("trackId", anchor.trackId)
                        put("latitude", anchor.latitude)
                        put("longitude", anchor.longitude)
                        put("elevation", anchor.elevation ?: JSONObject.NULL)
                        put("timestamp", anchor.timestamp)
                        put("photoPath", anchor.photoPath)
                    }
                    photoArray.put(pJson)
                }
                put("photoRefs", photoArray)
                put("note", "")
                put("waypoint", JSONArray())
                put("sensorData", JSONObject())
            }
            json.put("track", trackJson)

            val pointsArray = JSONArray()
            for (pt in points) {
                val ptJson = JSONObject().apply {
                    put("id", pt.id)
                    put("trackId", pt.trackId)
                    put("latitude", pt.latitude)
                    put("longitude", pt.longitude)
                    put("elevation", pt.elevation ?: JSONObject.NULL)
                    put("timestamp", pt.timestamp)
                    put("speed", pt.speed)
                    put("accuracy", pt.accuracy)
                }
                pointsArray.put(ptJson)
            }
            json.put("points", pointsArray)

            val file = File(TRACKS_PATH, "track_${track.id}.track")
            file.writeText(json.toString(2))
            Log.d(TAG, "Successfully saved track ${track.id} to JSON file: ${file.absolutePath}")
            return file
        } catch (e: Exception) {
            Log.e(TAG, "Error saving track ${track.id} to JSON file", e)
            return null
        }
    }

    /**
     * Reads a .track (JSON) file from disk and parses it into Track, List<TrackPoint>, and List<PhotoAnchor>
     */
    fun readTrackFromJson(file: File): Triple<Track, List<TrackPoint>, List<PhotoAnchor>>? {
        try {
            if (!file.exists()) return null
            val content = file.readText()
            val json = JSONObject(content)
            
            val trackJson = json.getJSONObject("track")
            val id = trackJson.getLong("id")
            val startTime = trackJson.getLong("startTime")
            val endTime = if (trackJson.isNull("endTime")) null else trackJson.getLong("endTime")
            val status = trackJson.getString("status")
            val name = if (trackJson.isNull("name")) null else trackJson.getString("name")

            val track = Track(id = id, startTime = startTime, endTime = endTime, status = status, name = name)

            val pointsList = mutableListOf<TrackPoint>()
            if (json.has("points")) {
                val pointsArray = json.getJSONArray("points")
                for (i in 0 until pointsArray.length()) {
                    val ptJson = pointsArray.getJSONObject(i)
                    val ptId = if (ptJson.has("id")) ptJson.getLong("id") else 0L
                    val ptTrackId = ptJson.getLong("trackId")
                    val latitude = ptJson.getDouble("latitude")
                    val longitude = ptJson.getDouble("longitude")
                    val elevation = if (ptJson.isNull("elevation")) null else ptJson.getDouble("elevation")
                    val timestamp = ptJson.getLong("timestamp")
                    val speed = ptJson.optDouble("speed", 0.0).toFloat()
                    val accuracy = ptJson.optDouble("accuracy", 0.0).toFloat()

                    pointsList.add(TrackPoint(
                        id = ptId,
                        trackId = ptTrackId,
                        latitude = latitude,
                        longitude = longitude,
                        elevation = elevation,
                        timestamp = timestamp,
                        speed = speed,
                        accuracy = accuracy
                    ))
                }
            }

            val photoList = mutableListOf<PhotoAnchor>()
            if (trackJson.has("photoRefs")) {
                val photoArray = trackJson.getJSONArray("photoRefs")
                for (i in 0 until photoArray.length()) {
                    val pJson = photoArray.getJSONObject(i)
                    val pId = if (pJson.has("id")) pJson.getLong("id") else 0L
                    val pTrackId = pJson.getLong("trackId")
                    val latitude = pJson.getDouble("latitude")
                    val longitude = pJson.getDouble("longitude")
                    val elevation = if (pJson.isNull("elevation")) null else pJson.getDouble("elevation")
                    val timestamp = pJson.getLong("timestamp")
                    val photoPath = pJson.getString("photoPath")

                    photoList.add(PhotoAnchor(
                        id = pId,
                        trackId = pTrackId,
                        latitude = latitude,
                        longitude = longitude,
                        elevation = elevation,
                        timestamp = timestamp,
                        photoPath = photoPath
                    ))
                }
            }

            return Triple(track, pointsList, photoList)
        } catch (e: Exception) {
            Log.e(TAG, "Error reading track from JSON file: ${file.absolutePath}", e)
            return null
        }
    }

    /**
     * Scans `/storage/emulated/0/CyberTrail/Tracks/` for `.track` files with status != "STOPPED"
     * Returns a list of files that were not properly ended.
     */
    fun scanUncompletedTrackFiles(): List<File> {
        ensureDirectories()
        val list = mutableListOf<File>()
        try {
            val dir = File(TRACKS_PATH)
            val files = dir.listFiles { _, name -> name.endsWith(".track") } ?: return emptyList()
            for (file in files) {
                try {
                    val content = file.readText()
                    val json = JSONObject(content)
                    if (json.has("track")) {
                        val trackJson = json.getJSONObject("track")
                        val status = trackJson.optString("status", "STOPPED")
                        if (status == "RECORDING" || status == "PAUSED") {
                            list.add(file)
                        }
                    }
                } catch (e: Exception) {
                    // Ignore malformed files
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error scanning uncompleted track files", e)
        }
        return list
    }

    /**
     * Exports a track directly from a .track JSON file to GPX
     */
    fun exportTrackFileToGpx(trackFile: File): File? {
        val parsed = readTrackFromJson(trackFile) ?: return null
        return exportTrack(parsed.first, parsed.second, "GPX")
    }

    /**
     * Delete the .track file associated with a track ID
     */
    fun deleteTrackJsonFile(trackId: Long) {
        try {
            val file = File(TRACKS_PATH, "track_${trackId}.track")
            if (file.exists()) {
                file.delete()
                Log.d(TAG, "Deleted JSON track file: ${file.absolutePath}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting JSON file for track $trackId", e)
        }
    }

    /**
     * Exports a track to standard GPX 1.1 format under CyberTrail/Export/
     */
    fun exportToGpx(track: Track, points: List<TrackPoint>): File? {
        return exportTrack(track, points, "GPX")
    }

    /**
     * Exports a track to GPX, KML, or JSON format under `/storage/emulated/0/CyberTrail/Tracks/`
     * Filename format: YYYYMMDD_HHMMSS_track.<ext>
     */
    fun exportTrack(track: Track, points: List<TrackPoint>, format: String): File? {
        ensureDirectories()
        try {
            val fileSdf = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
            val timeString = fileSdf.format(Date(track.startTime))
            val ext = format.lowercase(Locale.US)
            val file = File(TRACKS_PATH, "${timeString}_track.$ext")

            when (format.uppercase(Locale.US)) {
                "GPX" -> {
                    val sb = java.lang.StringBuilder()
                    sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
                    sb.append("<gpx version=\"1.1\" creator=\"CyberTrail\"\n")
                    sb.append("     xmlns=\"http://www.topografix.com/GPX/1/1\"\n")
                    sb.append("     xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n")
                    sb.append("     xsi:schemaLocation=\"http://www.topografix.com/GPX/1/1 http://www.topografix.com/GPX/1/1/gpx.xsd\">\n")

                    val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
                    sdf.timeZone = TimeZone.getTimeZone("UTC")

                    sb.append("  <metadata>\n")
                    sb.append("    <name><![CDATA[${track.name ?: "Track #${track.id}"}]]></name>\n")
                    sb.append("    <time>${sdf.format(Date(track.startTime))}</time>\n")
                    sb.append("  </metadata>\n")

                    sb.append("  <trk>\n")
                    sb.append("    <name><![CDATA[${track.name ?: "Track #${track.id}"}]]></name>\n")
                    sb.append("    <trkseg>\n")

                    for (pt in points) {
                        sb.append("      <trkpt lat=\"${pt.latitude}\" lon=\"${pt.longitude}\">\n")
                        if (pt.elevation != null) {
                            sb.append("        <ele>${pt.elevation}</ele>\n")
                        }
                        sb.append("        <time>${sdf.format(Date(pt.timestamp))}</time>\n")
                        if (pt.speed > 0f) {
                            sb.append("        <speed>${pt.speed}</speed>\n")
                        }
                        sb.append("      </trkpt>\n")
                    }

                    sb.append("    </trkseg>\n")
                    sb.append("  </trk>\n")
                    sb.append("</gpx>\n")

                    file.writeText(sb.toString())
                }
                "KML" -> {
                    val sb = java.lang.StringBuilder()
                    sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
                    sb.append("<kml xmlns=\"http://www.opengis.net/kml/2.2\">\n")
                    sb.append("  <Document>\n")
                    sb.append("    <name><![CDATA[${track.name ?: "Track #${track.id}"}]]></name>\n")
                    sb.append("    <Placemark>\n")
                    sb.append("      <name>Path</name>\n")
                    sb.append("      <LineString>\n")
                    sb.append("        <extrude>1</extrude>\n")
                    sb.append("        <tessellate>1</tessellate>\n")
                    sb.append("        <altitudeMode>clampToGround</altitudeMode>\n")
                    sb.append("        <coordinates>\n")

                    for (pt in points) {
                        sb.append("          ${pt.longitude},${pt.latitude},${pt.elevation ?: 0.0}\n")
                    }

                    sb.append("        </coordinates>\n")
                    sb.append("      </LineString>\n")
                    sb.append("    </Placemark>\n")
                    sb.append("  </Document>\n")
                    sb.append("</kml>\n")

                    file.writeText(sb.toString())
                }
                "JSON" -> {
                    val json = JSONObject()
                    val trackJson = JSONObject().apply {
                        put("id", track.id)
                        put("startTime", track.startTime)
                        put("endTime", track.endTime ?: JSONObject.NULL)
                        put("status", track.status)
                        put("name", track.name ?: JSONObject.NULL)
                    }
                    json.put("track", trackJson)

                    val pointsArray = JSONArray()
                    for (pt in points) {
                        val ptJson = JSONObject().apply {
                            put("id", pt.id)
                            put("trackId", pt.trackId)
                            put("latitude", pt.latitude)
                            put("longitude", pt.longitude)
                            put("elevation", pt.elevation ?: JSONObject.NULL)
                            put("timestamp", pt.timestamp)
                            put("speed", pt.speed)
                            put("accuracy", pt.accuracy)
                        }
                        pointsArray.put(ptJson)
                    }
                    json.put("points", pointsArray)

                    file.writeText(json.toString(2))
                }
            }
            Log.d(TAG, "Successfully exported track to $format file: ${file.absolutePath}")
            return file
        } catch (e: Exception) {
            Log.e(TAG, "Error exporting track to $format", e)
            return null
        }
    }

    /**
     * Unified import function that supports importing GPX, KML, and JSON files,
     * converting them to .track structure and inserting them into the database.
     */
    fun importTrackFile(file: File, trackDao: TrackDao): Track? {
        val ext = file.extension.lowercase(Locale.US)
        return when (ext) {
            "gpx" -> importGpx(file, trackDao)
            "kml" -> importKml(file, trackDao)
            "json" -> importJson(file, trackDao)
            else -> {
                Log.e(TAG, "Unsupported track file format: .$ext")
                null
            }
        }
    }

    /**
     * Imports a KML file into standard local DB and autosaves as .track
     */
    fun importKml(kmlFile: File, trackDao: TrackDao): Track? {
        ensureDirectories()
        try {
            if (!kmlFile.exists()) return null
            val content = kmlFile.readText()
            
            var trackName = kmlFile.nameWithoutExtension.replace('_', ' ')
            val nameRegex = Regex("<name>(.*?)</name>")
            val nameMatch = nameRegex.find(content)
            if (nameMatch != null) {
                val parsedName = nameMatch.groupValues[1].replace("<![CDATA[", "").replace("]]>", "").trim()
                if (parsedName.isNotEmpty() && parsedName != "Path" && parsedName != "Document") {
                    trackName = parsedName
                }
            }

            val coordRegex = Regex("<coordinates>([\\s\\S]*?)</coordinates>")
            val coordMatch = coordRegex.find(content) ?: return null
            val coordText = coordMatch.groupValues[1].trim()
            val tokens = coordText.split(Regex("\\s+"))

            val parsedPoints = mutableListOf<TempPoint>()
            var mockTime = System.currentTimeMillis() - tokens.size * 5000L

            for (token in tokens) {
                if (token.isBlank()) continue
                val parts = token.split(",")
                if (parts.size >= 2) {
                    val lon = parts[0].toDoubleOrNull() ?: continue
                    val lat = parts[1].toDoubleOrNull() ?: continue
                    val ele = if (parts.size >= 3) parts[2].toDoubleOrNull() else null
                    parsedPoints.add(TempPoint(lat, lon, ele, mockTime, 0f))
                    mockTime += 5000L
                }
            }

            if (parsedPoints.isEmpty()) return null

            val startTime = parsedPoints.first().timestamp ?: System.currentTimeMillis()
            val endTime = parsedPoints.last().timestamp ?: System.currentTimeMillis()

            val newTrack = Track(
                startTime = startTime,
                endTime = endTime,
                status = "STOPPED",
                name = trackName
            )
            val trackId = trackDao.insertTrack(newTrack)
            val insertedTrack = newTrack.copy(id = trackId)

            val dbPoints = parsedPoints.map {
                TrackPoint(
                    trackId = trackId,
                    latitude = it.lat,
                    longitude = it.lon,
                    elevation = it.ele,
                    timestamp = it.timestamp ?: System.currentTimeMillis(),
                    speed = it.speed,
                    accuracy = 5f
                )
            }
            trackDao.insertTrackPoints(dbPoints)

            // Auto-save completion
            saveTrackToJson(insertedTrack, dbPoints)
            return insertedTrack
        } catch (e: Exception) {
            Log.e(TAG, "Error importing KML file", e)
            return null
        }
    }

    /**
     * Imports a JSON file into standard local DB and autosaves as .track
     */
    fun importJson(jsonFile: File, trackDao: TrackDao): Track? {
        ensureDirectories()
        try {
            if (!jsonFile.exists()) return null
            val content = jsonFile.readText()
            val json = JSONObject(content)
            
            val trackJson = json.getJSONObject("track")
            val startTime = trackJson.getLong("startTime")
            val endTime = if (trackJson.isNull("endTime")) null else trackJson.getLong("endTime")
            val name = if (trackJson.isNull("name")) null else trackJson.getString("name")

            val newTrack = Track(
                startTime = startTime,
                endTime = endTime,
                status = "STOPPED",
                name = name
            )
            val trackId = trackDao.insertTrack(newTrack)
            val insertedTrack = newTrack.copy(id = trackId)

            val dbPoints = mutableListOf<TrackPoint>()
            if (json.has("points")) {
                val pointsArray = json.getJSONArray("points")
                for (i in 0 until pointsArray.length()) {
                    val ptJson = pointsArray.getJSONObject(i)
                    val latitude = ptJson.getDouble("latitude")
                    val longitude = ptJson.getDouble("longitude")
                    val elevation = if (ptJson.isNull("elevation")) null else ptJson.getDouble("elevation")
                    val timestamp = ptJson.getLong("timestamp")
                    val speed = ptJson.optDouble("speed", 0.0).toFloat()
                    val accuracy = ptJson.optDouble("accuracy", 5.0).toFloat()

                    dbPoints.add(TrackPoint(
                        trackId = trackId,
                        latitude = latitude,
                        longitude = longitude,
                        elevation = elevation,
                        timestamp = timestamp,
                        speed = speed,
                        accuracy = accuracy
                    ))
                }
            }
            trackDao.insertTrackPoints(dbPoints)

            // Auto-save completion
            saveTrackToJson(insertedTrack, dbPoints)
            return insertedTrack
        } catch (e: Exception) {
            Log.e(TAG, "Error importing JSON file", e)
            return null
        }
    }

    /**
     * Imports a standard GPX file, stores it into database and auto-saves to .track format
     */
    fun importGpx(gpxFile: File, trackDao: TrackDao): Track? {
        ensureDirectories()
        try {
            if (!gpxFile.exists()) return null

            val dbFactory = DocumentBuilderFactory.newInstance()
            val dBuilder = dbFactory.newDocumentBuilder()
            val doc = dBuilder.parse(gpxFile)
            doc.documentElement.normalize()

            var trackName = gpxFile.nameWithoutExtension.replace('_', ' ')
            val trkList = doc.getElementsByTagName("trk")
            if (trkList.length > 0) {
                val trkNode = trkList.item(0)
                val trkChildNodes = trkNode.childNodes
                for (i in 0 until trkChildNodes.length) {
                    val child = trkChildNodes.item(i)
                    if (child.nodeName == "name") {
                        val parsedName = child.textContent.trim()
                        if (parsedName.isNotEmpty()) {
                            trackName = parsedName
                        }
                        break
                    }
                }
            }

            val trkptList = doc.getElementsByTagName("trkpt")
            if (trkptList.length == 0) {
                Log.w(TAG, "No track points found in GPX file: ${gpxFile.absolutePath}")
                return null
            }

            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            val sdfMs = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            val sdfOffset = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US)

            var minTime = System.currentTimeMillis()
            var maxTime = System.currentTimeMillis()
            val parsedPoints = mutableListOf<TempPoint>()

            for (temp in 0 until trkptList.length) {
                val nNode = trkptList.item(temp)
                if (nNode.nodeType == Node.ELEMENT_NODE) {
                    val eElement = nNode as Element
                    val latStr = eElement.getAttribute("lat")
                    val lonStr = eElement.getAttribute("lon")
                    if (latStr.isEmpty() || lonStr.isEmpty()) continue

                    val lat = latStr.toDoubleOrNull() ?: continue
                    val lon = lonStr.toDoubleOrNull() ?: continue

                    var ele: Double? = null
                    var timestamp: Long? = null
                    var speed = 0f

                    val eleList = eElement.getElementsByTagName("ele")
                    if (eleList.length > 0) {
                        ele = eleList.item(0).textContent.trim().toDoubleOrNull()
                    }

                    val timeList = eElement.getElementsByTagName("time")
                    if (timeList.length > 0) {
                        val timeStr = timeList.item(0).textContent.trim()
                        val parsedTime = try {
                            sdf.parse(timeStr)?.time
                        } catch (e: Exception) {
                            try {
                                sdfMs.parse(timeStr)?.time
                            } catch (e2: Exception) {
                                try {
                                    sdfOffset.parse(timeStr)?.time
                                } catch (e3: Exception) {
                                    null
                                }
                            }
                        }
                        if (parsedTime != null) {
                            timestamp = parsedTime
                        }
                    }

                    val speedList = eElement.getElementsByTagName("speed")
                    if (speedList.length > 0) {
                        speed = speedList.item(0).textContent.trim().toFloatOrNull() ?: 0f
                    }

                    parsedPoints.add(TempPoint(lat, lon, ele, timestamp, speed))
                }
            }

            if (parsedPoints.isEmpty()) return null

            // Sort by timestamp if available, or generate logical timestamps
            parsedPoints.sortBy { it.timestamp ?: 0L }
            
            // Deduce start and end times
            val startTime = parsedPoints.first().timestamp ?: System.currentTimeMillis()
            val endTime = parsedPoints.last().timestamp ?: (startTime + parsedPoints.size * 5000L)

            // Fill in missing timestamps
            var currentTimestamp = startTime
            val finalPoints = parsedPoints.mapIndexed { index, tempPt ->
                val ts = tempPt.timestamp ?: run {
                    currentTimestamp += 5000L // 5 seconds interval
                    currentTimestamp
                }
                TempPoint(tempPt.lat, tempPt.lon, tempPt.ele, ts, tempPt.speed)
            }

            // Create and insert Track
            val newTrack = Track(
                startTime = startTime,
                endTime = endTime,
                status = "STOPPED",
                name = trackName
            )
            val trackId = trackDao.insertTrack(newTrack)
            val insertedTrack = newTrack.copy(id = trackId)

            // Create and insert TrackPoints
            val dbPoints = finalPoints.map {
                TrackPoint(
                    trackId = trackId,
                    latitude = it.lat,
                    longitude = it.lon,
                    elevation = it.ele,
                    timestamp = it.timestamp ?: System.currentTimeMillis(),
                    speed = it.speed,
                    accuracy = 5f
                )
            }
            trackDao.insertTrackPoints(dbPoints)

            // Also auto-save to JSON (.track)
            saveTrackToJson(insertedTrack, dbPoints)

            Log.d(TAG, "Successfully imported GPX file: ${gpxFile.name} as track ID $trackId with ${dbPoints.size} points.")
            return insertedTrack
        } catch (e: Exception) {
            Log.e(TAG, "Error importing GPX file", e)
            return null
        }
    }

    private data class TempPoint(
        val lat: Double,
        val lon: Double,
        val ele: Double?,
        val timestamp: Long?,
        val speed: Float
    )
}
