package com.tgserver.streamer.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.tgserver.streamer.MainActivity
import com.tgserver.streamer.data.model.ServerConfig
import com.tgserver.streamer.posters.SpatialPosterService
import com.tgserver.streamer.server.LocalHttpServer
import com.tgserver.streamer.telegram.TelegramClientManager

class StreamerForegroundService : Service() {

    companion object {
        const val CHANNEL_ID = "tg_server_channel"
        const val NOTIFICATION_ID = 7788

        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
        const val ACTION_RESTART = "ACTION_RESTART"

        var isServiceRunning = false
            private set

        var activeServer: LocalHttpServer? = null
            private set

        var activeConfig = ServerConfig()
            private set

        var activeTgClient: TelegramClientManager? = null
            private set
    }

    private var server: LocalHttpServer? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    override fun onCreate() {
        super.onCreate()
        activeTgClient = TelegramClientManager(applicationContext)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START

        when (action) {
            ACTION_STOP -> {
                stopServer()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_RESTART -> {
                stopServer()
                startServer()
            }
            ACTION_START -> {
                if (!isServiceRunning) {
                    startServer()
                }
            }
        }

        return START_STICKY
    }

    private fun startServer() {
        createNotificationChannel()

        val notification = buildNotification("Server running on port ${activeConfig.port}")
        startForeground(NOTIFICATION_ID, notification)

        // Acquire power locks to ensure low battery drain while keeping socket open
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "TGServer::HttpLock").apply {
            setReferenceCounted(false)
            acquire(24 * 60 * 60 * 1000L) // 24 hours max safeguard
        }

        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        wifiLock = wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "TGServer::WifiLock").apply {
            setReferenceCounted(false)
            acquire()
        }

        try {
            val posterService = SpatialPosterService()
            val tgClient = activeTgClient ?: TelegramClientManager(applicationContext).also { activeTgClient = it }
            server = LocalHttpServer(applicationContext, activeConfig, tgClient, posterService)
            server?.start()
            activeServer = server
            isServiceRunning = true

            val ip = server?.getLocalIpAddress() ?: "127.0.0.1"
            updateNotification("Online at http://$ip:${activeConfig.port}/manifest.json")
        } catch (e: Exception) {
            updateNotification("Error starting server: ${e.message}")
        }
    }

    private fun stopServer() {
        try {
            server?.stop()
            server = null
            activeServer = null
            isServiceRunning = false

            if (wakeLock?.isHeld == true) wakeLock?.release()
            if (wifiLock?.isHeld == true) wifiLock?.release()
        } catch (_: Exception) {
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "TG-Server Streaming Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Runs local Stremio manifest and video streaming service"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(statusText: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("TG-Server Mobile Streamer")
            .setContentText(statusText)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun updateNotification(statusText: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification(statusText))
    }

    override fun onDestroy() {
        stopServer()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
