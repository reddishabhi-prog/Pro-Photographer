package com.aiphotographer.intelligence

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.log2

/**
 * Found by the desk simulator: in bright light the ISO pinned at base while
 * the shutter stayed at the subject's minimum, so the settings the app sends
 * to the camera would have blown a sunny portrait out by about four stops.
 * Whatever the scene, the recommended pair must expose it correctly.
 */
class BrightLightExposureTest {

    /** Stops the recommendation would be off from the scene's real light. */
    private fun stopsOff(input: FrameInput, s: SettingsRecommendation): Double {
        val sceneEv = ExposureMath.sceneEv(input.camera)
        val n = input.camera.aperture.toDouble()
        val chosenEv = log2(n * n * s.shutterDenominator) - log2(s.iso / 100.0)
        return chosenEv - sceneEv
    }

    private fun portrait(iso: Int, shutter: Int) = FrameInput(
        people = listOf(Fixtures.person(0.30f, 0.12f, 0.66f, 0.90f, Fixtures.UPPER_BODY)),
        faces = listOf(Fixtures.face(0.40f, 0.12f, 0.58f, 0.32f)),
        labels = listOf(SceneLabel("person", 0.9f)),
        luma = LumaStats(
            mean = 140f, p5 = 45f, p95 = 205f,
            clippedShadows = 0.01f, clippedHighlights = 0.02f,
            regions = Fixtures.flatGrid(140f),
        ),
        color = Fixtures.neutralColor(145f),
        camera = Fixtures.camera(iso = iso, shutterDenominator = shutter),
    )

    @Test
    fun `sunlit portrait speeds the shutter up instead of overexposing`() {
        val input = portrait(iso = 50, shutter = 2500) // EV ~14, bright sun
        val g = PhotoIntelligenceEngine.analyse(input)

        assertEquals(ExposureMath.MIN_ISO, g.settings.iso)
        assertTrue(
            "needs a fast shutter in sun, got 1/${g.settings.shutterDenominator}",
            g.settings.shutterDenominator >= 2000,
        )
        assertTrue(
            "exposure off by ${stopsOff(input, g.settings)} stops",
            abs(stopsOff(input, g.settings)) <= 0.5,
        )
    }

    @Test
    fun `every daylight to indoor level is exposed within half a stop`() {
        // From open shade to full sun the pair must always land on the scene.
        for (shutter in listOf(250, 500, 1000, 2000, 4000)) {
            val input = portrait(iso = 50, shutter = shutter)
            val s = PhotoIntelligenceEngine.analyse(input).settings
            assertTrue(
                "1/$shutter metered scene: off by ${stopsOff(input, s)} stops",
                abs(stopsOff(input, s)) <= 0.5,
            )
        }
    }

    @Test
    fun `a dim room keeps the portrait shutter speed`() {
        // The fix must not touch normal light: indoors 1/125 is still right.
        val input = portrait(iso = 800, shutter = 60)
        val s = PhotoIntelligenceEngine.analyse(input).settings
        assertEquals(125, s.shutterDenominator)
    }
}
