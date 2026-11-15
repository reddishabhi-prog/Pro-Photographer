package com.aiphotographer.intelligence.imaging

import com.aiphotographer.intelligence.Box
import com.aiphotographer.intelligence.BodyFraming
import com.aiphotographer.intelligence.CompositionAssessment
import com.aiphotographer.intelligence.Guidance
import com.aiphotographer.intelligence.LightDirection
import com.aiphotographer.intelligence.LightingAssessment
import com.aiphotographer.intelligence.LightingType
import com.aiphotographer.intelligence.SceneType
import com.aiphotographer.intelligence.SettingsRecommendation
import com.aiphotographer.intelligence.ShootingAngle
import com.aiphotographer.intelligence.WhiteBalanceMode
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The enhancement runs on every photograph the user keeps, so a mistake here
 * is permanent in their gallery. The tone curve in particular gets checked
 * directly: a non-monotonic curve produces posterised, banded skies.
 */
class ImageEnhancerTest {

    private fun guidance(
        scene: SceneType = SceneType.PORTRAIT,
        lighting: LightingType = LightingType.GOOD,
    ) = Guidance(
        scene = scene,
        framing = BodyFraming.HALF,
        lighting = LightingAssessment(lighting, LightDirection.FRONT, 80, 0f),
        composition = CompositionAssessment(85, 0.38f, 0.42f, emptyList()),
        angle = ShootingAngle.EYE_LEVEL,
        settings = SettingsRecommendation(
            100, 125, 0f, WhiteBalanceMode.AUTO, false, "4:3", "test",
        ),
        poseId = null,
        hints = emptyList(),
        overallScore = 82,
        readyToShoot = true,
    )

    private fun argb(r: Int, g: Int, b: Int, a: Int = 255): Int =
        (a shl 24) or (r shl 16) or (g shl 8) or b

    private fun red(pixel: Int) = pixel ushr 16 and 0xFF
    private fun green(pixel: Int) = pixel ushr 8 and 0xFF
    private fun blue(pixel: Int) = pixel and 0xFF
    private fun alpha(pixel: Int) = pixel ushr 24 and 0xFF

    // -------------------------------------------------------------
    // Tone curve
    // -------------------------------------------------------------

    @Test
    fun `tone curve never goes backwards`() {
        // A curve that dips produces banding and false edges in smooth areas.
        val plans = listOf(
            EnhancementPlan(blackPoint = 0, whitePoint = 255),
            EnhancementPlan(blackPoint = 12, whitePoint = 240, shadowLift = 0.8f),
            EnhancementPlan(highlightRecovery = 1f),
            EnhancementPlan(blackPoint = 40, whitePoint = 210, shadowLift = 0.5f, highlightRecovery = 0.7f),
        )
        for (plan in plans) {
            val lut = ImageEnhancer.toneCurve(plan)
            for (i in 1 until lut.size) {
                assertTrue(
                    "curve dipped at $i for $plan: ${lut[i - 1]} -> ${lut[i]}",
                    lut[i] >= lut[i - 1],
                )
            }
            assertTrue("curve left the 0..255 range", lut.all { it in 0..255 })
        }
    }

    @Test
    fun `levels map the black and white points to the ends`() {
        val lut = ImageEnhancer.toneCurve(EnhancementPlan(blackPoint = 20, whitePoint = 230))
        assertEquals("below the black point should be black", 0, lut[0])
        assertEquals("the black point itself should be black", 0, lut[20])
        assertEquals("the white point should be white", 255, lut[230])
        assertEquals("above the white point stays white", 255, lut[255])
    }

    @Test
    fun `lifting shadows brightens the dark tones and leaves white alone`() {
        val flat = ImageEnhancer.toneCurve(EnhancementPlan())
        val lifted = ImageEnhancer.toneCurve(EnhancementPlan(shadowLift = 0.9f))

        assertTrue("a dark tone should get brighter", lifted[40] > flat[40])
        assertTrue("a midtone should get brighter", lifted[128] > flat[128])
        assertEquals("white must stay white", 255, lifted[255])
        assertEquals("black must stay black", 0, lifted[0])
    }

