package com.aiphotographer.intelligence

/**
 * A pose the app can draw as a ghost outline for the subject to match.
 *
 * [skeleton] is in pose-local space: x and y both run 0..1 inside the box the
 * UI chooses to draw the guide in, with y = 0 at the top of the head. The UI
 * scales that box to wherever the subject should stand, so one skeleton works
 * at any size on any screen.
 *
 * Sides are named as they appear to the VIEWER, because the point of the
 * outline is to be drawn, not to be matched against detector output.
 */
data class PoseTemplate(
    val id: String,
    val name: String,
    /** One short line telling the user why this pose works. */
    val why: String,
    val scenes: Set<SceneType>,
    val framings: Set<BodyFraming>,
    val minPeople: Int,
    val maxPeople: Int,
    val skeleton: Map<Landmark, NormPoint> = emptyMap(),
) {
    /** True when the UI has geometry to draw rather than only advice. */
    val isDrawable: Boolean get() = skeleton.size >= 4
}

object PoseLibrary {

    /**
     * Which landmarks join up, for drawing the outline.
     * Pairs missing from a given skeleton are simply skipped.
     */
    val BONES: List<Pair<Landmark, Landmark>> = listOf(
        Landmark.LEFT_EYE to Landmark.RIGHT_EYE,
        // There is no neck landmark, so nose-to-shoulder stands in for it.
        Landmark.NOSE to Landmark.LEFT_SHOULDER,
        Landmark.NOSE to Landmark.RIGHT_SHOULDER,
        Landmark.LEFT_SHOULDER to Landmark.RIGHT_SHOULDER,
        Landmark.LEFT_SHOULDER to Landmark.LEFT_ELBOW,
        Landmark.LEFT_ELBOW to Landmark.LEFT_WRIST,
        Landmark.RIGHT_SHOULDER to Landmark.RIGHT_ELBOW,
        Landmark.RIGHT_ELBOW to Landmark.RIGHT_WRIST,
        Landmark.LEFT_SHOULDER to Landmark.LEFT_HIP,
        Landmark.RIGHT_SHOULDER to Landmark.RIGHT_HIP,
        Landmark.LEFT_HIP to Landmark.RIGHT_HIP,
        Landmark.LEFT_HIP to Landmark.LEFT_KNEE,
        Landmark.LEFT_KNEE to Landmark.LEFT_ANKLE,
        Landmark.RIGHT_HIP to Landmark.RIGHT_KNEE,
        Landmark.RIGHT_KNEE to Landmark.RIGHT_ANKLE,
    )

    private fun p(x: Float, y: Float) = NormPoint(x, y)

