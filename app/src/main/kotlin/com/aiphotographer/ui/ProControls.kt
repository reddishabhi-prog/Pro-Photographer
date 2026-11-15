package com.aiphotographer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aiphotographer.CameraUiState
import com.aiphotographer.ManualSettings
import com.aiphotographer.intelligence.WhiteBalanceMode
import com.aiphotographer.intelligence.camera.ManualRanges
import com.aiphotographer.ui.theme.Amber
import com.aiphotographer.ui.theme.Smoke

private enum class ProControl(val label: String) {
    ISO("ISO"),
    SHUTTER("SHUTTER"),
    FOCUS("FOCUS"),
    WHITE_BALANCE("WB"),
    EV("EV"),
    ZOOM("ZOOM"),
}

/**
 * Pro mode's manual controls.
 *
 * Two rules run through this. Nothing is offered that the hardware cannot
 * actually do - the chips are built from what the sensor reports, so a dial is
 * never a lie. And locking one control does not surrender the others: a user
 * can fix the shutter speed and leave the ISO to the app, which is how people
 * really shoot.
 */
@Composable
fun ProControls(
    state: CameraUiState,
    onManualChange: ((ManualSettings) -> ManualSettings) -> Unit,
    onZoom: (Float) -> Unit,
    onExposureCompensation: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selected by remember { mutableStateOf<ProControl?>(null) }
    val manual = state.manual
    val caps = state.capabilities

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // --- The value editor for whichever control is open ---
        when (selected) {
            ProControl.ISO -> StopRow(
                options = ManualRanges.availableIsoStops(caps).map { it.toString() },
                selectedLabel = if (manual.isoAuto) null else manual.iso.toString(),
                onAuto = { onManualChange { it.copy(isoAuto = true) } },
                onPick = { label ->
                    val value = label.toIntOrNull() ?: return@StopRow
                    onManualChange { it.copy(isoAuto = false, iso = value) }
                },
            )

            ProControl.SHUTTER -> StopRow(
                options = ManualRanges.availableShutterStops(caps).map { "1/$it" },
                selectedLabel = if (manual.shutterAuto) null else "1/${manual.shutterDenominator}",
                onAuto = { onManualChange { it.copy(shutterAuto = true) } },
                onPick = { label ->
                    val value = label.removePrefix("1/").toIntOrNull() ?: return@StopRow
                    onManualChange { it.copy(shutterAuto = false, shutterDenominator = value) }
                },
            )

            ProControl.WHITE_BALANCE -> StopRow(
                options = WhiteBalanceMode.entries
                    .filter { it != WhiteBalanceMode.AUTO }
                    .map { it.label() },
                selectedLabel =
                    if (manual.whiteBalanceAuto) null else manual.whiteBalance.label(),
                onAuto = { onManualChange { it.copy(whiteBalanceAuto = true) } },
                onPick = { label ->
                    val mode = WhiteBalanceMode.entries.firstOrNull { it.label() == label }
                        ?: return@StopRow
                    onManualChange {
                        it.copy(whiteBalanceAuto = false, whiteBalance = mode)
                    }
                },
            )

            ProControl.FOCUS -> ValueSlider(
                label = if (manual.focusAuto) "Autofocus" else "Manual focus",
                value = manual.focusSlider,
                valueText = if (manual.focusAuto) "AUTO" else focusText(manual.focusSlider),
                onValueChange = { slider ->
                    onManualChange { it.copy(focusAuto = false, focusSlider = slider) }
                },
                onAuto = { onManualChange { it.copy(focusAuto = true) } },
            )

            ProControl.EV -> ValueSlider(
                label = "Exposure compensation",
                // Map the -range..+range span onto 0..1 for the slider.
                value = evToSlider(manual.evCompensation, caps.evCompensationMin, caps.evCompensationMax),
                valueText = formatStops(manual.evCompensation),
                onValueChange = { slider ->
                    val stops = sliderToEv(slider, caps.evCompensationMin, caps.evCompensationMax)
                    onExposureCompensation(ManualRanges.clampEvCompensation(stops, caps))
                },
                onAuto = { onExposureCompensation(0f) },
                autoLabel = "RESET",
            )

            ProControl.ZOOM -> ValueSlider(
                label = "Zoom",
                value = manual.zoomSlider,
                valueText = "${
                    String.format(
                        java.util.Locale.US, "%.1f",
                        ManualRanges.zoomRatio(manual.zoomSlider, caps),
                    )
                }x",
                onValueChange = onZoom,
                onAuto = { onZoom(ManualRanges.zoomSlider(1f, caps)) },
                autoLabel = "1x",
            )

            null -> Unit
        }

        // --- The chip row ---
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(ProControl.entries.toList()) { control ->
                val supported = control.isSupportedBy(state)
                ControlChip(
                    label = control.label,
                    value = control.valueText(state),
                    locked = control.isLocked(manual),
                    open = selected == control,
                    enabled = supported,
                    onClick = {
                        selected = if (selected == control) null else control
                    },
                )
            }
        }
    }
}

