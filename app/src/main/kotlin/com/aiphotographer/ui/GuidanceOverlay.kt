package com.aiphotographer.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import com.aiphotographer.intelligence.BodyFraming
import com.aiphotographer.intelligence.Guidance
import com.aiphotographer.intelligence.Landmark
import com.aiphotographer.intelligence.NormPoint
import com.aiphotographer.intelligence.PoseLibrary
import com.aiphotographer.intelligence.overlay.PoseOverlayGeometry
import com.aiphotographer.ui.theme.Amber
import com.aiphotographer.ui.theme.Good
import kotlin.math.abs

/**
 * Everything drawn on top of the live camera feed.
 *
 * The hard constraint is that the user must still be able to see their
 * photograph through all of this, so every line is thin, translucent, and
 * there for a reason. Nothing decorative gets drawn.
 */
@Composable
fun GuidanceOverlay(
    guidance: Guidance?,
    livePose: Map<Landmark, NormPoint>,
    showGrid: Boolean,
    showPoseGuide: Boolean,
    showLevel: Boolean,
    mirrored: Boolean,
    frameAspect: Float,
    rollDeg: Float,
    modifier: Modifier = Modifier,
    /** The pose to draw: one the user pinned, else the engine's choice. */
    pose: com.aiphotographer.intelligence.PoseTemplate? = PoseLibrary.byId(guidance?.poseId),
) {
    // The canvas is laid out at exactly the camera frame's shape (see
    // CameraScreen), so 0..1 frame coordinates map straight onto it.
    Canvas(modifier = modifier) {
        if (showGrid) drawThirdsGrid()

        if (showPoseGuide && pose != null) {
            drawPoseGuide(pose, guidance, livePose, mirrored, frameAspect)
            drawLivePose(livePose, mirrored)
        }
        if (showLevel) drawLevelIndicator(rollDeg)
    }
}

/** Convert dp to pixels inside a draw pass. */
private fun DrawScope.dp(value: Float): Float = value * density


/** Rule of thirds: the oldest and most useful grid there is. */
private fun DrawScope.drawThirdsGrid() {
    val line = Color.White.copy(alpha = 0.22f)
    val thickness = dp(1f)
    for (i in 1..2) {
        val x = size.width * i / 3f
        drawLine(line, Offset(x, 0f), Offset(x, size.height), strokeWidth = thickness)
        val y = size.height * i / 3f
        drawLine(line, Offset(0f, y), Offset(size.width, y), strokeWidth = thickness)
    }
}

/**
 * The pose outline to match: a ghost standing where the subject should stand,
 * in the shape they should take.
 */
private fun DrawScope.drawPoseGuide(
    pose: com.aiphotographer.intelligence.PoseTemplate,
    guidance: Guidance?,
    livePose: Map<Landmark, NormPoint>,
    mirrored: Boolean,
    frameAspect: Float,
) {
    if (!pose.isDrawable) return

    // With someone in frame, stand the ghost where the engine wants them.
    // Without (a pose picked from the library), use the pose's own shape,
    // standing on the left third where a portrait usually sits.
    val hasPerson = guidance != null && guidance.framing != BodyFraming.UNKNOWN
    val framing = when {
        hasPerson -> guidance!!.framing
        else -> pose.framings.firstOrNull { it != BodyFraming.UNKNOWN } ?: BodyFraming.HALF
    }
    val scene = if (hasPerson) guidance!!.scene else pose.scenes.first()

    // Someone in frame: lay the outline over them. Live landmarks and the
    // template are both in camera space; build() mirrors the finished outline
    // for the selfie camera, exactly as drawLivePose mirrors the person.
    val anchored = if (livePose.size >= 3) {
        PoseOverlayGeometry.boxOnPerson(pose, livePose, framing, scene, frameAspect)
    } else {
        null
    }

    val box = anchored ?: PoseOverlayGeometry.guideBox(
        scene = scene,
        framing = framing,
        targetCenterX = if (hasPerson) guidance!!.composition.targetCenterX else 0.42f,
        targetCenterY = if (hasPerson) guidance!!.composition.targetCenterY else 0.55f,
        frameAspect = frameAspect,
    )

    val overlay = PoseOverlayGeometry.build(
        pose = pose,
        box = box,
        viewWidth = size.width,
        viewHeight = size.height,
        mirrored = mirrored,
    ) ?: return

    val guideColor = Amber.copy(alpha = 0.75f)
    val boneWidth = dp(3f)
    val dashes = PathEffect.dashPathEffect(floatArrayOf(dp(10f), dp(7f)), 0f)

    for (bone in overlay.bones) {
        drawLine(
            color = guideColor,
            start = Offset(bone.from.x, bone.from.y),
            end = Offset(bone.to.x, bone.to.y),
            strokeWidth = boneWidth,
            cap = StrokeCap.Round,
            pathEffect = dashes,
        )
    }

    // A head, which the bones alone do not describe.
    val head = overlay.joints[Landmark.NOSE]
    val shoulder = overlay.joints[Landmark.LEFT_SHOULDER]
        ?: overlay.joints[Landmark.RIGHT_SHOULDER]
    if (head != null) {
        val radius = if (shoulder != null) {
            (abs(shoulder.y - head.y) * 0.55f).coerceIn(dp(12f), dp(90f))
        } else {
            dp(24f)
        }
        drawCircle(
            color = guideColor,
            radius = radius,
            center = Offset(head.x, head.y - radius * 0.35f),
            style = Stroke(width = boneWidth, pathEffect = dashes),
        )
    }

    val jointRadius = dp(3f)
    for ((_, joint) in overlay.joints) {
        drawCircle(guideColor, jointRadius, Offset(joint.x, joint.y))
    }
}

