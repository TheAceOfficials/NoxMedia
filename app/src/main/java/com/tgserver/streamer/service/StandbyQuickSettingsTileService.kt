package com.tgserver.streamer.service

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import com.tgserver.streamer.R

class StandbyQuickSettingsTileService : TileService() {

    companion object {
        private const val TAG = "StandbyQSTile"

        fun updateTileState(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                try {
                    requestListeningState(
                        context,
                        ComponentName(context, StandbyQuickSettingsTileService::class.java)
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to request tile update: ${e.message}")
                }
            }
        }
    }

    override fun onStartListening() {
        super.onStartListening()
        refreshTile()
    }

    override fun onClick() {
        super.onClick()
        val isStandby = StreamerForegroundService.isStandbyMode

        if (isLocked) {
            unlockAndRun {
                toggleStandby(!isStandby)
            }
        } else {
            toggleStandby(!isStandby)
        }
    }

    private fun toggleStandby(enableStandby: Boolean) {
        val prefs = applicationContext.getSharedPreferences("tg_server_prefs", Context.MODE_PRIVATE)

        if (enableStandby) {
            prefs.edit().putBoolean("alexa_voice_control_enabled", true).apply()
            StreamerForegroundService.activeConfig.alexaVoiceControlEnabled = true

            val intent = Intent(this, StreamerForegroundService::class.java).apply {
                action = StreamerForegroundService.ACTION_STANDBY
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        } else {
            prefs.edit().putBoolean("alexa_voice_control_enabled", false).apply()
            StreamerForegroundService.activeConfig.alexaVoiceControlEnabled = false

            val intent = Intent(this, StreamerForegroundService::class.java).apply {
                action = StreamerForegroundService.ACTION_EXIT_STANDBY
            }
            startService(intent)
        }

        updateTileVisual(isStandby = enableStandby)
        ServerQuickSettingsTileService.updateTileState(this)
    }

    private fun refreshTile() {
        val isStandby = StreamerForegroundService.isStandbyMode
        updateTileVisual(isStandby)
    }

    private fun updateTileVisual(isStandby: Boolean) {
        val tile = qsTile ?: return
        tile.icon = Icon.createWithResource(this, R.drawable.ic_qs_standby)
        tile.label = getString(R.string.tile_standby_toggle)

        if (isStandby) {
            tile.state = Tile.STATE_ACTIVE
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val deviceName = StreamerForegroundService.activeConfig.alexaDeviceName.ifBlank { "NoxMedia" }
                tile.subtitle = "${getString(R.string.tile_standby_active)} • $deviceName"
            }
        } else {
            tile.state = Tile.STATE_INACTIVE
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                tile.subtitle = if (StreamerForegroundService.isServiceRunning) {
                    getString(R.string.tile_standby_server_streaming)
                } else {
                    getString(R.string.tile_standby_off)
                }
            }
        }

        tile.updateTile()
    }
}
