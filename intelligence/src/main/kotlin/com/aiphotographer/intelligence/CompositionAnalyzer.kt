package com.aiphotographer.intelligence

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.min

/**
 * Judges how the frame is arranged and says where the subject should move.
 *
 * The rules encoded here are the ones that separate a snapshot from a
 * photograph: subject placement, headroom, clean crops, a level horizon, and
 * leaving space in the direction a subject is looking.
 */
object CompositionAnalyzer {

    /** Issue codes, shared with the hint builder and the tests. */
    const val HORIZON_TILTED = "HORIZON_TILTED"
    const val HORIZON_SLIGHTLY_TILTED = "HORIZON_SLIGHTLY_TILTED"
    const val SUBJECT_TOO_SMALL = "SUBJECT_TOO_SMALL"
    const val TOO_MUCH_HEADROOM = "TOO_MUCH_HEADROOM"
    const val HEAD_CROPPED = "HEAD_CROPPED"
    const val LIMBS_CROPPED = "LIMBS_CROPPED"
    const val CROPPED_AT_JOINT = "CROPPED_AT_JOINT"
    const val SUBJECT_OFF_TARGET = "SUBJECT_OFF_TARGET"
    const val DEAD_CENTRE = "DEAD_CENTRE"
    const val NO_LOOKING_ROOM = "NO_LOOKING_ROOM"
    const val HORIZON_CENTRED = "HORIZON_CENTRED"
    const val DOCUMENT_NOT_SQUARE = "DOCUMENT_NOT_SQUARE"

    private const val EDGE_MARGIN = 0.012f
    private const val HEADROOM_LIMIT = 0.22f

    /**
     * Where the main subject's centre ideally sits.
     *
     * Portraits read best off-centre with the eyes near the upper third.
     * Landscapes and architecture tolerate - often want - symmetry.
     */
    internal fun idealSubjectPoint(scene: SceneType, framing: BodyFraming): Pair<Float, Float> =
        when (scene) {
            SceneType.PORTRAIT, SceneType.CLOSEUP -> 0.38f to 0.42f
            SceneType.FULL_BODY -> 0.40f to 0.52f
            SceneType.GROUP -> 0.50f to 0.50f
            SceneType.LANDSCAPE, SceneType.ARCHITECTURE -> 0.50f to 0.50f
            SceneType.FOOD -> 0.50f to 0.50f
            SceneType.PET, SceneType.OBJECT -> 0.40f to 0.48f
            else -> 0.50f to 0.50f
        }

    fun assess(
        input: FrameInput,
        scene: SceneType,
        framing: BodyFraming,
    ): CompositionAssessment {
        val issues = ArrayList<String>()
        var score = 100

        var (targetX, targetY) = idealSubjectPoint(scene, framing)

        val main = SceneClassifier.mainPerson(input)
        val face = SceneClassifier.mainFace(input, main)

        // --- A level horizon ---
        val roll = abs(input.tilt.rollDeg)
        if (roll > 8f) {
            issues.add(HORIZON_TILTED)
            score -= min(25f, roll * 1.8f).toInt()
        } else if (roll > 3f) {
            issues.add(HORIZON_SLIGHTLY_TILTED)
            score -= (roll * 1.4f).toInt()
        }

        if (main != null) {
            val box = main.box

            // --- Is the subject big enough to be the subject? ---
            if ((scene == SceneType.PORTRAIT || scene == SceneType.CLOSEUP) && box.area < 0.10f) {
                issues.add(SUBJECT_TOO_SMALL)
                score -= 22
            } else if (scene == SceneType.FULL_BODY && box.height < 0.45f) {
                issues.add(SUBJECT_TOO_SMALL)
                score -= 16
            }

            // --- Headroom: the most common amateur mistake ---
            var headTop = box.top
            if (face != null) headTop = min(headTop, face.box.top)
            val headroomMatters = framing == BodyFraming.CLOSEUP ||
                framing == BodyFraming.HALF ||
                scene == SceneType.PORTRAIT ||
                scene == SceneType.CLOSEUP
            if (headroomMatters) {
                if (headTop > HEADROOM_LIMIT) {
                    issues.add(TOO_MUCH_HEADROOM)
                    score -= min(24f, (headTop - HEADROOM_LIMIT) * 110f).toInt()
                } else if (headTop < 0.015f) {
                    issues.add(HEAD_CROPPED)
                    score -= 20
                }
            }

            // --- Clean crops: nothing important touching the frame edge ---
            if (framing == BodyFraming.FULL) {
                val touching = box.left <= EDGE_MARGIN ||
                    box.right >= 1f - EDGE_MARGIN ||
                    box.bottom >= 1f - EDGE_MARGIN
                if (touching) {
                    issues.add(LIMBS_CROPPED)
                    score -= 18
                }
            }

            // --- Don't crop exactly at a joint ---
            if (framing == BodyFraming.THREE_QUARTER &&
                main.has(Landmark.LEFT_KNEE, minConfidence = 0.4f)
            ) {
                val kneeY = main.landmarks[Landmark.LEFT_KNEE]!!.y
                if (box.bottom - kneeY < 0.04f) {
                    issues.add(CROPPED_AT_JOINT)
                    score -= 12
                }
            }

            // --- Placement against the ideal point ---
            val placementError = hypot(
                (box.centerX - targetX).toDouble(),
                (box.centerY - targetY).toDouble(),
            ).toFloat()
            if (placementError > 0.12f) {
                issues.add(SUBJECT_OFF_TARGET)
                score -= min(20f, placementError * 95f).toInt()
            }

            // --- Dead centre is dull for a single portrait ---
            if ((scene == SceneType.PORTRAIT || scene == SceneType.CLOSEUP) &&
                abs(box.centerX - 0.5f) < 0.04f
            ) {
                issues.add(DEAD_CENTRE)
                score -= 8
            }

            // --- Looking room: a subject should look into the frame ---
            if (face != null && abs(face.eulerY) > 18f) {
                val lookingRight = face.eulerY < 0f
                val spaceRight = 1f - box.right
                val spaceLeft = box.left
                if (lookingRight && spaceRight < spaceLeft - 0.08f) {
                    issues.add(NO_LOOKING_ROOM)
                    score -= 12
                    targetX = min(0.45f, box.centerX - 0.12f)
                } else if (!lookingRight && spaceLeft < spaceRight - 0.08f) {
                    issues.add(NO_LOOKING_ROOM)
                    score -= 12
                    targetX = kotlin.math.max(0.55f, box.centerX + 0.12f)
                }
            }
        } else {
            // No people in frame.
            if (scene == SceneType.LANDSCAPE) {
                val grid = input.luma.grid
                val third = (grid / 3).coerceAtLeast(1)
                val top = Regions.mean(input.luma, Regions.rowIndices(grid, 0 until third))
                val mid = Regions.mean(input.luma, Regions.rowIndices(grid, third until (grid - third)))
                val bottom = Regions.mean(input.luma, Regions.rowIndices(grid, (grid - third) until grid))
                if (abs(top - mid) < abs(mid - bottom) && top - bottom > 55f) {
                    issues.add(HORIZON_CENTRED)
                    score -= 10
                }
            }
            if (scene == SceneType.DOCUMENT && roll > 2f) {
                issues.add(DOCUMENT_NOT_SQUARE)
                score -= 15
            }
        }

        return CompositionAssessment(
            score = score.coerceIn(0, 100),
            targetCenterX = targetX,
            targetCenterY = targetY,
            issues = issues,
        )
    }
}
