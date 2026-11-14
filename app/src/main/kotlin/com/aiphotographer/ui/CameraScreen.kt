package com.aiphotographer.ui

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import com.aiphotographer.Features
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.aiphotographer.intelligence.AdviceSelector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Grid3x3
import androidx.compose.material.icons.filled.HdrOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ScreenRotationAlt
import androidx.compose.material.icons.outlined.Accessibility
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.camera.view.PreviewView
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aiphotographer.CameraViewModel
import com.aiphotographer.CaptureStage
import com.aiphotographer.ShootingMode
import com.aiphotographer.ui.theme.Amber
import com.aiphotographer.ui.theme.Good
import com.aiphotographer.ui.theme.Smoke

/**
 * The viewfinder.
 *
 * Built for someone who has never read a manual: the picture you will get,
 * one instruction on it, a shutter, and your last photo. Everything else is
 * behind [Features] until it has earned its place on a real phone.
 */
@Composable
fun CameraScreen(
    viewModel: CameraViewModel = viewModel(),
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val state by viewModel.state.collectAsState()
    val advice = remember(state.guidance) { AdviceSelector.select(state.guidance) }

    val previewView = remember {
        PreviewView(context).apply {
            // The view is sized to the camera's own 3:4 frame below, so FIT
            // shows the whole frame: what you see is what gets saved, and
            // overlay coordinates line up with the picture underneath.
            scaleType = PreviewView.ScaleType.FIT_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }

    LaunchedEffect(Unit) {
        viewModel.bindCamera(lifecycleOwner, previewView)
    }

    // A white blink on every shot: proof the button did something.
    val flash = remember { Animatable(0f) }
    LaunchedEffect(state.shotCount) {
        if (state.shotCount > 0) {
            flash.snapTo(0.85f)
            flash.animateTo(0f, tween(durationMillis = 260))
        }
    }

    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (Features.TECH_READOUT) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TopReadout(state = state)
                ExposureReadout(state = state)
            }
        } else {
            Spacer(modifier = Modifier.height(12.dp))
        }

        // ---------------- The viewfinder: exactly the photo's shape ----------------
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(state.frameAspect.coerceIn(0.5f, 1f))
                .clip(RoundedCornerShape(14.dp)),
        ) {
            AndroidView(
                factory = { previewView },
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures { offset ->
                            viewModel.focusAt(previewView, offset.x, offset.y)
                        }
                    },
            )

            GuidanceOverlay(
                guidance = state.guidance,
                livePose = state.livePose,
                showGrid = state.showGrid,
                showPoseGuide = state.showPoseGuide,
                showLevel = state.showLevel,
                mirrored = state.usingFrontCamera,
                frameAspect = state.frameAspect,
                rollDeg = state.rollDeg,
                modifier = Modifier.fillMaxSize(),
            )

            HintStrip(
                advice = advice,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 12.dp, vertical = 14.dp),
            )

            if (flash.value > 0f) {
                Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = flash.value)))
            }
        }

        // ---------------- Controls ----------------
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AnimatedVisibility(visible = state.lastEnhancementSummary != null) {
                state.lastEnhancementSummary?.let { summary ->
                    Text(
                        text = "Saved · $summary",
                        color = Good,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Color.White.copy(alpha = 0.08f))
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }

            if (Features.GRID || Features.POSE_GUIDE || Features.HDR_BUTTON) {
                ToggleRow(
                    showGrid = state.showGrid,
                    showPose = state.showPoseGuide,
                    showLevel = state.showLevel,
                    hdrOn = state.manual.hdrRequested,
                    hdrAvailable = state.capabilities.supportsHdr,
                    onGrid = viewModel::toggleGrid,
                    onPose = viewModel::togglePoseGuide,
                    onLevel = viewModel::toggleLevel,
                    onHdr = { viewModel.toggleHdr(previewView) },
                    onCyclePose = viewModel::cyclePose,
                )
            }

            if (Features.PRO_MODE) {
                AnimatedVisibility(visible = state.isPro) {
                    ProControls(
                        state = state,
                        onManualChange = viewModel::updateManual,
                        onZoom = viewModel::setZoom,
                        onExposureCompensation = viewModel::setExposureCompensation,
                    )
                }
                ModeSwitch(mode = state.mode, onModeChange = viewModel::setMode)
            }

            ShutterRow(
                captureStage = state.captureStage,
                ready = advice.looksGood,
                enabled = state.cameraReady,
                lastPhotoUri = state.lastPhotoUri,
                onCapture = viewModel::capture,
                onSwitchCamera = { viewModel.switchCamera(previewView) },
            )
        }
    }

    // ---------------- Messages ----------------
    state.message?.let { message ->
        Box(modifier = Modifier.fillMaxSize().statusBarsPadding().padding(16.dp)) {
            Snackbar(
                action = {
                    Text(
                        text = "OK",
                        color = Amber,
                        modifier = Modifier
                            .clickable { viewModel.dismissMessage() }
                            .padding(8.dp),
                    )
                },
            ) {
                Text(message)
            }
        }
    }
    }
}

