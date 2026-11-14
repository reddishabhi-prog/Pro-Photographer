package com.aiphotographer.intelligence

import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The exposure maths is checked against known photographic values. If these
 * fail, the engine's idea of "how much light is there" is wrong, and every
 * ISO and shutter recommendation downstream is wrong with it.
 */
class ExposureMathTest {

    @Test
    fun `sunny 16 rule lands near EV 15`() {
        // f/16 at 1/125s on ISO 100 is correct exposure in bright sunlight.
        val ev = ExposureMath.sceneEv(
            CameraState(
                iso = 100,
                exposureTimeNs = Fixtures.shutter(125),
                aperture = 16f,
                focalLengthMm = 50f,
            )
        )
        assertTrue("expected ~15, got $ev", ev > 14.5 && ev < 15.5)
    }

    @Test
    fun `phone in bright sun lands near EV 14`() {
        val ev = ExposureMath.sceneEv(Fixtures.camera(iso = 50, shutterDenominator = 2000))
        assertTrue("expected ~13-14, got $ev", ev > 13.0 && ev < 14.5)
    }

    @Test
    fun `lit indoor room lands near EV 5 to 6`() {
        val ev = ExposureMath.sceneEv(Fixtures.camera(iso = 400, shutterDenominator = 60))
        assertTrue("expected ~5-6, got $ev", ev > 5.0 && ev < 6.5)
    }

    @Test
    fun `night street lands near EV 1 to 2`() {
        val ev = ExposureMath.sceneEv(Fixtures.camera(iso = 1600, shutterDenominator = 15))
        assertTrue("expected ~1-2, got $ev", ev > 0.5 && ev < 2.5)
    }

    @Test
    fun `recommended ISO reproduces the same scene EV`() {
        // The ISO we pick must actually expose the scene correctly: feeding it
        // back through the equation has to return the EV we started from.
        for (sceneEv in listOf(3.0, 6.5, 10.0, 13.5)) {
            for (denominator in listOf(60, 125, 500)) {
                val iso = ExposureMath.requiredIso(denominator, sceneEv, 1.8f)
                if (iso <= ExposureMath.MIN_ISO + 5 || iso >= ExposureMath.MAX_ISO - 800) {
                    continue // clamped at the limits; round-trip not meaningful
                }
                val back = ExposureMath.sceneEv(
                    Fixtures.camera(iso = iso, shutterDenominator = denominator)
                )
                assertTrue(
                    "EV$sceneEv at 1/$denominator gave ISO$iso which reads back as $back",
                    abs(back - sceneEv) < 0.2,
                )
            }
        }
    }

    @Test
    fun `shutter speeds snap to conventional stops`() {
        assertEquals(125, ExposureMath.snapShutter(100))
        assertEquals(500, ExposureMath.snapShutter(520))
        assertTrue(ExposureMath.snapShutter(40) == 30 || ExposureMath.snapShutter(40) == 60)
    }

    @Test
    fun `wide lens can be handheld at one thirtieth`() {
        assertEquals(30, ExposureMath.handheldFloor(5f))
    }

    @Test
    fun `telephoto needs a faster shutter to stay sharp`() {
        assertTrue(ExposureMath.handheldFloor(15f) >= 60)
    }

    @Test
    fun `ISO is always within the usable range`() {
        // Even absurd inputs must produce a setting the camera can accept.
        val pitchBlack = ExposureMath.requiredIso(1000, -5.0, 1.8f)
        val blindingSun = ExposureMath.requiredIso(2, 20.0, 1.8f)
        assertTrue(pitchBlack in ExposureMath.MIN_ISO..ExposureMath.MAX_ISO)
        assertTrue(blindingSun in ExposureMath.MIN_ISO..ExposureMath.MAX_ISO)
    }
}