    @Test
    fun `recovering highlights pulls the bright tones down`() {
        val flat = ImageEnhancer.toneCurve(EnhancementPlan())
        val recovered = ImageEnhancer.toneCurve(EnhancementPlan(highlightRecovery = 0.9f))

        assertTrue("a bright tone should come down", recovered[250] < flat[250])
        assertEquals("shadows must not move", flat[30], recovered[30])
    }

    // -------------------------------------------------------------
    // Pixel pipeline
    // -------------------------------------------------------------

    @Test
    fun `alpha is never touched`() {
        val pixels = IntArray(16) { argb(100, 120, 140, a = 123) }
        val plan = EnhancementPlan(
            blackPoint = 10, whitePoint = 240, shadowLift = 0.5f, vibrance = 0.4f,
        )
        ImageEnhancer.apply(pixels, 4, 4, plan)
        assertTrue("alpha changed", pixels.all { alpha(it) == 123 })
    }

    @Test
    fun `a no-op plan changes nothing at all`() {
        val original = IntArray(16) { argb(10 + it, 20 + it, 30 + it) }
        val pixels = original.copyOf()
        ImageEnhancer.apply(pixels, 4, 4, EnhancementPlan())
        assertTrue("pixels moved under a no-op plan", original.contentEquals(pixels))
    }

    @Test
    fun `grey stays grey - no colour cast is invented`() {
        val pixels = IntArray(64) { argb(128, 128, 128) }
        val plan = EnhancementPlan(
            blackPoint = 5, whitePoint = 250, shadowLift = 0.4f, vibrance = 0.5f,
        )
        ImageEnhancer.apply(pixels, 8, 8, plan)
        for (pixel in pixels) {
            assertEquals("grey gained a cast", red(pixel), green(pixel))
            assertEquals("grey gained a cast", green(pixel), blue(pixel))
        }
    }

    @Test
    fun `vibrance lifts a dull colour more than a vivid one`() {
        // The whole point of vibrance over saturation: skies and skin gain,
        // an already-red shirt does not turn to neon.
        val dull = IntArray(1) { argb(140, 130, 120) }     // barely coloured
        val vivid = IntArray(1) { argb(250, 10, 10) }       // fully saturated
        val plan = EnhancementPlan(vibrance = 0.6f)

        val dullBefore = red(dull[0]) - blue(dull[0])
        val vividBefore = red(vivid[0]) - blue(vivid[0])

        ImageEnhancer.apply(dull, 1, 1, plan)
        ImageEnhancer.apply(vivid, 1, 1, plan)

        val dullGain = (red(dull[0]) - blue(dull[0])) - dullBefore
        val vividGain = (red(vivid[0]) - blue(vivid[0])) - vividBefore

        assertTrue("the dull colour should gain, gained $dullGain", dullGain > 0)
        assertTrue(
            "the vivid colour should gain less, dull=$dullGain vivid=$vividGain",
            vividGain < dullGain,
        )
    }

    @Test
    fun `white balance gains correct a cast towards neutral`() {
        val plan = ImageEnhancer.withMeasuredLevels(
            ImageEnhancer.planFor(guidance()),
            p5 = 10f, p95 = 245f,
            clippedShadows = 0f, clippedHighlights = 0f,
            meanR = 180f, meanG = 140f, meanB = 100f, // strongly warm
        )
        assertTrue("too much red should be pulled down", plan.gainR < 1f)
        assertTrue("too little blue should be pushed up", plan.gainB > 1f)
        assertTrue("gains must stay sane", plan.gainR in 0.75f..1.35f)
        assertTrue("gains must stay sane", plan.gainB in 0.75f..1.35f)
    }

