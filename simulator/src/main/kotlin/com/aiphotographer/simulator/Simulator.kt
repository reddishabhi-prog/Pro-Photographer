package com.aiphotographer.simulator

import com.aiphotographer.intelligence.Box
import com.aiphotographer.intelligence.CameraState
import com.aiphotographer.intelligence.ColorStats
import com.aiphotographer.intelligence.DeviceTilt
import com.aiphotographer.intelligence.Face
import com.aiphotographer.intelligence.FrameInput
import com.aiphotographer.intelligence.Guidance
import com.aiphotographer.intelligence.GuidanceStabilizer
import com.aiphotographer.intelligence.LUMA_GRID
import com.aiphotographer.intelligence.Landmark
import com.aiphotographer.intelligence.LumaStats
import com.aiphotographer.intelligence.NormPoint
import com.aiphotographer.intelligence.Person
import com.aiphotographer.intelligence.PhotoIntelligenceEngine
import com.aiphotographer.intelligence.SceneLabel
import java.io.File
import kotlin.math.roundToInt

/**
 * Plays the viewfinder without a phone.
 *
 * Each scene is the set of observations the camera, ML Kit and the
 * accelerometer would hand the engine for a situation a photographer would
 * recognise. The engine is the real one; only the detectors are scripted.
 */

private fun grid(fn: (row: Int, col: Int) -> Float): List<Float> =
    (0 until LUMA_GRID * LUMA_GRID).map { fn(it / LUMA_GRID, it % LUMA_GRID) }

private fun flat(v: Float) = List(LUMA_GRID * LUMA_GRID) { v }

private val UPPER_BODY = mapOf(
    Landmark.NOSE to NormPoint(0.50f, 0.32f),
    Landmark.LEFT_EYE to NormPoint(0.47f, 0.30f),
    Landmark.RIGHT_EYE to NormPoint(0.53f, 0.30f),
    Landmark.LEFT_SHOULDER to NormPoint(0.40f, 0.46f),
    Landmark.RIGHT_SHOULDER to NormPoint(0.60f, 0.46f),
    Landmark.LEFT_HIP to NormPoint(0.43f, 0.75f),
    Landmark.RIGHT_HIP to NormPoint(0.57f, 0.75f),
)

private val FULL_BODY = UPPER_BODY + mapOf(
    Landmark.LEFT_KNEE to NormPoint(0.44f, 0.86f),
    Landmark.RIGHT_KNEE to NormPoint(0.56f, 0.86f),
    Landmark.LEFT_ANKLE to NormPoint(0.45f, 0.97f),
    Landmark.RIGHT_ANKLE to NormPoint(0.55f, 0.97f),
)

private fun person(l: Float, t: Float, r: Float, b: Float, lm: Map<Landmark, NormPoint>) =
    Person(Box(l, t, r, b), 0.95f, lm, lm.keys.associateWith { 0.9f })

private fun face(l: Float, t: Float, r: Float, b: Float, yaw: Float = 0f) =
    Face(Box(l, t, r, b), eulerY = yaw)

private fun cam(iso: Int, shutter: Int) =
    CameraState(iso, 1_000_000_000L / shutter, aperture = 1.8f, focalLengthMm = 5f)

private fun labels(vararg pairs: Pair<String, Float>) = pairs.map { SceneLabel(it.first, it.second) }

data class Scene(val title: String, val story: String, val input: FrameInput)

