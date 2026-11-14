package com.aiphotographer.intelligence

import java.util.Locale

/**
 * Chooses the camera settings for the shot.
 *
 * The order matters: decide the shutter speed the subject demands, work out
 * the ISO that exposes correctly at that speed, then trade shutter back down
 * if the ISO would wreck the image. That is how a photographer thinks, and
 * it is why the results beat a generic auto mode.
 */
object SettingsAdvisor {

    /** Above this, noise starts costing more than motion blur saves. */
    private const val ISO_PAIN_THRESHOLD = 3200

    fun recommend(
        input: FrameInput,
        scene: SceneType,
        lighting: LightingAssessment,
    ): SettingsRecommendation {
        val sceneEv = ExposureMath.sceneEv(input.camera)
        val floor = ExposureMath.handheldFloor(input.camera.focalLengthMm)

        // 1. What shutter speed does this subject need?
        var target = when (scene) {
            SceneType.PORTRAIT, SceneType.CLOSEUP, SceneType.GROUP,
            SceneType.FULL_BODY -> maxOf(floor, 125)
            SceneType.PET -> maxOf(floor, 250) // animals never hold still
            SceneType.LANDSCAPE, SceneType.ARCHITECTURE, SceneType.DOCUMENT -> floor
            SceneType.FOOD -> maxOf(floor, 60)
            SceneType.NIGHT -> maxOf(30, minOf(floor, 60))
            else -> maxOf(floor, 125)
        }

        // 2. A moving subject overrides everything: freeze it.
        target = when {
            input.subjectMotion > 0.6f -> maxOf(target, 500)
            input.subjectMotion > 0.3f -> maxOf(target, 250)
            else -> target
        }
        target = ExposureMath.snapShutter(target)

        // 3. What ISO exposes correctly at that speed?
        var iso = ExposureMath.requiredIso(target, sceneEv, input.camera.aperture)

        // 4. If the ISO is punishing, give shutter speed back down to the floor.
        while (iso > ISO_PAIN_THRESHOLD && target > floor) {
            val slower = ExposureMath.snapShutter(target / 2)
            if (slower >= target) break // no further stop available
            target = slower
            iso = ExposureMath.requiredIso(target, sceneEv, input.camera.aperture)
        }

        // 5. White balance: correct a cast, but never neutralise good light.
        val warmth = input.color.warmth
        val whiteBalance = when {
            scene == SceneType.NIGHT ->
                if (warmth > 0.18f) WhiteBalanceMode.INCANDESCENT else WhiteBalanceMode.AUTO
            lighting.type == LightingType.GOLDEN -> WhiteBalanceMode.DAYLIGHT
            warmth < -0.18f -> WhiteBalanceMode.CLOUDY
            warmth > 0.30f -> WhiteBalanceMode.INCANDESCENT
            else -> WhiteBalanceMode.AUTO
        }

        // 6. HDR only when the scene genuinely exceeds one exposure, and only
        //    when nothing is moving - otherwise the frames ghost.
        val wideDynamicRange =
            (input.luma.clippedHighlights > 0.03f && input.luma.clippedShadows > 0.03f) ||
                input.luma.contrast > 215f
        val useHdr = wideDynamicRange && input.subjectMotion < 0.3f

        val aspectRatio = when (scene) {
            SceneType.LANDSCAPE, SceneType.ARCHITECTURE -> "16:9"
            else -> "4:3"
        }

        val reason = buildString {
            append("EV")
            append(String.format(Locale.US, "%.1f", sceneEv))
            append(" scene, ")
            append(scene.name.lowercase().replace('_', ' '))
            append(" at 1/")
            append(target)
            append("s")
        }

        return SettingsRecommendation(
            iso = iso,
            shutterDenominator = target,
            evCompensation = lighting.evCompensation,
            whiteBalance = whiteBalance,
            useHdr = useHdr,
            aspectRatio = aspectRatio,
            reason = reason,
        )
    }
}
