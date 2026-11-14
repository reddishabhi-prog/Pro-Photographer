package com.aiphotographer.intelligence

/**
 * Inputs to the photography intelligence engine.
 *
 * This module has NO Android dependencies on purpose: every decision the app
 * makes about a photograph is plain Kotlin, so it can be unit-tested on a JVM
 * without a phone, an emulator or a camera.
 */

/** Number of rows/columns the frame's brightness is sampled into. */
const val LUMA_GRID: Int = 6

/** A bounding box in normalised frame coordinates: 0..1, origin top-left. */
data class Box(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    val width: Float get() = (right - left).coerceAtLeast(0f)
    val height: Float get() = (bottom - top).coerceAtLeast(0f)
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f
    val area: Float get() = width * height

    fun contains(x: Float, y: Float): Boolean =
        x in left..right && y in top..bottom
}

/** A point in normalised frame coordinates. */
data class NormPoint(val x: Float, val y: Float)

/** Pose landmarks we make decisions from. Mirrors a subset of ML Kit's set. */
enum class Landmark {
    NOSE,
    LEFT_EYE, RIGHT_EYE,
    LEFT_SHOULDER, RIGHT_SHOULDER,
    LEFT_ELBOW, RIGHT_ELBOW,
    LEFT_WRIST, RIGHT_WRIST,
    LEFT_HIP, RIGHT_HIP,
    LEFT_KNEE, RIGHT_KNEE,
    LEFT_ANKLE, RIGHT_ANKLE,
}

/** A detected person, with whatever pose landmarks were resolved. */
data class Person(
    val box: Box,
    val confidence: Float,
    val landmarks: Map<Landmark, NormPoint> = emptyMap(),
    val landmarkConfidence: Map<Landmark, Float> = emptyMap(),
) {
    /** True when every named landmark is present and confident enough to trust. */
    fun has(vararg names: Landmark, minConfidence: Float = 0.5f): Boolean =
        names.all { name ->
            landmarks.containsKey(name) &&
                (landmarkConfidence[name] ?: 0f) >= minConfidence
        }

    /** True when any of the named landmarks is present and confident enough. */
    fun hasAny(vararg names: Landmark, minConfidence: Float = 0.5f): Boolean =
        names.any { name ->
            landmarks.containsKey(name) &&
                (landmarkConfidence[name] ?: 0f) >= minConfidence
        }
}

/**
 * A detected face.
 *
 * [eulerY] is head turn: negative means the subject is turned to their
 * right, which is the viewer's right in a rear-camera frame. [eulerZ] is
 * head tilt. Probabilities are -1 when the detector did not report them.
 */
data class Face(
    val box: Box,
    val eulerY: Float = 0f,
    val eulerZ: Float = 0f,
    val smilingProbability: Float = -1f,
    val leftEyeOpen: Float = -1f,
    val rightEyeOpen: Float = -1f,
)

/** An on-device image label, e.g. "food" at 0.84 confidence. */
data class SceneLabel(val text: String, val confidence: Float)

/**
 * Brightness statistics for the current analysis frame.
 *
 * [regions] holds a [grid] x [grid] map of regional means in row-major order,
 * row 0 being the top of the frame. A 6x6 map is coarse enough to compute on
 * every frame and fine enough to separate a subject from its background.
 */
data class LumaStats(
    val mean: Float,
    val p5: Float,
    val p95: Float,
    val clippedShadows: Float,
    val clippedHighlights: Float,
    val regions: List<Float> = List(LUMA_GRID * LUMA_GRID) { 128f },
    val grid: Int = LUMA_GRID,
) {
    val contrast: Float get() = p95 - p5

    fun cell(row: Int, col: Int): Float {
        val index = row * grid + col
        return regions.getOrElse(index) { mean }
    }
}

/** Average channel values across the frame, used for white balance decisions. */
data class ColorStats(
    val r: Float,
    val g: Float,
    val b: Float,
) {
    /**
     * Positive means a warm (red-heavy) scene, negative means cool.
     * Roughly -1..1 in practice.
     */
    val warmth: Float
        get() {
            val total = r + g + b
            return if (total <= 0f) 0f else (r - b) / total * 3f
        }
}

/**
 * What the camera's own auto-exposure settled on this frame.
 *
 * These four values let us derive the scene's absolute light level, which is
 * far more reliable than reading pixel brightness - auto-exposure has already
 * normalised that away.
 */
data class CameraState(
    val iso: Int,
    val exposureTimeNs: Long,
    val aperture: Float,
    val focalLengthMm: Float,
    val isFrontFacing: Boolean = false,
)

/**
 * Device orientation from the accelerometer.
 *
 * [rollDeg] is horizon tilt: positive means the phone is rotated clockwise, so
 * the correction is to tilt it back to the left. Zero is level.
 *
 * [pitchDeg] is where the camera is pointing relative to the horizon:
 * positive is tilted up (shooting up at a building), negative is tilted down,
 * and -90 is pointing straight down at a table for a flat-lay.
 */
data class DeviceTilt(
    val rollDeg: Float = 0f,
    val pitchDeg: Float = 0f,
)

/** Everything the engine knows about one moment in the viewfinder. */
data class FrameInput(
    val people: List<Person> = emptyList(),
    val faces: List<Face> = emptyList(),
    val labels: List<SceneLabel> = emptyList(),
    val luma: LumaStats,
    val color: ColorStats,
    val camera: CameraState,
    val tilt: DeviceTilt = DeviceTilt(),
    /** 0..1 estimate of how fast the subject is moving between frames. */
    val subjectMotion: Float = 0f,
    /** What the user said they like to shoot; a prior for unclear scenes. */
    val interests: Set<ShootingInterest> = emptySet(),
)

/** How much of a person is inside the frame. */
enum class BodyFraming {
    CLOSEUP,          // head, or head and shoulders
    HALF,             // down to the waist
    THREE_QUARTER,    // down to the knees
    FULL,             // the whole body
    UNKNOWN,
}
