package com.aiphotographer.intelligence.imaging

import com.aiphotographer.intelligence.Box
import com.aiphotographer.intelligence.Guidance
import com.aiphotographer.intelligence.LightingType
import com.aiphotographer.intelligence.SceneType
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * What to do to a captured photograph, decided before a single pixel moves.
 *
 * Separating the decision from the work means the choices can be tested, shown
 * to the user, and undone - the original file is never overwritten.
 */
data class EnhancementPlan(
    /** Input level mapped to pure black. */
    val blackPoint: Int = 0,
    /** Input level mapped to pure white. */
    val whitePoint: Int = 255,
    /** 0..1: how much to open up the dark areas. */
    val shadowLift: Float = 0f,
    /** 0..1: how much to pull detail back out of the bright areas. */
    val highlightRecovery: Float = 0f,
    val gainR: Float = 1f,
    val gainG: Float = 1f,
    val gainB: Float = 1f,
    /** 0..1: saturation added, weighted towards the duller colours. */
    val vibrance: Float = 0f,
    /** 0..1: local contrast ("clarity") via an unsharp mask. */
    val clarity: Float = 0f,
    /** Blur radius for the clarity pass, in pixels at full resolution. */
    val clarityRadius: Int = 0,
    /** Areas - normally faces - where clarity is held back. */
    val protectedRegions: List<Box> = emptyList(),
    /**
     * 0..1 multiplier on the grey-world white-balance correction. Zero leaves
     * the colour of the light completely alone, which is what golden hour and
     * candlelight need.
     */
    val whiteBalanceStrength: Float = 1f,
    /** Plain-language summary of what was done, for the UI. */
    val summary: String = "",
) {
    val isNoOp: Boolean
        get() = blackPoint == 0 && whitePoint == 255 && shadowLift == 0f &&
            highlightRecovery == 0f && vibrance == 0f && clarity == 0f &&
            gainR == 1f && gainG == 1f && gainB == 1f
}

/**
 * The darkroom.
 *
 * Pure integer and float maths on an ARGB pixel array, with no Android types,
 * so the whole pipeline can be run and checked without a device.
 */
object ImageEnhancer {

    /** How much of a full grey-world correction we ever apply. */
    private const val WB_STRENGTH = 0.6f

    /** Clarity is reduced to this fraction inside a protected region. */
    private const val PROTECTED_CLARITY = 0.3f

    /** Highlights start being compressed above this point. */
    private const val HIGHLIGHT_KNEE = 0.72f

    // -----------------------------------------------------------------
    // 1. Deciding what to do
    // -----------------------------------------------------------------

    /**
     * Build a plan from what the engine understood about the scene.
     *
     * The guiding principle is restraint: a photograph that looks obviously
     * processed is worse than one left alone, so every amount here is the
     * smallest that still makes a visible improvement.
     */
    fun planFor(
        guidance: Guidance,
        faces: List<Box> = emptyList(),
    ): EnhancementPlan {
        val lighting = guidance.lighting.type
        val scene = guidance.scene

        // --- Tone ---
        val shadowLift = when {
            lighting == LightingType.BACKLIT -> 0.75f
            lighting == LightingType.HARSH -> 0.50f
            lighting == LightingType.LOW_LIGHT -> 0.35f
            else -> 0.20f
        }
        val highlightRecovery = when {
            lighting == LightingType.HARSH -> 0.70f
            lighting == LightingType.BACKLIT -> 0.50f
            scene == SceneType.LANDSCAPE -> 0.45f
            else -> 0.15f
        }

        // --- Colour: never neutralise light that is meant to be warm ---
        val wbStrength = when {
            lighting == LightingType.GOLDEN -> 0f
            scene == SceneType.NIGHT -> 0.25f
            else -> 1f
        }

        // --- Saturation ---
        val vibrance = when (scene) {
            SceneType.LANDSCAPE, SceneType.ARCHITECTURE -> 0.35f
            SceneType.FOOD -> 0.30f
            SceneType.PORTRAIT, SceneType.CLOSEUP -> 0.12f
            SceneType.NIGHT -> 0.05f
            else -> 0.18f
        }

        // --- Local contrast: heavy on stone and sky, gentle on skin ---
        val clarity = when (scene) {
            SceneType.LANDSCAPE, SceneType.ARCHITECTURE -> 0.55f
            SceneType.FOOD -> 0.40f
            SceneType.DOCUMENT -> 0.60f
            SceneType.PORTRAIT, SceneType.CLOSEUP -> 0.18f
            SceneType.NIGHT -> 0.10f // sharpening noise only makes noise worse
            else -> 0.30f
        }

        val summary = buildSummary(lighting, scene, shadowLift, highlightRecovery, vibrance, clarity)

        return EnhancementPlan(
            // Levels and gains are measured from the pixels later, by
            // withMeasuredLevels; the scene alone cannot tell us them.
            blackPoint = 0,
            whitePoint = 255,
            shadowLift = shadowLift,
            highlightRecovery = highlightRecovery,
            vibrance = vibrance,
            clarity = clarity,
            clarityRadius = 0, // set by withClarityRadius once the size is known
            protectedRegions = faces,
            whiteBalanceStrength = wbStrength,
            summary = summary,
        )
    }

