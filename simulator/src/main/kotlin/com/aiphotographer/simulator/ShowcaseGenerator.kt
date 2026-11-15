package com.aiphotographer.simulator

import com.aiphotographer.intelligence.Box
import com.aiphotographer.intelligence.CameraState
import com.aiphotographer.intelligence.ColorStats
import com.aiphotographer.intelligence.Face
import com.aiphotographer.intelligence.FrameInput
import com.aiphotographer.intelligence.LUMA_GRID
import com.aiphotographer.intelligence.Landmark
import com.aiphotographer.intelligence.LumaStats
import com.aiphotographer.intelligence.NormPoint
import com.aiphotographer.intelligence.Person
import com.aiphotographer.intelligence.PhotoIntelligenceEngine
import com.aiphotographer.intelligence.SceneLabel
import com.aiphotographer.intelligence.imaging.ImageEnhancer
import java.awt.AlphaComposite
import java.awt.BasicStroke
import java.awt.Color
import java.awt.GradientPaint
import java.awt.Graphics2D
import java.awt.MultipleGradientPaint
import java.awt.RadialGradientPaint
import java.awt.RenderingHints
import java.awt.geom.Ellipse2D
import java.awt.geom.GeneralPath
import java.awt.geom.Point2D
import java.awt.geom.RoundRectangle2D
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/**
 * Makes the onboarding showcase: six scenes, each as the phone first caught it
 * and as ShotMuse's own enhancer finished it.
 *
 * Every pixel is drawn here, so there is no third-party photograph and no
 * licence to track. The "after" is not retouched by hand: it is the "before"
 * run through the same ImageEnhancer the app runs after every shot, planned
 * from the same engine read-out. What the onboarding promises is what the
 * app does.
 *
 *   ./gradlew :simulator:generateShowcase
 */

private const val W = 720
private const val H = 960

private data class Shot(
    val id: String,
    val draw: (Graphics2D, Random) -> Unit,
    /** The camera mistake: what a phone typically gets wrong in this scene. */
    val mistake: Mistake,
    val labels: List<SceneLabel>,
    val camera: CameraState,
    val subject: Box? = null,
)

private data class Mistake(
    val exposure: Float = 1f,       // linear gain: < 1 is underexposed
    val contrast: Float = 1f,       // < 1 flattens towards mid grey
    val saturation: Float = 1f,
    val cast: Triple<Float, Float, Float> = Triple(1f, 1f, 1f),
    val haze: Float = 0f,           // lifts blacks towards grey
    val subjectDarken: Float = 1f,  // extra darkening inside the subject box (backlight)
)

fun main() {
    val out = File(System.getProperty("showcase.out") ?: "build/showcase")
    out.mkdirs()
    val previews = mutableListOf<Pair<BufferedImage, BufferedImage>>()
    for (shot in SHOTS) {
        val clean = render(shot)
        val before = applyMistake(clean, shot.mistake, shot.subject)
        val (after, summary) = enhance(before, shot)
        writeJpeg(before, File(out, "showcase_${shot.id}_before.jpg"))
        writeJpeg(after, File(out, "showcase_${shot.id}_after.jpg"))
        println("${shot.id}: $summary")
        previews += before to after
    }
    writePreview(previews, File(System.getProperty("showcase.preview") ?: "build/showcase-preview.png"))
}

// ---------------------------------------------------------------------------
// The six scenes
// ---------------------------------------------------------------------------

