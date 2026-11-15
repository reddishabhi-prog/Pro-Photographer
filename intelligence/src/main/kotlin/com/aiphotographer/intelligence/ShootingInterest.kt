package com.aiphotographer.intelligence

/**
 * What the user told us they like to photograph, picked once at first launch.
 *
 * It is a prior, not an override. When the scene is clear - a person in
 * frame, a plate filling the view - the evidence wins. It matters in the
 * ambiguous middle, where a food photographer's half-confident "dish" label
 * is far more likely to be food than a stranger's would be.
 */
enum class ShootingInterest(
    /** Scenes this interest makes more likely. */
    val scenes: Set<SceneType>,
    /** The one piece of advice worth hearing before the first shot. */
    val firstTip: String,
) {
    PEOPLE(
        setOf(SceneType.PORTRAIT, SceneType.CLOSEUP, SceneType.FULL_BODY, SceneType.GROUP),
        "For people: put the light in front of them, never behind",
    ),
    FOOD(
        setOf(SceneType.FOOD),
        "For food: shoot from straight above, near a window",
    ),
    TRAVEL(
        setOf(SceneType.LANDSCAPE, SceneType.ARCHITECTURE),
        "For views: keep the horizon level and off the middle",
    ),
    NIGHT(
        setOf(SceneType.NIGHT),
        "At night: brace the phone and hold still for a second",
    ),
    PRODUCTS(
        setOf(SceneType.OBJECT),
        "For products: soft side light and a plain background",
    ),
    PETS(
        setOf(SceneType.PET),
        "For pets: get down to their eye level",
    ),
    ;

    companion object {
        /** How much a stated interest strengthens matching scene evidence. */
        const val PRIOR_BOOST = 1.35f

        fun boostFor(scene: SceneType, interests: Set<ShootingInterest>): Float =
            if (interests.any { scene in it.scenes }) PRIOR_BOOST else 1f

        /** The tip for the first interest the user picked, if any. */
        fun welcomeTip(interests: List<ShootingInterest>): String? = interests.firstOrNull()?.firstTip

        fun parse(names: Collection<String>): Set<ShootingInterest> =
            names.mapNotNull { name -> entries.firstOrNull { it.name == name } }.toSet()
    }
}