val SCENES = listOf(
    Scene(
        "Backlit window portrait",
        "Friend standing in front of a bright window, centred, too much space above the head.",
        FrameInput(
            people = listOf(person(0.32f, 0.26f, 0.68f, 1.0f, UPPER_BODY)),
            faces = listOf(face(0.40f, 0.26f, 0.60f, 0.46f)),
            labels = labels("person" to 0.9f, "window" to 0.5f),
            luma = LumaStats(150f, 35f, 240f, 0.04f, 0.06f,
                grid { r, c -> if (r in 2..5 && c in 2..3) 55f else 215f }),
            color = ColorStats(150f, 150f, 152f),
            camera = cam(100, 500),
            tilt = DeviceTilt(rollDeg = 0.5f),
        ),
    ),
    Scene(
        "Golden-hour portrait (already good)",
        "Subject on the left third, warm low sun. The app should stay quiet.",
        FrameInput(
            people = listOf(person(0.22f, 0.10f, 0.54f, 0.74f, UPPER_BODY)),
            faces = listOf(face(0.30f, 0.10f, 0.46f, 0.26f, yaw = 5f)),
            labels = labels("person" to 0.95f, "sunset" to 0.5f),
            luma = LumaStats(132f, 42f, 205f, 0.01f, 0.02f, grid { _, c -> if (c < 3) 150f else 120f }),
            color = ColorStats(165f, 150f, 135f),
            camera = cam(100, 250),
            tilt = DeviceTilt(rollDeg = 1f),
        ),
    ),
    Scene(
        "Night street",
        "Dark street with a few lamps, phone hand-held.",
        FrameInput(
            labels = labels("street" to 0.6f, "light" to 0.5f),
            luma = LumaStats(38f, 4f, 190f, 0.22f, 0.04f, flat(38f)),
            color = ColorStats(120f, 100f, 95f),
            camera = cam(3200, 15),
        ),
    ),
    Scene(
        "Food on a café table",
        "Plate on the table, phone angled at about 60° down.",
        FrameInput(
            labels = labels("food" to 0.85f, "plate" to 0.7f, "dish" to 0.6f),
            luma = LumaStats(145f, 50f, 210f, 0.01f, 0.02f, flat(145f)),
            color = ColorStats(150f, 145f, 140f),
            camera = cam(200, 60),
            tilt = DeviceTilt(pitchDeg = -60f),
        ),
    ),
    Scene(
        "Full body, feet cut off, phone tilted",
        "Standing shot, ankles touching the frame edge, horizon 12° crooked.",
        FrameInput(
            people = listOf(person(0.30f, 0.05f, 0.70f, 1.0f, FULL_BODY)),
            faces = listOf(face(0.44f, 0.05f, 0.56f, 0.17f)),
            labels = labels("person" to 0.9f),
            luma = LumaStats(130f, 40f, 200f, 0.02f, 0.02f, flat(130f)),
            color = ColorStats(140f, 140f, 142f),
            camera = cam(100, 250),
            tilt = DeviceTilt(rollDeg = 12f),
        ),
    ),
    Scene(
        "Group of four",
        "Four friends in a row.",
        FrameInput(
            people = listOf(
                person(0.05f, 0.30f, 0.28f, 0.95f, UPPER_BODY),
                person(0.28f, 0.28f, 0.50f, 0.95f, UPPER_BODY),
                person(0.50f, 0.29f, 0.72f, 0.95f, UPPER_BODY),
                person(0.72f, 0.31f, 0.95f, 0.95f, UPPER_BODY),
            ),
            faces = listOf(
                face(0.12f, 0.30f, 0.22f, 0.42f), face(0.34f, 0.28f, 0.44f, 0.40f),
                face(0.56f, 0.29f, 0.66f, 0.41f), face(0.79f, 0.31f, 0.89f, 0.43f),
            ),
            labels = labels("person" to 0.95f, "people" to 0.9f),
            luma = LumaStats(140f, 50f, 205f, 0.01f, 0.02f, flat(140f)),
            color = ColorStats(145f, 145f, 147f),
            camera = cam(200, 125),
            tilt = DeviceTilt(rollDeg = 1f),
        ),
    ),
    Scene(
        "Harsh midday sun",
        "Portrait at noon, bright sky blowing out.",
        FrameInput(
            people = listOf(person(0.30f, 0.12f, 0.66f, 0.90f, UPPER_BODY)),
            faces = listOf(face(0.40f, 0.12f, 0.58f, 0.32f)),
            labels = labels("person" to 0.9f, "sun" to 0.5f),
            luma = LumaStats(168f, 25f, 252f, 0.05f, 0.16f, grid { r, _ -> if (r < 3) 230f else 120f }),
            color = ColorStats(150f, 150f, 152f),
            camera = cam(50, 2500),
            tilt = DeviceTilt(rollDeg = 1f),
        ),
    ),
    Scene(
        "Mountain landscape",
        "Bright sky, dark valley — big brightness range.",
        FrameInput(
            labels = labels("mountain" to 0.9f, "sky" to 0.85f, "cloud" to 0.6f),
            luma = LumaStats(120f, 12f, 246f, 0.07f, 0.06f, grid { r, _ -> if (r < 2) 225f else 70f }),
            color = ColorStats(140f, 145f, 155f),
            camera = cam(50, 1000),
            tilt = DeviceTilt(rollDeg = 0.5f),
        ),
    ),
    Scene(
        "Kid running",
        "Subject moving fast across the frame.",
        FrameInput(
            people = listOf(person(0.30f, 0.10f, 0.66f, 0.95f, FULL_BODY)),
            faces = listOf(face(0.42f, 0.10f, 0.56f, 0.26f)),
            labels = labels("person" to 0.9f),
            luma = LumaStats(140f, 45f, 210f, 0.01f, 0.02f, flat(140f)),
            color = ColorStats(145f, 145f, 147f),
            camera = cam(50, 2000),
            subjectMotion = 0.75f,
        ),
    ),
    Scene(
        "Crooked document",
        "Scanning a page, phone rotated 5°.",
        FrameInput(
            labels = labels("document" to 0.9f, "text" to 0.8f, "paper" to 0.7f),
            luma = LumaStats(200f, 120f, 250f, 0f, 0.01f, flat(200f)),
            color = ColorStats(205f, 203f, 200f),
            camera = cam(100, 100),
            tilt = DeviceTilt(rollDeg = 5f),
        ),
    ),
)

