package com.aiphotographer.intelligence

import kotlin.math.max
import kotlin.math.min

/**
 * Builds the engine's view of the people in frame out of what the on-device
 * detectors can actually see.
 *
 * This exists because of a hard limitation: the on-device pose detector finds
 * landmarks for ONE person - the most prominent - while the face detector
 * finds every face. So the main subject gets a real, landmark-derived body
 * box, and everyone else gets a box estimated from their face using human
 * proportions. Keeping that estimation here, in plain Kotlin, means it can be
 * tested rather than guessed at.
 */
object PersonEstimator {

    /** Confidence we report for a body inferred from a face alone. */
    const val ESTIMATED_CONFIDENCE = 0.7f

    /** Confidence we report for a body built from real pose landmarks. */
    const val LANDMARK_CONFIDENCE = 0.95f

    /**
     * A head is roughly one seventh of an adult's standing height, so a face
     * box projects downwards about six more of its own heights. Shoulders sit
     * a little over one face-width out from the centre line on each side.
     */
    private const val BODY_HEIGHTS_BELOW_FACE = 6.0f
    private const val SHOULDER_HALF_WIDTHS = 1.1f
    private const val HAIR_ABOVE_FACE = 0.25f

    /**
     * Estimate a whole-body box from a face box, clipped to the frame.
     *
     * The result is deliberately generous downwards: it is better to think a
     * body continues below the frame edge - which is true - than to treat a
     * head-and-shoulders crop as a full-length portrait.
     */
    fun bodyFromFace(face: Box): Box {
        val faceWidth = face.width.coerceAtLeast(0.001f)
        val faceHeight = face.height.coerceAtLeast(0.001f)
        val centerX = face.centerX

        val left = (centerX - faceWidth * SHOULDER_HALF_WIDTHS).coerceIn(0f, 1f)
        val right = (centerX + faceWidth * SHOULDER_HALF_WIDTHS).coerceIn(0f, 1f)
        val top = (face.top - faceHeight * HAIR_ABOVE_FACE).coerceIn(0f, 1f)
        val bottom = (face.bottom + faceHeight * BODY_HEIGHTS_BELOW_FACE).coerceIn(0f, 1f)

        return Box(
            left = min(left, right),
            top = min(top, bottom),
            right = max(left, right),
            bottom = max(top, bottom),
        )
    }

    /**
     * The bounding box of a set of pose landmarks, with a little padding so
     * the box covers the body rather than just the joint centres.
     */
    fun boxFromLandmarks(
        landmarks: Map<Landmark, NormPoint>,
        confidences: Map<Landmark, Float> = emptyMap(),
        minConfidence: Float = 0.3f,
        padding: Float = 0.02f,
    ): Box? {
        val usable = landmarks.filter { (key, _) ->
            (confidences[key] ?: 1f) >= minConfidence
        }
        if (usable.isEmpty()) return null

        var left = Float.MAX_VALUE
        var top = Float.MAX_VALUE
        var right = -Float.MAX_VALUE
        var bottom = -Float.MAX_VALUE
        for ((_, point) in usable) {
            if (point.x < left) left = point.x
            if (point.x > right) right = point.x
            if (point.y < top) top = point.y
            if (point.y > bottom) bottom = point.y
        }

        return Box(
            left = (left - padding).coerceIn(0f, 1f),
            top = (top - padding).coerceIn(0f, 1f),
            right = (right + padding).coerceIn(0f, 1f),
            bottom = (bottom + padding).coerceIn(0f, 1f),
        )
    }

    /**
     * Assemble the people list.
     *
     * [poseLandmarks] belong to whichever person the pose detector locked on
     * to; we attach them to the face nearest the landmarks' own head, so the
     * main subject is described as precisely as the hardware allows and the
     * rest are estimated.
     */
    fun buildPeople(
        faces: List<Face>,
        poseLandmarks: Map<Landmark, NormPoint> = emptyMap(),
        poseConfidences: Map<Landmark, Float> = emptyMap(),
    ): List<Person> {
        val poseBox = boxFromLandmarks(poseLandmarks, poseConfidences)

        if (faces.isEmpty()) {
            // Pose without a face still happens: someone facing away.
            return if (poseBox != null) {
                listOf(
                    Person(
                        box = poseBox,
                        confidence = LANDMARK_CONFIDENCE,
                        landmarks = poseLandmarks,
                        landmarkConfidence = poseConfidences,
                    )
                )
            } else {
                emptyList()
            }
        }

        // Which face does the pose belong to? The one nearest its head.
        val poseHead = poseLandmarks[Landmark.NOSE]
            ?: poseLandmarks[Landmark.LEFT_EYE]
            ?: poseLandmarks[Landmark.RIGHT_EYE]
        val poseOwner: Face? = if (poseBox == null) null else {
            if (poseHead != null) {
                faces.minByOrNull { face ->
                    val dx = face.box.centerX - poseHead.x
                    val dy = face.box.centerY - poseHead.y
                    dx * dx + dy * dy
                }
            } else {
                faces.maxByOrNull { it.box.area }
            }
        }

        return faces.map { face ->
            if (face === poseOwner && poseBox != null) {
                Person(
                    box = poseBox,
                    confidence = LANDMARK_CONFIDENCE,
                    landmarks = poseLandmarks,
                    landmarkConfidence = poseConfidences,
                )
            } else {
                Person(
                    box = bodyFromFace(face.box),
                    confidence = ESTIMATED_CONFIDENCE,
                )
            }
        }
    }
}
