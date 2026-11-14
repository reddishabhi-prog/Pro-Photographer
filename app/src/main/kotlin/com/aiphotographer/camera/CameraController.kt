package com.aiphotographer.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
import android.util.Log
import android.view.OrientationEventListener
import android.view.Surface
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.extensions.ExtensionMode
import androidx.camera.extensions.ExtensionsManager
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.lifecycle.LifecycleOwner
import com.aiphotographer.ManualSettings
import com.aiphotographer.intelligence.CameraState
import com.aiphotographer.intelligence.SettingsRecommendation
import com.aiphotographer.intelligence.WhiteBalanceMode
import com.aiphotographer.intelligence.camera.CameraCapabilities
import com.aiphotographer.intelligence.camera.ManualRanges
import com.google.common.util.concurrent.ListenableFuture
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/** A photograph straight off the sensor, before any enhancement. */
data class CapturedPhoto(
    val bitmap: Bitmap,
    val cameraState: CameraState?,
)

/**
 * Owns the camera.
 *
 * Two things here are worth knowing. First, exposure feedback: a Camera2
 * capture callback is attached to the preview, so every frame tells us the ISO
 * and shutter speed auto-exposure actually chose. That is what lets the engine
 * work out the true brightness of the scene rather than guessing from pixels.
 * Second, manual control goes through Camera2CameraControl, which applies
 * settings to the running session instead of tearing the camera down and
 * rebuilding it every time the user nudges a dial.
 */
