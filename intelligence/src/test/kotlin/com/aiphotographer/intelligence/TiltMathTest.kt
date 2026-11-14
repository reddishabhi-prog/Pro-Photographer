package com.aiphotographer.intelligence

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

/**
 * The accelerometer reports "up" in the phone's own axes. These vectors are
 * worked out from that physics, not read back from the code under test.
 */
class TiltMathTest {

    private val g = 9.81f

    /** Gravity reading for an upright phone turned clockwise by [deg]. */
    private fun turnedClockwise(deg: Double): DeviceTilt {
        val r = Math.toRadians(deg)
        // Up, seen from the phone's axes, swings towards the screen's left edge.
        return TiltMath.fromGravity((-g * sin(r)).toFloat(), (g * cos(r)).toFloat(), 0f)
    }

    @Test
    fun `upright phone is level`() {
        val t = TiltMath.fromGravity(0f, g, 0f)
        assertEquals(0f, t.rollDeg, 0.01f)
        assertEquals(0f, t.pitchDeg, 0.01f)
    }

    @Test
    fun `phone turned clockwise reads positive roll`() {
        assertEquals(10f, turnedClockwise(10.0).rollDeg, 0.01f)
    }

    @Test
    fun `phone turned anticlockwise reads negative roll`() {
        assertEquals(-10f, turnedClockwise(-10.0).rollDeg, 0.01f)
    }

    @Test
    fun `phone lying screen-up has its camera pointing straight down`() {
        assertEquals(-90f, TiltMath.fromGravity(0f, 0f, g).pitchDeg, 0.01f)
    }

    @Test
    fun `a clockwise-tilted phone is told to rotate left, which undoes it`() {
        val input = FrameInput(
            luma = LumaStats(130f, 40f, 200f, 0.02f, 0.02f),
            color = Fixtures.neutralColor(),
            camera = Fixtures.camera(iso = 100, shutterDenominator = 250),
            tilt = turnedClockwise(12.0),
        )
        val hint = PhotoIntelligenceEngine.analyse(input).hints
            .first { it.code == CompositionAnalyzer.HORIZON_TILTED }
        assertTrue(hint.text, hint.text.endsWith("left"))
    }

    @Test
    fun `shooting straight down at a plate never complains about tilt`() {
        // Phone nearly flat: gravity is almost all z, so roll is noise. Here
        // the noise happens to read as a 35 degree roll.
        val tilt = TiltMath.fromGravity(-0.4f, 0.57f, 9.78f)
        assertTrue("noise should look like a big roll", kotlin.math.abs(tilt.rollDeg) > 30f)
        val input = FrameInput(
            labels = listOf(SceneLabel("food", 0.85f), SceneLabel("plate", 0.7f)),
            luma = LumaStats(145f, 50f, 210f, 0.01f, 0.02f),
            color = Fixtures.neutralColor(),
            camera = Fixtures.camera(iso = 200, shutterDenominator = 60),
            tilt = tilt,
        )
        val g = PhotoIntelligenceEngine.analyse(input)
        assertTrue(g.hints.none { it.code == CompositionAnalyzer.HORIZON_TILTED })
    }
}
