package com.aiphotographer.intelligence.imaging

import com.aiphotographer.intelligence.ColorStats
import com.aiphotographer.intelligence.LUMA_GRID
import com.aiphotographer.intelligence.LumaStats

/**
 * One plane of a YUV camera frame, described without any Android types so it
 * can be built in a test as easily as from an `ImageProxy`.
 */
class PlaneData(
    val bytes: ByteArray,
    val rowStride: Int,
    val pixelStride: Int,
)

/**
 * Turns a raw camera frame into the brightness and colour statistics the
 * engine reasons about.
 *
 * This runs on every analysed frame, so it samples rather than reading every
 * pixel, and it is pure Kotlin so the maths can be tested without a camera.
 */
object LumaSampler {

    /** Pixels at or below this are crushed black. */
    const val SHADOW_CLIP = 4

    /** Pixels at or above this are blown white. */
    const val HIGHLIGHT_CLIP = 251

    /**
     * Read [yPlane] (and the chroma planes, when given) into statistics.
     *
     * [rotationDegrees] is how far the frame must be rotated clockwise to
     * appear upright. The grid is built in upright coordinates, so "the top of
     * the frame" means what the user sees at the top, whichever way the sensor
     * is mounted.
     *
     * [sampleStep] trades accuracy for speed: 4 reads one pixel in sixteen,
     * which is plenty for regional means and cheap enough for every frame.
     */
    fun sample(
        yPlane: PlaneData,
        width: Int,
        height: Int,
        rotationDegrees: Int = 0,
        uPlane: PlaneData? = null,
        vPlane: PlaneData? = null,
        sampleStep: Int = 4,
        grid: Int = LUMA_GRID,
    ): Pair<LumaStats, ColorStats> {
        require(grid > 0) { "grid must be positive" }
        if (width <= 0 || height <= 0) return emptyResult(grid)

        val step = sampleStep.coerceAtLeast(1)
        val histogram = IntArray(256)
        val regionSums = LongArray(grid * grid)
        val regionCounts = IntArray(grid * grid)

        val rotation = ((rotationDegrees % 360) + 360) % 360
        val upright = rotation == 90 || rotation == 270
        val displayWidth = if (upright) height else width
        val displayHeight = if (upright) width else height

        var total = 0L
        var count = 0

        var y = 0
        while (y < height) {
            val rowBase = y * yPlane.rowStride
            var x = 0
            while (x < width) {
                val index = rowBase + x * yPlane.pixelStride
                if (index < 0 || index >= yPlane.bytes.size) {
                    x += step
                    continue
                }
                val value = yPlane.bytes[index].toInt() and 0xFF
                histogram[value]++
                total += value
                count++

                // Where does this pixel sit once the frame is upright?
                val dx: Int
                val dy: Int
                when (rotation) {
                    90 -> { dx = height - 1 - y; dy = x }
                    180 -> { dx = width - 1 - x; dy = height - 1 - y }
                    270 -> { dx = y; dy = width - 1 - x }
                    else -> { dx = x; dy = y }
                }
                val col = (dx.toLong() * grid / displayWidth).toInt().coerceIn(0, grid - 1)
                val row = (dy.toLong() * grid / displayHeight).toInt().coerceIn(0, grid - 1)
                val cell = row * grid + col
                regionSums[cell] += value
                regionCounts[cell]++

                x += step
            }
            y += step
        }

        if (count == 0) return emptyResult(grid)

        val mean = total.toFloat() / count
        val p5 = percentile(histogram, count, 0.05f)
        val p95 = percentile(histogram, count, 0.95f)

        var shadowPixels = 0
        for (v in 0..SHADOW_CLIP) shadowPixels += histogram[v]
        var highlightPixels = 0
        for (v in HIGHLIGHT_CLIP..255) highlightPixels += histogram[v]

        val regions = List(grid * grid) { cell ->
            if (regionCounts[cell] == 0) mean
            else regionSums[cell].toFloat() / regionCounts[cell]
        }

        val luma = LumaStats(
            mean = mean,
            p5 = p5,
            p95 = p95,
            clippedShadows = shadowPixels.toFloat() / count,
            clippedHighlights = highlightPixels.toFloat() / count,
            regions = regions,
            grid = grid,
        )

        val color = sampleColor(mean, width, height, uPlane, vPlane, step)
        return luma to color
    }

    /**
     * Average colour, approximated from the mean chroma of the frame.
     *
     * We only need this accurate enough to tell warm light from cool light, so
     * averaging U and V and converting once is far cheaper than converting
     * every pixel - and gives the same answer for our purposes.
     */
    private fun sampleColor(
        meanY: Float,
        width: Int,
        height: Int,
        uPlane: PlaneData?,
        vPlane: PlaneData?,
        step: Int,
    ): ColorStats {
        if (uPlane == null || vPlane == null) {
            return ColorStats(meanY, meanY, meanY)
        }
        // Chroma planes in 4:2:0 are half resolution in each direction.
        val chromaWidth = width / 2
        val chromaHeight = height / 2
        if (chromaWidth <= 0 || chromaHeight <= 0) {
            return ColorStats(meanY, meanY, meanY)
        }

        var uTotal = 0L
        var vTotal = 0L
        var samples = 0

        var cy = 0
        while (cy < chromaHeight) {
            val uRow = cy * uPlane.rowStride
            val vRow = cy * vPlane.rowStride
            var cx = 0
            while (cx < chromaWidth) {
                val uIndex = uRow + cx * uPlane.pixelStride
                val vIndex = vRow + cx * vPlane.pixelStride
                if (uIndex in uPlane.bytes.indices && vIndex in vPlane.bytes.indices) {
                    uTotal += uPlane.bytes[uIndex].toInt() and 0xFF
                    vTotal += vPlane.bytes[vIndex].toInt() and 0xFF
                    samples++
                }
                cx += step
            }
            cy += step
        }

        if (samples == 0) return ColorStats(meanY, meanY, meanY)

        val u = uTotal.toFloat() / samples - 128f
        val v = vTotal.toFloat() / samples - 128f

        // Standard BT.601 YUV to RGB.
        val r = (meanY + 1.402f * v).coerceIn(0f, 255f)
        val g = (meanY - 0.344f * u - 0.714f * v).coerceIn(0f, 255f)
        val b = (meanY + 1.772f * u).coerceIn(0f, 255f)
        return ColorStats(r, g, b)
    }

    /** The brightness value below which [fraction] of sampled pixels fall. */
    internal fun percentile(histogram: IntArray, count: Int, fraction: Float): Float {
        if (count <= 0) return 0f
        val wanted = (count * fraction).toInt().coerceIn(0, count - 1)
        var running = 0
        for (value in histogram.indices) {
            running += histogram[value]
            if (running > wanted) return value.toFloat()
        }
        return 255f
    }

    private fun emptyResult(grid: Int): Pair<LumaStats, ColorStats> =
        LumaStats(
            mean = 0f, p5 = 0f, p95 = 0f,
            clippedShadows = 0f, clippedHighlights = 0f,
            regions = List(grid * grid) { 0f },
            grid = grid,
        ) to ColorStats(0f, 0f, 0f)
}
