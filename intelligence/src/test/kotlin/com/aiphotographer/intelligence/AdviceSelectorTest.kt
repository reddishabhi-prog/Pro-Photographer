package com.aiphotographer.intelligence

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AdviceSelectorTest {

    private fun frame(
        roll: Float = 1f,
        luma: LumaStats = LumaStats(132f, 42f, 205f, 0.01f, 0.02f, Fixtures.grid { _, c -> if (c < 3) 150f else 120f }),
        camera: CameraState = Fixtures.camera(iso = 100, shutterDenominator = 250),
    ) = FrameInput(
        people = listOf(Fixtures.person(0.22f, 0.10f, 0.54f, 0.74f, Fixtures.UPPER_BODY)),
        faces = listOf(Fixtures.face(0.30f, 0.10f, 0.46f, 0.26f, eulerY = 5f)),
        labels = listOf(SceneLabel("person", 0.95f)),
        luma = luma,
        color = ColorStats(165f, 150f, 135f),
        camera = camera,
        tilt = DeviceTilt(rollDeg = roll),
    )

    @Test
    fun `a good frame goes quiet even when polish advice exists`() {
        // The golden-hour portrait: the engine still has two polish notes.
        val g = PhotoIntelligenceEngine.analyse(frame())
        assertTrue("fixture should carry polish hints", g.hints.isNotEmpty())
        val advice = AdviceSelector.select(g)
        assertTrue(advice.looksGood)
        assertNull(advice.hint)
    }

    @Test
    fun `only one instruction is ever shown, the most urgent`() {
        val g = PhotoIntelligenceEngine.analyse(frame(roll = 14f))
        val advice = AdviceSelector.select(g)
        assertFalse(advice.looksGood)
        assertEquals(CompositionAnalyzer.HORIZON_TILTED, advice.hint?.code)
    }

    @Test
    fun `a dim room is a tip, not a wall`() {
        // ISO 1600 at 1/30: an ordinary lamp-lit living room in the evening.
        val dim = frame(
            luma = LumaStats(70f, 10f, 180f, 0.05f, 0.01f, Fixtures.flatGrid(70f)),
            camera = Fixtures.camera(iso = 1600, shutterDenominator = 30),
        )
        val g = PhotoIntelligenceEngine.analyse(dim)
        assertTrue(g.hints.none { it.priority == HintPriority.CRITICAL })
        assertEquals("LOW_LIGHT", AdviceSelector.select(g).hint?.code)
    }

    @Test
    fun `no analysis yet means say nothing`() {
        val advice = AdviceSelector.select(null)
        assertNull(advice.hint)
        assertFalse(advice.looksGood)
    }
}
