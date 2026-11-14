package com.aiphotographer.camera

import com.aiphotographer.intelligence.Face
import com.aiphotographer.intelligence.Box
import com.aiphotographer.intelligence.Landmark
import com.aiphotographer.intelligence.NormPoint
import com.aiphotographer.intelligence.SceneLabel
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.PoseLandmark
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions

/**
 * The on-device vision models, and the translation from their shapes into the
 * engine's.
 *
 * All three models are bundled into the APK rather than downloaded, which is
 * why the app needs no internet permission at all. One important limitation
 * shapes the design above this layer: the pose detector reports landmarks for
 * a single person only, while the face detector sees everyone.
 */
class MlKitDetectors {

    private val poseDetector = PoseDetection.getClient(
        PoseDetectorOptions.Builder()
            // STREAM_MODE keeps state between frames, which is both faster and
            // steadier than treating every frame as a fresh photograph.
            .setDetectorMode(PoseDetectorOptions.STREAM_MODE)
            .build()
    )

    private val faceDetector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
            .setContourMode(FaceDetectorOptions.CONTOUR_MODE_NONE)
            // We need head angle and expression, which come from classification.
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .setMinFaceSize(0.08f)
            .enableTracking()
            .build()
    )

    private val imageLabeler = ImageLabeling.getClient(
        ImageLabelerOptions.Builder()
            .setConfidenceThreshold(0.5f)
            .build()
    )

    fun detectPose(image: InputImage) = poseDetector.process(image)
    fun detectFaces(image: InputImage) = faceDetector.process(image)
    fun labelImage(image: InputImage) = imageLabeler.process(image)

    fun close() {
        runCatching { poseDetector.close() }
        runCatching { faceDetector.close() }
        runCatching { imageLabeler.close() }
    }

    companion object {

        /**
         * ML Kit's landmark constants, mapped to the subset the engine
         * reasons about. Everything not listed - finger tips, eye corners,
         * mouth points - is detail we never make a decision from.
         */
        private val LANDMARK_MAP: Map<Int, Landmark> = mapOf(
            PoseLandmark.NOSE to Landmark.NOSE,
            PoseLandmark.LEFT_EYE to Landmark.LEFT_EYE,
            PoseLandmark.RIGHT_EYE to Landmark.RIGHT_EYE,
            PoseLandmark.LEFT_SHOULDER to Landmark.LEFT_SHOULDER,
            PoseLandmark.RIGHT_SHOULDER to Landmark.RIGHT_SHOULDER,
            PoseLandmark.LEFT_ELBOW to Landmark.LEFT_ELBOW,
            PoseLandmark.RIGHT_ELBOW to Landmark.RIGHT_ELBOW,
            PoseLandmark.LEFT_WRIST to Landmark.LEFT_WRIST,
            PoseLandmark.RIGHT_WRIST to Landmark.RIGHT_WRIST,
            PoseLandmark.LEFT_HIP to Landmark.LEFT_HIP,
            PoseLandmark.RIGHT_HIP to Landmark.RIGHT_HIP,
            PoseLandmark.LEFT_KNEE to Landmark.LEFT_KNEE,
            PoseLandmark.RIGHT_KNEE to Landmark.RIGHT_KNEE,
            PoseLandmark.LEFT_ANKLE to Landmark.LEFT_ANKLE,
            PoseLandmark.RIGHT_ANKLE to Landmark.RIGHT_ANKLE,
        )

        /**
         * Convert a detected pose into normalised landmarks.
         *
         * [uprightWidth] and [uprightHeight] are the frame's dimensions AFTER
         * rotation, because that is the coordinate space ML Kit reports in.
         */
        fun mapPose(
            pose: com.google.mlkit.vision.pose.Pose?,
            uprightWidth: Int,
            uprightHeight: Int,
        ): Pair<Map<Landmark, NormPoint>, Map<Landmark, Float>> {
            if (pose == null || uprightWidth <= 0 || uprightHeight <= 0) {
                return emptyMap<Landmark, NormPoint>() to emptyMap()
            }
            val points = HashMap<Landmark, NormPoint>()
            val confidences = HashMap<Landmark, Float>()
            for (landmark in pose.allPoseLandmarks) {
                val mapped = LANDMARK_MAP[landmark.landmarkType] ?: continue
                val position = landmark.position
                points[mapped] = NormPoint(
                    x = (position.x / uprightWidth).coerceIn(0f, 1f),
                    y = (position.y / uprightHeight).coerceIn(0f, 1f),
                )
                confidences[mapped] = landmark.inFrameLikelihood
            }
            return points to confidences
        }

        /** Convert detected faces into the engine's face model. */
        fun mapFaces(
            faces: List<com.google.mlkit.vision.face.Face>,
            uprightWidth: Int,
            uprightHeight: Int,
        ): List<Face> {
            if (uprightWidth <= 0 || uprightHeight <= 0) return emptyList()
            return faces.map { face ->
                val rect = face.boundingBox
                Face(
                    box = Box(
                        left = (rect.left.toFloat() / uprightWidth).coerceIn(0f, 1f),
                        top = (rect.top.toFloat() / uprightHeight).coerceIn(0f, 1f),
                        right = (rect.right.toFloat() / uprightWidth).coerceIn(0f, 1f),
                        bottom = (rect.bottom.toFloat() / uprightHeight).coerceIn(0f, 1f),
                    ),
                    eulerY = face.headEulerAngleY,
                    eulerZ = face.headEulerAngleZ,
                    smilingProbability = face.smilingProbability ?: -1f,
                    leftEyeOpen = face.leftEyeOpenProbability ?: -1f,
                    rightEyeOpen = face.rightEyeOpenProbability ?: -1f,
                )
            }
        }

        /** Convert image labels into scene labels. */
        fun mapLabels(
            labels: List<com.google.mlkit.vision.label.ImageLabel>,
        ): List<SceneLabel> = labels.map { SceneLabel(it.text, it.confidence) }
    }
}
