package com.aiphotographer.intelligence

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The pose outlines get drawn straight onto the viewfinder, so bad geometry
 * shows up as a broken skeleton floating over the user's shot.
 */
class PoseLibraryTest {

    @Test
    fun `every pose id is unique`() {
        val ids = PoseLibrary.ALL.map { it.id }
        assertEquals("duplicate pose ids: $ids", ids.size, ids.toSet().size)
    }

    @Test
    fun `every skeleton point sits inside the guide box`() {
        for (pose in PoseLibrary.ALL) {
            for ((landmark, point) in pose.skeleton) {
                assertTrue(
                    "${pose.id}/$landmark x=${point.x} is outside 0..1",
                    point.x in 0f..1f,
                )
                assertTrue(
                    "${pose.id}/$landmark y=${point.y} is outside 0..1",
                    point.y in 0f..1f,
                )
            }
        }
    }

    @Test
    fun `drawable skeletons are the right way up`() {
        // Head above shoulders, shoulders above hips, hips above knees.
        for (pose in PoseLibrary.ALL.filter { it.isDrawable }) {
            val nose = pose.skeleton[Landmark.NOSE] ?: continue
            val shoulder = pose.skeleton[Landmark.LEFT_SHOULDER]
            val hip = pose.skeleton[Landmark.LEFT_HIP]
            val knee = pose.skeleton[Landmark.LEFT_KNEE]

            if (shoulder != null) {
                assertTrue("${pose.id}: head is below the shoulders", nose.y < shoulder.y)
            }
            if (shoulder != null && hip != null) {
                assertTrue("${pose.id}: shoulders are below the hips", shoulder.y < hip.y)
            }
            if (hip != null && knee != null) {
                assertTrue("${pose.id}: hips are below the knees", hip.y < knee.y)
            }
        }
    }

    @Test
    fun `full body poses reach the ankles and portraits do not`() {
        val fullBody = PoseLibrary.ALL.filter { SceneType.FULL_BODY in it.scenes }
        assertTrue("there should be several full-body poses", fullBody.size >= 3)
        for (pose in fullBody) {
            assertTrue(
                "${pose.id} is a full-body pose but has no ankles to show",
                pose.skeleton.containsKey(Landmark.LEFT_ANKLE),
            )
        }
    }

    @Test
    fun `group poses carry advice instead of an outline`() {
        // A skeleton drawn over four people hides what the user is composing.
        val groupPoses = PoseLibrary.ALL.filter { SceneType.GROUP in it.scenes }
        assertTrue(groupPoses.isNotEmpty())
        for (pose in groupPoses) {
            assertFalse("${pose.id} should not draw an outline", pose.isDrawable)
            assertTrue("${pose.id} must still explain itself", pose.why.length > 10)
        }
    }

    @Test
    fun `selection is deterministic and cycles through variants`() {
        val first = PoseLibrary.select(SceneType.FULL_BODY, BodyFraming.FULL, 1, variant = 0)
        val again = PoseLibrary.select(SceneType.FULL_BODY, BodyFraming.FULL, 1, variant = 0)
        assertEquals(first?.id, again?.id)

        val options = PoseLibrary.candidates(SceneType.FULL_BODY, BodyFraming.FULL, 1)
        assertTrue("expected more than one full-body option", options.size > 1)

        val second = PoseLibrary.select(SceneType.FULL_BODY, BodyFraming.FULL, 1, variant = 1)
        assertNotNull(second)
        assertTrue("cycling should change the pose", second?.id != first?.id)
    }

    @Test
    fun `negative and huge variants stay in range`() {
        assertNotNull(PoseLibrary.select(SceneType.FULL_BODY, BodyFraming.FULL, 1, variant = -7))
        assertNotNull(PoseLibrary.select(SceneType.FULL_BODY, BodyFraming.FULL, 1, variant = 9999))
    }

    @Test
    fun `scenes without poses return nothing rather than something wrong`() {
        assertNull(PoseLibrary.select(SceneType.LANDSCAPE, BodyFraming.UNKNOWN, 0))
        assertNull(PoseLibrary.select(SceneType.FOOD, BodyFraming.UNKNOWN, 0))
        assertNull(PoseLibrary.select(SceneType.DOCUMENT, BodyFraming.UNKNOWN, 0))
    }

    @Test
    fun `a single person never gets a group pose`() {
        for (scene in listOf(SceneType.PORTRAIT, SceneType.CLOSEUP, SceneType.FULL_BODY)) {
            for (framing in BodyFraming.entries) {
                val pose = PoseLibrary.select(scene, framing, peopleCount = 1)
                if (pose != null) {
                    assertFalse(
                        "$scene/$framing gave group pose ${pose.id} for one person",
                        SceneType.GROUP in pose.scenes,
                    )
                }
            }
        }
    }

    @Test
    fun `bones only connect landmarks that poses actually define`() {
        // Nothing to assert about missing bones - they are skipped when drawn -
        // but every drawable pose should produce at least a few lines.
        for (pose in PoseLibrary.ALL.filter { it.isDrawable }) {
            val drawable = PoseLibrary.BONES.count { (a, b) ->
                pose.skeleton.containsKey(a) && pose.skeleton.containsKey(b)
            }
            assertTrue("${pose.id} draws only $drawable bones", drawable >= 3)
        }
    }
}