    /**
     * Add the levels and white-balance gains, which depend on the actual
     * pixels rather than on the scene description.
     */
    fun withMeasuredLevels(
        plan: EnhancementPlan,
        p5: Float,
        p95: Float,
        clippedShadows: Float,
        clippedHighlights: Float,
        meanR: Float,
        meanG: Float,
        meanB: Float,
        whiteBalanceStrength: Float = plan.whiteBalanceStrength,
    ): EnhancementPlan {
        // Only pull the black point in if the photograph has no true blacks;
        // if the shadows are already clipped, pulling further destroys detail.
        val blackPoint = if (clippedShadows > 0.02f) 0 else p5.roundToInt().coerceIn(0, 48)
        val whitePoint = if (clippedHighlights > 0.02f) 255 else p95.roundToInt().coerceIn(200, 255)

        // Grey-world white balance: assume the average of a scene should be
        // neutral, then apply only part of the correction so real colour
        // (a red wall, a green field) survives.
        val mean = (meanR + meanG + meanB) / 3f
        val strength = (WB_STRENGTH * whiteBalanceStrength).coerceIn(0f, 1f)
        fun gain(channel: Float): Float {
            if (channel <= 1f) return 1f
            val full = mean / channel
            return (1f + (full - 1f) * strength).coerceIn(0.75f, 1.35f)
        }

        return plan.copy(
            blackPoint = blackPoint,
            whitePoint = max(blackPoint + 16, whitePoint),
            gainR = gain(meanR),
            gainG = gain(meanG),
            gainB = gain(meanB),
        )
    }

    /** Clarity radius scales with the image so it looks the same at any size. */
    fun withClarityRadius(plan: EnhancementPlan, width: Int, height: Int): EnhancementPlan {
        val shortSide = min(width, height)
        val radius = (shortSide / 180).coerceIn(1, 24)
        return plan.copy(clarityRadius = radius)
    }

    private fun buildSummary(
        lighting: LightingType,
        scene: SceneType,
        shadowLift: Float,
        highlightRecovery: Float,
        vibrance: Float,
        clarity: Float,
    ): String {
        val parts = ArrayList<String>()
        if (shadowLift >= 0.5f) parts.add("opened up the shadows")
        if (highlightRecovery >= 0.45f) parts.add("recovered the bright areas")
        if (lighting == LightingType.GOLDEN) parts.add("kept the warm light")
        if (vibrance >= 0.30f) parts.add("lifted the colour")
        if (clarity >= 0.50f) parts.add("added detail")
        if (scene == SceneType.PORTRAIT || scene == SceneType.CLOSEUP) {
            parts.add("kept skin soft")
        }
        if (parts.isEmpty()) parts.add("gentle tone and colour balance")
        return parts.joinToString(", ").replaceFirstChar { it.uppercase() }
    }

    // -----------------------------------------------------------------
    // 2. Measuring the actual photograph
    // -----------------------------------------------------------------

    /** What a full-resolution photograph actually contains. */
    data class Measurement(
        val p5: Float,
        val p95: Float,
        val clippedShadows: Float,
        val clippedHighlights: Float,
        val meanR: Float,
        val meanG: Float,
        val meanB: Float,
    )

