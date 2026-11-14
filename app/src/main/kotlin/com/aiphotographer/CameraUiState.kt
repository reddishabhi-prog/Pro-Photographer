package com.aiphotographer

import android.net.Uri
import com.aiphotographer.intelligence.Guidance
import com.aiphotographer.intelligence.Landmark
import com.aiphotographer.intelligence.NormPoint
import com.aiphotographer.intelligence.WhiteBalanceMode
import com.aiphotographer.intelligence.camera.CameraCapabilities

/** AUTO lets the app decide; PRO hands the controls to the user. */
enum class ShootingMode { AUTO, PRO }

/**
 * The user's manual choices.
 *
 * Each control has its own auto flag rather than one global switch, so the
 * user can lock the shutter speed while still letting the app pick the ISO -
 * which is how people actually shoot.
 */
data class ManualSettings(
    val isoAuto: Boolean = true,
    val iso: Int = 100,
    val shutterAuto: Boolean = true,
    val shutterDenominator: Int = 125,
    val focusAuto: Boolean = true,
    val focusSlider: Float = 0f,
    val whiteBalanceAuto: Boolean = true,
    val whiteBalance: WhiteBalanceMode = WhiteBalanceMode.AUTO,
    val evCompensation: Float = 0f,
    val zoomSlider: Float = 0f,
    val hdrRequested: Boolean = false,
    val rawRequested: Boolean = false,
) {
    /** True when nothing is locked, so AUTO can drive everything. */
    val isFullyAutomatic: Boolean
        get() = isoAuto && shutterAuto && focusAuto && whiteBalanceAuto
}

/** What the capture pipeline is currently doing, for the shutter button. */
enum class CaptureStage { IDLE, CAPTURING, PROCESSING }

/** Everything the viewfinder needs to draw itself. */
data class CameraUiState(
    val mode: ShootingMode = ShootingMode.AUTO,
    val guidance: Guidance? = null,
    val capabilities: CameraCapabilities = CameraCapabilities.UNKNOWN,
    val manual: ManualSettings = ManualSettings(),
    val poseVariant: Int = 0,
    val showPoseGuide: Boolean = Features.POSE_GUIDE,
    val showGrid: Boolean = Features.GRID,
    val showLevel: Boolean = true,
    val captureStage: CaptureStage = CaptureStage.IDLE,
    val usingFrontCamera: Boolean = false,
    val lastPhotoUri: Uri? = null,
    /** What the enhancement actually did, in plain words. */
    val lastEnhancementSummary: String? = null,
    /** Set when the hardware cannot honour a recommendation. */
    val hardwareNote: String? = null,
    /** A transient message for the user, e.g. an error. */
    val message: String? = null,
    val cameraReady: Boolean = false,
    /** The tracked person's live landmarks, for drawing their actual pose. */
    val livePose: Map<Landmark, NormPoint> = emptyMap(),
    /** Frame width / height, so overlays match the preview's shape. */
    val frameAspect: Float = 3f / 4f,
    /** Phone roll in degrees, clockwise positive, as the engine judged it. */
    val rollDeg: Float = 0f,
    /** Bumped on every successful shutter press, to flash the screen. */
    val shotCount: Int = 0,
) {
    val isPro: Boolean get() = mode == ShootingMode.PRO
}
