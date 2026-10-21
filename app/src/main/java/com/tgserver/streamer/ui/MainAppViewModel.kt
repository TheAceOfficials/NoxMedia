package com.tgserver.streamer.ui

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tgserver.streamer.data.model.AuthSession
import com.tgserver.streamer.data.model.ChannelInfo
import com.tgserver.streamer.data.model.MediaItem
import com.tgserver.streamer.data.model.ServerConfig
import com.tgserver.streamer.metadata.EnrichedMetadata
import com.tgserver.streamer.metadata.MetadataEnricher
import com.tgserver.streamer.server.LocalHttpServer
import com.tgserver.streamer.service.StreamerForegroundService
import com.tgserver.streamer.telegram.TelegramClientManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import com.tgserver.streamer.update.AppReleaseInfo
import com.tgserver.streamer.update.AppUpdateManager
import com.tgserver.streamer.update.UpdateUiState
import java.io.File
import java.util.concurrent.TimeUnit
import org.json.JSONArray
import org.json.JSONObject

enum class AppTab {
    HOME, CHANNELS, LIBRARY, SETTINGS
}

data class TelemetryUiState(
    val isRunning: Boolean = false, // Server OFF by default until user turns it on
    val port: Int = 7788,
    val localIp: String = "127.0.0.1",
    val manifestUrl: String = "http://127.0.0.1:7788/manifest.json",
    val uptimeFormatted: String = "0s",
    val liveSpeedFormatted: String = "0 B/s",
    val sessionDataFormatted: String = "0.0 MB",
    val allTimeDataFormatted: String = "0.0 MB",
    val memoryUsedMb: Long = 20,
    val cpuPercent: Double = 0.0
)

class MainAppViewModel(application: Application) : AndroidViewModel(application) {

    /** Tracks whether splash screen has completed during this app session (survives rotation) */
    var isSplashCompleted by mutableStateOf(false)

    /** In-App Auto Updater instance */
    val updateManager = AppUpdateManager(application.applicationContext)
    val updateState: StateFlow<UpdateUiState> = updateManager.updateState
    val isUpdateDismissed: StateFlow<Boolean> = updateManager.isDismissed

    private val tgClient: TelegramClientManager =
        StreamerForegroundService.activeTgClient ?: TelegramClientManager(application.applicationContext)

    private val prefs = application.getSharedPreferences("tg_server_prefs", Context.MODE_PRIVATE)

    val session: StateFlow<AuthSession> = tgClient.sessionState
    val channels: StateFlow<List<ChannelInfo>> = tgClient.channelsState
    val media: StateFlow<List<MediaItem>> = tgClient.mediaState
    val unmatchedMedia: StateFlow<List<com.tgserver.streamer.data.model.UnmatchedItem>> = tgClient.unmatchedState
    val scanProgress: StateFlow<Map<Long, com.tgserver.streamer.data.model.ScanProgressState>> = tgClient.scanProgress
    val apiCredentials: StateFlow<Pair<Int, String>> = tgClient.apiCredentials

    private val _selectedTab = MutableStateFlow(AppTab.HOME)
    val selectedTab: StateFlow<AppTab> = _selectedTab.asStateFlow()

    private val _telemetry = MutableStateFlow(TelemetryUiState())
    val telemetry: StateFlow<TelemetryUiState> = _telemetry.asStateFlow()

    private val _config = MutableStateFlow(StreamerForegroundService.activeConfig)
    val config: StateFlow<ServerConfig> = _config.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedCategory = MutableStateFlow("all")
    val selectedCategory: StateFlow<String> = _selectedCategory.asStateFlow()

    private val _isSendingCode = MutableStateFlow(false)
    val isSendingCode: StateFlow<Boolean> = _isSendingCode.asStateFlow()

    private val _isVerifyingCode = MutableStateFlow(false)
    val isVerifyingCode: StateFlow<Boolean> = _isVerifyingCode.asStateFlow()

    private val _codeSentSuccess = MutableStateFlow(false)
    val codeSentSuccess: StateFlow<Boolean> = _codeSentSuccess.asStateFlow()

    val codeDeliveryType: StateFlow<String> = tgClient.codeDeliveryType

    private val _is2FARequired = MutableStateFlow(false)
    val is2FARequired: StateFlow<Boolean> = _is2FARequired.asStateFlow()

    private val _isVerifying2FA = MutableStateFlow(false)
    val isVerifying2FA: StateFlow<Boolean> = _isVerifying2FA.asStateFlow()

    private val _isImportingSession = MutableStateFlow(false)
    val isImportingSession: StateFlow<Boolean> = _isImportingSession.asStateFlow()

    private val _sessionImportError = MutableStateFlow<String?>(null)
    val sessionImportError: StateFlow<String?> = _sessionImportError.asStateFlow()

    /** True while we are validating API credentials against Telegram servers */
    private val _isValidatingApi = MutableStateFlow(false)
    val isValidatingApi: StateFlow<Boolean> = _isValidatingApi.asStateFlow()

    /** Holds any API validation error message */
    private val _apiValidationError = MutableStateFlow<String?>(null)
    val apiValidationError: StateFlow<String?> = _apiValidationError.asStateFlow()

    private val _isAddingChannel = MutableStateFlow(false)
    val isAddingChannel: StateFlow<Boolean> = _isAddingChannel.asStateFlow()

    private val _scanningChannelIds = MutableStateFlow<Set<Long>>(emptySet())
    val scanningChannelIds: StateFlow<Set<Long>> = _scanningChannelIds.asStateFlow()

    private val _isScanningAll = MutableStateFlow(false)
    val isScanningAll: StateFlow<Boolean> = _isScanningAll.asStateFlow()

    private val _isBackingUpToCloud = MutableStateFlow(false)
    val isBackingUpToCloud: StateFlow<Boolean> = _isBackingUpToCloud.asStateFlow()

    private val _isRestoringFromCloud = MutableStateFlow(false)
    val isRestoringFromCloud: StateFlow<Boolean> = _isRestoringFromCloud.asStateFlow()

    private val startTime = System.currentTimeMillis()

