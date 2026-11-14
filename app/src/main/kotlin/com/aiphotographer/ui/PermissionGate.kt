package com.aiphotographer.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.aiphotographer.ui.theme.Amber
import com.aiphotographer.ui.theme.Smoke

/**
 * Asks for the camera, and explains why before asking.
 *
 * The explanation matters more than usual here: the app's main promise is that
 * nothing leaves the phone, and the moment someone is asked for camera access
 * is exactly when they are wondering about that.
 */
@Composable
fun PermissionGate(content: @Composable () -> Unit) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(context.hasRequiredPermissions()) }
    var asked by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        // Only the camera is essential; storage is only needed on older
        // versions, and its absence is not worth blocking the app for.
        granted = results[Manifest.permission.CAMERA] == true ||
            context.hasRequiredPermissions()
        asked = true
    }

    LaunchedEffect(Unit) {
        if (!granted) launcher.launch(requiredPermissions())
    }

    if (granted) {
        content()
    } else {
        PermissionExplanation(
            deniedAlready = asked,
            onGrant = { launcher.launch(requiredPermissions()) },
            onOpenSettings = { context.openAppSettings() },
        )
    }
}

@Composable
private fun PermissionExplanation(
    deniedAlready: Boolean,
    onGrant: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "Camera access needed",
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
            Text(
                text = "AI Photographer needs the camera to see your scene and coach " +
                    "your shot. Everything is analysed on this phone — the app has no " +
                    "internet permission at all, so nothing can be uploaded.",
                color = Smoke,
                textAlign = TextAlign.Center,
            )
            Text(
                text = if (deniedAlready) "OPEN SETTINGS" else "ALLOW CAMERA",
                color = Color.Black,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Amber)
                    .clickable { if (deniedAlready) onOpenSettings() else onGrant() }
                    .padding(horizontal = 24.dp, vertical = 12.dp),
            )
        }
    }
}

/**
 * Camera always; legacy storage only up to Android 9, where saving to the
 * gallery still goes through the filesystem.
 */
private fun requiredPermissions(): Array<String> =
    if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
        arrayOf(Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE)
    } else {
        arrayOf(Manifest.permission.CAMERA)
    }

private fun Context.hasRequiredPermissions(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
        PackageManager.PERMISSION_GRANTED

private fun Context.openAppSettings() {
    val intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", packageName, null),
    )
    startActivity(intent)
}
