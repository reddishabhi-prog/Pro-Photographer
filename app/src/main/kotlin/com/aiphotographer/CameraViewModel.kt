package com.aiphotographer

import android.app.Application
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.viewModelScope
import com.aiphotographer.camera.AnalysisResult
import com.aiphotographer.camera.CameraController
import com.aiphotographer.camera.MlKitDetectors
import com.aiphotographer.camera.TiltSensor
import com.aiphotographer.capture.PhotoProcessor
import com.aiphotographer.intelligence.Box
import com.aiphotographer.intelligence.Guidance
import com.aiphotographer.intelligence.AutoActions
import com.aiphotographer.intelligence.GuidanceStabilizer
import com.aiphotographer.intelligence.PoseLibrary
import com.aiphotographer.intelligence.ShootingInterest
import com.aiphotographer.intelligence.camera.ManualRanges
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Holds the camera and the viewfinder's state.
 *
 * The division of labour matters here: the engine decides what is true about
 * the scene, this class decides what the app does about it, and the camera
 * controller does it. In particular this is where AUTO and PRO diverge - the
 * engine's recommendations are applied to the hardware in AUTO, and in PRO
 * they are shown as advice but never allowed to move a control the user has
 * taken charge of.
 */
class CameraViewModel(application: Application) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(CameraUiState())
    val state: StateFlow<CameraUiState> = _state.asStateFlow()

    /** The Application, typed, for the places that need a Context. */
    private val appContext: Application get() = getApplication()

    private val detectors = MlKitDetectors()
    private val tiltSensor = TiltSensor(application)
    private val analysisExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val processor = PhotoProcessor(application)
    private val stabilizer = GuidanceStabilizer()

    private var controller: CameraController? = null

    /**
     * The owner the current controller is tied to.
     *
     * The ViewModel outlives the Activity, so after a recreation the old
     * controller holds a dead LifecycleOwner and the camera silently never
     * starts. Tracking the owner lets us notice and rebuild.
     */
    private var boundLifecycleOwner: LifecycleOwner? = null

    /** The latest detections, kept for the capture pipeline. */
    private var lastGuidance: Guidance? = null
    private var lastFaces: List<Box> = emptyList()

    /** What the user said they shoot; read by the analysis thread each frame. */
    @Volatile private var interests: Set<ShootingInterest> = emptySet()

    fun setInterests(picks: List<ShootingInterest>, welcome: Boolean) {
        interests = picks.toSet()
        if (welcome) {
            ShootingInterest.welcomeTip(picks)?.let { tip -> _state.update { it.copy(welcomeTip = tip) } }
        }
    }

    /** Settings are pushed to the hardware on a timer, not every frame. */
    private var lastSettingsAppliedAt = 0L

    // -----------------------------------------------------------------
    // Lifecycle
    // -----------------------------------------------------------------

    fun bindCamera(lifecycleOwner: LifecycleOwner, previewView: PreviewView) {
        tiltSensor.start()

        // A new Activity means a new lifecycle to bind to.
        if (controller != null && boundLifecycleOwner !== lifecycleOwner) {
            controller?.unbind()
            controller = null
            stabilizer.reset()
        }

        val cameraController = controller ?: CameraController(
            context = appContext,
            lifecycleOwner = lifecycleOwner,
            detectors = detectors,
            tiltSensor = tiltSensor,
            analysisExecutor = analysisExecutor,
            poseVariantProvider = { _state.value.poseVariant },
            interestsProvider = { interests },
            onAnalysis = ::onAnalysis,
            mainExecutor = ContextCompat.getMainExecutor(appContext),
        ).also {
            controller = it
            boundLifecycleOwner = lifecycleOwner
        }

        viewModelScope.launch {
            val result = cameraController.bind(
                previewView = previewView,
                frontCamera = _state.value.usingFrontCamera,
                hdr = _state.value.manual.hdrRequested,
            )
            result.fold(
                onSuccess = { capabilities ->
                    _state.update {
                        it.copy(
                            capabilities = capabilities,
                            cameraReady = true,
                            message = null,
                        )
                    }
                },
                onFailure = { error ->
                    _state.update {
                        it.copy(
                            cameraReady = false,
                            message = "Could not start the camera: ${error.message}",
                        )
                    }
                },
            )
        }
    }

    fun onPause() {
        tiltSensor.stop()
    }

    fun onResume() {
        tiltSensor.start()
    }

    override fun onCleared() {
        controller?.unbind()
        detectors.close()
        tiltSensor.stop()
        analysisExecutor.shutdown()
        super.onCleared()
    }

    // -----------------------------------------------------------------
    // Analysis
    // -----------------------------------------------------------------

    /** Called on the analysis thread, once per analysed frame. */
    private fun onAnalysis(result: AnalysisResult) {
        val steady = stabilizer.update(result.guidance)
        lastGuidance = steady
        lastFaces = result.faceBoxes

        val current = _state.value
        val shortfall = ManualRanges.describeShortfall(
            requestedIso = steady.settings.iso,
            requestedShutter = steady.settings.shutterDenominator,
            caps = current.capabilities,
        )

        _state.update {
            it.copy(
                guidance = steady,
                livePose = result.livePose,
                frameAspect = result.frameAspect,
                hardwareNote = shortfall,
                rollDeg = result.rollDeg,
                liveCamera = result.cameraState ?: it.liveCamera,
                appliedEv = if (it.isPro) it.manual.evCompensation else AutoActions.autoEv(steady),
                captureMode = controller?.captureMode,
                autoActions = AutoActions.describe(steady, controller?.captureMode),
            )
        }

        // Push settings to the camera on a timer. Every frame would fight the
        // camera's own convergence and drain the battery for nothing.
        val now = System.currentTimeMillis()
        if (now - lastSettingsAppliedAt >= SETTINGS_INTERVAL_MS) {
            lastSettingsAppliedAt = now
            controller?.applyRecommendation(
                recommendation = steady.settings,
                manual = current.manual,
                isProMode = current.isPro,
            )
        }
    }

    // -----------------------------------------------------------------
    // Controls
    // -----------------------------------------------------------------

    fun setMode(mode: ShootingMode) {
        _state.update { it.copy(mode = mode) }
        // Switching back to AUTO hands every control back to the engine.
        if (mode == ShootingMode.AUTO) {
            _state.update { it.copy(manual = ManualSettings()) }
        }
        pushManualSettings()
    }

    fun switchCamera(previewView: PreviewView) {
        val front = !_state.value.usingFrontCamera
        _state.update { it.copy(usingFrontCamera = front, cameraReady = false) }
        stabilizer.reset()
        controller?.resetAnalysis()
        viewModelScope.launch {
            controller?.bind(previewView, frontCamera = front)?.fold(
                onSuccess = { caps ->
                    _state.update { it.copy(capabilities = caps, cameraReady = true) }
                },
                onFailure = { error ->
                    _state.update {
                        it.copy(message = "Could not switch camera: ${error.message}")
                    }
                },
            )
        }
    }

    fun updateManual(transform: (ManualSettings) -> ManualSettings) {
        _state.update { it.copy(manual = transform(it.manual)) }
        pushManualSettings()
    }

    fun setZoom(slider: Float) {
        _state.update { it.copy(manual = it.manual.copy(zoomSlider = slider)) }
        controller?.setZoom(slider)
    }

    fun setExposureCompensation(stops: Float) {
        _state.update { it.copy(manual = it.manual.copy(evCompensation = stops)) }
        controller?.setExposureCompensation(stops)
    }

    fun focusAt(previewView: PreviewView, x: Float, y: Float) {
        controller?.focusAt(previewView, x, y)
    }

    fun cyclePose() {
        val pinned = _state.value.pinnedPoseId
        if (pinned == null) {
            _state.update { it.copy(poseVariant = it.poseVariant + 1) }
        } else {
            // Step through the library in order from the pinned pose.
            val drawable = PoseLibrary.ALL.filter { it.isDrawable }
            val i = drawable.indexOfFirst { it.id == pinned }
            _state.update { it.copy(pinnedPoseId = drawable[(i + 1) % drawable.size].id) }
        }
    }

    fun openPoseLibrary(open: Boolean) = _state.update { it.copy(showPoseLibrary = open) }

    /** Pin a pose from the library, or null to let ShotMuse choose again. */
    fun pinPose(id: String?) = _state.update {
        it.copy(pinnedPoseId = id, showPoseLibrary = false, showPoseGuide = true)
    }

    fun toggleGrid() = _state.update { it.copy(showGrid = !it.showGrid) }
    fun togglePoseGuide() = _state.update { it.copy(showPoseGuide = !it.showPoseGuide) }
    fun toggleLevel() = _state.update { it.copy(showLevel = !it.showLevel) }

    fun toggleHdr(previewView: PreviewView) {
        val wanted = !_state.value.manual.hdrRequested
        if (wanted && !_state.value.capabilities.supportsHdr) {
            _state.update { it.copy(message = "This camera does not offer HDR") }
            return
        }
        _state.update { it.copy(manual = it.manual.copy(hdrRequested = wanted)) }
        // HDR is a camera extension, so it needs a rebind rather than a
        // capture-request tweak.
        viewModelScope.launch {
            controller?.bind(previewView, hdr = wanted)?.onSuccess { caps ->
                _state.update { it.copy(capabilities = caps) }
            }
        }
    }

    fun dismissMessage() = _state.update { it.copy(message = null) }
    fun dismissWelcomeTip() = _state.update { it.copy(welcomeTip = null) }

    private fun pushManualSettings() {
        val current = _state.value
        controller?.applyManualSettings(current.manual, current.guidance?.settings)
    }

    // -----------------------------------------------------------------
    // Capture
    // -----------------------------------------------------------------

    fun capture() {
        val cameraController = controller ?: return
        if (_state.value.captureStage != CaptureStage.IDLE) return

        _state.update { it.copy(captureStage = CaptureStage.CAPTURING) }
        val guidanceAtCapture = lastGuidance
        val facesAtCapture = lastFaces

        cameraController.capture { result ->
            result.fold(
                onSuccess = { photo ->
                    // The flash fires the moment the sensor has the picture,
                    // not after saving, so the press feels instant.
                    _state.update {
                        it.copy(captureStage = CaptureStage.PROCESSING, shotCount = it.shotCount + 1)
                    }
                    viewModelScope.launch {
                        val saved = processor.enhanceAndSave(
                            photo = photo,
                            guidance = guidanceAtCapture,
                            faces = facesAtCapture,
                        )
                        // A 12-megapixel bitmap is ~48 MB; let it go promptly.
                        if (!photo.bitmap.isRecycled) photo.bitmap.recycle()
                        saved.fold(
                            onSuccess = { result ->
                                _state.update {
                                    it.copy(
                                        captureStage = CaptureStage.IDLE,
                                        lastPhotoUri = result.uri,
                                        lastEnhancementSummary = result.summary,
                                    )
                                }
                            },
                            onFailure = { error ->
                                _state.update {
                                    it.copy(
                                        captureStage = CaptureStage.IDLE,
                                        message = "Could not save the photo: ${error.message}",
                                    )
                                }
                            },
                        )
                    }
                },
                onFailure = { error ->
                    _state.update {
                        it.copy(
                            captureStage = CaptureStage.IDLE,
                            message = "Could not take the photo: ${error.message}",
                        )
                    }
                },
            )
        }
    }

    private companion object {
        /** How often manual or recommended settings are pushed to the camera. */
        const val SETTINGS_INTERVAL_MS = 400L
    }
}
