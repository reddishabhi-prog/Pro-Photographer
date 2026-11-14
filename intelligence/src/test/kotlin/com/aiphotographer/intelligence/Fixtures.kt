package com.aiphotographer.intelligence

/**
 * Builders for realistic photographic situations.
 *
 * Each scenario in the tests is a situation a working photographer would
 * recognise, with an answer they would agree with.
 */
object Fixtures {

    fun grid(fn: (row: Int, col: Int) -> Float): List<Float> =
        (0 until LUMA_GRID * LUMA_GRID).map { index ->
            fn(index / LUMA_GRID, index % LUMA_GRID)
        }

    fun flatGrid(value: Float): List<Float> = List(LUMA_GRID * LUMA_GRID) { value }

    /** Head, shoulders and hips visible: a half-body framing. */
    val UPPER_BODY: Map<Landmark, NormPoint> = mapOf(
        Landmark.NOSE to NormPoint(0.50f, 0.32f),
        Landmark.LEFT_EYE to NormPoint(0.47f, 0.30f),
        Landmark.RIGHT_EYE to NormPoint(0.53f, 0.30f),
        Landmark.LEFT_SHOULDER to NormPoint(0.40f, 0.46f),
        Landmark.RIGHT_SHOULDER to NormPoint(0.60f, 0.46f),
        Landmark.LEFT_HIP to NormPoint(0.43f, 0.75f),
        Landmark.RIGHT_HIP to NormPoint(0.57f, 0.75f),
    )

    /** Everything down to the ankles: a full-body framing. */
    val FULL_BODY: Map<Landmark, NormPoint> = UPPER_BODY + mapOf(
        Landmark.LEFT_KNEE to NormPoint(0.44f, 0.86f),
        Landmark.RIGHT_KNEE to NormPoint(0.56f, 0.86f),
        Landmark.LEFT_ANKLE to NormPoint(0.45f, 0.97f),
        Landmark.RIGHT_ANKLE to NormPoint(0.55f, 0.97f),
    )

    /** Down to the knees but no ankles. */
    val THREE_QUARTER_BODY: Map<Landmark, NormPoint> = UPPER_BODY + mapOf(
        Landmark.LEFT_KNEE to NormPoint(0.44f, 0.86f),
        Landmark.RIGHT_KNEE to NormPoint(0.56f, 0.86f),
    )

    fun person(
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        landmarks: Map<Landmark, NormPoint> = emptyMap(),
        confidence: Float = 0.95f,
        landmarkConfidence: Float = 0.9f,
    ): Person = Person(
        box = Box(left, top, right, bottom),
        confidence = confidence,
        landmarks = landmarks,
        landmarkConfidence = landmarks.keys.associateWith { landmarkConfidence },
    )

    fun face(
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        eulerY: Float = 0f,
    ): Face = Face(box = Box(left, top, right, bottom), eulerY = eulerY)

    /** Shutter speed as a denominator, e.g. 500 -> 1/500 s, in nanoseconds. */
    fun shutter(denominator: Int): Long = (1_000_000_000L / denominator)

    fun camera(
        iso: Int,
        shutterDenominator: Int,
        aperture: Float = 1.8f,
        focalLengthMm: Float = 5f,
    ): CameraState = CameraState(
        iso = iso,
        exposureTimeNs = shutter(shutterDenominator),
        aperture = aperture,
        focalLengthMm = focalLengthMm,
    )

    fun neutralColor(level: Float = 145f) = ColorStats(level, level, level + 2f)
}
