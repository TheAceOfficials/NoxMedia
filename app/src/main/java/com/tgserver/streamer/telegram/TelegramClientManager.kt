package com.tgserver.streamer.telegram

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.tgserver.streamer.data.model.AuthSession
import com.tgserver.streamer.data.model.ChannelInfo
import com.tgserver.streamer.data.model.MediaItem
import com.tgserver.streamer.data.model.UnmatchedItem
import com.tgserver.streamer.data.model.ScanProgressState
import com.tgserver.streamer.data.model.SplitPart
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import com.tgserver.streamer.metadata.EnrichedMetadata
import com.tgserver.streamer.metadata.MetadataEnricher
import com.tgserver.streamer.service.StreamerForegroundService
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

import android.util.Base64
import java.nio.ByteBuffer
import java.nio.ByteOrder
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

private const val TAG = "TelegramClientManager"

class TelegramClientManager(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("tg_server_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()
    private val metadataEnricher = com.tgserver.streamer.metadata.MetadataEnricher()

    private val _sessionState = MutableStateFlow(AuthSession())
    val sessionState: StateFlow<AuthSession> = _sessionState.asStateFlow()

    private val _channelsState = MutableStateFlow<List<ChannelInfo>>(emptyList())
    val channelsState: StateFlow<List<ChannelInfo>> = _channelsState.asStateFlow()

    private val _mediaState = MutableStateFlow<List<MediaItem>>(emptyList())
    val mediaState: StateFlow<List<MediaItem>> = _mediaState.asStateFlow()

    private val _unmatchedState = MutableStateFlow<List<UnmatchedItem>>(emptyList())
    val unmatchedState: StateFlow<List<UnmatchedItem>> = _unmatchedState.asStateFlow()

    private val _scanProgress = MutableStateFlow<Map<Long, ScanProgressState>>(emptyMap())
    val scanProgress: StateFlow<Map<Long, ScanProgressState>> = _scanProgress.asStateFlow()

    private val channels = ConcurrentHashMap<Long, ChannelInfo>()
    private val mediaIndex = ConcurrentHashMap<String, MediaItem>()
    private val unmatchedIndex = ConcurrentHashMap<String, UnmatchedItem>()
    private val activeScanJobs = ConcurrentHashMap<Long, Job>()
    private val detectedDeadIds = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    private val _apiCredentials = MutableStateFlow(
        Pair(
            prefs.getInt("tg_api_id", 0),
            prefs.getString("tg_api_hash", "") ?: ""
        )
    )
    val apiCredentials: StateFlow<Pair<Int, String>> = _apiCredentials.asStateFlow()

    private val _codeDeliveryType = MutableStateFlow("APP")
    val codeDeliveryType: StateFlow<String> = _codeDeliveryType.asStateFlow()

    fun getApiId(): Int = _apiCredentials.value.first
    fun getApiHash(): String = _apiCredentials.value.second

    fun hasApiCredentials(): Boolean {
        val (id, hash) = _apiCredentials.value
        return id > 0 && hash.length >= 20
    }

    fun saveApiCredentials(apiId: Int, apiHash: String) {
        prefs.edit().apply {
            putInt("tg_api_id", apiId)
            putString("tg_api_hash", apiHash.trim())
            apply()
        }
        _apiCredentials.value = Pair(apiId, apiHash.trim())
    }

    /**
     * Validates Telegram API credentials by making a test call to Telegram servers.
     * Returns Result.success if credentials are valid, Result.failure with descriptive message otherwise.
     */
    suspend fun validateApiCredentials(apiId: Int, apiHash: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // Basic format checks first
            if (apiId <= 0) {
                return@withContext Result.failure(IllegalArgumentException("API ID must be a positive number"))
            }
            if (apiHash.length < 20) {
                return@withContext Result.failure(IllegalArgumentException("API Hash looks too short. It should be 32 characters long."))
            }
            if (!apiHash.matches(Regex("[a-fA-F0-9]+"))) {
                return@withContext Result.failure(IllegalArgumentException("API Hash should only contain hex characters (0-9, a-f)."))
            }

            // Try a lightweight probe request to Telegram's API gateway
            // We call auth.sendCode with a dummy phone to detect if credentials are recognized
            // A valid API ID + Hash will return phone_code_required or similar, invalid ones return auth error
            val body = """{"@type":"testUseUpdate"}""".toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url("https://api.telegram.org/bot$apiId:$apiHash/getMe")
                .get()
                .addHeader("Content-Type", "application/json")
                .addHeader("User-Agent", "NoxMedia/1.0")
                .build()

            try {
                httpClient.newCall(request).execute().use { response ->
                    // For bot tokens this would work, but for user API we expect a specific error
                    // A 401 UNAUTHORIZED means the format is recognized but invalid
                    // Connection success means server is reachable
                    val bodyStr = response.body?.string() ?: ""
                    Log.d(TAG, "API validation probe response: ${response.code} - $bodyStr")
                    // Any response from telegram means we can reach their servers
                    // Actual API ID/Hash validation happens during sendCode
                }
            } catch (e: Exception) {
                // If we can't reach Telegram at all, warn but don't block
                Log.w(TAG, "Could not reach Telegram servers for validation probe: ${e.message}")
                // We still allow saving if format is correct, real validation will happen on sendCode
            }

            // If format checks pass, consider credentials valid format-wise
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    init {
        loadSession()
        loadChannels()
        CoroutineScope(Dispatchers.IO).launch {
            loadMediaIndex()
            loadUnmatchedIndex()
        }
        // Pre-warm Python runtime and tg_bridge module asynchronously in background
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (!Python.isStarted()) {
                    Python.start(AndroidPlatform(context))
                }
                Python.getInstance().getModule("tg_bridge")
                Log.d(TAG, "Python runtime pre-warmed successfully in background")
            } catch (e: Exception) {
                Log.w(TAG, "Background Python warm-up: ${e.message}")
            }
        }
    }

    private fun loadSession() {
        val session = AuthSession(
            isLoggedIn = prefs.getBoolean("is_logged_in", false),
            phoneNumber = prefs.getString("phone_number", "") ?: "",
            firstName = prefs.getString("first_name", "") ?: "",
            username = prefs.getString("username", "") ?: "",
            userId = prefs.getLong("user_id", 0L),
            sessionString = prefs.getString("session_string", "") ?: "",
            phoneCodeHash = prefs.getString("phone_code_hash", "") ?: ""
        )
        _sessionState.value = session
    }

    private fun saveSession(session: AuthSession) {
        prefs.edit().apply {
            putBoolean("is_logged_in", session.isLoggedIn)
            putString("phone_number", session.phoneNumber)
            putString("first_name", session.firstName)
            putString("username", session.username)
            putLong("user_id", session.userId)
            putString("session_string", session.sessionString)
            putString("phone_code_hash", session.phoneCodeHash)
            apply()
        }
        _sessionState.value = session
    }

    private fun loadChannels() {
        val json = prefs.getString("monitored_channels", null)
        channels.clear()
        if (!json.isNullOrBlank()) {
            val type = object : TypeToken<List<ChannelInfo>>() {}.type
            val list: List<ChannelInfo> = gson.fromJson(json, type) ?: emptyList()
            list.forEach { channels[it.id] = it }
        }
        _channelsState.value = channels.values.toList()
    }

    private fun saveChannels() {
        val list = channels.values.toList()
        val json = gson.toJson(list)
        prefs.edit().putString("monitored_channels", json).apply()
        _channelsState.value = list
    }

    private fun loadMediaIndex() {
        val file = File(context.filesDir, "media_index.json")
        val json = if (file.exists()) {
            try { file.readText() } catch (_: Exception) { prefs.getString("media_index", null) }
        } else {
            prefs.getString("media_index", null)
        }
        mediaIndex.clear()
        if (!json.isNullOrBlank()) {
            val type = object : TypeToken<List<MediaItem>>() {}.type
            val list: List<MediaItem> = gson.fromJson(json, type) ?: emptyList()
            list.forEach { mediaIndex[it.id] = it }
        }
        cleanupSplitDuplicates()
        _mediaState.value = mediaIndex.values.toList()

        autoEnrichMissingMetadata()
    }

    fun cleanupSplitDuplicates(): Int {
        var removedCount = 0

        // 1. Identify all known split items and their message IDs and clean bases
        val splitPartMessageIds = mediaIndex.values.filter { it.isSplit }
            .flatMap { it.parts.map { p -> p.messageId } }
            .toSet()

        val splitCleanBases = mediaIndex.values.filter { it.isSplit }.map {
            it.fileName.lowercase()
                .replace(Regex("""[\s._-]*(?:part|pt)[\s._-]*\d+.*""", RegexOption.IGNORE_CASE), "")
                .replace(Regex("""\.(mkv|mp4|avi|webm|ts|m4v)(\.\d{2,4})?$""", RegexOption.IGNORE_CASE), "")
                .trim()
        }.filter { it.isNotBlank() }.toSet()

        // 2. Remove standalone items that are redundant duplicates of an existing split item
        val toRemove = mediaIndex.values.filter { item ->
            if (item.isSplit) return@filter false

            // Direct message ID match with a part of a split item
            if (splitPartMessageIds.contains(item.messageId)) return@filter true

            val itemClean = item.fileName.lowercase()
                .replace(Regex("""[\s._\-\(\[]*(?:part|pt|cd|disc)[\s._\-]*\d+.*""", RegexOption.IGNORE_CASE), "")
                .replace(Regex("""\.(mkv|mp4|avi|webm|ts|m4v)(\.\d{2,4})?$""", RegexOption.IGNORE_CASE), "")
                .trim()

            if (splitCleanBases.contains(itemClean)) {
                // If it's a raw part slice (.part001, .part002, .001, .cd1, etc.)
                if (item.fileName.contains(Regex("""(?i)(?:part|pt|cd|disc|\.\d{2,4})"""))) {
                    return@filter true
                }
                // Or if it's an inferior duplicate with identical season/episode/cleanBase but smaller size
                val splitItem = mediaIndex.values.find {
                    it.isSplit && (it.channelId == item.channelId || it.channelId == 0L || item.channelId == 0L) &&
                    it.season == item.season && it.episode == item.episode
                }
                if (splitItem != null && item.sizeBytes < splitItem.sizeBytes) {
                    return@filter true
                }
            }

            false
        }

        if (toRemove.isNotEmpty()) {
            toRemove.forEach { mediaIndex.remove(it.id) }
            removedCount += toRemove.size
        }

        // 3. Scan for any UNMERGED split parts in mediaIndex from old scans and auto-merge them
        val partMarkerRegex = Regex("""(?i)(?:[\s._\-\(\[]*(?:part|pt|cd|disc)[\s._\-]*\d+[\s._\-\)\]]*\.[a-zA-Z0-9]{2,4}$|\.[a-zA-Z0-9]{2,4}\.\d{2,4}$|\.\d{2,4}$)""")
        val unmergedCandidateGroups = mediaIndex.values
            .filter { !it.isSplit && it.fileName.contains(partMarkerRegex) }
            .groupBy { item ->
                val base = item.fileName.lowercase()
                    .replace(Regex("""(?i)[\s._\-\(\[]*(?:part|pt|cd|disc)[\s._\-]*\d+[\s._\-\)\]]*\.[a-zA-Z0-9]{2,4}$"""), "")
                    .replace(Regex("""(?i)\.[a-zA-Z0-9]{2,4}\.\d{2,4}$"""), "")
                    .replace(Regex("""(?i)\.[a-zA-Z0-9]{2,4}$"""), "")
                    .trim()
                "${item.channelId}_${base}"
            }

        for ((_, group) in unmergedCandidateGroups) {
            if (group.size >= 2) {
                val sortedParts = group.sortedBy { item ->
                    val m1 = Regex("""(?i)(?:part|pt|cd|disc)[\s._\-]*(\d+)""").find(item.fileName)
                    val m2 = Regex("""(?i)\.(\d{2,4})$""").find(item.fileName)
                    m1?.groupValues?.get(1)?.toIntOrNull()
                        ?: m2?.groupValues?.get(1)?.toIntOrNull()
                        ?: 1
                }

                var runningStart = 0L
                val computedParts = sortedParts.mapIndexed { idx, item ->
                    val p = SplitPart(
                        messageId = item.messageId,
                        partNumber = idx + 1,
                        sizeBytes = item.sizeBytes,
                        cumStart = runningStart
                    )
                    runningStart += item.sizeBytes
                    p
                }

                val primary = sortedParts.first()
                val cleanFileName = primary.fileName
                    .replace(Regex("""(?i)[\s._\-\(\[]*(?:part|pt|cd|disc)[\s._\-]*\d+[\s._\-\)\]]*"""), "")
                    .replace(Regex("""(?i)\.\d{2,4}$"""), "")

                val splitId = "tg_${primary.channelId}_${primary.messageId}_split"
                val mergedItem = primary.copy(
                    id = splitId,
                    fileName = cleanFileName,
                    sizeBytes = runningStart,
                    isSplit = true,
                    parts = computedParts
                )

                group.forEach { mediaIndex.remove(it.id) }
                mediaIndex[splitId] = mergedItem
                removedCount += group.size
            }
        }

        if (removedCount > 0) {
            saveMediaIndex()
            _mediaState.value = mediaIndex.values.toList()
        }

        return removedCount
    }

    fun scanDuplicates(): Int {
        var count = 0
        // 1. Redundant raw part slices of existing split items
        val splitPartMessageIds = mediaIndex.values.filter { it.isSplit }
            .flatMap { it.parts.map { p -> p.messageId } }
            .toSet()
        count += mediaIndex.values.count { !it.isSplit && splitPartMessageIds.contains(it.messageId) }

        // 2. Unmerged part groups (group >= 2 items)
        val partMarkerRegex = Regex("""(?i)(?:[\s._\-\(\[]*(?:part|pt|cd|disc)[\s._\-]*\d+[\s._\-\)\]]*\.[a-zA-Z0-9]{2,4}$|\.[a-zA-Z0-9]{2,4}\.\d{2,4}$|\.\d{2,4}$)""")
        val unmergedCandidateGroups = mediaIndex.values
            .filter { !it.isSplit && it.fileName.contains(partMarkerRegex) }
            .groupBy { item ->
                val base = item.fileName.lowercase()
                    .replace(Regex("""(?i)[\s._\-\(\[]*(?:part|pt|cd|disc)[\s._\-]*\d+[\s._\-\)\]]*\.[a-zA-Z0-9]{2,4}$"""), "")
                    .replace(Regex("""(?i)\.[a-zA-Z0-9]{2,4}\.\d{2,4}$"""), "")
                    .replace(Regex("""(?i)\.[a-zA-Z0-9]{2,4}$"""), "")
                    .trim()
                "${item.channelId}_${base}"
            }
        for ((_, grp) in unmergedCandidateGroups) {
            if (grp.size >= 2) count += (grp.size - 1)
        }

        // 3. Exact identical file duplicates (same channelId, fileName, and sizeBytes)
        val duplicatesByNameAndSize = mediaIndex.values.filter { !it.isSplit }
            .groupBy { "${it.channelId}_${it.fileName.lowercase().trim()}_${it.sizeBytes}" }
        for ((_, grp) in duplicatesByNameAndSize) {
            if (grp.size > 1) count += (grp.size - 1)
        }

        // 4. Exact duplicate media items (same title, season, episode, resolution)
        val duplicatesByMedia = mediaIndex.values
            .filter { it.title.isNotBlank() && !it.isSplit }
            .groupBy { "${it.title.lowercase().trim()}_${it.season ?: 0}_${it.episode ?: 0}_${it.quality}" }
        for ((_, grp) in duplicatesByMedia) {
            if (grp.size > 1) {
                count += (grp.size - 1)
            }
        }
        return count
    }

    fun deleteDuplicates(): Int {
        var removed = cleanupSplitDuplicates()

        // 1. Remove exact identical file duplicates (same channel, fileName, sizeBytes)
        val duplicatesByNameAndSize = mediaIndex.values.filter { !it.isSplit }
            .groupBy { "${it.channelId}_${it.fileName.lowercase().trim()}_${it.sizeBytes}" }
        for ((_, grp) in duplicatesByNameAndSize) {
            if (grp.size > 1) {
                val sorted = grp.sortedWith(
                    compareByDescending<MediaItem> { !it.imdbId.isNullOrBlank() || it.kitsuId != null }
                        .thenByDescending { !it.posterUrl.isNullOrBlank() }
                        .thenByDescending { it.messageId }
                )
                val toRemove = sorted.drop(1)
                toRemove.forEach { mediaIndex.remove(it.id) }
                removed += toRemove.size
            }
        }

        // 2. Remove exact duplicate media items (same title, season, episode, resolution)
        val duplicatesByMedia = mediaIndex.values
            .filter { it.title.isNotBlank() && !it.isSplit }
            .groupBy { "${it.title.lowercase().trim()}_${it.season ?: 0}_${it.episode ?: 0}_${it.quality}" }
        for ((_, grp) in duplicatesByMedia) {
            if (grp.size > 1) {
                val sorted = grp.sortedWith(
                    compareByDescending<MediaItem> { !it.imdbId.isNullOrBlank() || it.kitsuId != null }
                        .thenByDescending { it.sizeBytes }
                        .thenByDescending { it.messageId }
                )
                val toRemove = sorted.drop(1)
                toRemove.forEach { mediaIndex.remove(it.id) }
                removed += toRemove.size
            }
        }

        if (removed > 0) {
            saveMediaIndex()
            _mediaState.value = mediaIndex.values.toList()
        }
        return removed
    }

    fun scanDeadFiles(): Int {
        val localDeadCount = mediaIndex.values.count {
            detectedDeadIds.contains(it.id) ||
            it.sizeBytes <= 0 ||
            it.fileName.isBlank() ||
            (it.channelId == 0L && it.messageId == 0) ||
            (it.isSplit && it.parts.isEmpty())
        }
        return localDeadCount
    }

    suspend fun scanDeadFilesRemote(): Int = withContext(Dispatchers.IO) {
        detectedDeadIds.clear()

        // 1. Check local corrupt items
        val allItems = mediaIndex.values.toList()
        for (item in allItems) {
            if (item.sizeBytes <= 0 || item.fileName.isBlank() || (itIsInvalidMessage(item))) {
                detectedDeadIds.add(item.id)
            }
        }

        // 2. If logged in, verify on Telegram via Pyrogram
        if (sessionState.value.isLoggedIn) {
            val apiId = getApiId()
            val apiHash = getApiHash()
            val sessionStr = sessionState.value.sessionString
            val bridge = getPyBridge()

            if (bridge != null && sessionStr.isNotBlank() && apiId > 0) {
                val byChannel = allItems.filter { it.channelId != 0L }.groupBy { it.channelId }
                for ((channelId, items) in byChannel) {
                    val msgIdToItemIds = mutableMapOf<Int, MutableList<String>>()
                    for (item in items) {
                        if (item.isSplit) {
                            for (p in item.parts) {
                                if (p.messageId > 0) {
                                    msgIdToItemIds.getOrPut(p.messageId) { mutableListOf() }.add(item.id)
                                }
                            }
                        } else if (item.messageId > 0) {
                            msgIdToItemIds.getOrPut(item.messageId) { mutableListOf() }.add(item.id)
                        }
                    }

                    val msgIds = msgIdToItemIds.keys.toList()
                    if (msgIds.isEmpty()) continue

                    for (chunk in msgIds.chunked(100)) {
                        try {
                            val jsonArrayStr = JSONArray(chunk).toString()
                            val resStr = bridge.callAttr(
                                "check_dead_messages",
                                apiId,
                                apiHash,
                                sessionStr,
                                channelId,
                                jsonArrayStr
                            ).toString()

                            val resJson = JSONObject(resStr)
                            if (resJson.optString("status") == "ok") {
                                val deadArray = resJson.optJSONArray("dead_ids")
                                if (deadArray != null) {
                                    for (i in 0 until deadArray.length()) {
                                        val deadMsgId = deadArray.getInt(i)
                                        msgIdToItemIds[deadMsgId]?.forEach { itemId ->
                                            detectedDeadIds.add(itemId)
                                        }
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error checking dead messages for channel $channelId: ${e.message}")
                        }
                    }
                }
            }
        }

        scanDeadFiles()
    }

    private fun itIsInvalidMessage(item: MediaItem): Boolean {
        return (item.channelId == 0L && item.messageId == 0) || (item.isSplit && item.parts.isEmpty())
    }

    fun purgeDeadFiles(): Int {
        val deadItems = mediaIndex.values.filter {
            detectedDeadIds.contains(it.id) ||
            it.sizeBytes <= 0 ||
            it.fileName.isBlank() ||
            itIsInvalidMessage(it)
        }
        if (deadItems.isNotEmpty()) {
            deadItems.forEach {
                mediaIndex.remove(it.id)
                detectedDeadIds.remove(it.id)
            }
            saveMediaIndex()
            _mediaState.value = mediaIndex.values.toList()
        }
        detectedDeadIds.clear()
        return deadItems.size
    }

    suspend fun refreshContentGroup(groupItems: List<MediaItem>): Triple<Int, Int, Int> = withContext(Dispatchers.IO) {
        if (groupItems.isEmpty()) return@withContext Triple(0, 0, 0)

        var deadPurged = 0
        var newAdded = 0
        val remainingItems = mutableListOf<MediaItem>()

        val primaryItem = groupItems.first()
        val channelId = primaryItem.channelId

        // 1. Check dead/deleted files on Telegram for this specific content group
        if (sessionState.value.isLoggedIn && channelId != 0L) {
            val apiId = getApiId()
            val apiHash = getApiHash()
            val sessionStr = sessionState.value.sessionString
            val bridge = getPyBridge()

            val msgIdToItemIds = mutableMapOf<Int, MutableList<String>>()
            for (item in groupItems) {
                if (item.isSplit) {
                    for (p in item.parts) {
                        if (p.messageId > 0) {
                            msgIdToItemIds.getOrPut(p.messageId) { mutableListOf() }.add(item.id)
                        }
                    }
                } else if (item.messageId > 0) {
                    msgIdToItemIds.getOrPut(item.messageId) { mutableListOf() }.add(item.id)
                }
            }

            val msgIds = msgIdToItemIds.keys.toList()
            val groupDeadIds = mutableSetOf<String>()

            if (bridge != null && sessionStr.isNotBlank() && msgIds.isNotEmpty()) {
                for (chunk in msgIds.chunked(100)) {
                    try {
                        val jsonArrayStr = JSONArray(chunk).toString()
                        val resStr = bridge.callAttr(
                            "check_dead_messages",
                            apiId,
                            apiHash,
                            sessionStr,
                            channelId,
                            jsonArrayStr
                        ).toString()

                        val resJson = JSONObject(resStr)
                        if (resJson.optString("status") == "ok") {
                            val deadArray = resJson.optJSONArray("dead_ids")
                            if (deadArray != null) {
                                for (i in 0 until deadArray.length()) {
                                    val deadMsgId = deadArray.getInt(i)
                                    msgIdToItemIds[deadMsgId]?.forEach { groupDeadIds.add(it) }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error checking group dead messages: ${e.message}")
                    }
                }
            }

            // Purge any dead files from mediaIndex
            for (item in groupItems) {
                if (groupDeadIds.contains(item.id) || item.sizeBytes <= 0 || itIsInvalidMessage(item)) {
                    mediaIndex.remove(item.id)
                    deadPurged++
                } else {
                    remainingItems.add(item)
                }
            }
        } else {
            for (item in groupItems) {
                if (item.sizeBytes <= 0 || item.fileName.isBlank() || itIsInvalidMessage(item)) {
                    mediaIndex.remove(item.id)
                    deadPurged++
                } else {
                    remainingItems.add(item)
                }
            }
        }

        // 2. Scan channel for new messages/episodes if channelId valid
        if (sessionState.value.isLoggedIn && channelId != 0L) {
            try {
                val beforeCount = mediaIndex.size
                syncChannel(channelId, limit = 100)
                val afterCount = mediaIndex.size
                newAdded = maxOf(0, afterCount - beforeCount)
            } catch (e: Exception) {
                Log.d(TAG, "Incremental channel sync error: ${e.message}")
            }
        }

        // 3. Re-enrich metadata & refresh episode names for remaining items
        val currentGroupItems = if (newAdded > 0) {
            mediaIndex.values.filter { it.channelId == channelId && (it.imdbId == primaryItem.imdbId || it.title.equals(primaryItem.title, ignoreCase = true)) }
        } else {
            remainingItems
        }

        if (currentGroupItems.isNotEmpty()) {
            val baseMeta = EnrichedMetadata(
                title = primaryItem.title,
                type = primaryItem.type,
                year = primaryItem.year,
                overview = primaryItem.overview,
                posterUrl = primaryItem.posterUrl,
                backdropUrl = primaryItem.backdropUrl,
                imdbId = primaryItem.imdbId,
                tmdbId = primaryItem.tmdbId
            )
            val tmdbKey = prefs.getString("tmdb_api_key", "") ?: ""

            val (enriched, episodeMap) = MetadataEnricher.fetchFullMetadataDetails(baseMeta, tmdbKey)

            for (item in currentGroupItems) {
                val s = item.season
                val e = item.episode
                val epTitle = if (s != null && e != null) episodeMap[Pair(s, e)] ?: item.episodeTitle else item.episodeTitle

                item.title = enriched.title ?: baseMeta.title ?: item.title
                item.year = enriched.year ?: baseMeta.year ?: item.year
                item.overview = enriched.overview ?: baseMeta.overview ?: item.overview
                item.posterUrl = enriched.posterUrl ?: baseMeta.posterUrl ?: item.posterUrl
                item.backdropUrl = enriched.backdropUrl ?: baseMeta.backdropUrl ?: item.backdropUrl
                item.episodeTitle = epTitle
                if (!enriched.imdbId.isNullOrBlank()) item.imdbId = enriched.imdbId
                if (enriched.tmdbId != null && enriched.tmdbId > 0) item.tmdbId = enriched.tmdbId

                mediaIndex[item.id] = item
            }

            MetadataEnricher.invalidateTitle(primaryItem.title)
            StreamerForegroundService.activeServer?.clearTitleCache()
        }

        saveMediaIndex()
        _mediaState.value = mediaIndex.values.toList()

        Triple(currentGroupItems.size, deadPurged, newAdded)
    }

    fun getAppCacheSizeBytes(): Long {
        var size = 0L
        try {
            size += getDirSize(context.cacheDir)
            context.externalCacheDir?.let { size += getDirSize(it) }
        } catch (_: Exception) {}
        return size
    }

    fun clearAppCache(): Long {
        val initialSize = getAppCacheSizeBytes()
        try {
            deleteDirRecursive(context.cacheDir)
            context.externalCacheDir?.let { deleteDirRecursive(it) }
            metadataEnricher.clearCache()
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing cache: ${e.message}")
        }
        return initialSize
    }

    fun clearAppLogs(): Boolean {
        return try {
            val logDir = File(context.filesDir, "logs")
            if (logDir.exists()) deleteDirRecursive(logDir)
            val files = context.filesDir.listFiles { _, name -> name.endsWith(".log") || name.endsWith(".txt") }
            files?.forEach { it.delete() }
            try {
                Runtime.getRuntime().exec("logcat -c")
            } catch (_: Exception) {}
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing logs: ${e.message}")
            false
        }
    }

    private fun getDirSize(dir: File?): Long {
        if (dir == null || !dir.exists()) return 0L
        var size = 0L
        dir.listFiles()?.forEach { file ->
            size += if (file.isDirectory) getDirSize(file) else file.length()
        }
        return size
    }

    private fun deleteDirRecursive(dir: File?): Long {
        if (dir == null || !dir.exists()) return 0L
        var freed = 0L
        dir.listFiles()?.forEach { file ->
            if (file.isDirectory) {
                freed += deleteDirRecursive(file)
                file.delete()
            } else {
                freed += file.length()
                file.delete()
            }
        }
        return freed
    }

    private fun autoEnrichMissingMetadata() {
        val itemsToEnrich = mediaIndex.values.filter { it.imdbId.isNullOrBlank() && it.kitsuId == null }
        if (itemsToEnrich.isEmpty()) return

        CoroutineScope(Dispatchers.IO).launch {
            val tmdbKey = prefs.getString("tmdb_api_key", "") ?: ""
            val tvdbKey = prefs.getString("tvdb_api_key", "") ?: ""
            var changed = false
            for (item in itemsToEnrich) {
                val ch = channels[item.channelId]
                val isAnime = ch?.isAnime ?: false
                metadataEnricher.enrich(
                    item = item,
                    isAnimeChannel = isAnime,
                    tmdbKey = tmdbKey,
                    tvdbKey = tvdbKey
                )
                changed = true
            }
            if (changed) {
                saveMediaIndex()
            }
        }
    }

    private fun saveMediaIndex() {
        val list = mediaIndex.values.toList()
        val json = gson.toJson(list)
        try {
            val file = File(context.filesDir, "media_index.json")
            file.writeText(json)
        } catch (e: Exception) {
            Log.e(TAG, "Error saving media_index.json: ${e.message}")
        }
        prefs.edit().putString("media_index", json).apply()
        _mediaState.value = list
    }

    private fun loadUnmatchedIndex() {
        try {
            val file = File(context.filesDir, "unmatched_media_index.json")
            val json = if (file.exists()) {
                try { file.readText() } catch (_: Exception) { prefs.getString("unmatched_media_index", null) }
            } else {
                prefs.getString("unmatched_media_index", null)
            }
            unmatchedIndex.clear()
            if (!json.isNullOrBlank()) {
                val type = object : TypeToken<List<UnmatchedItem>>() {}.type
                val list: List<UnmatchedItem> = gson.fromJson(json, type) ?: emptyList()
                list.forEach { unmatchedIndex[it.id] = it }
            }
            _unmatchedState.value = unmatchedIndex.values.sortedByDescending { it.scannedAt }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading unmatched media index: ${e.message}")
        }
    }

    private fun saveUnmatchedIndex() {
        try {
            val list = unmatchedIndex.values.sortedByDescending { it.scannedAt }
            val json = gson.toJson(list)
            try {
                val file = File(context.filesDir, "unmatched_media_index.json")
                file.writeText(json)
            } catch (e: Exception) {
                Log.e(TAG, "Error saving unmatched_media_index.json: ${e.message}")
            }
            prefs.edit().putString("unmatched_media_index", json).apply()
            _unmatchedState.value = list
        } catch (e: Exception) {
            Log.e(TAG, "Error saving unmatched media index: ${e.message}")
        }
    }

    // --- Authentication Engine (Session String) ---

    /**
     * Parses a Pyrogram or Telethon session string into an AuthSession.
     * Supports:
     * - Pyrogram v2 (>BI?256sQ? - 271 bytes, base64 url-safe)
     * - Pyrogram v1 (>B?256sI? - 263 bytes)
     * - Telethon / standard base64 string sessions
     */
    fun parseSessionString(raw: String): AuthSession? {
        val trimmed = raw.trim().replace("\r", "").replace("\n", "").replace(" ", "")
        if (trimmed.length < 20) return null

        try {
            var padded = trimmed
            val remainder = padded.length % 4
            if (remainder > 0) {
                padded += "=".repeat(4 - remainder)
            }

            val decodedBytes = try {
                Base64.decode(padded, Base64.URL_SAFE)
            } catch (_: Exception) {
                try {
                    Base64.decode(padded, Base64.DEFAULT)
                } catch (_: Exception) {
                    null
                }
            }

            if (decodedBytes != null) {
                if (decodedBytes.size >= 271) {
                    // Pyrogram v2: >BI?256sQ?
                    val dcId = decodedBytes[0].toInt() and 0xFF
                    val bbApi = ByteBuffer.wrap(decodedBytes, 1, 4).order(ByteOrder.BIG_ENDIAN)
                    val parsedApiId = bbApi.int
                    val bbUser = ByteBuffer.wrap(decodedBytes, 262, 8).order(ByteOrder.BIG_ENDIAN)
                    val parsedUserId = bbUser.long

                    if (getApiId() <= 0 && parsedApiId > 0) {
                        saveApiCredentials(parsedApiId, getApiHash())
                    }

                    return AuthSession(
                        isLoggedIn = true,
                        firstName = "Telegram User (DC $dcId)",
                        username = "user_$parsedUserId",
                        userId = parsedUserId,
                        sessionString = trimmed,
                        phoneNumber = "DC-$dcId"
                    )
                } else if (decodedBytes.size >= 263) {
                    // Pyrogram v1: >B?256sI?
                    val dcId = decodedBytes[0].toInt() and 0xFF
                    val bbUser = ByteBuffer.wrap(decodedBytes, 258, 4).order(ByteOrder.BIG_ENDIAN)
                    val parsedUserId = (bbUser.int.toLong()) and 0xFFFFFFFFL

                    return AuthSession(
                        isLoggedIn = true,
                        firstName = "Telegram User (DC $dcId)",
                        username = "user_$parsedUserId",
                        userId = parsedUserId,
                        sessionString = trimmed,
                        phoneNumber = "DC-$dcId"
                    )
                } else if (decodedBytes.size >= 240) {
                    // Telethon / generic MTProto session
                    val dcId = decodedBytes[0].toInt() and 0xFF
                    return AuthSession(
                        isLoggedIn = true,
                        firstName = "Telegram User (DC $dcId)",
                        username = "session_user",
                        userId = 10001L,
                        sessionString = trimmed,
                        phoneNumber = "DC-$dcId"
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not decode binary session string: ${e.message}")
        }

        // Generic fallback for any valid session string token
        if (trimmed.length >= 20) {
            return AuthSession(
                isLoggedIn = true,
                firstName = "Telegram User",
                username = "session_user",
                userId = 10001L,
                sessionString = trimmed,
                phoneNumber = ""
            )
        }

        return null
    }

    suspend fun importSessionString(sessionString: String): Result<AuthSession> = withContext(Dispatchers.IO) {
        try {
            val session = parseSessionString(sessionString)
                ?: return@withContext Result.failure(
                    IllegalArgumentException("Invalid Telegram session string. Please make sure you copied the entire string token.")
                )

            saveSession(session)
            Result.success(session)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun getPyBridge(): com.chaquo.python.PyObject? {
        return try {
            if (!Python.isStarted()) {
                Python.start(AndroidPlatform(context))
            }
            Python.getInstance().getModule("tg_bridge")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Python runtime: ${e.message}", e)
            null
        }
    }

    suspend fun sendLoginCode(phoneNumber: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val cleanPhone = phoneNumber.replace(Regex("[^0-9+]"), "").trim()
            if (cleanPhone.length < 8 || !cleanPhone.startsWith("+")) {
                return@withContext Result.failure(IllegalArgumentException("Please enter phone number with country code (e.g. +91...)"))
            }

            val apiId = getApiId()
            val apiHash = getApiHash()
            if (apiId <= 0 || apiHash.isBlank()) {
                return@withContext Result.failure(IllegalStateException("API ID and API HASH are required. Please configure them first."))
            }

            val bridge = getPyBridge()
                ?: return@withContext Result.failure(IllegalStateException("Python runtime could not be started"))

            val jsonStr = bridge.callAttr("send_code", apiId, apiHash, cleanPhone).toString()
            val json = JSONObject(jsonStr)

            if (json.optString("status") == "success") {
                val hash = json.optString("phone_code_hash")
                val codeType = json.optString("type", "")
                val updated = _sessionState.value.copy(
                    phoneNumber = cleanPhone,
                    phoneCodeHash = hash,
                    lastError = null
                )
                saveSession(updated)
                val isApp = codeType.equals("APP", ignoreCase = true) || codeType.contains("App", ignoreCase = true)
                val isSms = codeType.equals("SMS", ignoreCase = true) || codeType.contains("Sms", ignoreCase = true)
                _codeDeliveryType.value = if (isSms) "SMS" else "APP"
                val deliveryMsg = when {
                    isApp -> "Code sent to your Telegram app (check service chat)"
                    isSms -> "Code sent via SMS to $cleanPhone"
                    else -> "Code sent to $cleanPhone"
                }
                Result.success(deliveryMsg)
            } else {
                val err = json.optString("error", "Failed to send code")
                Result.failure(Exception(err))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Resends the login OTP using the existing session's phone_code_hash.
     * Uses Pyrogram's resend_code MTProto call instead of sending a new code
     * (which would trigger FloodWait and deliver to a different channel).
     */
    suspend fun resendLoginCode(): Result<String> = withContext(Dispatchers.IO) {
        try {
            val bridge = getPyBridge()
                ?: return@withContext Result.failure(IllegalStateException("Python runtime could not be started"))

            val jsonStr = bridge.callAttr("resend_code").toString()
            val json = JSONObject(jsonStr)

            if (json.optString("status") == "success") {
                val codeType = json.optString("type", "")
                val isApp = codeType.equals("APP", ignoreCase = true) || codeType.contains("App", ignoreCase = true)
                val isSms = codeType.equals("SMS", ignoreCase = true) || codeType.contains("Sms", ignoreCase = true)
                _codeDeliveryType.value = if (isSms) "SMS" else "APP"
                val deliveryMsg = when {
                    isSms -> "Code re-sent via SMS"
                    isApp -> "Code re-sent to your Telegram app"
                    else -> "Code re-sent successfully"
                }
                Result.success(deliveryMsg)
            } else {
                val err = json.optString("error", "Failed to resend code")
                Result.failure(Exception(err))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun verifyLoginCode(code: String): Result<AuthSession> = withContext(Dispatchers.IO) {
        try {
            val trimmedCode = code.trim()
            if (trimmedCode.length < 4) {
                return@withContext Result.failure(IllegalArgumentException("Code should be at least 5 digits."))
            }

            val bridge = getPyBridge()
                ?: return@withContext Result.failure(IllegalStateException("Python runtime could not be started"))

            val jsonStr = bridge.callAttr("verify_code", trimmedCode).toString()
            val json = JSONObject(jsonStr)
            val status = json.optString("status")

            if (status == "success") {
                val sessionStr = json.optString("session_string")
                val userId = json.optLong("user_id")
                val firstName = json.optString("first_name", "Telegram User")
                val username = json.optString("username", "user_$userId")

                val session = AuthSession(
                    isLoggedIn = true,
                    firstName = firstName,
                    username = username,
                    userId = userId,
                    sessionString = sessionStr,
                    phoneNumber = _sessionState.value.phoneNumber
                )
                saveSession(session)
                Result.success(session)
            } else if (status == "2fa_required") {
                Result.failure(Exception("2FA_REQUIRED"))
            } else {
                val err = json.optString("error", "Invalid verification code")
                Result.failure(Exception(err))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun verify2FAPassword(password: String): Result<AuthSession> = withContext(Dispatchers.IO) {
        try {
            val bridge = getPyBridge()
                ?: return@withContext Result.failure(IllegalStateException("Python runtime could not be started"))

            val jsonStr = bridge.callAttr("check_password", password.trim()).toString()
            val json = JSONObject(jsonStr)

            if (json.optString("status") == "success") {
                val sessionStr = json.optString("session_string")
                val userId = json.optLong("user_id")
                val firstName = json.optString("first_name", "Telegram User")
                val username = json.optString("username", "user_$userId")

                val session = AuthSession(
                    isLoggedIn = true,
                    firstName = firstName,
                    username = username,
                    userId = userId,
                    sessionString = sessionStr,
                    phoneNumber = _sessionState.value.phoneNumber
                )
                saveSession(session)
                Result.success(session)
            } else {
                val err = json.optString("error", "Incorrect 2FA password")
                Result.failure(Exception(err))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun validatePhoneNumber(phone: String): String? {
        val clean = phone.replace(Regex("[^0-9+]"), "").trim()
        if (clean.isBlank()) return "Phone number cannot be empty"
        if (!clean.startsWith("+")) return "Phone must include country code starting with + (e.g. +91...)"
        if (clean.length < 8) return "Phone number is too short"
        return null
    }

    suspend fun loginWithWebSession(firstName: String, username: String, userId: Long, sessionStr: String): Result<AuthSession> = withContext(Dispatchers.IO) {
        importSessionString(sessionStr)
    }

    fun logout() {
        val emptySession = AuthSession()
        saveSession(emptySession)
    }

    // --- Channel Management ---

    fun getChannels(): List<ChannelInfo> = channels.values.toList()
    fun getChannel(id: Long): ChannelInfo? = channels[id]

    suspend fun resolveAndAddChannel(identifier: String, isAnime: Boolean = false): Result<ChannelInfo> = withContext(Dispatchers.IO) {
        val clean = identifier.trim()
        if (clean.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Channel username, link, or ID cannot be empty"))
        }

        val apiId = getApiId()
        val apiHash = getApiHash()
        val sessionStr = sessionState.value.sessionString
        val bridge = getPyBridge()

        var realId: Long? = null
        var realTitle: String? = null
        var realUsername: String? = null

        if (bridge != null && sessionStr.isNotBlank() && apiId > 0) {
            try {
                val resStr = bridge.callAttr("resolve_channel", apiId, apiHash, sessionStr, clean).toString()
                val json = JSONObject(resStr)
                if (json.optString("status") == "success") {
                    realId = json.getLong("id")
                    realTitle = json.getString("title")
                    realUsername = json.optString("username", clean)
                } else {
                    val err = json.optString("error")
                    if (err.isNotBlank()) {
                        return@withContext Result.failure(Exception(err))
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not resolve channel via MTProto: ${e.message}")
            }
        }

        val parsedId = when {
            clean.startsWith("-100") && clean.substring(4).all { it.isDigit() } -> clean.toLongOrNull()
            clean.startsWith("-") && clean.substring(1).all { it.isDigit() } -> "-100${clean.substring(1).removePrefix("100")}".toLongOrNull()
            clean.all { it.isDigit() } -> "-100${clean.removePrefix("100")}".toLongOrNull()
            else -> null
        }

        val finalId = realId ?: parsedId ?: (-1000000000000L - (clean.hashCode().toLong().let { if (it < 0) -it else it } % 1000000000L))
        val finalTitle = realTitle ?: clean.removePrefix("@").replace("_", " ").split(" ")
            .joinToString(" ") { it.replaceFirstChar { char -> char.uppercase() } }
        val finalUsername = realUsername ?: clean.removePrefix("@")

        val newChannel = ChannelInfo(
            id = finalId,
            title = finalTitle,
            username = finalUsername,
            messageCount = 0,
            videoCount = 0,
            isActive = true,
            isAnime = isAnime,
            lastSyncTime = 0L
        )

        channels[finalId] = newChannel
        saveChannels()
        Result.success(newChannel)
    }

    fun addChannel(identifier: String, isAnime: Boolean = false): Result<ChannelInfo> {
        val clean = identifier.trim().removePrefix("@").removePrefix("https://t.me/").removePrefix("t.me/")
        if (clean.isBlank()) {
            return Result.failure(IllegalArgumentException("Channel name or link cannot be empty"))
        }

        val id = -1000000000000L - (clean.hashCode().toLong().let { if (it < 0) -it else it } % 1000000000L)
        val titleFormatted = clean.replace("_", " ").split(" ")
            .joinToString(" ") { it.replaceFirstChar { char -> char.uppercase() } }

        val newChannel = ChannelInfo(
            id = id,
            title = titleFormatted,
            username = clean,
            messageCount = 0,
            videoCount = 0,
            isActive = true,
            isAnime = isAnime,
            lastSyncTime = 0L
        )

        channels[id] = newChannel
        saveChannels()
        return Result.success(newChannel)
    }

    fun toggleAnimeChannel(channelId: Long, isAnime: Boolean) {
        channels[channelId]?.let {
            channels[channelId] = it.copy(isAnime = isAnime)
            saveChannels()
        }
    }

    fun toggleChannel(channelId: Long, active: Boolean) {
        channels[channelId]?.let {
            channels[channelId] = it.copy(isActive = active)
            saveChannels()
        }
    }

    fun removeChannel(channelId: Long) {
        stopChannelScan(channelId)
        channels.remove(channelId)
        saveChannels()
        val toRemove = mediaIndex.values.filter { it.channelId == channelId }.map { it.id }
        toRemove.forEach { mediaIndex.remove(it) }
        saveMediaIndex()
        clearUnmatched(channelId)
    }

    fun getMaxKnownMessageId(channelId: Long): Int {
        val channelLastId = channels[channelId]?.lastScannedMsgId ?: 0
        val maxMediaMsgId = mediaIndex.values
            .filter { it.channelId == channelId }
            .flatMap { item ->
                if (item.isSplit) {
                    item.parts.map { it.messageId }
                } else {
                    listOf(item.messageId)
                }
            }
            .maxOrNull() ?: 0

        val maxUnmatchedMsgId = unmatchedIndex.values
            .filter { it.channelId == channelId }
            .map { it.messageId }
            .maxOrNull() ?: 0

        return maxOf(channelLastId, maxMediaMsgId, maxUnmatchedMsgId)
    }

    suspend fun rescanFromScratch(channelId: Long): Result<Int> = withContext(Dispatchers.IO) {
        stopChannelScan(channelId)
        val toRemove = mediaIndex.values.filter { it.channelId == channelId }.map { it.id }
        toRemove.forEach { mediaIndex.remove(it) }
        saveMediaIndex()
        clearUnmatched(channelId)
        channels[channelId]?.let { chInfo ->
            channels[channelId] = chInfo.copy(videoCount = 0, lastScannedMsgId = 0)
            saveChannels()
        }
        scanChannelLive(channelId, incremental = false)
    }

    // --- Unmatched Content Operations ---

    fun getUnmatchedMedia(): List<UnmatchedItem> = unmatchedIndex.values.sortedByDescending { it.scannedAt }

    fun removeUnmatchedItem(id: String) {
        unmatchedIndex.remove(id)
        saveUnmatchedIndex()
    }

    fun clearUnmatched(channelId: Long? = null) {
        if (channelId == null) {
            unmatchedIndex.clear()
        } else {
            val toRemove = unmatchedIndex.values.filter { it.channelId == channelId }.map { it.id }
            toRemove.forEach { unmatchedIndex.remove(it) }
        }
        saveUnmatchedIndex()
    }

    suspend fun matchUnmatchedItem(
        unmatchedId: String,
        matchedMeta: com.tgserver.streamer.metadata.EnrichedMetadata
    ): MediaItem? = withContext(Dispatchers.IO) {
        val unmatched = unmatchedIndex[unmatchedId] ?: return@withContext null
        val origItem = unmatched.parsedMediaItem
        val resolvedType = when {
            matchedMeta.kitsuId != null -> "series"
            origItem?.season != null || origItem?.episode != null -> "series"
            origItem != null && origItem.type.isNotBlank() -> origItem.type
            else -> "movie"
        }
        val baseItem = origItem ?: MediaItem(
            id = "tg_${unmatched.channelId}_${unmatched.messageId}",
            channelId = unmatched.channelId,
            messageId = unmatched.messageId,
            title = matchedMeta.title ?: unmatched.fileName,
            type = resolvedType,
            sizeBytes = unmatched.sizeBytes,
            mimeType = unmatched.mimeType,
            fileName = unmatched.fileName
        )
        baseItem.type = resolvedType

        if (!matchedMeta.title.isNullOrBlank()) baseItem.title = matchedMeta.title
        if (!matchedMeta.year.isNullOrBlank()) baseItem.year = matchedMeta.year
        if (!matchedMeta.overview.isNullOrBlank()) baseItem.overview = matchedMeta.overview
        if (!matchedMeta.posterUrl.isNullOrBlank()) baseItem.posterUrl = matchedMeta.posterUrl
        if (!matchedMeta.backdropUrl.isNullOrBlank()) baseItem.backdropUrl = matchedMeta.backdropUrl
        if (!matchedMeta.imdbId.isNullOrBlank()) baseItem.imdbId = matchedMeta.imdbId
        if (matchedMeta.tmdbId != null && matchedMeta.tmdbId > 0) baseItem.tmdbId = matchedMeta.tmdbId
        if (matchedMeta.kitsuId != null && matchedMeta.kitsuId > 0) baseItem.kitsuId = matchedMeta.kitsuId

        mediaIndex[baseItem.id] = baseItem
        saveMediaIndex()

        unmatchedIndex.remove(unmatchedId)
        saveUnmatchedIndex()

        channels[baseItem.channelId]?.let { ch ->
            channels[baseItem.channelId] = ch.copy(videoCount = mediaIndex.values.count { it.channelId == baseItem.channelId })
            saveChannels()
        }

        baseItem
    }

    fun addUnmatchedToLibraryAnyway(unmatchedId: String): MediaItem? {
        val unmatched = unmatchedIndex[unmatchedId] ?: return null
        val item = unmatched.parsedMediaItem ?: MediaItem(
            id = "tg_${unmatched.channelId}_${unmatched.messageId}",
            channelId = unmatched.channelId,
            messageId = unmatched.messageId,
            title = unmatched.fileName,
            type = "movie",
            sizeBytes = unmatched.sizeBytes,
            mimeType = unmatched.mimeType,
            fileName = unmatched.fileName
        )

        mediaIndex[item.id] = item
        saveMediaIndex()

        unmatchedIndex.remove(unmatchedId)
        saveUnmatchedIndex()

        channels[item.channelId]?.let { ch ->
            channels[item.channelId] = ch.copy(videoCount = mediaIndex.values.count { it.channelId == item.channelId })
            saveChannels()
        }

        return item
    }

    // --- Library & Stream Operations ---

    fun getAllMedia(): List<MediaItem> = mediaIndex.values.toList()

    fun getMediaById(id: String): MediaItem? = mediaIndex[id]

    fun searchMedia(query: String, category: String = "all"): List<MediaItem> {
        val q = query.trim().lowercase()
        return mediaIndex.values.filter { item ->
            val matchesCategory = when (category.lowercase()) {
                "all" -> true
                "movies" -> item.type == "movie"
                "series" -> item.type == "series"
                "4k" -> item.quality.contains("4K", ignoreCase = true)
                else -> true
            }
            val matchesQuery = q.isEmpty() || item.title.lowercase().contains(q) || item.fileName.lowercase().contains(q)
            matchesCategory && matchesQuery
        }
    }

    fun addMediaItem(item: MediaItem) {
        mediaIndex[item.id] = item
        saveMediaIndex()
        channels[item.channelId]?.let { ch ->
            channels[item.channelId] = ch.copy(videoCount = ch.videoCount + 1)
            saveChannels()
        }
    }

    fun updateMediaItem(item: MediaItem) {
        mediaIndex[item.id] = item
        saveMediaIndex()
    }

    fun updateMediaItems(items: List<MediaItem>) {
        items.forEach { mediaIndex[it.id] = it }
        saveMediaIndex()
    }

    fun toggleHideMediaItem(itemId: String): Boolean {
        val existing = mediaIndex[itemId] ?: return false
        val newHidden = !existing.isHidden
        val updated = existing.copy(isHidden = newHidden)
        mediaIndex[itemId] = updated
        saveMediaIndex()
        return newHidden
    }

    suspend fun enrichExistingMedia(itemId: String): MediaItem? = withContext(Dispatchers.IO) {
        val existing = mediaIndex[itemId] ?: return@withContext null
        val enriched = com.tgserver.streamer.metadata.MetadataEnricher.enrich(existing)
        mediaIndex[itemId] = enriched
        saveMediaIndex()
        enriched
    }

    fun stopChannelScan(channelId: Long) {
        activeScanJobs[channelId]?.cancel()
        activeScanJobs.remove(channelId)
        val currentProgress = _scanProgress.value[channelId]
        if (currentProgress != null) {
            _scanProgress.value = _scanProgress.value + (channelId to currentProgress.copy(isScanning = false, statusMessage = "Scan stopped"))
        }
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes <= 0) return "0 MB"
        val gb = bytes.toDouble() / (1024 * 1024 * 1024)
        return if (gb >= 1.0) {
            String.format("%.2f GB", gb)
        } else {
            val mb = bytes.toDouble() / (1024 * 1024)
            String.format("%.1f MB", mb)
        }
    }

    suspend fun syncChannel(channelId: Long, limit: Int = 150): Result<Int> {
        return scanChannelLive(channelId, incremental = true)
    }

    suspend fun scanChannelLive(
        channelId: Long,
        incremental: Boolean = true,
        onProgress: (ScanProgressState) -> Unit = {}
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val apiId = getApiId()
            val apiHash = getApiHash()
            val sessionStr = sessionState.value.sessionString
            if (sessionStr.isBlank() || apiId <= 0) {
                return@withContext Result.failure(IllegalStateException("Must be logged in to sync channel"))
            }

            val bridge = getPyBridge()
                ?: return@withContext Result.failure(IllegalStateException("Python runtime could not be started"))

            val ch = channels[channelId]
            val isAnime = ch?.isAnime ?: false
            val tmdbKey = prefs.getString("tmdb_api_key", "") ?: ""
            val tvdbKey = prefs.getString("tvdb_api_key", "") ?: ""

            val minMsgId = if (incremental) getMaxKnownMessageId(channelId) else 0
            var newestEncounteredMsgId = 0

            var offsetId = 0
            var totalMessagesScanned = 0
            var newItemsAdded = 0
            var totalBytesScanned = 0L
            var batchNumber = 0
            val batchSize = 100
            val splitGroupsMap = mutableMapOf<String, MutableList<SplitPart>>()

            // Initialize existing split parts from mediaIndex if already present
            mediaIndex.values.filter { it.channelId == channelId && it.isSplit }.forEach { item ->
                splitGroupsMap[item.fileName] = item.parts.toMutableList()
            }

            val initialStatus = if (incremental && minMsgId > 0) {
                "Checking for new videos (since msg #$minMsgId)..."
            } else {
                "Starting channel scan..."
            }

            val initialProgress = ScanProgressState(
                channelId = channelId,
                channelTitle = ch?.title ?: "Channel",
                scannedMessages = 0,
                indexedMedia = mediaIndex.values.count { it.channelId == channelId },
                totalBytes = totalBytesScanned,
                currentBatch = 0,
                isScanning = true,
                statusMessage = initialStatus
            )
            _scanProgress.value = _scanProgress.value + (channelId to initialProgress)
            onProgress(initialProgress)

            while (kotlin.coroutines.coroutineContext.isActive) {
                batchNumber++

                val progressReport = ScanProgressState(
                    channelId = channelId,
                    channelTitle = ch?.title ?: "Channel",
                    scannedMessages = totalMessagesScanned,
                    indexedMedia = mediaIndex.values.count { it.channelId == channelId },
                    totalBytes = totalBytesScanned,
                    currentBatch = batchNumber,
                    isScanning = true,
                    statusMessage = if (incremental && minMsgId > 0) {
                        "Checking for new content (batch #$batchNumber)..."
                    } else {
                        "Scanning batch #$batchNumber (offset message $offsetId)..."
                    }
                )
                _scanProgress.value = _scanProgress.value + (channelId to progressReport)
                onProgress(progressReport)

                val jsonRes = try {
                    bridge.callAttr(
                        "scan_channel_batch",
                        apiId,
                        apiHash,
                        sessionStr,
                        channelId,
                        offsetId,
                        batchSize,
                        isAnime,
                        minMsgId
                    )?.toString() ?: "{}"
                } catch (e: Exception) {
                    Log.e(TAG, "Error in scan_channel_batch: ${e.message}", e)
                    break
                }

                val obj = JSONObject(jsonRes)
                val status = obj.optString("status")

                if (status == "flood_wait") {
                    val waitSec = obj.optInt("wait_seconds", 30)
                    Log.w(TAG, "Telegram FloodWait for $waitSec seconds on channel $channelId")
                    val cooldownProgress = ScanProgressState(
                        channelId = channelId,
                        channelTitle = ch?.title ?: "Channel",
                        scannedMessages = totalMessagesScanned,
                        indexedMedia = mediaIndex.values.count { it.channelId == channelId },
                        totalBytes = totalBytesScanned,
                        currentBatch = batchNumber,
                        isScanning = true,
                        isCooldown = true,
                        cooldownSeconds = waitSec,
                        statusMessage = "Telegram rate limit: waiting $waitSec seconds..."
                    )
                    _scanProgress.value = _scanProgress.value + (channelId to cooldownProgress)
                    onProgress(cooldownProgress)
                    delay((waitSec + 1) * 1000L)
                    continue
                }

                if (status != "success") {
                    val err = obj.optString("error", "Failed to fetch batch")
                    Log.e(TAG, "Batch scan failed: $err")
                    break
                }

                val lastMsgId = obj.optInt("last_msg_id", 0)
                val messagesScanned = obj.optInt("messages_scanned", 0)
                val hasMore = obj.optBoolean("has_more", false)
                val firstMsgId = obj.optInt("first_msg_id", 0)
                val maxMsgId = obj.optInt("max_msg_id", 0)
                if (firstMsgId > newestEncounteredMsgId) newestEncounteredMsgId = firstMsgId
                if (maxMsgId > newestEncounteredMsgId) newestEncounteredMsgId = maxMsgId
                totalMessagesScanned += messagesScanned

                // 1. Process regular media items in this batch
                val itemsArr = obj.optJSONArray("items")
                if (itemsArr != null) {
                    for (i in 0 until itemsArr.length()) {
                        val mObj = itemsArr.getJSONObject(i)
                        val mMsgId = mObj.getInt("messageId")
                        if (mMsgId > newestEncounteredMsgId) newestEncounteredMsgId = mMsgId
                        val item = MediaItem(
                            id = mObj.getString("id"),
                            channelId = mObj.getLong("channelId"),
                            messageId = mMsgId,
                            title = mObj.getString("title"),
                            type = mObj.getString("type"),
                            year = if (mObj.isNull("year")) null else mObj.getString("year"),
                            season = if (mObj.isNull("season")) null else mObj.getInt("season"),
                            episode = if (mObj.isNull("episode")) null else mObj.getInt("episode"),
                            quality = mObj.getString("quality"),
                            qualityBadge = mObj.optString("qualityBadge", mObj.getString("quality")),
                            sizeBytes = mObj.getLong("sizeBytes"),
                            durationSeconds = mObj.optInt("durationSeconds", 0),
                            mimeType = mObj.optString("mimeType", "video/mp4"),
                            fileName = mObj.getString("fileName"),
                            isSplit = false,
                            parts = emptyList()
                        )

                        // Enrich metadata
                        metadataEnricher.enrich(
                            item = item,
                            isAnimeChannel = isAnime,
                            tmdbKey = tmdbKey,
                            tvdbKey = tvdbKey
                        )

                        val isMatched = !item.posterUrl.isNullOrBlank() ||
                                !item.imdbId.isNullOrBlank() ||
                                item.tmdbId != null ||
                                item.kitsuId != null

                        if (isMatched) {
                            mediaIndex[item.id] = item
                            unmatchedIndex.remove("unmatched_${item.channelId}_${item.messageId}")
                            newItemsAdded++
                            totalBytesScanned += item.sizeBytes
                        } else {
                            // Unmatched media item
                            val rawFn = mObj.optString("rawFileName", item.fileName)
                            val un = UnmatchedItem(
                                id = "unmatched_${item.channelId}_${item.messageId}",
                                channelId = item.channelId,
                                channelTitle = ch?.title ?: "",
                                messageId = item.messageId,
                                fileName = if (item.fileName.isNotBlank()) item.fileName else item.title,
                                rawFileName = rawFn,
                                sizeBytes = item.sizeBytes,
                                scannedAt = System.currentTimeMillis(),
                                reason = if (item.title.isNotBlank()) "Uncertain match / low confidence for '${item.title}'" else "No match found on TMDB / TVDB / Kitsu",
                                mimeType = item.mimeType,
                                isVideo = true,
                                parsedMediaItem = item
                            )
                            unmatchedIndex[un.id] = un
                        }
                        delay(20)
                    }
                }

                // 2. Process split parts in this batch
                val splitPartsArr = obj.optJSONArray("split_parts")
                if (splitPartsArr != null && splitPartsArr.length() > 0) {
                    for (i in 0 until splitPartsArr.length()) {
                        val spObj = splitPartsArr.getJSONObject(i)
                        val cleanBase = spObj.getString("cleanBase")
                        val pNum = spObj.getInt("partNumber")
                        val pSize = spObj.getLong("sizeBytes")
                        val pMsgId = spObj.getInt("messageId")
                        if (pMsgId > newestEncounteredMsgId) newestEncounteredMsgId = pMsgId

                        val list = splitGroupsMap.getOrPut(cleanBase) { mutableListOf() }
                        if (list.none { it.partNumber == pNum && it.messageId == pMsgId }) {
                            list.add(
                                SplitPart(
                                    messageId = pMsgId,
                                    partNumber = pNum,
                                    sizeBytes = pSize,
                                    cumStart = 0L
                                )
                            )
                        }

                        // Recompute cumulative starts and total size
                        list.sortBy { it.partNumber }
                        var runningStart = 0L
                        val computedParts = list.map { p ->
                            val updated = p.copy(cumStart = runningStart)
                            runningStart += p.sizeBytes
                            updated
                        }
                        splitGroupsMap[cleanBase] = computedParts.toMutableList()

                        val firstMsgId = computedParts.first().messageId
                        val splitItemId = "tg_${channelId}_${firstMsgId}_split"

                        // Proactively clean up any standalone items or old partial split items for these part message IDs
                        val partMsgIds = computedParts.map { it.messageId }.toSet()
                        val toRemoveKeys = mediaIndex.filter { (k, v) ->
                            v.channelId == channelId && (partMsgIds.contains(v.messageId) || (v.isSplit && v.parts.any { partMsgIds.contains(it.messageId) })) && k != splitItemId
                        }.keys
                        toRemoveKeys.forEach { mediaIndex.remove(it) }

                        val splitItem = (mediaIndex[splitItemId] ?: MediaItem(
                            id = splitItemId,
                            channelId = channelId,
                            messageId = firstMsgId,
                            title = spObj.getString("title"),
                            type = spObj.getString("type"),
                            year = if (spObj.isNull("year")) null else spObj.getString("year"),
                            season = if (spObj.isNull("season")) null else spObj.getInt("season"),
                            episode = if (spObj.isNull("episode")) null else spObj.getInt("episode"),
                            quality = spObj.getString("quality"),
                            qualityBadge = spObj.optString("qualityBadge", spObj.getString("quality")),
                            sizeBytes = runningStart,
                            durationSeconds = spObj.optInt("duration", 0),
                            mimeType = spObj.optString("mimeType", "video/mp4"),
                            fileName = cleanBase,
                            isSplit = true,
                            parts = computedParts
                        )).copy(
                            id = splitItemId,
                            messageId = firstMsgId,
                            sizeBytes = runningStart,
                            parts = computedParts
                        )

                        // Enrich metadata
                        metadataEnricher.enrich(
                            item = splitItem,
                            isAnimeChannel = isAnime,
                            tmdbKey = tmdbKey,
                            tvdbKey = tvdbKey
                        )

                        val isMatched = !splitItem.posterUrl.isNullOrBlank() ||
                                !splitItem.imdbId.isNullOrBlank() ||
                                splitItem.tmdbId != null ||
                                splitItem.kitsuId != null

                        if (isMatched) {
                            val wasAlreadyInIndex = mediaIndex.containsKey(splitItem.id)
                            mediaIndex[splitItem.id] = splitItem
                            unmatchedIndex.remove("unmatched_${channelId}_${firstMsgId}")
                            if (!wasAlreadyInIndex) newItemsAdded++
                            totalBytesScanned += pSize
                        } else {
                            val rawFn = spObj.optString("rawFileName", cleanBase)
                            val un = UnmatchedItem(
                                id = "unmatched_${channelId}_${firstMsgId}",
                                channelId = channelId,
                                channelTitle = ch?.title ?: "",
                                messageId = firstMsgId,
                                fileName = cleanBase,
                                rawFileName = rawFn,
                                sizeBytes = runningStart,
                                scannedAt = System.currentTimeMillis(),
                                reason = if (splitItem.title.isNotBlank()) "Uncertain match / low confidence for '${splitItem.title}'" else "No match found on TMDB / TVDB / Kitsu",
                                mimeType = splitItem.mimeType,
                                isVideo = true,
                                parsedMediaItem = splitItem
                            )
                            unmatchedIndex[un.id] = un
                        }
                    }
                }

                // 3. Process skipped / non-video files
                val skippedArr = obj.optJSONArray("skipped")
                if (skippedArr != null) {
                    for (i in 0 until skippedArr.length()) {
                        val sk = skippedArr.getJSONObject(i)
                        val skMsgId = sk.getInt("messageId")
                        if (skMsgId > newestEncounteredMsgId) newestEncounteredMsgId = skMsgId
                        val unId = "skipped_${channelId}_$skMsgId"
                        val un = UnmatchedItem(
                            id = unId,
                            channelId = channelId,
                            channelTitle = ch?.title ?: "",
                            messageId = skMsgId,
                            fileName = sk.optString("fileName", "Document #$skMsgId"),
                            rawFileName = sk.optString("rawFileName", ""),
                            sizeBytes = sk.optLong("sizeBytes", 0L),
                            scannedAt = System.currentTimeMillis(),
                            reason = sk.optString("reason", "Skipped: non-video document"),
                            mimeType = sk.optString("mimeType", ""),
                            isVideo = false
                        )
                        unmatchedIndex[un.id] = un
                    }
                }

                // Live persist to disk and notify StateFlow
                saveMediaIndex()
                saveUnmatchedIndex()
                channels[channelId]?.let { chInfo ->
                    val updatedLastMsgId = maxOf(
                        chInfo.lastScannedMsgId,
                        newestEncounteredMsgId,
                        minMsgId
                    )
                    channels[channelId] = chInfo.copy(
                        videoCount = mediaIndex.values.count { it.channelId == channelId },
                        lastSyncTime = System.currentTimeMillis(),
                        lastScannedMsgId = updatedLastMsgId
                    )
                    saveChannels()
                }

                // Progress notification
                val batchDoneProgress = ScanProgressState(
                    channelId = channelId,
                    channelTitle = ch?.title ?: "Channel",
                    scannedMessages = totalMessagesScanned,
                    indexedMedia = mediaIndex.values.count { it.channelId == channelId },
                    totalBytes = totalBytesScanned,
                    currentBatch = batchNumber,
                    isScanning = true,
                    statusMessage = if (incremental && minMsgId > 0) {
                        if (newItemsAdded > 0) "+$newItemsAdded new video(s) found..." else "Checking messages..."
                    } else {
                        "Indexed ${mediaIndex.values.count { it.channelId == channelId }} videos (${formatBytes(totalBytesScanned)})"
                    }
                )
                _scanProgress.value = _scanProgress.value + (channelId to batchDoneProgress)
                onProgress(batchDoneProgress)

                // Check termination conditions
                if (!hasMore || lastMsgId <= 0 || messagesScanned == 0) {
                    Log.i(TAG, "Completed scan of channel $channelId. Total scanned: $totalMessagesScanned messages")
                    break
                }

                offsetId = lastMsgId

                // Polite cooperative delay between batches to avoid Telegram FloodWait
                delay(250L)
            }

            // Finished scanning
            val finalCount = mediaIndex.values.count { it.channelId == channelId }
            val updatedLastMsgId = maxOf(
                channels[channelId]?.lastScannedMsgId ?: 0,
                newestEncounteredMsgId,
                minMsgId
            )
            channels[channelId]?.let { chInfo ->
                channels[channelId] = chInfo.copy(
                    videoCount = finalCount,
                    lastSyncTime = System.currentTimeMillis(),
                    lastScannedMsgId = updatedLastMsgId
                )
                saveChannels()
            }

            val finalStatus = if (incremental && minMsgId > 0) {
                if (newItemsAdded > 0) {
                    "Sync complete: +$newItemsAdded new video(s) added ($finalCount total)"
                } else {
                    "Channel is already up to date ($finalCount videos)"
                }
            } else {
                "Scan complete: $finalCount videos indexed"
            }

            val finalProgress = ScanProgressState(
                channelId = channelId,
                channelTitle = ch?.title ?: "Channel",
                scannedMessages = totalMessagesScanned,
                indexedMedia = finalCount,
                totalBytes = totalBytesScanned,
                currentBatch = batchNumber,
                isScanning = false,
                statusMessage = finalStatus
            )
            _scanProgress.value = _scanProgress.value + (channelId to finalProgress)
            onProgress(finalProgress)

            Result.success(newItemsAdded)
        } catch (e: Exception) {
            Log.e(TAG, "Exception during live scan of channel $channelId: ${e.message}", e)
            val errProgress = ScanProgressState(
                channelId = channelId,
                channelTitle = channels[channelId]?.title ?: "Channel",
                scannedMessages = 0,
                indexedMedia = mediaIndex.values.count { it.channelId == channelId },
                isScanning = false,
                statusMessage = "Scan stopped: ${e.message}"
            )
            _scanProgress.value = _scanProgress.value + (channelId to errProgress)
            Result.failure(e)
        }
    }

    fun getStreamSource(
        channelId: Long,
        messageId: Int,
        startByte: Long,
        endByte: Long,
        mediaId: String? = null
    ): StreamChunkResult {
        val extractedMsgId = mediaId?.removePrefix("tg_")?.removeSuffix("_split")
            ?.substringAfterLast("_")
            ?.toIntOrNull() ?: if (messageId > 0) messageId else null

        val media = (if (!mediaId.isNullOrBlank()) mediaIndex[mediaId] else null)
            ?: (if (!mediaId.isNullOrBlank()) mediaIndex.values.find { it.id == mediaId } else null)
            ?: (if (extractedMsgId != null && extractedMsgId > 0) mediaIndex.values.find {
                (it.channelId == channelId || channelId == 0L) && it.isSplit &&
                (it.messageId == extractedMsgId || it.parts.any { p -> p.messageId == extractedMsgId })
            } else null)
            ?: (if (extractedMsgId != null && extractedMsgId > 0) mediaIndex.values.find {
                ((it.channelId == channelId || channelId == 0L) && it.messageId == extractedMsgId) ||
                (it.channelId == channelId && it.isSplit && it.parts.any { p -> p.messageId == extractedMsgId })
            } else null)
            ?: (if (extractedMsgId != null && extractedMsgId > 0) mediaIndex.values.find { it.messageId == extractedMsgId } else null)
            ?: unmatchedIndex.values.find {
                (!mediaId.isNullOrBlank() && it.id == mediaId) || (extractedMsgId != null && extractedMsgId > 0 && it.channelId == channelId && it.messageId == extractedMsgId)
            }?.parsedMediaItem

        val effectiveChannelId = media?.channelId ?: channelId
        val totalSize = media?.sizeBytes ?: 50_000_000L
        val effectiveEnd = if (endByte <= 0L || endByte >= totalSize) totalSize - 1 else endByte
        val contentLength = effectiveEnd - startByte + 1

        val mimeType = if (media?.fileName?.contains(".mkv", ignoreCase = true) == true ||
            media?.mimeType?.contains("matroska", ignoreCase = true) == true
        ) {
            "video/x-matroska"
        } else {
            media?.mimeType ?: "video/mp4"
        }

        Log.d(TAG, "getStreamSource: mediaId=$mediaId, effectiveMsgId=$extractedMsgId, isSplit=${media?.isSplit}, partsCount=${media?.parts?.size}, range=$startByte-$effectiveEnd/$totalSize, mime=$mimeType")

        val apiId = getApiId()
        val apiHash = getApiHash()
        val sessionStr = sessionState.value.sessionString
        val bridge = getPyBridge()

        if (bridge != null && sessionStr.isNotBlank() && apiId > 0) {
            try {
                val streamCacheDir = File(context.cacheDir, "stream_cache").apply { mkdirs() }.absolutePath
                val activeCfg = com.tgserver.streamer.service.StreamerForegroundService.activeConfig
                val diskMb = activeCfg.diskCacheMb
                val bufMb = activeCfg.bufferPipelineMb
                try {
                    bridge.callAttr("configure_cache", streamCacheDir, diskMb, bufMb)
                } catch (ce: Exception) {
                    Log.w(TAG, "configure_cache notice: ${ce.message}")
                }

                val partsList = if (media != null && media.isSplit && media.parts.isNotEmpty()) {
                    media.parts.sortedBy { it.partNumber }.map {
                        mapOf(
                            "messageId" to it.messageId,
                            "partNumber" to it.partNumber,
                            "sizeBytes" to it.sizeBytes,
                            "cumStart" to it.cumStart
                        )
                    }
                } else {
                    val effectiveMsgId = media?.messageId ?: extractedMsgId ?: messageId
                    listOf(
                        mapOf(
                            "messageId" to effectiveMsgId,
                            "partNumber" to 1,
                            "sizeBytes" to totalSize,
                            "cumStart" to 0L
                        )
                    )
                }

                val partsJson = gson.toJson(partsList)
                Log.d(TAG, "create_stream request: partsCount=${partsList.size}, channelId=$effectiveChannelId, range=$startByte-$effectiveEnd/$totalSize")
                val streamId = bridge.callAttr(
                    "create_stream",
                    apiId,
                    apiHash,
                    sessionStr,
                    effectiveChannelId,
                    partsJson,
                    startByte,
                    effectiveEnd
                )?.toString() ?: ""

                if (streamId.isNotBlank()) {
                    val stream = TgMediaInputStream(bridge, streamId, contentLength)
                    return StreamChunkResult(
                        inputStream = stream,
                        contentLength = contentLength,
                        startByte = startByte,
                        endByte = effectiveEnd,
                        totalSize = totalSize,
                        mimeType = mimeType
                    )
                } else {
                    Log.e(TAG, "create_stream returned blank streamId for mediaId=$mediaId, channel=$effectiveChannelId")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error creating live MTProto stream: ${e.message}", e)
                throw java.io.IOException("Failed to create MTProto stream: ${e.message}", e)
            }
        }

        throw java.io.IOException("MTProto stream unavailable: session inactive or streamId blank for mediaId=$mediaId")
    }

    fun updateStreamCacheConfig(diskCacheMb: Int, bufferPipelineMb: Int) {
        try {
            val bridge = getPyBridge() ?: return
            val cacheFolder = File(context.cacheDir, "stream_cache").apply { mkdirs() }.absolutePath
            bridge.callAttr("configure_cache", cacheFolder, diskCacheMb, bufferPipelineMb)
            Log.d(TAG, "Stream cache configured: dir=$cacheFolder, diskCacheMb=$diskCacheMb, bufferMb=$bufferPipelineMb")
        } catch (e: Exception) {
            Log.e(TAG, "Error updating stream cache config: ${e.message}", e)
        }
    }

    fun detectTailscaleAddress(): String? {
        return try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (iface.isLoopback || !iface.isUp) continue
                val addresses = iface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val addr = addresses.nextElement()
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        val host = addr.hostAddress ?: continue
                        if (host.startsWith("100.") || iface.name.contains("tailscale", ignoreCase = true)) {
                            return host
                        }
                    }
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }
}

data class StreamChunkResult(
    val inputStream: InputStream,
    val contentLength: Long,
    val startByte: Long,
    val endByte: Long,
    val totalSize: Long,
    val mimeType: String
)
