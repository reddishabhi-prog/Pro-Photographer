package com.aiphotographer.intelligence

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GuidanceStabilizerTest {

    private fun guidance(
        scene: SceneType = SceneType.PORTRAIT,
        hints: List<Hint> = emptyList(),
        ready: Boolean = true,
        score: Int = 80,
        iso: Int = 100,
    ) = Guidance(
        scene = scene,
        framing = BodyFraming.HALF,
        lighting = LightingAssessment(LightingType.GOOD, LightDirection.FRONT, score, 0f),
        composition = CompositionAssessment(score, 0.38f, 0.42f, emptyList()),
        angle = ShootingAngle.EYE_LEVEL,
        settings = SettingsRecommendation(
            iso, 125, 0f, WhiteBalanceMode.AUTO, false, "4:3", "test",
        ),
        poseId = null,
        hints = hints,
        overallScore = score,
        readyToShoot = ready,
    )

    private fun hint(code: String, priority: HintPriority = HintPriority.POLISH) =
        Hint(priority, code, code)

    @Test
    fun `the very first frame is shown immediately`() {
        val stabilizer = GuidanceStabilizer()
        val first = guidance(hints = listOf(hint("A")))
        assertEquals(listOf("A"), stabilizer.update(first).hints.map { it.code })
    }

    @Test
    fun `a one frame wobble is ignored`() {
        // A face dropping out for a single frame must not change the advice.
        val stabilizer = GuidanceStabilizer(framesToSwitch = 3)
        val steady = guidance(hints = listOf(hint("STEADY")))
        stabilizer.update(steady)

        val blip = guidance(hints = listOf(hint("BLIP")))
        val shown = stabilizer.update(blip)

        assertEquals(
            "a single odd frame should not reach the user",
            listOf("STEADY"), shown.hints.map { it.code },
        )
    }

    @Test
    fun `advice that persists does get shown`() {
        val stabilizer = GuidanceStabilizer(framesToSwitch = 3)
        stabilizer.update(guidance(hints = listOf(hint("OLD"))))

        val next = guidance(hints = listOf(hint("NEW")))
        stabilizer.update(next) // 1
        stabilizer.update(next) // 2
        val shown = stabilizer.update(next) // 3 - switches

        assertEquals(listOf("NEW"), shown.hints.map { it.code })
    }

    @Test
    fun `an urgent warning is never delayed`() {
        // Being polite about a ruined photograph is worse than being abrupt.
        val stabilizer = GuidanceStabilizer(framesToSwitch = 5)
        stabilizer.update(guidance(hints = listOf(hint("POLISH_THING"))))

        val critical = guidance(
            hints = listOf(hint("HEAD_CROPPED", HintPriority.CRITICAL)),
            ready = false,
        )
        val shown = stabilizer.update(critical)

        assertEquals(
            "a critical hint must appear at once",
            listOf("HEAD_CROPPED"), shown.hints.map { it.code },
        )
    }

    @Test
    fun `clearing an urgent warning is also immediate`() {
        // Once the user has fixed it, the warning must go away at once or
        // they will think the fix did not work.
        val stabilizer = GuidanceStabilizer(framesToSwitch = 5)
        stabilizer.update(
            guidance(hints = listOf(hint("LIMBS_CROPPED", HintPriority.CRITICAL)), ready = false)
        )

        val fixed = guidance(hints = emptyList(), ready = true)
        val shown = stabilizer.update(fixed)

        assertTrue("the warning should be gone", shown.hints.isEmpty())
        assertTrue("and the shot should read as ready", shown.readyToShoot)
    }

    @Test
    fun `live numbers keep updating while the advice is held`() {
        // The exposure readout must never be stale, even mid-hesitation.
        val stabilizer = GuidanceStabilizer(framesToSwitch = 3)
        stabilizer.update(guidance(hints = listOf(hint("HOLD")), score = 50, iso = 100))

        val changed = guidance(hints = listOf(hint("OTHER")), score = 90, iso = 800)
        val shown = stabilizer.update(changed)

        assertEquals("advice is held", listOf("HOLD"), shown.hints.map { it.code })
        assertEquals("but the score is live", 90, shown.overallScore)
        assertEquals("and so are the settings", 800, shown.settings.iso)
    }

    @Test
    fun `unchanged advice passes straight through`() {
        val stabilizer = GuidanceStabilizer()
        val a = guidance(hints = listOf(hint("SAME")), score = 70)
        stabilizer.update(a)
        val b = guidance(hints = listOf(hint("SAME")), score = 75)
        val shown = stabilizer.update(b)

        assertEquals(listOf("SAME"), shown.hints.map { it.code })
        assertEquals("the newest numbers should win", 75, shown.overallScore)
    }

    @Test
    fun `flapping between two readings does not switch`() {
        // The classic failure: the scene alternates every frame. Neither
        // reading should ever reach the screen.
        val stabilizer = GuidanceStabilizer(framesToSwitch = 3)
        stabilizer.update(guidance(scene = SceneType.PORTRAIT, hints = listOf(hint("P"))))

        val a = guidance(scene = SceneType.CLOSEUP, hints = listOf(hint("C")))
        val b = guidance(scene = SceneType.FULL_BODY, hints = listOf(hint("F")))
        var shown = guidance()
        repeat(6) { index ->
            shown = stabilizer.update(if (index % 2 == 0) a else b)
        }

        assertEquals(
            "neither alternative held long enough",
            listOf("P"), shown.hints.map { it.code },
        )
    }

    @Test
    fun `reset clears what is on screen`() {
        val stabilizer = GuidanceStabilizer(framesToSwitch = 3)
        stabilizer.update(guidance(hints = listOf(hint("OLD"))))
        stabilizer.reset()

        val fresh = guidance(hints = listOf(hint("FRESH")))
        assertEquals(listOf("FRESH"), stabilizer.update(fresh).hints.map { it.code })
    }

    @Test
    fun `a change of scene with the same advice is still held`() {
        val stabilizer = GuidanceStabilizer(framesToSwitch = 3)
        stabilizer.update(guidance(scene = SceneType.PORTRAIT, hints = listOf(hint("X"))))
        val shown = stabilizer.update(guidance(scene = SceneType.CLOSEUP, hints = listOf(hint("X"))))
        assertEquals(
            "the scene is part of what the user is being told",
            SceneType.PORTRAIT, shown.scene,
        )
    }
}
