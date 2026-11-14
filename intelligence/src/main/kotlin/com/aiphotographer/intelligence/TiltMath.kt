package com.aiphotographer.intelligence

import kotlin.math.atan2
import kotlin.math.hypot

/**
 * Turns the accelerometer's gravity vector into how the phone is being held.
 *
 * Lives here, not in the sensor class, because the sign of roll decides which
 * way the app tells people to turn their phone. The first version had it
 * backwards - a phone leaning right was told to lean further right - and no
 * test could catch it while the maths sat in Android code.
 *
 * Android's accelerometer reads the reaction to gravity, so a phone at rest
 * reports the direction that is UP in its own axes: x to the right of the
 * screen, y to its top, z out of the glass towards the viewer.
 */
object TiltMath {

    /**
     * [x], [y], [z] in m/s^2 as the accelerometer reports them.
     *
     * Roll is positive when the phone is rotated clockwise as you look at the
     * screen (its top leaning right). Rotated clockwise, "up" swings towards
     * the screen's left edge, so x goes negative - hence the minus sign.
     */
    /**
     * Beyond this the camera is looking down at a table (or up at a ceiling).
     * There is no horizon in the frame, and roll is just sensor noise: an
     * almost-flat gravity vector gives a random angle in the screen plane.
     */
    const val NO_HORIZON_PITCH_DEG = 60f

    /** True when the frame has a horizon worth keeping level. */
    fun hasHorizon(tilt: DeviceTilt): Boolean = kotlin.math.abs(tilt.pitchDeg) < NO_HORIZON_PITCH_DEG

    fun fromGravity(x: Float, y: Float, z: Float): DeviceTilt {
        val roll = Math.toDegrees(atan2(-x.toDouble(), y.toDouble())).toFloat()
        // The rear camera looks out of the back, opposite to z, so a phone
        // lying screen-up (z pointing up) has its camera looking straight down.
        val pitch = -Math.toDegrees(atan2(z.toDouble(), hypot(x.toDouble(), y.toDouble()))).toFloat()
        return DeviceTilt(rollDeg = roll, pitchDeg = pitch)
    }
}
