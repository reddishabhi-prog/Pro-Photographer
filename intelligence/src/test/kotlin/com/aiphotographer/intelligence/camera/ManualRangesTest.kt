package com.aiphotographer.intelligence.camera

import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Asking a camera for a setting it cannot do fails silently, and the user just
 * sees a slider that does nothing. Every clamp here is tested because this is
 * exactly the code that breaks on one phone model and no others.
 */
class ManualRangesTest {

    private val typicalPhone = CameraCapabilities(
        isoMin = 50,
        isoMax = 6400,
        exposureTimeMinNs = 50_000L,        // 1/20000 s
        exposureTimeMaxNs = 1_000_000_000L, // 1 s
        focusDistanceMaxDiopters = 10f,
        evCompensationMin = -3f,
        evCompensationMax = 3f,
        evCompensationStep = 1f / 3f,
        supportsManualIso = true,
        supportsManualShutter = true,
        supportsManualFocus = true,
        supportsManualWhiteBalance = true,
        supportsRaw = true,
        supportsHdr = true,
        zoomMin = 0.6f,
        zoomMax = 10f,
    )

    private val limitedPhone = typicalPhone.copy(
        isoMin = 100,
        isoMax = 1600,
        exposureTimeMinNs = 1_000_000L,   // 1/1000 s is as fast as it goes
        exposureTimeMaxNs = 100_000_000L, // 1/10 s is as slow as it goes
        zoomMin = 1f,
        zoomMax = 1f,
    )

    @Test
    fun `ISO is clamped to what the sensor offers`() {
        assertEquals(50, ManualRanges.clampIso(10, typicalPhone))
        assertEquals(6400, ManualRanges.clampIso(25600, typicalPhone))
        assertEquals(800, ManualRanges.clampIso(800, typicalPhone))
        assertEquals(100, ManualRanges.clampIso(50, limitedPhone))
        assertEquals(1600, ManualRanges.clampIso(6400, limitedPhone))
    }

    @Test
    fun `shutter denominators convert to exposure time and back`() {
        for (denominator in listOf(30, 60, 125, 250, 500, 1000)) {
            val ns = ManualRanges.exposureTimeNs(denominator, typicalPhone)
            val back = ManualRanges.shutterDenominator(ns)
            assertTrue(
                "1/$denominator became 1/$back",
                abs(back - denominator) <= 1,
            )
        }
    }

    @Test
    fun `a shutter speed beyond the hardware is pulled into range`() {
        // Asking a 1/1000 camera for 1/8000 must give 1/1000, not silence.
        val ns = ManualRanges.exposureTimeNs(8000, limitedPhone)
        assertEquals(limitedPhone.exposureTimeMinNs, ns)
        assertEquals(1000, ManualRanges.shutterDenominator(ns))

        val slow = ManualRanges.exposureTimeNs(2, limitedPhone)
        assertEquals(limitedPhone.exposureTimeMaxNs, slow)
    }

    @Test
    fun `the Pro dial only offers shutter stops that work`() {
        val stops = ManualRanges.availableShutterStops(limitedPhone)
        assertTrue("expected some stops", stops.isNotEmpty())
        for (denominator in stops) {
            val ns = 1_000_000_000L / denominator
            assertTrue(
                "1/$denominator is outside the hardware range",
                ns in limitedPhone.exposureTimeMinNs..limitedPhone.exposureTimeMaxNs,
            )
        }
        assertTrue("1/8000 should not be offered", 8000 !in stops)
        assertTrue("1/1000 should be offered", 1000 in stops)
    }

    @Test
    fun `the ISO dial covers the range and includes the true endpoints`() {
        val stops = ManualRanges.availableIsoStops(typicalPhone)
        assertEquals("should start at the sensor floor", 50, stops.first())
        assertEquals("should end at the sensor ceiling", 6400, stops.last())
        assertTrue("should be sorted", stops == stops.sorted())
        assertTrue("should have no duplicates", stops.size == stops.distinct().size)
        assertTrue("should include the usual stops", stops.containsAll(listOf(100, 400, 1600)))

        val limited = ManualRanges.availableIsoStops(limitedPhone)
        assertEquals(100, limited.first())
        assertEquals(1600, limited.last())
        assertTrue("must not offer ISO above the ceiling", limited.all { it <= 1600 })
    }

