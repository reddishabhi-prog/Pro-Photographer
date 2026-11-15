package com.aiphotographer.intelligence

/**
 * Recommends where to hold the camera.
 *
 * Camera height changes a photograph more than almost any setting: slightly
 * above eye level flatters a face, below waist height lengthens a standing
 * body, and straight down is the only honest angle for food and paper.
 */
object AngleAdvisor {

    fun recommend(
        input: FrameInput,
        scene: SceneType,
        @Suppress("UNUSED_PARAMETER") framing: BodyFraming,
    ): ShootingAngle = when (scene) {
        SceneType.FOOD, SceneType.DOCUMENT -> ShootingAngle.HIGH
        SceneType.FULL_BODY -> ShootingAngle.SLIGHTLY_LOW
        SceneType.PORTRAIT, SceneType.CLOSEUP -> ShootingAngle.SLIGHTLY_HIGH
        SceneType.PET -> ShootingAngle.LOW
        SceneType.ARCHITECTURE ->
            if (input.tilt.pitchDeg > 12f) ShootingAngle.LOW else ShootingAngle.EYE_LEVEL
        else -> ShootingAngle.EYE_LEVEL
    }
}
