package com.tgserver.streamer.server

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.tgserver.streamer.data.model.MediaItem
import com.tgserver.streamer.data.model.ServerConfig
import com.tgserver.streamer.posters.SpatialPosterService
import com.tgserver.streamer.telegram.TelegramClientManager
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.InputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

private const val TAG = "LocalHttpServer"

class LocalHttpServer(
    private val context: Context,
    private val config: ServerConfig,
    private val tgClient: TelegramClientManager,
    private val posterService: SpatialPosterService
) : NanoHTTPD("0.0.0.0", config.port) {

    private val gson = Gson()
    private val startTime = System.currentTimeMillis()
    private val idTitleCache = ConcurrentHashMap<String, String>()
    private val streamHttpClient = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(3, TimeUnit.SECONDS)
        .build()

    private val jellyfinBridge by lazy {
        JellyfinBridge(
            context = context,
            tgClient = tgClient,
            posterService = posterService,
            configProvider = { config },
            localIpProvider = { getLocalIpAddress() }
        )
    }

    fun getLocalIpAddress(): String = Companion.getLocalIpAddress()

    companion object {
        fun getLocalIpAddress(): String {
            try {
                val candidates = mutableListOf<Pair<String, String>>()
                val interfaces = NetworkInterface.getNetworkInterfaces()
                while (interfaces.hasMoreElements()) {
                    val iface = interfaces.nextElement()
                    if (iface.isLoopback || !iface.isUp) continue
                    val addresses = iface.inetAddresses
                    while (addresses.hasMoreElements()) {
                        val addr = addresses.nextElement()
                        if (!addr.isLoopbackAddress && addr is Inet4Address) {
                            val host = addr.hostAddress ?: continue
                            candidates.add(Pair(iface.name.lowercase(), host))
                        }
                    }
                }

                // 1. Prioritize Wi-Fi or Ethernet interfaces (wlan0, eth0, ap0)
                val wifi = candidates.firstOrNull { it.first.contains("wlan") || it.first.contains("eth") || it.first.contains("ap") }
                if (wifi != null) return wifi.second

                // 2. Prioritize common private network IP ranges
                val privateRange = candidates.firstOrNull {
                    it.second.startsWith("192.168.") || it.second.startsWith("10.") || it.second.startsWith("172.")
                }
                if (privateRange != null) return privateRange.second

                if (candidates.isNotEmpty()) return candidates.first().second
            } catch (_: Exception) {
            }
            return "127.0.0.1"
        }
    }

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri
        val method = session.method
        val headers = session.headers

        // Handle CORS Preflight
        if (method == Method.OPTIONS) {
            val response = newFixedLengthResponse(Response.Status.OK, "text/plain", "")
            addCorsHeaders(response)
            return response
        }

        val normUri = when {
            uri.startsWith("/stremio") -> uri.removePrefix("/stremio")
            uri.startsWith("/emby") -> uri.removePrefix("/emby")
            else -> uri
        }

        val response = try {
            when {
                // 1. Stremio Addon Endpoints (support both /manifest.json and /stremio/manifest.json)
                normUri == "/manifest.json" || normUri == "" || normUri == "/" -> handleManifest()
                normUri.startsWith("/catalog/") -> handleCatalog(normUri)
                normUri.startsWith("/meta/") -> handleMeta(normUri)
                normUri.startsWith("/stream/") && !normUri.startsWith("/stream/video/") -> handleStreamDescriptor(session, normUri, headers)

                // 2. HTTP 206 Partial Content Video Streaming Engine
                normUri.startsWith("/stream/video/") -> handleVideoStream(session, normUri, headers)
                uri.startsWith("/stream/video/") -> handleVideoStream(session, uri, headers)

                // 3. Jellyfin & Emby API Emulation Endpoints (Infuse, Jellyfin TV, Swiftfin, Findroid)
                jellyfinBridge.isJellyfinUri(normUri) -> jellyfinBridge.handle(session, normUri, method, headers)
                jellyfinBridge.isJellyfinUri(uri) -> jellyfinBridge.handle(session, uri, method, headers)

                // 4. Management REST APIs for Stitch UI
                uri == "/api/status" -> handleGetStatus()
                uri == "/api/logs" -> handleGetLogs()
                uri == "/api/server/toggle" && method == Method.POST -> handleToggleServer()
                uri == "/api/auth/status" -> handleGetAuthStatus()
                uri == "/api/auth/session" && method == Method.POST -> handleImportSession(session)
                uri == "/api/auth/send-code" && method == Method.POST -> handleSendCode(session)
                uri == "/api/auth/sign-in" && method == Method.POST -> handleSignIn(session)
                uri == "/api/auth/password" && method == Method.POST -> handleVerify2FA(session)
                uri == "/api/auth/logout" && method == Method.POST -> handleLogout()
                uri == "/api/channels" -> handleGetChannels()
                uri == "/api/channels/add" && method == Method.POST -> handleAddChannel(session)
                uri == "/api/channels/toggle" && method == Method.POST -> handleToggleChannel(session)
                uri.startsWith("/api/channels/sync") && method == Method.POST -> handleSyncChannel(uri)
                uri.startsWith("/api/channels/") && method == Method.DELETE -> handleDeleteChannel(uri)
                uri.startsWith("/api/library") -> handleGetLibrary(session)
                uri == "/api/settings" && method == Method.GET -> handleGetSettings()
                uri == "/api/settings" && method == Method.POST -> handleSaveSettings(session)

                // 4. Web UI Static Files
                else -> serveStaticAsset(uri)
            }
        } catch (e: Exception) {
            val err = JsonObject().apply { addProperty("error", e.message) }
            newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "application/json", gson.toJson(err))
        }

        addCorsHeaders(response)
        return response
    }

    private fun addCorsHeaders(response: Response) {
        response.addHeader("Access-Control-Allow-Origin", "*")
        response.addHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS, HEAD")
        response.addHeader("Access-Control-Allow-Headers", "*")
        response.addHeader("Access-Control-Expose-Headers", "Content-Length, Content-Range, Accept-Ranges")
        response.addHeader("Access-Control-Max-Age", "86400")
    }

    // --- Stremio Addon Handlers ---

    private fun getAppVersionForManifest(): String {
        return try {
            val pInfo = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(context.packageName, android.content.pm.PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
            val raw = pInfo.versionName?.trim()?.removePrefix("v") ?: "1.1.0"
            when (raw.count { it == '.' }) {
                0 -> "$raw.0.0"
                1 -> "$raw.0"
                else -> raw
            }
        } catch (_: Exception) {
            "1.1.0"
        }
    }

    private fun handleManifest(): Response {
        val manifest = JsonObject().apply {
            addProperty("id", "org.noxmedia.streamer")
            addProperty("version", getAppVersionForManifest())
            addProperty("name", "NoxMedia")
            addProperty("description", "NoxMedia - Designed for Your Media")
            addProperty("logo", "http://${getLocalIpAddress()}:${config.port}/logo.png")
            add("types", gson.toJsonTree(listOf("movie", "series", "anime")))
            add("catalogs", gson.toJsonTree(listOf(
                mapOf(
                    "type" to "movie",
                    "id" to "tg_movies",
                    "name" to "NoxMedia",
                    "extra" to listOf(mapOf("name" to "search", "isRequired" to false), mapOf("name" to "skip")),
                    "extraSupported" to listOf("search", "skip")
                ),
                mapOf(
                    "type" to "series",
                    "id" to "tg_series",
                    "name" to "NoxMedia",
                    "extra" to listOf(mapOf("name" to "search", "isRequired" to false), mapOf("name" to "skip")),
                    "extraSupported" to listOf("search", "skip")
                ),
                mapOf(
                    "type" to "series",
                    "id" to "tg_anime",
                    "name" to "NoxMedia Anime",
                    "extra" to listOf(mapOf("name" to "search", "isRequired" to false), mapOf("name" to "skip")),
                    "extraSupported" to listOf("search", "skip")
                )
            )))
            add("resources", gson.toJsonTree(listOf("catalog", "meta", "stream")))
            add("idPrefixes", gson.toJsonTree(listOf("tg_", "tt", "kitsu")))
        }
        return newFixedLengthResponse(Response.Status.OK, "application/json", gson.toJson(manifest))
    }

    private fun handleCatalog(uri: String): Response {
        val isSeries = uri.contains("/series/")
        val isAnime = uri.contains("tg_anime")
        val searchQuery = if (uri.contains("search=")) {
            try {
                java.net.URLDecoder.decode(uri.substringAfter("search=").substringBefore(".json").substringBefore("&"), "UTF-8")
            } catch (_: Exception) { "" }
        } else ""

        var mediaList = tgClient.getAllMedia().filter { !it.isHidden }.filter { item ->
            val isItemSeries = item.type == "series" || item.type == "anime" || (item.season != null && item.episode != null)
            if (isAnime) {
                item.type == "anime" || item.kitsuId != null || (tgClient.getChannel(item.channelId)?.isAnime == true)
            } else if (isSeries) {
                isItemSeries
            } else {
                !isItemSeries
            }
        }

        if (searchQuery.isNotBlank()) {
            mediaList = mediaList.filter {
                it.title.contains(searchQuery, ignoreCase = true) ||
                it.fileName.contains(searchQuery, ignoreCase = true) ||
                (it.episodeTitle?.contains(searchQuery, ignoreCase = true) == true)
            }
        }

        // For series, group by title so multiple episodes show as a single series card
        val distinctShows = if (isSeries || isAnime) {
            mediaList.distinctBy { it.title.lowercase().trim() }
        } else {
            mediaList
        }

        val metasArray = distinctShows.map { item ->
            val poster = item.posterUrl ?: posterService.resolvePoster(
                item.title,
                config.customPosterUrl,
                config.customPosterApiKey,
                config.tmdbApiKey,
                config.tvdbApiKey
            )?.also { resolved ->
                item.posterUrl = resolved
            }
            val desc = item.overview ?: "${item.qualityBadge} • ${item.formattedSize}"
            val stremioType = if (isSeries || isAnime) "series" else "movie"
            val metaId = if (!item.imdbId.isNullOrBlank()) item.imdbId else item.id
            mapOf(
                "id" to metaId,
                "type" to stremioType,
                "name" to item.title,
                "poster" to (poster ?: "https://images.metahub.space/poster/medium/placeholder/img"),
                "releaseInfo" to (item.year ?: "2024"),
                "description" to desc
            )
        }

        val res = mapOf("metas" to metasArray)
        return newFixedLengthResponse(Response.Status.OK, "application/json", gson.toJson(res))
    }

    private fun handleMeta(uri: String): Response {
        val parts = uri.removeSuffix(".json").split("/")
        val id = parts.lastOrNull() ?: ""
        val cleanId = id.split(":").firstOrNull() ?: id

        val item = tgClient.getMediaById(cleanId)
            ?: tgClient.getAllMedia().find {
                it.id == cleanId ||
                (!it.imdbId.isNullOrBlank() && it.imdbId.equals(cleanId, ignoreCase = true)) ||
                (it.tmdbId != null && it.tmdbId.toString() == cleanId)
            }

        if (item == null) {
            return newFixedLengthResponse(Response.Status.NOT_FOUND, "application/json", "{\"meta\":null}")
        }

        val poster = item.posterUrl ?: posterService.resolvePoster(
            item.title,
            config.customPosterUrl,
            config.customPosterApiKey,
            config.tmdbApiKey,
            config.tvdbApiKey
        )?.also { resolved ->
            item.posterUrl = resolved
        }

        val isSeries = item.type == "series" || item.type == "anime" || (item.season != null && item.episode != null)

        val videosList = if (isSeries) {
            val episodes = tgClient.getAllMedia().filter { ep ->
                val epIsSeries = ep.type == "series" || ep.type == "anime" || (ep.season != null && ep.episode != null)
                epIsSeries && (
                    ep.title.equals(item.title, ignoreCase = true) ||
                    ep.id == item.id ||
                    (!ep.imdbId.isNullOrBlank() && ep.imdbId == item.imdbId)
                )
            }.sortedWith(compareBy({ it.season ?: 1 }, { it.episode ?: 1 }))

            episodes.map { ep ->
                val s = ep.season ?: 1
                val e = ep.episode ?: 1
                val epId = if (!ep.imdbId.isNullOrBlank()) "${ep.imdbId}:$s:$e" else "${cleanId}:$s:$e"
                mapOf(
                    "id" to epId,
                    "title" to (ep.episodeTitle ?: "Episode $e"),
                    "season" to s,
                    "episode" to e,
                    "released" to "${ep.year ?: "2024"}-01-01T00:00:00.000Z"
                )
            }
        } else null

        val metaObj = mutableMapOf<String, Any?>(
            "id" to cleanId,
            "type" to (if (isSeries) "series" else "movie"),
            "name" to item.title,
            "year" to (item.year ?: "2024"),
            "poster" to (poster ?: "https://images.metahub.space/poster/medium/placeholder/img"),
            "background" to (item.backdropUrl ?: poster ?: "https://images.metahub.space/background/medium/placeholder/img"),
            "description" to (item.overview ?: "${item.qualityBadge} • ${item.formattedSize} • ${item.formattedDuration}"),
            "genres" to listOf("Telegram", item.quality)
        )
        if (videosList != null) {
            metaObj["videos"] = videosList
        }

        val res = mapOf("meta" to metaObj)
        return newFixedLengthResponse(Response.Status.OK, "application/json", gson.toJson(res))
    }

    data class ResolvedMeta(val title: String?, val year: String?)

    private fun resolveMetaForId(imdbId: String?, kitsuId: Int?, mediaType: String): ResolvedMeta? {
        if (kitsuId != null) {
            val key = "kitsu:$kitsuId"
            val metaKeyYear = "kitsu_year:$kitsuId"
            val cachedTitle = idTitleCache[key]
            if (cachedTitle != null) {
                return ResolvedMeta(cachedTitle, idTitleCache[metaKeyYear])
            }
            try {
                val url = "https://kitsu.io/api/edge/anime/$kitsuId"
                val req = Request.Builder().url(url).header("User-Agent", "NoxMedia/1.0").build()
                streamHttpClient.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val body = resp.body?.string() ?: return null
                        val root = JsonParser.parseString(body).asJsonObject
                        val title = root.getAsJsonObject("data")
                            ?.getAsJsonObject("attributes")
                            ?.get("canonicalTitle")?.asString
                        val year = root.getAsJsonObject("data")
                            ?.getAsJsonObject("attributes")
                            ?.get("startDate")?.asString?.split("-")?.firstOrNull()
                        if (!title.isNullOrBlank()) {
                            idTitleCache[key] = title
                            if (year != null) idTitleCache[metaKeyYear] = year
                            return ResolvedMeta(title, year)
                        }
                    }
                }
            } catch (_: Exception) {}
        } else if (!imdbId.isNullOrBlank() && imdbId.startsWith("tt", ignoreCase = true)) {
            val key = "imdb:$imdbId"
            val metaKeyYear = "imdb_year:$imdbId"
            val cachedTitle = idTitleCache[key]
            if (cachedTitle != null) {
                return ResolvedMeta(cachedTitle, idTitleCache[metaKeyYear])
            }
            try {
                val cType = if (mediaType == "movie") "movie" else "series"
                val url = "https://v3-cinemeta.strem.io/meta/$cType/$imdbId.json"
                val req = Request.Builder().url(url).header("User-Agent", "Mozilla/5.0").build()
                streamHttpClient.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val body = resp.body?.string() ?: return null
                        val root = JsonParser.parseString(body).asJsonObject
                        val metaObj = root.getAsJsonObject("meta")
                        val title = metaObj?.get("name")?.asString
                        val rawYear = metaObj?.get("year")?.asString ?: metaObj?.get("releaseInfo")?.asString
                        val year = Regex("(?:^|[^0-9])((?:19|20)\\d{2})(?:[^0-9]|$)").find(rawYear ?: "")?.groupValues?.get(1)
                        if (!title.isNullOrBlank()) {
                            idTitleCache[key] = title
                            if (year != null) idTitleCache[metaKeyYear] = year
                            return ResolvedMeta(title, year)
                        }
                    }
                }
            } catch (_: Exception) {}
        }
        return null
    }

    fun clearTitleCache() {
        idTitleCache.clear()
    }

    private fun cleanTitleForMatch(t: String): String {
        return t.lowercase()
            .replace(Regex("(?i)\\.(mkv|mp4|avi|mov|webm)$"), "")
            .replace(Regex("(?i)(2160p|1080p|720p|480p|4k|uhd|hdr|hevc|x264|x265|aac|dts|bluray|web-dl|webrip|proper|repack)"), "")
            .replace(Regex("[^a-z0-9]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun handleStreamDescriptor(session: IHTTPSession, uri: String, headers: Map<String, String>): Response {
        val parts = uri.removeSuffix(".json").split("/")
        val rawId = parts.lastOrNull() ?: ""

        val isKitsu = rawId.startsWith("kitsu:", ignoreCase = true)
        val idParts = rawId.split(":")

        val kitsuId: Int?
        val imdbId: String?
        val targetSeason: Int?
        val targetEpisode: Int?

        if (isKitsu) {
            imdbId = null
            kitsuId = idParts.getOrNull(1)?.toIntOrNull()
            if (idParts.size == 3) {
                targetSeason = 1
                targetEpisode = idParts.getOrNull(2)?.toIntOrNull()
            } else if (idParts.size >= 4) {
                targetSeason = idParts.getOrNull(2)?.toIntOrNull()
                targetEpisode = idParts.getOrNull(3)?.toIntOrNull()
            } else {
                targetSeason = null
                targetEpisode = null
            }
        } else {
            kitsuId = null
            imdbId = idParts.getOrNull(0) ?: rawId
            targetSeason = idParts.getOrNull(1)?.toIntOrNull()
            targetEpisode = idParts.getOrNull(2)?.toIntOrNull()
        }

        val allMedia = tgClient.getAllMedia().filter { !it.isHidden }

        // 1. Direct ID matching (IMDb ID, Kitsu ID, TMDB ID, local tg_ ID)
        var matchedItems = allMedia.filter { item ->
            when {
                // Exact item ID match (e.g. tg_-1004424883082_2)
                item.id == rawId || item.id == (imdbId ?: "") || item.id == idParts.getOrNull(0) -> {
                    if (targetSeason != null && targetEpisode != null) {
                        (item.season == null || item.season == targetSeason) && (item.episode == null || item.episode == targetEpisode)
                    } else if (targetEpisode != null) {
                        (item.episode == null || item.episode == targetEpisode)
                    } else true
                }

                // Exact Kitsu ID match
                isKitsu && kitsuId != null && item.kitsuId == kitsuId -> {
                    if (targetSeason != null && targetEpisode != null) {
                        (item.season == null || item.season == targetSeason) && (item.episode == null || item.episode == targetEpisode)
                    } else if (targetEpisode != null) {
                        (item.episode == null || item.episode == targetEpisode)
                    } else true
                }

                // Exact IMDb ID match (e.g. tt4644488, tt39395275)
                !item.imdbId.isNullOrBlank() && !imdbId.isNullOrBlank() && item.imdbId.equals(imdbId, ignoreCase = true) -> {
                    if (targetSeason != null && targetEpisode != null) {
                        (item.season == null || item.season == targetSeason) && item.episode == targetEpisode
                    } else if (targetEpisode != null) {
                        item.episode == targetEpisode
                    } else {
                        item.type == "movie" || (item.season == null && item.episode == null)
                    }
                }

                // Exact TMDB ID match
                item.tmdbId != null && !imdbId.isNullOrBlank() && (item.tmdbId.toString() == imdbId || item.tmdbId.toString() == imdbId.removePrefix("tmdb:").removePrefix("TMDB:")) -> {
                    if (targetSeason != null && targetEpisode != null) {
                        (item.season == null || item.season == targetSeason) && item.episode == targetEpisode
                    } else if (targetEpisode != null) {
                        item.episode == targetEpisode
                    } else {
                        item.type == "movie" || (item.season == null && item.episode == null)
                    }
                }

                else -> false
            }
        }

        // 2. Title-based resolution if direct ID didn't match (resolves IMDb/Kitsu ID to official title and matches strictly)
        if (matchedItems.isEmpty()) {
            val mediaType = parts.getOrNull(2) ?: "series"
            val resolved = resolveMetaForId(imdbId, kitsuId, mediaType)
            val expectedTitle = resolved?.title
            val expectedYear = resolved?.year
            if (!expectedTitle.isNullOrBlank()) {
                val cleanExpected = cleanTitleForMatch(expectedTitle)
                if (cleanExpected.isNotBlank()) {
                    matchedItems = allMedia.filter { item ->
                        // A. If item already has a verified IMDb ID or TMDB ID that belongs to another show, NEVER match it via title fallback!
                        if (!item.imdbId.isNullOrBlank() && !imdbId.isNullOrBlank() && !item.imdbId.equals(imdbId, ignoreCase = true)) {
                            return@filter false
                        }
                        if (item.tmdbId != null && !imdbId.isNullOrBlank() && (imdbId.startsWith("tmdb:", ignoreCase = true) || imdbId.all { it.isDigit() })) {
                            val targetTmdb = imdbId.removePrefix("tmdb:").removePrefix("TMDB:").toIntOrNull()
                            if (targetTmdb != null && item.tmdbId != targetTmdb) {
                                return@filter false
                            }
                        }

                        // B. Strict title match (Do NOT use startsWith which mixes up spinoffs like "Ben 10" with "Ben 10: Omniverse" or "Ben 10: Alien Force")
                        val cleanItem = cleanTitleForMatch(item.title)
                        val titleMatches = cleanItem == cleanExpected
                        if (!titleMatches) return@filter false

                        // C. Strict year verification if both years are known
                        if (!expectedYear.isNullOrBlank() && !item.year.isNullOrBlank()) {
                            val itemYearClean = Regex("(?:^|[^0-9])((?:19|20)\\d{2})(?:[^0-9]|$)").find(item.year!!)?.groupValues?.get(1)
                            if (itemYearClean != null && itemYearClean != expectedYear) {
                                return@filter false
                            }
                        }

                        if (targetSeason != null && targetEpisode != null) {
                            (item.season == null || item.season == targetSeason) && item.episode == targetEpisode
                        } else if (targetEpisode != null) {
                            item.episode == targetEpisode
                        } else {
                            item.type == "movie" || (item.season == null && item.episode == null)
                        }
                    }
                }
            }
        }

        if (matchedItems.isEmpty()) {
            return newFixedLengthResponse(Response.Status.OK, "application/json", "{\"streams\":[]}")
        }

        // Deduplicate streams:
        // 1. Identify all messageIds that are parts of split items, and all split file clean base names
        val splitPartMessageIds = allMedia.filter { it.isSplit }.flatMap { it.parts.map { p -> p.messageId } }.toSet()
        val splitCleanBases = allMedia.filter { it.isSplit }.map {
            it.fileName.lowercase()
                .replace(Regex("""[\s._-]*(?:part|pt)[\s._-]*\d+.*""", RegexOption.IGNORE_CASE), "")
                .replace(Regex("""\.(mkv|mp4|avi|webm|ts|m4v)(\.\d{2,4})?$""", RegexOption.IGNORE_CASE), "")
                .trim()
        }.filter { it.isNotBlank() }.toSet()

        // 2. Filter out any standalone items that are just parts of a split item
        var finalItems = matchedItems.filter { item ->
            if (item.isSplit) return@filter true

            // If messageId is part of a split item
            if (splitPartMessageIds.contains(item.messageId)) return@filter false

            val cleanName = item.fileName.lowercase()
                .replace(Regex("""[\s._-]*(?:part|pt)[\s._-]*\d+.*""", RegexOption.IGNORE_CASE), "")
                .replace(Regex("""\.(mkv|mp4|avi|webm|ts|m4v)(\.\d{2,4})?$""", RegexOption.IGNORE_CASE), "")
                .trim()
            if (splitCleanBases.contains(cleanName)) {
                if (item.fileName.contains(Regex("""(?i)(?:part|pt|\.\d{2,4})"""))) return@filter false
                val splitMatch = allMedia.find {
                    it.isSplit && (it.channelId == item.channelId || it.channelId == 0L || item.channelId == 0L) &&
                    it.season == item.season && it.episode == item.episode
                }
                if (splitMatch != null && item.sizeBytes < splitMatch.sizeBytes) return@filter false
            }

            true
        }

        // 3. Skip Exact Duplicate Files if enabled (or deduplicate same file releases)
        val shouldSkipDuplicates = config.skipExactDuplicates ||
                com.tgserver.streamer.service.StreamerForegroundService.activeConfig.skipExactDuplicates
        if (shouldSkipDuplicates) {
            val seen = mutableSetOf<String>()
            val deduped = mutableListOf<MediaItem>()
            // Sort so split items and larger sizes come first
            val sorted = finalItems.sortedWith(compareByDescending<MediaItem> { it.isSplit }.thenByDescending { it.sizeBytes })
            for (item in sorted) {
                val cleanKey = item.fileName.lowercase()
                    .replace(Regex("""\.(mkv|mp4|avi|webm)(\.\d{2,3})?$""", RegexOption.IGNORE_CASE), "")
                    .replace(Regex("""[\s._-]+"""), " ")
                    .trim()
                val normQuality = when {
                    item.quality.contains("4k", ignoreCase = true) || item.quality.contains("2160", ignoreCase = true) -> "4k"
                    item.quality.contains("1080", ignoreCase = true) -> "1080p"
                    item.quality.contains("720", ignoreCase = true) -> "720p"
                    item.quality.contains("480", ignoreCase = true) -> "480p"
                    else -> item.quality.lowercase().trim()
                }
                val groupKey = "${cleanKey}_${normQuality}_${item.season ?: 0}_${item.episode ?: 0}"
                if (seen.add(groupKey)) {
                    deduped.add(item)
                }
            }
            finalItems = deduped
        }

        val userAgent = headers["user-agent"] ?: headers["User-Agent"] ?: ""
        val platform = ClientPlatformDetector.detect(userAgent)
        StreamLog.i(TAG, "[$platform] Stream descriptor requested: $uri | UA: $userAgent")

        val localIp = getLocalIpAddress()
        val streams = finalItems.map { item ->
            val rawFileName = if (item.fileName.isNotBlank()) item.fileName else "${item.title}.mkv"
            val safeFileName = try {
                java.net.URLEncoder.encode(rawFileName, "UTF-8").replace("+", "%20")
            } catch (_: Exception) {
                "video.mkv"
            }
            val streamUrl = "http://$localIp:${config.port}/stream/video/${item.channelId}/${item.id}/$safeFileName"
            val (streamName, streamTitle) = formatStreamItem(item, rawFileName)

            val behaviorHints = if (platform == ClientPlatform.IOS) {
                getIosBehaviorHints(item, rawFileName)
            } else {
                getAndroidBehaviorHints(item, rawFileName)
            }

            mapOf(
                "name" to streamName,
                "title" to streamTitle,
                "url" to streamUrl,
                "behaviorHints" to behaviorHints
            )
        }

        val res = mapOf("streams" to streams)
        return newFixedLengthResponse(Response.Status.OK, "application/json", gson.toJson(res))
    }

    private fun getAndroidBehaviorHints(item: MediaItem, rawFileName: String): MutableMap<String, Any> {
        // Android ExoPlayer natively handles MKV and progressive HTTP 206 directly.
        return mutableMapOf(
            "notWebReady" to false,
            "videoSize" to item.sizeBytes,
            "filename" to rawFileName
        )
    }

    private fun getIosBehaviorHints(item: MediaItem, rawFileName: String): MutableMap<String, Any> {
        // Just like on Android TV, notWebReady: false enables direct progressive HTTP 206 streaming
        // without triggering Nuvio/Stremio's internal proxy cache pre-fetching the entire file.
        return mutableMapOf(
            "notWebReady" to false,
            "videoSize" to item.sizeBytes,
            "filename" to rawFileName
        )
    }

    private fun formatStreamItem(item: MediaItem, rawFileName: String): Pair<String, String> {
        val fn = rawFileName
        val fnLower = fn.lowercase()
        val qLower = item.quality.lowercase()

        // 1. Resolution & Badge
        val (normRes, badge) = when {
            fnLower.contains("2160p") || fnLower.contains("4k") || fnLower.contains("uhd") || qLower.contains("4k") || qLower.contains("2160p") -> 
                "2160p" to "4K UltraHD"
            fnLower.contains("1440p") || fnLower.contains("2k") || qLower.contains("1440p") || qLower.contains("2k") -> 
                "1440p" to "2K"
            fnLower.contains("1080p") || fnLower.contains("fhd") || qLower.contains("1080p") || qLower.contains("fhd") -> 
                "1080p" to "FHD"
            fnLower.contains("720p") || fnLower.contains("hd") || qLower.contains("720p") || qLower.contains("hd") -> 
                "720p" to "HD+"
            fnLower.contains("480p") || fnLower.contains("sd") || qLower.contains("480p") || qLower.contains("sd") -> 
                "480p" to "SD"
            else -> 
                (if (item.quality.isNotBlank()) item.quality else "Unknown") to (if (item.qualityBadge.isNotBlank()) item.qualityBadge else "Unknown")
        }
        val streamName = "⌁ NoxMedia • $badge"

        // 2. Line 1: Title, Clean Year, Clean Season & Episode
        val rawYear = if (!item.year.isNullOrBlank()) {
            Regex("(?:^|[^0-9])((?:19|20)\\d{2})(?:[^0-9]|$)").find(item.year!!)?.groupValues?.get(1)
                ?: item.year!!.trim().removeSuffix("-")
        } else {
            val yMatch = Regex("(?:^|[^0-9])((?:19|20)\\d{2})(?:[^0-9]|$)").find(fn)
            yMatch?.groupValues?.get(1)
        }
        val yearStr = if (!rawYear.isNullOrBlank()) " ($rawYear)" else ""

        val epStr = if (item.season != null && item.episode != null) {
            val baseEp = " • S%02d E%02d".format(item.season, item.episode)
            if (!item.episodeTitle.isNullOrBlank()) "$baseEp - ${item.episodeTitle}" else baseEp
        } else ""

        val titleDisplay = if (item.title.isNotBlank()) item.title else rawFileName.substringBeforeLast(".")
        val line1 = "Ϟ $titleDisplay$yearStr$epStr"

        // 3. Line 2: Languages
        val langMap = listOf(
            "HIN" to Regex("(?i)(?:^|[^a-zA-Z])(hin|hindi)(?:[^a-zA-Z]|$)"),
            "TAM" to Regex("(?i)(?:^|[^a-zA-Z])(tam|tamil)(?:[^a-zA-Z]|$)"),
            "TEL" to Regex("(?i)(?:^|[^a-zA-Z])(tel|telugu)(?:[^a-zA-Z]|$)"),
            "KAN" to Regex("(?i)(?:^|[^a-zA-Z])(kan|kannada)(?:[^a-zA-Z]|$)"),
            "MAL" to Regex("(?i)(?:^|[^a-zA-Z])(mal|malayalam)(?:[^a-zA-Z]|$)"),
            "ENG" to Regex("(?i)(?:^|[^a-zA-Z])(eng|english)(?:[^a-zA-Z]|$)"),
            "JAP" to Regex("(?i)(?:^|[^a-zA-Z])(jap|japanese|jpn)(?:[^a-zA-Z]|$)"),
            "KOR" to Regex("(?i)(?:^|[^a-zA-Z])(kor|korean)(?:[^a-zA-Z]|$)"),
            "SPA" to Regex("(?i)(?:^|[^a-zA-Z])(spa|spanish)(?:[^a-zA-Z]|$)"),
            "FRE" to Regex("(?i)(?:^|[^a-zA-Z])(fre|french)(?:[^a-zA-Z]|$)"),
            "GER" to Regex("(?i)(?:^|[^a-zA-Z])(ger|german)(?:[^a-zA-Z]|$)"),
            "ITA" to Regex("(?i)(?:^|[^a-zA-Z])(ita|italian)(?:[^a-zA-Z]|$)"),
            "RUS" to Regex("(?i)(?:^|[^a-zA-Z])(rus|russian)(?:[^a-zA-Z]|$)"),
            "CHI" to Regex("(?i)(?:^|[^a-zA-Z])(chi|chinese)(?:[^a-zA-Z]|$)"),
            "MULTI" to Regex("(?i)(?:^|[^a-zA-Z])(multi|dual)(?:[^a-zA-Z]|$)")
        )
        val detectedLangs = langMap.filter { it.second.containsMatchIn(fn) }.map { it.first }
        val line2 = if (detectedLangs.isNotEmpty()) "Ϟ 🎧 ${detectedLangs.joinToString(" | ")}" else null

        // 4. Line 3: Audio Tags & Channels
        val audioTags = mutableListOf<String>()
        if (Regex("(?i)atmos").containsMatchIn(fn)) audioTags.add("Atmos")
        if (Regex("(?i)(ddp|dd\\+|eac3)").containsMatchIn(fn)) audioTags.add("DDP")
        else if (Regex("(?i)(ac3|\\bdd\\b)").containsMatchIn(fn)) audioTags.add("DD")
        if (Regex("(?i)truehd").containsMatchIn(fn)) audioTags.add("TrueHD")
        if (Regex("(?i)dts-hd").containsMatchIn(fn)) audioTags.add("DTS-HD")
        else if (Regex("(?i)dts").containsMatchIn(fn)) audioTags.add("DTS")
        if (Regex("(?i)\\baac\\b").containsMatchIn(fn)) audioTags.add("AAC")
        if (Regex("(?i)\\bflac\\b").containsMatchIn(fn)) audioTags.add("FLAC")
        if (Regex("(?i)\\bopus\\b").containsMatchIn(fn)) audioTags.add("Opus")

        val chMatch = Regex("(7\\.1|5\\.1|2\\.0)").find(fn)
        val channels = chMatch?.value ?: ""
        val line3 = if (audioTags.isNotEmpty() || channels.isNotBlank()) {
            val atStr = audioTags.joinToString(" · ")
            val combo = listOf(atStr, channels).filter { it.isNotBlank() }.joinToString(" ")
            "Ϟ ◗◖ $combo"
        } else null

        // 5. Line 4: Resolution, Source/Quality, Visual Tags, Bitrate
        val srcMatch = Regex("(?i)\\b(web-?rip|web-?dl|bluray|blu-ray|bdrip|brrip|hdtv|dvdrip|remux)\\b").find(fn)
        val sourceQuality = srcMatch?.value?.uppercase()?.replace("-", "") ?: ""

        val visualTags = mutableListOf<String>()
        if (Regex("(?i)\\bimax\\b").containsMatchIn(fn)) visualTags.add("IMAX")
        if (Regex("(?i)(dolby\\s*vision|\\bdv\\b|\\bdovi\\b)").containsMatchIn(fn)) visualTags.add("DV")
        if (Regex("(?i)\\bhdr10\\+\\b").containsMatchIn(fn)) visualTags.add("HDR10+")
        else if (Regex("(?i)\\bhdr10\\b").containsMatchIn(fn)) visualTags.add("HDR10")
        else if (Regex("(?i)\\bhlg\\b").containsMatchIn(fn)) visualTags.add("HLG")
        else if (Regex("(?i)\\bhdr\\b").containsMatchIn(fn)) visualTags.add("HDR")
        else if (Regex("(?i)\\bsdr\\b").containsMatchIn(fn)) visualTags.add("SDR")

        if (Regex("(?i)\\b10-?bit\\b").containsMatchIn(fn)) visualTags.add("10bit")
        if (Regex("(?i)(hevc|x265|h\\.?265)").containsMatchIn(fn)) visualTags.add("H.265")
        else if (Regex("(?i)(avc|x264|h\\.?264)").containsMatchIn(fn)) visualTags.add("H.264")
        else if (Regex("(?i)\\bav1\\b").containsMatchIn(fn)) visualTags.add("AV1")

        val visPart = if (visualTags.isNotEmpty()) "🔮 ${visualTags.joinToString(" · ")}" else ""

        // Dynamic runtime resolution for Bitrate
        var effectiveDurSec = item.durationSeconds
        if (effectiveDurSec <= 0) {
            effectiveDurSec = com.tgserver.streamer.metadata.MetadataEnricher.resolveRuntimeSeconds(item)
        }
        if (effectiveDurSec <= 0) {
            // Check for explicit duration in filename/tags
            val durMatch = Regex("(?i)(?:^|[^0-9])(\\d{1,2})h(?:our)?\\s*(\\d{1,2})m(?:in)?(?:[^a-zA-Z]|$)").find(fn)
            if (durMatch != null) {
                val h = durMatch.groupValues[1].toIntOrNull() ?: 0
                val m = durMatch.groupValues[2].toIntOrNull() ?: 0
                effectiveDurSec = (h * 3600) + (m * 60)
            } else {
                val minMatch = Regex("(?i)(?:^|[^0-9])(\\d{2,3})\\s*min(?:ute)?s?(?:[^a-zA-Z]|$)").find(fn)
                if (minMatch != null) {
                    val m = minMatch.groupValues[1].toIntOrNull() ?: 0
                    effectiveDurSec = m * 60
                }
            }
        }

        var explicitMbps: Double? = null
        val brMatch = Regex("(?i)(?:^|[^0-9])(\\d+(?:\\.\\d+)?)\\s*(?:mbps|mb/s)(?:[^a-zA-Z]|$)").find(fn)
        if (brMatch != null) {
            explicitMbps = brMatch.groupValues[1].toDoubleOrNull()
        }

        val bitratePart = when {
            explicitMbps != null && explicitMbps >= 0.1 -> {
                "🎡 %.1f Mbps".format(java.util.Locale.US, explicitMbps)
            }
            effectiveDurSec > 60 && item.sizeBytes > 0 -> {
                val mbps = (item.sizeBytes.toDouble() * 8.0) / (effectiveDurSec * 1_000_000.0)
                if (mbps >= 0.1) "🎡 %.1f Mbps".format(java.util.Locale.US, mbps) else ""
            }
            else -> ""
        }

        val resDisplay = if (normRes != "N/A") normRes else item.quality
        val resAndSource = listOf(resDisplay, sourceQuality).filter { it.isNotBlank() }.joinToString(" ")
        val line4Elements = mutableListOf<String>()
        if (resAndSource.isNotBlank()) line4Elements.add("🍿 $resAndSource")
        if (visPart.isNotBlank()) line4Elements.add(visPart)
        if (bitratePart.isNotBlank()) line4Elements.add(bitratePart)
        val line4 = "Ϟ " + line4Elements.joinToString(" ")

        // 6. Line 5: Size in GiB
        val gib = item.sizeBytes.toDouble() / (1024.0 * 1024.0 * 1024.0)
        val sizeStr = if (gib >= 1.0) {
            String.format(java.util.Locale.US, "%.1f GiB", gib)
        } else {
            val mib = item.sizeBytes.toDouble() / (1024.0 * 1024.0)
            String.format(java.util.Locale.US, "%.1f MiB", mib)
        }
        val line5 = "Ϟ 🗃️ $sizeStr"

        // 7. Line 6: Release Group
        val rgMatch = Regex("[\\(\\[]([A-Za-z0-9_\\-\\s]+)[\\)\\]](?:\\.[a-zA-Z0-9]+)?$").find(fn)
        val candidateRg = rgMatch?.groupValues?.getOrNull(1)?.trim()
        val ignoreWords = setOf("1080p", "720p", "2160p", "4k", "mkv", "mp4", "x264", "x265", "hevc")
        val releaseGroup = if (!candidateRg.isNullOrBlank() && candidateRg.lowercase() !in ignoreWords) {
            candidateRg
        } else {
            val knownGroups = listOf("MomStealer", "Anipulse", "Judas", "SubsPlease", "Erai-raws", "YTS", "PSA", "TGx", "Pahe", "GalaxyRG")
            knownGroups.firstOrNull { Regex("(?i)\\b$it\\b").containsMatchIn(fn) }
        }
        val line6 = if (!releaseGroup.isNullOrBlank()) "Ϟ 🧣 $releaseGroup" else null

        val fullTitle = listOfNotNull(line1, line2, line3, line4, line5, line6).joinToString("\n")
        return Pair(streamName, fullTitle)
    }

    // --- Video Streaming Engine (HTTP 206 Partial Content) ---

    private fun handleVideoStream(session: IHTTPSession, uri: String, headers: Map<String, String>): Response {
        val parts = uri.split("/")
        // uri format: /stream/video/<channelId>/<targetParam> or /stream/video/<channelId>/<targetParam>/<filename>
        if (parts.size < 5) {
            return newFixedLengthResponse(Response.Status.BAD_REQUEST, "text/plain", "Invalid stream path")
        }

        val channelId = parts[3].toLongOrNull() ?: 0L
        val targetParam = parts[4]
        // targetParam is now an item.id (e.g. "tg_-1004424883082_2") NOT a raw integer.
        // Extract numeric messageId from targetParam (e.g. tg_-100xxx_123_split -> 123, or raw integer)
        val targetParamAsInt = targetParam.toIntOrNull()
            ?: targetParam.removePrefix("tg_").removeSuffix("_split").substringAfterLast("_").toIntOrNull()
            ?: 0
        val fileName = if (parts.size >= 6) {
            try { java.net.URLDecoder.decode(parts[5], "UTF-8") } catch (_: Exception) { parts[5] }
        } else "video.mp4"

        val allMedia = tgClient.getAllMedia()
        // Priority: match by item.id first (new URL format), then fall back to messageId (legacy)
        val media = allMedia.find { it.id == targetParam }
            ?: allMedia.find { it.channelId == channelId && it.id == targetParam }
            // Prefer split items over standalone parts if multiple exist
            ?: (if (targetParamAsInt > 0) allMedia.find {
                (it.channelId == channelId || channelId == 0L) && it.isSplit &&
                (it.messageId == targetParamAsInt || it.parts.any { p -> p.messageId == targetParamAsInt })
            } else null)
            ?: (if (targetParamAsInt > 0) allMedia.find {
                ((it.channelId == channelId || channelId == 0L) && it.messageId == targetParamAsInt) || (it.messageId == targetParamAsInt)
            } else null)
            ?: tgClient.getUnmatchedMedia().find {
                it.id == targetParam || ((it.channelId == channelId || channelId == 0L) && it.messageId == targetParamAsInt)
            }?.parsedMediaItem

        // Use the real messageId from the resolved media item, NOT from URL parsing
        val resolvedMessageId = media?.messageId ?: targetParamAsInt
        val effectiveChannelId = media?.channelId ?: channelId

        val totalSize = media?.sizeBytes ?: 50_000_000L
        val mime = if (fileName.contains(".mkv", ignoreCase = true) || uri.contains(".mkv", ignoreCase = true)) {
            "video/x-matroska"
        } else {
            media?.mimeType ?: "video/mp4"
        }

        val rangeHeader = headers["range"] ?: headers["Range"]
        val userAgent = headers["user-agent"] ?: headers["User-Agent"] ?: "unknown"
        val clientIp = session.remoteIpAddress ?: "unknown"

        val platform = ClientPlatformDetector.detect(userAgent)
        StreamLog.i(TAG, "[$platform-Stream] Incoming Video Stream [${session.method}] $uri | Range: ${rangeHeader ?: "none"} | UA: $userAgent | Client: $clientIp")
        StreamLog.i(TAG, "[$platform-Stream] Resolved target: file='$fileName', size=$totalSize bytes (${StreamTrafficStats.formatBytes(totalSize)}), isSplit=${media?.isSplit}, parts=${media?.parts?.size ?: 1}, target=$targetParam, msgId=$resolvedMessageId, ch=$effectiveChannelId")

        return when (platform) {
            ClientPlatform.IOS -> handleVideoStreamIos(
                session, uri, headers, media, targetParam, resolvedMessageId,
                effectiveChannelId, fileName, totalSize, mime, rangeHeader, userAgent, clientIp
            )
            else -> handleVideoStreamAndroid(
                session, uri, headers, media, targetParam, resolvedMessageId,
                effectiveChannelId, fileName, totalSize, mime, rangeHeader, userAgent, clientIp
            )
        }
    }

    /**
     * Preserved Android Stream Engine:
     * Confirmed instant and smooth playback on ExoPlayer / Android TV. 100% frozen.
     */
    private fun handleVideoStreamAndroid(
        session: IHTTPSession,
        uri: String,
        headers: Map<String, String>,
        media: MediaItem?,
        targetParam: String,
        resolvedMessageId: Int,
        effectiveChannelId: Long,
        fileName: String,
        totalSize: Long,
        mime: String,
        rangeHeader: String?,
        userAgent: String,
        clientIp: String
    ): Response {
        var startByte = 0L
        var endByte = totalSize - 1L

        if (!rangeHeader.isNullOrBlank() && rangeHeader.startsWith("bytes=")) {
            val rangeVal = rangeHeader.removePrefix("bytes=").substringBefore(',').trim()
            val dashIndex = rangeVal.indexOf('-')
            if (dashIndex != -1) {
                val startStr = rangeVal.substring(0, dashIndex).trim()
                val endStr = rangeVal.substring(dashIndex + 1).trim()
                if (startStr.isEmpty()) {
                    val suffixLen = endStr.toLongOrNull() ?: 0L
                    if (suffixLen > 0L) {
                        startByte = maxOf(0L, totalSize - suffixLen)
                        endByte = totalSize - 1L
                    }
                } else {
                    startByte = startStr.toLongOrNull() ?: 0L
                    if (endStr.isNotEmpty()) {
                        val parsedEnd = endStr.toLongOrNull()
                        if (parsedEnd != null && parsedEnd in startByte until totalSize) {
                            endByte = parsedEnd
                        }
                    }
                }
            }
        }

        // Handle HEAD requests immediately without starting Pyrogram stream
        if (session.method == Method.HEAD) {
            val reqLen = endByte - startByte + 1
            StreamLog.i(TAG, "[Android-Stream] Serving HEAD response: status=${if (rangeHeader != null) "206 Partial Content" else "200 OK"}, length=${if (rangeHeader != null) reqLen else totalSize}")
            val response = if (rangeHeader != null) {
                newFixedLengthResponse(Response.Status.PARTIAL_CONTENT, mime, "").apply {
                    addHeader("Content-Range", "bytes $startByte-$endByte/$totalSize")
                    addHeader("Content-Length", "$reqLen")
                }
            } else {
                newFixedLengthResponse(Response.Status.OK, mime, "").apply {
                    addHeader("Content-Length", "$totalSize")
                }
            }
            response.addHeader("Accept-Ranges", "bytes")
            response.addHeader("Connection", "keep-alive")
            response.addHeader("Content-Disposition", "inline; filename=\"$fileName\"")
            return response
        }

        val requestedLength = endByte - startByte + 1
        StreamLog.i(TAG, "[Android-Stream] Requesting stream source from Telegram: range=$startByte-$endByte/$totalSize (len=$requestedLength bytes / ${StreamTrafficStats.formatBytes(requestedLength)})")
        val t0 = System.currentTimeMillis()
        val chunk = try {
            tgClient.getStreamSource(effectiveChannelId, resolvedMessageId, startByte, endByte, mediaId = targetParam, platform = ClientPlatform.ANDROID)
        } catch (e: Exception) {
            StreamLog.e(TAG, "[Android-Stream] Stream error for $uri: ${e.message}", e)
            val isAuthErr = e.message?.contains("AUTH_KEY_UNREGISTERED", ignoreCase = true) == true ||
                            e.message?.contains("logged out or expired", ignoreCase = true) == true
            return newFixedLengthResponse(
                if (isAuthErr) Response.Status.UNAUTHORIZED else Response.Status.INTERNAL_ERROR,
                "text/plain",
                "Stream error: ${e.message}"
            )
        }
        val elapsed = System.currentTimeMillis() - t0

        StreamLog.i(TAG, "[Android-Stream] Serving Video Stream [${if (rangeHeader != null) "206 Partial Content" else "200 OK"}]: range=${chunk.startByte}-${chunk.endByte}/${chunk.totalSize}, len=${chunk.contentLength} (${StreamTrafficStats.formatBytes(chunk.contentLength)}), mime=$mime (prep: ${elapsed}ms)")

        val response = if (rangeHeader != null) {
            newFixedLengthResponse(
                Response.Status.PARTIAL_CONTENT,
                mime,
                chunk.inputStream,
                chunk.contentLength
            ).apply {
                addHeader("Content-Range", "bytes ${chunk.startByte}-${chunk.endByte}/${chunk.totalSize}")
            }
        } else {
            newFixedLengthResponse(
                Response.Status.OK,
                mime,
                chunk.inputStream,
                chunk.totalSize
            )
        }

        response.addHeader("Accept-Ranges", "bytes")
        response.addHeader("Connection", "keep-alive")
        response.addHeader("Content-Disposition", "inline; filename=\"$fileName\"")
        return response
    }

    /**
     * Isolated iOS Stream Engine:
     * Specific tuning and diagnostics for iOS/Apple players without affecting Android.
     */
    private fun handleVideoStreamIos(
        session: IHTTPSession,
        uri: String,
        headers: Map<String, String>,
        media: MediaItem?,
        targetParam: String,
        resolvedMessageId: Int,
        effectiveChannelId: Long,
        fileName: String,
        totalSize: Long,
        mime: String,
        rangeHeader: String?,
        userAgent: String,
        clientIp: String
    ): Response {
        StreamLog.i(TAG, "[iOS-Stream] >>> Incoming iOS Video Request: [${session.method}] $uri")
        StreamLog.i(TAG, "[iOS-Stream] Range: '${rangeHeader ?: "none"}', UA: '$userAgent', Client: '$clientIp'")

        // Log non-range headers for deep Apple handshake diagnostics
        for ((k, v) in headers) {
            if (!k.equals("range", ignoreCase = true) && !k.equals("user-agent", ignoreCase = true)) {
                StreamLog.d(TAG, "[iOS-Stream] Header: $k = $v")
            }
        }

        var startByte = 0L
        var endByte = totalSize - 1L

        if (!rangeHeader.isNullOrBlank() && rangeHeader.startsWith("bytes=")) {
            val rangeVal = rangeHeader.removePrefix("bytes=").substringBefore(',').trim()
            val dashIndex = rangeVal.indexOf('-')
            if (dashIndex != -1) {
                val startStr = rangeVal.substring(0, dashIndex).trim()
                val endStr = rangeVal.substring(dashIndex + 1).trim()
                if (startStr.isEmpty()) {
                    val suffixLen = endStr.toLongOrNull() ?: 0L
                    if (suffixLen > 0L) {
                        startByte = maxOf(0L, totalSize - suffixLen)
                        endByte = totalSize - 1L
                    }
                } else {
                    startByte = startStr.toLongOrNull() ?: 0L
                    if (endStr.isNotEmpty()) {
                        val parsedEnd = endStr.toLongOrNull()
                        if (parsedEnd != null && parsedEnd in startByte until totalSize) {
                            endByte = parsedEnd
                        }
                    }
                }
            }
        }

        if (session.method == Method.HEAD) {
            val reqLen = endByte - startByte + 1
            StreamLog.i(TAG, "[iOS-Stream] Serving HEAD response: status=${if (rangeHeader != null) "206 Partial Content" else "200 OK"}, length=${if (rangeHeader != null) reqLen else totalSize}")
            val response = if (rangeHeader != null) {
                newFixedLengthResponse(Response.Status.PARTIAL_CONTENT, mime, "").apply {
                    addHeader("Content-Range", "bytes $startByte-$endByte/$totalSize")
                    addHeader("Content-Length", "$reqLen")
                }
            } else {
                newFixedLengthResponse(Response.Status.OK, mime, "").apply {
                    addHeader("Content-Length", "$totalSize")
                }
            }
            response.addHeader("Accept-Ranges", "bytes")
            val clientConnection = headers["connection"] ?: headers["Connection"]
            if (clientConnection?.contains("close", ignoreCase = true) == true) {
                response.addHeader("Connection", "close")
            } else {
                response.addHeader("Connection", "keep-alive")
            }
            response.addHeader("Content-Disposition", "inline; filename=\"$fileName\"")
            return response
        }

        val requestedLength = endByte - startByte + 1
        StreamLog.i(TAG, "[iOS-Stream] Requesting iOS stream source from Telegram: range=$startByte-$endByte/$totalSize (len=$requestedLength bytes / ${StreamTrafficStats.formatBytes(requestedLength)})")
        val t0 = System.currentTimeMillis()
        val chunk = try {
            tgClient.getStreamSource(
                effectiveChannelId, resolvedMessageId, startByte, endByte,
                mediaId = targetParam, platform = ClientPlatform.IOS, userAgent = userAgent
            )
        } catch (e: Exception) {
            StreamLog.e(TAG, "[iOS-Stream] Stream error for $uri: ${e.message}", e)
            val isAuthErr = e.message?.contains("AUTH_KEY_UNREGISTERED", ignoreCase = true) == true ||
                            e.message?.contains("logged out or expired", ignoreCase = true) == true
            return newFixedLengthResponse(
                if (isAuthErr) Response.Status.UNAUTHORIZED else Response.Status.INTERNAL_ERROR,
                "text/plain",
                "Stream error: ${e.message}"
            )
        }
        val elapsed = System.currentTimeMillis() - t0

        StreamLog.i(TAG, "[iOS-Stream] Serving iOS Stream [${if (rangeHeader != null) "206 Partial Content" else "200 OK"}]: range=${chunk.startByte}-${chunk.endByte}/${chunk.totalSize}, len=${chunk.contentLength} (${StreamTrafficStats.formatBytes(chunk.contentLength)}), mime=$mime (prep: ${elapsed}ms)")

        val response = if (rangeHeader != null) {
            newFixedLengthResponse(
                Response.Status.PARTIAL_CONTENT,
                mime,
                chunk.inputStream,
                chunk.contentLength
            ).apply {
                addHeader("Content-Range", "bytes ${chunk.startByte}-${chunk.endByte}/${chunk.totalSize}")
            }
        } else {
            newFixedLengthResponse(
                Response.Status.OK,
                mime,
                chunk.inputStream,
                chunk.totalSize
            )
        }

        response.addHeader("Accept-Ranges", "bytes")
        val clientConnection = headers["connection"] ?: headers["Connection"]
        if (clientConnection?.contains("close", ignoreCase = true) == true) {
            response.addHeader("Connection", "close")
        } else {
            response.addHeader("Connection", "keep-alive")
        }
        response.addHeader("Content-Disposition", "inline; filename=\"$fileName\"")
        return response
    }

    // --- REST APIs for Stitch Web UI ---

    private fun handleGetStatus(): Response {
        val localIp = getLocalIpAddress()
        val uptimeSeconds = (System.currentTimeMillis() - startTime) / 1000
        val runtime = Runtime.getRuntime()
        val usedMem = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
        val maxMem = runtime.maxMemory() / (1024 * 1024)

        val obj = JsonObject().apply {
            addProperty("isRunning", config.isRunning)
            addProperty("port", config.port)
            addProperty("localIp", localIp)
            addProperty("manifestUrl", "http://$localIp:${config.port}/manifest.json")
            addProperty("jellyfinUrl", "http://$localIp:${config.port}")
            addProperty("uptimeSeconds", uptimeSeconds)
            addProperty("uptimeFormatted", formatUptime(uptimeSeconds))
            addProperty("memoryUsedMb", usedMem)
            addProperty("memoryTotalMb", maxMem)
            addProperty("cpuPercent", 4.2)
            addProperty("totalChannels", tgClient.getChannels().size)
            addProperty("totalMedia", tgClient.getAllMedia().size)
            addProperty("activeStreams", 1)
            add("user", gson.toJsonTree(mapOf(
                "username" to tgClient.sessionState.value.username,
                "firstName" to tgClient.sessionState.value.firstName,
                "isLoggedIn" to tgClient.sessionState.value.isLoggedIn
            )))
        }

        return newFixedLengthResponse(Response.Status.OK, "application/json", gson.toJson(obj))
    }

    private fun formatUptime(seconds: Long): String {
        val d = seconds / 86400
        val h = (seconds % 86400) / 3600
        val m = (seconds % 3600) / 60
        return if (d > 0) "${d}d ${h}h" else "${h}h ${m}m"
    }

    private fun handleGetLogs(): Response {
        val logFile = StreamLog.getLogFile()
        if (logFile == null || !logFile.exists()) {
            return newFixedLengthResponse(Response.Status.OK, "text/plain; charset=utf-8", "No stream logs recorded yet.")
        }
        val lines = try {
            logFile.readLines().takeLast(300).joinToString("\n")
        } catch (e: Exception) {
            "Error reading log: ${e.message}"
        }
        return newFixedLengthResponse(Response.Status.OK, "text/plain; charset=utf-8", lines)
    }

    private fun handleToggleServer(): Response {
        config.isRunning = !config.isRunning
        return handleGetStatus()
    }

    private fun handleGetAuthStatus(): Response {
        return newFixedLengthResponse(Response.Status.OK, "application/json", gson.toJson(tgClient.sessionState.value))
    }

    private fun handleSendCode(session: IHTTPSession): Response = runBlocking {
        val body = getRequestBody(session)
        val phone = body?.get("phoneNumber")?.asString ?: ""
        val result = tgClient.sendLoginCode(phone)

        val res = JsonObject()
        if (result.isSuccess) {
            res.addProperty("success", true)
            res.addProperty("message", result.getOrNull())
        } else {
            res.addProperty("success", false)
            res.addProperty("error", result.exceptionOrNull()?.message)
        }
        newFixedLengthResponse(Response.Status.OK, "application/json", gson.toJson(res))
    }

    private fun handleSignIn(session: IHTTPSession): Response = runBlocking {
        val body = getRequestBody(session)
        val code = body?.get("code")?.asString ?: ""
        val result = tgClient.verifyLoginCode(code)

        val res = JsonObject()
        if (result.isSuccess) {
            res.addProperty("success", true)
            res.add("session", gson.toJsonTree(result.getOrNull()))
        } else {
            res.addProperty("success", false)
            res.addProperty("error", result.exceptionOrNull()?.message)
        }
        newFixedLengthResponse(Response.Status.OK, "application/json", gson.toJson(res))
    }

    private fun handleVerify2FA(session: IHTTPSession): Response = runBlocking {
        val body = getRequestBody(session)
        val password = body?.get("password")?.asString ?: ""
        val result = tgClient.verify2FAPassword(password)

        val res = JsonObject()
        if (result.isSuccess) {
            res.addProperty("success", true)
            res.add("session", gson.toJsonTree(result.getOrNull()))
        } else {
            res.addProperty("success", false)
            res.addProperty("error", result.exceptionOrNull()?.message)
        }
        newFixedLengthResponse(Response.Status.OK, "application/json", gson.toJson(res))
    }

    private fun handleImportSession(session: IHTTPSession): Response = runBlocking {
        val body = getRequestBody(session)
        val sessionString = body?.get("sessionString")?.asString ?: ""
        val result = tgClient.importSessionString(sessionString)

        val res = JsonObject()
        if (result.isSuccess) {
            res.addProperty("success", true)
            res.add("session", gson.toJsonTree(result.getOrNull()))
        } else {
            res.addProperty("success", false)
            res.addProperty("error", result.exceptionOrNull()?.message)
        }
        newFixedLengthResponse(Response.Status.OK, "application/json", gson.toJson(res))
    }

    private fun handleLogout(): Response {
        tgClient.logout()
        return handleGetAuthStatus()
    }

    private fun handleGetChannels(): Response {
        val list = tgClient.getChannels()
        return newFixedLengthResponse(Response.Status.OK, "application/json", gson.toJson(list))
    }

    private fun handleAddChannel(session: IHTTPSession): Response {
        val body = getRequestBody(session)
        val identifier = body?.get("identifier")?.asString ?: ""
        val channel = tgClient.addChannel(identifier)
        return newFixedLengthResponse(Response.Status.OK, "application/json", gson.toJson(channel))
    }

    private fun handleToggleChannel(session: IHTTPSession): Response {
        val body = getRequestBody(session)
        val id = body?.get("id")?.asLong ?: 0L
        val active = body?.get("active")?.asBoolean ?: true
        tgClient.toggleChannel(id, active)
        return newFixedLengthResponse(Response.Status.OK, "application/json", "{\"success\":true}")
    }

    private fun handleDeleteChannel(uri: String): Response {
        val id = uri.substringAfterLast("/").toLongOrNull() ?: 0L
        tgClient.removeChannel(id)
        return newFixedLengthResponse(Response.Status.OK, "application/json", "{\"success\":true}")
    }

    private fun handleSyncChannel(uri: String): Response = runBlocking {
        val id = uri.substringAfterLast("/").toLongOrNull()
        val channelsToSync = if (id != null && id != 0L) {
            tgClient.getChannels().filter { it.id == id }
        } else {
            tgClient.getChannels().filter { it.isActive }
        }

        var totalAdded = 0
        val errors = mutableListOf<String>()

        for (ch in channelsToSync) {
            val res = tgClient.syncChannel(ch.id)
            if (res.isSuccess) {
                totalAdded += res.getOrDefault(0)
            } else {
                errors.add("${ch.title}: ${res.exceptionOrNull()?.message}")
            }
        }

        val json = JsonObject().apply {
            addProperty("success", errors.isEmpty())
            addProperty("totalMediaAdded", totalAdded)
            if (errors.isNotEmpty()) {
                add("errors", gson.toJsonTree(errors))
            }
        }
        newFixedLengthResponse(Response.Status.OK, "application/json", gson.toJson(json))
    }

    private fun handleGetLibrary(session: IHTTPSession): Response {
        val params = session.parameters
        val query = params["q"]?.firstOrNull() ?: ""
        val category = params["category"]?.firstOrNull() ?: "all"
        val media = tgClient.searchMedia(query, category)
        return newFixedLengthResponse(Response.Status.OK, "application/json", gson.toJson(media))
    }

    private fun handleGetSettings(): Response {
        return newFixedLengthResponse(Response.Status.OK, "application/json", gson.toJson(config))
    }

    private fun handleSaveSettings(session: IHTTPSession): Response {
        val body = getRequestBody(session)
        body?.let {
            if (it.has("port")) config.port = it.get("port").asInt
            if (it.has("customPosterUrl")) config.customPosterUrl = it.get("customPosterUrl").asString
            if (it.has("customPosterApiKey")) config.customPosterApiKey = it.get("customPosterApiKey").asString
            if (it.has("bufferChunkSizeKb")) config.bufferChunkSizeKb = it.get("bufferChunkSizeKb").asInt
            if (it.has("autoSleepEnabled")) config.autoSleepEnabled = it.get("autoSleepEnabled").asBoolean
            if (it.has("autoSleepTimeoutMinutes")) config.autoSleepTimeoutMinutes = it.get("autoSleepTimeoutMinutes").asInt
        }
        return handleGetSettings()
    }

    private fun getRequestBody(session: IHTTPSession): JsonObject? {
        val map = HashMap<String, String>()
        session.parseBody(map)
        val postData = map["postData"]
        return if (!postData.isNullOrBlank()) {
            JsonParser.parseString(postData).asJsonObject
        } else {
            null
        }
    }

    private fun serveStaticAsset(uri: String): Response {
        val cleanPath = uri.trimStart('/').substringBefore('?')
        if (cleanPath.isNotBlank()) {
            val assetPath = if (cleanPath.startsWith("web/")) cleanPath else "web/$cleanPath"
            try {
                val mimeType = when {
                    cleanPath.endsWith(".ico") -> "image/x-icon"
                    cleanPath.endsWith(".png") -> "image/png"
                    cleanPath.endsWith(".jpg") || cleanPath.endsWith(".jpeg") -> "image/jpeg"
                    cleanPath.endsWith(".svg") -> "image/svg+xml"
                    cleanPath.endsWith(".webmanifest") || cleanPath.endsWith(".json") -> "application/manifest+json"
                    cleanPath.endsWith(".html") -> "text/html"
                    cleanPath.endsWith(".css") -> "text/css"
                    cleanPath.endsWith(".js") -> "application/javascript"
                    else -> null
                }
                if (mimeType != null) {
                    val stream = context.assets.open(assetPath)
                    return newChunkedResponse(Response.Status.OK, mimeType, stream)
                }
            } catch (_: Exception) {
                // Asset not found, fall back to server info JSON
            }
        }

        val info = JsonObject().apply {
            addProperty("name", "NoxMedia Server")
            addProperty("version", "1.0.0")
            addProperty("status", if (config.isRunning) "online" else "paused")
            addProperty("port", config.port)
            addProperty("manifest", "http://${getLocalIpAddress()}:${config.port}/manifest.json")
        }
        return newFixedLengthResponse(Response.Status.OK, "application/json", gson.toJson(info))
    }
}
