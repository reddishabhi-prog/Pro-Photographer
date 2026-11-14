package com.aiphotographer.intelligence

import kotlin.math.roundToInt

/**
 * The photographer's brain.
 *
 * Give it one frame's worth of observations and it returns everything the app
 * needs to show and do: what is being photographed, what the light is doing,
 * how the frame is arranged, where to stand, what to tell the user, and what
 * to set the camera to.
 *
 * It is deliberately stateless and free of Android types, so the same code
 * runs in a unit test and in the viewfinder.
 */
object PhotoIntelligenceEngine {

    /** Composition counts for slightly more than light in the final score. */
    private const val COMPOSITION_WEIGHT = 0.55
    private const val LIGHTING_WEIGHT = 0.45

    /** Below this the app will not tell the user the shot is good to take. */
    private const val READY_THRESHOLD = 70

    fun analyse(input: FrameInput, poseVariant: Int = 0): Guidance {
        val (scene, framing) = SceneClassifier.classify(input)

        val people = input.people.filter {
            it.confidence >= SceneClassifier.PERSON_CONFIDENCE_THRESHOLD
        }
        val mainBox = people.maxByOrNull { it.box.area }?.box

        val lighting = LightingAnalyzer.assess(input, mainBox)
        val composition = CompositionAnalyzer.assess(input, scene, framing)
        val angle = AngleAdvisor.recommend(input, scene, framing)
        val settings = SettingsAdvisor.recommend(input, scene, lighting)
        val pose = PoseLibrary.select(scene, framing, people.size, poseVariant)
        val hints = HintBuilder.build(input, scene, framing, lighting, composition, angle)

        val overall = (
            composition.score * COMPOSITION_WEIGHT + lighting.score * LIGHTING_WEIGHT
            ).roundToInt().coerceIn(0, 100)

        val hasCritical = hints.any { it.priority == HintPriority.CRITICAL }

        return Guidance(
            scene = scene,
            framing = framing,
            lighting = lighting,
            composition = composition,
            angle = angle,
            settings = settings,
            poseId = pose?.id,
            hints = hints,
            overallScore = overall,
            readyToShoot = overall >= READY_THRESHOLD && !hasCritical,
        )
    }
}
