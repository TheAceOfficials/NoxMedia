package com.tgserver.streamer

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.tgserver.streamer.theme.TGServerTheme
import com.tgserver.streamer.ui.AppRootScreen
import com.tgserver.streamer.ui.MainAppViewModel
import com.tgserver.streamer.ui.screens.SplashScreen

class MainActivity : ComponentActivity() {

    private val viewModel: MainAppViewModel by viewModels()

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { _ ->
            // Permission result handled - we don't auto-start the server here anymore.
            // Server is started manually by the user via the toggle in HomeScreen.
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Request notification permission on Android 13+ for foreground service notifications.
        // We do NOT auto-start the server — the user controls it via the toggle after login.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        setContent {
            TGServerTheme {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                ) {
                    AppRootScreen(viewModel = viewModel)

                    // Splash screen runs strictly once per app launch session; survives phone rotation
                    if (!viewModel.isSplashCompleted) {
                        SplashScreen(
                            onDismiss = { viewModel.isSplashCompleted = true }
                        )
                    }
                }
            }
        }
    }
}
