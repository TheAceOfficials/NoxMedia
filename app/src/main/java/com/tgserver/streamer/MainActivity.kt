package com.tgserver.streamer

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import com.tgserver.streamer.service.StreamerForegroundService
import com.tgserver.streamer.theme.TGServerTheme
import com.tgserver.streamer.ui.AppRootScreen
import com.tgserver.streamer.ui.MainAppViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: MainAppViewModel by viewModels()

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { _ ->
            startStreamerService()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Notification permission on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                startStreamerService()
            }
        } else {
            startStreamerService()
        }

        setContent {
            TGServerTheme {
                AppRootScreen(viewModel = viewModel)
            }
        }
    }

    private fun startStreamerService() {
        val intent = Intent(this, StreamerForegroundService::class.java).apply {
            action = StreamerForegroundService.ACTION_START
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }
}
