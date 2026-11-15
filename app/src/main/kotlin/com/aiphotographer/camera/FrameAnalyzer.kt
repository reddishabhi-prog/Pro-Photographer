package com.aiphotographer.camera

import android.annotation.SuppressLint
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.aiphotographer.intelligence.Box
import com.aiphotographer.intelligence.CameraState
import com.aiphotographer.intelligence.FrameInput
import com.aiphotographer.intelligence.Guidance
import com.aiphotographer.intelligence.Landmark
import com.aiphotographer.intelligence.NormPoint
import com.aiphotographer.intelligence.PersonEstimator
import com.aiphotographer.intelligence.PhotoIntelligenceEngine
import com.aiphotographer.intelligence.SceneClassifier
import com.aiphotographer.intelligence.SceneLabel
import com.aiphotographer.intelligence.ShootingInterest
import com.aiphotographer.intelligence.SubjectMotionTracker
import com.aiphotographer.intelligence.TiltMath
import com.aiphotographer.intelligence.imaging.LumaSampler
import com.aiphotographer.intelligence.imaging.PlaneData
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import java.nio.ByteBuffer
import java.util.concurrent.TimeUnit

/** What one analysed frame told us. */
data class AnalysisResult(
    val guidance: Guidance,
    /** Frame width / height once rotated upright, for laying out overlays. */
    val frameAspect: Float,
    /** Live landmarks of the tracked person, for drawing their actual pose. */
    val livePose: Map<Landmark, NormPoint>,
    /** Detected face boxes, used to hold back sharpening on skin. */
    val faceBoxes: List<Box>,
    /** What the camera reported for this frame; null while it has not yet. */
    val cameraState: CameraState? = null,
    /** The phone's roll this frame, the same value the engine judged. */
    val rollDeg: Float = 0f,
)

/**
 * Looks at the live camera feed and works out what to tell the user.
 *
 * Runs on CameraX's analysis executor, one frame at a time. Three things keep
 * it inside its frame budget: the frame is subsampled rather than read whole,
 * scene labelling runs only every tenth frame because what you are
 * photographing changes slowly, and every model call has a timeout so one slow
 * frame cannot stall the viewfinder.
 */
