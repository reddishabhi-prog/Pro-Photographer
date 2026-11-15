package com.aiphotographer.intelligence

/**
 * Stops the advice flickering.
 *
 * The engine is deterministic, but the detectors underneath it are not: a face
 * drops out for one frame, a landmark wobbles, and the scene flips between
 * PORTRAIT and CLOSEUP. Shown raw at fifteen frames a second that reads as a
 * nervous, untrustworthy app.
 *
 * So advice has to hold for several consecutive frames before it replaces what
 * is on screen, while the numbers behind it (scores, recommended settings)
 * keep updating live. Urgent warnings are the exception - if the user is about
 * to take a ruined photograph, a third of a second of politeness is too long.
 *
 * Not thread-safe: it belongs to whichever single thread reads the analysis.
 */
class GuidanceStabilizer(
    /** Consecutive frames new advice must persist before it is shown. */
    private val framesToSwitch: Int = 3,
) {
    private var shown: Guidance? = null
    private var candidateKey: String? = null
    private var candidateCount = 0

    /**
     * What the advice amounts to. Two frames with the same key are saying the
     * same thing to the user, even if the scores differ by a point.
     */
    private fun keyOf(guidance: Guidance): String = buildString {
        append(guidance.scene.name)
        append('|')
        append(guidance.readyToShoot)
        append('|')
        guidance.hints.joinTo(this, ",") { it.code }
    }

    /** True when something has gone wrong enough to say immediately. */
    private fun isUrgent(guidance: Guidance): Boolean =
        guidance.hints.any { it.priority == HintPriority.CRITICAL }

    fun update(next: Guidance): Guidance {
        val current = shown
        if (current == null) {
            shown = next
            candidateKey = keyOf(next)
            candidateCount = 0
            return next
        }

        val currentKey = keyOf(current)
        val nextKey = keyOf(next)

        if (nextKey == currentKey) {
            // Same advice: pass the fresh numbers straight through.
            candidateKey = nextKey
            candidateCount = 0
            shown = next
            return next
        }

        // A critical warning jumps the queue, as does clearing one.
        if (isUrgent(next) || isUrgent(current)) {
            shown = next
            candidateKey = nextKey
            candidateCount = 0
            return next
        }

        if (nextKey == candidateKey) {
            candidateCount++
        } else {
            candidateKey = nextKey
            candidateCount = 1
        }

        return if (candidateCount >= framesToSwitch) {
            shown = next
            candidateCount = 0
            next
        } else {
            // Keep the advice on screen, but let the live numbers through so
            // the exposure readout and score are never stale.
            current.copy(
                lighting = next.lighting,
                composition = current.composition.copy(score = next.composition.score),
                settings = next.settings,
                overallScore = next.overallScore,
            )
        }
    }

    fun reset() {
        shown = null
        candidateKey = null
        candidateCount = 0
    }
}
