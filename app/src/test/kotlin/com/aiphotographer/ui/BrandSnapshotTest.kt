package com.aiphotographer.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.aiphotographer.R
import org.junit.Rule
import org.junit.Test

/** The launcher icon as a circle and a rounded square, plus the splash. */
class BrandSnapshotTest {
    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_6)

    @Test
    fun icon_and_splash() = paparazzi.snapshot {
        Row(
            Modifier.background(Color(0xFFE9E4EE)).padding(24.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Icon(CircleShape)
                Icon(RoundedCornerShape(28.dp))
                Text("ShotMuse")
            }
            Box(
                Modifier.size(180.dp, 380.dp).clip(RoundedCornerShape(24.dp)).background(Color(0xFF0E0B12)),
                contentAlignment = Alignment.Center,
            ) {
                Image(painterResource(R.drawable.ic_launcher_foreground), null, Modifier.size(150.dp))
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun Icon(shape: androidx.compose.ui.graphics.Shape) {
        Box(Modifier.size(110.dp).clip(shape)) {
            Image(painterResource(R.drawable.ic_launcher_background), null, Modifier.size(110.dp))
            Image(painterResource(R.drawable.ic_launcher_foreground), null, Modifier.size(110.dp))
        }
    }
}
