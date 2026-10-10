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
import com.tgserver.streamer.theme.TGServerTheme
import com.tgserver.streamer.ui.AppRootScreen
import com.tgserver.streamer.ui.MainAppViewModel

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
                AppRootScreen(viewModel = viewModel)
            }
        }
    }
}
