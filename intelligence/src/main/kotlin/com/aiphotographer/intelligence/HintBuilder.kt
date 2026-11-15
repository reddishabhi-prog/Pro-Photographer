package com.aiphotographer.intelligence

import kotlin.math.abs

/**
 * Turns the engine's findings into the handful of words shown in the viewfinder.
 *
 * Two rules govern this file. Say it the way a person standing next to you
 * would say it - "step back", not "subject bounding box exceeds frame bounds".
 * And never show more than three at once: a viewfinder full of advice is a
 * viewfinder you can't see through.
 */
object HintBuilder {

    /** The most hints we will ever put on screen at one time. */
    const val MAX_HINTS = 3

    fun build(
        input: FrameInput,
        scene: SceneType,
        @Suppress("UNUSED_PARAMETER") framing: BodyFraming,
        lighting: LightingAssessment,
        composition: CompositionAssessment,
        angle: ShootingAngle,
    ): List<Hint> {
        val hints = ArrayList<Hint>()
        val issues = composition.issues.toHashSet()

        // ---- CRITICAL: the shot is spoiled without acting ----
        if (CompositionAnalyzer.HEAD_CROPPED in issues) {
            hints += Hint(
                HintPriority.CRITICAL, CompositionAnalyzer.HEAD_CROPPED,
                "Tilt down — the head is cut off",
            )
        }
        if (CompositionAnalyzer.LIMBS_CROPPED in issues) {
            hints += Hint(
                HintPriority.CRITICAL, CompositionAnalyzer.LIMBS_CROPPED,
                "Step back — feet or hands are cut off",
            )
        }

        // ---- IMPORTANT: a clear, easy improvement ----
        // Light problems are IMPORTANT, not CRITICAL: in AUTO the app already
        // brightens a backlit face and lengthens the exposure in the dark, so
        // the photo is usable either way. A CRITICAL here blocked "Looks good"
        // in every dim room and by every window, which is most of indoor life.
        if (lighting.type == LightingType.BACKLIT) {
            hints += Hint(
                HintPriority.IMPORTANT, "BACKLIT",
                "Bright light behind them — turn them to face the light",
            )
        }
        if (lighting.type == LightingType.LOW_LIGHT) {
            hints += Hint(
                HintPriority.IMPORTANT, "LOW_LIGHT",
                "Low light — hold the phone very still",
            )
        }
        if (CompositionAnalyzer.HORIZON_TILTED in issues) {
            // Positive roll is the phone turned clockwise; undo it by turning
            // anticlockwise, which people call "left".
            val way = if (input.tilt.rollDeg > 0) "left" else "right"
            hints += Hint(
                HintPriority.IMPORTANT, CompositionAnalyzer.HORIZON_TILTED,
                "Phone is tilted — rotate it $way",
            )
        }
        if (CompositionAnalyzer.TOO_MUCH_HEADROOM in issues) {
            hints += Hint(
                HintPriority.IMPORTANT, CompositionAnalyzer.TOO_MUCH_HEADROOM,
                "Too much space above the head — tilt down",
            )
        }
        if (CompositionAnalyzer.SUBJECT_TOO_SMALL in issues) {
            hints += Hint(
                HintPriority.IMPORTANT, CompositionAnalyzer.SUBJECT_TOO_SMALL,
                "Get closer — they're lost in the frame",
            )
        }
        if (CompositionAnalyzer.NO_LOOKING_ROOM in issues) {
            hints += Hint(
                HintPriority.IMPORTANT, CompositionAnalyzer.NO_LOOKING_ROOM,
                "Leave space in the direction they're looking",
            )
        }
        if (lighting.type == LightingType.HARSH) {
            hints += Hint(
                HintPriority.IMPORTANT, "HARSH",
                "Harsh light. Move into shade for softer skin",
            )
        }
        if (CompositionAnalyzer.DOCUMENT_NOT_SQUARE in issues) {
            hints += Hint(
                HintPriority.IMPORTANT, CompositionAnalyzer.DOCUMENT_NOT_SQUARE,
                "Hold the phone flat and square to the page",
            )
        }
        if (CompositionAnalyzer.CROPPED_AT_JOINT in issues) {
            hints += Hint(
                HintPriority.IMPORTANT, CompositionAnalyzer.CROPPED_AT_JOINT,
                "Don't crop at the knee — crop above or below it",
            )
        }

        // ---- POLISH: the difference between good and better ----
        if (CompositionAnalyzer.SUBJECT_OFF_TARGET in issues) {
            val main = SceneClassifier.mainPerson(input)
            if (main != null) {
                val dx = composition.targetCenterX - main.box.centerX
                if (abs(dx) > 0.06f) {
                    val way = if (dx < 0) "left" else "right"
                    hints += Hint(
                        HintPriority.POLISH, CompositionAnalyzer.SUBJECT_OFF_TARGET,
                        "Pan slightly $way",
                    )
                }
            }
        }
        if (CompositionAnalyzer.DEAD_CENTRE in issues) {
            hints += Hint(
                HintPriority.POLISH, CompositionAnalyzer.DEAD_CENTRE,
                "Try them off-centre, on a third line",
            )
        }
        if (CompositionAnalyzer.HORIZON_CENTRED in issues) {
            hints += Hint(
                HintPriority.POLISH, CompositionAnalyzer.HORIZON_CENTRED,
                "Put the horizon on a third, not the middle",
            )
        }
        if (lighting.type == LightingType.FLAT) {
            hints += Hint(
                HintPriority.POLISH, "FLAT",
                "Flat light. Side light would give it shape",
            )
        }
        if (lighting.type == LightingType.GOLDEN) {
            hints += Hint(
                HintPriority.POLISH, "GOLDEN",
                "Beautiful warm light — shoot now, it won't last",
            )
        }
        if (angle == ShootingAngle.SLIGHTLY_HIGH &&
            (scene == SceneType.PORTRAIT || scene == SceneType.CLOSEUP)
        ) {
            hints += Hint(
                HintPriority.POLISH, "ANGLE_SLIGHTLY_HIGH",
                "Lift the phone just above eye level",
            )
        }
        if (angle == ShootingAngle.SLIGHTLY_LOW && scene == SceneType.FULL_BODY) {
            hints += Hint(
                HintPriority.POLISH, "ANGLE_SLIGHTLY_LOW",
                "Drop the phone to waist height to lengthen the legs",
            )
        }
        if (angle == ShootingAngle.HIGH && scene == SceneType.FOOD) {
            hints += Hint(
                HintPriority.POLISH, "ANGLE_HIGH_FOOD",
                "Shoot straight down for a clean flat-lay",
            )
        }
        if (angle == ShootingAngle.LOW && scene == SceneType.PET) {
            hints += Hint(
                HintPriority.POLISH, "ANGLE_LOW_PET",
                "Get down to their eye level",
            )
        }
        if (scene == SceneType.GROUP) {
            hints += Hint(
                HintPriority.POLISH, "GROUP_TIGHTEN",
                "Close the gaps and stagger the heights",
            )
        }

        // Most urgent first; order within a band stays as written above.
        return hints
            .sortedByDescending { it.priority.weight }
            .take(MAX_HINTS)
    }
}
