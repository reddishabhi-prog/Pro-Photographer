package com.aiphotographer.intelligence

/**
 * What ShotMuse is doing to the camera right now, in words a person reads in
 * a glance - so the user can see the app working, not just trust that it is.
 *
 * Also the one place that decides how hard AUTO may push exposure. The first
 * phone tests produced photos worse than the stock camera; part of the reason
 * was compensation of up to +1.7 stops for backlight, which clips a sky beyond
 * recovery. The phone's own HDR now does the heavy lifting, so we nudge.
 */
object AutoActions {

    /** The most exposure compensation AUTO will apply on its own, in stops. */
    const val MAX_AUTO_EV = 1.0f

    fun autoEv(guidance: Guidance): Float =
        guidance.settings.evCompensation.coerceIn(-MAX_AUTO_EV, MAX_AUTO_EV)

    /**
     * Up to three short labels, most telling first. [captureMode] is the
     * phone's own processing in use for stills ("HDR", "Night", "Auto+"), if any.
     */
    fun describe(guidance: Guidance, captureMode: String?): List<String> {
        val ev = autoEv(guidance)
        val labels = ArrayList<String>()
        when (guidance.lighting.type) {
            LightingType.BACKLIT -> if (ev > 0f) labels += "Brightening the face"
            LightingType.LOW_LIGHT -> labels += "Low-light boost"
            LightingType.GOLDEN -> labels += "Keeping the warm light"
            LightingType.HARSH -> labels += "Protecting highlights"
            else -> if (ev < 0f) labels += "Protecting highlights" else if (ev > 0f) labels += "Brightening"
        }
        if (guidance.scene == SceneType.FOOD) labels += "Food colour"
        if (guidance.scene == SceneType.LANDSCAPE) labels += "Sky and detail"
        if (guidance.scene in PEOPLE) labels += "Skin kept soft"
        captureMode?.let { labels.add(0, it) }
        return labels.distinct().take(3)
    }

    private val PEOPLE = setOf(SceneType.PORTRAIT, SceneType.CLOSEUP, SceneType.FULL_BODY, SceneType.GROUP)
}