    @Test
    fun `golden light is never white balanced away`() {
        val plan = ImageEnhancer.planFor(guidance(lighting = LightingType.GOLDEN))
        assertEquals("warm light must be preserved", 0f, plan.whiteBalanceStrength, 0.001f)

        val measured = ImageEnhancer.withMeasuredLevels(
            plan,
            p5 = 10f, p95 = 245f, clippedShadows = 0f, clippedHighlights = 0f,
            meanR = 190f, meanG = 150f, meanB = 110f,
        )
        assertEquals("no red correction", 1f, measured.gainR, 0.001f)
        assertEquals("no blue correction", 1f, measured.gainB, 0.001f)
    }

    @Test
    fun `already clipped shadows are not crushed further`() {
        val plan = ImageEnhancer.withMeasuredLevels(
            ImageEnhancer.planFor(guidance()),
            p5 = 30f, p95 = 200f,
            clippedShadows = 0.2f, clippedHighlights = 0.2f,
            meanR = 128f, meanG = 128f, meanB = 128f,
        )
        assertEquals("black point must stay at zero", 0, plan.blackPoint)
        assertEquals("white point must stay at 255", 255, plan.whitePoint)
    }

    @Test
    fun `white point always stays above the black point`() {
        // A flat, low-contrast frame could otherwise invert the levels.
        val plan = ImageEnhancer.withMeasuredLevels(
            ImageEnhancer.planFor(guidance()),
            p5 = 48f, p95 = 200f,
            clippedShadows = 0f, clippedHighlights = 0f,
            meanR = 128f, meanG = 128f, meanB = 128f,
        )
        assertTrue(
            "levels inverted: ${plan.blackPoint}..${plan.whitePoint}",
            plan.whitePoint > plan.blackPoint,
        )
        // And the curve built from it must still be usable.
        val lut = ImageEnhancer.toneCurve(plan)
        assertTrue(lut.last() >= lut.first())
    }

    // -------------------------------------------------------------
    // Clarity
    // -------------------------------------------------------------

    @Test
    fun `blurring a flat image leaves it flat`() {
        val source = ByteArray(64) { 100.toByte() }
        val out = ByteArray(64)
        ImageEnhancer.boxBlur(source, out, 8, 8, radius = 2)
        assertTrue("flat input blurred unevenly", out.all { (it.toInt() and 0xFF) == 100 })
    }

    @Test
    fun `blurring spreads a bright spot into its neighbours`() {
        val width = 9
        val height = 9
        val source = ByteArray(width * height) { 0 }
        source[4 * width + 4] = 255.toByte() // single bright pixel in the centre
        val out = ByteArray(width * height)
        ImageEnhancer.boxBlur(source, out, width, height, radius = 2)

        val centre = out[4 * width + 4].toInt() and 0xFF
        val neighbour = out[4 * width + 5].toInt() and 0xFF
        val faraway = out[0].toInt() and 0xFF

        assertTrue("the spot should dim", centre < 255)
        assertTrue("the neighbour should brighten, got $neighbour", neighbour > 0)
        assertEquals("the far corner should be untouched", 0, faraway)
    }

    @Test
    fun `clarity increases contrast across an edge`() {
        // Half dark, half light. Unsharp masking should push the two sides
        // further apart right at the boundary.
        val width = 32
        val height = 8
        fun build() = IntArray(width * height) { i ->
            val x = i % width
            if (x < width / 2) argb(90, 90, 90) else argb(150, 150, 150)
        }

        val plain = build()
        val sharpened = build()
        ImageEnhancer.apply(
            sharpened, width, height,
            EnhancementPlan(clarity = 0.8f, clarityRadius = 3),
        )

        val darkEdgeIndex = 3 * width + (width / 2 - 1)
        val lightEdgeIndex = 3 * width + (width / 2)

        val plainGap = red(plain[lightEdgeIndex]) - red(plain[darkEdgeIndex])
        val sharpGap = red(sharpened[lightEdgeIndex]) - red(sharpened[darkEdgeIndex])

        assertTrue(
            "clarity should widen the edge: plain=$plainGap sharpened=$sharpGap",
            sharpGap > plainGap,
        )
    }

