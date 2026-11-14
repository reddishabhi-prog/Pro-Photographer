package com.aiphotographer.intelligence

/**
 * What the viewfinder actually says: one line, or a green light.
 *
 * Testing the first build on a phone showed that three hints at once, half of
 * them cosmetic, reads as an app that is never satisfied. Someone holding a
 * phone up has time to read one instruction. So: the single most urgent thing
 * to fix, and when nothing important is wrong, "Looks good" - even if a
 * purist could still nudge the framing.
 */
data class ViewfinderAdvice(
    /** The one thing to tell the user, or null to say nothing. */
    val hint: Hint?,
    /** Show the green "Looks good — take it" and the green shutter ring. */
    val looksGood: Boolean,
)

object AdviceSelector {

    fun select(guidance: Guidance?): ViewfinderAdvice {
        if (guidance == null) return ViewfinderAdvice(hint = null, looksGood = false)

        val actionable = guidance.hints.filter { it.priority != HintPriority.POLISH }
        if (actionable.isNotEmpty()) {
            return ViewfinderAdvice(hint = actionable.first(), looksGood = false)
        }

        // Nothing important is wrong. Polish advice is held back unless the
        // shot is genuinely weak, where one nudge beats silence.
        return if (guidance.readyToShoot) {
            ViewfinderAdvice(hint = null, looksGood = true)
        } else {
            ViewfinderAdvice(hint = guidance.hints.firstOrNull(), looksGood = false)
        }
    }
}
