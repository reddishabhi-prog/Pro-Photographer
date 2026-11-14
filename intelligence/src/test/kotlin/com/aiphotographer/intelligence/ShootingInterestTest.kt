package com.aiphotographer.intelligence

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ShootingInterestTest {

    private fun frame(labels: List<SceneLabel>, interests: Set<ShootingInterest> = emptySet()) = FrameInput(
        labels = labels,
        luma = LumaStats(140f, 50f, 205f, 0.01f, 0.02f),
        color = Fixtures.neutralColor(),
        camera = Fixtures.camera(iso = 200, shutterDenominator = 60),
        interests = interests,
    )

    @Test
    fun `a borderline dish reads as food for someone who shoots food`() {
        val unsure = listOf(SceneLabel("dish", 0.38f))
        assertEquals(SceneType.UNKNOWN, SceneClassifier.classify(frame(unsure)).first)
        assertEquals(
            SceneType.FOOD,
            SceneClassifier.classify(frame(unsure, setOf(ShootingInterest.FOOD))).first,
        )
    }

    @Test
    fun `clear evidence beats a stated interest`() {
        val mountains = listOf(SceneLabel("mountain", 0.9f), SceneLabel("sky", 0.8f))
        assertEquals(
            SceneType.LANDSCAPE,
            SceneClassifier.classify(frame(mountains, setOf(ShootingInterest.FOOD))).first,
        )
    }

    @Test
    fun `a person in frame is a portrait whatever the interests`() {
        val input = frame(listOf(SceneLabel("dish", 0.5f)), setOf(ShootingInterest.FOOD)).copy(
            people = listOf(Fixtures.person(0.3f, 0.1f, 0.7f, 0.9f, Fixtures.UPPER_BODY)),
        )
        assertEquals(SceneType.PORTRAIT, SceneClassifier.classify(input).first)
    }

    @Test
    fun `the welcome tip follows the first pick, and saved names round-trip`() {
        assertEquals(ShootingInterest.PETS.firstTip, ShootingInterest.welcomeTip(listOf(ShootingInterest.PETS, ShootingInterest.FOOD)))
        assertNull(ShootingInterest.welcomeTip(emptyList()))
        assertEquals(setOf(ShootingInterest.NIGHT), ShootingInterest.parse(listOf("NIGHT", "nonsense")))
    }
}
