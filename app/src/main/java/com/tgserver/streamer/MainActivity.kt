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

        // 1. Initialize custom disk logger early and clear old session logs on cold launch
        try {
            com.tgserver.streamer.server.StreamLog.init(applicationContext)
            if (savedInstanceState == null) {
                com.tgserver.streamer.server.StreamLog.clear()
            }
        } catch (_: Exception) {}

        // 2. Global Uncaught Exception Crash Guard: log crash and auto-heal corrupt caches
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                android.util.Log.e("CrashGuard", "FATAL uncaught exception on thread ${thread.name}: ${throwable.message}", throwable)
                com.tgserver.streamer.server.StreamLog.e("CrashGuard", "FATAL on thread ${thread.name}: ${throwable.message}\n${android.util.Log.getStackTraceString(throwable)}")
                val groupCache = java.io.File(filesDir, "media_groups_cache.json")
                if (groupCache.exists()) groupCache.delete()
            } catch (_: Exception) {}
            defaultHandler?.uncaughtException(thread, throwable)
        }

        // 3. Startup crash recovery: if consecutive startup crashes detected, auto-heal caches without wiping user session/login
        try {
            val prefs = getSharedPreferences("tg_server_prefs", android.content.Context.MODE_PRIVATE)
            val crashes = prefs.getInt("startup_crash_count", 0)
            if (crashes >= 2) {
                val groupCache = java.io.File(filesDir, "media_groups_cache.json")
                if (groupCache.exists()) groupCache.delete()
                filesDir.listFiles()?.filter { it.name.endsWith(".tmp") }?.forEach { it.delete() }
                prefs.edit().putInt("startup_crash_count", 0).apply()
                android.util.Log.w("MainActivity", "Auto-healed startup caches after repeated startup crashes.")
            } else {
                prefs.edit().putInt("startup_crash_count", crashes + 1).apply()
            }
        } catch (_: Exception) {}

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

    override fun onResume() {
        super.onResume()
        // Reset startup crash counter once UI is safely up and resumed
        try {
            getSharedPreferences("tg_server_prefs", android.content.Context.MODE_PRIVATE)
                .edit()
                .putInt("startup_crash_count", 0)
                .apply()
        } catch (_: Exception) {}
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing) {
            try {
                com.tgserver.streamer.server.StreamLog.clear()
            } catch (_: Exception) {}
        }
    }
}
