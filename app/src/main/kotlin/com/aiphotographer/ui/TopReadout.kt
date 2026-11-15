package com.aiphotographer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aiphotographer.CameraUiState
import com.aiphotographer.intelligence.LightingType
import com.aiphotographer.intelligence.SceneType
import com.aiphotographer.ui.theme.Amber
import com.aiphotographer.ui.theme.Good
import com.aiphotographer.ui.theme.Smoke

/**
 * The readout along the top: what the app thinks it is looking at, and what
 * the camera is actually set to.
 *
 * Showing the real ISO and shutter speed is a deliberate choice. It is how the
 * app earns trust with anyone who knows photography, and it teaches everyone
 * else what those numbers mean by letting them watch the numbers change as the
 * light does.
 */
@Composable
fun TopReadout(
    state: CameraUiState,
    modifier: Modifier = Modifier,
) {
    val guidance = state.guidance

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (guidance != null) {
            Chip(
                text = guidance.scene.label(),
                tint = Amber,
            )

            val lighting = guidance.lighting.type
            if (lighting != LightingType.GOOD) {
                Chip(text = lighting.label(), tint = lighting.tint())
            }

            if (guidance.settings.useHdr) {
                Chip(text = "HDR", tint = Good)
            }
        }
    }
}

/** The exposure numbers, shown as a camera would show them. */
@Composable
fun ExposureReadout(
    state: CameraUiState,
    modifier: Modifier = Modifier,
) {
    val settings = state.guidance?.settings ?: return

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color.Black.copy(alpha = 0.55f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.End,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Value("ISO", settings.iso.toString())
            Value("SHUTTER", "1/${settings.shutterDenominator}")
            if (settings.evCompensation != 0f) {
                Value("EV", formatStops(settings.evCompensation))
            }
        }
        if (state.hardwareNote != null) {
            Text(
                text = state.hardwareNote,
                color = Smoke,
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

@Composable
private fun Value(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            color = Smoke,
            style = MaterialTheme.typography.labelMedium,
        )
        Text(
            text = value,
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun Chip(text: String, tint: Color) {
    Text(
        text = text,
        color = tint,
        fontWeight = FontWeight.Medium,
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Color.Black.copy(alpha = 0.55f))
            .padding(horizontal = 11.dp, vertical = 6.dp),
    )
}

/** Exposure compensation, written the way a camera writes it. */
fun formatStops(stops: Float): String {
    val sign = if (stops >= 0f) "+" else "-"
    val magnitude = kotlin.math.abs(stops)
    val whole = magnitude.toInt()
    val fraction = magnitude - whole
    val fractionText = when {
        fraction < 0.08f -> ""
        fraction < 0.42f -> "⅓"
        fraction < 0.58f -> "½"
        fraction < 0.92f -> "⅔"
        else -> ""
    }
    val wholeText = if (whole == 0 && fractionText.isNotEmpty()) "" else whole.toString()
    val rounded = if (fraction >= 0.92f) (whole + 1).toString() else wholeText
    return "$sign$rounded$fractionText"
}

private fun SceneType.label(): String = when (this) {
    SceneType.PORTRAIT -> "PORTRAIT"
    SceneType.CLOSEUP -> "CLOSE-UP"
    SceneType.FULL_BODY -> "FULL BODY"
    SceneType.GROUP -> "GROUP"
    SceneType.LANDSCAPE -> "LANDSCAPE"
    SceneType.ARCHITECTURE -> "ARCHITECTURE"
    SceneType.FOOD -> "FOOD"
    SceneType.PET -> "PET"
    SceneType.OBJECT -> "OBJECT"
    SceneType.NIGHT -> "NIGHT"
    SceneType.DOCUMENT -> "DOCUMENT"
    SceneType.UNKNOWN -> "AUTO"
}

private fun LightingType.label(): String = when (this) {
    LightingType.GOOD -> "GOOD LIGHT"
    LightingType.BACKLIT -> "BACKLIT"
    LightingType.HARSH -> "HARSH SUN"
    LightingType.FLAT -> "FLAT LIGHT"
    LightingType.LOW_LIGHT -> "LOW LIGHT"
    LightingType.MIXED -> "MIXED LIGHT"
    LightingType.GOLDEN -> "GOLDEN HOUR"
}

private fun LightingType.tint(): Color = when (this) {
    LightingType.GOLDEN -> Amber
    LightingType.BACKLIT, LightingType.LOW_LIGHT, LightingType.HARSH ->
        com.aiphotographer.ui.theme.Critical
    else -> Smoke
}
