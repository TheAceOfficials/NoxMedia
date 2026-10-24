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

class ServerQuickSettingsTileService : TileService() {

    companion object {
        private const val TAG = "ServerQSTile"

        fun updateTileState(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                try {
                    requestListeningState(
                        context,
                        ComponentName(context, ServerQuickSettingsTileService::class.java)
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
        val isRunning = StreamerForegroundService.isServiceRunning

        if (isLocked) {
            unlockAndRun {
                toggleServer(!isRunning)
            }
        } else {
            toggleServer(!isRunning)
        }
    }

    private fun toggleServer(start: Boolean) {
        val intent = Intent(this, StreamerForegroundService::class.java).apply {
            action = if (start) StreamerForegroundService.ACTION_START else StreamerForegroundService.ACTION_STOP
        }

        if (start) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        } else {
            startService(intent)
        }

        // Optimistically update the tile immediately for reactive touch response
        updateTileVisual(isRunning = start)
        StandbyQuickSettingsTileService.updateTileState(this)
    }

    private fun refreshTile() {
        val isRunning = StreamerForegroundService.isServiceRunning
        updateTileVisual(isRunning)
    }

    private fun updateTileVisual(isRunning: Boolean) {
        val tile = qsTile ?: return
        tile.icon = Icon.createWithResource(this, R.drawable.ic_qs_server)
        tile.label = getString(R.string.tile_server_toggle)

        if (isRunning) {
            tile.state = Tile.STATE_ACTIVE
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val port = StreamerForegroundService.activeConfig.port
                tile.subtitle = "${getString(R.string.tile_server_running)} • :$port"
            }
        } else {
            tile.state = Tile.STATE_INACTIVE
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                tile.subtitle = if (StreamerForegroundService.isStandbyMode) {
                    "Standby (Alexa)"
                } else {
                    getString(R.string.tile_server_stopped)
                }
            }
        }

        tile.updateTile()
    }
}
