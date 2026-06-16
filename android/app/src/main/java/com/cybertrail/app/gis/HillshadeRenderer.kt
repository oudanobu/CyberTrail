package com.cybertrail.app.gis

import kotlin.math.*

/**
 * High-precision offline relief shading.
 * Generates multidimensional analytical hillshades based on Lambertian illumination math.
 */
class HillshadeRenderer(
    private val sunAzimuth: Double = 315.0,  // Standard Northwest NW lighting
    private val sunAltitude: Double = 45.0   // 45 degrees above horizon
) {

    private val zenithRad = Math.toRadians(90.0 - sunAltitude)
    private val azimuthRad = Math.toRadians(360.0 - sunAzimuth + 90.0)

    /**
     * Calculates illumination factor in range [0.0, 1.0] representing shading coefficient.
     * - 0.0 (total shade/valley)
     * - 1.0 (maximal ridge/cliff facing the source directly)
     */
    fun computeShadingIntensity(slopeDegrees: Double, aspectDegrees: Double): Double {
        // If terrain is flat, perfect horizontal base light
        if (aspectDegrees < 0.0 || slopeDegrees < 0.001) {
            return cos(zenithRad)
        }

        val slopeRad = Math.toRadians(slopeDegrees)
        val aspectRad = Math.toRadians(aspectDegrees)

        // Lambertian cosine law for topographic illumination
        var illum = cos(zenithRad) * cos(slopeRad) + sin(zenithRad) * sin(slopeRad) * cos(azimuthRad - aspectRad)
        if (illum < 0.0) {
            illum = 0.0
        } else if (illum > 1.0) {
            illum = 1.0
        }
        return illum
    }

    /**
     * Maps calculated illumination coefficient [0.0..1.0] to a grayscale byte [0..255] for direct image processing.
     */
    fun computeShadingByte(slopeDegrees: Double, aspectDegrees: Double): Int {
        val intensity = computeShadingIntensity(slopeDegrees, aspectDegrees)
        return (intensity * 255.0).roundToInt().coerceIn(0, 255)
    }

    /**
     * Overlays shading pixel mask directly on top of base terrain background pixel colors.
     */
    fun applyHillshadeMask(baseColor: Int, slopeDegrees: Double, aspectDegrees: Double): Int {
        val intensity = computeShadingIntensity(slopeDegrees, aspectDegrees)
        
        // Extract ARGB channels
        val a = (baseColor shr 24) and 0xFF
        val r = (baseColor shr 16) and 0xFF
        val g = (baseColor shr 8) and 0xFF
        val b = baseColor and 0xFF

        // Multiply Red, Green, Blue by the illumination coefficient (shading/tinting)
        val rOut = (r * intensity).roundToInt().coerceIn(0, 255)
        val gOut = (g * intensity).roundToInt().coerceIn(0, 255)
        val bOut = (b * intensity).roundToInt().coerceIn(0, 255)

        return (a shl 24) or (rOut shl 16) or (gOut shl 8) or bOut
    }
}
