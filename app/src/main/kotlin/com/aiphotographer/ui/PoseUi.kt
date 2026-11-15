package com.aiphotographer.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.outlined.Accessibility
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aiphotographer.intelligence.Landmark
import com.aiphotographer.intelligence.PoseLibrary
import com.aiphotographer.intelligence.PoseTemplate

private val PoseAmber = Color(0xFFFFC46B)
private val PoseCard = Color(0xFF1C1722)
private val PoseMuted = Color(0xFFB8AFC2)

/** A pose as a little figure: the same skeleton the ghost outline draws. */
@Composable
fun StickFigure(pose: PoseTemplate, modifier: Modifier = Modifier, color: Color = PoseAmber) {
    Canvas(modifier = modifier) {
        val pad = size.minDimension * 0.08f
        val w = size.width - 2 * pad
        val h = size.height - 2 * pad
        fun at(l: Landmark) = pose.skeleton[l]?.let { Offset(pad + it.x * w, pad + it.y * h) }
        val stroke = (size.minDimension * 0.03f).coerceIn(2f * density, 4f * density)
        for ((a, b) in PoseLibrary.BONES) {
            val p = at(a) ?: continue
            val q = at(b) ?: continue
            drawLine(color, p, q, strokeWidth = stroke, cap = StrokeCap.Round)
        }
        at(Landmark.NOSE)?.let { nose ->
            val shoulder = at(Landmark.LEFT_SHOULDER) ?: at(Landmark.RIGHT_SHOULDER)
            val r = shoulder?.let { (it.y - nose.y) * 0.5f }?.coerceIn(h * 0.05f, h * 0.14f) ?: (h * 0.08f)
            drawCircle(color, r, Offset(nose.x, nose.y - r * 0.4f), style = Stroke(stroke))
        }
    }
}

/**
 * The pose suggestion under the viewfinder: what to try, why it works, and
 * buttons for the next idea, the outline, and the full library.
 */
@Composable
fun PoseStrip(
    pose: PoseTemplate?,
    pinned: Boolean,
    outlineOn: Boolean,
    onNext: () -> Unit,
    onToggleOutline: () -> Unit,
    onOpenLibrary: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (pose == null) {
        // Nobody in frame yet: offer the library instead of an empty card.
        Row(
            modifier = modifier
                .clip(RoundedCornerShape(50))
                .background(PoseCard)
                .clickable(onClick = onOpenLibrary)
                .semantics { contentDescription = "Pose ideas" }
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(Icons.Filled.AutoAwesome, null, tint = PoseAmber, modifier = Modifier.size(18.dp))
            Text("Pose ideas", color = Color.White, fontWeight = FontWeight.SemiBold)
        }
        return
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(PoseCard)
            .clickable(onClick = onOpenLibrary)
            .padding(start = 8.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        StickFigure(pose, Modifier.size(width = 40.dp, height = 54.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = (if (pinned) "Your pose · " else "Try this · ") + pose.name,
                color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(pose.why, color = PoseMuted, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        RoundIcon(Icons.Outlined.Accessibility, if (outlineOn) "Hide outline" else "Show outline", outlineOn, onToggleOutline)
        RoundIcon(Icons.Filled.SkipNext, "Next pose", false, onNext)
    }
}

@Composable
private fun RoundIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, active: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(if (active) PoseAmber else Color.White.copy(alpha = 0.08f))
            .clickable(onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = if (active) Color.Black else Color.White, modifier = Modifier.size(20.dp))
    }
}

/** Every pose ShotMuse knows, to browse and pick before anyone is in frame. */
@Composable
fun PoseLibraryContent(
    pinnedId: String?,
    onPick: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val poses = PoseLibrary.ALL.filter { it.isDrawable }
    Column(modifier = modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Pose ideas", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(
            "Pick one and ShotMuse draws it in the viewfinder for your subject to match.",
            color = PoseMuted, fontSize = 14.sp,
        )
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .border(1.dp, if (pinnedId == null) PoseAmber else Color.White.copy(alpha = 0.3f), RoundedCornerShape(50))
                .clickable { onPick(null) }
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(Icons.Filled.AutoAwesome, null, tint = PoseAmber, modifier = Modifier.size(16.dp))
            Text("Let ShotMuse choose for the scene", color = Color.White, fontSize = 14.sp)
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.height(460.dp),
        ) {
            items(poses) { pose ->
                val picked = pose.id == pinnedId
                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(18.dp))
                        .background(PoseCard)
                        .border(2.dp, if (picked) PoseAmber else Color.Transparent, RoundedCornerShape(18.dp))
                        .clickable { onPick(pose.id) }
                        .semantics { contentDescription = pose.name }
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    StickFigure(pose, Modifier.fillMaxWidth().aspectRatio(0.9f))
                    Text(pose.name, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 2)
                }
            }
        }
    }
}