private val SHOTS = listOf(
    Shot(
        "people", ::drawPortrait,
        Mistake(exposure = 0.78f, contrast = 0.92f, saturation = 0.85f, subjectDarken = 0.45f),
        listOf(SceneLabel("person", 0.95f)),
        CameraState(100, 1_000_000_000L / 250, 1.8f, 5f),
        subject = Box(0.22f, 0.30f, 0.78f, 1.0f),
    ),
    Shot(
        "food", ::drawFood,
        Mistake(exposure = 0.72f, contrast = 0.85f, saturation = 0.8f, cast = Triple(1.12f, 1.0f, 0.74f)),
        listOf(SceneLabel("food", 0.9f), SceneLabel("plate", 0.8f), SceneLabel("dish", 0.7f)),
        CameraState(800, 1_000_000_000L / 60, 1.8f, 5f),
    ),
    Shot(
        "travel", ::drawLandscape,
        Mistake(exposure = 0.95f, contrast = 0.7f, saturation = 0.7f, haze = 0.18f, cast = Triple(0.96f, 0.98f, 1.06f)),
        listOf(SceneLabel("mountain", 0.9f), SceneLabel("sky", 0.85f), SceneLabel("lake", 0.6f)),
        CameraState(50, 1_000_000_000L / 1000, 1.8f, 5f),
    ),
    Shot(
        "night", ::drawNight,
        Mistake(exposure = 0.62f, contrast = 0.9f, saturation = 0.75f, haze = 0.06f),
        listOf(SceneLabel("city", 0.8f), SceneLabel("light", 0.6f)),
        CameraState(3200, 1_000_000_000L / 15, 1.8f, 5f),
    ),
    Shot(
        "products", ::drawProduct,
        Mistake(exposure = 0.82f, contrast = 0.8f, saturation = 0.7f, cast = Triple(0.92f, 0.98f, 1.1f)),
        listOf(SceneLabel("bottle", 0.8f), SceneLabel("product", 0.6f)),
        CameraState(400, 1_000_000_000L / 60, 1.8f, 5f),
    ),
    Shot(
        "pets", ::drawCat,
        Mistake(exposure = 0.7f, contrast = 0.88f, saturation = 0.8f, subjectDarken = 0.6f),
        listOf(SceneLabel("cat", 0.9f), SceneLabel("pet", 0.8f)),
        CameraState(400, 1_000_000_000L / 125, 1.8f, 5f),
        subject = Box(0.22f, 0.36f, 0.80f, 0.92f),
    ),
)

private fun render(shot: Shot): BufferedImage {
    val img = BufferedImage(W, H, BufferedImage.TYPE_INT_RGB)
    val g = img.createGraphics()
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
    shot.draw(g, Random(shot.id.hashCode()))
    g.dispose()
    addGrain(img, Random(7), 5)
    return img
}

private fun c(hex: Int, a: Int = 255) = Color((hex shr 16) and 255, (hex shr 8) and 255, hex and 255, a)

private fun vGradient(g: Graphics2D, y0: Float, y1: Float, vararg stops: Pair<Float, Int>) {
    val paint = java.awt.LinearGradientPaint(
        Point2D.Float(0f, y0), Point2D.Float(0f, y1),
        stops.map { it.first }.toFloatArray(), stops.map { c(it.second) }.toTypedArray(),
    )
    g.paint = paint
    g.fillRect(0, y0.toInt(), W, (y1 - y0).toInt() + 1)
}

private fun glow(g: Graphics2D, x: Float, y: Float, r: Float, color: Int, alpha: Int) {
    g.paint = RadialGradientPaint(
        Point2D.Float(x, y), r, floatArrayOf(0f, 1f),
        arrayOf(c(color, alpha), c(color, 0)),
    )
    g.fill(Ellipse2D.Float(x - r, y - r, 2 * r, 2 * r))
}

private fun bokeh(g: Graphics2D, rnd: Random, count: Int, yMin: Float, yMax: Float, colors: IntArray, rMin: Float, rMax: Float) {
    repeat(count) {
        val r = rMin + rnd.nextFloat() * (rMax - rMin)
        val x = rnd.nextFloat() * W
        val y = yMin + rnd.nextFloat() * (yMax - yMin)
        val col = colors[rnd.nextInt(colors.size)]
        g.color = c(col, 40 + rnd.nextInt(60))
        g.fill(Ellipse2D.Float(x - r, y - r, 2 * r, 2 * r))
        g.color = c(col, 70)
        g.stroke = BasicStroke(1.5f)
        g.draw(Ellipse2D.Float(x - r, y - r, 2 * r, 2 * r))
    }
}