    /**
     * Measure a captured photograph.
     *
     * The viewfinder statistics describe a 640x480 preview; the levels and
     * white balance applied to a 12-megapixel file have to come from the file
     * itself. [step] subsamples, since a histogram does not need every pixel.
     */
    fun measure(pixels: IntArray, width: Int, height: Int, step: Int = 8): Measurement {
        val count = width * height
        if (count <= 0 || pixels.isEmpty()) {
            return Measurement(0f, 255f, 0f, 0f, 128f, 128f, 128f)
        }

        val histogram = IntArray(256)
        var sumR = 0L
        var sumG = 0L
        var sumB = 0L
        var samples = 0
        var shadowPixels = 0
        var highlightPixels = 0

        val stride = step.coerceAtLeast(1)
        var y = 0
        while (y < height) {
            val row = y * width
            var x = 0
            while (x < width) {
                val index = row + x
                if (index >= pixels.size) break
                val pixel = pixels[index]
                val r = pixel ushr 16 and 0xFF
                val g = pixel ushr 8 and 0xFF
                val b = pixel and 0xFF
                sumR += r
                sumG += g
                sumB += b
                val luma = (r * 299 + g * 587 + b * 114) / 1000
                histogram[luma.coerceIn(0, 255)]++
                if (luma <= 4) shadowPixels++
                if (luma >= 251) highlightPixels++
                samples++
                x += stride
            }
            y += stride
        }

        if (samples == 0) return Measurement(0f, 255f, 0f, 0f, 128f, 128f, 128f)

        return Measurement(
            p5 = percentileOf(histogram, samples, 0.05f),
            p95 = percentileOf(histogram, samples, 0.95f),
            clippedShadows = shadowPixels.toFloat() / samples,
            clippedHighlights = highlightPixels.toFloat() / samples,
            meanR = sumR.toFloat() / samples,
            meanG = sumG.toFloat() / samples,
            meanB = sumB.toFloat() / samples,
        )
    }

    private fun percentileOf(histogram: IntArray, count: Int, fraction: Float): Float {
        val wanted = (count * fraction).toInt().coerceIn(0, count - 1)
        var running = 0
        for (value in histogram.indices) {
            running += histogram[value]
            if (running > wanted) return value.toFloat()
        }
        return 255f
    }

    // -----------------------------------------------------------------
    // 3. Doing it
    // -----------------------------------------------------------------

    /**
     * The 256-entry tone curve for a plan.
     *
     * Exposed so it can be tested directly: a broken curve is the fastest way
     * to ruin every photograph the app takes.
     */
    fun toneCurve(plan: EnhancementPlan): IntArray {
        val lut = IntArray(256)
        val black = plan.blackPoint.coerceIn(0, 254)
        val white = plan.whitePoint.coerceIn(black + 1, 255)
        val span = (white - black).toFloat()

        for (value in 0..255) {
            var t = ((value - black) / span).coerceIn(0f, 1f)

            // Lift shadows with a gamma below 1.
            if (plan.shadowLift > 0f) {
                t = t.pow(1f - 0.45f * plan.shadowLift.coerceIn(0f, 1f))
            }

            // Soft-knee compression to bring highlight detail back.
            if (plan.highlightRecovery > 0f && t > HIGHLIGHT_KNEE) {
                val over = (t - HIGHLIGHT_KNEE) / (1f - HIGHLIGHT_KNEE)
                val compressed = over * (1f - 0.55f * plan.highlightRecovery.coerceIn(0f, 1f))
                t = HIGHLIGHT_KNEE + compressed * (1f - HIGHLIGHT_KNEE)
            }

            lut[value] = (t * 255f).roundToInt().coerceIn(0, 255)
        }
        return lut
    }