    @Test
    fun `exposure compensation snaps to a step the camera accepts`() {
        // A camera on third-stops cannot do +0.5 EV.
        val snapped = ManualRanges.clampEvCompensation(0.5f, typicalPhone)
        assertTrue(
            "0.5 should snap to a third-stop, got $snapped",
            abs(snapped - 0.333f) < 0.01f || abs(snapped - 0.667f) < 0.01f,
        )
        assertEquals(1f, ManualRanges.clampEvCompensation(1f, typicalPhone), 0.01f)
        assertEquals(3f, ManualRanges.clampEvCompensation(9f, typicalPhone), 0.01f)
        assertEquals(-3f, ManualRanges.clampEvCompensation(-9f, typicalPhone), 0.01f)
    }

    @Test
    fun `exposure compensation is handed to the camera as step indices`() {
        // Camera2 wants steps, not stops: +1 EV on a third-stop camera is 3.
        assertEquals(3, ManualRanges.evCompensationIndex(1f, typicalPhone))
        assertEquals(-3, ManualRanges.evCompensationIndex(-1f, typicalPhone))
        assertEquals(0, ManualRanges.evCompensationIndex(0f, typicalPhone))
        assertEquals(
            "beyond the limit must still be a legal index",
            9, ManualRanges.evCompensationIndex(5f, typicalPhone),
        )
    }

    @Test
    fun `zoom slider and ratio are inverses of each other`() {
        for (slider in listOf(0f, 0.25f, 0.5f, 0.75f, 1f)) {
            val ratio = ManualRanges.zoomRatio(slider, typicalPhone)
            val back = ManualRanges.zoomSlider(ratio, typicalPhone)
            assertEquals("slider $slider round-tripped to $back", slider, back, 0.01f)
        }
    }

    @Test
    fun `zoom respects the lens limits`() {
        assertEquals(0.6f, ManualRanges.zoomRatio(0f, typicalPhone), 0.01f)
        assertEquals(10f, ManualRanges.zoomRatio(1f, typicalPhone), 0.01f)
        // A phone with no zoom range must not divide by zero.
        assertEquals(1f, ManualRanges.zoomRatio(0.5f, limitedPhone), 0.01f)
        assertEquals(0f, ManualRanges.zoomSlider(1f, limitedPhone), 0.01f)
    }

    @Test
    fun `manual focus maps the slider onto diopters`() {
        assertEquals(0f, ManualRanges.focusDiopters(0f, typicalPhone), 0.01f)
        assertEquals(10f, ManualRanges.focusDiopters(1f, typicalPhone), 0.01f)
        assertEquals(5f, ManualRanges.focusDiopters(0.5f, typicalPhone), 0.01f)
        // Out-of-range sliders are clamped, never passed through.
        assertEquals(0f, ManualRanges.focusDiopters(-2f, typicalPhone), 0.01f)
        assertEquals(10f, ManualRanges.focusDiopters(4f, typicalPhone), 0.01f)
    }

    @Test
    fun `the user is told when the hardware cannot honour a recommendation`() {
        assertNull(
            "ISO 800 at 1-125 is fine on this phone",
            ManualRanges.describeShortfall(800, 125, typicalPhone),
        )
        val message = ManualRanges.describeShortfall(6400, 8000, limitedPhone)
        assertNotNull("should explain the limit", message)
        assertTrue("should mention ISO: $message", message!!.contains("ISO"))
        assertTrue("should mention shutter: $message", message.contains("shutter"))
    }

    @Test
    fun `the unknown camera fallback is safe to use`() {
        val caps = CameraCapabilities.UNKNOWN
        assertTrue(ManualRanges.clampIso(100, caps) in caps.isoMin..caps.isoMax)
        assertTrue(
            ManualRanges.exposureTimeNs(125, caps)
                in caps.exposureTimeMinNs..caps.exposureTimeMaxNs,
        )
        assertEquals(1f, ManualRanges.zoomRatio(0.5f, caps), 0.01f)
        assertTrue(ManualRanges.availableShutterStops(caps).isNotEmpty())
        assertTrue(ManualRanges.availableIsoStops(caps).isNotEmpty())
    }
}