@OptIn(ExperimentalCamera2Interop::class)
class CameraController(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val detectors: MlKitDetectors,
    private val tiltSensor: TiltSensor,
    private val analysisExecutor: Executor,
    private val mainExecutor: Executor,
    private val poseVariantProvider: () -> Int,
    private val onAnalysis: (AnalysisResult) -> Unit,
) {

    private var cameraProvider: ProcessCameraProvider? = null
    private var extensionsManager: ExtensionsManager? = null
    private var camera: Camera? = null

    private var preview: Preview? = null
    private var imageAnalysis: ImageAnalysis? = null
    private var imageCapture: ImageCapture? = null

    private val latestCameraState = AtomicReference<CameraState?>(null)
    private val captureExecutor = java.util.concurrent.Executors.newSingleThreadExecutor()
    private var analyzer: FrameAnalyzer? = null

    /** What the current camera can do, read from the hardware once bound. */
    var capabilities: CameraCapabilities = CameraCapabilities.UNKNOWN
        private set

    private var usingFrontCamera = false
    private var hdrEnabled = false

    /**
     * The device's physical orientation, so a photograph taken with the phone
     * on its side is saved the right way up even though the UI stays portrait.
     */
    private var deviceRotation = Surface.ROTATION_0
    private val orientationListener = object : OrientationEventListener(context) {
        override fun onOrientationChanged(orientation: Int) {
            if (orientation == ORIENTATION_UNKNOWN) return
            val rotation = when {
                orientation >= 315 || orientation < 45 -> Surface.ROTATION_0
                orientation < 135 -> Surface.ROTATION_270
                orientation < 225 -> Surface.ROTATION_180
                else -> Surface.ROTATION_90
            }
            if (rotation != deviceRotation) {
                deviceRotation = rotation
                imageCapture?.targetRotation = rotation
            }
        }
    }

    /** Reads what auto-exposure settled on, frame by frame. */
    private val sessionCaptureCallback = object : CameraCaptureSession.CaptureCallback() {
        override fun onCaptureCompleted(
            session: CameraCaptureSession,
            request: CaptureRequest,
            result: TotalCaptureResult,
        ) {
            val iso = result.get(CaptureResult.SENSOR_SENSITIVITY)
            val exposureTime = result.get(CaptureResult.SENSOR_EXPOSURE_TIME)
            if (iso == null || exposureTime == null) return

            latestCameraState.set(
                CameraState(
                    iso = iso,
                    exposureTimeNs = exposureTime,
                    // Most phone lenses have a fixed aperture and do not
                    // report one; f/1.8 is the usual main-camera value.
                    aperture = result.get(CaptureResult.LENS_APERTURE) ?: 1.8f,
                    focalLengthMm = result.get(CaptureResult.LENS_FOCAL_LENGTH) ?: 5f,
                    isFrontFacing = usingFrontCamera,
                )
            )
        }
    }

    fun latestCameraState(): CameraState? = latestCameraState.get()

    // -----------------------------------------------------------------
    // Binding
    // -----------------------------------------------------------------

    suspend fun bind(
        previewView: PreviewView,
        frontCamera: Boolean = usingFrontCamera,
        hdr: Boolean = hdrEnabled,
    ): Result<CameraCapabilities> = runCatching {
        usingFrontCamera = frontCamera
        hdrEnabled = hdr

        val provider = cameraProvider
            ?: ProcessCameraProvider.getInstance(context).await(mainExecutor)
                .also { cameraProvider = it }

        val manager = extensionsManager
            ?: runCatching {
                ExtensionsManager.getInstanceAsync(context, provider).await(mainExecutor)
            }.getOrNull()?.also { extensionsManager = it }

        provider.unbindAll()

        val baseSelector =
            if (frontCamera) CameraSelector.DEFAULT_FRONT_CAMERA
            else CameraSelector.DEFAULT_BACK_CAMERA

        val hdrAvailable = manager
            ?.isExtensionAvailable(baseSelector, ExtensionMode.HDR) == true

        val selector = if (hdr && hdrAvailable && manager != null) {
            manager.getExtensionEnabledCameraSelector(baseSelector, ExtensionMode.HDR)
        } else {
            baseSelector
        }

        // --- Preview, with the capture callback attached ---
        val previewBuilder = Preview.Builder()
        Camera2Interop.Extender(previewBuilder)
            .setSessionCaptureCallback(sessionCaptureCallback)
        val newPreview = previewBuilder.build().also {
            it.setSurfaceProvider(previewView.surfaceProvider)
        }

        // --- Analysis, kept small and current ---
        val analysisResolution = ResolutionSelector.Builder()
            .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
            .setResolutionStrategy(
                ResolutionStrategy(
                    ANALYSIS_SIZE,
                    ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER,
                )
            )
            .build()

        val frameAnalyzer = FrameAnalyzer(
            detectors = detectors,
            tiltSensor = tiltSensor,
            cameraStateProvider = { latestCameraState.get() },
            poseVariantProvider = poseVariantProvider,
            onResult = onAnalysis,
        )
        analyzer = frameAnalyzer

        val newAnalysis = ImageAnalysis.Builder()
            .setResolutionSelector(analysisResolution)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
            .build()
            .also { it.setAnalyzer(analysisExecutor, frameAnalyzer) }

        // --- Stills ---
        // Capped near 12 MP. Left alone, CameraX picks the sensor's full size,
        // which on 50-200 MP phones is a 200-800 MB bitmap: decoding it, then
        // enhancing a copy, runs out of memory and the photo is lost. 12 MP is
        // what the phones' own camera apps save by default.
        val stillResolution = ResolutionSelector.Builder()
            .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
            .setResolutionStrategy(
                ResolutionStrategy(
                    STILL_SIZE,
                    ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER,
                )
            )
            .build()
        val newCapture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .setResolutionSelector(stillResolution)
            .setTargetRotation(deviceRotation)
            .build()

        val bound = provider.bindToLifecycle(
            lifecycleOwner, selector, newPreview, newAnalysis, newCapture,
        )

        preview = newPreview
        imageAnalysis = newAnalysis
        imageCapture = newCapture
        camera = bound

        capabilities = CameraCapabilitiesReader.read(bound.cameraInfo, hdrAvailable)
        orientationListener.enable()

        capabilities
    }.onFailure { error ->
        Log.e(TAG, "Failed to bind camera", error)
    }

    fun unbind() {
        orientationListener.disable()
        captureExecutor.shutdown()
        imageAnalysis?.clearAnalyzer()
        cameraProvider?.unbindAll()
        camera = null
        analyzer = null
    }

    fun resetAnalysis() {
        analyzer?.reset()
        latestCameraState.set(null)
    }

    // -----------------------------------------------------------------
    // Controls
    // -----------------------------------------------------------------

    fun setZoom(slider: Float) {
        val ratio = ManualRanges.zoomRatio(slider, capabilities)
        camera?.cameraControl?.setZoomRatio(ratio)
    }

    /**
     * Exposure compensation, which only has meaning while auto-exposure is
     * running - with AE off, the ISO and shutter are the exposure.
     */
    fun setExposureCompensation(stops: Float) {
        val index = ManualRanges.evCompensationIndex(stops, capabilities)
        runCatching { camera?.cameraControl?.setExposureCompensationIndex(index) }
    }

    fun focusAt(previewView: PreviewView, x: Float, y: Float) {
        val control = camera?.cameraControl ?: return
        val point = previewView.meteringPointFactory.createPoint(x, y)
        val action = FocusMeteringAction.Builder(point).build()
        runCatching { control.startFocusAndMetering(action) }
    }

    fun setFlashMode(mode: Int) {
        imageCapture?.flashMode = mode
    }

    /**
     * Push the user's manual choices to the running session.
     *
     * Camera2 requires that turning auto-exposure off supplies BOTH the ISO
     * and the shutter speed - leaving one unset is undefined behaviour that
     * looks like a broken slider. So when the user locks only one, the other
     * is filled in from the engine's recommendation.
     */
    fun applyManualSettings(
        manual: ManualSettings,
        recommendation: SettingsRecommendation?,
    ) {
        val control = camera?.cameraControl ?: return
        val camera2Control = Camera2CameraControl.from(control)
        val options = CaptureRequestOptions.Builder()

        val wantsManualExposure =
            (!manual.isoAuto && capabilities.supportsManualIso) ||
                (!manual.shutterAuto && capabilities.supportsManualShutter)

        if (wantsManualExposure) {
            val fallback = latestCameraState.get()

            val iso = when {
                !manual.isoAuto -> manual.iso
                recommendation != null -> recommendation.iso
                fallback != null -> fallback.iso
                else -> capabilities.isoMin
            }
            val shutter = when {
                !manual.shutterAuto -> manual.shutterDenominator
                recommendation != null -> recommendation.shutterDenominator
                fallback != null -> ManualRanges.shutterDenominator(fallback.exposureTimeNs)
                else -> 60
            }

            options.setCaptureRequestOption(
                CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_OFF,
            )
            options.setCaptureRequestOption(
                CaptureRequest.SENSOR_SENSITIVITY, ManualRanges.clampIso(iso, capabilities),
            )
            options.setCaptureRequestOption(
                CaptureRequest.SENSOR_EXPOSURE_TIME,
                ManualRanges.exposureTimeNs(shutter, capabilities),
            )
        } else {
            options.setCaptureRequestOption(
                CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_ON,
            )
        }

        if (!manual.focusAuto && capabilities.supportsManualFocus) {
            options.setCaptureRequestOption(
                CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_OFF,
            )
            options.setCaptureRequestOption(
                CaptureRequest.LENS_FOCUS_DISTANCE,
                ManualRanges.focusDiopters(manual.focusSlider, capabilities),
            )
        } else {
            options.setCaptureRequestOption(
                CaptureRequest.CONTROL_AF_MODE,
                CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_PICTURE,
            )
        }

        if (!manual.whiteBalanceAuto && capabilities.supportsManualWhiteBalance) {
            options.setCaptureRequestOption(
                CaptureRequest.CONTROL_AWB_MODE, manual.whiteBalance.toCamera2Mode(),
            )
        } else {
            options.setCaptureRequestOption(
                CaptureRequest.CONTROL_AWB_MODE, CameraMetadata.CONTROL_AWB_MODE_AUTO,
            )
        }

        runCatching { camera2Control.setCaptureRequestOptions(options.build()) }
            .onFailure { Log.w(TAG, "Could not apply manual settings", it) }

        // Exposure compensation is an auto-exposure concept only.
        if (!wantsManualExposure) setExposureCompensation(manual.evCompensation)
    }

    /**
     * Apply what the engine recommends, without overriding anything the user
     * has locked. This is the line between AUTO and PRO: advice in Pro mode
     * never silently moves a control the user has taken charge of.
     */
    fun applyRecommendation(
        recommendation: SettingsRecommendation,
        manual: ManualSettings,
        isProMode: Boolean,
    ) {
        if (isProMode && !manual.isFullyAutomatic) {
            applyManualSettings(manual, recommendation)
            return
        }

        if (isProMode) {
            // Pro mode with nothing locked: still the user's exposure choice.
            setExposureCompensation(manual.evCompensation)
            return
        }

        // AUTO mode: the engine drives.
        setExposureCompensation(recommendation.evCompensation)
    }

    // -----------------------------------------------------------------
    // Capture
    // -----------------------------------------------------------------

    fun capture(onResult: (Result<CapturedPhoto>) -> Unit) {
        val capture = imageCapture ?: run {
            onResult(Result.failure(IllegalStateException("Camera is not ready")))
            return
        }
        val stateAtCapture = latestCameraState.get()

        // Not the analysis thread: that is kept busy by the vision models, and
        // a shutter press queued behind them felt like the button did nothing.
        capture.takePicture(
            captureExecutor,
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    try {
                        val bitmap = image.toUprightBitmap(usingFrontCamera)
                        if (bitmap == null) {
                            onResult(
                                Result.failure(IllegalStateException("Could not decode the photo"))
                            )
                        } else {
                            onResult(Result.success(CapturedPhoto(bitmap, stateAtCapture)))
                        }
                    } catch (e: OutOfMemoryError) {
                        onResult(Result.failure(RuntimeException("Not enough memory for the photo", e)))
                    } catch (e: Exception) {
                        onResult(Result.failure(e))
                    } finally {
                        image.close()
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e(TAG, "Capture failed", exception)
                    onResult(Result.failure(exception))
                }
            },
        )
    }

    /**
     * Decode a captured JPEG and rotate it upright.
     *
     * The front camera's preview is mirrored for the user's benefit, but a
     * saved selfie should not be: text in the background would read backwards.
     */
    private fun ImageProxy.toUprightBitmap(front: Boolean): Bitmap? {
        val buffer = planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null

        val rotation = imageInfo.rotationDegrees
        if (rotation == 0 && !front) return decoded

        val matrix = Matrix()
        if (rotation != 0) matrix.postRotate(rotation.toFloat())

        val rotated = Bitmap.createBitmap(
            decoded, 0, 0, decoded.width, decoded.height, matrix, true,
        )
        if (rotated != decoded) decoded.recycle()
        return rotated
    }

    companion object {
        private const val TAG = "CameraController"

        /**
         * Analysis resolution. Small on purpose: the vision models downscale
         * anyway, and every extra pixel is spent on every frame.
         */
        private val ANALYSIS_SIZE = android.util.Size(640, 480)

        /** 4:3 at about 12 MP, the size every phone camera saves by default. */
        private val STILL_SIZE = android.util.Size(4000, 3000)
    }
}