private fun shutterText(d: Int) = if (d <= 1) "1s" else "1/$d"

private fun ev(v: Float): String {
    val r = (v * 10).roundToInt() / 10f
    return if (r > 0) "+$r" else "$r"
}

/** What the screen shows, in the same order and wording as the app's HintStrip. */
fun viewfinder(g: Guidance): List<String> = buildList {
    add("Scene ${g.scene} · framing ${g.framing} · light ${g.lighting.type} from ${g.lighting.direction}")
    add("Score ${g.overallScore}/100  (composition ${g.composition.score}, light ${g.lighting.score})")
    val s = g.settings
    add("Camera → ISO ${s.iso} · ${shutterText(s.shutterDenominator)} · EV ${ev(s.evCompensation)} · WB ${s.whiteBalance} · HDR ${if (s.useHdr) "on" else "off"} · ${s.aspectRatio} · angle ${g.angle}")
    add("Why: ${s.reason}")
    g.poseId?.let { add("Pose guide: $it") }
    if (g.readyToShoot && g.hints.isEmpty()) {
        add("✅ Looks good — take it")
    } else {
        g.hints.take(3).forEach { add("[${it.priority}] ${it.text}") }
        if (g.readyToShoot) add("✅ Ready to shoot")
    }
}

/**
 * A live session: the user starts with a crooked, centred, backlit frame and
 * fixes it over ~2 seconds. Detector jitter is injected on alternate frames to
 * prove the stabiliser holds the advice steady instead of flickering.
 */
val liveFrames = mutableListOf<String>()