    /**
     * Apply [plan] to [pixels] in place. ARGB, as produced by
     * `Bitmap.getPixels`.
     *
     * Works in place and reuses byte buffers for the clarity pass, because a
     * 12-megapixel photograph does not leave room to copy it several times.
     */
    fun apply(pixels: IntArray, width: Int, height: Int, plan: EnhancementPlan) {
        if (width <= 0 || height <= 0 || pixels.isEmpty() || plan.isNoOp) return
        val expected = width * height
        require(pixels.size >= expected) {
            "pixels array holds ${pixels.size}, need $expected for ${width}x$height"
        }

        val lut = toneCurve(plan)
        val wantsClarity = plan.clarity > 0f && plan.clarityRadius > 0
        val luma = if (wantsClarity) ByteArray(expected) else null

        // --- Pass 1: white balance, tone curve, vibrance ---
        for (i in 0 until expected) {
            val pixel = pixels[i]
            val a = pixel ushr 24 and 0xFF
            var r = pixel ushr 16 and 0xFF
            var g = pixel ushr 8 and 0xFF
            var b = pixel and 0xFF

            if (plan.gainR != 1f) r = (r * plan.gainR).roundToInt().coerceIn(0, 255)
            if (plan.gainG != 1f) g = (g * plan.gainG).roundToInt().coerceIn(0, 255)
            if (plan.gainB != 1f) b = (b * plan.gainB).roundToInt().coerceIn(0, 255)

            r = lut[r]
            g = lut[g]
            b = lut[b]

            if (plan.vibrance > 0f) {
                val maxC = max(r, max(g, b))
                val minC = min(r, min(g, b))
                val chroma = maxC - minC
                if (chroma > 0) {
                    // Boost dull colours more than already-saturated ones, so
                    // skies gain and red shirts don't turn to neon.
                    val saturation = chroma.toFloat() / maxC
                    val amount = plan.vibrance * (1f - saturation)
                    val grey = (r * 0.299f + g * 0.587f + b * 0.114f)
                    r = (grey + (r - grey) * (1f + amount)).roundToInt().coerceIn(0, 255)
                    g = (grey + (g - grey) * (1f + amount)).roundToInt().coerceIn(0, 255)
                    b = (grey + (b - grey) * (1f + amount)).roundToInt().coerceIn(0, 255)
                }
            }

            pixels[i] = (a shl 24) or (r shl 16) or (g shl 8) or b
            if (luma != null) {
                luma[i] = ((r * 299 + g * 587 + b * 114) / 1000).coerceIn(0, 255).toByte()
            }
        }

        // --- Pass 2: clarity, via an unsharp mask on brightness only ---
        if (luma != null) {
            val blurred = ByteArray(expected)
            boxBlur(luma, blurred, width, height, plan.clarityRadius)
            applyClarity(pixels, luma, blurred, width, height, plan)
        }
    }

    /**
     * Separable box blur with a sliding window, so cost does not grow with
     * radius. Two passes: horizontal into [out], then vertical in place.
     */
    internal fun boxBlur(
        source: ByteArray,
        out: ByteArray,
        width: Int,
        height: Int,
        radius: Int,
    ) {
        val r = radius.coerceIn(1, max(1, min(width, height) / 2))
        val window = 2 * r + 1

        // Horizontal
        for (y in 0 until height) {
            val row = y * width
            var sum = 0
            // Prime the window, clamping at the edges.
            for (k in -r..r) {
                val x = k.coerceIn(0, width - 1)
                sum += source[row + x].toInt() and 0xFF
            }
            for (x in 0 until width) {
                out[row + x] = (sum / window).toByte()
                val outgoing = (x - r).coerceIn(0, width - 1)
                val incoming = (x + r + 1).coerceIn(0, width - 1)
                sum += (source[row + incoming].toInt() and 0xFF) -
                    (source[row + outgoing].toInt() and 0xFF)
            }
        }

        // Vertical, reading from out and writing back into out via a column buffer.
        val column = IntArray(height)
        for (x in 0 until width) {
            for (y in 0 until height) column[y] = out[y * width + x].toInt() and 0xFF
            var sum = 0
            for (k in -r..r) sum += column[k.coerceIn(0, height - 1)]
            for (y in 0 until height) {
                out[y * width + x] = (sum / window).toByte()
                val outgoing = (y - r).coerceIn(0, height - 1)
                val incoming = (y + r + 1).coerceIn(0, height - 1)
                sum += column[incoming] - column[outgoing]
            }
        }
    }

    private fun applyClarity(
        pixels: IntArray,
        luma: ByteArray,
        blurred: ByteArray,
        width: Int,
        height: Int,
        plan: EnhancementPlan,
    ) {
        val regions = plan.protectedRegions
        for (y in 0 until height) {
            val ny = (y + 0.5f) / height
            val row = y * width
            for (x in 0 until width) {
                val index = row + x
                val detail = (luma[index].toInt() and 0xFF) - (blurred[index].toInt() and 0xFF)
                if (detail == 0) continue

                var amount = plan.clarity
                if (regions.isNotEmpty()) {
                    val nx = (x + 0.5f) / width
                    if (regions.any { it.contains(nx, ny) }) {
                        amount *= PROTECTED_CLARITY
                    }
                }

                // Taper the effect on very strong edges to avoid halos.
                val boost = if (abs(detail) > 60) amount * 0.5f else amount
                val delta = (detail * boost).roundToInt()
                if (delta == 0) continue

                val pixel = pixels[index]
                val a = pixel ushr 24 and 0xFF
                val r = ((pixel ushr 16 and 0xFF) + delta).coerceIn(0, 255)
                val g = ((pixel ushr 8 and 0xFF) + delta).coerceIn(0, 255)
                val b = ((pixel and 0xFF) + delta).coerceIn(0, 255)
                pixels[index] = (a shl 24) or (r shl 16) or (g shl 8) or b
            }
        }
    }
}
