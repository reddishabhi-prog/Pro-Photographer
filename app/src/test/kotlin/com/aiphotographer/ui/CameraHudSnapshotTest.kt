package com.aiphotographer.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.aiphotographer.R
import com.aiphotographer.intelligence.AdviceSelector
import com.aiphotographer.intelligence.AutoActions
import com.aiphotographer.intelligence.CameraState
import com.aiphotographer.intelligence.ColorStats
import com.aiphotographer.intelligence.DeviceTilt
import com.aiphotographer.intelligence.Face
import com.aiphotographer.intelligence.FrameInput
import com.aiphotographer.intelligence.Landmark
import com.aiphotographer.intelligence.LumaStats
import com.aiphotographer.intelligence.NormPoint
import com.aiphotographer.intelligence.Person
import com.aiphotographer.intelligence.PhotoIntelligenceEngine
import com.aiphotographer.intelligence.PoseLibrary
import com.aiphotographer.intelligence.SceneLabel
import org.junit.Rule
import org.junit.Test

/**
 * The shooting screen's overlays over a still, with the real engine's answer
 * for a slightly crooked half-length portrait. CameraX cannot run on the JVM,
 * so the preview is a showcase image; everything drawn over it is real.
 */
class CameraHudSnapshotTest {
    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_6)

    private val upper = mapOf(
        Landmark.NOSE to NormPoint(0.52f, 0.47f), Landmark.LEFT_EYE to NormPoint(0.48f, 0.45f),
        Landmark.RIGHT_EYE to NormPoint(0.56f, 0.45f), Landmark.LEFT_SHOULDER to NormPoint(0.33f, 0.74f),
        Landmark.RIGHT_SHOULDER to NormPoint(0.69f, 0.74f), Landmark.LEFT_HIP to NormPoint(0.38f, 0.98f),
        Landmark.RIGHT_HIP to NormPoint(0.64f, 0.98f),
    )
    private val camera = CameraState(iso = 125, exposureTimeNs = 1_000_000_000L / 250, aperture = 1.8f, focalLengthMm = 5f)
    private val guidance = PhotoIntelligenceEngine.analyse(
        FrameInput(
            people = listOf(Person(com.aiphotographer.intelligence.Box(0.24f, 0.30f, 0.80f, 1f), 0.95f, upper, upper.keys.associateWith { 0.9f })),
            faces = listOf(Face(com.aiphotographer.intelligence.Box(0.37f, 0.37f, 0.64f, 0.63f))),
            labels = listOf(SceneLabel("person", 0.9f)),
            luma = LumaStats(130f, 30f, 235f, 0.02f, 0.05f),
            color = ColorStats(170f, 140f, 120f),
            camera = camera,
            tilt = DeviceTilt(rollDeg = 4f),
        ),
    )

    @Test
    fun shooting_screen() = paparazzi.snapshot {
        Column(
            Modifier.fillMaxSize().background(Color.Black),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ShotHud(
                scene = guidance.scene, camera = camera, appliedEv = AutoActions.autoEv(guidance),
                actions = AutoActions.describe(guidance, "HDR"),
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            )
            Box(Modifier.fillMaxWidth().aspectRatio(0.75f).clip(RoundedCornerShape(14.dp))) {
                Image(painterResource(R.drawable.showcase_people_after), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                GuidanceOverlay(
                    guidance = guidance, livePose = upper, showGrid = false, showPoseGuide = true,
                    showLevel = true, mirrored = false, frameAspect = 0.75f, rollDeg = 4f,
                    modifier = Modifier.fillMaxSize(),
                )
                HintStrip(AdviceSelector.select(guidance), Modifier.align(Alignment.BottomCenter).padding(14.dp))
            }
            PoseStrip(
                pose = PoseLibrary.byId(guidance.poseId), pinned = false, outlineOn = true,
                onNext = {}, onToggleOutline = {}, onOpenLibrary = {},
                modifier = Modifier.padding(horizontal = 20.dp),
            )
        }
    }

    @Test
    fun pose_library() = paparazzi.snapshot {
        Box(Modifier.fillMaxSize().background(Color(0xFF120E16)).padding(top = 24.dp)) {
            PoseLibraryContent(pinnedId = "full_contrapposto", onPick = {})
        }
    }
}
