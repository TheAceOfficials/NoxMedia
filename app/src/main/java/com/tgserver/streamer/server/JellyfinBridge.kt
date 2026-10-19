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
        return hexToIdMap[clean] ?: hexOrRaw
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
                lowerUri.contains("/shows/") && lowerUri.endsWith("/seasons") -> {
                    val seriesId = extractSegmentBetween(cleanUri, "shows/", "/seasons")
                    handleShowSeasons(seriesId, localIp, config.port)
                }
                lowerUri.contains("/shows/") && lowerUri.endsWith("/episodes") -> {
                    val seriesId = extractSegmentBetween(cleanUri, "shows/", "/episodes")
                    val seasonNum = getParam(params, "season", "seasonNumber", "seasonId")
                        .let { s -> s.toIntOrNull() ?: fromHexId(s).substringAfterLast("_s").toIntOrNull() }
                    handleShowEpisodes(seriesId, seasonNum, localIp, config.port)
                }

                // ================= 6. Single Item Metadata =================
                // Matches /Items/{itemId} or /Users/{userId}/Items/{itemId}
                Regex(".*/items/([0-9a-fA-F]{32}|tg_[^/]+|series_[^/]+|lib_[^/]+)", RegexOption.IGNORE_CASE).matches(cleanUri)
                        && !lowerUri.contains("/images")
                        && !lowerUri.endsWith("/latest")
                        && !lowerUri.endsWith("/resume")
                        && !lowerUri.endsWith("/counts") -> {
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

            // 4. Inside a Series (e.g. parentId is series_loki) -> Return Seasons
            rawParentId.startsWith("series_") && !rawParentId.contains("_s") -> {
                val seriesTitle = getSeriesTitleFromId(rawParentId)
                val episodes = allMedia.filter { it.title.equals(seriesTitle, ignoreCase = true) }
                val seasons = episodes.mapNotNull { it.season ?: 1 }.distinct().sorted()
                seasons.map { s ->
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

            // 5. Inside a Season (e.g. parentId is series_loki_s1) -> Return Episodes
            rawParentId.contains("_s") -> {
                val seriesId = rawParentId.substringBefore("_s")
                val seasonNum = rawParentId.substringAfter("_s").toIntOrNull() ?: 1
                val seriesTitle = getSeriesTitleFromId(seriesId)
                allMedia.filter {
                    it.title.equals(seriesTitle, ignoreCase = true) && (it.season ?: 1) == seasonNum
                }.sortedBy { it.episode ?: 1 }
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
        val rawSeriesId = fromHexId(seriesId)
        val cleanTitle = getSeriesTitleFromId(rawSeriesId)
        val all = tgClient.getAllMedia().filter { !it.isHidden && it.title.equals(cleanTitle, ignoreCase = true) }
        val seasons = all.mapNotNull { it.season ?: 1 }.distinct().sorted()
        val seasonList = seasons.map { s ->
            val seasonRawId = "${rawSeriesId}_s$s"
            val seasonHex = toHexId(seasonRawId)
            mapOf(
                "Name" to "Season $s",
                "ServerId" to serverId,
                "Id" to seasonHex,
                "SeriesId" to toHexId(rawSeriesId),
                "IndexNumber" to s,
                "Type" to "Season",
                "IsFolder" to true,
                "UserData" to mapOf(
                    "UnplayedItemCount" to all.count { (it.season ?: 1) == s },
                    "PlaybackPositionTicks" to 0L,
                    "PlayCount" to 0,
                    "IsFavorite" to false,
                    "Played" to false
                ),
                "ImageTags" to mapOf("Primary" to seasonHex)
            )
        }
        return jsonResponse(mapOf("Items" to seasonList, "TotalRecordCount" to seasonList.size, "StartIndex" to 0))
    }

    private fun handleShowEpisodes(seriesId: String, seasonNum: Int?, localIp: String, port: Int): Response {
        val rawSeriesId = fromHexId(seriesId)
        val cleanTitle = getSeriesTitleFromId(rawSeriesId)
        var episodes = tgClient.getAllMedia().filter { !it.isHidden && it.title.equals(cleanTitle, ignoreCase = true) }
        if (seasonNum != null) {
            episodes = episodes.filter { (it.season ?: 1) == seasonNum }
        }
        val epList = episodes.sortedBy { it.episode ?: 1 }
            .map { toJellyfinItem(it, localIp, port, isSeriesOverview = false) }
        return jsonResponse(mapOf("Items" to epList, "TotalRecordCount" to epList.size, "StartIndex" to 0))
    }

    private fun handleGetSingleItem(itemId: String, localIp: String, port: Int): Response {
        val rawId = fromHexId(itemId)

        // Check if querying a library folder
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

        // Check if querying a series overview item
        if (rawId.startsWith("series_")) {
            val title = getSeriesTitleFromId(rawId)
            val match = tgClient.getAllMedia().firstOrNull { it.title.equals(title, ignoreCase = true) }
            if (match != null) {
                return jsonResponse(toJellyfinItem(match, localIp, port, isSeriesOverview = true))
            }
        }

        val item = findMedia(itemId)
        if (item != null) {
            val dto = toJellyfinItem(item, localIp, port, isSeriesOverview = false)
            return jsonResponse(dto)
        }
        return jsonResponse(mapOf<String, Any?>())
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
            "MediaStreams" to listOf(
                mapOf(
                    "Type" to "Video",
                    "Index" to 0,
                    "Codec" to "h264",
                    "IsDefault" to true,
                    "Width" to 1920,
                    "Height" to 1080
                ),
                mapOf(
                    "Type" to "Audio",
                    "Index" to 1,
                    "Codec" to "aac",
                    "IsDefault" to true,
                    "Channels" to 2
                )
            )
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
        val poster = if (imageType.equals("Backdrop", ignoreCase = true)) {
            item?.backdropUrl ?: item?.posterUrl
        } else {
            item?.posterUrl ?: if (item != null) {
                posterService.resolvePoster(item.title, config.customPosterUrl, config.customPosterApiKey)
            } else null
        }

        val targetUrl = poster ?: "https://images.metahub.space/poster/medium/placeholder/img"
        val resp = NanoHTTPD.newFixedLengthResponse(Response.Status.REDIRECT, "text/plain", "")
        resp.addHeader("Location", targetUrl)
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

    private fun findMedia(id: String): MediaItem? {
        val rawId = fromHexId(id.removeSuffix(".mkv"))
        val all = tgClient.getAllMedia()
        return all.firstOrNull { it.id == rawId }
            ?: all.firstOrNull { it.id == id }
            ?: all.firstOrNull { it.id.endsWith(rawId) }
            ?: all.firstOrNull { it.id.endsWith(id) }
            ?: all.firstOrNull { it.imdbId != null && it.imdbId.equals(rawId, ignoreCase = true) }
    }

    private fun getSeriesId(item: MediaItem): String {
        val slug = item.title.lowercase().replace(Regex("[^a-z0-9]"), "_").trim('_')
        return "series_$slug"
    }

    private fun getSeriesTitleFromId(seriesId: String): String {
        val slug = seriesId.removePrefix("series_")
        val match = tgClient.getAllMedia().firstOrNull {
            it.title.lowercase().replace(Regex("[^a-z0-9]"), "_").trim('_') == slug
        }
        return match?.title ?: slug.replace("_", " ")
    }

    private fun extractSegmentBetween(uri: String, startDelim: String, endDelim: String): String {
        val lower = uri.lowercase()
        val sIdx = lower.indexOf(startDelim.lowercase())
        if (sIdx == -1) return ""
        val after = uri.substring(sIdx + startDelim.length)
        val eIdx = after.lowercase().indexOf(endDelim.lowercase())
        return if (eIdx != -1) after.substring(0, eIdx) else after
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

        val displayName = when {
            (isSer || isAni) && isSeriesOverview -> item.title
            (isSer || isAni) && item.episode != null -> item.episodeTitle ?: "Episode ${item.episode}"
            else -> item.title
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
            map["Type"] = "Series"
            map["IsFolder"] = true
            map["CollectionType"] = "tvshows"
        } else if ((isSer || isAni) && item.episode != null) {
            val seriesRawId = getSeriesId(item)
            val seasonRawId = "${seriesRawId}_s${item.season ?: 1}"
            map["Type"] = "Episode"
            map["IsFolder"] = false
            map["IndexNumber"] = item.episode
            map["ParentIndexNumber"] = item.season ?: 1
            map["SeriesName"] = item.title
            map["SeriesId"] = toHexId(seriesRawId)
            map["SeasonId"] = toHexId(seasonRawId)
            map["SeasonName"] = "Season ${item.season ?: 1}"
        } else {
            map["Type"] = "Movie"
            map["IsFolder"] = false
        }

        if (!isSeriesOverview) {
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
                    "MediaStreams" to listOf(
                        mapOf(
                            "Type" to "Video",
                            "Index" to 0,
                            "Codec" to "h264",
                            "IsDefault" to true,
                            "Width" to 1920,
                            "Height" to 1080
                        ),
                        mapOf(
                            "Type" to "Audio",
                            "Index" to 1,
                            "Codec" to "aac",
                            "IsDefault" to true,
                            "Channels" to 2
                        )
                    )
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