@Composable
private fun ControlChip(
    label: String,
    value: String,
    locked: Boolean,
    open: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val background = when {
        !enabled -> Color.Black.copy(alpha = 0.35f)
        open -> Amber.copy(alpha = 0.22f)
        else -> Color.Black.copy(alpha = 0.55f)
    }
    val valueColor = when {
        !enabled -> Smoke.copy(alpha = 0.5f)
        locked -> Amber
        else -> Color.White
    }

    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(background)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = label,
            color = if (enabled) Smoke else Smoke.copy(alpha = 0.5f),
            style = MaterialTheme.typography.labelMedium,
        )
        Text(
            text = if (enabled) value else "n/a",
            color = valueColor,
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

/** A row of discrete stops, the way a camera's dial clicks between them. */
@Composable
private fun StopRow(
    options: List<String>,
    selectedLabel: String?,
    onAuto: () -> Unit,
    onPick: (String) -> Unit,
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color.Black.copy(alpha = 0.5f))
            .padding(8.dp),
    ) {
        item {
            StopPill(text = "AUTO", selected = selectedLabel == null, onClick = onAuto)
        }
        items(options) { option ->
            StopPill(
                text = option,
                selected = option == selectedLabel,
                onClick = { onPick(option) },
            )
        }
    }
}

@Composable
private fun StopPill(text: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = text,
        color = if (selected) Color.Black else Color.White,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) Amber else Color.White.copy(alpha = 0.12f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

@Composable
private fun ValueSlider(
    label: String,
    value: Float,
    valueText: String,
    onValueChange: (Float) -> Unit,
    onAuto: () -> Unit,
    autoLabel: String = "AUTO",
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color.Black.copy(alpha = 0.5f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(label, color = Smoke, style = MaterialTheme.typography.labelMedium)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    valueText,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = autoLabel,
                    color = Amber,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable(onClick = onAuto)
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
        }
        Slider(
            value = value.coerceIn(0f, 1f),
            onValueChange = onValueChange,
            colors = SliderDefaults.colors(
                thumbColor = Amber,
                activeTrackColor = Amber,
                inactiveTrackColor = Color.White.copy(alpha = 0.22f),
            ),
        )
    }
}

// --------------------------------------------------------------------
// Small mappings
// --------------------------------------------------------------------

private fun ProControl.isSupportedBy(state: CameraUiState): Boolean = when (this) {
    ProControl.ISO -> state.capabilities.supportsManualIso
    ProControl.SHUTTER -> state.capabilities.supportsManualShutter
    ProControl.FOCUS -> state.capabilities.supportsManualFocus
    ProControl.WHITE_BALANCE -> state.capabilities.supportsManualWhiteBalance
    ProControl.EV -> state.capabilities.evCompensationMax > state.capabilities.evCompensationMin
    ProControl.ZOOM -> state.capabilities.zoomMax > state.capabilities.zoomMin
}

private fun ProControl.isLocked(manual: ManualSettings): Boolean = when (this) {
    ProControl.ISO -> !manual.isoAuto
    ProControl.SHUTTER -> !manual.shutterAuto
    ProControl.FOCUS -> !manual.focusAuto
    ProControl.WHITE_BALANCE -> !manual.whiteBalanceAuto
    ProControl.EV -> manual.evCompensation != 0f
    ProControl.ZOOM -> manual.zoomSlider != 0f
}

private fun ProControl.valueText(state: CameraUiState): String {
    val manual = state.manual
    val recommended = state.guidance?.settings
    return when (this) {
        ProControl.ISO ->
            if (manual.isoAuto) recommended?.iso?.toString() ?: "AUTO"
            else manual.iso.toString()

        ProControl.SHUTTER ->
            if (manual.shutterAuto) {
                recommended?.let { "1/${it.shutterDenominator}" } ?: "AUTO"
            } else {
                "1/${manual.shutterDenominator}"
            }

        ProControl.FOCUS -> if (manual.focusAuto) "AUTO" else focusText(manual.focusSlider)

        ProControl.WHITE_BALANCE ->
            if (manual.whiteBalanceAuto) {
                recommended?.whiteBalance?.label() ?: "AUTO"
            } else {
                manual.whiteBalance.label()
            }

        ProControl.EV -> formatStops(manual.evCompensation)

        ProControl.ZOOM -> String.format(
            java.util.Locale.US, "%.1fx",
            ManualRanges.zoomRatio(manual.zoomSlider, state.capabilities),
        )
    }
}

private fun WhiteBalanceMode.label(): String = when (this) {
    WhiteBalanceMode.AUTO -> "AUTO"
    WhiteBalanceMode.DAYLIGHT -> "SUN"
    WhiteBalanceMode.CLOUDY -> "CLOUD"
    WhiteBalanceMode.SHADE -> "SHADE"
    WhiteBalanceMode.INCANDESCENT -> "BULB"
    WhiteBalanceMode.FLUORESCENT -> "TUBE"
}

/** The focus slider runs from far to near, so describe it that way. */
private fun focusText(slider: Float): String = when {
    slider < 0.08f -> "∞"
    slider < 0.4f -> "FAR"
    slider < 0.75f -> "MID"
    else -> "MACRO"
}

private fun evToSlider(stops: Float, min: Float, max: Float): Float {
    if (max <= min) return 0.5f
    return ((stops - min) / (max - min)).coerceIn(0f, 1f)
}

private fun sliderToEv(slider: Float, min: Float, max: Float): Float {
    if (max <= min) return 0f
    return min + slider.coerceIn(0f, 1f) * (max - min)
}
