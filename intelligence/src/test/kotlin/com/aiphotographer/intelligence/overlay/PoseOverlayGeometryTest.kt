package com.aiphotographer.intelligence.overlay

import com.aiphotographer.intelligence.BodyFraming
import com.aiphotographer.intelligence.Landmark
import com.aiphotographer.intelligence.NormPoint
import com.aiphotographer.intelligence.PoseLibrary
import com.aiphotographer.intelligence.SceneType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The pose outline is drawn straight over the live camera feed. Geometry
 * errors here are not subtle - they look like a broken skeleton floating off
 * the edge of the screen.
 */
class PoseOverlayGeometryTest {

    private val viewWidth = 1080f
    private val viewHeight = 2400f

    @Test
    fun `the guide box always stays inside the frame`() {
        // Composition rules can push the target close to an edge; the guide
        // must slide back in rather than hang off the screen.
        val targets = listOf(0f, 0.05f, 0.38f, 0.5f, 0.95f, 1f)
        for (scene in listOf(SceneType.PORTRAIT, SceneType.FULL_BODY, SceneType.CLOSEUP)) {
            for (framing in BodyFraming.entries) {
                for (tx in targets) {
                    for (ty in targets) {
                        val box = PoseOverlayGeometry.guideBox(scene, framing, tx, ty)
                        assertTrue("$scene/$framing left=${box.left}", box.left >= -0.001f)
                        assertTrue("$scene/$framing top=${box.top}", box.top >= -0.001f)
                        assertTrue("$scene/$framing right=${box.right}", box.right <= 1.001f)
                        assertTrue("$scene/$framing bottom=${box.bottom}", box.bottom <= 1.001f)
                        assertTrue("$scene/$framing has no width", box.width > 0f)
                        assertTrue("$scene/$framing has no height", box.height > 0f)
                    }
                }
            }
        }
    }

    @Test
    fun `a full body guide is taller and narrower than a close-up guide`() {
        val full = PoseOverlayGeometry.guideBox(
            SceneType.FULL_BODY, BodyFraming.FULL, 0.4f, 0.5f,
        )
        val closeup = PoseOverlayGeometry.guideBox(
            SceneType.CLOSEUP, BodyFraming.CLOSEUP, 0.4f, 0.45f,
        )
        assertTrue("full body should be taller", full.height > closeup.height)
        assertTrue("full body should be narrower", full.width < closeup.width)
    }

    @Test
    fun `a drawable pose produces joints and bones on screen`() {
        val pose = PoseLibrary.byId("full_contrapposto")
        assertNotNull(pose)
        val box = PoseOverlayGeometry.guideBox(SceneType.FULL_BODY, BodyFraming.FULL, 0.4f, 0.5f)
        val overlay = PoseOverlayGeometry.build(pose!!, box, viewWidth, viewHeight)

        assertNotNull(overlay)
        assertEquals(pose.skeleton.size, overlay!!.joints.size)
        assertTrue("expected bones to draw, got ${overlay.bones.size}", overlay.bones.size >= 8)

        for ((landmark, point) in overlay.joints) {
            assertTrue("$landmark off screen horizontally: ${point.x}", point.x in 0f..viewWidth)
            assertTrue("$landmark off screen vertically: ${point.y}", point.y in 0f..viewHeight)
        }
    }

    @Test
    fun `an advice-only pose draws nothing`() {
        // Group poses carry words, not an outline.
        val pose = PoseLibrary.byId("group_stagger")
        assertNotNull(pose)
        val box = PoseOverlayGeometry.guideBox(SceneType.GROUP, BodyFraming.UNKNOWN, 0.5f, 0.5f)
        assertNull(PoseOverlayGeometry.build(pose!!, box, viewWidth, viewHeight))
    }

    @Test
    fun `the selfie camera mirrors the outline`() {
        // The front preview is flipped, so the guide has to flip with it or it
        // tells the user to move the wrong way.
        val pose = PoseLibrary.byId("portrait_shoulder_turn")!!
        val box = PoseOverlayGeometry.guideBox(SceneType.PORTRAIT, BodyFraming.HALF, 0.38f, 0.42f)

        val normal = PoseOverlayGeometry.build(pose, box, viewWidth, viewHeight, mirrored = false)!!
        val mirrored = PoseOverlayGeometry.build(pose, box, viewWidth, viewHeight, mirrored = true)!!

        val landmark = Landmark.NOSE
        val a = normal.joints[landmark]!!
        val b = mirrored.joints[landmark]!!

        assertEquals("mirroring must not move anything vertically", a.y, b.y, 0.01f)
        assertEquals("x should reflect about the centre", viewWidth - a.x, b.x, 0.01f)
        assertEquals("the box should reflect too", viewWidth - normal.boxRight, mirrored.boxLeft, 0.01f)
    }

    @Test
    fun `a zero sized view is handled rather than dividing by zero`() {
        val pose = PoseLibrary.byId("full_contrapposto")!!
        val box = PoseOverlayGeometry.guideBox(SceneType.FULL_BODY, BodyFraming.FULL, 0.4f, 0.5f)
        assertNull(PoseOverlayGeometry.build(pose, box, 0f, 0f))
        assertNull(PoseOverlayGeometry.build(pose, box, viewWidth, 0f))
    }

    @Test
    fun `every drawable pose in the library can be laid out`() {
        for (pose in PoseLibrary.ALL.filter { it.isDrawable }) {
            val scene = pose.scenes.first()
            val framing = pose.framings.first()
            val box = PoseOverlayGeometry.guideBox(scene, framing, 0.42f, 0.5f)
            val overlay = PoseOverlayGeometry.build(pose, box, viewWidth, viewHeight)
            assertNotNull("${pose.id} failed to lay out", overlay)
            assertTrue("${pose.id} drew no bones", overlay!!.bones.isNotEmpty())
        }
    }

    @Test
    fun `the outline sits on the person, nose and shoulders pinned`() {
        val pose = PoseLibrary.byId("portrait_shoulder_turn")!!
        val live = mapOf(
            Landmark.NOSE to NormPoint(0.60f, 0.40f),
            Landmark.LEFT_SHOULDER to NormPoint(0.45f, 0.62f),
            Landmark.RIGHT_SHOULDER to NormPoint(0.75f, 0.62f),
        )
        val box = PoseOverlayGeometry.boxOnPerson(pose, live, BodyFraming.HALF, SceneType.PORTRAIT)!!
        val overlay = PoseOverlayGeometry.build(pose, box, 1000f, 1000f)!!
        val nose = overlay.joints.getValue(Landmark.NOSE)
        val ls = overlay.joints.getValue(Landmark.LEFT_SHOULDER)
        val rs = overlay.joints.getValue(Landmark.RIGHT_SHOULDER)
        // Same height for nose and shoulder line as the person in frame.
        org.junit.Assert.assertEquals(400f, nose.y, 2f)
        org.junit.Assert.assertEquals(620f, (ls.y + rs.y) / 2f, 2f)
        // And centred on their shoulders.
        org.junit.Assert.assertEquals(600f, (ls.x + rs.x) / 2f, 2f)
    }

    @Test
    fun `no outline anchor without a nose and a shoulder`() {
        val pose = PoseLibrary.byId("portrait_shoulder_turn")!!
        org.junit.Assert.assertNull(
            PoseOverlayGeometry.boxOnPerson(pose, mapOf(Landmark.NOSE to NormPoint(0.5f, 0.4f)), BodyFraming.HALF, SceneType.PORTRAIT),
        )
    }
}
