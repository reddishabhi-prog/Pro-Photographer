package com.aiphotographer.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aiphotographer.intelligence.Guidance
import com.aiphotographer.intelligence.Hint
import com.aiphotographer.intelligence.HintPriority
import com.aiphotographer.ui.theme.Amber
import com.aiphotographer.ui.theme.Critical
import com.aiphotographer.ui.theme.Good

/**
 * The coaching, in words.
 *
 * Phrasing and restraint are the product here. The engine caps this at three
 * hints and sorts the most urgent first; this just has to render them so they
 * can be read at a glance while the user is holding a camera up and looking at
 * something else.
 */
@Composable
fun HintStrip(
    guidance: Guidance?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val hints = guidance?.hints ?: emptyList()

        AnimatedVisibility(
            visible = hints.isEmpty() && guidance?.readyToShoot == true,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            HintPill(
                icon = Icons.Filled.CheckCircle,
                text = "Looks good — take it",
                tint = Good,
            )
        }

        for (hint in hints) {
            HintPill(
                icon = hint.icon(),
                text = hint.text,
                tint = hint.tint(),
            )
        }
    }
}

@Composable
private fun HintPill(
    icon: ImageVector,
    text: String,
    tint: Color,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Color.Black.copy(alpha = 0.62f))
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.padding(end = 0.dp),
        )
        Text(
            text = text,
            color = Color.White,
            fontWeight = FontWeight.Medium,
        )
    }
}

private fun Hint.icon(): ImageVector = when (priority) {
    HintPriority.CRITICAL -> Icons.Filled.WarningAmber
    HintPriority.IMPORTANT -> Icons.Filled.Lightbulb
    HintPriority.POLISH -> Icons.Filled.AutoAwesome
}

private fun Hint.tint(): Color = when (priority) {
    HintPriority.CRITICAL -> Critical
    HintPriority.IMPORTANT -> Amber
    HintPriority.POLISH -> Color.White.copy(alpha = 0.85f)
}
