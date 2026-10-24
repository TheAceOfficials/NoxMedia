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
import com.tgserver.streamer.server.AlexaVoiceBridge
import com.tgserver.streamer.server.LocalHttpServer
import com.tgserver.streamer.telegram.TelegramClientManager

import android.util.Log
import kotlinx.coroutines.*
import android.content.pm.ServiceInfo

class StreamerForegroundService : Service() {

    companion object {
        const val CHANNEL_ID = "tg_server_channel"
        const val NOTIFICATION_ID = 7788

        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
        const val ACTION_RESTART = "ACTION_RESTART"
        const val ACTION_UPDATE_ALEXA = "ACTION_UPDATE_ALEXA"
        const val ACTION_STANDBY = "ACTION_STANDBY"
        const val ACTION_EXIT_STANDBY = "ACTION_EXIT_STANDBY"

        var isServiceRunning = false
            private set

        var isStandbyMode = false
            private set

        var activeServer: LocalHttpServer? = null
            private set

        var activeConfig = ServerConfig()

        var activeTgClient: TelegramClientManager? = null
            private set

        var serverStartTime: Long = 0L
            private set
    }

    private var server: LocalHttpServer? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private var monitorJob: Job? = null
    private var alexaBridge: AlexaVoiceBridge? = null

    private fun loadConfigFromPrefs() {
        val prefs = applicationContext.getSharedPreferences("tg_server_prefs", Context.MODE_PRIVATE)
        activeConfig = ServerConfig(
            port = prefs.getInt("server_port", 8080),
            bufferChunkSizeKb = prefs.getInt("buffer_chunk_kb", 512),
            diskCacheMb = prefs.getInt("disk_cache_mb", 500),
            bufferPipelineMb = prefs.getInt("buffer_pipeline_mb", 64),
            customPosterUrl = prefs.getString("custom_poster_url", "") ?: "",
            customPosterApiKey = prefs.getString("custom_poster_api_key", "") ?: "",
            tmdbApiKey = prefs.getString("tmdb_api_key", "") ?: "",
            tvdbApiKey = prefs.getString("tvdb_api_key", "") ?: "",
            preferredQuality = prefs.getString("preferred_quality", "Original") ?: "Original",
            hideCamTs = prefs.getBoolean("hide_cam_ts", true),
            remotePublicUrl = prefs.getString("remote_public_url", "") ?: "",
            startOnBoot = prefs.getBoolean("start_on_boot", false),
            autoScanNewContent = prefs.getBoolean("auto_scan_new_content", true),
            replaceSameQuality = prefs.getBoolean("replace_same_quality", false),
            skipExactDuplicates = prefs.getBoolean("skip_exact_duplicates", true),
            autoBackupEnabled = prefs.getBoolean("auto_backup_enabled", false),
            autoBackupIntervalHours = prefs.getInt("auto_backup_interval_hours", 24),
            autoSleepEnabled = prefs.getBoolean("auto_sleep_enabled", false),
            autoSleepTimeoutMinutes = prefs.getInt("auto_sleep_timeout_minutes", 30),
            alexaVoiceControlEnabled = prefs.getBoolean("alexa_voice_control_enabled", false),
            alexaDeviceName = prefs.getString("alexa_device_name", "NoxMedia") ?: "NoxMedia",
            receiveBetaUpdates = prefs.getBoolean("receive_beta_updates", false),
            animeMetadataPreference = prefs.getString("anime_metadata_preference", "seasonal") ?: "seasonal",
            backupDestination = prefs.getString("backup_destination", "") ?: "",
            cloudSyncLibrary = prefs.getBoolean("cloud_sync_library", true)
        )
    }

