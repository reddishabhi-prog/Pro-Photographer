package com.aiphotographer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Image
import com.aiphotographer.R
import com.aiphotographer.intelligence.CameraState
import com.aiphotographer.intelligence.SceneType
import kotlin.math.roundToInt

private val HudAmber = Color(0xFFFFC46B)
private val HudMuted = Color(0xFFB8AFC2)

/**
 * What ShotMuse is doing, live, above the viewfinder.
 *
 * Line one: what it sees, and the real numbers the camera is using right now
 * (read back from the hardware every frame, not what we asked for). Line two:
 * the decisions it has made, in plain words. Nothing here asks the user to do
 * anything - it is there so the app's work is visible.
 */
@Composable
fun ShotHud(
    scene: SceneType?,
    camera: CameraState?,
    appliedEv: Float,
    actions: List<String>,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(painterResource(R.drawable.ic_launcher_foreground), null, Modifier.size(26.dp))
            Text(
                text = scene?.let(::sceneName) ?: "Reading the scene…",
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
            )
            Spacer(Modifier.weight(1f))
            if (camera != null) {
                Readout("ISO", camera.iso.toString())
                Readout("SHUTTER", shutterText(camera.exposureTimeNs))
                Readout("EV", if (kotlin.math.abs(appliedEv) < 0.05f) "0" else formatStops(appliedEv))
            }
        }
        if (actions.isNotEmpty()) {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                actions.forEach { ActionChip(it) }
            }
        }
    }
}

@Composable
private fun Readout(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(start = 12.dp)) {
        Text(label, color = HudMuted, fontSize = 9.sp, letterSpacing = 0.8.sp)
        Text(value, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun ActionChip(text: String) {
    Text(
        text = text,
        color = HudAmber,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .border(1.dp, HudAmber.copy(alpha = 0.5f), RoundedCornerShape(50))
            .background(HudAmber.copy(alpha = 0.08f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

fun sceneName(scene: SceneType): String = when (scene) {
    SceneType.PORTRAIT -> "Portrait"
    SceneType.GROUP -> "Group"
    SceneType.FULL_BODY -> "Full body"
    SceneType.CLOSEUP -> "Close-up"
    SceneType.LANDSCAPE -> "Landscape"
    SceneType.ARCHITECTURE -> "Architecture"
    SceneType.FOOD -> "Food"
    SceneType.PET -> "Pet"
    SceneType.OBJECT -> "Product"
    SceneType.NIGHT -> "Night"
    SceneType.DOCUMENT -> "Document"
    SceneType.UNKNOWN -> "Auto"
}

/** Shutter speed the way a camera prints it: 1/250, or 0.5s when slow. */
fun shutterText(exposureTimeNs: Long): String {
    val seconds = exposureTimeNs / 1_000_000_000.0
    return if (seconds >= 0.3) "${(seconds * 10).roundToInt() / 10.0}s"
    else "1/${(1.0 / seconds.coerceAtLeast(1e-6)).roundToInt()}"
}
