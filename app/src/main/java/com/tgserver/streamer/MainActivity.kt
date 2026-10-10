package com.tgserver.streamer

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.tgserver.streamer.service.StreamerForegroundService
import com.tgserver.streamer.theme.TGServerTheme

class MainActivity : ComponentActivity() {

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { _ ->
            startStreamerService()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Check and request notification permissions on Android 13+
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
                Surface(
                    modifier = Modifier.fillMaxSize().background(Color(0xFF121318)),
                    color = Color(0xFF121318)
                ) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { context ->
                            WebView(context).apply {
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                                setBackgroundColor(0xFF121318.toInt())

                                settings.apply {
                                    javaScriptEnabled = true
                                    domStorageEnabled = true
                                    databaseEnabled = true
                                    allowFileAccess = true
                                    allowContentAccess = true
                                    loadWithOverviewMode = true
                                    useWideViewPort = true
                                    cacheMode = WebSettings.LOAD_DEFAULT
                                    mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                                }

                                webChromeClient = WebChromeClient()
                                webViewClient = object : WebViewClient() {
                                    override fun shouldOverrideUrlLoading(
                                        view: WebView?,
                                        request: WebResourceRequest?
                                    ): Boolean {
                                        return false
                                    }
                                }

                                // Load the offline Stitch dark theme web application from assets
                                loadUrl("file:///android_asset/web/index.html")
                            }
                        }
                    )
                }
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
