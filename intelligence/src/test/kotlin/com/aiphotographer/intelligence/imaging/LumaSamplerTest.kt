package com.aiphotographer.intelligence.imaging

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The sampler is the engine's only window onto the actual pixels. If it reads
 * the frame wrongly - wrong stride, wrong rotation - every judgement about
 * light that follows is made on fiction.
 */
class LumaSamplerTest {

    private fun plane(
        width: Int,
        height: Int,
        rowStride: Int = width,
        pixelStride: Int = 1,
        value: (x: Int, y: Int) -> Int,
    ): PlaneData {
        val bytes = ByteArray(rowStride * height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                bytes[y * rowStride + x * pixelStride] = value(x, y).toByte()
            }
        }
        return PlaneData(bytes, rowStride, pixelStride)
    }

    @Test
    fun `a flat grey frame reads as flat grey`() {
        val y = plane(16, 16) { _, _ -> 128 }
        val (luma, color) = LumaSampler.sample(y, 16, 16, sampleStep = 1)

        assertEquals(128f, luma.mean, 0.01f)
        assertTrue("no shadow clipping expected", luma.clippedShadows == 0f)
        assertTrue("no highlight clipping expected", luma.clippedHighlights == 0f)
        assertTrue("every region should read 128", luma.regions.all { it == 128f })
        // Without chroma planes we can only report neutral.
        assertEquals(128f, color.r, 0.01f)
        assertEquals(0f, color.warmth, 0.01f)
    }

    @Test
    fun `brightness is mapped to the correct side of the frame`() {
        // Left half bright, right half dark, no rotation.
        val y = plane(4, 4) { x, _ -> if (x < 2) 240 else 20 }
        val (luma, _) = LumaSampler.sample(y, 4, 4, rotationDegrees = 0, sampleStep = 1, grid = 2)

        // 2x2 grid, row-major: [topLeft, topRight, bottomLeft, bottomRight]
        assertEquals(240f, luma.regions[0], 0.01f)
        assertEquals(20f, luma.regions[1], 0.01f)
        assertEquals(240f, luma.regions[2], 0.01f)
        assertEquals(20f, luma.regions[3], 0.01f)
    }

    @Test
    fun `rotation moves brightness to where the user actually sees it`() {
        // Same frame as above, but the sensor is mounted so the frame needs a
        // 90 degree clockwise rotation to appear upright. The bright left half
        // of the sensor image becomes the TOP half of what the user sees.
        val y = plane(4, 4) { x, _ -> if (x < 2) 240 else 20 }
        val (luma, _) = LumaSampler.sample(y, 4, 4, rotationDegrees = 90, sampleStep = 1, grid = 2)

        assertEquals("top-left should be bright", 240f, luma.regions[0], 0.01f)
        assertEquals("top-right should be bright", 240f, luma.regions[1], 0.01f)
        assertEquals("bottom-left should be dark", 20f, luma.regions[2], 0.01f)
        assertEquals("bottom-right should be dark", 20f, luma.regions[3], 0.01f)
    }

    @Test
    fun `row padding does not leak into the reading`() {
        // Camera planes are often padded: rowStride is wider than the image.
        // Reading the padding would corrupt every statistic.
        val width = 5
        val height = 4
        val rowStride = 8
        val bytes = ByteArray(rowStride * height) { 0 }
        for (yy in 0 until height) {
            for (xx in 0 until width) bytes[yy * rowStride + xx] = 200.toByte()
            // leave columns 5..7 as padding zeros
        }
        val (luma, _) = LumaSampler.sample(
            PlaneData(bytes, rowStride, 1), width, height, sampleStep = 1,
        )
        assertEquals("padding must be ignored", 200f, luma.mean, 0.01f)
    }

    @Test
    fun `interleaved pixel stride is respected`() {
        // Some devices hand back a Y plane with pixelStride 2.
        val width = 4
        val height = 2
        val pixelStride = 2
        val rowStride = width * pixelStride
        val bytes = ByteArray(rowStride * height)
        for (yy in 0 until height) {
            for (xx in 0 until width) {
                bytes[yy * rowStride + xx * pixelStride] = 100.toByte()
                bytes[yy * rowStride + xx * pixelStride + 1] = 255.toByte() // filler
            }
        }
        val (luma, _) = LumaSampler.sample(
            PlaneData(bytes, rowStride, pixelStride), width, height, sampleStep = 1,
        )
        assertEquals("filler bytes must not be read", 100f, luma.mean, 0.01f)
    }

    @Test
    fun `clipping is counted at both ends`() {
        // A quarter crushed black, a quarter blown white, half mid-grey.
        val y = plane(4, 4) { x, _ ->
            when {
                x == 0 -> 0
                x == 1 -> 255
                else -> 128
            }
        }
        val (luma, _) = LumaSampler.sample(y, 4, 4, sampleStep = 1)

        assertEquals(0.25f, luma.clippedShadows, 0.001f)
        assertEquals(0.25f, luma.clippedHighlights, 0.001f)
        assertTrue("contrast should be wide, got ${luma.contrast}", luma.contrast > 200f)
    }

    @Test
    fun `percentiles bracket the data`() {
        val y = plane(32, 32) { x, _ -> (x * 8).coerceAtMost(255) }
        val (luma, _) = LumaSampler.sample(y, 32, 32, sampleStep = 1)

        assertTrue("p5 below p95", luma.p5 < luma.p95)
        assertTrue("p5 in range", luma.p5 >= 0f && luma.p5 <= 255f)
        assertTrue("p95 in range", luma.p95 >= 0f && luma.p95 <= 255f)
        assertTrue("mean between them", luma.mean > luma.p5 && luma.mean < luma.p95)
    }

    @Test
    fun `warm chroma reads as warm and cool chroma reads as cool`() {
        val y = plane(8, 8) { _, _ -> 140 }
        // V above neutral pushes red; U above neutral pushes blue.
        val warmU = plane(4, 4) { _, _ -> 128 }
        val warmV = plane(4, 4) { _, _ -> 170 }
        val (_, warm) = LumaSampler.sample(
            y, 8, 8, uPlane = warmU, vPlane = warmV, sampleStep = 1,
        )
        assertTrue("expected warm, got ${warm.warmth}", warm.warmth > 0.1f)

        val coolU = plane(4, 4) { _, _ -> 170 }
        val coolV = plane(4, 4) { _, _ -> 128 }
        val (_, cool) = LumaSampler.sample(
            y, 8, 8, uPlane = coolU, vPlane = coolV, sampleStep = 1,
        )
        assertTrue("expected cool, got ${cool.warmth}", cool.warmth < -0.1f)
    }

    @Test
    fun `an impossible frame returns zeroes instead of crashing`() {
        val (luma, color) = LumaSampler.sample(PlaneData(ByteArray(0), 0, 1), 0, 0)
        assertEquals(0f, luma.mean, 0.01f)
        assertEquals(0f, color.r, 0.01f)
        assertTrue(luma.regions.isNotEmpty())
    }

    @Test
    fun `sampling fewer pixels gives nearly the same answer`() {
        // Subsampling is what keeps this cheap enough to run every frame, so
        // it has to agree with a full read.
        val y = plane(64, 64) { x, yy -> (x + yy) * 2 }
        val (full, _) = LumaSampler.sample(y, 64, 64, sampleStep = 1)
        val (sparse, _) = LumaSampler.sample(y, 64, 64, sampleStep = 4)

        assertEquals("mean should barely move", full.mean, sparse.mean, 10f)
    }
}
