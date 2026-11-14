package com.aiphotographer.ui

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
 * The layout follows one rule: the photograph owns the screen. Controls live
 * at the edges, advice sits just above the shutter where the thumb already is,
 * and nothing opaque ever covers the middle of the frame.
 */
@Composable
fun CameraScreen(
    viewModel: CameraViewModel = viewModel(),
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val state by viewModel.state.collectAsState()

    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }

    LaunchedEffect(Unit) {
        viewModel.bindCamera(lifecycleOwner, previewView)
    }

    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {

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
            modifier = Modifier.fillMaxSize(),
        )

        // ---------------- Top ----------------
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            TopReadout(state = state)
            ExposureReadout(state = state)
        }

        // ---------------- Bottom ----------------
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            HintStrip(guidance = state.guidance)

            AnimatedVisibility(visible = state.lastEnhancementSummary != null) {
                state.lastEnhancementSummary?.let { summary ->
                    Text(
                        text = summary,
                        color = Good,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Color.Black.copy(alpha = 0.6f))
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }

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

            AnimatedVisibility(visible = state.isPro) {
                ProControls(
                    state = state,
                    onManualChange = viewModel::updateManual,
                    onZoom = viewModel::setZoom,
                    onExposureCompensation = viewModel::setExposureCompensation,
                )
            }

            ModeSwitch(
                mode = state.mode,
                onModeChange = viewModel::setMode,
            )

            ShutterRow(
                captureStage = state.captureStage,
                ready = state.guidance?.readyToShoot == true,
                enabled = state.cameraReady,
                onCapture = viewModel::capture,
                onSwitchCamera = { viewModel.switchCamera(previewView) },
            )
        }

        // ---------------- Messages ----------------
        state.message?.let { message ->
            Snackbar(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(16.dp),
                action = {
                    Text(
                        text = "DISMISS",
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
    onCapture: () -> Unit,
    onSwitchCamera: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Spacer(modifier = Modifier.size(52.dp))

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
                    .clickable(enabled = enabled && !busy, onClick = onCapture),
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
