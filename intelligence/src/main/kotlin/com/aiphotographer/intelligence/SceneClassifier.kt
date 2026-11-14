package com.aiphotographer.intelligence

/**
 * Works out what kind of photograph the user is taking.
 *
 * People take priority over labels: if someone is in the frame, this is a
 * picture of them, whatever else the scene contains.
 */
object SceneClassifier {

    private val LANDSCAPE_WORDS = setOf(
        "sky", "mountain", "cloud", "beach", "sea", "ocean", "lake", "river",
        "sunset", "sunrise", "field", "forest", "tree", "desert", "horizon",
        "valley", "snow", "landscape",
    )
    private val ARCHITECTURE_WORDS = setOf(
        "building", "architecture", "skyscraper", "house", "temple", "church",
        "bridge", "monument", "tower", "facade", "arch", "palace",
    )
    private val FOOD_WORDS = setOf(
        "food", "dish", "cuisine", "dessert", "breakfast", "lunch", "meal",
        "plate", "coffee", "drink", "cake", "bread", "fruit", "salad", "pizza",
    )
    private val PET_WORDS = setOf(
        "dog", "cat", "pet", "puppy", "kitten", "animal", "bird", "horse",
    )
    private val DOCUMENT_WORDS = setOf(
        "text", "paper", "document", "menu", "book", "receipt", "whiteboard",
    )

    /** Minimum confidence for a detected person to count. */
    const val PERSON_CONFIDENCE_THRESHOLD: Float = 0.5f

    /** A face this large relative to the frame means a close-up. */
    private const val CLOSEUP_FACE_AREA = 0.16f

    /** Below this EV we treat the scene as night. */
    private const val NIGHT_EV = 2.0

    /** Sum of confidences for labels whose words appear in [vocabulary]. */
    internal fun labelScore(labels: List<SceneLabel>, vocabulary: Set<String>): Float {
        var total = 0f
        for (label in labels) {
            val words = label.text
                .replace('-', ' ')
                .split(' ')
                .map { it.trim().lowercase() }
                .filter { it.isNotEmpty() }
                .toSet()
            if (words.any { it in vocabulary }) total += label.confidence
        }
        return total
    }

    /** How much of the person is in frame, from landmarks then box shape. */
    fun classifyFraming(person: Person): BodyFraming {
        if (person.hasAny(Landmark.LEFT_ANKLE, Landmark.RIGHT_ANKLE, minConfidence = 0.4f)) {
            return BodyFraming.FULL
        }
        if (person.hasAny(Landmark.LEFT_KNEE, Landmark.RIGHT_KNEE, minConfidence = 0.4f)) {
            return BodyFraming.THREE_QUARTER
        }
        if (person.hasAny(Landmark.LEFT_HIP, Landmark.RIGHT_HIP, minConfidence = 0.4f)) {
            return BodyFraming.HALF
        }
        if (person.hasAny(Landmark.LEFT_SHOULDER, Landmark.RIGHT_SHOULDER, minConfidence = 0.4f)) {
            return BodyFraming.CLOSEUP
        }

        // No usable landmarks: fall back to how much height the box fills.
        return when {
            person.box.height > 0.85f -> BodyFraming.FULL
            person.box.height > 0.55f -> BodyFraming.HALF
            else -> BodyFraming.CLOSEUP
        }
    }

    /** The face most likely to belong to [person]. */
    fun mainFace(input: FrameInput, person: Person?): Face? {
        if (input.faces.isEmpty()) return null
        if (person == null) return input.faces.maxByOrNull { it.box.area }
        return input.faces.minByOrNull { face ->
            val dx = face.box.centerX - person.box.centerX
            val dy = face.box.centerY - person.box.top
            dx * dx + dy * dy
        }
    }

    /** The largest confident person in the frame, if any. */
    fun mainPerson(input: FrameInput): Person? =
        input.people
            .filter { it.confidence >= PERSON_CONFIDENCE_THRESHOLD }
            .maxByOrNull { it.box.area }

    fun classify(input: FrameInput): Pair<SceneType, BodyFraming> {
        val people = input.people.filter { it.confidence >= PERSON_CONFIDENCE_THRESHOLD }
        val veryDark = ExposureMath.sceneEv(input.camera) < NIGHT_EV

        if (people.size >= 3) return SceneType.GROUP to BodyFraming.UNKNOWN

        if (people.isNotEmpty()) {
            val main = people.maxByOrNull { it.box.area }!!
            val framing = classifyFraming(main)
            if (people.size == 2) return SceneType.GROUP to framing

            val face = mainFace(input, main)
            if (face != null && face.box.area > CLOSEUP_FACE_AREA) {
                return SceneType.CLOSEUP to framing
            }
            return if (framing == BodyFraming.FULL || framing == BodyFraming.THREE_QUARTER) {
                SceneType.FULL_BODY to framing
            } else {
                SceneType.PORTRAIT to framing
            }
        }

        // Nobody in frame: decide from labels.
        val scores = mapOf(
            SceneType.FOOD to labelScore(input.labels, FOOD_WORDS),
            SceneType.PET to labelScore(input.labels, PET_WORDS),
            SceneType.DOCUMENT to labelScore(input.labels, DOCUMENT_WORDS),
            SceneType.ARCHITECTURE to labelScore(input.labels, ARCHITECTURE_WORDS),
            SceneType.LANDSCAPE to labelScore(input.labels, LANDSCAPE_WORDS),
        ).mapValues { (scene, score) -> score * ShootingInterest.boostFor(scene, input.interests) }
        val best = scores.maxByOrNull { it.value }
        if (best != null && best.value >= 0.45f) return best.key to BodyFraming.UNKNOWN
        if (veryDark) return SceneType.NIGHT to BodyFraming.UNKNOWN
        return SceneType.UNKNOWN to BodyFraming.UNKNOWN
    }
}
