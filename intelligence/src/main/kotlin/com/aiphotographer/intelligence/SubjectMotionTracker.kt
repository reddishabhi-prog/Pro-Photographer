package com.aiphotographer.intelligence

import kotlin.math.hypot

/**
 * Estimates how fast the subject is moving across the frame.
 *
 * This is what decides whether the app freezes the action or protects the ISO.
 * It is deliberately crude - the centre of the subject's box, frame to frame -
 * because anything cleverer costs more than it is worth at 15 frames a second,
 * and the only decision riding on it is "fast, moving, or still".
 *
 * Not thread-safe: it belongs to the single analysis thread.
 */
class SubjectMotionTracker(
    /** How much of each new reading to take. Lower is smoother. */
    private val smoothing: Float = 0.35f,
    /**
     * Movement, in frame widths per second, that counts as full speed.
     * Half a frame per second is a brisk walk across the shot.
     */
    private val fullSpeed: Float = 0.5f,
) {
    private var lastCenterX: Float? = null
    private var lastCenterY: Float? = null
    private var smoothed = 0f

    /** The latest estimate, 0..1. */
    val motion: Float get() = smoothed

    /**
     * Feed in the main subject's box and the time since the previous frame.
     * Returns the smoothed estimate, 0..1.
     */
    fun update(subject: Box?, deltaSeconds: Float): Float {
        if (subject == null) {
            // Nobody to track: decay towards still rather than snapping, so a
            // momentary detection drop does not change the shutter speed.
            smoothed += (0f - smoothed) * smoothing
            lastCenterX = null
            lastCenterY = null
            return smoothed
        }

        val cx = subject.centerX
        val cy = subject.centerY
        val previousX = lastCenterX
        val previousY = lastCenterY
        lastCenterX = cx
        lastCenterY = cy

        if (previousX == null || previousY == null || deltaSeconds <= 0f) {
            return smoothed
        }

        val distance = hypot((cx - previousX).toDouble(), (cy - previousY).toDouble()).toFloat()
        val perSecond = distance / deltaSeconds
        val instant = (perSecond / fullSpeed).coerceIn(0f, 1f)
        smoothed = (smoothed + (instant - smoothed) * smoothing).coerceIn(0f, 1f)
        return smoothed
    }

    /** Forget everything, e.g. after switching camera. */
    fun reset() {
        lastCenterX = null
        lastCenterY = null
        smoothed = 0f
    }
}