    val ALL: List<PoseTemplate> = listOf(

        PoseTemplate(
            id = "closeup_chin_tilt",
            name = "Chin forward, slight tilt",
            why = "Pushing the chin a little forward and down sharpens the jawline.",
            scenes = setOf(SceneType.CLOSEUP),
            framings = setOf(BodyFraming.CLOSEUP, BodyFraming.UNKNOWN),
            minPeople = 1, maxPeople = 1,
            skeleton = mapOf(
                Landmark.LEFT_EYE to p(0.43f, 0.34f),
                Landmark.RIGHT_EYE to p(0.57f, 0.32f),
                Landmark.NOSE to p(0.50f, 0.44f),
                Landmark.LEFT_SHOULDER to p(0.20f, 0.90f),
                Landmark.RIGHT_SHOULDER to p(0.80f, 0.86f),
            ),
        ),

        PoseTemplate(
            id = "portrait_shoulder_turn",
            name = "Turn the shoulders, face the lens",
            why = "Angling the shoulders away while the face stays to camera adds depth.",
            scenes = setOf(SceneType.PORTRAIT),
            framings = setOf(BodyFraming.CLOSEUP, BodyFraming.HALF),
            minPeople = 1, maxPeople = 1,
            skeleton = mapOf(
                Landmark.LEFT_EYE to p(0.46f, 0.13f),
                Landmark.RIGHT_EYE to p(0.58f, 0.13f),
                Landmark.NOSE to p(0.52f, 0.17f),
                Landmark.LEFT_SHOULDER to p(0.34f, 0.33f),
                Landmark.RIGHT_SHOULDER to p(0.66f, 0.29f),
                Landmark.LEFT_ELBOW to p(0.28f, 0.60f),
                Landmark.RIGHT_ELBOW to p(0.71f, 0.56f),
                Landmark.LEFT_WRIST to p(0.33f, 0.84f),
                Landmark.RIGHT_WRIST to p(0.66f, 0.81f),
                Landmark.LEFT_HIP to p(0.40f, 0.93f),
                Landmark.RIGHT_HIP to p(0.61f, 0.90f),
            ),
        ),

        PoseTemplate(
            id = "portrait_hand_to_jaw",
            name = "Hand near the jaw",
            why = "A relaxed hand near the jaw gives the arms something to do.",
            scenes = setOf(SceneType.PORTRAIT),
            framings = setOf(BodyFraming.HALF, BodyFraming.CLOSEUP),
            minPeople = 1, maxPeople = 1,
            skeleton = mapOf(
                Landmark.LEFT_EYE to p(0.45f, 0.14f),
                Landmark.RIGHT_EYE to p(0.56f, 0.14f),
                Landmark.NOSE to p(0.50f, 0.18f),
                Landmark.LEFT_SHOULDER to p(0.33f, 0.35f),
                Landmark.RIGHT_SHOULDER to p(0.67f, 0.33f),
                Landmark.LEFT_ELBOW to p(0.27f, 0.64f),
                Landmark.RIGHT_ELBOW to p(0.76f, 0.66f),
                Landmark.LEFT_WRIST to p(0.36f, 0.88f),
                Landmark.RIGHT_WRIST to p(0.61f, 0.29f), // hand up by the jaw
                Landmark.LEFT_HIP to p(0.40f, 0.94f),
                Landmark.RIGHT_HIP to p(0.61f, 0.92f),
            ),
        ),

        PoseTemplate(
            id = "full_contrapposto",
            name = "Weight on the back foot",
            why = "Shifting weight onto one leg makes an S-curve instead of a plank.",
            scenes = setOf(SceneType.FULL_BODY),
            framings = setOf(BodyFraming.FULL, BodyFraming.THREE_QUARTER),
            minPeople = 1, maxPeople = 1,
            skeleton = mapOf(
                Landmark.LEFT_EYE to p(0.47f, 0.05f),
                Landmark.RIGHT_EYE to p(0.53f, 0.05f),
                Landmark.NOSE to p(0.50f, 0.07f),
                Landmark.LEFT_SHOULDER to p(0.40f, 0.17f),
                Landmark.RIGHT_SHOULDER to p(0.60f, 0.17f),
                Landmark.LEFT_ELBOW to p(0.36f, 0.33f),
                Landmark.RIGHT_ELBOW to p(0.65f, 0.32f),
                Landmark.LEFT_WRIST to p(0.38f, 0.47f),
                Landmark.RIGHT_WRIST to p(0.68f, 0.46f),
                Landmark.LEFT_HIP to p(0.44f, 0.51f),
                Landmark.RIGHT_HIP to p(0.57f, 0.50f),
                Landmark.LEFT_KNEE to p(0.43f, 0.72f),
                Landmark.RIGHT_KNEE to p(0.60f, 0.71f),
                Landmark.LEFT_ANKLE to p(0.42f, 0.95f),
                Landmark.RIGHT_ANKLE to p(0.63f, 0.93f),
            ),
        ),

        PoseTemplate(
            id = "full_walk_toward",
            name = "Walk towards the lens",
            why = "Movement relaxes the face and the arms on its own.",
            scenes = setOf(SceneType.FULL_BODY),
            framings = setOf(BodyFraming.FULL),
            minPeople = 1, maxPeople = 1,
            skeleton = mapOf(
                Landmark.LEFT_EYE to p(0.47f, 0.05f),
                Landmark.RIGHT_EYE to p(0.53f, 0.05f),
                Landmark.NOSE to p(0.50f, 0.07f),
                Landmark.LEFT_SHOULDER to p(0.39f, 0.17f),
                Landmark.RIGHT_SHOULDER to p(0.61f, 0.17f),
                Landmark.LEFT_ELBOW to p(0.33f, 0.34f),
                Landmark.RIGHT_ELBOW to p(0.68f, 0.31f),
                Landmark.LEFT_WRIST to p(0.30f, 0.49f),
                Landmark.RIGHT_WRIST to p(0.72f, 0.45f),
                Landmark.LEFT_HIP to p(0.44f, 0.51f),
                Landmark.RIGHT_HIP to p(0.57f, 0.51f),
                Landmark.LEFT_KNEE to p(0.40f, 0.70f),
                Landmark.RIGHT_KNEE to p(0.62f, 0.73f),
                Landmark.LEFT_ANKLE to p(0.36f, 0.90f),
                Landmark.RIGHT_ANKLE to p(0.68f, 0.95f),
            ),
        ),

        PoseTemplate(
            id = "full_lean",
            name = "Lean on something",
            why = "A wall or railing gives the body a believable anchor.",
            scenes = setOf(SceneType.FULL_BODY),
            framings = setOf(BodyFraming.FULL, BodyFraming.THREE_QUARTER),
            minPeople = 1, maxPeople = 1,
            skeleton = mapOf(
                Landmark.LEFT_EYE to p(0.41f, 0.05f),
                Landmark.RIGHT_EYE to p(0.49f, 0.05f),
                Landmark.NOSE to p(0.45f, 0.07f),
                Landmark.LEFT_SHOULDER to p(0.33f, 0.18f),
                Landmark.RIGHT_SHOULDER to p(0.56f, 0.19f),
                Landmark.LEFT_ELBOW to p(0.27f, 0.36f),
                Landmark.RIGHT_ELBOW to p(0.61f, 0.37f),
                Landmark.LEFT_WRIST to p(0.30f, 0.51f),
                Landmark.RIGHT_WRIST to p(0.57f, 0.53f),
                Landmark.LEFT_HIP to p(0.40f, 0.53f),
                Landmark.RIGHT_HIP to p(0.56f, 0.54f),
                Landmark.LEFT_KNEE to p(0.42f, 0.74f),
                Landmark.RIGHT_KNEE to p(0.57f, 0.74f),
                Landmark.LEFT_ANKLE to p(0.46f, 0.95f),
                Landmark.RIGHT_ANKLE to p(0.63f, 0.93f),
            ),
        ),

        PoseTemplate(
            id = "full_hands_in_pockets",
            name = "Hands in pockets, one knee soft",
            why = "Gives restless hands a home and stops the stiff standing look.",
            scenes = setOf(SceneType.FULL_BODY),
            framings = setOf(BodyFraming.FULL, BodyFraming.THREE_QUARTER),
            minPeople = 1, maxPeople = 1,
            skeleton = mapOf(
                Landmark.LEFT_EYE to p(0.47f, 0.05f),
                Landmark.RIGHT_EYE to p(0.53f, 0.05f),
                Landmark.NOSE to p(0.50f, 0.07f),
                Landmark.LEFT_SHOULDER to p(0.39f, 0.17f),
                Landmark.RIGHT_SHOULDER to p(0.61f, 0.17f),
                Landmark.LEFT_ELBOW to p(0.34f, 0.35f),
                Landmark.RIGHT_ELBOW to p(0.66f, 0.35f),
                Landmark.LEFT_WRIST to p(0.41f, 0.50f),
                Landmark.RIGHT_WRIST to p(0.59f, 0.50f),
                Landmark.LEFT_HIP to p(0.43f, 0.52f),
                Landmark.RIGHT_HIP to p(0.57f, 0.52f),
                Landmark.LEFT_KNEE to p(0.42f, 0.73f),
                Landmark.RIGHT_KNEE to p(0.59f, 0.72f),
                Landmark.LEFT_ANKLE to p(0.43f, 0.95f),
                Landmark.RIGHT_ANKLE to p(0.60f, 0.94f),
            ),
        ),

        PoseTemplate(
            id = "seated_lean_forward",
            name = "Sit tall, lean in slightly",
            why = "Leaning a little towards the lens reads as engaged, not slouched.",
            scenes = setOf(SceneType.PORTRAIT),
            framings = setOf(BodyFraming.HALF, BodyFraming.THREE_QUARTER),
            minPeople = 1, maxPeople = 1,
            skeleton = mapOf(
                Landmark.LEFT_EYE to p(0.46f, 0.12f),
                Landmark.RIGHT_EYE to p(0.57f, 0.12f),
                Landmark.NOSE to p(0.51f, 0.16f),
                Landmark.LEFT_SHOULDER to p(0.35f, 0.34f),
                Landmark.RIGHT_SHOULDER to p(0.67f, 0.34f),
                Landmark.LEFT_ELBOW to p(0.29f, 0.62f),
                Landmark.RIGHT_ELBOW to p(0.73f, 0.62f),
                Landmark.LEFT_WRIST to p(0.42f, 0.80f),
                Landmark.RIGHT_WRIST to p(0.60f, 0.80f),
                Landmark.LEFT_HIP to p(0.39f, 0.88f),
                Landmark.RIGHT_HIP to p(0.63f, 0.88f),
            ),
        ),

        // Group poses are advice, not outlines: drawing a skeleton over four
        // people would obscure the very thing the user is composing.
        PoseTemplate(
            id = "group_stagger",
            name = "Stagger the heights",
            why = "Uneven head heights read as a photograph, not a police line-up.",
            scenes = setOf(SceneType.GROUP),
            framings = setOf(BodyFraming.UNKNOWN, BodyFraming.FULL, BodyFraming.HALF, BodyFraming.CLOSEUP),
            minPeople = 2, maxPeople = 12,
        ),

        PoseTemplate(
            id = "group_close_in",
            name = "Close the gaps",
            why = "Shoulders touching removes the dead space between people.",
            scenes = setOf(SceneType.GROUP),
            framings = setOf(BodyFraming.UNKNOWN, BodyFraming.FULL, BodyFraming.HALF, BodyFraming.CLOSEUP),
            minPeople = 2, maxPeople = 12,
        ),
    )

    private val byId: Map<String, PoseTemplate> = ALL.associateBy { it.id }

    fun byId(id: String?): PoseTemplate? = id?.let { byId[it] }

    /** Every pose that suits the current scene, in library order. */
    fun candidates(
        scene: SceneType,
        framing: BodyFraming,
        peopleCount: Int,
    ): List<PoseTemplate> = ALL.filter { pose ->
        scene in pose.scenes &&
            (framing in pose.framings || BodyFraming.UNKNOWN in pose.framings) &&
            peopleCount in pose.minPeople..pose.maxPeople
    }

    /**
     * Pick a pose. [variant] lets the user cycle through alternatives without
     * the engine ever returning something random: the same inputs and the same
     * variant always give the same pose.
     */
    fun select(
        scene: SceneType,
        framing: BodyFraming,
        peopleCount: Int,
        variant: Int = 0,
    ): PoseTemplate? {
        val options = candidates(scene, framing, peopleCount)
        if (options.isEmpty()) return null
        val index = ((variant % options.size) + options.size) % options.size
        return options[index]
    }
}
