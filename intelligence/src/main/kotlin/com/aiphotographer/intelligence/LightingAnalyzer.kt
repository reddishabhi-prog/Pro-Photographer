package com.aiphotographer.intelligence

import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Reads the light in the frame and decides what to tell the user about it.
 *
 * Bad light is the single biggest reason an otherwise well-framed phone photo
 * looks amateur, so this runs on every analysed frame.
 */
object LightingAnalyzer {

    private const val BACKLIT_RATIO = 1.7f
    private const val BACKLIT_SUBJECT_CEILING = 110f
    private const val DIRECTION_BEHIND_RATIO = 1.6f
    private const val SIDE_LIGHT_DELTA = 28f
    private const val TOP_LIGHT_DELTA = 38f
    private const val HARSH_CLIPPING = 0.09f
    private const val HARSH_CONTRAST = 190f
    private const val FLAT_CONTRAST = 85f
    private const val LOW_LIGHT_EV = 4.0
    private const val GOLDEN_WARMTH = 0.14f

    fun assess(input: FrameInput, subject: Box?): LightingAssessment {
        val luma = input.luma
        val grid = luma.grid
        val sceneEv = ExposureMath.sceneEv(input.camera)

        var subjectLuma: Float? = null
        var backgroundLuma: Float? = null

        if (subject != null) {
            val subjectCells = Regions.cellsInBox(subject, grid)
            val subjectSet = subjectCells.toHashSet()
            val otherCells = (0 until grid * grid).filter { it !in subjectSet }
            subjectLuma = Regions.mean(luma, subjectCells)
            if (otherCells.isNotEmpty()) backgroundLuma = Regions.mean(luma, otherCells)
        }

        // --- Direction: compare the outer thirds of the frame ---
        val third = (grid / 3).coerceAtLeast(1)
        val left = Regions.mean(luma, Regions.columnIndices(grid, 0 until third))
        val right = Regions.mean(luma, Regions.columnIndices(grid, (grid - third) until grid))
        val top = Regions.mean(luma, Regions.rowIndices(grid, 0 until third))
        val bottom = Regions.mean(luma, Regions.rowIndices(grid, (grid - third) until grid))

        val sideDelta = right - left
        val verticalDelta = top - bottom

        val direction = when {
            subjectLuma != null && backgroundLuma != null &&
                backgroundLuma > subjectLuma * DIRECTION_BEHIND_RATIO -> LightDirection.BEHIND
            abs(sideDelta) > SIDE_LIGHT_DELTA ->
                if (sideDelta > 0) LightDirection.RIGHT else LightDirection.LEFT
            verticalDelta > TOP_LIGHT_DELTA -> LightDirection.ABOVE
            else -> LightDirection.FRONT
        }

        // --- Type ---
        val backlit = subjectLuma != null && backgroundLuma != null &&
            backgroundLuma > subjectLuma * BACKLIT_RATIO &&
            subjectLuma < BACKLIT_SUBJECT_CEILING
        val harsh = luma.clippedHighlights > HARSH_CLIPPING && luma.contrast > HARSH_CONTRAST
        val flat = luma.contrast < FLAT_CONTRAST
        val lowLight = sceneEv < LOW_LIGHT_EV
        val golden = input.color.warmth > GOLDEN_WARMTH &&
            sceneEv in 4.5..11.0 &&
            luma.clippedHighlights < 0.08f

        val type = when {
            lowLight -> LightingType.LOW_LIGHT
            backlit -> LightingType.BACKLIT
            harsh -> LightingType.HARSH
            golden -> LightingType.GOLDEN
            flat -> LightingType.FLAT
            else -> LightingType.GOOD
        }

        // --- Exposure compensation ---
        var ev = when {
            backlit -> {
                val ratio = backgroundLuma / subjectLuma.coerceAtLeast(1f)
                min(1.7f, 0.4f + 0.45f * (ratio - BACKLIT_RATIO))
            }
            // Bright scenes (snow, sand, a white wall) always meter too dark.
            luma.mean > 185f && luma.clippedHighlights < 0.02f -> 0.7f
            // Dark scenes meter too bright and lose their mood.
            luma.mean < 55f && luma.clippedShadows < 0.10f -> -0.3f
            harsh -> -0.3f
            else -> 0f
        }
        ev = ev.coerceIn(-2f, 2f)
        ev = (ev * 3f).roundToInt() / 3f // snap to third-stops

        // --- Score ---
        var score = 100
        when (type) {
            LightingType.BACKLIT -> score -= 35
            LightingType.HARSH -> score -= 28
            LightingType.FLAT -> score -= 18
            LightingType.LOW_LIGHT -> score -= 30
            else -> Unit
        }
        score -= min(18f, luma.clippedHighlights * 120f).toInt()
        score -= min(12f, luma.clippedShadows * 70f).toInt()

        return LightingAssessment(
            type = type,
            direction = direction,
            score = score.coerceIn(0, 100),
            evCompensation = ev,
        )
    }
}
