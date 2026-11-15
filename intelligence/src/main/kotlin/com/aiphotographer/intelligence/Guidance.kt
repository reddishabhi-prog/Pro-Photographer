package com.aiphotographer.intelligence

/** Outputs of the photography intelligence engine. */

enum class SceneType {
    PORTRAIT,
    GROUP,
    FULL_BODY,
    CLOSEUP,
    LANDSCAPE,
    ARCHITECTURE,
    FOOD,
    PET,
    OBJECT,
    NIGHT,
    DOCUMENT,
    UNKNOWN,
}

enum class LightingType {
    GOOD,
    BACKLIT,
    HARSH,
    FLAT,
    LOW_LIGHT,
    MIXED,
    GOLDEN,
}

enum class LightDirection {
    FRONT, LEFT, RIGHT, BEHIND, ABOVE, UNKNOWN,
}

enum class ShootingAngle {
    EYE_LEVEL,
    LOW,
    HIGH,
    SLIGHTLY_HIGH,
    SLIGHTLY_LOW,
}

enum class WhiteBalanceMode {
    AUTO, DAYLIGHT, CLOUDY, SHADE, INCANDESCENT, FLUORESCENT,
}

/** How badly the user needs to act on a hint. */
enum class HintPriority(val weight: Int) {
    POLISH(1),     // nice to have
    IMPORTANT(2),  // clear improvement
    CRITICAL(3),   // the shot is spoiled without this
}

/**
 * A single piece of coaching shown in the viewfinder.
 * [code] is a stable identifier the UI maps to an icon and tests assert on.
 */
data class Hint(
    val priority: HintPriority,
    val code: String,
    val text: String,
)

data class LightingAssessment(
    val type: LightingType,
    val direction: LightDirection,
    val score: Int,
    val evCompensation: Float,
)

data class CompositionAssessment(
    val score: Int,
    /** Where the main subject's centre should move to, normalised. */
    val targetCenterX: Float,
    val targetCenterY: Float,
    val issues: List<String> = emptyList(),
)

data class SettingsRecommendation(
    val iso: Int,
    /** Denominator of the shutter speed: 125 means 1/125 s. */
    val shutterDenominator: Int,
    val evCompensation: Float,
    val whiteBalance: WhiteBalanceMode,
    val useHdr: Boolean,
    val aspectRatio: String,
    val reason: String,
)

/** The engine's complete read on the current frame. */
data class Guidance(
    val scene: SceneType,
    val framing: BodyFraming,
    val lighting: LightingAssessment,
    val composition: CompositionAssessment,
    val angle: ShootingAngle,
    val settings: SettingsRecommendation,
    val poseId: String?,
    val hints: List<Hint>,
    val overallScore: Int,
    val readyToShoot: Boolean,
)
