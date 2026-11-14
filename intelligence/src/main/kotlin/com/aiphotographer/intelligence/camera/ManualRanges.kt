package com.aiphotographer.intelligence.camera

import com.aiphotographer.intelligence.ExposureMath
import kotlin.math.abs
import kotlin.math.log2
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * What one particular camera on one particular phone can actually do.
 *
 * Every value the Pro mode offers has to come from the hardware, not from a
 * wish list: asking a sensor for ISO 50 when its floor is 100 fails silently
 * and the user never learns why their slider did nothing.
 */
data class CameraCapabilities(
    val isoMin: Int,
    val isoMax: Int,
    val exposureTimeMinNs: Long,
    val exposureTimeMaxNs: Long,
    val focusDistanceMaxDiopters: Float,
    val evCompensationMin: Float,
    val evCompensationMax: Float,
    /** Smallest EV step the camera accepts, e.g. 1/3 or 1/2 of a stop. */
    val evCompensationStep: Float,
    val supportsManualIso: Boolean,
    val supportsManualShutter: Boolean,
    val supportsManualFocus: Boolean,
    val supportsManualWhiteBalance: Boolean,
    val supportsRaw: Boolean,
    val supportsHdr: Boolean,
    val zoomMin: Float,
    val zoomMax: Float,
) {
    companion object {
        /**
         * A conservative stand-in for a camera we could not interrogate, and
         * the baseline used in tests.
         */
        val UNKNOWN = CameraCapabilities(
            isoMin = 100,
            isoMax = 3200,
            exposureTimeMinNs = 100_000L,          // 1/10000 s
            exposureTimeMaxNs = 500_000_000L,      // 1/2 s
            focusDistanceMaxDiopters = 10f,
            evCompensationMin = -2f,
            evCompensationMax = 2f,
            evCompensationStep = 1f / 3f,
            supportsManualIso = false,
            supportsManualShutter = false,
            supportsManualFocus = false,
            supportsManualWhiteBalance = false,
            supportsRaw = false,
            supportsHdr = false,
            zoomMin = 1f,
            zoomMax = 1f,
        )
    }
}

/**
 * Translates what the app wants into what the hardware accepts.
 *
 * Pure Kotlin so every clamp can be tested: this is exactly the code that goes
 * wrong quietly on one phone model and nowhere else.
 */
object ManualRanges {

    /** The nearest ISO this camera can actually deliver. */
    fun clampIso(requested: Int, caps: CameraCapabilities): Int =
        requested.coerceIn(caps.isoMin, caps.isoMax)

    /** Exposure time in nanoseconds for a shutter denominator, clamped. */
    fun exposureTimeNs(shutterDenominator: Int, caps: CameraCapabilities): Long {
        val requested = (1_000_000_000.0 / shutterDenominator.coerceAtLeast(1)).roundToLong()
        return requested.coerceIn(caps.exposureTimeMinNs, caps.exposureTimeMaxNs)
    }

    /** The shutter denominator an exposure time corresponds to. */
    fun shutterDenominator(exposureTimeNs: Long): Int {
        if (exposureTimeNs <= 0L) return 0
        return (1_000_000_000.0 / exposureTimeNs).roundToInt().coerceAtLeast(1)
    }

    /**
     * The shutter stops this camera can offer, fastest first, so the Pro dial
     * only ever shows positions that work.
     */
    fun availableShutterStops(caps: CameraCapabilities): List<Int> =
        ExposureMath.SHUTTER_STOPS.filter { denominator ->
            val ns = (1_000_000_000.0 / denominator).roundToLong()
            ns in caps.exposureTimeMinNs..caps.exposureTimeMaxNs
        }

    /**
     * The ISO stops this camera can offer, in the conventional doubling
     * sequence, clipped to the hardware range.
     */
    fun availableIsoStops(caps: CameraCapabilities): List<Int> {
        val stops = ArrayList<Int>()
        var iso = 50
        while (iso <= 25600) {
            if (iso in caps.isoMin..caps.isoMax) stops.add(iso)
            iso *= 2
        }
        // Always offer the true endpoints, even if they are not round numbers.
        if (stops.isEmpty() || stops.first() != caps.isoMin) stops.add(0, caps.isoMin)
        if (stops.last() != caps.isoMax) stops.add(caps.isoMax)
        return stops.distinct().sorted()
    }

    /** Snap an exposure compensation request to a step the camera accepts. */
    fun clampEvCompensation(requested: Float, caps: CameraCapabilities): Float {
        val step = if (caps.evCompensationStep > 0f) caps.evCompensationStep else 1f / 3f
        val clamped = requested.coerceIn(caps.evCompensationMin, caps.evCompensationMax)
        return (clamped / step).roundToInt() * step
    }

    /**
     * Exposure compensation expressed as the integer index Camera2 expects.
     * Camera2 takes steps, not stops, so 1 stop at a 1/3 step is index 3.
     */
    fun evCompensationIndex(requested: Float, caps: CameraCapabilities): Int {
        val step = if (caps.evCompensationStep > 0f) caps.evCompensationStep else 1f / 3f
        val clamped = clampEvCompensation(requested, caps)
        return (clamped / step).roundToInt()
    }

    /**
     * Manual focus as diopters (1/metres), from a 0..1 slider where 0 is the
     * far distance and 1 is as close as the lens focuses.
     */
    fun focusDiopters(slider: Float, caps: CameraCapabilities): Float =
        (slider.coerceIn(0f, 1f) * caps.focusDistanceMaxDiopters)
            .coerceIn(0f, caps.focusDistanceMaxDiopters)

    /** Zoom ratio from a 0..1 slider, following a log scale so it feels even. */
    fun zoomRatio(slider: Float, caps: CameraCapabilities): Float {
        val min = caps.zoomMin.coerceAtLeast(0.1f)
        val max = caps.zoomMax.coerceAtLeast(min)
        if (max <= min) return min
        val t = slider.coerceIn(0f, 1f)
        val logged = log2(min.toDouble()) + t * (log2(max.toDouble()) - log2(min.toDouble()))
        return 2.0.pow(logged).toFloat().coerceIn(min, max)
    }

    /** Inverse of [zoomRatio], for placing the slider when zoom changes elsewhere. */
    fun zoomSlider(ratio: Float, caps: CameraCapabilities): Float {
        val min = caps.zoomMin.coerceAtLeast(0.1f)
        val max = caps.zoomMax.coerceAtLeast(min)
        if (max <= min) return 0f
        val clamped = ratio.coerceIn(min, max)
        val span = log2(max.toDouble()) - log2(min.toDouble())
        if (span <= 0.0) return 0f
        return ((log2(clamped.toDouble()) - log2(min.toDouble())) / span).toFloat()
            .coerceIn(0f, 1f)
    }

    /**
     * Whether the camera can honour a recommendation exactly, so the UI can
     * say "ISO 6400 isn't available on this lens" instead of lying.
     */
    fun describeShortfall(
        requestedIso: Int,
        requestedShutter: Int,
        caps: CameraCapabilities,
    ): String? {
        val iso = clampIso(requestedIso, caps)
        val ns = exposureTimeNs(requestedShutter, caps)
        val shutter = shutterDenominator(ns)
        val problems = ArrayList<String>()
        if (iso != requestedIso) problems.add("ISO limited to $iso")
        if (abs(shutter - requestedShutter) > requestedShutter / 10) {
            problems.add("shutter limited to 1/$shutter")
        }
        return if (problems.isEmpty()) null else problems.joinToString(", ")
    }
}