class FrameAnalyzer(
    private val detectors: MlKitDetectors,
    private val tiltSensor: TiltSensor,
    private val cameraStateProvider: () -> CameraState?,
    private val poseVariantProvider: () -> Int,
    private val interestsProvider: () -> Set<ShootingInterest> = { emptySet() },
    private val onResult: (AnalysisResult) -> Unit,
) : ImageAnalysis.Analyzer {

    private val motionTracker = SubjectMotionTracker()

    private var frameCount = 0L
    private var cachedLabels: List<SceneLabel> = emptyList()
    private var lastFrameNanos = 0L
    private var warnedAboutMissingCameraState = false

    fun reset() {
        motionTracker.reset()
        cachedLabels = emptyList()
        lastFrameNanos = 0L
    }

    @SuppressLint("UnsafeOptInUsageError")
    override fun analyze(image: ImageProxy) {
        try {
            val mediaImage = image.image ?: return
            val rotation = image.imageInfo.rotationDegrees
            val upright = rotation == 90 || rotation == 270
            val uprightWidth = if (upright) image.height else image.width
            val uprightHeight = if (upright) image.width else image.height
            if (uprightWidth <= 0 || uprightHeight <= 0) return

            val inputImage = InputImage.fromMediaImage(mediaImage, rotation)

            // --- Vision models ---
            val poseTask = detectors.detectPose(inputImage)
            val faceTask = detectors.detectFaces(inputImage)

            frameCount++
            val labelTask =
                if (frameCount % LABEL_EVERY_N_FRAMES == 1L) detectors.labelImage(inputImage)
                else null

            val pose = awaitOrNull(poseTask)
            val faces = awaitOrNull(faceTask) ?: emptyList()
            if (labelTask != null) {
                awaitOrNull(labelTask)?.let { cachedLabels = MlKitDetectors.mapLabels(it) }
            }

            val (poseLandmarks, poseConfidences) =
                MlKitDetectors.mapPose(pose, uprightWidth, uprightHeight)
            val mappedFaces = MlKitDetectors.mapFaces(faces, uprightWidth, uprightHeight)
            val people = PersonEstimator.buildPeople(
                faces = mappedFaces,
                poseLandmarks = poseLandmarks,
                poseConfidences = poseConfidences,
            )

            // --- Brightness and colour ---
            val (luma, color) = LumaSampler.sample(
                yPlane = image.planes[0].toPlaneData(),
                width = image.width,
                height = image.height,
                rotationDegrees = rotation,
                uPlane = image.planes.getOrNull(1)?.toPlaneData(),
                vPlane = image.planes.getOrNull(2)?.toPlaneData(),
                sampleStep = SAMPLE_STEP,
            )

            // --- Motion ---
            val now = System.nanoTime()
            val deltaSeconds =
                if (lastFrameNanos == 0L) 0f
                else ((now - lastFrameNanos) / 1_000_000_000.0).toFloat()
            lastFrameNanos = now
            val mainBox = people
                .filter { it.confidence >= SceneClassifier.PERSON_CONFIDENCE_THRESHOLD }
                .maxByOrNull { it.box.area }
                ?.box
            val motion = motionTracker.update(mainBox, deltaSeconds)

            // --- The engine ---
            val reported = cameraStateProvider()
            val cameraState = reported ?: run {
                if (!warnedAboutMissingCameraState) {
                    warnedAboutMissingCameraState = true
                    Log.i(TAG, "No capture result yet; using a provisional exposure estimate")
                }
                PROVISIONAL_CAMERA_STATE
            }

            val frameInput = FrameInput(
                people = people,
                faces = mappedFaces,
                labels = cachedLabels,
                luma = luma,
                color = color,
                camera = cameraState,
                tilt = tiltSensor.current(),
                subjectMotion = motion,
                interests = interestsProvider(),
            )

            val guidance = PhotoIntelligenceEngine.analyse(frameInput, poseVariantProvider())

            onResult(
                AnalysisResult(
                    guidance = guidance,
                    frameAspect = uprightWidth.toFloat() / uprightHeight.toFloat(),
                    livePose = poseLandmarks,
                    faceBoxes = mappedFaces.map { it.box },
                    cameraState = reported,
                    rollDeg = if (TiltMath.hasHorizon(frameInput.tilt)) frameInput.tilt.rollDeg else 0f,
                )
            )
        } catch (e: Exception) {
            // A single bad frame must never take the viewfinder down.
            Log.w(TAG, "Frame analysis failed", e)
        } finally {
            image.close()
        }
    }

    /**
     * Wait for a model, giving up rather than blocking the viewfinder.
     *
     * We are already on the analysis thread, which exists to be blocked, so
     * waiting here is correct - and it keeps the ImageProxy alive until every
     * model has finished reading it.
     */
    private fun <T> awaitOrNull(task: com.google.android.gms.tasks.Task<T>): T? =
        try {
            Tasks.await(task, MODEL_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        } catch (e: Exception) {
            null
        }

    private fun ImageProxy.PlaneProxy.toPlaneData(): PlaneData =
        PlaneData(
            bytes = buffer.copyToByteArray(),
            rowStride = rowStride,
            pixelStride = pixelStride,
        )

    /**
     * Copy a plane without disturbing the original buffer's position, which
     * the vision models may still be reading from.
     */
    private fun ByteBuffer.copyToByteArray(): ByteArray {
        val duplicate = duplicate()
        duplicate.rewind()
        val bytes = ByteArray(duplicate.remaining())
        duplicate.get(bytes)
        return bytes
    }

    companion object {
        private const val TAG = "FrameAnalyzer"

        /** What you are photographing changes slowly; labels can lag. */
        private const val LABEL_EVERY_N_FRAMES = 10L

        /** Read one pixel in sixteen: plenty for regional brightness. */
        private const val SAMPLE_STEP = 4

        private const val MODEL_TIMEOUT_MS = 400L

        /**
         * Used only until the first capture result arrives, which is normally
         * within a frame or two. A plausible indoor exposure, so early advice
         * is sane rather than wild.
         */
        private val PROVISIONAL_CAMERA_STATE = CameraState(
            iso = 100,
            exposureTimeNs = 16_666_666L, // 1/60 s
            aperture = 1.8f,
            focalLengthMm = 5f,
        )
    }
}
