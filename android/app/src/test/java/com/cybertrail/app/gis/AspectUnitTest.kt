package com.cybertrail.app.gis

import org.junit.Assert.assertEquals
import org.junit.Test

class AspectUnitTest {

    fun calculateAspect(hN: Double, hS: Double, hE: Double, hW: Double): Double {
        val cellSideM = 11.1
        val dzDx = (hE - hW) / (2.0 * cellSideM)
        val dzDy = (hN - hS) / (2.0 * cellSideM)

        val downSlopeX = -dzDx
        val downSlopeY = -dzDy
        val mathAngleDeg = Math.toDegrees(Math.atan2(downSlopeY, downSlopeX))
        var gisAspect = 90.0 - mathAngleDeg
        if (gisAspect < 0.0) {
            gisAspect += 360.0
        }
        if (gisAspect >= 360.0) {
            gisAspect -= 360.0
        }
        return if (dzDx == 0.0 && dzDy == 0.0) -1.0 else gisAspect
    }

    @Test
    fun testCase1() {
        val actual = calculateAspect(10.0, 10.0, 9.0, 11.0)
        assertEquals(90.0, actual, 0.1)
    }

    @Test
    fun testCase2() {
        val actual = calculateAspect(9.0, 11.0, 10.0, 10.0)
        assertEquals(0.0, actual, 0.1)
    }

    @Test
    fun testCase3() {
        val actual = calculateAspect(10.0, 10.0, 11.0, 9.0)
        assertEquals(270.0, actual, 0.1)
    }

    @Test
    fun testCase4() {
        val actual = calculateAspect(11.0, 9.0, 10.0, 10.0)
        assertEquals(180.0, actual, 0.1)
    }

    @Test
    fun testCase5() {
        val actual = calculateAspect(9.0, 11.0, 9.0, 11.0)
        assertEquals(45.0, actual, 0.1)
    }

    @Test
    fun testCase6() {
        val actual = calculateAspect(9.0, 11.0, 11.0, 9.0)
        assertEquals(315.0, actual, 0.1)
    }

    @Test
    fun testCase7() {
        val actual = calculateAspect(11.0, 9.0, 9.0, 11.0)
        assertEquals(135.0, actual, 0.1)
    }

    @Test
    fun testCase8() {
        val actual = calculateAspect(11.0, 9.0, 11.0, 9.0)
        assertEquals(225.0, actual, 0.1)
    }
}