fun liveSession(out: MutableList<String>) {
    val stabilizer = GuidanceStabilizer()
    val base = SCENES[1].input // the golden-hour frame is the destination
    var lastShown = ""
    for (frame in 0 until 30) {
        val progress = (frame / 20f).coerceAtMost(1f)
        val roll = 14f * (1 - progress) + 1f * progress
        val shift = 0.20f * (1 - progress)
        val jitter = if (frame % 2 == 0) 0.015f else -0.015f
        val p = base.people.first()
        val input = base.copy(
            people = listOf(p.copy(box = p.box.copy(left = p.box.left + shift + jitter, right = p.box.right + shift + jitter))),
            faces = base.faces.map { it.copy(box = it.box.copy(left = it.box.left + shift, right = it.box.right + shift)) },
            tilt = DeviceTilt(rollDeg = roll),
        )
        val shown = stabilizer.update(PhotoIntelligenceEngine.analyse(input))
        liveFrames += frameJson(input, shown, "\"frame\":$frame,")
        val line = if (shown.readyToShoot && shown.hints.isEmpty()) "✅ Looks good — take it"
        else shown.hints.joinToString(" | ") { it.text }.ifEmpty { "(no hints)" }
        if (line != lastShown) {
            out += "  frame %2d (%.2fs) tilt %5.1f° score %3d → %s".format(frame, frame / 15f, roll, shown.overallScore, line)
            lastShown = line
        }
    }
}

private fun q(v: String) = "\"" + v.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
private fun box(b: Box) = "[${b.left},${b.top},${b.right},${b.bottom}]"

/** One frame as JSON: what the detectors saw and what the screen showed. */
fun frameJson(input: FrameInput, g: Guidance, extra: String = ""): String = buildString {
    append("{")
    append(extra)
    append("\"luma\":[${input.luma.regions.joinToString(",")}],")
    append("\"people\":[${input.people.joinToString(",") { box(it.box) }}],")
    append("\"faces\":[${input.faces.joinToString(",") { box(it.box) }}],")
    append("\"roll\":${input.tilt.rollDeg},\"pitch\":${input.tilt.pitchDeg},")
    append("\"warmth\":${input.color.warmth},")
    append("\"scene\":${q(g.scene.name)},\"framing\":${q(g.framing.name)},")
    append("\"light\":${q(g.lighting.type.name)},\"direction\":${q(g.lighting.direction.name)},")
    append("\"score\":${g.overallScore},\"compScore\":${g.composition.score},\"lightScore\":${g.lighting.score},")
    append("\"target\":[${g.composition.targetCenterX},${g.composition.targetCenterY}],")
    append("\"angle\":${q(g.angle.name)},\"pose\":${g.poseId?.let { q(it) } ?: "null"},")
    val st = g.settings
    append("\"iso\":${st.iso},\"shutter\":${st.shutterDenominator},\"ev\":${st.evCompensation},")
    append("\"wb\":${q(st.whiteBalance.name)},\"hdr\":${st.useHdr},\"aspect\":${q(st.aspectRatio)},\"reason\":${q(st.reason)},")
    append("\"ready\":${g.readyToShoot},")
    append("\"hints\":[${g.hints.joinToString(",") { "{\"p\":${q(it.priority.name)},\"code\":${q(it.code)},\"text\":${q(it.text)}}" }}]")
    append("}")
}

fun main() {
    val out = mutableListOf<String>()
    out += "AI PHOTOGRAPHER — DESK SIMULATION (real :intelligence engine, scripted detectors)"
    out += "=".repeat(80)
    SCENES.forEachIndexed { i, scene ->
        out += ""
        out += "${i + 1}. ${scene.title}"
        out += "   ${scene.story}"
        viewfinder(PhotoIntelligenceEngine.analyse(scene.input)).forEach { out += "   $it" }
    }
    out += ""
    out += "LIVE SESSION — user straightens the phone and reframes over 2 seconds (15 fps)"
    out += "-".repeat(80)
    out += "  Only frames where the on-screen advice changes are listed."
    liveSession(out)

    val report = out.joinToString("\n")
    println(report)
    File("build").mkdirs()
    File("build/simulation-report.txt").writeText(report + "\n")
    val scenes = SCENES.joinToString(",\n") { sc ->
        frameJson(sc.input, PhotoIntelligenceEngine.analyse(sc.input), "\"title\":${q(sc.title)},\"story\":${q(sc.story)},")
    }
    File("build/simulation.json").writeText("{\"scenes\":[\n$scenes\n],\"live\":[\n${liveFrames.joinToString(",\n")}\n]}\n")
}
