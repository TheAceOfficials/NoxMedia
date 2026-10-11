package com.tgserver.streamer.ui

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tgserver.streamer.data.model.AuthSession
import com.tgserver.streamer.data.model.ChannelInfo
import com.tgserver.streamer.data.model.MediaItem
import com.tgserver.streamer.data.model.ServerConfig
import com.tgserver.streamer.service.StreamerForegroundService
import com.tgserver.streamer.telegram.TelegramClientManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class AppTab {
    HOME, CHANNELS, LIBRARY, SETTINGS
}

data class TelemetryUiState(
    val isRunning: Boolean = false, // Server OFF by default until user turns it on
    val port: Int = 7788,
    val localIp: String = "127.0.0.1",
    val manifestUrl: String = "http://127.0.0.1:7788/manifest.json",
    val uptimeFormatted: String = "0m",
    val memoryUsedMb: Long = 20,
    val cpuPercent: Double = 0.0
)

class MainAppViewModel(application: Application) : AndroidViewModel(application) {

    private val tgClient: TelegramClientManager =
        StreamerForegroundService.activeTgClient ?: TelegramClientManager(application.applicationContext)

    val session: StateFlow<AuthSession> = tgClient.sessionState
    val channels: StateFlow<List<ChannelInfo>> = tgClient.channelsState
    val media: StateFlow<List<MediaItem>> = tgClient.mediaState
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

    private val startTime = System.currentTimeMillis()

    init {
        startTelemetryLoop()
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
        viewModelScope.launch {
            while (true) {
                val server = StreamerForegroundService.activeServer
                val ip = server?.getLocalIpAddress() ?: "127.0.0.1"
                val port = _config.value.port
                val isRunning = StreamerForegroundService.isServiceRunning
                val uptimeSec = (System.currentTimeMillis() - startTime) / 1000
                val h = uptimeSec / 3600
                val m = (uptimeSec % 3600) / 60
                val uptimeStr = if (h > 0) "${h}h ${m}m" else "${m}m"

                val runtime = Runtime.getRuntime()
                val usedMem = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)

                _telemetry.value = TelemetryUiState(
                    isRunning = isRunning,
                    port = port,
                    localIp = ip,
                    manifestUrl = "http://$ip:$port/manifest.json",
                    uptimeFormatted = uptimeStr,
                    memoryUsedMb = usedMem,
                    cpuPercent = if (isRunning) 1.2 else 0.0
                )
                delay(3000)
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
        clipboard.setPrimaryClip(ClipData.newPlainText("TGServer", text))
        Toast.makeText(getApplication(), label, Toast.LENGTH_SHORT).show()
    }

    // --- Channels Actions ---

    fun addChannel(identifier: String) {
        val result = tgClient.addChannel(identifier)
        if (result.isSuccess) {
            Toast.makeText(getApplication(), "Channel added", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(getApplication(), result.exceptionOrNull()?.message ?: "Error adding channel", Toast.LENGTH_SHORT).show()
        }
    }

    fun toggleChannel(channelId: Long, active: Boolean) {
        tgClient.toggleChannel(channelId, active)
    }

    fun removeChannel(channelId: Long) {
        tgClient.removeChannel(channelId)
        Toast.makeText(getApplication(), "Channel removed", Toast.LENGTH_SHORT).show()
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
                Toast.makeText(getApplication(), "✓ Code sent to your Telegram app", Toast.LENGTH_LONG).show()
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
        // Reset codeSentSuccess before resending so UI reacts
        _codeSentSuccess.value = false
        sendAuthCode(phoneNumber)
    }

    fun verifyAuthCode(code: String) {
        viewModelScope.launch {
            _isVerifyingCode.value = true
            val res = tgClient.verifyLoginCode(code)
            _isVerifyingCode.value = false
            if (res.isSuccess) {
                _codeSentSuccess.value = false
                Toast.makeText(getApplication(), "✓ Logged in successfully!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(
                    getApplication(),
                    res.exceptionOrNull()?.message ?: "Invalid code",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    fun importSessionString(sessionStr: String) {
        viewModelScope.launch {
            _isImportingSession.value = true
            _sessionImportError.value = null
            val res = tgClient.importSessionString(sessionStr)
            _isImportingSession.value = false
            if (res.isSuccess) {
                Toast.makeText(getApplication(), "✓ Connected to Telegram successfully!", Toast.LENGTH_SHORT).show()
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
            }
        }
    }

    fun logout() {
        tgClient.logout()
        _codeSentSuccess.value = false
        Toast.makeText(getApplication(), "Logged out", Toast.LENGTH_SHORT).show()
    }

    // --- Settings Actions ---

    fun updateConfig(port: Int, posterUrl: String, apiKey: String) {
        _config.value.port = port
        _config.value.customPosterUrl = posterUrl
        _config.value.customPosterApiKey = apiKey
        Toast.makeText(getApplication(), "Settings saved (Port: $port)", Toast.LENGTH_SHORT).show()
    }
}
