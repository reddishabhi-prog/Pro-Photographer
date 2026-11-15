package com.aiphotographer

/**
 * What ships switched on in this build, and why.
 *
 * Decided after the first real-phone test, where the verdict was "confusing"
 * and "it doesn't take photos". The test for every feature: does a person
 * who has never read a manual get a better photo because of it, in the first
 * ten seconds? Anything that fails stays in the code, switched off here, until
 * it is proven on a phone. Turning one back on is a one-word change.
 */
object Features {

    /**
     * Manual ISO, shutter, focus and white balance. Valuable to the few who
     * know what they mean, a wall of dials to everyone else - and the people
     * who want them already own a camera app that does it. Off.
     */
    const val PRO_MODE = false

    /**
     * Pose suggestions: a pose card under the viewfinder, a library to pick
     * from, and the ghost outline. Back on: the outline was misplaced only
     * because the preview was cropped, which is fixed, and the second phone
     * test asked for poses by name.
     */
    const val POSE_GUIDE = true

    /**
     * The live panel over the viewfinder: what the camera is really set to and
     * what ShotMuse is doing about the scene. The second phone test asked to
     * see the app working; showing real numbers is how it earns that trust.
     */
    const val SHOT_HUD = true

    /** The old ISO / shutter readout, replaced by [SHOT_HUD]. */
    const val TECH_READOUT = false

    /**
     * Rule-of-thirds grid. Harmless, but the coaching already says where to
     * put the subject, so it is clutter on a first launch. Off.
     */
    const val GRID = false

    /** HDR button. AUTO already decides; a second switch only adds doubt. Off. */
    const val HDR_BUTTON = false
}
