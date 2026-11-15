package com.aiphotographer.intelligence.overlay

import com.aiphotographer.intelligence.BodyFraming
import com.aiphotographer.intelligence.Box
import com.aiphotographer.intelligence.Landmark
import com.aiphotographer.intelligence.NormPoint
import com.aiphotographer.intelligence.PoseLibrary
import com.aiphotographer.intelligence.PoseTemplate
import com.aiphotographer.intelligence.SceneType

/** A point in view pixels, ready to draw. */
data class ViewPoint(val x: Float, val y: Float)

/** A line between two landmarks, in view pixels. */
data class ViewLine(val from: ViewPoint, val to: ViewPoint)

/** Everything the UI needs to draw a pose guide. */
data class PoseOverlay(
    val joints: Map<Landmark, ViewPoint>,
    val bones: List<ViewLine>,
    /** The guide box in view pixels, for drawing a halo or a target zone. */
    val boxLeft: Float,
    val boxTop: Float,
    val boxRight: Float,
    val boxBottom: Float,
)

/**
 * Places a pose outline on screen.
 *
 * The pose templates are drawn in their own 0..1 space. This works out where
 * that space should sit in the viewfinder - which depends on how much of the
 * body is in shot and where the composition rules want the subject - and then
 * converts to view pixels, mirroring for the selfie camera.
 */
object PoseOverlayGeometry {

    /** Height of the guide box as a fraction of the frame, and its width:height. */
    private fun shapeFor(framing: BodyFraming, scene: SceneType): Pair<Float, Float> =
        when {
            scene == SceneType.CLOSEUP -> 0.55f to 0.95f
            framing == BodyFraming.FULL -> 0.88f to 0.38f
            framing == BodyFraming.THREE_QUARTER -> 0.82f to 0.48f
            framing == BodyFraming.HALF -> 0.72f to 0.60f
            framing == BodyFraming.CLOSEUP -> 0.55f to 0.95f
            else -> 0.72f to 0.60f
        }

    /**
     * Where the pose guide should sit in the frame, normalised 0..1.
     *
     * Centred on the composition target, then nudged back inside the frame if
     * that would push it off an edge.
     */
    fun guideBox(
        scene: SceneType,
        framing: BodyFraming,
        targetCenterX: Float,
        targetCenterY: Float,
        frameAspect: Float = 3f / 4f,
    ): Box {
        val (heightFraction, widthToHeight) = shapeFor(framing, scene)
        val height = heightFraction.coerceIn(0.1f, 1f)
        // frameAspect is width/height of the frame, so a box that is
        // widthToHeight as wide as it is tall occupies less normalised width.
        val width = (height * widthToHeight * (1f / frameAspect).coerceAtLeast(0.1f))
            .coerceIn(0.05f, 1f)

        var left = targetCenterX - width / 2f
        var top = targetCenterY - height / 2f
        left = left.coerceIn(0f, (1f - width).coerceAtLeast(0f))
        top = top.coerceIn(0f, (1f - height).coerceAtLeast(0f))

        return Box(left, top, left + width, top + height)
    }

    /**
     * The guide box laid over the person actually in frame, so the outline
     * sits on their body and shows how to move from where they stand.
     *
     * The second phone test showed why: a ghost placed at the composition
     * target floated beside the subject, and people could not tell what to
     * match. Here the template's nose and shoulder line are pinned to the
     * live ones; only the limbs differ, which is exactly the instruction.
     * Returns null when the live pose lacks a nose or both shoulders.
     */
    fun boxOnPerson(
        pose: PoseTemplate,
        live: Map<Landmark, NormPoint>,
        framing: BodyFraming,
        scene: SceneType,
        frameAspect: Float = 3f / 4f,
    ): Box? {
        val liveNose = live[Landmark.NOSE] ?: return null
        val liveShoulder = midShoulder(live) ?: return null
        val tNose = pose.skeleton[Landmark.NOSE] ?: return null
        val tShoulder = midShoulder(pose.skeleton) ?: return null

        val templateSpan = tShoulder.y - tNose.y
        val liveSpan = liveShoulder.y - liveNose.y
        if (templateSpan <= 0.01f || liveSpan <= 0.01f) return null

        val height = liveSpan / templateSpan
        val (_, widthToHeight) = shapeFor(framing, scene)
        val width = height * widthToHeight * (1f / frameAspect).coerceAtLeast(0.1f)
        // Pin the template's shoulder midpoint onto the person's.
        val left = liveShoulder.x - tShoulder.x * width
        val top = liveShoulder.y - tShoulder.y * height
        return Box(left, top, left + width, top + height)
    }

    private fun midShoulder(points: Map<Landmark, NormPoint>): NormPoint? {
        val l = points[Landmark.LEFT_SHOULDER]
        val r = points[Landmark.RIGHT_SHOULDER]
        return when {
            l != null && r != null -> NormPoint((l.x + r.x) / 2f, (l.y + r.y) / 2f)
            else -> l ?: r
        }
    }

    /**
     * Build the drawable overlay for [pose].
     *
     * [mirrored] should be true for the front camera, whose preview is
     * flipped, so the guide matches what the user sees of themselves.
     */
    fun build(
        pose: PoseTemplate,
        box: Box,
        viewWidth: Float,
        viewHeight: Float,
        mirrored: Boolean = false,
    ): PoseOverlay? {
        if (!pose.isDrawable || viewWidth <= 0f || viewHeight <= 0f) return null

        fun toView(point: NormPoint): ViewPoint {
            val nx = box.left + point.x * box.width
            val ny = box.top + point.y * box.height
            val x = if (mirrored) 1f - nx else nx
            return ViewPoint(x * viewWidth, ny * viewHeight)
        }

        val joints = pose.skeleton.mapValues { (_, point) -> toView(point) }

        val bones = PoseLibrary.BONES.mapNotNull { (a, b) ->
            val from = joints[a]
            val to = joints[b]
            if (from != null && to != null) ViewLine(from, to) else null
        }

        val boxLeftNorm = if (mirrored) 1f - box.right else box.left
        val boxRightNorm = if (mirrored) 1f - box.left else box.right

        return PoseOverlay(
            joints = joints,
            bones = bones,
            boxLeft = boxLeftNorm * viewWidth,
            boxTop = box.top * viewHeight,
            boxRight = boxRightNorm * viewWidth,
            boxBottom = box.bottom * viewHeight,
        )
    }
}
