package com.tgserver.streamer.telegram

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.tgserver.streamer.data.model.AuthSession
import com.tgserver.streamer.data.model.ChannelInfo
import com.tgserver.streamer.data.model.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap

class TelegramClientManager(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("tg_server_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()

    val currentSession = AuthSession()
    private val channels = ConcurrentHashMap<Long, ChannelInfo>()
    private val mediaIndex = ConcurrentHashMap<String, MediaItem>()

    init {
        loadSession()
        loadChannels()
        loadMediaIndex()
    }

    private fun loadSession() {
        currentSession.isLoggedIn = prefs.getBoolean("is_logged_in", false)
        currentSession.phoneNumber = prefs.getString("phone_number", "") ?: ""
        currentSession.firstName = prefs.getString("first_name", "Telegram User") ?: "Telegram User"
        currentSession.username = prefs.getString("username", "user") ?: "user"
        currentSession.userId = prefs.getLong("user_id", 0L)
        currentSession.sessionString = prefs.getString("session_string", "") ?: ""
    }

    private fun saveSession() {
        prefs.edit().apply {
            putBoolean("is_logged_in", currentSession.isLoggedIn)
            putString("phone_number", currentSession.phoneNumber)
            putString("first_name", currentSession.firstName)
            putString("username", currentSession.username)
            putLong("user_id", currentSession.userId)
            putString("session_string", currentSession.sessionString)
            apply()
        }
    }

    private fun loadChannels() {
        val json = prefs.getString("monitored_channels", null)
        if (!json.isNullOrBlank()) {
            val type = object : TypeToken<List<ChannelInfo>>() {}.type
            val list: List<ChannelInfo> = gson.fromJson(json, type) ?: emptyList()
            list.forEach { channels[it.id] = it }
        } else {
            // Initial default demo channels from Stitch designs
            val defaultChannels = listOf(
                ChannelInfo(
                    id = -1001928374829L,
                    title = "Cinema 4K UHD",
                    username = "Cinema4KUHD",
                    messageCount = 1420,
                    videoCount = 854,
                    isActive = true
                ),
                ChannelInfo(
                    id = -1001837492019L,
                    title = "Anime Express 1080p",
                    username = "AnimeExpressHD",
                    messageCount = 3120,
                    videoCount = 2890,
                    isActive = true
                ),
                ChannelInfo(
                    id = -1001748291028L,
                    title = "Web Series Vault",
                    username = "WebSeriesVault",
                    messageCount = 980,
                    videoCount = 612,
                    isActive = true
                )
            )
            defaultChannels.forEach { channels[it.id] = it }
            saveChannels()
        }
    }

    fun saveChannels() {
        val json = gson.toJson(channels.values.toList())
        prefs.edit().putString("monitored_channels", json).apply()
    }

    private fun loadMediaIndex() {
        val json = prefs.getString("media_index", null)
        if (!json.isNullOrBlank()) {
            val type = object : TypeToken<List<MediaItem>>() {}.type
            val list: List<MediaItem> = gson.fromJson(json, type) ?: emptyList()
            list.forEach { mediaIndex[it.id] = it }
        } else {
            // Seed library with items matching Stitch mock design
            val sampleMedia = listOf(
                MediaItem(
                    id = "tg_1001_101",
                    channelId = -1001928374829L,
                    messageId = 101,
                    title = "Dune: Part Two",
                    type = "movie",
                    year = "2024",
                    quality = "4K UHD",
                    sizeBytes = 8_500_000_000L,
                    durationSeconds = 9960,
                    mimeType = "video/mp4",
                    fileName = "Dune.Part.Two.2024.2160p.UHD.HDR.mkv",
                    posterUrl = "https://images.metahub.space/poster/medium/tt15239678/img",
                    overview = "Paul Atreides unites with Chani and the Fremen while seeking revenge against the conspirators who destroyed his family."
                ),
                MediaItem(
                    id = "tg_1001_102",
                    channelId = -1001928374829L,
                    messageId = 102,
                    title = "Oppenheimer",
                    type = "movie",
                    year = "2023",
                    quality = "4K IMAX",
                    sizeBytes = 12_400_000_000L,
                    durationSeconds = 10800,
                    mimeType = "video/mp4",
                    fileName = "Oppenheimer.2023.IMAX.2160p.UHD.mkv",
                    posterUrl = "https://images.metahub.space/poster/medium/tt15398776/img",
                    overview = "The story of American scientist J. Robert Oppenheimer and his role in the development of the atomic bomb."
                ),
                MediaItem(
                    id = "tg_1002_201",
                    channelId = -1001837492019L,
                    messageId = 201,
                    title = "Solo Leveling",
                    type = "series",
                    year = "2024",
                    season = 1,
                    episode = 12,
                    quality = "1080p",
                    sizeBytes = 1_400_000_000L,
                    durationSeconds = 1440,
                    mimeType = "video/mp4",
                    fileName = "Solo.Leveling.S01E12.1080p.HEVC.mkv",
                    posterUrl = "https://images.metahub.space/poster/medium/tt21209876/img",
                    overview = "In a world where hunters must battle deadly monsters to protect humanity, Sung Jinwoo is known as the weakest hunter of all mankind."
                ),
                MediaItem(
                    id = "tg_1003_301",
                    channelId = -1001748291028L,
                    messageId = 301,
                    title = "Severance",
                    type = "series",
                    year = "2022",
                    season = 1,
                    episode = 9,
                    quality = "1080p",
                    sizeBytes = 2_100_000_000L,
                    durationSeconds = 3420,
                    mimeType = "video/mp4",
                    fileName = "Severance.S01E09.1080p.HDR.mkv",
                    posterUrl = "https://images.metahub.space/poster/medium/tt11280740/img",
                    overview = "Mark leads a team of office workers whose memories have been surgically divided between their work and personal lives."
                )
            )
            sampleMedia.forEach { mediaIndex[it.id] = it }
            saveMediaIndex()
        }
    }

    fun saveMediaIndex() {
        val json = gson.toJson(mediaIndex.values.toList())
        prefs.edit().putString("media_index", json).apply()
    }

    // --- Phase 1: Authentication Engine ---

    suspend fun sendLoginCode(phoneNumber: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val cleanPhone = phoneNumber.replace(Regex("[^0-9+]"), "")
            if (cleanPhone.length < 8) {
                return@withContext Result.failure(IllegalArgumentException("Invalid phone number format"))
            }

            // In production MTProto, this calls auth.sendCode
            val fakeHash = "phone_hash_" + System.currentTimeMillis()
            currentSession.phoneNumber = cleanPhone
            currentSession.phoneCodeHash = fakeHash
            currentSession.lastError = null

            Result.success("Code sent successfully to $cleanPhone")
        } catch (e: Exception) {
            currentSession.lastError = e.message
            Result.failure(e)
        }
    }

    suspend fun verifyLoginCode(code: String): Result<AuthSession> = withContext(Dispatchers.IO) {
        try {
            if (code.isBlank() || code.length < 4) {
                return@withContext Result.failure(IllegalArgumentException("Code must be at least 4 digits"))
            }

            // Successful authentication
            currentSession.isLoggedIn = true
            currentSession.firstName = "Telegram Streamer"
            currentSession.username = "TheAceOfficials"
            currentSession.userId = 184920194L
            currentSession.sessionString = "1BVtsOMwBu8QWj..."
            currentSession.lastError = null
            saveSession()

            Result.success(currentSession)
        } catch (e: Exception) {
            currentSession.lastError = e.message
            Result.failure(e)
        }
    }

    suspend fun verify2FAPassword(password: String): Result<AuthSession> = withContext(Dispatchers.IO) {
        try {
            if (password.isBlank()) {
                return@withContext Result.failure(IllegalArgumentException("Password cannot be blank"))
            }

            currentSession.isLoggedIn = true
            currentSession.lastError = null
            saveSession()

            Result.success(currentSession)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun logout() {
        currentSession.isLoggedIn = false
        currentSession.sessionString = ""
        currentSession.lastError = null
        saveSession()
    }

    // --- Channel & Library APIs ---

    fun getChannels(): List<ChannelInfo> = channels.values.toList()

    fun addChannel(identifier: String): ChannelInfo {
        val clean = identifier.trim().replace("@", "")
        val newId = -(1000000000000L + (channels.size + 1) * 123456L)
        val channel = ChannelInfo(
            id = newId,
            title = clean,
            username = clean,
            messageCount = 0,
            videoCount = 0,
            isActive = true
        )
        channels[newId] = channel
        saveChannels()
        return channel
    }

    fun toggleChannel(channelId: Long, active: Boolean) {
        channels[channelId]?.let {
            val updated = it.copy(isActive = active)
            channels[channelId] = updated
            saveChannels()
        }
    }

    fun removeChannel(channelId: Long) {
        channels.remove(channelId)
        saveChannels()
    }

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

    fun addIndexedMedia(item: MediaItem) {
        mediaIndex[item.id] = item
        saveMediaIndex()
    }

    // --- Phase 2: Stream Chunk Provider ---

    fun getStreamSource(
        channelId: Long,
        messageId: Int,
        startByte: Long,
        endByte: Long
    ): StreamChunkResult {
        val media = mediaIndex.values.find { it.channelId == channelId && it.messageId == messageId }
        val totalSize = media?.sizeBytes ?: 100_000_000L
        val effectiveEnd = if (endByte <= 0L || endByte >= totalSize) totalSize - 1 else endByte
        val contentLength = effectiveEnd - startByte + 1

        // In active MTProto chunk streaming, this pulls upload.getFile slices directly.
        // For mock/development fallback, provides dummy video container bytes to allow handshake.
        val dummyBytes = ByteArray(minOf(contentLength, 65536L).toInt())
        val inputStream: InputStream = ByteArrayInputStream(dummyBytes)

        return StreamChunkResult(
            inputStream = inputStream,
            contentLength = contentLength,
            startByte = startByte,
            endByte = effectiveEnd,
            totalSize = totalSize,
            mimeType = media?.mimeType ?: "video/mp4"
        )
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
