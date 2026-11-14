package com.aiphotographer.intelligence

import kotlin.math.abs
import kotlin.math.log2
import kotlin.math.roundToInt

/**
 * The photographic maths behind every exposure decision.
 *
 * These are the real relationships between aperture, shutter and ISO, not
 * heuristics, which is why they can be checked against known values such as
 * the sunny-16 rule.
 */
object ExposureMath {

    /** Conventional shutter stops, fastest first. */
    val SHUTTER_STOPS: List<Int> =
        listOf(8000, 4000, 2000, 1000, 500, 250, 125, 60, 30, 15, 8, 4, 2)

    const val MIN_ISO: Int = 50
    const val MAX_ISO: Int = 12800

    /**
     * The scene's exposure value at ISO 100, derived from what auto-exposure
     * chose for this frame.
     *
     *     EV100 = log2(N^2 / t) - log2(ISO / 100)
     *
     * Bright sunlight lands near 14-15, a lit room near 5-6, a night street
     * near 1-2. This gives us an absolute measure of available light.
     */
    fun sceneEv(camera: CameraState): Double {
        val seconds = camera.exposureTimeNs.coerceAtLeast(1L) / 1_000_000_000.0
        val aperture = camera.aperture.coerceAtLeast(0.5f).toDouble()
        val iso = camera.iso.coerceAtLeast(1).toDouble()
        return log2((aperture * aperture) / seconds) - log2(iso / 100.0)
    }

    /**
     * The ISO needed to expose correctly at a chosen shutter speed, given the
     * scene's light level. The same equation rearranged:
     *
     *     ISO = 100 * (N^2 / t) / 2^EV100
     */
    fun requiredIso(shutterDenominator: Int, sceneEv: Double, aperture: Float): Int {
        val seconds = 1.0 / shutterDenominator.coerceAtLeast(1)
        val n = aperture.coerceAtLeast(0.5f).toDouble()
        val iso = 100.0 * ((n * n) / seconds) / Math.pow(2.0, sceneEv)
        return iso.roundToInt().coerceIn(MIN_ISO, MAX_ISO)
    }

    /** Snap an arbitrary shutter denominator to the nearest conventional stop. */
    fun snapShutter(denominator: Int): Int {
        val target = log2(denominator.coerceAtLeast(1).toDouble())
        return SHUTTER_STOPS.minByOrNull { abs(log2(it.toDouble()) - target) }
            ?: denominator
    }

    /**
     * The slowest shutter speed that still gives a sharp handheld frame.
     *
     * The reciprocal rule says don't go below 1/focal-length in 35mm terms.
     * Phone lenses are short, so we convert to a 35mm equivalent, then allow
     * one stop of credit for optical stabilisation - but never slower than
     * 1/30, where human handshake always shows.
     */
    fun handheldFloor(focalLengthMm: Float): Int {
        val equivalent = focalLengthMm.coerceAtLeast(1f) * 7f // ~1/1.7" sensor crop
        val floor = (equivalent / 2f).coerceIn(30f, 500f)
        return snapShutter(floor.toInt())
    }
}