    override fun onCreate() {
        super.onCreate()
        loadConfigFromPrefs()
        // Only initialize the TG client — do NOT auto-start the HTTP server.
        // The server is started explicitly by the user via the toggle in HomeScreen.
        if (activeTgClient == null) {
            activeTgClient = TelegramClientManager(applicationContext)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START

        when (action) {
            ACTION_STOP -> {
                if (activeConfig.alexaVoiceControlEnabled) {
                    enterStandbyMode()
                    return START_STICKY
                } else {
                    stopAlexaBridge()
                    stopServer()
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                    return START_NOT_STICKY
                }
            }
            ACTION_STANDBY -> {
                loadConfigFromPrefs()
                activeConfig.alexaVoiceControlEnabled = true
                enterStandbyMode()
                return START_STICKY
            }
            ACTION_EXIT_STANDBY -> {
                activeConfig.alexaVoiceControlEnabled = false
                if (isStandbyMode) {
                    stopAlexaBridge()
                    isStandbyMode = false
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                    ServerQuickSettingsTileService.updateTileState(applicationContext)
                    StandbyQuickSettingsTileService.updateTileState(applicationContext)
                    return START_NOT_STICKY
                } else if (isServiceRunning) {
                    stopAlexaBridge()
                    ServerQuickSettingsTileService.updateTileState(applicationContext)
                    StandbyQuickSettingsTileService.updateTileState(applicationContext)
                }
            }
            ACTION_UPDATE_ALEXA -> {
                if (activeConfig.alexaVoiceControlEnabled) {
                    startAlexaBridgeIfNeeded()
                    if (!isServiceRunning) {
                        enterStandbyMode()
                        return START_STICKY
                    }
                } else {
                    stopAlexaBridge()
                    if (!isServiceRunning) {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                        return START_NOT_STICKY
                    }
                }
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

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        // Maintain streaming server process running even when app task is swiped from recents
    }

    private fun startAlexaBridgeIfNeeded() {
        if (activeConfig.alexaVoiceControlEnabled) {
            if (alexaBridge == null) {
                alexaBridge = AlexaVoiceBridge(
                    context = applicationContext,
                    httpPort = activeConfig.port + 2,
                    deviceNameProvider = { activeConfig.alexaDeviceName },
                    isRunningProvider = { isServiceRunning && server != null },
                    onStateChange = { shouldRun ->
                        CoroutineScope(Dispatchers.Main).launch {
                            if (shouldRun) {
                                if (!isServiceRunning) {
                                    startServer()
                                }
                            } else {
                                if (isServiceRunning) {
                                    enterStandbyMode()
                                }
                            }
                        }
                    }
                ).apply { start() }
            }
        } else {
            stopAlexaBridge()
        }
    }

    private fun stopAlexaBridge() {
        alexaBridge?.stop()
        alexaBridge = null
    }

    private fun enterStandbyMode() {
        isStandbyMode = true
        stopServerInternal()
        startAlexaBridgeIfNeeded()
        createNotificationChannel()
        val notification = buildNotification(
            "Standby • Listening for Alexa (\"${activeConfig.alexaDeviceName}\")",
            isStandby = true
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        ServerQuickSettingsTileService.updateTileState(applicationContext)
        StandbyQuickSettingsTileService.updateTileState(applicationContext)
    }

    private fun startServer() {
        isStandbyMode = false
        createNotificationChannel()

        val notification = buildNotification("Server running on port ${activeConfig.port}", isStandby = false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC or ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        // Acquire partial wake lock to keep CPU alive during screen-off streaming
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "TGServer::HttpLock").apply {
            setReferenceCounted(false)
            acquire(24 * 60 * 60 * 1000L) // 24 hours safeguard
        }

        // Acquire Wi-Fi lock to prevent Wi-Fi chip power collapse when screen is off
        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val wifiMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            WifiManager.WIFI_MODE_FULL_LOW_LATENCY
        } else {
            WifiManager.WIFI_MODE_FULL_HIGH_PERF
        }
        wifiLock = wifiManager.createWifiLock(wifiMode, "TGServer::WifiLock").apply {
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
            serverStartTime = System.currentTimeMillis()
            com.tgserver.streamer.server.StreamLog.init(applicationContext)
            com.tgserver.streamer.server.StreamTrafficStats.init(applicationContext)
            com.tgserver.streamer.server.StreamTrafficStats.resetSession()
            com.tgserver.streamer.server.StreamTrafficStats.notifyActivity()
            ServerQuickSettingsTileService.updateTileState(applicationContext)
            StandbyQuickSettingsTileService.updateTileState(applicationContext)

            val ip = server?.getLocalIpAddress() ?: "127.0.0.1"
            com.tgserver.streamer.server.StreamLog.i("StreamerService", "Server started at http://$ip:${activeConfig.port}/manifest.json")
            updateNotification("Online at http://$ip:${activeConfig.port}/manifest.json")
            startAutoSleepMonitor()
            startAlexaBridgeIfNeeded()
        } catch (e: Exception) {
            updateNotification("Error starting server: ${e.message}")
        }
    }

    private fun stopServerInternal() {
        try {
            monitorJob?.cancel()
            monitorJob = null
            server?.stop()
            server = null
            activeServer = null
            isServiceRunning = false
            serverStartTime = 0L
            com.tgserver.streamer.server.StreamTrafficStats.resetSession()
            ServerQuickSettingsTileService.updateTileState(applicationContext)
            StandbyQuickSettingsTileService.updateTileState(applicationContext)

            if (wakeLock?.isHeld == true) wakeLock?.release()
            if (wifiLock?.isHeld == true) wifiLock?.release()
        } catch (_: Exception) {
        }
    }

    private fun stopServer() {
        stopServerInternal()
    }

    private fun startAutoSleepMonitor() {
        monitorJob?.cancel()
        monitorJob = CoroutineScope(Dispatchers.Main).launch {
            while (isActive && isServiceRunning) {
                val config = activeConfig
                val ip = server?.getLocalIpAddress() ?: "127.0.0.1"
                val liveSpeed = com.tgserver.streamer.server.StreamTrafficStats.calculateLiveSpeed()
                val isStreaming = liveSpeed > 1024L // Active stream (> 1 KB/s)

                if (isStreaming) {
                    com.tgserver.streamer.server.StreamTrafficStats.notifyActivity()
                }

                if (config.autoSleepEnabled) {
                    val timeoutMillis = config.autoSleepTimeoutMinutes * 60 * 1000L
                    val idleMillis = System.currentTimeMillis() - com.tgserver.streamer.server.StreamTrafficStats.lastActivityTime
                    val remainingMillis = timeoutMillis - idleMillis

                    if (remainingMillis <= 0L) {
                        Log.i("StreamerService", "Auto-sleep timeout reached (${config.autoSleepTimeoutMinutes}m idle).")
                        if (config.alexaVoiceControlEnabled) {
                            enterStandbyMode()
                        } else {
                            stopServer()
                            stopForeground(STOP_FOREGROUND_REMOVE)
                            stopSelf()
                        }
                        break
                    } else {
                        if (isStreaming) {
                            val speedStr = com.tgserver.streamer.server.StreamTrafficStats.formatSpeed(liveSpeed)
                            updateNotification("Streaming: $speedStr • http://$ip:${config.port}")
                        } else {
                            val remainingSec = (remainingMillis + 999) / 1000
                            val h = remainingSec / 3600
                            val m = (remainingSec % 3600) / 60
                            val s = remainingSec % 60
                            val countdownStr = when {
                                h > 0 -> "${h}h ${m}m ${s}s"
                                m > 0 -> "${m}m ${s}s"
                                else -> "${s}s"
                            }
                            updateNotification("Idle • Auto-sleep in $countdownStr • http://$ip:${config.port}")
                        }
                    }
                } else {
                    if (isStreaming) {
                        val speedStr = com.tgserver.streamer.server.StreamTrafficStats.formatSpeed(liveSpeed)
                        updateNotification("Streaming: $speedStr • http://$ip:${config.port}")
                    } else {
                        updateNotification("Online at http://$ip:${config.port}/manifest.json")
                    }
                }

                delay(1000)
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "NoxMedia Streaming Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Runs local Stremio manifest and video streaming service"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(statusText: String, isStandby: Boolean = false): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val actionIntent = Intent(this, StreamerForegroundService::class.java).apply {
            action = if (isStandby) ACTION_START else ACTION_STOP
        }
        val actionPendingIntent = PendingIntent.getService(
            this,
            1,
            actionIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val actionTitle = if (isStandby) "Start Server" else "Stop Server"
        val actionIcon = if (isStandby) android.R.drawable.ic_media_play else android.R.drawable.ic_menu_close_clear_cancel

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(if (isStandby) "NoxMedia (Standby)" else "NoxMedia Server")
            .setContentText(statusText)
            .setSmallIcon(if (isStandby) android.R.drawable.ic_lock_idle_alarm else android.R.drawable.ic_media_play)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(pendingIntent)
            .addAction(actionIcon, actionTitle, actionPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun updateNotification(statusText: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification(statusText, isStandbyMode))
    }

    override fun onDestroy() {
        stopAlexaBridge()
        stopServer()
        isStandbyMode = false
        isServiceRunning = false
        ServerQuickSettingsTileService.updateTileState(applicationContext)
        StandbyQuickSettingsTileService.updateTileState(applicationContext)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
