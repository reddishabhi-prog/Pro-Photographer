package com.aiphotographer.intelligence

import com.aiphotographer.intelligence.imaging.EnhancementPlan
import com.aiphotographer.intelligence.imaging.ImageEnhancer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoActionsTest {

    private fun backlit() = PhotoIntelligenceEngine.analyse(
        FrameInput(
            people = listOf(Fixtures.person(0.32f, 0.26f, 0.68f, 1.0f, Fixtures.UPPER_BODY)),
            faces = listOf(Fixtures.face(0.40f, 0.26f, 0.60f, 0.46f)),
            labels = listOf(SceneLabel("person", 0.9f)),
            luma = LumaStats(
                mean = 160f, p5 = 20f, p95 = 250f, clippedShadows = 0.04f, clippedHighlights = 0.08f,
                regions = Fixtures.grid { r, c -> if (r in 2..5 && c in 2..3) 35f else 235f },
            ),
            color = Fixtures.neutralColor(),
            camera = Fixtures.camera(iso = 50, shutterDenominator = 1000),
        ),
    )

    @Test
    fun `auto never pushes exposure more than a stop, however strong the backlight`() {
        val g = backlit()
        assertTrue("fixture should want a big push, got ${g.settings.evCompensation}", g.settings.evCompensation > 1f)
        assertEquals(AutoActions.MAX_AUTO_EV, AutoActions.autoEv(g), 0.001f)
    }

    @Test
    fun `the user is told what the app is doing, phone mode first`() {
        val labels = AutoActions.describe(backlit(), captureMode = "HDR")
        assertEquals("HDR", labels.first())
        assertTrue(labels.toString(), "Brightening the face" in labels)
        assertTrue(labels.size <= 3)
    }

    @Test
    fun `natural strength scales every correction the same way`() {
        val plan = EnhancementPlan(shadowLift = 0.5f, highlightRecovery = 0.5f, vibrance = 0.5f, clarity = 0.5f)
        val n = ImageEnhancer.natural(plan)
        assertEquals(0.3f, n.shadowLift, 0.001f)
        assertEquals(0.3f, n.vibrance, 0.001f)
        assertEquals(0.3f, n.clarity, 0.001f)
    }
}
