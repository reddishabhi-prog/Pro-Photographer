package com.aiphotographer.intelligence

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SubjectMotionTrackerTest {

    private fun boxAt(cx: Float, cy: Float) =
        Box(cx - 0.1f, cy - 0.2f, cx + 0.1f, cy + 0.2f)

    @Test
    fun `a still subject reads as still`() {
        val tracker = SubjectMotionTracker()
        var motion = 0f
        repeat(20) { motion = tracker.update(boxAt(0.5f, 0.5f), deltaSeconds = 1f / 15f) }
        assertEquals("a still subject must not trigger a fast shutter", 0f, motion, 0.01f)
    }

    @Test
    fun `the first frame cannot know anything yet`() {
        val tracker = SubjectMotionTracker()
        assertEquals(0f, tracker.update(boxAt(0.2f, 0.5f), 1f / 15f), 0.001f)
    }

    @Test
    fun `a running subject reads as fast`() {
        // Crossing most of the frame in well under a second.
        val tracker = SubjectMotionTracker()
        var motion = 0f
        var x = 0.05f
        repeat(20) {
            x += 0.045f
            motion = tracker.update(boxAt(x, 0.5f), deltaSeconds = 1f / 15f)
        }
        assertTrue("expected high motion, got $motion", motion > 0.6f)
    }

    @Test
    fun `a slow drift is movement but not a sprint`() {
        val tracker = SubjectMotionTracker()
        var motion = 0f
        var x = 0.3f
        repeat(40) {
            x += 0.004f
            motion = tracker.update(boxAt(x, 0.5f), deltaSeconds = 1f / 15f)
        }
        assertTrue("expected some motion, got $motion", motion > 0.05f)
        assertTrue("expected not full speed, got $motion", motion < 0.6f)
    }

    @Test
    fun `losing the subject decays instead of snapping`() {
        val tracker = SubjectMotionTracker()
        var x = 0.05f
        repeat(12) {
            x += 0.05f
            tracker.update(boxAt(x, 0.5f), 1f / 15f)
        }
        val before = tracker.motion
        assertTrue("should have built up motion", before > 0.3f)

        val afterOneDrop = tracker.update(null, 1f / 15f)
        assertTrue("one dropped frame should not reset it", afterOneDrop > before * 0.5f)

        repeat(30) { tracker.update(null, 1f / 15f) }
        assertTrue("a long absence should settle to still", tracker.motion < 0.05f)
    }

    @Test
    fun `the estimate always stays in range`() {
        val tracker = SubjectMotionTracker()
        // Teleporting the subject must not produce a value above 1.
        tracker.update(boxAt(0.05f, 0.05f), 1f / 30f)
        val motion = tracker.update(boxAt(0.95f, 0.95f), 1f / 30f)
        assertTrue("motion out of range: $motion", motion in 0f..1f)
    }

    @Test
    fun `a zero or negative time step is ignored safely`() {
        val tracker = SubjectMotionTracker()
        tracker.update(boxAt(0.3f, 0.5f), 1f / 15f)
        val motion = tracker.update(boxAt(0.6f, 0.5f), deltaSeconds = 0f)
        assertTrue("must not divide by zero: $motion", motion in 0f..1f)
    }

    @Test
    fun `reset forgets the subject`() {
        val tracker = SubjectMotionTracker()
        var x = 0.1f
        repeat(15) {
            x += 0.05f
            tracker.update(boxAt(x, 0.5f), 1f / 15f)
        }
        assertTrue(tracker.motion > 0f)
        tracker.reset()
        assertEquals(0f, tracker.motion, 0.001f)
    }
}
