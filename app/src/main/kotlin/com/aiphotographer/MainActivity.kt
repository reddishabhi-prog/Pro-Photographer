package com.aiphotographer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.core.view.WindowCompat
import com.aiphotographer.ui.CameraScreen
import com.aiphotographer.ui.PermissionGate
import com.aiphotographer.ui.theme.AIPhotographerTheme

/**
 * The only screen.
 *
 * A camera app that makes you navigate to the camera has already failed, so
 * this opens straight into the viewfinder and draws edge to edge behind the
 * system bars - the photograph gets the whole screen.
 */
class MainActivity : ComponentActivity() {

    private val viewModel: CameraViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            AIPhotographerTheme {
                PermissionGate {
                    CameraScreen(viewModel = viewModel)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.onResume()
    }

    override fun onPause() {
        // Stop reading the accelerometer the moment we are not showing a
        // viewfinder: it is pure battery drain once nobody can see the level.
        viewModel.onPause()
        super.onPause()
    }
}