    @Test
    fun `protected regions are sharpened less than the rest of the frame`() {
        // Faces get held back so skin does not turn crunchy.
        val width = 32
        val height = 16
        fun build() = IntArray(width * height) { i ->
            val x = i % width
            if (x < width / 2) argb(90, 90, 90) else argb(150, 150, 150)
        }

        // Protect the top half of the frame only.
        val protectedPlan = EnhancementPlan(
            clarity = 0.8f,
            clarityRadius = 3,
            protectedRegions = listOf(Box(0f, 0f, 1f, 0.5f)),
        )
        val pixels = build()
        ImageEnhancer.apply(pixels, width, height, protectedPlan)

        val edgeX = width / 2
        val protectedGap = abs(
            red(pixels[2 * width + edgeX]) - red(pixels[2 * width + edgeX - 1])
        )
        val unprotectedGap = abs(
            red(pixels[13 * width + edgeX]) - red(pixels[13 * width + edgeX - 1])
        )

        assertTrue(
            "protected area should be softer: protected=$protectedGap free=$unprotectedGap",
            protectedGap < unprotectedGap,
        )
    }

    // -------------------------------------------------------------
    // Plans
    // -------------------------------------------------------------

    @Test
    fun `portraits are treated gently and landscapes are not`() {
        val portrait = ImageEnhancer.planFor(guidance(SceneType.PORTRAIT))
        val landscape = ImageEnhancer.planFor(guidance(SceneType.LANDSCAPE))

        assertTrue(
            "skin should get less clarity than rock: ${portrait.clarity} vs ${landscape.clarity}",
            portrait.clarity < landscape.clarity,
        )
        assertTrue(
            "skin should get less saturation than sky",
            portrait.vibrance < landscape.vibrance,
        )
    }

    @Test
    fun `backlit photographs get their shadows opened up`() {
        val backlit = ImageEnhancer.planFor(guidance(lighting = LightingType.BACKLIT))
        val normal = ImageEnhancer.planFor(guidance(lighting = LightingType.GOOD))
        assertTrue(
            "a silhouetted face needs lifting: ${backlit.shadowLift} vs ${normal.shadowLift}",
            backlit.shadowLift > normal.shadowLift,
        )
        assertTrue("and it should say so", backlit.summary.isNotEmpty())
    }

    @Test
    fun `night photographs are not sharpened into noise`() {
        val night = ImageEnhancer.planFor(guidance(SceneType.NIGHT))
        val day = ImageEnhancer.planFor(guidance(SceneType.LANDSCAPE))
        assertTrue(
            "sharpening noise only makes noise: ${night.clarity} vs ${day.clarity}",
            night.clarity < day.clarity,
        )
    }

    @Test
    fun `clarity radius scales with the image size`() {
        val small = ImageEnhancer.withClarityRadius(EnhancementPlan(), 640, 480)
        val large = ImageEnhancer.withClarityRadius(EnhancementPlan(), 4000, 3000)
        assertTrue("a big photo needs a bigger radius", large.clarityRadius > small.clarityRadius)
        assertTrue("radius must stay sane", large.clarityRadius in 1..24)
        assertTrue("radius must never be zero", small.clarityRadius >= 1)
    }

    @Test
    fun `every plan describes itself in plain words`() {
        for (scene in SceneType.entries) {
            for (light in LightingType.entries) {
                val plan = ImageEnhancer.planFor(guidance(scene, light))
                assertTrue(
                    "$scene/$light produced no summary",
                    plan.summary.isNotBlank(),
                )
                assertFalse("summary should not be a no-op plan", plan.isNoOp)
            }
        }
    }

    @Test
    fun `a mismatched pixel array is refused rather than corrupted`() {
        val pixels = IntArray(10)
        var threw = false
        try {
            ImageEnhancer.apply(pixels, 8, 8, EnhancementPlan(shadowLift = 0.5f))
        } catch (e: IllegalArgumentException) {
            threw = true
        }
        assertTrue("should refuse an undersized array", threw)
    }

