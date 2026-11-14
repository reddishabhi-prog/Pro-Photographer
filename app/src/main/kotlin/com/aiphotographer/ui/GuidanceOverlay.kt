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
import com.aiphotographer.intelligence.CompositionAnalyzer
import com.aiphotographer.intelligence.Guidance
import com.aiphotographer.intelligence.Landmark
import com.aiphotographer.intelligence.NormPoint
import com.aiphotographer.intelligence.PoseLibrary
import com.aiphotographer.intelligence.overlay.PoseOverlayGeometry
import com.aiphotographer.ui.theme.Amber
import com.aiphotographer.ui.theme.Critical
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
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        if (showGrid) drawThirdsGrid()

        if (guidance != null) {
            if (showPoseGuide) {
                drawPoseGuide(guidance, mirrored, frameAspect)
                drawLivePose(livePose, mirrored)
            }
            drawTargetMarker(guidance, mirrored)
            if (showLevel) drawLevelIndicator(rollFrom(guidance))
        }
    }
}

/** Convert dp to pixels inside a draw pass. */
private fun DrawScope.dp(value: Float): Float = value * density

/**
 * How far off level the engine judged this frame.
 *
 * Driven by the issues the engine reported rather than a second read of the
 * sensor, so the indicator can never disagree with the advice beside it.
 */
private fun rollFrom(guidance: Guidance): Float = when {
    CompositionAnalyzer.HORIZON_TILTED in guidance.composition.issues -> 9f
    CompositionAnalyzer.HORIZON_SLIGHTLY_TILTED in guidance.composition.issues -> 4f
    else -> 0f
}

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
    guidance: Guidance,
    mirrored: Boolean,
    frameAspect: Float,
) {
    val pose = PoseLibrary.byId(guidance.poseId) ?: return
    if (!pose.isDrawable) return

    val framing =
        if (guidance.framing == BodyFraming.UNKNOWN) BodyFraming.HALF else guidance.framing

    val box = PoseOverlayGeometry.guideBox(
        scene = guidance.scene,
        framing = framing,
        targetCenterX = guidance.composition.targetCenterX,
        targetCenterY = guidance.composition.targetCenterY,
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
 * person holding it does not, which is why it earns permanent screen space.
 */
private fun DrawScope.drawLevelIndicator(rollDegrees: Float) {
    val level = abs(rollDegrees) < 1.5f
    val color = if (level) Good else Critical.copy(alpha = 0.9f)
    val halfWidth = size.width * 0.18f
    val center = Offset(size.width / 2f, size.height / 2f)
    val thickness = dp(2f)

    drawLine(
        Color.White.copy(alpha = 0.25f),
        Offset(center.x - halfWidth, center.y),
        Offset(center.x + halfWidth, center.y),
        strokeWidth = thickness,
    )

    if (level) {
        drawCircle(color, radius = dp(4f), center = center)
    } else {
        rotate(degrees = -rollDegrees, pivot = center) {
            drawLine(
                color,
                Offset(center.x - halfWidth, center.y),
                Offset(center.x + halfWidth, center.y),
                strokeWidth = thickness,
            )
        }
    }
}
