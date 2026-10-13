package com.tgserver.streamer.server

import android.content.Context
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

        val normUri = if (uri.startsWith("/stremio")) uri.removePrefix("/stremio") else uri

        val response = try {
            when {
                // 1. Stremio Addon Endpoints (support both /manifest.json and /stremio/manifest.json)
                normUri == "/manifest.json" || normUri == "" || normUri == "/" -> handleManifest()
                normUri.startsWith("/catalog/") -> handleCatalog(normUri)
                normUri.startsWith("/meta/") -> handleMeta(normUri)
                normUri.startsWith("/stream/") && !normUri.startsWith("/stream/video/") -> handleStreamDescriptor(normUri)

                // 2. HTTP 206 Partial Content Video Streaming Engine
                normUri.startsWith("/stream/video/") -> handleVideoStream(session, normUri, headers)
                uri.startsWith("/stream/video/") -> handleVideoStream(session, uri, headers)

                // 3. Management REST APIs for Stitch UI
                uri == "/api/status" -> handleGetStatus()
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

    private fun handleManifest(): Response {
        val manifest = JsonObject().apply {
            addProperty("id", "org.tgserver.streamer")
            addProperty("version", "1.0.0")
            addProperty("name", "TG-Server Streamer")
            addProperty("description", "Direct Telegram video streamer with Stremio & Kitsu/TMDB integration")
            addProperty("logo", "https://lh3.googleusercontent.com/aida/AEtjO1XxF5nLod1dKj8IrkfY2sKq1asKsR74gub1jDyICCeoafQ6Dh5N6bGXc8PAqlJkXYlWXajcnx4pVNnZX2pM9Ra923Ml5jb0jvAdarsZqKIj-yYVPDpCufiBbr8kb-kHeO3ymMD4-VoMVeVlJYOx8J0b4lEJpK2yAgUA8edYgOHhCTWrPUrSYWP6ec9qa80nJgzFgxMxgeIMqJ4lOX40yq670kg84dITxJghMvwrPMzL_NtP5hBpi82AQyAN")
            add("types", gson.toJsonTree(listOf("movie", "series", "anime")))
            add("catalogs", gson.toJsonTree(listOf(
                mapOf(
                    "type" to "movie",
                    "id" to "tg_movies",
                    "name" to "Telegram Movies",
                    "extra" to listOf(mapOf("name" to "search", "isRequired" to false), mapOf("name" to "skip")),
                    "extraSupported" to listOf("search", "skip")
                ),
                mapOf(
                    "type" to "series",
                    "id" to "tg_series",
                    "name" to "Telegram Series",
                    "extra" to listOf(mapOf("name" to "search", "isRequired" to false), mapOf("name" to "skip")),
                    "extraSupported" to listOf("search", "skip")
                ),
                mapOf(
                    "type" to "series",
                    "id" to "tg_anime",
                    "name" to "Telegram Anime",
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
                config.customPosterApiKey
            )
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
            config.customPosterApiKey
        )

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

    private fun resolveTitleForId(imdbId: String?, kitsuId: Int?, mediaType: String): String? {
        if (kitsuId != null) {
            val key = "kitsu:$kitsuId"
            idTitleCache[key]?.let { return it }
            try {
                val url = "https://kitsu.io/api/edge/anime/$kitsuId"
                val req = Request.Builder().url(url).header("User-Agent", "TG-Server/1.0").build()
                streamHttpClient.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val body = resp.body?.string() ?: return null
                        val root = JsonParser.parseString(body).asJsonObject
                        val title = root.getAsJsonObject("data")
                            ?.getAsJsonObject("attributes")
                            ?.get("canonicalTitle")?.asString
                        if (!title.isNullOrBlank()) {
                            idTitleCache[key] = title
                            return title
                        }
                    }
                }
            } catch (_: Exception) {}
        } else if (!imdbId.isNullOrBlank() && imdbId.startsWith("tt", ignoreCase = true)) {
            val key = "imdb:$imdbId"
            idTitleCache[key]?.let { return it }
            try {
                val cType = if (mediaType == "movie") "movie" else "series"
                val url = "https://v3-cinemeta.strem.io/meta/$cType/$imdbId.json"
                val req = Request.Builder().url(url).header("User-Agent", "Mozilla/5.0").build()
                streamHttpClient.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val body = resp.body?.string() ?: return null
                        val root = JsonParser.parseString(body).asJsonObject
                        val title = root.getAsJsonObject("meta")?.get("name")?.asString
                        if (!title.isNullOrBlank()) {
                            idTitleCache[key] = title
                            return title
                        }
                    }
                }
            } catch (_: Exception) {}
        }
        return null
    }

    private fun cleanTitleForMatch(t: String): String {
        return t.lowercase()
            .replace(Regex("(?i)\\.(mkv|mp4|avi|mov|webm)$"), "")
            .replace(Regex("(?i)(2160p|1080p|720p|480p|4k|uhd|hdr|hevc|x264|x265|aac|dts|bluray|web-dl|webrip|proper|repack)"), "")
            .replace(Regex("[^a-z0-9]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun handleStreamDescriptor(uri: String): Response {
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
            val expectedTitle = resolveTitleForId(imdbId, kitsuId, mediaType)
            if (!expectedTitle.isNullOrBlank()) {
                val cleanExpected = cleanTitleForMatch(expectedTitle)
                if (cleanExpected.isNotBlank()) {
                    matchedItems = allMedia.filter { item ->
                        val cleanItem = cleanTitleForMatch(item.title)
                        val titleMatches = cleanItem == cleanExpected ||
                                cleanItem.startsWith(cleanExpected) ||
                                cleanExpected.startsWith(cleanItem)
                        if (!titleMatches) return@filter false

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

        val localIp = getLocalIpAddress()
        val streams = finalItems.map { item ->
            val rawFileName = if (item.fileName.isNotBlank()) item.fileName else "${item.title}.mkv"
            val safeFileName = try {
                java.net.URLEncoder.encode(rawFileName, "UTF-8").replace("+", "%20")
            } catch (_: Exception) {
                "video.mkv"
            }
            val streamUrl = "http://$localIp:${config.port}/stream/video/${item.channelId}/${item.id}/$safeFileName"
            val epInfo = if (item.season != null && item.episode != null) "S%02dE%02d • ".format(item.season, item.episode) else ""
            val epTitle = if (!item.episodeTitle.isNullOrBlank()) "${item.episodeTitle}\n" else ""

            mapOf(
                "name" to "TG-Server [${item.quality}]",
                "title" to "📁 $rawFileName\n$epTitle$epInfo💾 ${item.formattedSize}",
                "url" to streamUrl
            )
        }

        val res = mapOf("streams" to streams)
        return newFixedLengthResponse(Response.Status.OK, "application/json", gson.toJson(res))
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
            ?: if (targetParamAsInt > 0) allMedia.find {
                (it.channelId == channelId || channelId == 0L) && it.isSplit &&
                (it.messageId == targetParamAsInt || it.parts.any { p -> p.messageId == targetParamAsInt })
            } else null
            ?: if (targetParamAsInt > 0) allMedia.find {
                ((it.channelId == channelId || channelId == 0L) && it.messageId == targetParamAsInt) || (it.messageId == targetParamAsInt)
            } else null
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

        // Handle HEAD requests immediately without starting Pyrogram stream
        if (session.method == Method.HEAD) {
            val response = newFixedLengthResponse(Response.Status.OK, mime, "")
            response.addHeader("Accept-Ranges", "bytes")
            response.addHeader("Content-Range", "bytes 0-${totalSize - 1}/$totalSize")
            response.addHeader("Content-Length", "$totalSize")
            response.addHeader("Content-Disposition", "inline; filename=\"$fileName\"")
            return response
        }

        val rangeHeader = headers["range"] ?: headers["Range"]
        var startByte = 0L
        var endByte = -1L

        if (!rangeHeader.isNullOrBlank() && rangeHeader.startsWith("bytes=")) {
            val rangeVal = rangeHeader.removePrefix("bytes=").trim()
            val dashIndex = rangeVal.indexOf('-')
            if (dashIndex != -1) {
                val startStr = rangeVal.substring(0, dashIndex)
                val endStr = rangeVal.substring(dashIndex + 1)
                startByte = startStr.toLongOrNull() ?: 0L
                endByte = endStr.toLongOrNull() ?: -1L
            }
        }

        // Pass resolvedMessageId (from actual media item) and targetParam (item.id) for lookup
        val chunk = try {
            tgClient.getStreamSource(effectiveChannelId, resolvedMessageId, startByte, endByte, mediaId = targetParam)
        } catch (e: Exception) {
            Log.e(TAG, "Stream error: ${e.message}", e)
            return newFixedLengthResponse(
                Response.Status.INTERNAL_ERROR,
                "text/plain",
                "Stream error: ${e.message}"
            )
        }

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
        val info = JsonObject().apply {
            addProperty("name", "TG-Server Mobile Streamer")
            addProperty("version", "1.0.0")
            addProperty("status", if (config.isRunning) "online" else "paused")
            addProperty("port", config.port)
            addProperty("manifest", "http://${getLocalIpAddress()}:${config.port}/manifest.json")
        }
        return newFixedLengthResponse(Response.Status.OK, "application/json", gson.toJson(info))
    }
}