/**
 * Where the subject actually is, drawn faintly under the guide so the user can
 * see how far they have to move to match it.
 */
private fun DrawScope.drawLivePose(livePose: Map<Landmark, NormPoint>, mirrored: Boolean) {
    if (livePose.size < 4) return
    val color = Color.White.copy(alpha = 0.30f)
    val width = dp(2f)

    fun toOffset(point: NormPoint): Offset {
        val x = if (mirrored) 1f - point.x else point.x
        return Offset(x * size.width, point.y * size.height)
    }

    for ((a, b) in PoseLibrary.BONES) {
        val from = livePose[a] ?: continue
        val to = livePose[b] ?: continue
        drawLine(color, toOffset(from), toOffset(to), strokeWidth = width, cap = StrokeCap.Round)
    }
}

/** A small crosshair where the subject's centre should sit. */
private fun DrawScope.drawTargetMarker(guidance: Guidance, mirrored: Boolean) {
    val targetX =
        if (mirrored) 1f - guidance.composition.targetCenterX
        else guidance.composition.targetCenterX
    val center = Offset(targetX * size.width, guidance.composition.targetCenterY * size.height)
    val arm = dp(9f)
    val color = if (guidance.readyToShoot) Good.copy(alpha = 0.9f) else Amber.copy(alpha = 0.9f)
    val thickness = dp(2f)

    drawLine(color, center.copy(x = center.x - arm), center.copy(x = center.x + arm), thickness)
    drawLine(color, center.copy(y = center.y - arm), center.copy(y = center.y + arm), thickness)
}

/**
 * A horizon level: one of the few things a phone knows for certain that the
 * person holding it does not.
 */
private fun DrawScope.drawLevelIndicator(rollDegrees: Float) {
    // Hidden when the phone is level: it only speaks when it has something
    // to say. (Flat-lay shots arrive here as zero; see TiltMath.hasHorizon.)
    if (abs(rollDegrees) < 1.5f) return
    val color = Amber.copy(alpha = 0.95f)
    val halfWidth = size.width * 0.18f
    val center = Offset(size.width / 2f, size.height / 2f)
    val thickness = dp(2f)

    drawLine(
        Color.White.copy(alpha = 0.25f),
        Offset(center.x - halfWidth, center.y),
        Offset(center.x + halfWidth, center.y),
        strokeWidth = thickness,
    )

    // The real horizon appears turned against the phone's roll; drawing the
    // line there shows the user how far to turn back to the faint level mark.
    rotate(degrees = -rollDegrees, pivot = center) {
        drawLine(
            color,
            Offset(center.x - halfWidth, center.y),
            Offset(center.x + halfWidth, center.y),
            strokeWidth = thickness,
        )
    }
}
