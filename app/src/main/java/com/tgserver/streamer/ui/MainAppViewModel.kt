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
import com.tgserver.streamer.data.model.MediaGroup
import com.tgserver.streamer.data.model.MediaItem
import com.tgserver.streamer.data.model.ServerConfig
import com.tgserver.streamer.metadata.EnrichedMetadata
import com.tgserver.streamer.metadata.MetadataEnricher
import com.tgserver.streamer.server.LocalHttpServer
import com.tgserver.streamer.service.StreamerForegroundService
import com.tgserver.streamer.telegram.TelegramClientManager
import com.tgserver.streamer.data.model.toMediaGroups
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
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
import java.util.UUID
import java.util.concurrent.TimeUnit
import org.json.JSONArray
import org.json.JSONObject
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Job

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

data class CloudSyncProgress(
    val isSyncing: Boolean = false,
    val message: String = "",
    val isSuccess: Boolean? = null,
    val timestamp: Long = System.currentTimeMillis()
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

    /**
     * Enterprise / Netflix scale: Grouping raw media into distinct movie/series titles
     * is precalculated completely on Dispatchers.Default (background thread pool).
     * The Main UI looper / composition thread is NEVER blocked!
     */
    val mediaGroups: StateFlow<List<MediaGroup>> = media
        .map { list -> list.toMediaGroups() }
        .flowOn(Dispatchers.Default)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = emptyList()
        )

    /**
     * Precomputed 15 newest titles for HomeScreen, eliminating heavy sorting on startup.
     */
    val recentlyAddedGroups: StateFlow<List<MediaGroup>> = mediaGroups
        .map { groups ->
            groups.sortedByDescending { group ->
                val date = group.dateAdded.takeIf { it > 0L }
                date ?: (group.items.maxOfOrNull { it.messageId }?.toLong() ?: 0L)
            }.take(15)
        }
        .flowOn(Dispatchers.Default)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = emptyList()
        )

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

    /** Non-blocking floating status indicator for True Cloud Sync */
    private val _cloudSyncProgress = MutableStateFlow<CloudSyncProgress?>(null)
    val cloudSyncProgress: StateFlow<CloudSyncProgress?> = _cloudSyncProgress.asStateFlow()
    private var syncDismissJob: Job? = null

    fun dismissCloudSyncProgress() {
        syncDismissJob?.cancel()
        _cloudSyncProgress.value = null
    }

    private fun postSyncProgress(isSyncing: Boolean, message: String, isSuccess: Boolean? = null, autoDismissMs: Long? = null) {
        _cloudSyncProgress.value = CloudSyncProgress(
            isSyncing = isSyncing,
            message = message,
            isSuccess = isSuccess
        )
        syncDismissJob?.cancel()
        if (autoDismissMs != null && autoDismissMs > 0) {
            syncDismissJob = viewModelScope.launch {
                delay(autoDismissMs)
                _cloudSyncProgress.value = null
            }
        }
    }

    val syncDeviceId: String by lazy {
        var id = prefs.getString("device_sync_id", null)
        if (id.isNullOrBlank()) {
            id = UUID.randomUUID().toString().take(8)
            prefs.edit().putString("device_sync_id", id).apply()
        }
        id
    }

    val syncDeviceName: String
        get() {
            val model = Build.MODEL ?: "Device"
            val brand = Build.MANUFACTURER ?: ""
            return if (model.startsWith(brand, ignoreCase = true)) model else "$brand $model".trim()
        }

    private var autoBackupJob: Job? = null

    fun triggerAutoCloudBackupDebounced(delayMs: Long = 0L) {
        val cfg = _config.value
        if (!session.value.isLoggedIn) return
        autoBackupJob?.cancel()
        // Execute on persistent tgClient background job so cloud backup completes even if app/activity is closed
        autoBackupJob = tgClient.launchBackgroundJob {
            if (delayMs > 0L) {
                delay(delayMs)
            }
            try {
                val dest = cfg.backupDestination.ifBlank { "me" }
                val backupJson = exportBackupJson()
                val chCount = channels.value.size
                val medCount = tgClient.getAllMediaItems().size
                Log.d("MainAppViewModel", "Auto-pushing cloud backup snapshot ($medCount items) to $dest...")
                val res = tgClient.uploadBackupToTelegram(
                    backupJson = backupJson,
                    destination = dest,
                    channelCount = chCount,
                    mediaCount = medCount,
                    originDevice = syncDeviceName
                )
                if (res.isSuccess) {
                    val now = System.currentTimeMillis()
                    prefs.edit()
                        .putLong("last_auto_backup_timestamp", now)
                        .putLong("last_cloud_sync_timestamp", now)
                        .apply()
                    Log.i("MainAppViewModel", "Auto-backup pushed successfully to Telegram ($dest)")
                } else {
                    Log.w("MainAppViewModel", "Auto-backup push failed: ${res.exceptionOrNull()?.message}")
                }
            } catch (e: Exception) {
                Log.w("MainAppViewModel", "Auto-backup error: ${e.message}")
            }
        }
    }

    private val startTime = System.currentTimeMillis()

    init {
        val loaded = loadStoredConfig()
        _config.value = loaded
        StreamerForegroundService.activeConfig = loaded
        tgClient.setBackupJsonProvider { exportBackupJson() }
        viewModelScope.launch(Dispatchers.IO) {
            if (loaded.skipExactDuplicates) {
                tgClient.cleanupExactDuplicates()
            } else {
                tgClient.cleanupSplitDuplicates()
            }
            if (session.value.isLoggedIn) {
                tgClient.refreshUserProfile()
                if (loaded.cloudSyncLibrary || loaded.autoBackupEnabled) {
                    delay(8000L) // Defer cloud sync so startup & UI render remain 100% fluid
                    syncFromCloud(silent = true)
                }
            }
            // Auto-check for GitHub app updates in background
            updateManager.checkForUpdate(manual = false, includeBeta = loaded.receiveBetaUpdates)
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
            updateManager.checkForUpdate(manual = manual, includeBeta = _config.value.receiveBetaUpdates)
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
                    triggerAutoCloudBackupDebounced(0L)
                    Toast.makeText(getApplication(), "✓ Synced: $newCount new video(s) added (${ch?.title ?: "channel"})", Toast.LENGTH_SHORT).show()
                } else {
                    triggerAutoCloudBackupDebounced(0L)
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
            triggerAutoCloudBackupDebounced(0L)
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
                triggerAutoCloudBackupDebounced(0L)
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

    fun matchUnmatchedItem(
        unmatchedId: String,
        matchedMeta: EnrichedMetadata,
        targetType: String? = null,
        onDone: (Boolean) -> Unit = {}
    ) {
        viewModelScope.launch {
            val tmdbKey = prefs.getString("tmdb_api_key", "") ?: ""
            val tvdbKey = prefs.getString("tvdb_api_key", "") ?: ""
            val (enriched, episodeMap, episodeThumbMap) = withContext(Dispatchers.IO) {
                MetadataEnricher.fetchFullMetadataDetails(matchedMeta, tmdbKey, tvdbKey)
            }
            val finalMeta = matchedMeta.copy(
                title = enriched.title ?: matchedMeta.title,
                year = enriched.year ?: matchedMeta.year,
                overview = enriched.overview ?: matchedMeta.overview,
                posterUrl = enriched.posterUrl ?: matchedMeta.posterUrl,
                backdropUrl = enriched.backdropUrl ?: matchedMeta.backdropUrl,
                imdbId = enriched.imdbId ?: matchedMeta.imdbId,
                tmdbId = enriched.tmdbId ?: matchedMeta.tmdbId,
                kitsuId = enriched.kitsuId ?: matchedMeta.kitsuId,
                episodeThumbnailUrl = enriched.episodeThumbnailUrl ?: matchedMeta.episodeThumbnailUrl
            )
            val item = tgClient.matchUnmatchedItem(unmatchedId, finalMeta, targetType)
            if (item != null) {
                val s = item.season ?: 1
                val e = item.episode
                if (e != null && episodeMap.containsKey(Pair(s, e))) {
                    item.episodeTitle = episodeMap[Pair(s, e)]
                }
                if (e != null && episodeThumbMap.containsKey(Pair(s, e))) {
                    item.episodeThumbnailUrl = episodeThumbMap[Pair(s, e)]
                }
                tgClient.saveMediaIndex()
                triggerAutoCloudBackupDebounced(0L)
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
            triggerAutoCloudBackupDebounced(0L)
            Toast.makeText(getApplication(), "✓ Added to library: ${item.title}", Toast.LENGTH_SHORT).show()
        }
    }

    fun removeMultipleUnmatched(unmatchedIds: List<String>) {
        if (unmatchedIds.isEmpty()) return
        tgClient.removeUnmatchedItems(unmatchedIds)
        Toast.makeText(getApplication(), "✓ Removed ${unmatchedIds.size} items", Toast.LENGTH_SHORT).show()
    }

    fun removeUnmatchedItems(unmatchedIds: Collection<String>) {
        removeMultipleUnmatched(unmatchedIds.toList())
    }

    fun matchMultipleUnmatchedItems(
        unmatchedIds: List<String>,
        matchedMeta: EnrichedMetadata,
        targetType: String? = null,
        onDone: (Int) -> Unit = {}
    ) {
        if (unmatchedIds.isEmpty()) {
            onDone(0)
            return
        }
        viewModelScope.launch {
            val tmdbKey = prefs.getString("tmdb_api_key", "") ?: ""
            val tvdbKey = prefs.getString("tvdb_api_key", "") ?: ""
            val (enriched, episodeMap, episodeThumbMap) = withContext(Dispatchers.IO) {
                MetadataEnricher.fetchFullMetadataDetails(matchedMeta, tmdbKey, tvdbKey)
            }
            val finalMeta = matchedMeta.copy(
                title = enriched.title ?: matchedMeta.title,
                year = enriched.year ?: matchedMeta.year,
                overview = enriched.overview ?: matchedMeta.overview,
                posterUrl = enriched.posterUrl ?: matchedMeta.posterUrl,
                backdropUrl = enriched.backdropUrl ?: matchedMeta.backdropUrl,
                imdbId = enriched.imdbId ?: matchedMeta.imdbId,
                tmdbId = enriched.tmdbId ?: matchedMeta.tmdbId,
                kitsuId = enriched.kitsuId ?: matchedMeta.kitsuId,
                episodeThumbnailUrl = enriched.episodeThumbnailUrl ?: matchedMeta.episodeThumbnailUrl
            )
            val matched = tgClient.matchUnmatchedItemsBatch(unmatchedIds, finalMeta, targetType, episodeMap, episodeThumbMap)
            if (matched.isNotEmpty()) {
                triggerAutoCloudBackupDebounced(0L)
                Toast.makeText(getApplication(), "✓ Matched ${matched.size} items with ${finalMeta.title}", Toast.LENGTH_SHORT).show()
                onDone(matched.size)
            } else {
                Toast.makeText(getApplication(), "Could not match selected items", Toast.LENGTH_SHORT).show()
                onDone(0)
            }
        }
    }

    fun matchUnmatchedItemsBatch(
        unmatchedIds: List<String>,
        matchedMeta: EnrichedMetadata,
        targetType: String? = null,
        onDone: (Boolean) -> Unit = {}
    ) {
        matchMultipleUnmatchedItems(unmatchedIds, matchedMeta, targetType) { count ->
            onDone(count > 0)
        }
    }

    fun addMultipleUnmatchedToLibraryAnyway(unmatchedIds: List<String>) {
        if (unmatchedIds.isEmpty()) return
        val added = tgClient.addUnmatchedToLibraryAnywayBatch(unmatchedIds)
        if (added.isNotEmpty()) {
            triggerAutoCloudBackupDebounced(0L)
            Toast.makeText(getApplication(), "✓ Added ${added.size} items to library", Toast.LENGTH_SHORT).show()
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
                triggerAutoCloudBackupDebounced(0L)
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
            if (count > 0) {
                triggerAutoCloudBackupDebounced(0L)
            }
            Toast.makeText(getApplication(), "✓ Enriched metadata for $count items", Toast.LENGTH_SHORT).show()
        }
    }

    fun toggleHideMedia(itemId: String): Boolean {
        val newState = tgClient.toggleHideMediaItem(itemId)
        val msg = if (newState) "Item hidden from Stremio" else "Item unhidden"
        Toast.makeText(getApplication(), msg, Toast.LENGTH_SHORT).show()
        triggerAutoCloudBackupDebounced(0L)
        return newState
    }

    fun setGroupHidden(group: MediaGroup, hide: Boolean) {
        val ids = group.items.map { it.id }
        tgClient.setMediaItemsHidden(ids, hide)
        val action = if (hide) "hidden & blacklisted" else "unhidden"
        Toast.makeText(getApplication(), "✓ \"${group.title}\" $action", Toast.LENGTH_SHORT).show()
        triggerAutoCloudBackupDebounced(0L)
    }

    fun setMultipleGroupsHidden(groups: List<MediaGroup>, hide: Boolean) {
        val ids = groups.flatMap { g -> g.items.map { it.id } }
        tgClient.setMediaItemsHidden(ids, hide)
        val action = if (hide) "hidden & blacklisted" else "unhidden"
        Toast.makeText(getApplication(), "✓ ${groups.size} titles $action", Toast.LENGTH_SHORT).show()
        triggerAutoCloudBackupDebounced(0L)
    }

    suspend fun searchMetadataCandidates(query: String, type: String): List<EnrichedMetadata> = withContext(Dispatchers.IO) {
        val tmdbKey = prefs.getString("tmdb_api_key", "") ?: ""
        MetadataEnricher.searchCandidates(query, type, tmdbKey)
    }

    fun applyManualMetadata(groupItems: List<MediaItem>, meta: EnrichedMetadata, targetType: String? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            val tmdbKey = prefs.getString("tmdb_api_key", "") ?: ""
            val tvdbKey = prefs.getString("tvdb_api_key", "") ?: ""
            val (enriched, episodeMap, episodeThumbMap) = MetadataEnricher.fetchFullMetadataDetails(meta, tmdbKey, tvdbKey)
            val effectiveType = if (targetType == "anime" || meta.kitsuId != null) "series" else (targetType ?: meta.type ?: groupItems.firstOrNull()?.type ?: "movie")

            val updated = groupItems.mapIndexed { index, item ->
                // If switching from movie to series and item had no season/episode, default to S01 E(index+1)
                val s = if (effectiveType == "series") (item.season ?: 1) else null
                val e = if (effectiveType == "series") (item.episode ?: (index + 1)) else null

                val newEpTitle = if (s != null && e != null) {
                    episodeMap[Pair(s, e)] ?: item.episodeTitle ?: "Episode $e"
                } else null

                val newEpThumb = if (s != null && e != null) {
                    episodeThumbMap[Pair(s, e)] ?: item.episodeThumbnailUrl
                } else item.episodeThumbnailUrl

                item.copy(
                    title = enriched.title ?: meta.title ?: item.title,
                    type = effectiveType,
                    year = enriched.year ?: meta.year ?: item.year,
                    season = s,
                    episode = e,
                    overview = enriched.overview ?: meta.overview ?: item.overview,
                    posterUrl = enriched.posterUrl ?: meta.posterUrl ?: item.posterUrl,
                    backdropUrl = enriched.backdropUrl ?: meta.backdropUrl ?: item.backdropUrl,
                    episodeTitle = newEpTitle,
                    episodeThumbnailUrl = newEpThumb,
                    imdbId = enriched.imdbId ?: meta.imdbId,
                    tmdbId = enriched.tmdbId ?: meta.tmdbId,
                    tvdbId = enriched.tvdbId ?: meta.tvdbId ?: item.tvdbId,
                    kitsuId = enriched.kitsuId ?: meta.kitsuId ?: item.kitsuId,
                    isManuallyMatched = true,
                    lastModified = System.currentTimeMillis()
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
            triggerAutoCloudBackupDebounced(0L)
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    getApplication(),
                    "✓ Metadata synced: ${enriched.title ?: meta.title} (${enriched.year ?: meta.year}) • ${effectiveType.uppercase()}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    fun mergeMediaGroups(sourceGroup: MediaGroup, targetGroup: MediaGroup) {
        viewModelScope.launch(Dispatchers.IO) {
            val bestTitle = targetGroup.title.ifBlank { sourceGroup.title }
            val bestType = targetGroup.type.ifBlank { sourceGroup.type }
            val bestYear = targetGroup.year ?: sourceGroup.year
            val bestImdbId = targetGroup.imdbId ?: sourceGroup.imdbId
            val bestTmdbId = if ((targetGroup.tmdbId ?: 0) > 0) targetGroup.tmdbId else sourceGroup.tmdbId
            val bestTvdbId = targetGroup.tvdbId ?: sourceGroup.tvdbId
                ?: targetGroup.items.firstOrNull { (it.tvdbId ?: 0) > 0 }?.tvdbId
                ?: sourceGroup.items.firstOrNull { (it.tvdbId ?: 0) > 0 }?.tvdbId
            val bestKitsuId = targetGroup.items.firstOrNull { (it.kitsuId ?: 0) > 0 }?.kitsuId
                ?: sourceGroup.items.firstOrNull { (it.kitsuId ?: 0) > 0 }?.kitsuId
            val bestPoster = targetGroup.posterUrl ?: sourceGroup.posterUrl
            val bestBackdrop = targetGroup.backdropUrl ?: sourceGroup.backdropUrl
            val bestOverview = targetGroup.overview ?: sourceGroup.overview

            val allItemsToUpdate = (sourceGroup.items + targetGroup.items).map { item ->
                item.copy(
                    title = bestTitle,
                    type = bestType,
                    year = bestYear,
                    imdbId = bestImdbId,
                    tmdbId = bestTmdbId,
                    tvdbId = bestTvdbId,
                    kitsuId = bestKitsuId,
                    posterUrl = bestPoster,
                    backdropUrl = bestBackdrop,
                    overview = bestOverview,
                    isManuallyMatched = true,
                    lastModified = System.currentTimeMillis()
                )
            }

            val oldTitle = sourceGroup.title
            if (!oldTitle.isNullOrBlank()) {
                MetadataEnricher.invalidateTitle(oldTitle)
            }
            if (!targetGroup.title.isNullOrBlank()) {
                MetadataEnricher.invalidateTitle(targetGroup.title)
            }
            StreamerForegroundService.activeServer?.clearTitleCache()

            tgClient.updateMediaItems(allItemsToUpdate)
            triggerAutoCloudBackupDebounced(0L)

            val newKey = when {
                bestTmdbId != null && bestTmdbId > 0 -> "tmdb_${bestType}_$bestTmdbId"
                !bestImdbId.isNullOrBlank() -> "imdb_$bestImdbId"
                bestTvdbId != null && bestTvdbId > 0 -> "tvdb_${bestType}_$bestTvdbId"
                bestKitsuId != null && bestKitsuId > 0 -> "kitsu_${bestType}_$bestKitsuId"
                else -> "title_${bestType}_${bestTitle.trim().lowercase()}"
            }

            withContext(Dispatchers.Main) {
                _selectedMediaGroupKey.value = newKey
                Toast.makeText(
                    getApplication(),
                    "✓ Merged into '${bestTitle}' (${allItemsToUpdate.size} files unified)",
                    Toast.LENGTH_LONG
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
        syncFromCloud(silent = false)
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
            alexaDeviceName = prefs.getString("alexa_device_name", "NoxMedia") ?: "NoxMedia",
            receiveBetaUpdates = prefs.getBoolean("receive_beta_updates", false),
            animeMetadataPreference = prefs.getString("anime_metadata_preference", "seasonal") ?: "seasonal",
            backupDestination = prefs.getString("backup_destination", "") ?: "",
            cloudSyncLibrary = prefs.getBoolean("cloud_sync_library", true)
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
            putBoolean("receive_beta_updates", cfg.receiveBetaUpdates)
            putString("anime_metadata_preference", cfg.animeMetadataPreference)
            putString("backup_destination", cfg.backupDestination)
            putBoolean("cloud_sync_library", cfg.cloudSyncLibrary)
            apply()
        }
        _config.value = cfg
        StreamerForegroundService.activeConfig = cfg
        tgClient.updateStreamCacheConfig(cfg.diskCacheMb, cfg.bufferPipelineMb)
        if (cfg.skipExactDuplicates) {
            tgClient.cleanupExactDuplicates()
        } else {
            tgClient.cleanupSplitDuplicates()
        }
    }

    fun cleanupDuplicates(): Int {
        val cfg = _config.value
        return if (cfg.skipExactDuplicates) {
            tgClient.cleanupExactDuplicates()
        } else {
            tgClient.cleanupSplitDuplicates()
        }
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

        com.tgserver.streamer.service.StandbyQuickSettingsTileService.updateTileState(context)
        com.tgserver.streamer.service.ServerQuickSettingsTileService.updateTileState(context)

        val msg = if (enabled) {
            "✓ Alexa Voice Control active for \"$cleanName\". Say 'Alexa, discover devices'."
        } else {
            "✓ Alexa Voice Control disabled"
        }
        Toast.makeText(getApplication(), msg, Toast.LENGTH_LONG).show()
    }

    fun updateReceiveBetaUpdates(enabled: Boolean) {
        val updated = _config.value.copy(receiveBetaUpdates = enabled)
        saveStoredConfig(updated)
        val msg = if (enabled) "✓ Enrolled in Beta Updates" else "✓ Switched to Stable Updates only"
        Toast.makeText(getApplication(), msg, Toast.LENGTH_SHORT).show()
        if (!enabled) {
            updateManager.resetUpdateState()
        }
        checkForAppUpdate(manual = false)
    }

    fun updateAnimeMetadataPreference(pref: String) {
        val updated = _config.value.copy(animeMetadataPreference = pref)
        saveStoredConfig(updated)
        val label = if (pref == "seasonal") "Seasonal (TVDB / TMDB)" else "Absolute (Kitsu)"
        Toast.makeText(getApplication(), "✓ Anime indexing set to $label", Toast.LENGTH_SHORT).show()
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

    fun updateBackupSettings(
        enabled: Boolean,
        intervalHours: Int,
        destination: String = _config.value.backupDestination,
        cloudSyncLibrary: Boolean = _config.value.cloudSyncLibrary
    ) {
        val updated = _config.value.copy(
            autoBackupEnabled = enabled,
            autoBackupIntervalHours = intervalHours,
            backupDestination = destination,
            cloudSyncLibrary = cloudSyncLibrary
        )
        saveStoredConfig(updated)
        prefs.edit().putString("backup_destination", destination).apply()
        if (enabled) {
            tgClient.startAutoBackupLoop { exportBackupJson() }
            val targetName = if (destination.isNotBlank()) destination else "Saved Messages"
            Toast.makeText(getApplication(), "✓ Auto-backup scheduled every ${intervalHours}h to $targetName", Toast.LENGTH_SHORT).show()
        } else {
            tgClient.stopAutoBackupLoop()
            Toast.makeText(getApplication(), "✓ Auto-backup disabled", Toast.LENGTH_SHORT).show()
        }
    }

    private val _isScanningDuplicates = MutableStateFlow(false)
    val isScanningDuplicates: StateFlow<Boolean> = _isScanningDuplicates.asStateFlow()

    private val _duplicateScanCount = MutableStateFlow<Int?>(null)
    val duplicateScanCount: StateFlow<Int?> = _duplicateScanCount.asStateFlow()

    private val _isScanningDeadFiles = MutableStateFlow(false)
    val isScanningDeadFiles: StateFlow<Boolean> = _isScanningDeadFiles.asStateFlow()

    private val _deadFilesScanCount = MutableStateFlow<Int?>(null)
    val deadFilesScanCount: StateFlow<Int?> = _deadFilesScanCount.asStateFlow()

    fun startBackgroundDuplicateScan() {
        if (_isScanningDuplicates.value) return
        _isScanningDuplicates.value = true
        tgClient.launchBackgroundJob {
            try {
                val res = tgClient.scanDuplicates()
                _duplicateScanCount.value = res
                withContext(Dispatchers.Main) {
                    val msg = if (res == 0) "✓ Catalog clean: 0 duplicates" else "⚠ Found $res duplicate entries"
                    Toast.makeText(getApplication(), msg, Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                android.util.Log.e("MainAppViewModel", "Duplicate scan failed", e)
            } finally {
                _isScanningDuplicates.value = false
            }
        }
    }

    fun scanDuplicates(): Int = tgClient.scanDuplicates()

    fun deleteDuplicates(): Int {
        val res = tgClient.deleteDuplicates()
        _duplicateScanCount.value = 0
        return res
    }

    fun startBackgroundDeadFilesScan() {
        if (_isScanningDeadFiles.value) return
        _isScanningDeadFiles.value = true
        tgClient.launchBackgroundJob {
            try {
                val res = tgClient.scanDeadFilesRemote()
                _deadFilesScanCount.value = res
                withContext(Dispatchers.Main) {
                    val msg = if (res == 0) "✓ All indexed media files verified & online" else "⚠ Detected $res deleted or broken files"
                    Toast.makeText(getApplication(), msg, Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                android.util.Log.e("MainAppViewModel", "Dead files scan failed", e)
            } finally {
                _isScanningDeadFiles.value = false
            }
        }
    }

    fun scanDeadFiles(): Int = tgClient.scanDeadFiles()

    suspend fun scanDeadFilesRemote(): Int = tgClient.scanDeadFilesRemote()

    fun purgeDeadFiles(): Int {
        val res = tgClient.purgeDeadFiles()
        _deadFilesScanCount.value = 0
        return res
    }

    fun refreshContentGroup(groupItems: List<MediaItem>, onFinished: ((Triple<Int, Int, Int>) -> Unit)? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = tgClient.refreshContentGroup(groupItems)
            triggerAutoCloudBackupDebounced(0L)
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
            put("animeMetadataPreference", cfg.animeMetadataPreference)
            put("backupDestination", cfg.backupDestination)
            put("cloudSyncLibrary", cfg.cloudSyncLibrary)
        }

        val mediaList = tgClient.getAllMediaItems()
        val root = JSONObject().apply {
            put("version", 3)
            put("appName", "NoxMedia")
            put("deviceId", syncDeviceId)
            put("deviceName", syncDeviceName)
            put("timestamp", System.currentTimeMillis())
            put("settings", settingsObj)
            put("channels", channelsJsonArray)
            put("mediaCount", mediaList.size)
            if (mediaList.isNotEmpty()) {
                put("mediaItems", JSONArray(Gson().toJson(mediaList)))
            }
        }

        return root.toString()
    }

    fun importBackupJsonDetailed(json: String): Triple<Boolean, Int, Int> {
        return try {
            val root = JSONObject(json)
            var settingsRestored = false
            var channelsRestoredCount = 0
            var mediaRestoredCount = 0

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
                    autoBackupIntervalHours = settingsObj.optInt("autoBackupIntervalHours", cur.autoBackupIntervalHours),
                    animeMetadataPreference = settingsObj.optString("animeMetadataPreference", cur.animeMetadataPreference),
                    backupDestination = settingsObj.optString("backupDestination", cur.backupDestination),
                    cloudSyncLibrary = settingsObj.optBoolean("cloudSyncLibrary", cur.cloudSyncLibrary)
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

            if (root.has("mediaItems")) {
                val mediaElement = try {
                    com.google.gson.JsonParser.parseString(json).asJsonObject.get("mediaItems")
                } catch (_: Exception) { null }
                if (mediaElement != null && mediaElement.isJsonArray && mediaElement.asJsonArray.size() > 0) {
                    val type = object : TypeToken<List<MediaItem>>() {}.type
                    val list: List<MediaItem> = Gson().fromJson(mediaElement, type) ?: emptyList()
                    if (list.isNotEmpty()) {
                        mediaRestoredCount = tgClient.mergeMediaItems(list)
                    }
                }
            }

            Triple(settingsRestored, channelsRestoredCount, mediaRestoredCount)
        } catch (e: Exception) {
            Log.e("MainAppViewModel", "Failed to parse backup JSON: ${e.message}", e)
            Triple(false, 0, 0)
        }
    }

    fun importBackupJson(json: String): Boolean {
        val (settingsRestored, channelsRestored, mediaRestored) = importBackupJsonDetailed(json)
        return if (settingsRestored || channelsRestored > 0 || mediaRestored > 0) {
            val parts = mutableListOf<String>()
            if (mediaRestored > 0) parts.add("$mediaRestored media items")
            if (channelsRestored > 0) parts.add("$channelsRestored channels")
            if (settingsRestored) parts.add("settings")
            val msg = "✓ Restored from backup: ${parts.joinToString(", ")}"
            Toast.makeText(getApplication(), msg, Toast.LENGTH_LONG).show()
            true
        } else {
            Toast.makeText(getApplication(), "Invalid backup JSON: No valid data found", Toast.LENGTH_SHORT).show()
            false
        }
    }

    fun backupToTelegramCloud(destination: String? = null, onDone: ((Boolean, String?) -> Unit)? = null) {
        viewModelScope.launch {
            if (!session.value.isLoggedIn) {
                Toast.makeText(getApplication(), "Telegram is not connected", Toast.LENGTH_SHORT).show()
                onDone?.invoke(false, "Telegram not connected")
                return@launch
            }
            _isBackingUpToCloud.value = true
            val target = destination ?: _config.value.backupDestination.ifBlank { "me" }
            val backupJson = exportBackupJson()
            val chCount = channels.value.size
            val medCount = tgClient.getAllMediaItems().size
            val res = tgClient.uploadBackupToTelegram(
                backupJson = backupJson,
                destination = target,
                channelCount = chCount,
                mediaCount = medCount,
                originDevice = syncDeviceName
            )
            _isBackingUpToCloud.value = false
            if (res.isSuccess) {
                val now = System.currentTimeMillis()
                prefs.edit()
                    .putLong("last_auto_backup_timestamp", now)
                    .putLong("last_cloud_sync_timestamp", now)
                    .apply()
                val targetName = if (target == "me") "Saved Messages" else target
                Toast.makeText(getApplication(), "✓ Cloud backup saved to $targetName!", Toast.LENGTH_SHORT).show()
                onDone?.invoke(true, null)
            } else {
                val err = res.exceptionOrNull()?.message ?: "Upload failed"
                Toast.makeText(getApplication(), "Backup upload failed: $err", Toast.LENGTH_LONG).show()
                onDone?.invoke(false, err)
            }
        }
    }

    fun restoreFromTelegramCloud(source: String? = null, onDone: ((Boolean, String?) -> Unit)? = null) {
        syncFromCloud(silent = false, onDone = onDone)
    }

    fun syncFromCloud(silent: Boolean = true, onDone: ((Boolean, String?) -> Unit)? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            if (!session.value.isLoggedIn) {
                withContext(Dispatchers.Main) {
                    if (!silent) Toast.makeText(getApplication(), "Telegram is not connected", Toast.LENGTH_SHORT).show()
                }
                onDone?.invoke(false, "Telegram not connected")
                return@launch
            }
            postSyncProgress(isSyncing = true, message = "Checking True Cloud Sync...")
            if (!silent) {
                withContext(Dispatchers.Main) { _isRestoringFromCloud.value = true }
            }
            val target = _config.value.backupDestination.ifBlank { "me" }
            val lastSyncTs = prefs.getLong("last_cloud_sync_timestamp", 0L)
            val res = tgClient.fetchLatestBackupFromTelegram(target, if (silent) lastSyncTs else 0L)
            if (!silent) {
                withContext(Dispatchers.Main) { _isRestoringFromCloud.value = false }
            }
            if (res.isSuccess) {
                val json = res.getOrNull()
                if (json.isNullOrBlank()) {
                    if (silent) {
                        postSyncProgress(isSyncing = false, isSuccess = true, message = "Cloud library up to date", autoDismissMs = 2500L)
                        onDone?.invoke(true, "Already up to date")
                    } else {
                        postSyncProgress(isSyncing = false, isSuccess = false, message = "No backup found in Telegram $target", autoDismissMs = 2500L)
                        withContext(Dispatchers.Main) {
                            Toast.makeText(getApplication(), "No new backup found in Telegram $target", Toast.LENGTH_SHORT).show()
                        }
                        onDone?.invoke(false, "No backup found")
                    }
                    return@launch
                }
                try {
                    // Fast header check using lightweight regex before doing any heavy JSON tree parsing
                    val tsMatch = Regex(""""timestamp"\s*:\s*(\d+)""").find(json)
                    val devMatch = Regex(""""deviceId"\s*:\s*"([^"]+)"""").find(json)
                    val backupTs = tsMatch?.groupValues?.get(1)?.toLongOrNull() ?: 0L
                    val originDeviceId = devMatch?.groupValues?.get(1) ?: ""

                    // If silent check and uploaded by this device or older than last sync, skip immediately
                    if (silent && (originDeviceId == syncDeviceId || (lastSyncTs > 0L && backupTs <= lastSyncTs))) {
                        postSyncProgress(isSyncing = false, isSuccess = true, message = "Cloud library up to date", autoDismissMs = 2500L)
                        onDone?.invoke(true, "Already up to date")
                        return@launch
                    }

                    postSyncProgress(isSyncing = true, message = "Syncing cloud media & channels...")
                    val (settingsRestored, channelsRestored, mediaRestored) = importBackupJsonDetailed(json)
                    if (settingsRestored || channelsRestored > 0 || mediaRestored > 0) {
                        if (backupTs > 0L) {
                            prefs.edit().putLong("last_cloud_sync_timestamp", backupTs).apply()
                        }
                        val originMatch = Regex(""""deviceName"\s*:\s*"([^"]+)"""").find(json)
                        val origin = originMatch?.groupValues?.get(1) ?: "Cloud"
                        val summary = mutableListOf<String>()
                        if (mediaRestored > 0) summary.add("$mediaRestored media")
                        if (channelsRestored > 0) summary.add("$channelsRestored channels")
                        if (settingsRestored) summary.add("settings")
                        val msg = "✓ Synced from $origin: ${summary.joinToString(", ")}"
                        postSyncProgress(isSyncing = false, isSuccess = true, message = msg, autoDismissMs = 3500L)
                        withContext(Dispatchers.Main) {
                            Toast.makeText(getApplication(), msg, Toast.LENGTH_LONG).show()
                        }
                        if (channelsRestored > 0 && mediaRestored == 0) {
                            syncAllChannels()
                        }
                        onDone?.invoke(true, null)
                    } else {
                        postSyncProgress(isSyncing = false, isSuccess = true, message = "Cloud library up to date", autoDismissMs = 2500L)
                        if (!silent) {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(getApplication(), "Backup file did not contain new data", Toast.LENGTH_SHORT).show()
                            }
                        }
                        onDone?.invoke(false, "No new data")
                    }
                } catch (e: Exception) {
                    Log.e("MainAppViewModel", "Error parsing backup: ${e.message}", e)
                    postSyncProgress(isSyncing = false, isSuccess = false, message = "Sync error: ${e.message}", autoDismissMs = 3000L)
                    if (!silent) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(getApplication(), "Error parsing backup: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                    onDone?.invoke(false, e.message)
                }
            } else {
                val err = res.exceptionOrNull()?.message ?: "Fetch failed"
                postSyncProgress(isSyncing = false, isSuccess = false, message = "Sync failed: $err", autoDismissMs = 3000L)
                if (!silent) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(getApplication(), "Failed to fetch backup: $err", Toast.LENGTH_LONG).show()
                    }
                }
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
