package com.cybertrail.app.gis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PositionManagerUnitTest {

    @Test
    fun testLocationSourcePriorityOrdering() {
        val sources = LocationSource.values()
        assertEquals(6, sources.size)

        assertTrue(LocationSource.GPS.priority < LocationSource.NETWORK.priority)
        assertTrue(LocationSource.NETWORK.priority < LocationSource.CELL.priority)
        assertTrue(LocationSource.CELL.priority < LocationSource.LAST_FIX.priority)
        assertTrue(LocationSource.LAST_FIX.priority < LocationSource.INS.priority)
        assertTrue(LocationSource.INS.priority < LocationSource.MANUAL.priority)
    }

    @Test
    fun testCyberLocationCreation() {
        val now = System.currentTimeMillis()
        val loc = CyberLocation(
            latitude = 40.12345,
            longitude = 124.38910,
            altitude = 850.0,
            accuracy = 5.0f,
            source = LocationSource.GPS,
            timestamp = now
        )

        assertEquals(40.12345, loc.latitude, 0.00001)
        assertEquals(124.38910, loc.longitude, 0.00001)
        assertEquals(850.0, loc.altitude!!, 0.1)
        assertEquals(LocationSource.GPS, loc.source)
        assertEquals(now, loc.timestamp)
    }

    @Test
    fun testManualLocationOverrideAndInsContinuation() {
        var currentLoc = CyberLocation(
            latitude = 40.00000,
            longitude = 124.00000,
            source = LocationSource.MANUAL
        )

        // User manual override
        assertEquals(LocationSource.MANUAL, currentLoc.source)

        // Next PDR step from Manual location
        val stepLat = currentLoc.latitude + 0.00005
        val stepLon = currentLoc.longitude + 0.00005

        currentLoc = CyberLocation(
            latitude = stepLat,
            longitude = stepLon,
            source = LocationSource.INS
        )

        assertEquals(LocationSource.INS, currentLoc.source)
        assertTrue("INS navigation continues from manual start point", currentLoc.latitude > 40.00000)
    }
}
