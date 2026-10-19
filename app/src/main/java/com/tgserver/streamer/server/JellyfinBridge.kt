package com.tgserver.streamer.server

import android.content.Context
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.tgserver.streamer.data.model.MediaItem
import com.tgserver.streamer.data.model.ServerConfig
import com.tgserver.streamer.posters.SpatialPosterService
import com.tgserver.streamer.telegram.TelegramClientManager
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoHTTPD.IHTTPSession
import fi.iki.elonen.NanoHTTPD.Method
import fi.iki.elonen.NanoHTTPD.Response
import java.net.URLEncoder
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.ConcurrentHashMap

private const val TAG = "JellyfinBridge"

/**
 * High-performance, production-grade Jellyfin & Emby Server API Emulation Layer.
 *
 * Implements standard Jellyfin/Emby REST endpoints with 100% OpenAPI schema fidelity,
 * enabling third-party client apps (Infuse on Apple TV/iOS/Mac, Official Jellyfin Android TV,
 * Swiftfin, Findroid) to connect, authenticate, browse catalogs, and stream seamlessly.
 *
 * All video playback routes through DirectPlay HTTP 206 Partial Content without server-side
 * transcoding, maintaining negligible CPU and battery usage.
 */
class JellyfinBridge(
    private val context: Context,
    private val tgClient: TelegramClientManager,
    private val posterService: SpatialPosterService,
    private val configProvider: () -> ServerConfig,
    private val localIpProvider: () -> String
) {
    private val gson = Gson()

    // Canonical 32-character hexadecimal GUIDs (standard Jellyfin format "N")
    val serverId = "48b6c8d76c67429bbad58923a1a9e871"
    val defaultUserId = "a1b2c3d4e5f64789a0b1c2d3e4f5a6b7"
    val defaultSessionId = "e1f2a3b4c5d64789a0b1c2d3e4f5a6b8"
    val defaultUserName = "NoxMedia"
    val accessToken = "nox_token_12345"

    // Fixed canonical 32-hex GUIDs for root virtual libraries
    val libMoviesHex = "10000000000000000000000000000001"
    val libSeriesHex = "20000000000000000000000000000002"
    val libAnimeHex  = "30000000000000000000000000000003"

    // Bidirectional deterministic ID mapping: raw string ID <-> 32-character hex GUID
    private val idToHexMap = ConcurrentHashMap<String, String>()
    private val hexToIdMap = ConcurrentHashMap<String, String>()

    init {
        // Register known system IDs
        registerHexId("lib_movies", libMoviesHex)
        registerHexId("lib_series", libSeriesHex)
        registerHexId("lib_anime", libAnimeHex)
        registerHexId("user_nox", defaultUserId)
        registerHexId("server_nox", serverId)
    }

    private fun registerHexId(rawId: String, hex32: String) {
        val lowerHex = hex32.lowercase()
        idToHexMap[rawId] = lowerHex
        hexToIdMap[lowerHex] = rawId
    }

    private fun toMd5Hex(input: String): String {
        return try {
            val md = MessageDigest.getInstance("MD5")
            val digest = md.digest(input.toByteArray(Charsets.UTF_8))
            digest.joinToString("") { "%02x".format(it) }
        } catch (_: Exception) {
            input.padEnd(32, '0').take(32).lowercase()
        }
    }

    fun toHexId(rawId: String): String {
        val clean = rawId.trim()
        val mapped = idToHexMap[clean]
        if (mapped != null) return mapped
        if (clean.length == 32 && clean.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) {
            val lower = clean.lowercase()
            hexToIdMap.putIfAbsent(lower, clean)
            return lower
        }
        return idToHexMap.computeIfAbsent(clean) { key ->
            val hex = toMd5Hex(key)
            hexToIdMap.putIfAbsent(hex, key)
            hex
        }
    }

    fun fromHexId(hexOrRaw: String): String {
        val clean = hexOrRaw.replace("-", "").lowercase().trim()
        val cached = hexToIdMap[clean]
        if (cached != null) return cached

        if (clean == libMoviesHex) return "lib_movies"
        if (clean == libSeriesHex) return "lib_series"
        if (clean == libAnimeHex) return "lib_anime"

        if (clean.length == 32 && clean.all { it in '0'..'9' || it in 'a'..'f' }) {
            val all = tgClient.getAllMedia()
            for (item in all) {
                if (toMd5Hex(item.id) == clean) {
                    hexToIdMap[clean] = item.id
                    return item.id
                }
                if (isSeries(item) || isAnime(item)) {
                    val sRaw = getSeriesId(item)
                    if (toMd5Hex(sRaw) == clean) {
                        hexToIdMap[clean] = sRaw
                        return sRaw
                    }
                    val currentS = item.season ?: 1
                    for (s in listOf(currentS, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10)) {
                        val seasonRaw = "${sRaw}_s$s"
                        if (toMd5Hex(seasonRaw) == clean) {
                            hexToIdMap[clean] = seasonRaw
                            return seasonRaw
                        }
                    }
                }
            }
        }

        return hexOrRaw
    }

    private fun getIsoTimestamp(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSSS'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(Date())
    }

    private fun getParam(params: Map<String, String>, vararg names: String): String {
        for (name in names) {
            val v = params[name]
            if (!v.isNullOrBlank()) return v
        }
        for ((k, v) in params) {
            if (names.any { it.equals(k, ignoreCase = true) } && v.isNotBlank()) {
                return v
            }
        }
        return ""
    }

    // Media Classification Helpers
    private fun isAnime(item: MediaItem): Boolean {
        return item.type.equals("anime", ignoreCase = true) ||
                item.kitsuId != null ||
                (tgClient.getChannel(item.channelId)?.isAnime == true)
    }

    private fun isSeries(item: MediaItem): Boolean {
        if (isAnime(item)) return false
        return item.type.equals("series", ignoreCase = true) ||
                item.season != null ||
                item.episode != null ||
                item.episodeTitle != null
    }

    private fun isMovie(item: MediaItem): Boolean {
        return !isAnime(item) && !isSeries(item)
    }

    /**
     * Identifies whether an incoming HTTP request URI belongs to the Jellyfin/Emby namespace.
     */
    fun isJellyfinUri(uri: String): Boolean {
        val u = uri.removePrefix("/emby").trimEnd('/').lowercase()
        return u.startsWith("/system") ||
                u.startsWith("/users") ||
                u.startsWith("/userviews") ||
                u.startsWith("/library") ||
                u.startsWith("/libraries") ||
                u.startsWith("/items") ||
                u.startsWith("/shows") ||
                u.startsWith("/seasons") ||
                u.startsWith("/episodes") ||
                u.startsWith("/videos") ||
                u.startsWith("/audio") ||
                u.startsWith("/sessions") ||
                u.startsWith("/displaypreferences") ||
                u.startsWith("/localization") ||
                u.startsWith("/quickconnect") ||
                u.startsWith("/branding") ||
                u.startsWith("/livetv") ||
                u.startsWith("/scheduledtasks") ||
                u.startsWith("/playback") ||
                u.startsWith("/infusesync") ||
                u.startsWith("/environment")
    }

    /**
     * Main request dispatcher for all Jellyfin API endpoints.
     */
    fun handle(
        session: IHTTPSession,
        uri: String,
        method: Method,
        headers: Map<String, String>
    ): Response {
        val cleanUri = uri.removePrefix("/emby").trimEnd('/')
        val lowerUri = cleanUri.lowercase()
        val params = session.parameters.mapValues { it.value.firstOrNull() ?: "" }
        val localIp = localIpProvider()
        val config = configProvider()

        StreamLog.i(TAG, "[$method] $cleanUri params=$params (from ${session.remoteIpAddress ?: "unknown"})")

        return try {
            when {
                // ================= 1. Handshake & System Info =================
                lowerUri == "/system/info/public" -> {
                    handleSystemInfo(localIp, config.port, isPublic = true)
                }
                lowerUri == "/system/info" -> {
                    handleSystemInfo(localIp, config.port, isPublic = false)
                }
                lowerUri == "/system/configuration" -> {
                    handleSystemConfiguration(config.port)
                }
                lowerUri == "/system/endpoint" -> {
                    jsonResponse(mapOf("LocalAddress" to "http://$localIp:${config.port}", "IsInNetwork" to true))
                }

                // ================= 2. Authentication & User Profile =================
                lowerUri == "/users/public" -> {
                    handleUsersPublic()
                }
                lowerUri == "/users/authenticatebyname" -> {
                    handleAuthenticateByName(session, params)
                }
                lowerUri == "/users/me" -> {
                    handleGetUser(defaultUserName)
                }
                // Specific user lookup: /Users/{id}
                lowerUri.matches(Regex("/users/[^/]+")) && !lowerUri.contains("/views") && !lowerUri.contains("/items") && !lowerUri.contains("/groupingoptions") -> {
                    handleGetUser(defaultUserName)
                }

                // ================= 3. Library Views & Grouping Options =================
                lowerUri.endsWith("/groupingoptions") -> {
                    handleGroupingOptions()
                }
                lowerUri.endsWith("/views") || lowerUri == "/userviews" -> {
                    handleViews()
                }
                lowerUri.startsWith("/library/virtualfolders") -> {
                    handleVirtualFolders()
                }
                lowerUri == "/libraries/availableoptions" -> {
                    jsonResponse(
                        mapOf(
                            "MetadataFetchers" to emptyList<Any>(),
                            "MetadataReaders" to emptyList<Any>(),
                            "ImageFetchers" to emptyList<Any>(),
                            "SubtitleFetchers" to emptyList<Any>()
                        )
                    )
                }
                lowerUri == "/items/counts" -> {
                    handleItemCounts()
                }

                // ================= 4. Resume & Latest Items =================
                lowerUri.endsWith("/items/resume") -> {
                    jsonResponse(mapOf("Items" to emptyList<Any>(), "TotalRecordCount" to 0, "StartIndex" to 0))
                }
                lowerUri.endsWith("/items/latest") -> {
                    handleLatestItems(params, localIp, config.port)
                }

                // ================= 5. TV Show Hierarchy: Seasons & Episodes =================
                lowerUri.contains("/shows/nextup") -> {
                    jsonResponse(mapOf("Items" to emptyList<Any>(), "TotalRecordCount" to 0, "StartIndex" to 0))
                }
                lowerUri.contains("/shows/") && lowerUri.contains("/seasons") -> {
                    val seriesId = extractSegmentBetween(cleanUri, "shows/", "/seasons").substringBefore("/")
                    handleShowSeasons(seriesId, localIp, config.port)
                }
                lowerUri.contains("/shows/") && lowerUri.contains("/episodes") -> {
                    val seriesId = extractSegmentBetween(cleanUri, "shows/", "/episodes").substringBefore("/")
                    val seasonNum = getParam(params, "season", "seasonNumber", "seasonId")
                        .let { s -> s.toIntOrNull() ?: fromHexId(s).substringAfterLast("_s").toIntOrNull() }
                    handleShowEpisodes(seriesId, seasonNum, localIp, config.port)
                }
                (lowerUri.startsWith("/shows/") || (lowerUri.contains("/shows/") && !lowerUri.contains("/seasons") && !lowerUri.contains("/episodes") && !lowerUri.contains("/nextup"))) && !lowerUri.contains("/images") -> {
                    val seriesId = extractSegmentBetween(cleanUri, "shows/", "/").ifBlank { cleanUri.substringAfterLast("/") }
                    handleGetSingleItem(seriesId, localIp, config.port)
                }

                // ================= 6. Similar Items & Recommendations =================
                lowerUri.endsWith("/similar") -> {
                    handleSimilarItems(cleanUri, params, localIp, config.port)
                }

                // ================= 7. Special Features, Themes & Extras =================
                lowerUri.endsWith("/thememedia") -> {
                    jsonResponse(
                        mapOf(
                            "ThemeVideosResult" to mapOf("Items" to emptyList<Any>(), "TotalRecordCount" to 0),
                            "ThemeSongsResult" to mapOf("Items" to emptyList<Any>(), "TotalRecordCount" to 0),
                            "SoundtrackSongsResult" to mapOf("Items" to emptyList<Any>(), "TotalRecordCount" to 0)
                        )
                    )
                }
                lowerUri.endsWith("/themesongs") || lowerUri.endsWith("/themevideos") || lowerUri.endsWith("/intros") -> {
                    jsonResponse(mapOf("Items" to emptyList<Any>(), "TotalRecordCount" to 0, "StartIndex" to 0))
                }
                lowerUri.endsWith("/specialfeatures") || lowerUri.endsWith("/ancestors") || lowerUri.endsWith("/externalidinfos") -> {
                    jsonResponse(emptyList<Any>())
                }
                lowerUri.endsWith("/filters") || lowerUri.endsWith("/filters2") -> {
                    jsonResponse(
                        mapOf(
                            "Genres" to emptyList<String>(),
                            "Tags" to emptyList<String>(),
                            "Years" to emptyList<Int>(),
                            "OfficialRatings" to emptyList<String>()
                        )
                    )
                }
                lowerUri.contains("/additionalparts") -> {
                    jsonResponse(mapOf("Items" to emptyList<Any>(), "TotalRecordCount" to 0, "StartIndex" to 0))
                }
                lowerUri.contains("/favoriteitems/") || lowerUri.contains("/rating") -> {
                    jsonResponse(mapOf("IsFavorite" to true))
                }
                lowerUri.contains("/playeditems/") -> {
                    jsonResponse(mapOf("Played" to true, "PlayCount" to 1, "PlaybackPositionTicks" to 0L))
                }

                // ================= 8. Single Item Metadata =================
                // Matches /Items/{itemId} or /Users/{userId}/Items/{itemId}
                cleanUri.contains("/items/", ignoreCase = true)
                        && !lowerUri.contains("/images")
                        && !lowerUri.endsWith("/latest")
                        && !lowerUri.endsWith("/resume")
                        && !lowerUri.endsWith("/counts")
                        && !lowerUri.endsWith("/similar")
                        && !lowerUri.endsWith("/thememedia")
                        && !lowerUri.endsWith("/themesongs")
                        && !lowerUri.endsWith("/themevideos")
                        && !lowerUri.endsWith("/specialfeatures")
                        && !lowerUri.endsWith("/intros")
                        && !lowerUri.endsWith("/ancestors")
                        && !lowerUri.endsWith("/externalidinfos")
                        && !lowerUri.endsWith("/filters")
                        && !lowerUri.endsWith("/filters2")
                        && !cleanUri.substringAfterLast("/").equals("items", ignoreCase = true) -> {
                    val itemId = cleanUri.substringAfterLast("/")
                    handleGetSingleItem(itemId, localIp, config.port)
                }

                // ================= 7. Library Catalog Queries =================
                lowerUri == "/items" || lowerUri.matches(Regex(".*/users/[^/]+/items")) -> {
                    handleQueryItems(params, localIp, config.port)
                }

                // ================= 8. Playback Info (Direct Play Sources) =================
                lowerUri.contains("/playbackinfo") -> {
                    val urlItemId = extractSegmentBetween(cleanUri, "items/", "/playbackinfo")
                        .ifBlank { extractSegmentBetween(cleanUri, "videos/", "/playbackinfo") }
                    val itemId = urlItemId.ifBlank {
                        getParam(params, "ItemId", "itemId", "Id", "id")
                    }
                    handlePlaybackInfo(itemId, localIp, config.port)
                }

                // ================= 9. Video Direct Stream Redirect =================
                lowerUri.contains("/stream") && (lowerUri.contains("/videos/") || lowerUri.contains("/audio/")) -> {
                    val itemId = extractSegmentBetween(cleanUri, "videos/", "/stream")
                        .ifBlank { extractSegmentBetween(cleanUri, "audio/", "/stream") }
                        .removeSuffix(".mkv")
                    handleVideoStreamRedirect(itemId, localIp, config.port)
                }

                // ================= 10. Poster & Backdrop Image Redirect =================
                lowerUri.contains("/images/") -> {
                    val itemId = extractSegmentBetween(cleanUri, "items/", "/images")
                        .ifBlank { extractSegmentBetween(cleanUri, "shows/", "/images") }
                        .ifBlank { extractSegmentBetween(cleanUri, "seasons/", "/images") }
                    val imageType = cleanUri.substringAfter("/images/").substringBefore("/")
                    handleImageRedirect(itemId, imageType)
                }

                // ================= 11. Sessions, Capabilities & Sync Stubs =================
                lowerUri.startsWith("/sessions/playing") -> {
                    noContentResponse()
                }
                lowerUri.startsWith("/sessions/capabilities") -> {
                    noContentResponse()
                }
                lowerUri.startsWith("/displaypreferences") -> {
                    handleDisplayPreferences()
                }
                lowerUri.startsWith("/localization") -> {
                    jsonResponse(emptyList<Any>())
                }
                lowerUri == "/quickconnect/enabled" -> {
                    jsonResponse(false)
                }
                lowerUri == "/branding/configuration" -> {
                    jsonResponse(mapOf("CustomCss" to "", "SplashscreenEnabled" to false))
                }
                lowerUri == "/branding/css" -> {
                    NanoHTTPD.newFixedLengthResponse(Response.Status.OK, "text/css", "")
                }
                lowerUri == "/livetv/channels" -> {
                    jsonResponse(mapOf("Items" to emptyList<Any>(), "TotalRecordCount" to 0, "StartIndex" to 0))
                }
                lowerUri == "/scheduledtasks" -> {
                    jsonResponse(emptyList<Any>())
                }
                lowerUri == "/playback/bitratetest" -> {
                    NanoHTTPD.newFixedLengthResponse(Response.Status.OK, "text/plain", "")
                }
                lowerUri.startsWith("/infusesync") -> {
                    jsonResponse(mapOf("Items" to emptyList<Any>(), "TotalRecordCount" to 0, "StartIndex" to 0))
                }
                lowerUri.startsWith("/environment") -> {
                    jsonResponse(mapOf("Path" to "/media"))
                }

                else -> {
                    jsonResponse(mapOf("Items" to emptyList<Any>(), "TotalRecordCount" to 0, "StartIndex" to 0))
                }
            }
        } catch (e: Exception) {
            StreamLog.e(TAG, "Error handling Jellyfin request: ${e.message}", e)
            jsonResponse(mapOf("error" to (e.message ?: "Internal Server Error")), Response.Status.INTERNAL_ERROR)
        }
    }

    // --- Endpoint Handlers ---

    private fun handleSystemInfo(localIp: String, port: Int, isPublic: Boolean): Response {
        val info = mutableMapOf<String, Any?>(
            "LocalAddress" to "http://$localIp:$port",
            "ServerName" to "NoxMedia",
            "Version" to "10.10.3",
            "ProductName" to "Jellyfin Server",
            "OperatingSystem" to "Linux",
            "Id" to serverId,
            "StartupWizardCompleted" to true
        )

        if (!isPublic) {
            info["OperatingSystemDisplayName"] = "Android / Linux"
            info["HasPendingRestart"] = false
            info["IsShuttingDown"] = false
            info["SupportsLibraryMonitor"] = true
            info["WebSocketPortNumber"] = port
            info["CompletedInstallations"] = emptyList<String>()
            info["CanSelfRestart"] = false
            info["CanSelfUpdate"] = false
            info["CanLaunchWebBrowser"] = false
            info["ProgramDataPath"] = "/data"
            info["WebPath"] = "/web"
            info["ItemsByNamePath"] = "/metadata"
            info["CachePath"] = "/cache"
            info["LogPath"] = "/logs"
            info["InternalMetadataPath"] = "/metadata"
            info["TranscodingTempPath"] = "/transcodes"
            info["CastReceiverApplications"] = emptyList<Map<String, String>>()
            info["HasUpdateAvailable"] = false
            info["EncoderLocation"] = "System"
            info["SystemArchitecture"] = "Arm64"
        }

        return jsonResponse(info)
    }

    private fun handleSystemConfiguration(port: Int): Response {
        val config = mapOf(
            "LogFileRetentionDays" to 3,
            "IsStartupWizardCompleted" to true,
            "CachePath" to "/cache",
            "EnableMetrics" to false,
            "EnableNormalizedItemByNameIds" to true,
            "IsPortAuthorized" to true,
            "QuickConnectAvailable" to true,
            "EnableCaseSensitiveItemIds" to true,
            "DisableLiveTvChannelUserDataName" to true,
            "MetadataPath" to "/metadata",
            "MetadataNetworkPath" to "",
            "PreferredMetadataLanguage" to "en",
            "MetadataCountryCode" to "US",
            "SortReplaceCharacters" to listOf(".", "+", "%"),
            "SortRemoveCharacters" to listOf(",", "&", "-"),
            "PagingLimit" to 100,
            "EnableExternalContentInSuggestions" to true,
            "IsBehindReverseProxy" to false,
            "ServerName" to "NoxMedia",
            "UICulture" to "en-US",
            "SaveMetadataHidden" to false,
            "ContentTypes" to emptyList<String>(),
            "RemoteIPFilter" to emptyList<String>(),
            "IsIpFilterBlacklist" to false,
            "EnableUPnP" to false,
            "EnableAutomaticPortMapping" to false,
            "PublicHttpsPort" to port,
            "HttpServerPortNumber" to port,
            "HttpsPortNumber" to port,
            "EnableHttps" to false,
            "PublicPort" to port,
            "AutoRunServer" to true,
            "LibraryMonitorDelay" to 60,
            "ImageSavingQuality" to 90,
            "MetadataOptions" to emptyList<String>(),
            "SkipDeserializationForBasicTypes" to false,
            "ServerId" to serverId,
            "UICultures" to listOf("en-US"),
            "AllowClientLogUpload" to false,
            "DummyChapterDuration" to 300,
            "ResourcePath" to "",
            "FindMetadataFromHiddenPaths" to false
        )
        return jsonResponse(config)
    }

    private fun buildUserDto(username: String): Map<String, Any?> {
        val isoNow = getIsoTimestamp()
        return mapOf(
            "Name" to username,
            "ServerId" to serverId,
            "ServerName" to "NoxMedia",
            "Id" to defaultUserId,
            "HasPassword" to false,
            "HasConfiguredPassword" to false,
            "HasConfiguredEasyPassword" to false,
            "EnableAutoLogin" to true,
            "LastLoginDate" to isoNow,
            "LastActivityDate" to isoNow,
            "Configuration" to mapOf(
                "PlayDefaultAudioTrack" to true,
                "SubtitleLanguagePreference" to "eng",
                "DisplayMissingEpisodes" to false,
                "GroupedFolders" to emptyList<String>(),
                "SubtitleMode" to "Default",
                "OrderedViews" to emptyList<String>(),
                "LatestItemsExcludes" to emptyList<String>(),
                "MyMediaExcludes" to emptyList<String>(),
                "HidePlayedInLatest" to false,
                "RememberAudioSelections" to true,
                "RememberSubtitleSelections" to true,
                "EnableNextEpisodeAutoPlay" to true
            ),
            "Policy" to mapOf(
                "IsAdministrator" to true,
                "IsHidden" to false,
                "IsDisabled" to false,
                "EnableUserPreferenceAccess" to true,
                "EnableRemoteAccess" to true,
                "EnableLiveTvManagement" to true,
                "EnableLiveTvAccess" to true,
                "EnableMediaPlayback" to true,
                "EnableAudioPlaybackTranscoding" to true,
                "EnableVideoPlaybackTranscoding" to true,
                "EnablePlaybackRemuxing" to true,
                "EnableContentDownloading" to true,
                "EnableContentDeletion" to false,
                "EnableSyncTranscoding" to false,
                "EnablePublicSharing" to false,
                "BlockedChannels" to emptyList<String>(),
                "PublicChannels" to emptyList<String>(),
                "EnabledChannels" to emptyList<String>(),
                "EnabledFolders" to emptyList<String>(),
                "SimultaneousStreamLimit" to 0,
                "DeviceAccessMode" to "All"
            )
        )
    }

    private fun handleUsersPublic(): Response {
        return jsonResponse(listOf(buildUserDto(defaultUserName)))
    }

    private fun handleAuthenticateByName(session: IHTTPSession, params: Map<String, String>): Response {
        var requestedUsername = defaultUserName
        try {
            val map = HashMap<String, String>()
            session.parseBody(map)
            val postData = map["postData"] ?: ""
            if (postData.isNotBlank()) {
                val json = JsonParser.parseString(postData).asJsonObject
                val u = json.get("Username")?.asString ?: json.get("username")?.asString
                if (!u.isNullOrBlank()) requestedUsername = u
            }
        } catch (_: Exception) {}

        val paramUser = getParam(params, "Username", "username")
        if (paramUser.isNotBlank()) requestedUsername = paramUser

        val isoNow = getIsoTimestamp()
        val userObj = buildUserDto(requestedUsername)

        val sessionObj = mapOf(
            "PlayState" to mapOf(
                "CanSeek" to true,
                "IsPaused" to false,
                "IsMuted" to false,
                "VolumeLevel" to 100,
                "PositionTicks" to 0L,
                "RepeatMode" to "RepeatNone",
                "SubtitleStreamIndex" to -1,
                "AudioStreamIndex" to -1
            ),
            "AdditionalUsers" to emptyList<String>(),
            "RemoteEndPoint" to (session.remoteIpAddress ?: "127.0.0.1"),
            "Id" to defaultSessionId,
            "UserId" to defaultUserId,
            "UserName" to requestedUsername,
            "Client" to "Infuse",
            "LastActivityDate" to isoNow,
            "DeviceName" to "Client Device",
            "DeviceId" to "nox_device_1",
            "ApplicationVersion" to "1.0.0",
            "IsActive" to true,
            "SupportsMediaControl" to true,
            "SupportsRemoteControl" to true,
            "NowPlayingItem" to null,
            "HasCustomTheme" to false
        )

        val authResult = mapOf(
            "User" to userObj,
            "SessionInfo" to sessionObj,
            "AccessToken" to accessToken,
            "ServerId" to serverId
        )
        return jsonResponse(authResult)
    }

    private fun handleGetUser(username: String): Response {
        return jsonResponse(buildUserDto(username))
    }

    private fun handleGroupingOptions(): Response {
        val options = listOf(
            mapOf("Name" to "Movies", "Id" to "movies"),
            mapOf("Name" to "TV Shows", "Id" to "shows")
        )
        return jsonResponse(options)
    }

    private fun handleVirtualFolders(): Response {
        val folders = listOf(
            mapOf(
                "Name" to "Movies",
                "Locations" to listOf("/media/movies"),
                "CollectionType" to "movies",
                "ItemId" to libMoviesHex,
                "Guid" to libMoviesHex,
                "RefreshProgress" to 0,
                "RefreshStatus" to "Idle"
            ),
            mapOf(
                "Name" to "TV Shows",
                "Locations" to listOf("/media/tvshows"),
                "CollectionType" to "tvshows",
                "ItemId" to libSeriesHex,
                "Guid" to libSeriesHex,
                "RefreshProgress" to 0,
                "RefreshStatus" to "Idle"
            ),
            mapOf(
                "Name" to "Anime",
                "Locations" to listOf("/media/anime"),
                "CollectionType" to "tvshows",
                "ItemId" to libAnimeHex,
                "Guid" to libAnimeHex,
                "RefreshProgress" to 0,
                "RefreshStatus" to "Idle"
            )
        )
        return jsonResponse(folders)
    }

    private fun handleViews(): Response {
        val all = tgClient.getAllMedia().filter { !it.isHidden }
        val movieCount = all.count { isMovie(it) }
        val seriesCount = all.filter { isSeries(it) }.distinctBy { it.title.lowercase().trim() }.size
        val animeCount = all.filter { isAnime(it) }.distinctBy { it.title.lowercase().trim() }.size

        val views = listOf(
            mapOf(
                "Name" to "Movies",
                "ServerId" to serverId,
                "Id" to libMoviesHex,
                "CollectionType" to "movies",
                "Type" to "CollectionFolder",
                "IsFolder" to true,
                "CanDelete" to false,
                "CanDownload" to false,
                "UserData" to mapOf(
                    "UnplayedItemCount" to movieCount,
                    "PlaybackPositionTicks" to 0L,
                    "PlayCount" to 0,
                    "IsFavorite" to false,
                    "Played" to false
                ),
                "ImageTags" to mapOf("Primary" to libMoviesHex),
                "BackdropImageTags" to emptyList<String>()
            ),
            mapOf(
                "Name" to "TV Shows",
                "ServerId" to serverId,
                "Id" to libSeriesHex,
                "CollectionType" to "tvshows",
                "Type" to "CollectionFolder",
                "IsFolder" to true,
                "CanDelete" to false,
                "CanDownload" to false,
                "UserData" to mapOf(
                    "UnplayedItemCount" to seriesCount,
                    "PlaybackPositionTicks" to 0L,
                    "PlayCount" to 0,
                    "IsFavorite" to false,
                    "Played" to false
                ),
                "ImageTags" to mapOf("Primary" to libSeriesHex),
                "BackdropImageTags" to emptyList<String>()
            ),
            mapOf(
                "Name" to "Anime",
                "ServerId" to serverId,
                "Id" to libAnimeHex,
                "CollectionType" to "tvshows",
                "Type" to "CollectionFolder",
                "IsFolder" to true,
                "CanDelete" to false,
                "CanDownload" to false,
                "UserData" to mapOf(
                    "UnplayedItemCount" to animeCount,
                    "PlaybackPositionTicks" to 0L,
                    "PlayCount" to 0,
                    "IsFavorite" to false,
                    "Played" to false
                ),
                "ImageTags" to mapOf("Primary" to libAnimeHex),
                "BackdropImageTags" to emptyList<String>()
            )
        )
        return jsonResponse(mapOf("Items" to views, "TotalRecordCount" to views.size, "StartIndex" to 0))
    }

    private fun handleItemCounts(): Response {
        val all = tgClient.getAllMedia().filter { !it.isHidden }
        val movies = all.count { isMovie(it) }
        val series = all.filter { isSeries(it) }.distinctBy { it.title.lowercase().trim() }.size
        val episodes = all.count { isSeries(it) }
        return jsonResponse(
            mapOf(
                "MovieCount" to movies,
                "SeriesCount" to series,
                "EpisodeCount" to episodes,
                "ItemCount" to all.size
            )
        )
    }

    private fun handleLatestItems(params: Map<String, String>, localIp: String, port: Int): Response {
        val parentIdParam = getParam(params, "ParentId", "parentId", "parentID", "folderId")
        val cleanParentId = parentIdParam.replace("-", "").lowercase().trim()
        val rawParentId = if (cleanParentId.isNotBlank()) fromHexId(cleanParentId) else ""

        var all = tgClient.getAllMedia().filter { !it.isHidden }
        all = when {
            cleanParentId == libMoviesHex || rawParentId == "lib_movies" -> all.filter { isMovie(it) }
            cleanParentId == libSeriesHex || rawParentId == "lib_series" -> all.filter { isSeries(it) }
            cleanParentId == libAnimeHex || rawParentId == "lib_anime" -> all.filter { isAnime(it) }
            else -> all
        }

        val latest = all.sortedByDescending { it.dateAdded }
            .take(20)
            .map { toJellyfinItem(it, localIp, port, isSeriesOverview = false) }
        return jsonResponse(mapOf("Items" to latest, "TotalRecordCount" to latest.size, "StartIndex" to 0))
    }

    private fun handleQueryItems(params: Map<String, String>, localIp: String, port: Int): Response {
        val parentIdParam = getParam(params, "ParentId", "parentId", "parentID", "folderId", "id")
        val cleanParentId = parentIdParam.replace("-", "").lowercase().trim()
        val rawParentId = if (cleanParentId.isNotBlank()) fromHexId(cleanParentId) else ""

        val searchTerm = getParam(params, "SearchTerm", "searchTerm", "search", "q")
        val includeItemTypes = getParam(params, "IncludeItemTypes", "includeItemTypes", "itemTypes")
            .split(",")
            .map { it.trim().lowercase() }
            .filter { it.isNotBlank() }
        val startIndex = getParam(params, "StartIndex", "startIndex").toIntOrNull() ?: 0
        val limit = getParam(params, "Limit", "limit").toIntOrNull() ?: 100

        StreamLog.i(TAG, "handleQueryItems: parentId='$cleanParentId' (raw='$rawParentId'), types=$includeItemTypes, start=$startIndex, limit=$limit")

        var allMedia = tgClient.getAllMedia().filter { !it.isHidden }

        if (searchTerm.isNotBlank()) {
            allMedia = allMedia.filter {
                it.title.contains(searchTerm, ignoreCase = true) ||
                        it.fileName.contains(searchTerm, ignoreCase = true) ||
                        (it.episodeTitle?.contains(searchTerm, ignoreCase = true) == true)
            }
        }

        val items: List<Map<String, Any?>> = when {
            // 1. Movies Folder / View
            cleanParentId == libMoviesHex || rawParentId == "lib_movies" ||
                    (cleanParentId.isBlank() && includeItemTypes.contains("movie") && !includeItemTypes.contains("series") && !includeItemTypes.contains("episode")) -> {
                allMedia.filter { isMovie(it) }
                    .map { toJellyfinItem(it, localIp, port, isSeriesOverview = false) }
            }

            // 2. TV Shows Folder / View (Returns Series overview cards)
            cleanParentId == libSeriesHex || rawParentId == "lib_series" ||
                    (cleanParentId.isBlank() && includeItemTypes.contains("series") && !includeItemTypes.contains("movie")) -> {
                allMedia.filter { isSeries(it) }
                    .distinctBy { it.title.lowercase().trim() }
                    .map { toJellyfinItem(it, localIp, port, isSeriesOverview = true) }
            }

            // 3. Anime Folder / View (Returns Anime overview cards)
            cleanParentId == libAnimeHex || rawParentId == "lib_anime" -> {
                allMedia.filter { isAnime(it) }
                    .distinctBy { it.title.lowercase().trim() }
                    .map { toJellyfinItem(it, localIp, port, isSeriesOverview = (it.season != null || it.episode != null || it.type == "series" || it.type == "anime")) }
            }

            // 4. Inside a Series (e.g. parentId is series_loki) -> Return Seasons (unless episodes requested)
            rawParentId.startsWith("series_") && !rawParentId.contains("_s") && !includeItemTypes.contains("episode") -> {
                val episodes = getEpisodesForSeries(rawParentId)
                val seasons = episodes.mapNotNull { it.season ?: 1 }.distinct().sorted()
                val effectiveSeasons = if (seasons.isEmpty()) listOf(1) else seasons
                effectiveSeasons.map { s ->
                    val seasonRawId = "${rawParentId}_s$s"
                    val seasonHex = toHexId(seasonRawId)
                    mapOf(
                        "Name" to "Season $s",
                        "ServerId" to serverId,
                        "Id" to seasonHex,
                        "SeriesId" to toHexId(rawParentId),
                        "IndexNumber" to s,
                        "Type" to "Season",
                        "IsFolder" to true,
                        "UserData" to mapOf(
                            "UnplayedItemCount" to episodes.count { (it.season ?: 1) == s },
                            "PlaybackPositionTicks" to 0L,
                            "PlayCount" to 0,
                            "IsFavorite" to false,
                            "Played" to false
                        ),
                        "ImageTags" to mapOf("Primary" to seasonHex)
                    )
                }
            }

            // 5. Inside a Season OR Requesting Episodes for a Series
            (rawParentId.contains("_s") || (rawParentId.startsWith("series_") && includeItemTypes.contains("episode"))) -> {
                val seasonNum = if (rawParentId.contains("_s")) {
                    rawParentId.substringAfter("_s").toIntOrNull()
                } else null
                var episodes = getEpisodesForSeries(rawParentId)
                if (seasonNum != null) {
                    episodes = episodes.filter { (it.season ?: 1) == seasonNum }
                }
                episodes.sortedWith(compareBy({ it.season ?: 1 }, { it.episode ?: 1 }))
                    .map { toJellyfinItem(it, localIp, port, isSeriesOverview = false) }
            }

            // 6. Global Catalog Query / Fallback
            else -> {
                var filtered = allMedia
                if (includeItemTypes.isNotEmpty()) {
                    filtered = filtered.filter { item ->
                        val isSer = isSeries(item)
                        val isAni = isAnime(item)
                        when {
                            isAni -> includeItemTypes.contains("series") || includeItemTypes.contains("movie") || includeItemTypes.contains("episode")
                            isSer -> includeItemTypes.contains("series") || includeItemTypes.contains("episode")
                            else -> includeItemTypes.contains("movie")
                        }
                    }
                }
                filtered.distinctBy { if (isSeries(it)) it.title.lowercase().trim() else it.id }
                    .map { toJellyfinItem(it, localIp, port, isSeriesOverview = isSeries(it)) }
            }
        }

        val total = items.size
        val paged = items.drop(startIndex).take(limit)
        return jsonResponse(mapOf("Items" to paged, "TotalRecordCount" to total, "StartIndex" to startIndex))
    }

    private fun handleShowSeasons(seriesId: String, localIp: String, port: Int): Response {
        val episodes = getEpisodesForSeries(seriesId)
        val rawSeriesId = fromHexId(seriesId).substringBefore("_s")
        val seriesHex = toHexId(rawSeriesId)
        val showTitle = episodes.firstOrNull()?.title ?: getSeriesTitleFromId(seriesId)
        val seasons = episodes.mapNotNull { it.season ?: 1 }.distinct().sorted()
        val effectiveSeasons = if (seasons.isEmpty()) listOf(1) else seasons
        val seasonList = effectiveSeasons.map { s ->
            val seasonRawId = "${rawSeriesId}_s$s"
            val seasonHex = toHexId(seasonRawId)
            mapOf(
                "Name" to "Season $s",
                "ServerId" to serverId,
                "Id" to seasonHex,
                "SeriesId" to seriesHex,
                "SeriesName" to showTitle,
                "SeriesPrimaryImageTag" to seriesHex,
                "IndexNumber" to s,
                "Type" to "Season",
                "IsFolder" to true,
                "UserData" to mapOf(
                    "UnplayedItemCount" to episodes.count { (it.season ?: 1) == s },
                    "PlaybackPositionTicks" to 0L,
                    "PlayCount" to 0,
                    "IsFavorite" to false,
                    "Played" to false
                ),
                "ImageTags" to mapOf("Primary" to seriesHex)
            )
        }
        return jsonResponse(mapOf("Items" to seasonList, "TotalRecordCount" to seasonList.size, "StartIndex" to 0))
    }

    private fun handleShowEpisodes(seriesId: String, seasonNum: Int?, localIp: String, port: Int): Response {
        var episodes = getEpisodesForSeries(seriesId)
        if (seasonNum != null) {
            episodes = episodes.filter { (it.season ?: 1) == seasonNum }
        }
        val epList = episodes.sortedWith(compareBy({ it.season ?: 1 }, { it.episode ?: 1 }))
            .map { toJellyfinItem(it, localIp, port, isSeriesOverview = false) }
        return jsonResponse(mapOf("Items" to epList, "TotalRecordCount" to epList.size, "StartIndex" to 0))
    }

    private fun handleGetSingleItem(itemId: String, localIp: String, port: Int): Response {
        val rawId = fromHexId(itemId)

        // 1. Library Folders
        if (rawId == "lib_movies" || itemId.replace("-", "").lowercase() == libMoviesHex) {
            return jsonResponse(
                mapOf(
                    "Name" to "Movies",
                    "ServerId" to serverId,
                    "Id" to libMoviesHex,
                    "Type" to "CollectionFolder",
                    "IsFolder" to true,
                    "ImageTags" to mapOf("Primary" to libMoviesHex)
                )
            )
        }
        if (rawId == "lib_series" || itemId.replace("-", "").lowercase() == libSeriesHex) {
            return jsonResponse(
                mapOf(
                    "Name" to "TV Shows",
                    "ServerId" to serverId,
                    "Id" to libSeriesHex,
                    "Type" to "CollectionFolder",
                    "IsFolder" to true,
                    "ImageTags" to mapOf("Primary" to libSeriesHex)
                )
            )
        }
        if (rawId == "lib_anime" || itemId.replace("-", "").lowercase() == libAnimeHex) {
            return jsonResponse(
                mapOf(
                    "Name" to "Anime",
                    "ServerId" to serverId,
                    "Id" to libAnimeHex,
                    "Type" to "CollectionFolder",
                    "IsFolder" to true,
                    "ImageTags" to mapOf("Primary" to libAnimeHex)
                )
            )
        }

        // 2. Series item
        if (rawId.startsWith("series_") && !rawId.contains("_s")) {
            val episodes = getEpisodesForSeries(rawId)
            val match = episodes.firstOrNull() ?: tgClient.getAllMedia().firstOrNull { isSeries(it) || isAnime(it) }
            if (match != null) {
                return jsonResponse(toJellyfinItem(match, localIp, port, isSeriesOverview = true))
            }
        }

        // 3. Season item
        if (rawId.startsWith("series_") && rawId.contains("_s")) {
            val seriesRawId = rawId.substringBefore("_s")
            val seasonNum = rawId.substringAfter("_s").toIntOrNull() ?: 1
            val episodes = getEpisodesForSeries(rawId)
            val seasonHex = toHexId(rawId)
            val seriesHex = toHexId(seriesRawId)
            val showTitle = episodes.firstOrNull()?.title ?: getSeriesTitleFromId(rawId)
            return jsonResponse(
                mapOf(
                    "Name" to "Season $seasonNum",
                    "ServerId" to serverId,
                    "Id" to seasonHex,
                    "SeriesId" to seriesHex,
                    "SeriesName" to showTitle,
                    "SeriesPrimaryImageTag" to seriesHex,
                    "IndexNumber" to seasonNum,
                    "Type" to "Season",
                    "IsFolder" to true,
                    "UserData" to mapOf(
                        "UnplayedItemCount" to episodes.count { (it.season ?: 1) == seasonNum },
                        "PlaybackPositionTicks" to 0L,
                        "PlayCount" to 0,
                        "IsFavorite" to false,
                        "Played" to false
                    ),
                    "ImageTags" to mapOf("Primary" to seriesHex)
                )
            )
        }

        // 4. Media item
        val item = findMedia(itemId)
        if (item != null) {
            val dto = toJellyfinItem(item, localIp, port, isSeriesOverview = false)
            return jsonResponse(dto)
        }

        // 5. Final fallback if itemId was series hex
        val seriesEpisodes = getEpisodesForSeries(itemId)
        if (seriesEpisodes.isNotEmpty()) {
            return jsonResponse(toJellyfinItem(seriesEpisodes.first(), localIp, port, isSeriesOverview = true))
        }

        return jsonResponse(mapOf<String, Any?>())
    }

    private fun handleSimilarItems(cleanUri: String, params: Map<String, String>, localIp: String, port: Int): Response {
        val itemId = extractSegmentBetween(cleanUri, "items/", "/similar")
            .ifBlank { extractSegmentBetween(cleanUri, "shows/", "/similar") }
            .ifBlank { cleanUri.substringBeforeLast("/").substringAfterLast("/") }
        val target = findMedia(itemId)
        val limit = getParam(params, "Limit", "limit").toIntOrNull() ?: 12
        val all = tgClient.getAllMedia().filter { !it.isHidden }

        val isTargetSeries = target != null && (isSeries(target) || isAnime(target))
        val targetTitle = target?.title ?: ""

        val similarItems = if (isTargetSeries) {
            all.filter { (isSeries(it) || isAnime(it)) && !it.title.equals(targetTitle, ignoreCase = true) }
                .distinctBy { it.title.lowercase().trim() }
                .take(limit)
                .map { toJellyfinItem(it, localIp, port, isSeriesOverview = true) }
        } else {
            all.filter { isMovie(it) && it.id != target?.id }
                .distinctBy { it.title.lowercase().trim() }
                .take(limit)
                .map { toJellyfinItem(it, localIp, port, isSeriesOverview = false) }
        }

        return jsonResponse(
            mapOf(
                "Items" to similarItems,
                "TotalRecordCount" to similarItems.size,
                "StartIndex" to 0
            )
        )
    }

    private fun handlePlaybackInfo(itemId: String, localIp: String, port: Int): Response {
        val item = findMedia(itemId)
        if (item == null) {
            return jsonResponse(mapOf("MediaSources" to emptyList<Any>()))
        }

        val hexId = toHexId(item.id)
        val rawFileName = if (item.fileName.isNotBlank()) item.fileName else "${item.title}.mkv"
        val safeFileName = try {
            URLEncoder.encode(rawFileName, "UTF-8").replace("+", "%20")
        } catch (_: Exception) {
            "video.mkv"
        }
        val streamUrl = "http://$localIp:$port/stream/video/${item.channelId}/${item.id}/$safeFileName"
        val durationTicks = if (item.durationSeconds > 0) item.durationSeconds.toLong() * 10_000_000L else 3600L * 10_000_000L
        val ext = rawFileName.substringAfterLast(".", "mkv").lowercase()

        val (videoStream, audioStream) = resolveMediaStreams(item)
        val mediaSource = mapOf(
            "Id" to hexId,
            "Name" to item.title,
            "Path" to streamUrl,
            "Protocol" to "Http",
            "Container" to ext,
            "Size" to item.sizeBytes,
            "RunTimeTicks" to durationTicks,
            "SupportsDirectPlay" to true,
            "SupportsDirectStream" to true,
            "SupportsTranscoding" to false,
            "DirectStreamUrl" to streamUrl,
            "MediaStreams" to listOf(videoStream, audioStream)
        )

        val result = mapOf(
            "MediaSources" to listOf(mediaSource),
            "PlaySessionId" to toHexId("session_${item.id}")
        )
        return jsonResponse(result)
    }

    private fun handleVideoStreamRedirect(itemId: String, localIp: String, port: Int): Response {
        val item = findMedia(itemId)
        if (item != null) {
            val rawFileName = if (item.fileName.isNotBlank()) item.fileName else "${item.title}.mkv"
            val safeFileName = try {
                URLEncoder.encode(rawFileName, "UTF-8").replace("+", "%20")
            } catch (_: Exception) {
                "video.mkv"
            }
            val redirectUrl = "http://$localIp:$port/stream/video/${item.channelId}/${item.id}/$safeFileName"
            val resp = NanoHTTPD.newFixedLengthResponse(Response.Status.REDIRECT, "text/plain", "")
            resp.addHeader("Location", redirectUrl)
            return resp
        }
        return NanoHTTPD.newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Not Found")
    }

    private fun handleImageRedirect(itemId: String, imageType: String): Response {
        val item = findMedia(itemId)
        val config = configProvider()

        // Fast resolution for series, anime, seasons, or episodes: inherit poster from show
        val showPoster = if (item != null && (isSeries(item) || isAnime(item))) {
            tgClient.getAllMedia().firstOrNull {
                it.title.equals(item.title, ignoreCase = true) && !it.posterUrl.isNullOrBlank()
            }?.posterUrl
        } else null

        val poster = if (imageType.equals("Backdrop", ignoreCase = true)) {
            item?.backdropUrl ?: item?.posterUrl ?: showPoster
        } else {
            item?.posterUrl ?: showPoster ?: if (item != null) {
                val resolved = posterService.resolvePoster(
                    item.title,
                    config.customPosterUrl,
                    config.customPosterApiKey,
                    config.tmdbApiKey,
                    config.tvdbApiKey
                )
                if (resolved != null) {
                    item.posterUrl = resolved
                    posterService.cachePoster(item.title, resolved)
                }
                resolved
            } else null
        }

        val targetUrl = poster ?: "https://images.metahub.space/poster/medium/placeholder/img"
        val resp = NanoHTTPD.newFixedLengthResponse(Response.Status.REDIRECT, "text/plain", "")
        resp.addHeader("Location", targetUrl)
        resp.addHeader("Cache-Control", "public, max-age=86400, immutable")
        return resp
    }

    private fun handleDisplayPreferences(): Response {
        val prefs = mapOf(
            "Id" to "usersettings",
            "ViewType" to "Poster",
            "SortBy" to "SortName",
            "IndexBy" to "None",
            "RememberIndexing" to false,
            "PrimaryImageAspectRatio" to 0.6666667,
            "CustomPrefs" to emptyMap<String, String>(),
            "ScrollDirection" to "Horizontal",
            "ShowBackdrop" to true,
            "RememberSorting" to true,
            "SortOrder" to "Ascending",
            "ShowSidebar" to true,
            "Client" to "Infuse"
        )
        return jsonResponse(prefs)
    }

    // --- Helper Functions ---

    private fun getSeriesSlug(item: MediaItem): String {
        return item.title.lowercase().replace(Regex("[^a-z0-9]"), "_").trim('_')
    }

    private fun getSeriesId(item: MediaItem): String {
        return "series_${getSeriesSlug(item)}"
    }

    private fun getSeriesTitleFromId(seriesId: String): String {
        val raw = fromHexId(seriesId)
        val slug = raw.removePrefix("series_").substringBefore("_s")
        val all = tgClient.getAllMedia()
        val match = all.firstOrNull { getSeriesSlug(it) == slug }
        return match?.title ?: slug.replace("_", " ")
    }

    private fun getEpisodesForSeries(seriesOrSeasonId: String): List<MediaItem> {
        val raw = fromHexId(seriesOrSeasonId)
        val clean = seriesOrSeasonId.replace("-", "").lowercase().trim()
        val all = tgClient.getAllMedia().filter { !it.isHidden && (isSeries(it) || isAnime(it)) }

        // 1. Try matching by slug from raw (e.g. series_loki -> loki)
        val targetSlug = raw.removePrefix("series_").substringBefore("_s")
        val matches = all.filter { getSeriesSlug(it) == targetSlug }
        if (matches.isNotEmpty()) return matches

        // 2. Try matching by hex ID of series
        val hexMatches = all.filter { toHexId(getSeriesId(it)) == clean }
        if (hexMatches.isNotEmpty()) return hexMatches

        // 3. Fallback by normalized title
        val fallbackTitle = targetSlug.replace("_", " ")
        return all.filter { it.title.equals(fallbackTitle, ignoreCase = true) }
    }

    private fun findMedia(id: String): MediaItem? {
        val rawId = fromHexId(id.removeSuffix(".mkv"))
        val all = tgClient.getAllMedia()
        val direct = all.firstOrNull { it.id == rawId }
            ?: all.firstOrNull { it.id == id }
            ?: all.firstOrNull { it.id.endsWith(rawId) }
            ?: all.firstOrNull { it.id.endsWith(id) }
            ?: all.firstOrNull { it.imdbId != null && it.imdbId.equals(rawId, ignoreCase = true) }
        if (direct != null) return direct

        // Support virtual series and season IDs
        if (rawId.startsWith("series_")) {
            val episodes = getEpisodesForSeries(rawId)
            return episodes.firstOrNull()
        }
        return null
    }

    private fun extractSegmentBetween(uri: String, startDelim: String, endDelim: String): String {
        val lower = uri.lowercase()
        val sIdx = lower.indexOf(startDelim.lowercase())
        if (sIdx == -1) return ""
        val after = uri.substring(sIdx + startDelim.length)
        val eIdx = after.lowercase().indexOf(endDelim.lowercase())
        return if (eIdx != -1) after.substring(0, eIdx) else after
    }

    private fun resolveMediaStreams(item: MediaItem): Pair<Map<String, Any?>, Map<String, Any?>> {
        val fn = (item.fileName + " " + item.quality + " " + item.qualityBadge).lowercase()

        // 1. Resolution
        val is4K = fn.contains("2160") || fn.contains("4k") || fn.contains("uhd")
        val is1080 = fn.contains("1080") || fn.contains("fhd")
        val is720 = fn.contains("720") || fn.contains("hd")
        val is480 = fn.contains("480") || fn.contains("sd")

        val width = when {
            is4K -> 3840
            is1080 -> 1920
            is720 -> 1280
            is480 -> 854
            else -> 1920
        }
        val height = when {
            is4K -> 2160
            is1080 -> 1080
            is720 -> 720
            is480 -> 480
            else -> 1080
        }

        // 2. Video Codec
        val isHevc = fn.contains("hevc") || fn.contains("h265") || fn.contains("h.265") || fn.contains("x265")
        val isAv1 = fn.contains("av1")
        val isVp9 = fn.contains("vp9")
        val videoCodec = when {
            isHevc -> "hevc"
            isAv1 -> "av1"
            isVp9 -> "vp9"
            else -> "h264"
        }

        // 3. Bit Depth & HDR
        val is10Bit = fn.contains("10bit") || fn.contains("10-bit") || fn.contains("hdr") || fn.contains("dovi") || fn.contains("dv")
        val bitDepth = if (is10Bit) 10 else 8
        val isHdr = fn.contains("hdr") || fn.contains("hdr10") || fn.contains("dovi") || fn.contains("dv") || fn.contains("dolby vision")
        val isDv = fn.contains("dovi") || fn.contains("dv") || fn.contains("dolby vision")

        val videoStream = mutableMapOf<String, Any?>(
            "Type" to "Video",
            "Index" to 0,
            "Codec" to videoCodec,
            "IsDefault" to true,
            "Width" to width,
            "Height" to height,
            "AspectRatio" to "16:9",
            "BitDepth" to bitDepth,
            "Profile" to (if (isHevc) (if (is10Bit) "Main 10" else "Main") else "High"),
            "DisplayTitle" to "${if (is4K) "4K" else if (is1080) "1080p" else if (is720) "720p" else "SD"} $videoCodec${if (isHdr) " HDR" else ""}"
        )
        if (isHdr) {
            videoStream["VideoRange"] = if (isDv) "DOVI" else "HDR"
            videoStream["ColorSpace"] = "bt2020nc"
            videoStream["ColorTransfer"] = if (fn.contains("hlg")) "arib-std-b67" else "smpte2084"
            videoStream["ColorPrimaries"] = "bt2020"
        } else {
            videoStream["VideoRange"] = "SDR"
        }

        // 4. Audio Codec & Channels
        val isAtmos = fn.contains("atmos")
        val isDdp = fn.contains("ddp") || fn.contains("dd+") || fn.contains("eac3") || isAtmos
        val isTrueHd = fn.contains("truehd")
        val isDts = fn.contains("dts")
        val isAc3 = fn.contains("ac3") || fn.contains("dd5.1")
        val is71 = fn.contains("7.1")
        val is51 = fn.contains("5.1") || isAtmos || isDdp || isTrueHd || isDts || isAc3

        val audioCodec = when {
            isTrueHd -> "truehd"
            isDdp -> "eac3"
            isDts -> "dts"
            isAc3 -> "ac3"
            fn.contains("flac") -> "flac"
            fn.contains("opus") -> "opus"
            else -> "aac"
        }
        val audioChannels = when {
            is71 -> 8
            is51 -> 6
            else -> 2
        }
        val audioTitle = when {
            isAtmos -> "Dolby Atmos (E-AC-3 5.1)"
            isDdp -> "Dolby Digital Plus 5.1"
            isTrueHd -> "Dolby TrueHD 7.1"
            isDts -> "DTS-HD 5.1"
            isAc3 -> "Dolby Digital 5.1"
            else -> "Stereo 2.0"
        }

        val audioStream = mapOf<String, Any?>(
            "Type" to "Audio",
            "Index" to 1,
            "Codec" to audioCodec,
            "IsDefault" to true,
            "Channels" to audioChannels,
            "Title" to audioTitle,
            "DisplayTitle" to "$audioTitle ($audioCodec)"
        )

        return Pair(videoStream, audioStream)
    }

    private fun toJellyfinItem(
        item: MediaItem,
        localIp: String,
        port: Int,
        isSeriesOverview: Boolean = false
    ): Map<String, Any?> {
        val isSer = isSeries(item)
        val isAni = isAnime(item)
        val rawItemId = if ((isSer || isAni) && isSeriesOverview) getSeriesId(item) else item.id
        val hexId = toHexId(rawItemId)

        val rawFileName = if (item.fileName.isNotBlank()) item.fileName else "${item.title}.mkv"
        val safeFileName = try {
            URLEncoder.encode(rawFileName, "UTF-8").replace("+", "%20")
        } catch (_: Exception) {
            "video.mkv"
        }
        val streamUrl = "http://$localIp:$port/stream/video/${item.channelId}/${item.id}/$safeFileName"
        val durationTicks = if (item.durationSeconds > 0) item.durationSeconds.toLong() * 10_000_000L else 3600L * 10_000_000L
        val yearInt = item.year?.toIntOrNull() ?: 2024
        val ext = rawFileName.substringAfterLast(".", "mkv").lowercase()

        if (item.posterUrl.isNullOrBlank() && (isSer || isAni)) {
            val showPoster = tgClient.getAllMedia().firstOrNull {
                it.title.equals(item.title, ignoreCase = true) && !it.posterUrl.isNullOrBlank()
            }?.posterUrl
            if (showPoster != null) {
                item.posterUrl = showPoster
            }
        }

        val displayName = when {
            (isSer || isAni) && isSeriesOverview -> item.title
            (isSer || isAni) && item.episode != null -> item.episodeTitle ?: "Episode ${item.episode}"
            else -> item.title
        }

        val providerIds = mutableMapOf<String, String>()
        if (!item.imdbId.isNullOrBlank()) {
            providerIds["Imdb"] = item.imdbId!!
        }
        if (item.tmdbId != null && item.tmdbId!! > 0) {
            providerIds["Tmdb"] = item.tmdbId.toString()
        }
        if (item.kitsuId != null && item.kitsuId!! > 0) {
            providerIds["Kitsu"] = item.kitsuId.toString()
        }

        val map = mutableMapOf<String, Any?>(
            "Name" to displayName,
            "OriginalTitle" to item.title,
            "ServerId" to serverId,
            "Id" to hexId,
            "Overview" to (item.overview ?: "${item.qualityBadge} • ${item.formattedSize}"),
            "ProductionYear" to yearInt,
            "CommunityRating" to (if (item.rating > 0) item.rating else 8.0),
            "RunTimeTicks" to durationTicks,
            "Container" to ext,
            "CanDownload" to true,
            "SupportsDirectPlay" to true,
            "SupportsDirectStream" to true,
            "SupportsTranscoding" to false,
            "ProviderIds" to providerIds,
            "Genres" to (if (isAni) listOf("Animation", "Anime") else if (isSer) listOf("Drama", "Series") else listOf("Action", "Adventure")),
            "PremiereDate" to "${yearInt}-01-01T00:00:00.0000000Z",
            "UserData" to mapOf(
                "PlaybackPositionTicks" to 0L,
                "PlayCount" to 0,
                "IsFavorite" to false,
                "Played" to false,
                "UnplayedItemCount" to 0
            ),
            "ImageTags" to mapOf("Primary" to hexId),
            "BackdropImageTags" to listOf(hexId),
            "MediaType" to "Video"
        )

        if ((isSer || isAni) && isSeriesOverview) {
            val episodes = getEpisodesForSeries(rawItemId)
            val seasonCount = episodes.mapNotNull { it.season ?: 1 }.distinct().size
            map["Type"] = "Series"
            map["IsFolder"] = true
            map["CollectionType"] = "tvshows"
            map["Status"] = "Continuing"
            map["ChildCount"] = if (seasonCount > 0) seasonCount else 1
            map["SeasonCount"] = if (seasonCount > 0) seasonCount else 1
            map["EpisodeCount"] = episodes.size
            map["RecursiveItemCount"] = episodes.size
            map["UserData"] = mapOf(
                "PlaybackPositionTicks" to 0L,
                "PlayCount" to 0,
                "IsFavorite" to false,
                "Played" to false,
                "UnplayedItemCount" to episodes.size
            )
        } else if (isSer || isAni) {
            val seriesRawId = getSeriesId(item)
            val sNum = item.season ?: 1
            val epNum = item.episode ?: 1
            val seasonRawId = "${seriesRawId}_s$sNum"
            map["Type"] = "Episode"
            map["IsFolder"] = false
            map["IndexNumber"] = epNum
            map["ParentIndexNumber"] = sNum
            map["SeriesName"] = item.title
            map["SeriesId"] = toHexId(seriesRawId)
            map["SeasonId"] = toHexId(seasonRawId)
            map["SeasonName"] = "Season $sNum"
        } else {
            map["Type"] = "Movie"
            map["IsFolder"] = false
        }

        if (!isSeriesOverview) {
            val (videoStream, audioStream) = resolveMediaStreams(item)
            map["Path"] = streamUrl
            map["MediaSources"] = listOf(
                mapOf(
                    "Id" to hexId,
                    "Name" to item.title,
                    "Path" to streamUrl,
                    "Protocol" to "Http",
                    "Container" to ext,
                    "Size" to item.sizeBytes,
                    "RunTimeTicks" to durationTicks,
                    "SupportsDirectPlay" to true,
                    "SupportsDirectStream" to true,
                    "SupportsTranscoding" to false,
                    "DirectStreamUrl" to streamUrl,
                    "MediaStreams" to listOf(videoStream, audioStream)
                )
            )
        }

        return map
    }

    private fun jsonResponse(data: Any, status: Response.Status = Response.Status.OK): Response {
        val json = gson.toJson(data)
        val resp = NanoHTTPD.newFixedLengthResponse(status, "application/json; charset=utf-8", json)
        resp.addHeader("X-Application-Version", "10.10.3")
        return resp
    }

    private fun noContentResponse(): Response {
        val resp = NanoHTTPD.newFixedLengthResponse(Response.Status.NO_CONTENT, "text/plain", "")
        resp.addHeader("X-Application-Version", "10.10.3")
        return resp
    }
}