/** Golden-hour portrait: the classic backlit shot phones get wrong. */
private fun drawPortrait(g: Graphics2D, rnd: Random) {
    vGradient(g, 0f, H.toFloat(), 0f to 0x2B3A67, 0.45f to 0xE58C5A, 0.62f to 0xF6C37A, 1f to 0x6B4A3A)
    glow(g, 470f, 520f, 380f, 0xFFE2A8, 210)
    bokeh(g, rnd, 26, 380f, 760f, intArrayOf(0xFFD38A, 0xFFB36B, 0xFFF0C8), 14f, 46f)
    // distant hills
    g.color = c(0x5A3E3A, 200)
    g.fill(wave(560f, 30f, 0.012f, 1.3f))
    // shoulders and shirt
    val body = GeneralPath().apply {
        moveTo(160f, 960f); curveTo(170f, 760f, 260f, 700f, 360f, 690f)
        curveTo(460f, 700f, 550f, 760f, 560f, 960f); closePath()
    }
    g.paint = GradientPaint(160f, 700f, c(0x2F6F73), 560f, 960f, c(0x16393D))
    g.fill(body)
    // neck
    g.paint = GradientPaint(330f, 600f, c(0xC98B6B), 390f, 700f, c(0x9A6248))
    g.fill(RoundRectangle2D.Float(325f, 590f, 70f, 120f, 30f, 30f))
    // face
    g.paint = RadialGradientPaint(
        Point2D.Float(395f, 470f), 170f, floatArrayOf(0f, 0.7f, 1f),
        arrayOf(c(0xE9B08F), c(0xC88566), c(0x8E5A43)),
    )
    g.fill(Ellipse2D.Float(262f, 360f, 196f, 250f))
    // hair
    val hair = GeneralPath().apply {
        moveTo(250f, 470f); curveTo(230f, 330f, 330f, 290f, 380f, 300f)
        curveTo(470f, 300f, 500f, 380f, 470f, 470f); curveTo(450f, 400f, 380f, 370f, 300f, 400f)
        curveTo(270f, 420f, 262f, 450f, 250f, 470f); closePath()
    }
    g.color = c(0x2A1B17); g.fill(hair)
    // eyes and smile, simple and warm
    g.color = c(0x3A2620)
    g.fill(Ellipse2D.Float(318f, 470f, 22f, 12f)); g.fill(Ellipse2D.Float(390f, 470f, 22f, 12f))
    g.stroke = BasicStroke(5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
    g.color = c(0x8A3F35)
    g.draw(java.awt.geom.Arc2D.Float(330f, 515f, 70f, 40f, 200f, 140f, java.awt.geom.Arc2D.OPEN))
    // rim light from the sun behind
    g.stroke = BasicStroke(6f)
    g.color = c(0xFFE0A0, 150)
    g.draw(java.awt.geom.Arc2D.Float(262f, 360f, 196f, 250f, -40f, 120f, java.awt.geom.Arc2D.OPEN))
}

/** Overhead food on a wooden table, under warm kitchen bulbs. */
private fun drawFood(g: Graphics2D, rnd: Random) {
    // planks
    for (i in 0 until 8) {
        val base = listOf(0x8B5A3C, 0x7A4E33, 0x94623F)[i % 3]
        g.color = c(base)
        g.fillRect(0, i * 120, W, 120)
        g.color = c(0x5E3A24, 120); g.fillRect(0, i * 120, W, 3)
        repeat(18) {
            g.color = c(0x4A2E1C, 30 + rnd.nextInt(40))
            val y = i * 120 + rnd.nextInt(120)
            g.fillRect(0, y, W, 1 + rnd.nextInt(2))
        }
    }
    // linen napkin
    g.color = c(0xE9E2D3); g.fill(RoundRectangle2D.Float(-40f, 640f, 300f, 360f, 30f, 30f))
    g.color = c(0xC9B99A); g.stroke = BasicStroke(3f)
    for (k in 0 until 6) g.drawLine(-40, 660 + k * 50, 260, 660 + k * 50)
    // plate shadow, plate
    g.color = c(0x000000, 70); g.fill(Ellipse2D.Float(150f, 250f, 470f, 470f))
    g.paint = RadialGradientPaint(Point2D.Float(370f, 460f), 240f, floatArrayOf(0f, 0.8f, 1f), arrayOf(c(0xFFFFFF), c(0xF1EEE8), c(0xD9D4CC)))
    g.fill(Ellipse2D.Float(130f, 220f, 460f, 460f))
    g.color = c(0xE6E1D8); g.fill(Ellipse2D.Float(190f, 280f, 340f, 340f))
    // salad: leaves, tomatoes, feta, olives
    repeat(70) {
        val a = rnd.nextFloat() * 6.283f; val d = rnd.nextFloat() * 140f
        val x = 360f + cos(a) * d; val y = 450f + sin(a) * d
        g.color = c(listOf(0x4E8B3A, 0x6FA84B, 0x3C6E2C, 0x8DBF5A)[rnd.nextInt(4)])
        val r = 18f + rnd.nextFloat() * 22f
        val leaf = Ellipse2D.Float(x - r, y - r / 2, 2 * r, r)
        val t = g.transform; g.rotate(a.toDouble(), x.toDouble(), y.toDouble()); g.fill(leaf); g.transform = t
    }
    repeat(9) {
        val a = rnd.nextFloat() * 6.283f; val d = 30f + rnd.nextFloat() * 110f
        val x = 360f + cos(a) * d; val y = 450f + sin(a) * d
        g.paint = RadialGradientPaint(Point2D.Float(x - 6, y - 6), 26f, floatArrayOf(0f, 1f), arrayOf(c(0xFF6B57), c(0xB5251B)))
        g.fill(Ellipse2D.Float(x - 18, y - 18, 36f, 36f))
        g.color = c(0xFFFFFF, 160); g.fill(Ellipse2D.Float(x - 9, y - 11, 8f, 6f))
    }
    repeat(10) {
        val x = 270f + rnd.nextFloat() * 180f; val y = 370f + rnd.nextFloat() * 170f
        g.color = c(0xF7F3E8); g.fill(RoundRectangle2D.Float(x, y, 20f, 16f, 7f, 7f))
    }
    repeat(7) {
        val x = 270f + rnd.nextFloat() * 180f; val y = 370f + rnd.nextFloat() * 170f
        g.color = c(0x2B2230); g.fill(Ellipse2D.Float(x, y, 20f, 14f))
    }
    // small dip bowl and a fork
    g.color = c(0x000000, 60); g.fill(Ellipse2D.Float(530f, 120f, 150f, 150f))
    g.color = c(0x2E5E8C); g.fill(Ellipse2D.Float(520f, 105f, 150f, 150f))
    g.color = c(0xF2E6C9); g.fill(Ellipse2D.Float(540f, 125f, 110f, 110f))
    g.color = c(0xC9CCD1); g.stroke = BasicStroke(10f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
    g.drawLine(640, 380, 660, 820)
    g.stroke = BasicStroke(4f); for (k in -1..2) g.drawLine(632 + k * 8, 300, 637 + k * 8, 380)
}

/** Mountains over a lake: a wide-range scene that comes out flat and hazy. */
private fun drawLandscape(g: Graphics2D, rnd: Random) {
    vGradient(g, 0f, 560f, 0f to 0x3E6FB0, 0.6f to 0x8DB7DE, 1f to 0xF2D9B8)
    glow(g, 520f, 420f, 220f, 0xFFF1D0, 180)
    g.color = c(0xFFF6DF); g.fill(Ellipse2D.Float(495f, 395f, 50f, 50f))
    val ranges = listOf(
        Triple(0xA9BCD4, 400f, 230f), Triple(0x7D93B2, 460f, 170f),
        Triple(0x55708F, 515f, 110f), Triple(0x34506B, 555f, 60f),
    )
    for ((i, r) in ranges.withIndex()) {
        val shape = peaks(Random(11 + i), r.second, r.third, 3 + i)
        g.color = c(r.first); g.fill(shape)
        if (i == 0) {
            // snow on the highest range only: clip to it, paint the tops
            val clip = g.clip; g.clip = shape
            g.paint = GradientPaint(0f, r.second - r.third, c(0xF4F7FB), 0f, r.second - r.third * 0.45f, c(0xF4F7FB, 0))
            g.fillRect(0, (r.second - r.third).toInt(), W, r.third.toInt()); g.clip = clip
        }
    }
    // lake with reflection
    vGradient(g, 560f, H.toFloat(), 0f to 0x2C4A66, 0.4f to 0x4E7397, 1f to 0x1C2F42)
    glow(g, 520f, 660f, 140f, 0xFFF1D0, 90)
    g.color = c(0xFFFFFF, 40)
    repeat(40) { val y = 570 + rnd.nextInt(380); g.fillRect(rnd.nextInt(W), y, 40 + rnd.nextInt(160), 2) }
    // foreground pines
    for (k in 0 until 7) {
        val x = -20f + k * 34f; val h = 260f + rnd.nextFloat() * 120f
        g.color = c(0x16241C); g.fill(pine(x, 980f, h))
    }
}

/** A city skyline at night with street-level bokeh. */
private fun drawNight(g: Graphics2D, rnd: Random) {
    vGradient(g, 0f, H.toFloat(), 0f to 0x070B1D, 0.55f to 0x1C2350, 0.75f to 0x3D2D5A, 1f to 0x0D0D16)
    repeat(70) { g.color = c(0xFFFFFF, 60 + rnd.nextInt(120)); g.fillRect(rnd.nextInt(W), rnd.nextInt(420), 2, 2) }
    g.color = c(0xF4F0E0); g.fill(Ellipse2D.Float(560f, 90f, 70f, 70f))
    glow(g, 595f, 125f, 120f, 0xCFD8FF, 60)
    var x = -10
    while (x < W) {
        val w = 60 + rnd.nextInt(70); val h = 200 + rnd.nextInt(330)
        val top = 760 - h
        g.color = c(listOf(0x121528, 0x181C33, 0x0F1222)[rnd.nextInt(3)]); g.fillRect(x, top, w, h)
        var wy = top + 14
        while (wy < 740) {
            var wx = x + 8
            while (wx < x + w - 10) {
                if (rnd.nextFloat() < 0.42f) {
                    g.color = c(listOf(0xFFD98A, 0xFFC163, 0xBFE3FF)[rnd.nextInt(3)], 160 + rnd.nextInt(90))
                    g.fillRect(wx, wy, 7, 10)
                }
                wx += 14
            }
            wy += 20
        }
        x += w + 4
    }
    // wet street and reflections
    vGradient(g, 760f, H.toFloat(), 0f to 0x1A1730, 1f to 0x07070D)
    repeat(30) {
        val rx = rnd.nextInt(W); g.color = c(listOf(0xFFC163, 0xFF5E7A, 0x7FD1FF)[rnd.nextInt(3)], 50)
        g.fillRect(rx, 770 + rnd.nextInt(40), 6, 120 + rnd.nextInt(60))
    }
    bokeh(g, rnd, 22, 820f, 960f, intArrayOf(0xFFC163, 0xFF5E7A, 0x7FD1FF, 0xFFE9B0), 20f, 60f)
}

/** A glass bottle on a plinth: a small-business product shot. */
private fun drawProduct(g: Graphics2D, rnd: Random) {
    vGradient(g, 0f, H.toFloat(), 0f to 0xF4D9C6, 0.6f to 0xE9B9A0, 1f to 0xC98F78)
    glow(g, 260f, 300f, 360f, 0xFFF4EA, 160)
    // plinth
    g.color = c(0x000000, 50); g.fill(Ellipse2D.Float(170f, 780f, 420f, 70f))
    g.paint = GradientPaint(180f, 700f, c(0xF7EDE6), 560f, 700f, c(0xD9C4B8))
    g.fillRect(180, 700, 380, 120)
    g.color = c(0xFFFDFB); g.fill(Ellipse2D.Float(180f, 670f, 380f, 64f))
    // window-blind light across the wall
    for (k in 0 until 5) {
        val t = g.transform; g.rotate(-0.35, 360.0, 300.0)
        g.color = c(0xFFFFFF, 55); g.fillRect(-200, 80 + k * 70, 1200, 30); g.transform = t
    }
    // bottle
    g.color = c(0x000000, 60); g.fill(Ellipse2D.Float(285f, 690f, 190f, 40f))
    val glass = RoundRectangle2D.Float(270f, 380f, 220f, 330f, 70f, 70f)
    g.paint = GradientPaint(270f, 0f, c(0xC77B4F, 235), 490f, 0f, c(0x8E4A2C, 235))
    g.fill(glass)
    g.paint = GradientPaint(300f, 400f, c(0xFFFFFF, 140), 340f, 400f, c(0xFFFFFF, 0))
    g.fill(RoundRectangle2D.Float(292f, 410f, 34f, 270f, 30f, 30f))
    // label: shapes only, no brand
    g.color = c(0xF6EEE6); g.fill(RoundRectangle2D.Float(310f, 500f, 140f, 120f, 12f, 12f))
    g.color = c(0x8E4A2C); g.fillRect(340, 535, 80, 8); g.fillRect(355, 555, 50, 5); g.fillRect(350, 570, 60, 5)
    // neck and cap
    g.color = c(0xB46A42); g.fill(RoundRectangle2D.Float(345f, 340f, 70f, 60f, 16f, 16f))
    g.paint = GradientPaint(330f, 0f, c(0x2A2422), 430f, 0f, c(0x5A4C46))
    g.fill(RoundRectangle2D.Float(330f, 260f, 100f, 90f, 18f, 18f))
}

/** A cat on a windowsill against bright window light. */
private fun drawCat(g: Graphics2D, rnd: Random) {
    vGradient(g, 0f, H.toFloat(), 0f to 0xBFD7E6, 0.55f to 0xF3E3C3, 1f to 0x8C6A4E)
    // window frame
    g.color = c(0xFFFFFF, 210); g.stroke = BasicStroke(26f)
    g.drawRect(60, 40, 600, 640); g.drawLine(360, 40, 360, 680); g.drawLine(60, 360, 660, 360)
    glow(g, 360f, 300f, 420f, 0xFFF7E0, 120)
    // plant in the corner
    g.color = c(0x3F7A4A)
    for (k in 0 until 9) {
        val t = g.transform; g.rotate(-1.1 + k * 0.28, 600.0, 760.0)
        g.fill(Ellipse2D.Float(585f, 560f, 34f, 200f)); g.transform = t
    }
    g.color = c(0xC56B4A); g.fill(RoundRectangle2D.Float(545f, 740f, 110f, 120f, 18f, 18f))
    // sill
    g.paint = GradientPaint(0f, 830f, c(0xA77A55), 0f, 960f, c(0x5C3E2A)); g.fillRect(0, 830, W, 130)
    // cat body (orange tabby), sitting
    val fur = GradientPaint(220f, 500f, c(0xE39A55), 520f, 880f, c(0xA35A2A))
    g.paint = fur
    g.fill(Ellipse2D.Float(200f, 560f, 330f, 320f))
    // tail
    g.stroke = BasicStroke(46f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
    g.color = c(0xC9793E)
    g.draw(GeneralPath().apply { moveTo(500f, 850f); curveTo(620f, 860f, 640f, 760f, 600f, 700f) })
    // head and ears
    g.paint = fur
    g.fill(Ellipse2D.Float(250f, 380f, 230f, 210f))
    g.fill(GeneralPath().apply { moveTo(262f, 450f); lineTo(275f, 330f); lineTo(345f, 405f); closePath() })
    g.fill(GeneralPath().apply { moveTo(468f, 450f); lineTo(455f, 330f); lineTo(385f, 405f); closePath() })
    g.color = c(0xF2B9A4)
    g.fill(GeneralPath().apply { moveTo(283f, 425f); lineTo(287f, 362f); lineTo(325f, 405f); closePath() })
    g.fill(GeneralPath().apply { moveTo(447f, 425f); lineTo(443f, 362f); lineTo(405f, 405f); closePath() })
    // stripes
    g.color = c(0x8A4A22, 170); g.stroke = BasicStroke(9f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
    for (k in 0 until 3) g.drawLine(340 + k * 25, 395, 345 + k * 22, 430)
    for (k in 0 until 5) g.drawArc(215, 600 + k * 40, 300, 140, 200, 60)
    // muzzle, eyes, nose
    g.color = c(0xF6E3D0); g.fill(Ellipse2D.Float(315f, 495f, 100f, 70f))
    g.color = c(0x6FA34A); g.fill(Ellipse2D.Float(300f, 455f, 40f, 34f)); g.fill(Ellipse2D.Float(390f, 455f, 40f, 34f))
    g.color = c(0x1A1A1A); g.fill(Ellipse2D.Float(314f, 457f, 12f, 30f)); g.fill(Ellipse2D.Float(404f, 457f, 12f, 30f))
    g.color = c(0xFFFFFF); g.fill(Ellipse2D.Float(308f, 462f, 7f, 7f)); g.fill(Ellipse2D.Float(398f, 462f, 7f, 7f))
    g.color = c(0xD9767A); g.fill(GeneralPath().apply { moveTo(352f, 505f); lineTo(378f, 505f); lineTo(365f, 520f); closePath() })
    g.color = c(0xFFFFFF, 170); g.stroke = BasicStroke(2f)
    for (k in -1..1) { g.drawLine(320, 525 + k * 8, 250, 515 + k * 14); g.drawLine(410, 525 + k * 8, 480, 515 + k * 14) }
    // rim light from the window
    g.color = c(0xFFF2D0, 140); g.stroke = BasicStroke(7f)
    g.drawArc(250, 380, 230, 210, 30, 120)
}

private fun wave(base: Float, amp: Float, freq: Float, phase: Float) = GeneralPath().apply {
    moveTo(0f, H.toFloat()); lineTo(0f, base)
    var x = 0f
    while (x <= W) { lineTo(x, base + amp * sin(x * freq + phase)); x += 8f }
    lineTo(W.toFloat(), H.toFloat()); closePath()
}

private fun peaks(rnd: Random, base: Float, height: Float, count: Int) = GeneralPath().apply {
    moveTo(0f, H.toFloat()); lineTo(0f, base - height * 0.2f)
    val step = W.toFloat() / count
    for (k in 0 until count) {
        val px = step * k + step * (0.3f + rnd.nextFloat() * 0.4f)
        val py = base - height * (0.65f + rnd.nextFloat() * 0.35f)
        // a slightly jagged climb and descent to each summit
        val sx = step * k
        for (j in 1..4) lineTo(sx + (px - sx) * j / 4f, base - (base - py) * j / 4f + (rnd.nextFloat() - 0.5f) * 12f)
        for (j in 1..4) lineTo(px + (step * (k + 1) - px) * j / 4f, py + (base - height * 0.2f - py) * j / 4f + (rnd.nextFloat() - 0.5f) * 12f)
    }
    lineTo(W.toFloat(), H.toFloat()); closePath()
}

@Suppress("unused")
private fun ridge(rnd: Random, base: Float, height: Float) = GeneralPath().apply {
    moveTo(0f, H.toFloat()); lineTo(0f, base)
    var x = 0f; var y = base
    while (x <= W) {
        y = (y + (rnd.nextFloat() - 0.5f) * height * 0.5f).coerceIn(base - height, base + height * 0.3f)
        lineTo(x, y); x += 24f
    }
    lineTo(W.toFloat(), H.toFloat()); closePath()
}

private fun pine(x: Float, bottom: Float, h: Float) = GeneralPath().apply {
    moveTo(x, bottom - h)
    for (k in 1..5) { val f = k / 5f; lineTo(x + 30f * f + 8f, bottom - h + h * f * 0.85f); lineTo(x + 10f * f, bottom - h + h * f * 0.85f) }
    lineTo(x + 6f, bottom); lineTo(x - 6f, bottom)
    for (k in 5 downTo 1) { val f = k / 5f; lineTo(x - 10f * f, bottom - h + h * f * 0.85f); lineTo(x - 30f * f - 8f, bottom - h + h * f * 0.85f) }
    closePath()
}

private fun addGrain(img: BufferedImage, rnd: Random, amount: Int) {
    for (y in 0 until img.height) for (x in 0 until img.width) {
        val p = img.getRGB(x, y); val n = rnd.nextInt(2 * amount + 1) - amount
        val r = ((p shr 16 and 255) + n).coerceIn(0, 255)
        val gg = ((p shr 8 and 255) + n).coerceIn(0, 255)
        val b = ((p and 255) + n).coerceIn(0, 255)
        img.setRGB(x, y, (r shl 16) or (gg shl 8) or b)
    }
}

// ---------------------------------------------------------------------------
// Camera mistake, then the app's real fix
// ---------------------------------------------------------------------------

private fun applyMistake(src: BufferedImage, m: Mistake, subject: Box?): BufferedImage {
    val out = BufferedImage(W, H, BufferedImage.TYPE_INT_RGB)
    for (y in 0 until H) for (x in 0 until W) {
        val p = src.getRGB(x, y)
        var r = (p shr 16 and 255) / 255f; var g = (p shr 8 and 255) / 255f; var b = (p and 255) / 255f
        var gain = m.exposure
        if (subject != null && m.subjectDarken < 1f) {
            // Soft elliptical falloff: metering for a bright window darkens
            // the subject smoothly, not inside a rectangle.
            val nx = (x / W.toFloat() - subject.centerX) / (subject.width / 2f)
            val ny = (y / H.toFloat() - subject.centerY) / (subject.height / 2f)
            val d = kotlin.math.sqrt(nx * nx + ny * ny)
            val w = ((1.25f - d) / 0.5f).coerceIn(0f, 1f)
            gain *= 1f - (1f - m.subjectDarken) * w * w * (3 - 2 * w)
        }
        // exposure in linear light, so it darkens like a sensor would
        r = lin(r) * gain * m.cast.first; g = lin(g) * gain * m.cast.second; b = lin(b) * gain * m.cast.third
        r = gam(r); g = gam(g); b = gam(b)
        val l = 0.299f * r + 0.587f * g + 0.114f * b
        r = l + (r - l) * m.saturation; g = l + (g - l) * m.saturation; b = l + (b - l) * m.saturation
        r = 0.5f + (r - 0.5f) * m.contrast; g = 0.5f + (g - 0.5f) * m.contrast; b = 0.5f + (b - 0.5f) * m.contrast
        r = r + (1 - r) * m.haze * 0.5f + m.haze * 0.15f; g = g + (1 - g) * m.haze * 0.5f + m.haze * 0.15f; b = b + (1 - b) * m.haze * 0.5f + m.haze * 0.15f
        out.setRGB(x, y, rgb(r, g, b))
    }
    return out
}

private fun lin(v: Float) = v.toDouble().pow(2.2).toFloat()
private fun gam(v: Float) = v.coerceIn(0f, 1f).toDouble().pow(1 / 2.2).toFloat()
private fun rgb(r: Float, g: Float, b: Float) =
    ((r.coerceIn(0f, 1f) * 255).roundToInt() shl 16) or ((g.coerceIn(0f, 1f) * 255).roundToInt() shl 8) or (b.coerceIn(0f, 1f) * 255).roundToInt()

/** Exactly the app's post-capture path (PhotoProcessor.enhanceInPlace). */
private fun enhance(before: BufferedImage, shot: Shot): Pair<BufferedImage, String> {
    val pixels = before.getRGB(0, 0, W, H, null, 0, W)
    val m = ImageEnhancer.measure(pixels, W, H)
    val input = frameFor(pixels, m, shot)
    val guidance = PhotoIntelligenceEngine.analyse(input)
    val faces = input.faces.map { it.box }
    var plan = ImageEnhancer.planFor(guidance, faces)
    plan = ImageEnhancer.withMeasuredLevels(plan, m.p5, m.p95, m.clippedShadows, m.clippedHighlights, m.meanR, m.meanG, m.meanB)
    plan = ImageEnhancer.withClarityRadius(plan, W, H)
    plan = ImageEnhancer.natural(plan)
    ImageEnhancer.apply(pixels, W, H, plan)
    val after = BufferedImage(W, H, BufferedImage.TYPE_INT_RGB)
    after.setRGB(0, 0, W, H, pixels, 0, W)
    return after to "${guidance.scene} / ${guidance.lighting.type}: ${plan.summary}"
}

/** What the live analysis would have reported for this frame. */
private fun frameFor(pixels: IntArray, m: ImageEnhancer.Measurement, shot: Shot): FrameInput {
    val sums = FloatArray(LUMA_GRID * LUMA_GRID); val counts = IntArray(LUMA_GRID * LUMA_GRID)
    var rs = 0.0; var gs = 0.0; var bs = 0.0; var n = 0
    for (y in 0 until H step 4) for (x in 0 until W step 4) {
        val p = pixels[y * W + x]
        val r = p shr 16 and 255; val g = p shr 8 and 255; val b = p and 255
        val cell = min(LUMA_GRID - 1, y * LUMA_GRID / H) * LUMA_GRID + min(LUMA_GRID - 1, x * LUMA_GRID / W)
        sums[cell] += 0.299f * r + 0.587f * g + 0.114f * b; counts[cell]++
        rs += r; gs += g; bs += b; n++
    }
    val regions = sums.indices.map { sums[it] / max(1, counts[it]) }
    val people = shot.subject?.takeIf { shot.id == "people" }?.let { box ->
        val lm = mapOf(
            Landmark.NOSE to NormPoint(0.51f, 0.52f), Landmark.LEFT_EYE to NormPoint(0.47f, 0.50f),
            Landmark.RIGHT_EYE to NormPoint(0.56f, 0.50f), Landmark.LEFT_SHOULDER to NormPoint(0.33f, 0.76f),
            Landmark.RIGHT_SHOULDER to NormPoint(0.69f, 0.76f),
        )
        listOf(Person(box, 0.95f, lm, lm.keys.associateWith { 0.9f }))
    } ?: emptyList()
    val faces = if (people.isNotEmpty()) listOf(Face(Box(0.36f, 0.37f, 0.64f, 0.64f))) else emptyList()
    return FrameInput(
        people = people, faces = faces, labels = shot.labels,
        luma = LumaStats(regions.average().toFloat(), m.p5, m.p95, m.clippedShadows, m.clippedHighlights, regions),
        color = ColorStats((rs / n).toFloat(), (gs / n).toFloat(), (bs / n).toFloat()),
        camera = shot.camera,
    )
}

private fun writeJpeg(img: BufferedImage, file: File) {
    val writer = ImageIO.getImageWritersByFormatName("jpg").next()
    val params = writer.defaultWriteParam.apply {
        compressionMode = ImageWriteParam.MODE_EXPLICIT; compressionQuality = 0.82f
    }
    file.outputStream().use { os ->
        ImageIO.createImageOutputStream(os).use { ios ->
            writer.output = ios
            writer.write(null, IIOImage(img, null, null), params)
        }
    }
    writer.dispose()
}

private fun writePreview(pairs: List<Pair<BufferedImage, BufferedImage>>, file: File) {
    val tw = 240; val th = 320
    val sheet = BufferedImage(tw * 4 + 30, th * 3 + 20, BufferedImage.TYPE_INT_RGB)
    val g = sheet.createGraphics()
    g.color = Color(20, 20, 24); g.fillRect(0, 0, sheet.width, sheet.height)
    g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
    pairs.forEachIndexed { i, (b, a) ->
        val col = (i % 2) * 2; val row = i / 2
        val x = col * tw + (i % 2) * 20 + 5; val y = row * th + 5 + row * 3
        g.drawImage(b, x, y, tw - 4, th - 4, null)
        g.drawImage(a, x + tw, y, tw - 4, th - 4, null)
    }
    g.composite = AlphaComposite.SrcOver
    g.dispose()
    ImageIO.write(sheet, "png", file)
}