    init {
        val loaded = loadStoredConfig()
        _config.value = loaded
        StreamerForegroundService.activeConfig = loaded
        tgClient.setBackupJsonProvider { exportBackupJson() }
        viewModelScope.launch(Dispatchers.IO) {
            tgClient.cleanupSplitDuplicates()
            if (session.value.isLoggedIn) {
                tgClient.refreshUserProfile()
            }
            // Auto-check for GitHub app updates in background
            updateManager.checkForUpdate(manual = false)
        }
        // Ensure Alexa voice bridge is active if enabled in preferences
        if (loaded.alexaVoiceControlEnabled) {
            val context = getApplication<Application>().applicationContext
            val intent = Intent(context, StreamerForegroundService::class.java).apply {
                action = StreamerForegroundService.ACTION_UPDATE_ALEXA
            }
            try {
                if (!StreamerForegroundService.isServiceRunning) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(intent)
                    } else {
                        context.startService(intent)
                    }
                } else {
                    context.startService(intent)
                }
            } catch (_: Exception) {}
        }

        startTelemetryLoop()
    }

    fun checkForAppUpdate(manual: Boolean = true) {
        viewModelScope.launch {
            updateManager.checkForUpdate(manual = manual)
        }
    }

    fun startDownloadUpdate(info: AppReleaseInfo) {
        viewModelScope.launch {
            updateManager.downloadAndInstall(info)
        }
    }

    fun dismissUpdateBanner() {
        updateManager.dismissBanner()
    }

    fun installDownloadedApk(file: File) {
        updateManager.installApk(getApplication(), file)
    }

    fun getAppVersion(): String = updateManager.getAppCurrentVersion()
    fun getDeviceArchitecture(): String = updateManager.getDeviceArchitecture()

    fun refreshUserProfile() {
        viewModelScope.launch(Dispatchers.IO) {
            tgClient.refreshUserProfile()
        }
    }

    private val _selectedMediaGroupKey = MutableStateFlow<String?>(null)
    val selectedMediaGroupKey: StateFlow<String?> = _selectedMediaGroupKey.asStateFlow()

    fun openMediaDetails(groupKey: String) {
        _selectedMediaGroupKey.value = groupKey
        _selectedTab.value = AppTab.LIBRARY
    }

    fun clearSelectedMediaGroup() {
        _selectedMediaGroupKey.value = null
    }

    fun selectTab(tab: AppTab) {
        _selectedTab.value = tab
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setSelectedCategory(cat: String) {
        _selectedCategory.value = cat
    }

    private fun startTelemetryLoop() {
        com.tgserver.streamer.server.StreamTrafficStats.init(getApplication())
        viewModelScope.launch {
            while (true) {
                val server = StreamerForegroundService.activeServer
                val ip = server?.getLocalIpAddress() ?: LocalHttpServer.getLocalIpAddress()
                val port = _config.value.port
                val isRunning = StreamerForegroundService.isServiceRunning
                val sTime = StreamerForegroundService.serverStartTime
                val uptimeSec = if (isRunning && sTime > 0L) {
                    maxOf(0L, (System.currentTimeMillis() - sTime) / 1000)
                } else 0L

                val h = uptimeSec / 3600
                val m = (uptimeSec % 3600) / 60
                val s = uptimeSec % 60
                val uptimeStr = when {
                    h > 0 -> "${h}h ${m}m"
                    m > 0 -> "${m}m ${s}s"
                    else -> "${s}s"
                }

                val speedBps = if (isRunning) com.tgserver.streamer.server.StreamTrafficStats.calculateLiveSpeed() else 0L
                val sessionBytes = if (isRunning) com.tgserver.streamer.server.StreamTrafficStats.getSessionBytes() else 0L
                val allTimeBytes = com.tgserver.streamer.server.StreamTrafficStats.getLifetimeBytes()

                val runtime = Runtime.getRuntime()
                val usedMem = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)

                _telemetry.value = TelemetryUiState(
                    isRunning = isRunning,
                    port = port,
                    localIp = ip,
                    manifestUrl = "http://$ip:$port/manifest.json",
                    uptimeFormatted = uptimeStr,
                    liveSpeedFormatted = com.tgserver.streamer.server.StreamTrafficStats.formatSpeed(speedBps),
                    sessionDataFormatted = com.tgserver.streamer.server.StreamTrafficStats.formatBytes(sessionBytes),
                    allTimeDataFormatted = com.tgserver.streamer.server.StreamTrafficStats.formatBytes(allTimeBytes),
                    memoryUsedMb = usedMem,
                    cpuPercent = if (isRunning) 1.2 else 0.0
                )
                delay(1000)
            }
        }
    }

    /**
     * Validates API credentials against Telegram, then saves them if valid.
     * Calls onResult(success, errorMessage)
     */
    fun validateAndSaveApiCredentials(apiId: Int, apiHash: String, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            _isValidatingApi.value = true
            _apiValidationError.value = null
            val res = tgClient.validateApiCredentials(apiId, apiHash)
            _isValidatingApi.value = false
            if (res.isSuccess) {
                tgClient.saveApiCredentials(apiId, apiHash)
                _config.value.telegramApiId = apiId
                _config.value.telegramApiHash = apiHash
                onResult(true, null)
            } else {
                val msg = res.exceptionOrNull()?.message ?: "Invalid API credentials"
                _apiValidationError.value = msg
                onResult(false, msg)
            }
        }
    }

    fun saveApiCredentials(apiId: Int, apiHash: String) {
        tgClient.saveApiCredentials(apiId, apiHash)
        _config.value.telegramApiId = apiId
        _config.value.telegramApiHash = apiHash
    }

    fun clearApiCredentials() {
        tgClient.saveApiCredentials(0, "")
        _config.value.telegramApiId = 0
        _config.value.telegramApiHash = ""
    }

    fun toggleServer() {
        val context = getApplication<Application>().applicationContext
        val isCurrentlyRunning = StreamerForegroundService.isServiceRunning

        if (!isCurrentlyRunning) {
            // Start the server
            val intent = Intent(context, StreamerForegroundService::class.java).apply {
                action = StreamerForegroundService.ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        } else {
            // Stop the server
            val intent = Intent(context, StreamerForegroundService::class.java).apply {
                action = StreamerForegroundService.ACTION_STOP
            }
            context.startService(intent)
        }

        // Optimistically update UI; telemetry loop will confirm in 3s
        val newState = !isCurrentlyRunning
        _config.value.isRunning = newState
        _telemetry.value = _telemetry.value.copy(isRunning = newState)
    }

    fun copyToClipboard(text: String, label: String = "Link copied") {
        val clipboard = getApplication<Application>().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("NoxMedia", text))
        Toast.makeText(getApplication(), label, Toast.LENGTH_SHORT).show()
    }

    // --- Channels Actions ---

    fun addChannel(identifier: String, isAnime: Boolean = false, onDone: (Boolean, String?) -> Unit = { _, _ -> }) {
        viewModelScope.launch {
            _isAddingChannel.value = true
            val result = tgClient.resolveAndAddChannel(identifier, isAnime)
            _isAddingChannel.value = false
            if (result.isSuccess) {
                val channel = result.getOrNull()!!
                Toast.makeText(getApplication(), "✓ Connected: ${channel.title}", Toast.LENGTH_SHORT).show()
                onDone(true, null)
                // Auto scan newly added channel
                syncChannel(channel.id)
            } else {
                val err = result.exceptionOrNull()?.message ?: "Error connecting channel"
                Toast.makeText(getApplication(), err, Toast.LENGTH_LONG).show()
                onDone(false, err)
            }
        }
    }

    fun syncChannel(channelId: Long) {
        viewModelScope.launch {
            _scanningChannelIds.value = _scanningChannelIds.value + channelId
            val ch = channels.value.find { it.id == channelId }
            val res = tgClient.scanChannelLive(channelId, incremental = true)
            _scanningChannelIds.value = _scanningChannelIds.value - channelId
            if (res.isSuccess) {
                val newCount = res.getOrDefault(0)
                val totalCount = channels.value.find { it.id == channelId }?.videoCount ?: newCount
                if (newCount > 0) {
                    Toast.makeText(getApplication(), "✓ Synced: $newCount new video(s) added (${ch?.title ?: "channel"})", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(getApplication(), "✓ ${ch?.title ?: "Channel"} is up to date ($totalCount videos)", Toast.LENGTH_SHORT).show()
                }
            } else {
                val err = res.exceptionOrNull()?.message ?: "Scan failed"
                Toast.makeText(getApplication(), "Error: $err", Toast.LENGTH_LONG).show()
            }
        }
    }

    fun stopScan(channelId: Long) {
        tgClient.stopChannelScan(channelId)
        _scanningChannelIds.value = _scanningChannelIds.value - channelId
        Toast.makeText(getApplication(), "Scan stopped", Toast.LENGTH_SHORT).show()
    }

    fun syncAllChannels() {
        viewModelScope.launch {
            val activeChannels = channels.value.filter { it.isActive }
            if (activeChannels.isEmpty()) {
                Toast.makeText(getApplication(), "No active channels to scan", Toast.LENGTH_SHORT).show()
                return@launch
            }
            _isScanningAll.value = true
            var totalNew = 0
            for (ch in activeChannels) {
                _scanningChannelIds.value = _scanningChannelIds.value + ch.id
                val res = tgClient.scanChannelLive(ch.id, incremental = true)
                _scanningChannelIds.value = _scanningChannelIds.value - ch.id
                if (res.isSuccess) {
                    totalNew += res.getOrDefault(0)
                }
            }
            _isScanningAll.value = false
            if (totalNew > 0) {
                Toast.makeText(getApplication(), "✓ Sync complete: $totalNew new video(s) added across active channels", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(getApplication(), "✓ All channels are up to date", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun rescanFromScratch(channelId: Long) {
        viewModelScope.launch {
            _scanningChannelIds.value = _scanningChannelIds.value + channelId
            val ch = channels.value.find { it.id == channelId }
            val res = tgClient.rescanFromScratch(channelId)
            _scanningChannelIds.value = _scanningChannelIds.value - channelId
            if (res.isSuccess) {
                val count = res.getOrDefault(0)
                Toast.makeText(getApplication(), "✓ Clean rescan: $count videos found for ${ch?.title ?: "channel"}", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(getApplication(), "Rescan failed: ${res.exceptionOrNull()?.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun refreshChannelAvatar(channelId: Long) {
        viewModelScope.launch {
            tgClient.refreshChannelAvatar(channelId)
        }
    }

    // --- Unmatched Content Actions ---

    fun removeUnmatched(unmatchedId: String) {
        tgClient.removeUnmatchedItem(unmatchedId)
    }

    fun clearAllUnmatched(channelId: Long? = null) {
        tgClient.clearUnmatched(channelId)
        Toast.makeText(getApplication(), "Cleared unmatched items", Toast.LENGTH_SHORT).show()
    }

    fun matchUnmatchedItem(unmatchedId: String, matchedMeta: EnrichedMetadata, onDone: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            val item = tgClient.matchUnmatchedItem(unmatchedId, matchedMeta)
            if (item != null) {
                Toast.makeText(getApplication(), "✓ Matched: ${item.title}", Toast.LENGTH_SHORT).show()
                onDone(true)
            } else {
                Toast.makeText(getApplication(), "Could not match item", Toast.LENGTH_SHORT).show()
                onDone(false)
            }
        }
    }

    fun addUnmatchedToLibraryAnyway(unmatchedId: String) {
        val item = tgClient.addUnmatchedToLibraryAnyway(unmatchedId)
        if (item != null) {
            Toast.makeText(getApplication(), "✓ Added to library: ${item.title}", Toast.LENGTH_SHORT).show()
        }
    }

    fun toggleAnimeChannel(channelId: Long) {
        val ch = channels.value.find { it.id == channelId } ?: return
        val newState = !ch.isAnime
        tgClient.toggleAnimeChannel(channelId, newState)
        val msg = if (newState) "✓ Marked as Anime channel (Kitsu absolute matching enabled)" else "✓ Unmarked Anime channel"
        Toast.makeText(getApplication(), msg, Toast.LENGTH_SHORT).show()
    }

    fun toggleChannel(channelId: Long, active: Boolean) {
        tgClient.toggleChannel(channelId, active)
    }

    fun removeChannel(channelId: Long) {
        tgClient.removeChannel(channelId)
        Toast.makeText(getApplication(), "Channel removed", Toast.LENGTH_SHORT).show()
    }

    fun refreshMediaMetadata(item: MediaItem) {
        viewModelScope.launch {
            val enriched = tgClient.enrichExistingMedia(item.id)
            if (enriched != null) {
                Toast.makeText(getApplication(), "✓ Metadata enriched: ${enriched.title}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun refreshAllLibraryMetadata() {
        viewModelScope.launch {
            val all = tgClient.getAllMedia()
            var count = 0
            for (item in all) {
                val enriched = tgClient.enrichExistingMedia(item.id)
                if (enriched != null) count++
            }
            Toast.makeText(getApplication(), "✓ Enriched metadata for $count items", Toast.LENGTH_SHORT).show()
        }
    }

    fun toggleHideMedia(itemId: String): Boolean {
        val newState = tgClient.toggleHideMediaItem(itemId)
        val msg = if (newState) "Item hidden from Stremio" else "Item unhidden"
        Toast.makeText(getApplication(), msg, Toast.LENGTH_SHORT).show()
        return newState
    }

    suspend fun searchMetadataCandidates(query: String, type: String): List<EnrichedMetadata> = withContext(Dispatchers.IO) {
        val tmdbKey = prefs.getString("tmdb_api_key", "") ?: ""
        MetadataEnricher.searchCandidates(query, type, tmdbKey)
    }

    fun applyManualMetadata(groupItems: List<MediaItem>, meta: EnrichedMetadata) {
        viewModelScope.launch(Dispatchers.IO) {
            val tmdbKey = prefs.getString("tmdb_api_key", "") ?: ""
            val (enriched, episodeMap) = MetadataEnricher.fetchFullMetadataDetails(meta, tmdbKey)

            val updated = groupItems.map { item ->
                val s = item.season
                val e = item.episode
                val newEpTitle = if (s != null && e != null) {
                    episodeMap[Pair(s, e)] ?: item.episodeTitle
                } else {
                    item.episodeTitle
                }

                item.copy(
                    title = enriched.title ?: meta.title ?: item.title,
                    year = enriched.year ?: meta.year ?: item.year,
                    overview = enriched.overview ?: meta.overview ?: item.overview,
                    posterUrl = enriched.posterUrl ?: meta.posterUrl ?: item.posterUrl,
                    backdropUrl = enriched.backdropUrl ?: meta.backdropUrl ?: item.backdropUrl,
                    episodeTitle = newEpTitle,
                    imdbId = enriched.imdbId ?: meta.imdbId,
                    tmdbId = enriched.tmdbId ?: meta.tmdbId
                )
            }

            // Invalidate caches for both old and new titles
            val oldTitle = groupItems.firstOrNull()?.title
            if (!oldTitle.isNullOrBlank()) {
                MetadataEnricher.invalidateTitle(oldTitle)
            }
            if (!meta.title.isNullOrBlank()) {
                MetadataEnricher.invalidateTitle(meta.title)
            }
            StreamerForegroundService.activeServer?.clearTitleCache()

            tgClient.updateMediaItems(updated)
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    getApplication(),
                    "✓ Metadata synced: ${enriched.title ?: meta.title} (${enriched.year ?: meta.year})",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    // --- Telegram Auth Actions ---

    fun validatePhoneNumber(phone: String): String? {
        return tgClient.validatePhoneNumber(phone)
    }

    fun sendAuthCode(phoneNumber: String) {
        viewModelScope.launch {
            _isSendingCode.value = true
            val res = tgClient.sendLoginCode(phoneNumber)
            _isSendingCode.value = false
            if (res.isSuccess) {
                _codeSentSuccess.value = true
                val msg = res.getOrNull() ?: "Code sent successfully"
                Toast.makeText(getApplication(), "✓ $msg", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(
                    getApplication(),
                    res.exceptionOrNull()?.message ?: "Failed to send code",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    fun resendAuthCode(phoneNumber: String) {
        viewModelScope.launch {
            _isSendingCode.value = true
            // Use proper resend_code MTProto call (keeps same phone_code_hash, switches delivery channel)
            val res = tgClient.resendLoginCode()
            _isSendingCode.value = false
            if (res.isSuccess) {
                _codeSentSuccess.value = true
                Toast.makeText(getApplication(), "✓ ${res.getOrNull()}", Toast.LENGTH_LONG).show()
            } else {
                // Fallback: if resend fails (e.g. session lost), do fresh send
                val err = res.exceptionOrNull()?.message ?: ""
                if (err.contains("Session expired", ignoreCase = true)) {
                    sendAuthCode(phoneNumber)
                } else {
                    Toast.makeText(getApplication(), err.ifBlank { "Failed to resend code" }, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    fun verifyAuthCode(code: String) {
        viewModelScope.launch {
            _isVerifyingCode.value = true
            val res = tgClient.verifyLoginCode(code)
            _isVerifyingCode.value = false
            if (res.isSuccess) {
                _codeSentSuccess.value = false
                _is2FARequired.value = false
                Toast.makeText(getApplication(), "✓ Logged in successfully!", Toast.LENGTH_SHORT).show()
                autoRestoreCloudBackupSilently()
            } else {
                val err = res.exceptionOrNull()?.message ?: "Invalid code"
                if (err == "2FA_REQUIRED") {
                    _is2FARequired.value = true
                    Toast.makeText(getApplication(), "2-Step verification password required", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(getApplication(), err, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    fun verify2FAPassword(password: String) {
        viewModelScope.launch {
            _isVerifying2FA.value = true
            val res = tgClient.verify2FAPassword(password)
            _isVerifying2FA.value = false
            if (res.isSuccess) {
                _codeSentSuccess.value = false
                _is2FARequired.value = false
                Toast.makeText(getApplication(), "✓ 2FA Verified! Logged in successfully!", Toast.LENGTH_SHORT).show()
                autoRestoreCloudBackupSilently()
            } else {
                val err = res.exceptionOrNull()?.message ?: "Incorrect 2FA password"
                Toast.makeText(getApplication(), err, Toast.LENGTH_LONG).show()
            }
        }
    }

    fun cancelPhoneAuth() {
        _codeSentSuccess.value = false
        _is2FARequired.value = false
    }

    fun importSessionString(sessionStr: String) {
        viewModelScope.launch {
            _isImportingSession.value = true
            _sessionImportError.value = null
            val res = tgClient.importSessionString(sessionStr)
            _isImportingSession.value = false
            if (res.isSuccess) {
                Toast.makeText(getApplication(), "✓ Connected to Telegram successfully!", Toast.LENGTH_SHORT).show()
                autoRestoreCloudBackupSilently()
            } else {
                val err = res.exceptionOrNull()?.message ?: "Invalid session string"
                _sessionImportError.value = err
                Toast.makeText(getApplication(), err, Toast.LENGTH_LONG).show()
            }
        }
    }

    fun loginWithWebSession(firstName: String, username: String, userId: Long, sessionStr: String) {
        viewModelScope.launch {
            val res = tgClient.loginWithWebSession(firstName, username, userId, sessionStr)
            if (res.isSuccess) {
                Toast.makeText(getApplication(), "✓ Telegram Login Successful!", Toast.LENGTH_SHORT).show()
                autoRestoreCloudBackupSilently()
            }
        }
    }

    fun autoRestoreCloudBackupSilently() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                Log.d("MainAppViewModel", "Checking Telegram Saved Messages for cloud backup after login...")
                val res = tgClient.fetchLatestBackupFromTelegram("me")
                val json = res.getOrNull()
                if (!json.isNullOrBlank()) {
                    withContext(Dispatchers.Main) {
                        val (settingsRestored, channelsRestored) = importBackupJsonDetailed(json)
                        if (settingsRestored || channelsRestored > 0) {
                            val msg = "✓ Cloud backup restored: $channelsRestored channels & settings synced!"
                            Toast.makeText(getApplication(), msg, Toast.LENGTH_LONG).show()
                            if (channelsRestored > 0) {
                                syncAllChannels()
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w("MainAppViewModel", "Silent auto-restore on login: ${e.message}")
            }
        }
    }

    fun logout() {
        val freed = clearAppCache()
        tgClient.logout()
        _codeSentSuccess.value = false
        val freedMb = freed / (1024 * 1024)
        val msg = if (freedMb > 0) "✓ Logged out • ${freedMb}MB cache cleared" else "✓ Logged out • Cache cleared"
        Toast.makeText(getApplication(), msg, Toast.LENGTH_SHORT).show()
    }

    // --- Settings Actions & Persistence ---

    private fun loadStoredConfig(): ServerConfig {
        return ServerConfig(
            port = prefs.getInt("server_port", 7788),
            bufferChunkSizeKb = prefs.getInt("buffer_chunk_kb", 512),
            diskCacheMb = prefs.getInt("disk_cache_mb", 2048),
            bufferPipelineMb = prefs.getInt("buffer_pipeline_mb", 20),
            customPosterUrl = prefs.getString("custom_poster_url", "") ?: "",
            customPosterApiKey = prefs.getString("custom_poster_api_key", "") ?: "",
            tmdbApiKey = prefs.getString("tmdb_api_key", "") ?: "",
            tvdbApiKey = prefs.getString("tvdb_api_key", "") ?: "",
            preferredQuality = prefs.getString("preferred_quality", "Any") ?: "Any",
            hideCamTs = prefs.getBoolean("hide_cam_ts", false),
            remotePublicUrl = prefs.getString("remote_public_url", "") ?: "",
            startOnBoot = prefs.getBoolean("start_on_boot", false),
            autoScanNewContent = prefs.getBoolean("auto_scan_new_content", true),
            replaceSameQuality = prefs.getBoolean("replace_same_quality", false),
            skipExactDuplicates = prefs.getBoolean("skip_exact_duplicates", true),
            autoBackupEnabled = prefs.getBoolean("auto_backup_enabled", false),
            autoBackupIntervalHours = prefs.getInt("auto_backup_interval_hours", 12),
            autoSleepEnabled = prefs.getBoolean("auto_sleep_enabled", false),
            autoSleepTimeoutMinutes = prefs.getInt("auto_sleep_timeout_minutes", 30),
            alexaVoiceControlEnabled = prefs.getBoolean("alexa_voice_control_enabled", false),
            alexaDeviceName = prefs.getString("alexa_device_name", "NoxMedia") ?: "NoxMedia"
        )
    }

    private fun saveStoredConfig(cfg: ServerConfig) {
        prefs.edit().apply {
            putInt("server_port", cfg.port)
            putInt("buffer_chunk_kb", cfg.bufferChunkSizeKb)
            putInt("disk_cache_mb", cfg.diskCacheMb)
            putInt("buffer_pipeline_mb", cfg.bufferPipelineMb)
            putString("custom_poster_url", cfg.customPosterUrl)
            putString("custom_poster_api_key", cfg.customPosterApiKey)
            putString("tmdb_api_key", cfg.tmdbApiKey)
            putString("tvdb_api_key", cfg.tvdbApiKey)
            putString("preferred_quality", cfg.preferredQuality)
            putBoolean("hide_cam_ts", cfg.hideCamTs)
            putString("remote_public_url", cfg.remotePublicUrl)
            putBoolean("start_on_boot", cfg.startOnBoot)
            putBoolean("auto_scan_new_content", cfg.autoScanNewContent)
            putBoolean("replace_same_quality", cfg.replaceSameQuality)
            putBoolean("skip_exact_duplicates", cfg.skipExactDuplicates)
            putBoolean("auto_backup_enabled", cfg.autoBackupEnabled)
            putInt("auto_backup_interval_hours", cfg.autoBackupIntervalHours)
            putBoolean("auto_sleep_enabled", cfg.autoSleepEnabled)
            putInt("auto_sleep_timeout_minutes", cfg.autoSleepTimeoutMinutes)
            putBoolean("alexa_voice_control_enabled", cfg.alexaVoiceControlEnabled)
            putString("alexa_device_name", cfg.alexaDeviceName)
            apply()
        }
        _config.value = cfg
        StreamerForegroundService.activeConfig = cfg
        tgClient.updateStreamCacheConfig(cfg.diskCacheMb, cfg.bufferPipelineMb)
        if (cfg.skipExactDuplicates) {
            tgClient.cleanupSplitDuplicates()
        }
    }

    fun cleanupDuplicates(): Int {
        return tgClient.cleanupSplitDuplicates()
    }

    suspend fun verifyAndSaveMetadataKeys(tmdb: String, tvdb: String): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val client = OkHttpClient.Builder()
            .connectTimeout(7, TimeUnit.SECONDS)
            .readTimeout(7, TimeUnit.SECONDS)
            .build()

        val cleanTmdb = tmdb.trim()
        val cleanTvdb = tvdb.trim()

        // 1. Verify TMDB API Key if provided
        if (cleanTmdb.isNotEmpty()) {
            try {
                val req = Request.Builder()
                    .url("https://api.themoviedb.org/3/movie/550?api_key=$cleanTmdb")
                    .get()
                    .build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        return@withContext Pair(false, "Invalid TMDB API Key (HTTP ${resp.code})")
                    }
                }
            } catch (e: Exception) {
                return@withContext Pair(false, "TMDB check failed: ${e.localizedMessage ?: "Network error"}")
            }
        }

        // 2. Verify TVDB API Key if provided
        if (cleanTvdb.isNotEmpty()) {
            try {
                val jsonBody = """{"apikey":"$cleanTvdb"}"""
                val body = jsonBody.toRequestBody("application/json".toMediaType())
                val req = Request.Builder()
                    .url("https://api4.thetvdb.com/v4/login")
                    .post(body)
                    .build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        return@withContext Pair(false, "Invalid TVDB API Key (HTTP ${resp.code})")
                    }
                }
            } catch (e: Exception) {
                return@withContext Pair(false, "TVDB check failed: ${e.localizedMessage ?: "Network error"}")
            }
        }

        // Both verified or cleared
        updateMetadataProviders(cleanTmdb, cleanTvdb)
        return@withContext Pair(true, "✓ Metadata keys verified and saved!")
    }

    fun updateMetadataProviders(tmdb: String, tvdb: String) {
        val updated = _config.value.copy(
            tmdbApiKey = tmdb.trim(),
            tvdbApiKey = tvdb.trim()
        )
        saveStoredConfig(updated)
        // Immediately kick off fast parallel enrichment for all indexed media missing posters
        tgClient.autoEnrichMissingMetadata(forceRecheckNoPoster = true)
    }

    fun updatePosterSettings(url: String) {
        val updated = _config.value.copy(
            customPosterUrl = url.trim()
        )
        saveStoredConfig(updated)
        Toast.makeText(getApplication(), "✓ Poster template updated", Toast.LENGTH_SHORT).show()
    }

    fun updateStreamingSettings(quality: String, hideCam: Boolean) {
        val updated = _config.value.copy(
            preferredQuality = quality,
            hideCamTs = hideCam
        )
        saveStoredConfig(updated)
        Toast.makeText(getApplication(), "✓ Streaming preferences saved", Toast.LENGTH_SHORT).show()
    }

    fun updateServerNetwork(port: Int, diskCacheMb: Int, bufferPipelineMb: Int, remoteUrl: String, startOnBoot: Boolean) {
        val updated = _config.value.copy(
            port = port,
            diskCacheMb = diskCacheMb,
            bufferPipelineMb = bufferPipelineMb,
            remotePublicUrl = remoteUrl.trim(),
            startOnBoot = startOnBoot
        )
        saveStoredConfig(updated)
        Toast.makeText(getApplication(), "✓ Server settings saved (Port: $port, Cache: ${diskCacheMb}MB, Buffer: ${bufferPipelineMb}MB)", Toast.LENGTH_SHORT).show()
    }

    fun restoreServerDefaults(): ServerConfig {
        val current = _config.value
        val updated = current.copy(
            port = 7788,
            diskCacheMb = 2048,
            bufferPipelineMb = 20,
            autoSleepEnabled = false,
            autoSleepTimeoutMinutes = 30
        )
        saveStoredConfig(updated)
        Toast.makeText(getApplication(), "✓ Defaults restored: Cache 2048MB, Buffer 20MB, Port 7788", Toast.LENGTH_SHORT).show()
        return updated
    }

    fun updateAutoSleep(enabled: Boolean, timeoutMinutes: Int) {
        val updated = _config.value.copy(
            autoSleepEnabled = enabled,
            autoSleepTimeoutMinutes = timeoutMinutes
        )
        saveStoredConfig(updated)
        if (enabled && StreamerForegroundService.isServiceRunning) {
            com.tgserver.streamer.server.StreamTrafficStats.notifyActivity()
        }
        val msg = if (enabled) {
            val durationText = when (timeoutMinutes) {
                15 -> "15 min"
                30 -> "30 min"
                60 -> "1 hour"
                120 -> "2 hours"
                360 -> "6 hours"
                else -> "${timeoutMinutes}m"
            }
            "✓ Auto Sleep set to $durationText"
        } else {
            "✓ Auto Sleep disabled"
        }
        Toast.makeText(getApplication(), msg, Toast.LENGTH_SHORT).show()
    }

    fun updateAlexaVoiceControl(enabled: Boolean, deviceName: String) {
        val cleanName = deviceName.trim().ifBlank { "NoxMedia" }
        val updated = _config.value.copy(
            alexaVoiceControlEnabled = enabled,
            alexaDeviceName = cleanName
        )
        saveStoredConfig(updated)

        // Notify foreground service of updated Alexa state
        val context = getApplication<Application>().applicationContext
        val intent = Intent(context, StreamerForegroundService::class.java).apply {
            action = StreamerForegroundService.ACTION_UPDATE_ALEXA
        }
        try {
            if (enabled && !StreamerForegroundService.isServiceRunning) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } else {
                context.startService(intent)
            }
        } catch (_: Exception) {}

        val msg = if (enabled) {
            "✓ Alexa Voice Control active for \"$cleanName\". Say 'Alexa, discover devices'."
        } else {
            "✓ Alexa Voice Control disabled"
        }
        Toast.makeText(getApplication(), msg, Toast.LENGTH_LONG).show()
    }

    fun detectTailscaleIp(): String? {
        return tgClient.detectTailscaleAddress()
    }

    fun updateChannelAutomation(autoScan: Boolean, replaceSameQuality: Boolean, skipExact: Boolean) {
        val updated = _config.value.copy(
            autoScanNewContent = autoScan,
            replaceSameQuality = replaceSameQuality,
            skipExactDuplicates = skipExact
        )
        saveStoredConfig(updated)
        if (autoScan) {
            tgClient.startAutoScanLoop()
        } else {
            tgClient.stopAutoScanLoop()
        }
        Toast.makeText(getApplication(), "✓ Channel automation rules updated", Toast.LENGTH_SHORT).show()
    }

    fun updateBackupSettings(enabled: Boolean, intervalHours: Int) {
        val updated = _config.value.copy(
            autoBackupEnabled = enabled,
            autoBackupIntervalHours = intervalHours
        )
        saveStoredConfig(updated)
        if (enabled) {
            tgClient.startAutoBackupLoop { exportBackupJson() }
            Toast.makeText(getApplication(), "✓ Auto-backup scheduled every ${intervalHours}h to Telegram", Toast.LENGTH_SHORT).show()
        } else {
            tgClient.stopAutoBackupLoop()
            Toast.makeText(getApplication(), "✓ Auto-backup disabled", Toast.LENGTH_SHORT).show()
        }
    }

    fun scanDuplicates(): Int = tgClient.scanDuplicates()

    fun deleteDuplicates(): Int = tgClient.deleteDuplicates()

    fun scanDeadFiles(): Int = tgClient.scanDeadFiles()

    suspend fun scanDeadFilesRemote(): Int = tgClient.scanDeadFilesRemote()

    fun purgeDeadFiles(): Int = tgClient.purgeDeadFiles()

    fun refreshContentGroup(groupItems: List<MediaItem>, onFinished: ((Triple<Int, Int, Int>) -> Unit)? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = tgClient.refreshContentGroup(groupItems)
            withContext(Dispatchers.Main) {
                val (aliveCount, deadPurged, newAdded) = result
                val msg = buildString {
                    append("✓ Refreshed: $aliveCount active")
                    if (deadPurged > 0) append(", $deadPurged dead removed")
                    if (newAdded > 0) append(", $newAdded new added")
                }
                Toast.makeText(getApplication(), msg, Toast.LENGTH_SHORT).show()
                onFinished?.invoke(result)
            }
        }
    }

    fun getAppCacheSizeBytes(): Long = tgClient.getAppCacheSizeBytes()

    fun clearAppCache(): Long {
        val freed = tgClient.clearAppCache()
        com.tgserver.streamer.ui.screens.ImageMemoryCache.clear()
        return freed
    }

    private val _appLogs = MutableStateFlow<List<String>>(emptyList())
    val appLogs: StateFlow<List<String>> = _appLogs.asStateFlow()

    private val _isRefreshingLogs = MutableStateFlow(false)
    val isRefreshingLogs: StateFlow<Boolean> = _isRefreshingLogs.asStateFlow()

    fun refreshAppLogs() {
        viewModelScope.launch(Dispatchers.IO) {
            _isRefreshingLogs.value = true
            try {
                val logs = tgClient.getAppLogs()
                _appLogs.value = logs
            } catch (e: Exception) {
                Log.e("MainAppViewModel", "Failed to refresh logs: ${e.message}")
            } finally {
                _isRefreshingLogs.value = false
            }
        }
    }

    fun clearAppLogs(): Boolean {
        val ok = tgClient.clearAppLogs()
        if (ok) {
            _appLogs.value = emptyList()
        }
        return ok
    }

    fun exportBackupJson(): String {
        val cfg = _config.value
        val channelList = channels.value
        val channelsJsonArray = JSONArray()
        channelList.forEach { ch ->
            val chObj = JSONObject().apply {
                put("id", ch.id)
                put("title", ch.title)
                if (!ch.username.isNullOrBlank()) put("username", ch.username)
                put("isAnime", ch.isAnime)
                put("isActive", ch.isActive)
                put("autoIndex", ch.autoIndex)
            }
            channelsJsonArray.put(chObj)
        }

        val settingsObj = JSONObject().apply {
            put("preferredQuality", cfg.preferredQuality)
            put("hideCamTs", cfg.hideCamTs)
            put("remotePublicUrl", cfg.remotePublicUrl)
            put("customPosterUrl", cfg.customPosterUrl)
            put("customPosterApiKey", cfg.customPosterApiKey)
            put("tmdbApiKey", cfg.tmdbApiKey)
            put("tvdbApiKey", cfg.tvdbApiKey)
            put("autoScanNewContent", cfg.autoScanNewContent)
            put("replaceSameQuality", cfg.replaceSameQuality)
            put("skipExactDuplicates", cfg.skipExactDuplicates)
            put("autoBackupEnabled", cfg.autoBackupEnabled)
            put("autoBackupIntervalHours", cfg.autoBackupIntervalHours)
        }

        val root = JSONObject().apply {
            put("version", 2)
            put("appName", "NoxMedia")
            put("timestamp", System.currentTimeMillis())
            put("settings", settingsObj)
            put("channels", channelsJsonArray)
        }

        return root.toString(2)
    }

    fun importBackupJsonDetailed(json: String): Pair<Boolean, Int> {
        return try {
            val root = JSONObject(json)
            var settingsRestored = false
            var channelsRestoredCount = 0

            val settingsObj = when {
                root.has("settings") -> root.getJSONObject("settings")
                root.has("serverConfig") -> root.getJSONObject("serverConfig")
                else -> null
            }

            if (settingsObj != null) {
                val cur = _config.value
                val updated = cur.copy(
                    preferredQuality = settingsObj.optString("preferredQuality", cur.preferredQuality),
                    hideCamTs = settingsObj.optBoolean("hideCamTs", cur.hideCamTs),
                    remotePublicUrl = settingsObj.optString("remotePublicUrl", cur.remotePublicUrl),
                    customPosterUrl = settingsObj.optString("customPosterUrl", cur.customPosterUrl),
                    customPosterApiKey = settingsObj.optString("customPosterApiKey", cur.customPosterApiKey),
                    tmdbApiKey = settingsObj.optString("tmdbApiKey", cur.tmdbApiKey),
                    tvdbApiKey = settingsObj.optString("tvdbApiKey", cur.tvdbApiKey),
                    autoScanNewContent = settingsObj.optBoolean("autoScanNewContent", cur.autoScanNewContent),
                    replaceSameQuality = settingsObj.optBoolean("replaceSameQuality", cur.replaceSameQuality),
                    skipExactDuplicates = settingsObj.optBoolean("skipExactDuplicates", cur.skipExactDuplicates),
                    autoBackupEnabled = settingsObj.optBoolean("autoBackupEnabled", cur.autoBackupEnabled),
                    autoBackupIntervalHours = settingsObj.optInt("autoBackupIntervalHours", cur.autoBackupIntervalHours)
                )
                saveStoredConfig(updated)
                settingsRestored = true
            }

            if (root.has("channels")) {
                val channelsArray = root.getJSONArray("channels")
                val channelList = mutableListOf<ChannelInfo>()
                for (i in 0 until channelsArray.length()) {
                    val chObj = channelsArray.getJSONObject(i)
                    val id = chObj.optLong("id")
                    if (id != 0L) {
                        val title = chObj.optString("title", "Channel $id")
                        val username = if (chObj.has("username") && !chObj.isNull("username")) chObj.getString("username") else null
                        val isAnime = chObj.optBoolean("isAnime", false)
                        val isActive = chObj.optBoolean("isActive", true)
                        val autoIndex = chObj.optBoolean("autoIndex", true)
                        channelList.add(
                            ChannelInfo(
                                id = id,
                                title = title,
                                username = username,
                                isAnime = isAnime,
                                isActive = isActive,
                                autoIndex = autoIndex
                            )
                        )
                    }
                }
                if (channelList.isNotEmpty()) {
                    channelsRestoredCount = tgClient.restoreChannels(channelList)
                }
            }

            Pair(settingsRestored, channelsRestoredCount)
        } catch (e: Exception) {
            Log.e("MainAppViewModel", "Failed to parse backup JSON: ${e.message}", e)
            Pair(false, 0)
        }
    }

    fun importBackupJson(json: String): Boolean {
        val (settingsRestored, channelsRestored) = importBackupJsonDetailed(json)
        return if (settingsRestored || channelsRestored > 0) {
            val msg = if (channelsRestored > 0) {
                "✓ Restored settings and $channelsRestored channel(s) from backup!"
            } else {
                "✓ Settings successfully restored from backup!"
            }
            Toast.makeText(getApplication(), msg, Toast.LENGTH_LONG).show()
            true
        } else {
            Toast.makeText(getApplication(), "Invalid backup JSON: No settings or channels found", Toast.LENGTH_SHORT).show()
            false
        }
    }

    fun backupToTelegramCloud(destination: String = "me", onDone: ((Boolean, String?) -> Unit)? = null) {
        viewModelScope.launch {
            if (!session.value.isLoggedIn) {
                Toast.makeText(getApplication(), "Telegram is not connected", Toast.LENGTH_SHORT).show()
                onDone?.invoke(false, "Telegram not connected")
                return@launch
            }
            _isBackingUpToCloud.value = true
            val backupJson = exportBackupJson()
            val res = tgClient.uploadBackupToTelegram(backupJson, destination)
            _isBackingUpToCloud.value = false
            if (res.isSuccess) {
                Toast.makeText(getApplication(), "✓ Backup saved to Telegram Saved Messages!", Toast.LENGTH_SHORT).show()
                onDone?.invoke(true, null)
            } else {
                val err = res.exceptionOrNull()?.message ?: "Upload failed"
                Toast.makeText(getApplication(), "Backup upload failed: $err", Toast.LENGTH_LONG).show()
                onDone?.invoke(false, err)
            }
        }
    }

    fun restoreFromTelegramCloud(source: String = "me", onDone: ((Boolean, String?) -> Unit)? = null) {
        viewModelScope.launch {
            if (!session.value.isLoggedIn) {
                Toast.makeText(getApplication(), "Telegram is not connected", Toast.LENGTH_SHORT).show()
                onDone?.invoke(false, "Telegram not connected")
                return@launch
            }
            _isRestoringFromCloud.value = true
            val res = tgClient.fetchLatestBackupFromTelegram(source)
            _isRestoringFromCloud.value = false
            if (res.isSuccess) {
                val json = res.getOrNull()
                if (json.isNullOrBlank()) {
                    Toast.makeText(getApplication(), "No backup found in Telegram", Toast.LENGTH_SHORT).show()
                    onDone?.invoke(false, "No backup found")
                } else {
                    val (settingsRestored, channelsRestored) = importBackupJsonDetailed(json)
                    if (settingsRestored || channelsRestored > 0) {
                        Toast.makeText(
                            getApplication(),
                            "✓ Cloud backup restored: $channelsRestored channels & settings synced!",
                            Toast.LENGTH_LONG
                        ).show()
                        if (channelsRestored > 0) {
                            syncAllChannels()
                        }
                        onDone?.invoke(true, null)
                    } else {
                        Toast.makeText(getApplication(), "Backup file did not contain valid settings", Toast.LENGTH_SHORT).show()
                        onDone?.invoke(false, "Invalid format")
                    }
                }
            } else {
                val err = res.exceptionOrNull()?.message ?: "Fetch failed"
                Toast.makeText(getApplication(), "Failed to fetch backup: $err", Toast.LENGTH_LONG).show()
                onDone?.invoke(false, err)
            }
        }
    }

    fun updateConfig(port: Int, posterUrl: String, apiKey: String) {
        val updated = _config.value.copy(
            port = port,
            customPosterUrl = posterUrl,
            customPosterApiKey = apiKey
        )
        saveStoredConfig(updated)
        Toast.makeText(getApplication(), "Settings saved (Port: $port)", Toast.LENGTH_SHORT).show()
    }
}