/**
 * The last photo, bottom left, like every camera app: proof the shot was
 * saved and one tap to see it.
 */
@Composable
private fun LastPhotoButton(uri: Uri?) {
    val context = LocalContext.current
    var thumb by remember { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(uri) {
        thumb = uri?.let { loadThumbnail(context, it) }
    }
    Box(
        modifier = Modifier
            .size(52.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.10f))
            .border(1.5.dp, Color.White.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
            .clickable(enabled = uri != null) {
                uri ?: return@clickable
                val view = Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri, "image/*")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                runCatching { context.startActivity(view) }
            },
        contentAlignment = Alignment.Center,
    ) {
        thumb?.let {
            Image(
                bitmap = it,
                contentDescription = "Last photo",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** A small, correctly rotated preview of a saved photo, off the main thread. */
private suspend fun loadThumbnail(context: Context, uri: Uri): ImageBitmap? =
    withContext(Dispatchers.IO) {
        runCatching {
            val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                context.contentResolver.loadThumbnail(uri, android.util.Size(256, 256), null)
            } else {
                val options = BitmapFactory.Options().apply { inSampleSize = 16 }
                context.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, options)
                }
            }
            bitmap?.asImageBitmap()
        }.getOrNull()
    }

@Composable
private fun ModeSwitch(
    mode: ShootingMode,
    onModeChange: (ShootingMode) -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Color.Black.copy(alpha = 0.55f))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        for (option in ShootingMode.entries) {
            val selected = option == mode
            Text(
                text = option.name,
                color = if (selected) Color.Black else Color.White,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (selected) Amber else Color.Transparent)
                    .clickable { onModeChange(option) }
                    .padding(horizontal = 18.dp, vertical = 7.dp),
            )
        }
    }
}

@Composable
private fun ToggleRow(
    showGrid: Boolean,
    showPose: Boolean,
    showLevel: Boolean,
    hdrOn: Boolean,
    hdrAvailable: Boolean,
    onGrid: () -> Unit,
    onPose: () -> Unit,
    onLevel: () -> Unit,
    onHdr: () -> Unit,
    onCyclePose: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        IconToggle(Icons.Filled.Grid3x3, "Grid", showGrid, onGrid)
        IconToggle(Icons.Outlined.Accessibility, "Pose guide", showPose, onPose)
        IconToggle(Icons.Filled.ScreenRotationAlt, "Level", showLevel, onLevel)
        IconToggle(Icons.Filled.HdrOn, "HDR", hdrOn, onHdr, enabled = hdrAvailable)
        if (showPose) {
            IconToggle(Icons.Filled.Refresh, "Another pose", false, onCyclePose)
        }
    }
}

@Composable
private fun IconToggle(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    active: Boolean,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(
                when {
                    !enabled -> Color.Black.copy(alpha = 0.3f)
                    active -> Amber.copy(alpha = 0.9f)
                    else -> Color.Black.copy(alpha = 0.55f)
                }
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = when {
                !enabled -> Smoke.copy(alpha = 0.5f)
                active -> Color.Black
                else -> Color.White
            },
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun ShutterRow(
    captureStage: CaptureStage,
    ready: Boolean,
    enabled: Boolean,
    lastPhotoUri: Uri?,
    onCapture: () -> Unit,
    onSwitchCamera: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        LastPhotoButton(uri = lastPhotoUri)

        Box(contentAlignment = Alignment.Center) {
            val busy = captureStage != CaptureStage.IDLE
            Box(
                modifier = Modifier
                    .size(74.dp)
                    .clip(CircleShape)
                    .border(
                        width = 3.dp,
                        color = if (ready) Good else Color.White,
                        shape = CircleShape,
                    )
                    .padding(6.dp)
                    .clip(CircleShape)
                    .background(if (busy) Smoke else Color.White)
                    .clickable(enabled = enabled && !busy, onClick = onCapture)
                    .semantics { contentDescription = "Take photo" },
            )
            if (busy) {
                CircularProgressIndicator(
                    modifier = Modifier.size(58.dp),
                    color = Amber,
                    strokeWidth = 3.dp,
                )
            }
        }

        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.55f))
                .clickable(enabled = enabled, onClick = onSwitchCamera),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Cameraswitch,
                contentDescription = "Switch camera",
                tint = Color.White,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}
