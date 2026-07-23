package com.cybertrail.app.gis

import com.cybertrail.app.gis.ins.KalmanFusionEngine
import com.cybertrail.app.gis.ins.NavState
import com.cybertrail.app.gis.ins.PdrPositionEstimator
import com.cybertrail.app.gis.ins.StepLengthEstimator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI

class InsPdrUnitTest {

    @Test
    fun testPdrPositionEstimatorNorthMove() {
        val startLat = 40.12345
        val startLon = 124.38910
        val stepLengthMeters = 100.0 // Move 100 meters due North
        val headingRad = 0.0 // North

        val nextPos = PdrPositionEstimator.computeNextPosition(startLat, startLon, stepLengthMeters, headingRad)

        // Moving North should increase latitude while longitude remains roughly constant
        assertTrue("Latitude should increase when walking North", nextPos.latitude > startLat)
        assertEquals("Longitude should remain constant when walking North", startLon, nextPos.longitude, 0.00001)

        val distance = PdrPositionEstimator.distanceMeters(startLat, startLon, nextPos.latitude, nextPos.longitude)
        assertEquals(100.0, distance, 0.5)
    }

    @Test
    fun testPdrPositionEstimatorEastMove() {
        val startLat = 40.12345
        val startLon = 124.38910
        val stepLengthMeters = 50.0 // Move 50 meters due East
        val headingRad = PI / 2.0 // East

        val nextPos = PdrPositionEstimator.computeNextPosition(startLat, startLon, stepLengthMeters, headingRad)

        // Moving East should increase longitude while latitude remains constant
        assertEquals("Latitude should remain constant when walking East", startLat, nextPos.latitude, 0.00001)
        assertTrue("Longitude should increase when walking East", nextPos.longitude > startLon)

        val distance = PdrPositionEstimator.distanceMeters(startLat, startLon, nextPos.latitude, nextPos.longitude)
        assertEquals(50.0, distance, 0.5)
    }

    @Test
    fun testStepLengthEstimator() {
        val estimator = StepLengthEstimator()

        // Normal walk peak-trough difference (~2.0 m/s^2)
        val normalStep = estimator.estimateStepLength(11.8f, 9.8f)
        assertTrue("Normal step length should be between 0.4 and 0.9m", normalStep in 0.4..0.9)

        // Fast walk / run peak-trough difference (~10 m/s^2)
        val fastStep = estimator.estimateStepLength(15.0f, 5.0f)
        assertTrue("Fast step length should be larger than normal step", fastStep > normalStep)
        assertTrue("Step length must be capped at 1.2m", fastStep <= 1.2)
    }

    @Test
    fun testKalmanFusionEngineGpsRecovery() {
        val kalman = KalmanFusionEngine()
        val initLat = 40.00000
        val initLon = 124.00000

        kalman.initialize(initLat, initLon)

        // Simulate 20 PDR steps due East without GPS (INS_ONLY state)
        for (i in 1..20) {
            kalman.predictPdrStep(0.75, PI / 2.0)
        }

        val insLat = kalman.fusedLat
        val insLon = kalman.fusedLon
        assertTrue("INS longitude should have advanced East", insLon > initLon)

        // Simulate GPS recovery at a slightly shifted position (e.g. 5m correction)
        val recoveredGpsLat = insLat + 0.00002
        val recoveredGpsLon = insLon + 0.00002
        val fusedPos = kalman.updateGpsMeasurement(recoveredGpsLat, recoveredGpsLon, 3.0f)

        assertTrue("Kalman fusion should pull latitude towards GPS recovery point", fusedPos.latitude > insLat)
        assertTrue("Kalman fusion should pull longitude towards GPS recovery point", fusedPos.longitude > insLon)
    }

    @Test
    fun testNavStateEnumValues() {
        val states = NavState.values()
        assertEquals(4, states.size)
        assertTrue(states.contains(NavState.NORMAL))
        assertTrue(states.contains(NavState.HYBRID))
        assertTrue(states.contains(NavState.INS_ONLY))
        assertTrue(states.contains(NavState.GPS_RECOVERY))
    }
}
