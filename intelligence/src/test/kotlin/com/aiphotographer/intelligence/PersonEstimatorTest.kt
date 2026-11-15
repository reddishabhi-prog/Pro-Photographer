package com.aiphotographer.intelligence

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The on-device pose detector only tracks one person, so everyone else in a
 * group photo has their body inferred from their face. If that inference is
 * wrong, group composition advice is wrong with it.
 */
class PersonEstimatorTest {

    private fun face(l: Float, t: Float, r: Float, b: Float) =
        Face(box = Box(l, t, r, b))

    @Test
    fun `an estimated body contains the face it came from`() {
        val f = Box(0.45f, 0.10f, 0.55f, 0.22f)
        val body = PersonEstimator.bodyFromFace(f)

        assertTrue("body should be wider than the face", body.width > f.width)
        assertTrue("body should start at or above the hairline", body.top <= f.top)
        assertTrue("body should extend below the chin", body.bottom > f.bottom)
        assertTrue("face left is inside the body", body.left <= f.left)
        assertTrue("face right is inside the body", body.right >= f.right)
    }

    @Test
    fun `an estimated body never leaves the frame`() {
        // Faces at the very edges must not produce boxes outside 0..1, or
        // every composition measurement downstream goes out of range.
        val corners = listOf(
            Box(0f, 0f, 0.12f, 0.14f),
            Box(0.88f, 0f, 1f, 0.14f),
            Box(0f, 0.86f, 0.12f, 1f),
            Box(0.88f, 0.86f, 1f, 1f),
            Box(0.4f, 0.75f, 0.6f, 0.95f),
        )
        for (f in corners) {
            val body = PersonEstimator.bodyFromFace(f)
            assertTrue("left ${body.left}", body.left in 0f..1f)
            assertTrue("top ${body.top}", body.top in 0f..1f)
            assertTrue("right ${body.right}", body.right in 0f..1f)
            assertTrue("bottom ${body.bottom}", body.bottom in 0f..1f)
            assertTrue("box inverted", body.right >= body.left && body.bottom >= body.top)
        }
    }

    @Test
    fun `a face low in the frame has its body cut off at the frame edge`() {
        // Which is correct: the body really does continue past the edge.
        val body = PersonEstimator.bodyFromFace(Box(0.45f, 0.70f, 0.55f, 0.84f))
        assertEquals(1f, body.bottom, 0.001f)
    }

    @Test
    fun `landmarks with no usable points give no box`() {
        assertNull(PersonEstimator.boxFromLandmarks(emptyMap()))
        assertNull(
            PersonEstimator.boxFromLandmarks(
                landmarks = mapOf(Landmark.NOSE to NormPoint(0.5f, 0.3f)),
                confidences = mapOf(Landmark.NOSE to 0.05f),
                minConfidence = 0.3f,
            )
        )
    }

    @Test
    fun `a landmark box wraps all the confident points`() {
        val landmarks = mapOf(
            Landmark.NOSE to NormPoint(0.50f, 0.20f),
            Landmark.LEFT_SHOULDER to NormPoint(0.40f, 0.35f),
            Landmark.RIGHT_SHOULDER to NormPoint(0.62f, 0.35f),
            Landmark.LEFT_ANKLE to NormPoint(0.45f, 0.92f),
        )
        val box = PersonEstimator.boxFromLandmarks(landmarks)
        assertNotNull(box)
        assertTrue("should reach the leftmost joint", box!!.left <= 0.40f)
        assertTrue("should reach the rightmost joint", box.right >= 0.62f)
        assertTrue("should reach the highest joint", box.top <= 0.20f)
        assertTrue("should reach the lowest joint", box.bottom >= 0.92f)
        assertTrue("should stay in the frame", box.left >= 0f && box.bottom <= 1f)
    }

    @Test
    fun `low confidence joints are left out of the box`() {
        val landmarks = mapOf(
            Landmark.NOSE to NormPoint(0.50f, 0.20f),
            Landmark.LEFT_ANKLE to NormPoint(0.45f, 0.95f), // detector was unsure
        )
        val confidences = mapOf(
            Landmark.NOSE to 0.9f,
            Landmark.LEFT_ANKLE to 0.1f,
        )
        val box = PersonEstimator.boxFromLandmarks(landmarks, confidences)!!
        assertTrue("an unsure ankle must not stretch the body", box.bottom < 0.5f)
    }

    @Test
    fun `in a group only one person gets real landmarks`() {
        val faces = listOf(
            face(0.10f, 0.30f, 0.20f, 0.42f),
            face(0.45f, 0.28f, 0.57f, 0.42f), // biggest, and where the pose is
            face(0.80f, 0.31f, 0.90f, 0.43f),
        )
        val landmarks = mapOf(
            Landmark.NOSE to NormPoint(0.51f, 0.35f),
            Landmark.LEFT_SHOULDER to NormPoint(0.44f, 0.48f),
            Landmark.RIGHT_SHOULDER to NormPoint(0.58f, 0.48f),
            Landmark.LEFT_HIP to NormPoint(0.47f, 0.75f),
        )

        val people = PersonEstimator.buildPeople(faces, landmarks)

        assertEquals("every face should become a person", 3, people.size)
        val withLandmarks = people.filter { it.landmarks.isNotEmpty() }
        assertEquals("exactly one person has real landmarks", 1, withLandmarks.size)
        assertEquals(
            "the landmark person should be the one the pose sits on",
            PersonEstimator.LANDMARK_CONFIDENCE, withLandmarks.first().confidence, 0.001f,
        )
        assertTrue(
            "the landmark person should be the middle face",
            withLandmarks.first().box.centerX in 0.35f..0.65f,
        )
        assertTrue(
            "the rest are estimated",
            people.filter { it.landmarks.isEmpty() }
                .all { it.confidence == PersonEstimator.ESTIMATED_CONFIDENCE },
        )
    }

    @Test
    fun `everyone is confident enough to count as a person`() {
        // The scene classifier ignores people below its confidence threshold,
        // so an estimated body still has to clear that bar.
        val people = PersonEstimator.buildPeople(
            listOf(face(0.1f, 0.3f, 0.2f, 0.42f), face(0.5f, 0.3f, 0.6f, 0.42f)),
        )
        assertEquals(2, people.size)
        assertTrue(
            "estimated people must clear the classifier threshold",
            people.all { it.confidence >= SceneClassifier.PERSON_CONFIDENCE_THRESHOLD },
        )
    }

    @Test
    fun `a person facing away is still found from their pose alone`() {
        val landmarks = mapOf(
            Landmark.LEFT_SHOULDER to NormPoint(0.40f, 0.30f),
            Landmark.RIGHT_SHOULDER to NormPoint(0.60f, 0.30f),
            Landmark.LEFT_HIP to NormPoint(0.44f, 0.60f),
            Landmark.RIGHT_HIP to NormPoint(0.56f, 0.60f),
        )
        val people = PersonEstimator.buildPeople(emptyList(), landmarks)
        assertEquals(1, people.size)
        assertTrue(people.first().landmarks.isNotEmpty())
    }

    @Test
    fun `an empty frame yields nobody`() {
        assertTrue(PersonEstimator.buildPeople(emptyList()).isEmpty())
    }
}