    // -------------------------------------------------------------
    // Measuring the real photograph
    // -------------------------------------------------------------

    @Test
    fun `measuring a flat grey photo reports flat grey`() {
        val pixels = IntArray(64 * 64) { argb(128, 128, 128) }
        val m = ImageEnhancer.measure(pixels, 64, 64, step = 1)

        assertEquals(128f, m.meanR, 0.01f)
        assertEquals(128f, m.meanG, 0.01f)
        assertEquals(128f, m.meanB, 0.01f)
        assertEquals(0f, m.clippedShadows, 0.001f)
        assertEquals(0f, m.clippedHighlights, 0.001f)
    }

    @Test
    fun `measuring finds a colour cast`() {
        val pixels = IntArray(32 * 32) { argb(200, 150, 90) }
        val m = ImageEnhancer.measure(pixels, 32, 32, step = 1)
        assertTrue("red should dominate", m.meanR > m.meanG && m.meanG > m.meanB)
    }

    @Test
    fun `measuring counts clipping at both ends`() {
        // Half pure black, half pure white.
        val width = 32
        val height = 32
        val pixels = IntArray(width * height) { i ->
            if ((i % width) < width / 2) argb(0, 0, 0) else argb(255, 255, 255)
        }
        val m = ImageEnhancer.measure(pixels, width, height, step = 1)
        assertEquals(0.5f, m.clippedShadows, 0.02f)
        assertEquals(0.5f, m.clippedHighlights, 0.02f)
        assertTrue("p5 should be at the bottom", m.p5 < 10f)
        assertTrue("p95 should be at the top", m.p95 > 245f)
    }

    @Test
    fun `measuring a photo with unused range reports it`() {
        // A flat, hazy photograph: nothing near black, nothing near white.
        // This is exactly the case auto-levels exists to fix.
        val width = 32
        val height = 32
        val pixels = IntArray(width * height) { i ->
            val v = 80 + (i % 60) // 80..139 only
            argb(v, v, v)
        }
        val m = ImageEnhancer.measure(pixels, width, height, step = 1)
        assertTrue("p5 should sit well above black, got ${m.p5}", m.p5 > 60f)
        assertTrue("p95 should sit well below white, got ${m.p95}", m.p95 < 180f)

        val plan = ImageEnhancer.withMeasuredLevels(
            ImageEnhancer.planFor(guidance()),
            m.p5, m.p95, m.clippedShadows, m.clippedHighlights, m.meanR, m.meanG, m.meanB,
        )
        assertTrue("levels should reclaim the unused range", plan.blackPoint > 0)
        assertTrue("levels should reclaim the unused range", plan.whitePoint < 255)
    }

    @Test
    fun `measuring an empty photo does not crash`() {
        val m = ImageEnhancer.measure(IntArray(0), 0, 0)
        assertTrue(m.meanR in 0f..255f)
    }

    @Test
    fun `subsampling the measurement agrees with reading every pixel`() {
        val width = 128
        val height = 128
        val pixels = IntArray(width * height) { i ->
            val v = ((i % width) + (i / width)) and 0xFF
            argb(v, v, v)
        }
        val full = ImageEnhancer.measure(pixels, width, height, step = 1)
        val sparse = ImageEnhancer.measure(pixels, width, height, step = 8)
        // A stride misses the last few rows and columns, which on a perfect
        // linear ramp biases the mean slightly low. Real photographs are not
        // ramps, so a few levels of drift is the worst case, not the norm.
        assertEquals("mean should barely move", full.meanR, sparse.meanR, 10f)
    }

    @Test
    fun `an empty image is a harmless no-op`() {
        ImageEnhancer.apply(IntArray(0), 0, 0, EnhancementPlan(shadowLift = 1f))
        // Reaching here without an exception is the assertion.
        assertTrue(true)
    }
}
