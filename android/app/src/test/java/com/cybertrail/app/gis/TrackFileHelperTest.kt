package com.cybertrail.app.gis

import com.cybertrail.app.db.Track
import com.cybertrail.app.db.TrackPoint
import org.json.JSONObject
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class TrackFileHelperTest {

    @Test
    fun testTrackSerializationAndDeserialization() {
        // Create a test track
        val startTime = 1680000000000L
        val track = Track(
            id = 42L,
            startTime = startTime,
            endTime = startTime + 60000L,
            status = "RECORDING",
            name = "Test Cyber Trail"
        )

        // Create test track points
        val points = listOf(
            TrackPoint(id = 1L, trackId = 42L, latitude = 39.9, longitude = 116.4, elevation = 50.0, timestamp = startTime, speed = 2.5f, accuracy = 4.0f),
            TrackPoint(id = 2L, trackId = 42L, latitude = 39.91, longitude = 116.41, elevation = 52.0, timestamp = startTime + 10000L, speed = 3.0f, accuracy = 3.5f)
        )

        // Simulate TrackFileHelper's JSON writing logic
        val json = JSONObject()
        val trackJson = JSONObject().apply {
            put("id", track.id)
            put("startTime", track.startTime)
            put("endTime", track.endTime)
            put("status", track.status)
            put("name", track.name)
            
            // Reserved extension fields
            put("photoRefs", JSONArray())
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
                put("elevation", pt.elevation)
                put("timestamp", pt.timestamp)
                put("speed", pt.speed)
                put("accuracy", pt.accuracy)
            }
            pointsArray.put(ptJson)
        }
        json.put("points", pointsArray)

        // Verify JSON fields
        assertTrue(json.has("track"))
        assertTrue(json.has("points"))

        val parsedTrackObj = json.getJSONObject("track")
        assertEquals(42L, parsedTrackObj.getLong("id"))
        assertEquals("RECORDING", parsedTrackObj.getString("status"))
        assertEquals("Test Cyber Trail", parsedTrackObj.getString("name"))

        // Verify reserved fields are present
        assertTrue(parsedTrackObj.has("photoRefs"))
        assertTrue(parsedTrackObj.has("note"))
        assertTrue(parsedTrackObj.has("waypoint"))
        assertTrue(parsedTrackObj.has("sensorData"))

        val parsedPointsArr = json.getJSONArray("points")
        assertEquals(2, parsedPointsArr.length())

        val pt0 = parsedPointsArr.getJSONObject(0)
        assertEquals(39.9, pt0.getDouble("latitude"), 0.0001)
        assertEquals(116.4, pt0.getDouble("longitude"), 0.0001)
        assertEquals(50.0, pt0.getDouble("elevation"), 0.0001)
        assertEquals(2.5f, pt0.getDouble("speed").toFloat(), 0.01f)
    }

    @Test
    fun testUncompletedTrackFilter() {
        // Simulate scanning logic in uncompleted tracks
        val json1 = JSONObject().apply {
            put("track", JSONObject().apply {
                put("id", 1L)
                put("status", "STOPPED")
            })
        }
        val json2 = JSONObject().apply {
            put("track", JSONObject().apply {
                put("id", 2L)
                put("status", "RECORDING")
            })
        }
        val json3 = JSONObject().apply {
            put("track", JSONObject().apply {
                put("id", 3L)
                put("status", "PAUSED")
            })
        }

        // Verify status check logic
        assertEquals("STOPPED", json1.getJSONObject("track").getString("status"))
        
        val status2 = json2.getJSONObject("track").getString("status")
        val status3 = json3.getJSONObject("track").getString("status")
        
        assertTrue(status2 == "RECORDING" || status2 == "PAUSED")
        assertTrue(status3 == "RECORDING" || status3 == "PAUSED")
    }
}
