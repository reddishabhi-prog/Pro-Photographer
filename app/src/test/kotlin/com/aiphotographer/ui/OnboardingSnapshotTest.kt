package com.aiphotographer.ui

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import org.junit.Rule
import org.junit.Test

/**
 * Renders the first-run screens on the JVM, no phone needed, so layout
 * breakage shows up as a picture in review rather than on someone's device.
 *   ./gradlew :app:recordPaparazziDebug   -> app/src/test/snapshots/images
 */
class OnboardingSnapshotTest {
    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_6)

    @Test fun page1_hero() = paparazzi.snapshot { OnboardingScreen(onFinish = {}, initialPage = 0) }
    @Test fun page2_coaching() = paparazzi.snapshot { OnboardingScreen(onFinish = {}, initialPage = 1) }
    @Test fun page3_edits() = paparazzi.snapshot { OnboardingScreen(onFinish = {}, initialPage = 2) }
    @Test fun page4_interests() = paparazzi.snapshot { OnboardingScreen(onFinish = {}, initialPage = 3) }
}