/** Map our white balance names onto Camera2's constants. */
private fun WhiteBalanceMode.toCamera2Mode(): Int = when (this) {
    WhiteBalanceMode.AUTO -> CameraMetadata.CONTROL_AWB_MODE_AUTO
    WhiteBalanceMode.DAYLIGHT -> CameraMetadata.CONTROL_AWB_MODE_DAYLIGHT
    WhiteBalanceMode.CLOUDY -> CameraMetadata.CONTROL_AWB_MODE_CLOUDY_DAYLIGHT
    WhiteBalanceMode.SHADE -> CameraMetadata.CONTROL_AWB_MODE_SHADE
    WhiteBalanceMode.INCANDESCENT -> CameraMetadata.CONTROL_AWB_MODE_INCANDESCENT
    WhiteBalanceMode.FLUORESCENT -> CameraMetadata.CONTROL_AWB_MODE_FLUORESCENT
}

/** Await a CameraX future without pulling in a Guava coroutine dependency. */
private suspend fun <T> ListenableFuture<T>.await(executor: Executor): T =
    suspendCancellableCoroutine { continuation ->
        addListener(
            {
                try {
                    continuation.resume(get())
                } catch (e: Exception) {
                    continuation.resumeWithException(e)
                }
            },
            executor,
        )
        continuation.invokeOnCancellation { cancel(true) }
    }
