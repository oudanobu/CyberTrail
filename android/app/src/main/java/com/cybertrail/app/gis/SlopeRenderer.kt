package com.cybertrail.app.gis

/**
 * Visualizer and classifier for slope-steepness levels.
 * Groups degrees into Tactical Cyberpunk danger bands.
 */
class SlopeRenderer {

    companion object {
        private const val COLOR_HEX_SAFE = "#442ECC71"     // Green (0-10)
        private const val COLOR_HEX_CAUTION = "#44F1C40F"  // Yellow (10-25)
        private const val COLOR_HEX_WARNING = "#44E67E22"  // Orange (25-40)
        private const val COLOR_HEX_EXTREME = "#44E74C3C"  // Red (40+)

        const val COLOR_INT_SAFE = 0x442ECC71
        const val COLOR_INT_CAUTION = 0x44F1C40F
        const val COLOR_INT_WARNING = 0x44E67E22
        const val COLOR_INT_EXTREME = 0x44E74C3C
    }

    fun classifySlopeHex(slopeDegrees: Double): String {
        return when {
            slopeDegrees < 10.0 -> COLOR_HEX_SAFE
            slopeDegrees < 25.0 -> COLOR_HEX_CAUTION
            slopeDegrees < 40.0 -> COLOR_HEX_WARNING
            else -> COLOR_HEX_EXTREME
        }
    }

    fun classifySlopeColorInt(slopeDegrees: Double): Int {
        return when {
            slopeDegrees < 10.0 -> COLOR_INT_SAFE
            slopeDegrees < 25.0 -> COLOR_INT_CAUTION
            slopeDegrees < 40.0 -> COLOR_INT_WARNING
            else -> COLOR_INT_EXTREME
        }
    }
}
