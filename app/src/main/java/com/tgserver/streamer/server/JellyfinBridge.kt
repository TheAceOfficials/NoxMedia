package com.tgserver.streamer.server

import android.content.Context
import com.google.gson.Gson
import com.tgserver.streamer.data.model.MediaItem
import com.tgserver.streamer.data.model.ServerConfig
import com.tgserver.streamer.posters.SpatialPosterService
import com.tgserver.streamer.telegram.TelegramClientManager
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoHTTPD.IHTTPSession
import fi.iki.elonen.NanoHTTPD.Method
import fi.iki.elonen.NanoHTTPD.Response
import java.net.URLEncoder

private const val TAG = "JellyfinBridge"

/**
 * High-performance, lightweight Jellyfin/Emby Server API Emulation Layer.
 *
 * Allows industry-standard Jellyfin client apps (Infuse on Apple TV/iOS/Mac,
 * Official Jellyfin Android TV/Mobile, Swiftfin, Findroid) to connect directly
 * to NoxMedia as a native home media server.
 *
 * Implements DirectPlay streaming without server-side transcoding, leveraging
 * NoxMedia's existing HTTP 206 Partial Content video streaming engine.
 */
class JellyfinBridge(
    private val context: Context,
    private val tgClient: TelegramClientManager,
    private val posterService: SpatialPosterService,
    private val configProvider: () -> ServerConfig,
    private val localIpProvider: () -> String
) {
    private val gson = Gson()

    val serverId = "48b6c8d76c67429bbad58923a1a9e871"
    val defaultUserId = "a1b2c3d4e5f64789a0b1c2d3e4f5a6b7"
    val defaultUserName = "NoxUser"
    val accessToken = "nox_token_12345"

    /**
     * Determines whether an incoming HTTP request URI belongs to the Jellyfin API namespace.
     */
    fun isJellyfinUri(uri: String): Boolean {
        val u = uri.trimEnd('/').lowercase()
        return u.startsWith("/system") ||
                u.startsWith("/users") ||
                u.startsWith("/userviews") ||
                u.startsWith("/items") ||
                u.startsWith("/shows") ||
                u.startsWith("/videos") ||
                u.startsWith("/sessions") ||
                u.startsWith("/displaypreferences") ||
                u.startsWith("/localization") ||
                u.startsWith("/quickconnect") ||
                u.startsWith("/branding") ||
                u.startsWith("/livetv") ||
                u.startsWith("/scheduledtasks") ||
                u.startsWith("/playback") ||
                u.startsWith("/infusesync")
    }

    /**
     * Dispatches and handles Jellyfin API requests.
     */
    fun handle(
        session: IHTTPSession,
        uri: String,
        method: Method,
        headers: Map<String, String>
    ): Response {
        val cleanUri = uri.trimEnd('/')
        val lowerUri = cleanUri.lowercase()
        val params = session.parameters.mapValues { it.value.firstOrNull() ?: "" }
        val localIp = localIpProvider()
        val config = configProvider()

        StreamLog.i(TAG, "[$method] $cleanUri from ${session.remoteIpAddress}")

        return when {
            // ================= 1. Handshake & System Info =================
            lowerUri == "/system/info/public" || lowerUri == "/system/info" -> {
                handleSystemInfo(localIp, config.port)
            }
            lowerUri == "/system/configuration" -> {
                jsonResponse(mapOf<String, Any?>())
            }

            // ================= 2. Authentication & User Management =================
            lowerUri == "/users/public" -> {
                handleUsersPublic()
            }
            lowerUri == "/users/authenticatebyname" && method == Method.POST -> {
                handleAuthenticateByName(session)
            }
            lowerUri == "/users/me" || lowerUri.matches(Regex("/users/[^/]+")) && !lowerUri.contains("/views") && !lowerUri.contains("/items") -> {
                handleGetUser()
            }

            // ================= 3. Library Views (Home Folders) =================
            lowerUri.endsWith("/views") || lowerUri == "/userviews" -> {
                handleViews()
            }
            lowerUri == "/items/counts" -> {
                handleItemCounts()
            }

            // ================= 4. Items & Library Catalog =================
            lowerUri.endsWith("/items/resume") -> {
                jsonResponse(mapOf("Items" to emptyList<Any>(), "TotalRecordCount" to 0))
            }
            lowerUri.endsWith("/items/latest") -> {
                handleLatestItems(localIp, config.port)
            }
            lowerUri.startsWith("/shows/") && lowerUri.endsWith("/seasons") -> {
                val seriesId = cleanUri.substringAfter("/Shows/").substringBefore("/Seasons")
                handleShowSeasons(seriesId, localIp, config.port)
            }
            lowerUri.startsWith("/shows/") && lowerUri.endsWith("/episodes") -> {
                val seriesId = cleanUri.substringAfter("/Shows/").substringBefore("/Episodes")
                val seasonNum = params["season"]?.toIntOrNull() ?: params["seasonId"]?.substringAfterLast("_s")?.toIntOrNull()
                handleShowEpisodes(seriesId, seasonNum, localIp, config.port)
            }
            lowerUri == "/items" || lowerUri.matches(Regex("/users/[^/]+/items")) -> {
                handleQueryItems(params, localIp, config.port)
            }

            // ================= 5. Single Item Metadata & Playback Info =================
            lowerUri.contains("/playbackinfo") -> {
                val itemId = cleanUri.substringAfter("/Items/").substringAfter("/Videos/").substringBefore("/PlaybackInfo")
                handlePlaybackInfo(itemId, localIp, config.port)
            }
            lowerUri.matches(Regex("/items/[^/]+")) && !lowerUri.contains("/images") -> {
                val itemId = cleanUri.substringAfterLast("/")
                handleGetSingleItem(itemId, localIp, config.port)
            }

            // ================= 6. Video Direct Stream Redirect =================
            lowerUri.startsWith("/videos/") && lowerUri.contains("/stream") -> {
                val itemId = cleanUri.substringAfter("/Videos/").substringBefore("/stream").removeSuffix(".mkv")
                handleVideoStreamRedirect(itemId, localIp, config.port)
            }

            // ================= 7. Poster & Image Handling =================
            lowerUri.contains("/images/") -> {
                val itemId = cleanUri.substringAfter("/Items/").substringBefore("/Images")
                val imageType = cleanUri.substringAfter("/Images/").substringBefore("/")
                handleImageRedirect(itemId, imageType)
            }

            // ================= 8. Session, Telemetry, Display Prefs & Stubs =================
            lowerUri.startsWith("/infusesync") -> {
                jsonResponse(mapOf("Items" to emptyList<Any>(), "TotalRecordCount" to 0))
            }
            lowerUri.startsWith("/sessions/playing") -> {
                noContentResponse()
            }
            lowerUri.startsWith("/sessions/capabilities") -> {
                noContentResponse()
            }
            lowerUri.startsWith("/displaypreferences") -> {
                jsonResponse(
                    mapOf(
                        "Id" to "usersettings",
                        "ViewType" to "Poster",
                        "SortBy" to "SortName",
                        "RememberSorting" to true
                    )
                )
            }
            lowerUri.startsWith("/localization") -> {
                jsonResponse(emptyList<Any>())
            }
            lowerUri == "/quickconnect/enabled" -> {
                jsonResponse(false)
            }
            lowerUri.startsWith("/branding") -> {
                jsonResponse(mapOf<String, Any?>())
            }
            lowerUri == "/livetv/channels" -> {
                jsonResponse(mapOf("Items" to emptyList<Any>(), "TotalRecordCount" to 0))
            }
            lowerUri == "/scheduledtasks" -> {
                jsonResponse(emptyList<Any>())
            }
            lowerUri == "/playback/bitratetest" -> {
                NanoHTTPD.newFixedLengthResponse(Response.Status.OK, "text/plain", "")
            }

            else -> {
                jsonResponse(mapOf("Items" to emptyList<Any>(), "TotalRecordCount" to 0))
            }
        }
    }

    // --- Endpoint Handlers ---

    private fun handleSystemInfo(localIp: String, port: Int): Response {
        val info = mapOf(
            "LocalAddress" to "http://$localIp:$port",
            "ServerName" to "NoxMedia",
            "Version" to "10.10.3",
            "ProductName" to "Jellyfin Server",
            "OperatingSystem" to "Linux",
            "Id" to serverId,
            "StartupWizardCompleted" to true,
            "CanSelfRestart" to false,
            "CanSelfUpdate" to false,
            "CanLaunchWebBrowser" to false
        )
        return jsonResponse(info)
    }

    private fun handleUsersPublic(): Response {
        val users = listOf(
            mapOf(
                "Name" to defaultUserName,
                "ServerId" to serverId,
                "Id" to defaultUserId,
                "HasPassword" to false,
                "HasConfiguredPassword" to false,
                "EnableAutoLogin" to true,
                "Policy" to mapOf(
                    "IsAdministrator" to true,
                    "IsHidden" to false,
                    "EnableContentDownloading" to true,
                    "EnableMediaPlayback" to true
                )
            )
        )
        return jsonResponse(users)
    }

    private fun handleAuthenticateByName(session: IHTTPSession): Response {
        var requestedUsername = defaultUserName
        try {
            val map = HashMap<String, String>()
            session.parseBody(map)
            val postData = map["postData"] ?: ""
            if (postData.isNotBlank()) {
                val json = com.google.gson.JsonParser.parseString(postData).asJsonObject
                val u = json.get("Username")?.asString
                if (!u.isNullOrBlank()) requestedUsername = u
            }
        } catch (_: Exception) {}

        val userObj = mapOf(
            "Name" to requestedUsername,
            "ServerId" to serverId,
            "Id" to defaultUserId,
            "HasPassword" to false,
            "HasConfiguredPassword" to false,
            "HasConfiguredEasyPassword" to false,
            "EnableAutoLogin" to true,
            "Policy" to mapOf(
                "IsAdministrator" to true,
                "IsHidden" to false,
                "IsDisabled" to false,
                "EnableContentDownloading" to true,
                "EnableMediaPlayback" to true,
                "EnableAudioPlaybackTranscoding" to true,
                "EnableVideoPlaybackTranscoding" to true,
                "EnablePlaybackRemuxing" to true,
                "EnableAllFolders" to true,
                "EnableAllDevices" to true,
                "EnableAllChannels" to true
            ),
            "Configuration" to mapOf(
                "PlayDefaultAudioTrack" to true,
                "SubtitleMode" to "Default"
            )
        )
        val authResult = mapOf(
            "User" to userObj,
            "SessionInfo" to mapOf(
                "Id" to "session_nox_1",
                "UserId" to defaultUserId,
                "UserName" to requestedUsername,
                "ServerId" to serverId,
                "IsActive" to true
            ),
            "AccessToken" to accessToken,
            "ServerId" to serverId
        )
        return jsonResponse(authResult)
    }

    private fun handleGetUser(): Response {
        val userObj = mapOf(
            "Name" to defaultUserName,
            "ServerId" to serverId,
            "Id" to defaultUserId,
            "HasPassword" to false,
            "EnableAutoLogin" to true,
            "Policy" to mapOf(
                "IsAdministrator" to true,
                "EnableContentDownloading" to true
            ),
            "Configuration" to mapOf(
                "PlayDefaultAudioTrack" to true
            )
        )
        return jsonResponse(userObj)
    }

    private fun handleViews(): Response {
        val views = listOf(
            mapOf(
                "Name" to "Movies",
                "ServerId" to serverId,
                "Id" to "lib_movies",
                "CollectionType" to "movies",
                "Type" to "CollectionFolder",
                "IsFolder" to true
            ),
            mapOf(
                "Name" to "TV Shows",
                "ServerId" to serverId,
                "Id" to "lib_series",
                "CollectionType" to "tvshows",
                "Type" to "CollectionFolder",
                "IsFolder" to true
            ),
            mapOf(
                "Name" to "Anime",
                "ServerId" to serverId,
                "Id" to "lib_anime",
                "CollectionType" to "tvshows",
                "Type" to "CollectionFolder",
                "IsFolder" to true
            )
        )
        return jsonResponse(mapOf("Items" to views, "TotalRecordCount" to views.size))
    }

    private fun handleItemCounts(): Response {
        val all = tgClient.getAllMedia().filter { !it.isHidden }
        val movies = all.count { it.type != "series" && it.type != "anime" && it.season == null }
        val series = all.filter { it.type == "series" || (it.season != null && it.episode != null) }.distinctBy { it.title.lowercase().trim() }.size
        val episodes = all.count { it.season != null && it.episode != null }
        return jsonResponse(
            mapOf(
                "MovieCount" to movies,
                "SeriesCount" to series,
                "EpisodeCount" to episodes
            )
        )
    }

    private fun handleLatestItems(localIp: String, port: Int): Response {
        val latest = tgClient.getAllMedia()
            .filter { !it.isHidden }
            .sortedByDescending { it.dateAdded }
            .take(20)
            .map { toJellyfinItem(it, localIp, port, isSeriesOverview = false) }
        return jsonResponse(mapOf("Items" to latest, "TotalRecordCount" to latest.size))
    }

    private fun handleQueryItems(params: Map<String, String>, localIp: String, port: Int): Response {
        val parentId = params["ParentId"] ?: params["parentId"] ?: ""
        val searchTerm = params["SearchTerm"] ?: params["searchTerm"] ?: ""
        val startIndex = params["StartIndex"]?.toIntOrNull() ?: 0
        val limit = params["Limit"]?.toIntOrNull() ?: 100

        var allMedia = tgClient.getAllMedia().filter { !it.isHidden }

        if (searchTerm.isNotBlank()) {
            allMedia = allMedia.filter {
                it.title.contains(searchTerm, ignoreCase = true) ||
                        it.fileName.contains(searchTerm, ignoreCase = true)
            }
        }

        val items: List<Map<String, Any?>> = when (parentId) {
            "lib_movies" -> {
                allMedia.filter { it.type != "series" && it.type != "anime" && it.season == null }
                    .map { toJellyfinItem(it, localIp, port, isSeriesOverview = false) }
            }
            "lib_series" -> {
                allMedia.filter { (it.type == "series" || it.season != null) && it.type != "anime" }
                    .distinctBy { it.title.lowercase().trim() }
                    .map { toJellyfinItem(it, localIp, port, isSeriesOverview = true) }
            }
            "lib_anime" -> {
                allMedia.filter { it.type == "anime" || it.kitsuId != null || tgClient.getChannel(it.channelId)?.isAnime == true }
                    .distinctBy { it.title.lowercase().trim() }
                    .map { toJellyfinItem(it, localIp, port, isSeriesOverview = true) }
            }
            else -> {
                // Check if parentId is a series ID
                if (parentId.startsWith("series_")) {
                    val seriesTitle = parentId.removePrefix("series_").replace("_", " ")
                    val episodes = allMedia.filter { it.title.equals(seriesTitle, ignoreCase = true) }
                    val seasons = episodes.mapNotNull { it.season ?: 1 }.distinct().sorted()
                    seasons.map { s ->
                        mapOf(
                            "Name" to "Season $s",
                            "ServerId" to serverId,
                            "Id" to "${parentId}_s$s",
                            "SeriesId" to parentId,
                            "IndexNumber" to s,
                            "Type" to "Season",
                            "IsFolder" to true
                        )
                    }
                } else if (parentId.contains("_s")) {
                    val seriesId = parentId.substringBefore("_s")
                    val seasonNum = parentId.substringAfter("_s").toIntOrNull() ?: 1
                    val seriesTitle = seriesId.removePrefix("series_").replace("_", " ")
                    allMedia.filter {
                        it.title.equals(seriesTitle, ignoreCase = true) && (it.season ?: 1) == seasonNum
                    }.sortedBy { it.episode ?: 1 }
                        .map { toJellyfinItem(it, localIp, port, isSeriesOverview = false) }
                } else {
                    allMedia.map { toJellyfinItem(it, localIp, port, isSeriesOverview = false) }
                }
            }
        }

        val total = items.size
        val paged = items.drop(startIndex).take(limit)
        return jsonResponse(mapOf("Items" to paged, "TotalRecordCount" to total, "StartIndex" to startIndex))
    }

    private fun handleShowSeasons(seriesId: String, localIp: String, port: Int): Response {
        val cleanTitle = getSeriesTitleFromId(seriesId)
        val all = tgClient.getAllMedia().filter { !it.isHidden && it.title.equals(cleanTitle, ignoreCase = true) }
        val seasons = all.mapNotNull { it.season ?: 1 }.distinct().sorted()
        val seasonList = seasons.map { s ->
            mapOf(
                "Name" to "Season $s",
                "ServerId" to serverId,
                "Id" to "${seriesId}_s$s",
                "SeriesId" to seriesId,
                "IndexNumber" to s,
                "Type" to "Season",
                "IsFolder" to true
            )
        }
        return jsonResponse(mapOf("Items" to seasonList, "TotalRecordCount" to seasonList.size))
    }

    private fun handleShowEpisodes(seriesId: String, seasonNum: Int?, localIp: String, port: Int): Response {
        val cleanTitle = getSeriesTitleFromId(seriesId)
        var episodes = tgClient.getAllMedia().filter { !it.isHidden && it.title.equals(cleanTitle, ignoreCase = true) }
        if (seasonNum != null) {
            episodes = episodes.filter { (it.season ?: 1) == seasonNum }
        }
        val epList = episodes.sortedBy { it.episode ?: 1 }
            .map { toJellyfinItem(it, localIp, port, isSeriesOverview = false) }
        return jsonResponse(mapOf("Items" to epList, "TotalRecordCount" to epList.size))
    }

    private fun handleGetSingleItem(itemId: String, localIp: String, port: Int): Response {
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
            "Id" to item.id,
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
                mapOf("Type" to "Video", "Index" to 0, "Codec" to "h264", "IsDefault" to true),
                mapOf("Type" to "Audio", "Index" to 1, "Codec" to "aac", "IsDefault" to true)
            )
        )

        val result = mapOf(
            "MediaSources" to listOf(mediaSource),
            "PlaySessionId" to "session_${item.id}"
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

    // --- Helper Functions ---

    private fun findMedia(id: String): MediaItem? {
        val all = tgClient.getAllMedia()
        return all.firstOrNull { it.id == id }
            ?: all.firstOrNull { it.id.endsWith(id) }
            ?: all.firstOrNull { it.imdbId != null && it.imdbId.equals(id, ignoreCase = true) }
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

    private fun toJellyfinItem(
        item: MediaItem,
        localIp: String,
        port: Int,
        isSeriesOverview: Boolean = false
    ): Map<String, Any?> {
        val isSeries = item.type == "series" || item.type == "anime" || (item.season != null && item.episode != null)
        val cleanId = item.id

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

        val map = mutableMapOf<String, Any?>(
            "Name" to if (isSeries && !isSeriesOverview && item.episode != null) (item.episodeTitle ?: "Episode ${item.episode}") else item.title,
            "OriginalTitle" to item.title,
            "ServerId" to serverId,
            "Id" to if (isSeries && isSeriesOverview) getSeriesId(item) else cleanId,
            "Overview" to (item.overview ?: "${item.qualityBadge} • ${item.formattedSize}"),
            "ProductionYear" to yearInt,
            "CommunityRating" to (if (item.rating > 0) item.rating else 8.0),
            "RunTimeTicks" to durationTicks,
            "Container" to ext,
            "CanDownload" to true,
            "SupportsDirectPlay" to true,
            "SupportsDirectStream" to true,
            "SupportsTranscoding" to false,
            "ImageTags" to mapOf("Primary" to cleanId),
            "MediaType" to "Video"
        )

        if (isSeries && isSeriesOverview) {
            map["Type"] = "Series"
            map["IsFolder"] = true
            map["CollectionType"] = "tvshows"
        } else if (isSeries && item.episode != null) {
            map["Type"] = "Episode"
            map["IsFolder"] = false
            map["IndexNumber"] = item.episode
            map["ParentIndexNumber"] = item.season ?: 1
            map["SeriesName"] = item.title
            map["SeriesId"] = getSeriesId(item)
            map["SeasonName"] = "Season ${item.season ?: 1}"
        } else {
            map["Type"] = "Movie"
            map["IsFolder"] = false
        }

        if (!isSeriesOverview) {
            map["Path"] = streamUrl
            map["MediaSources"] = listOf(
                mapOf(
                    "Id" to cleanId,
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
                        mapOf("Type" to "Video", "Index" to 0, "Codec" to "h264", "IsDefault" to true),
                        mapOf("Type" to "Audio", "Index" to 1, "Codec" to "aac", "IsDefault" to true)
                    )
                )
            )
        }

        return map
    }

    private fun jsonResponse(data: Any): Response {
        val json = gson.toJson(data)
        return NanoHTTPD.newFixedLengthResponse(Response.Status.OK, "application/json", json)
    }

    private fun noContentResponse(): Response {
        return NanoHTTPD.newFixedLengthResponse(Response.Status.NO_CONTENT, "text/plain", "")
    }
}
