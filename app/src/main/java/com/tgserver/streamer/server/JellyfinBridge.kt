package com.tgserver.streamer.server

import android.content.Context
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.tgserver.streamer.data.model.MediaItem
import com.tgserver.streamer.data.model.MediaGroup
import com.tgserver.streamer.data.model.CatalogDefinition
import com.tgserver.streamer.data.model.ServerConfig
import com.tgserver.streamer.data.model.toMediaGroups
import com.tgserver.streamer.data.repository.CatalogRepository
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
    companion object {
        const val SERVER_VERSION = "12.0.0"
    }

    private val gson = Gson()
    private val catalogRepo by lazy { CatalogRepository(context) }
    private val rootHexPathRegex = Regex("/[0-9a-fA-F]{32}")
    private val seriesSlugCleanRegex = Regex("[^a-z0-9]")
    // Season IDs always end with _s<digits> e.g. series_lucifer_s1, series_lucifer_s2
    private val seasonSuffixRegex = Regex("_s(\\d+)$")

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

    // =========================================================================
    // IN-MEMORY TV SHOW & ANIME REGISTRY (100% SPEC COMPLIANT WITH INFUSE)
    // =========================================================================
    data class TvShowIndex(
        val slug: String,
        val title: String,
        val seriesRawId: String,
        val seriesHexId: String,
        val seasons: Map<Int, TvSeasonIndex>,
        val allEpisodes: List<MediaItem>,
        val allVersions: List<MediaItem>,
        val firstEpisode: MediaItem?,
        val isAnime: Boolean
    )

    data class TvSeasonIndex(
        val seasonNumber: Int,
        val seasonRawId: String,
        val seasonHexId: String,
        val seriesHexId: String,
        val seriesTitle: String,
        val episodes: List<MediaItem>,
        val allVersions: List<MediaItem>
    )

    @Volatile private var cachedTvMediaCount = -1
    private val showsByHex = ConcurrentHashMap<String, TvShowIndex>()
    private val showsBySlug = ConcurrentHashMap<String, TvShowIndex>()
    private val seasonsByHex = ConcurrentHashMap<String, TvSeasonIndex>()
    private val episodesById = ConcurrentHashMap<String, MediaItem>()
    private val episodeToShow = ConcurrentHashMap<String, TvShowIndex>()

    // Known Reefin client IPs (cached across active connections)
    private val reefinClientIps = ConcurrentHashMap.newKeySet<String>()

    private fun isSeasonRawId(raw: String): Boolean =
        raw.startsWith("series_") && seasonSuffixRegex.containsMatchIn(raw)

    private fun isSeriesRawId(raw: String): Boolean =
        raw.startsWith("series_") && !seasonSuffixRegex.containsMatchIn(raw)

    @Volatile private var cachedMediaGroups: List<MediaGroup> = emptyList()
    @Volatile private var cachedMediaSize: Int = -1
    private val catalogItemsCache = ConcurrentHashMap<String, Pair<Int, List<MediaGroup>>>()

    private fun getMediaGroupsCached(): List<MediaGroup> {
        val all = tgClient.getAllMedia()
        if (all.isEmpty()) return emptyList()

        if (cachedMediaSize == all.size && cachedMediaGroups.isNotEmpty()) {
            return cachedMediaGroups
        }

        // Fast load from precomputed on-disk cache if memory cache is empty (~10-20ms instead of 5000ms)
        if (cachedMediaGroups.isEmpty()) {
            try {
                val groupCacheFile = java.io.File(context.filesDir, "media_groups_cache.json")
                if (groupCacheFile.exists()) {
                    val json = groupCacheFile.readText()
                    if (json.isNotBlank()) {
                        val type = object : com.google.gson.reflect.TypeToken<List<MediaGroup>>() {}.type
                        val list: List<MediaGroup>? = gson.fromJson(json, type)
                        val valid = list?.filterNotNull()?.filter { it.key != null && it.items != null } ?: emptyList()
                        if (valid.isNotEmpty()) {
                            cachedMediaGroups = valid
                            cachedMediaSize = all.size
                            return valid
                        }
                    }
                }
            } catch (e: Exception) {
                StreamLog.w(TAG, "Failed to load precomputed media_groups_cache.json: ${e.message}")
            }
        }

        val groups = all.toMediaGroups()
        cachedMediaGroups = groups
        cachedMediaSize = all.size
        return groups
    }

    private fun getCatalogGroupsCached(cat: CatalogDefinition): List<MediaGroup> {
        val allSize = tgClient.getAllMedia().size
        val cached = catalogItemsCache[cat.id]
        if (cached != null && cached.first == allSize) {
            return cached.second
        }
        val allGroups = getMediaGroupsCached()
        val matched = catalogRepo.filterMediaForCatalog(cat, allGroups)
        catalogItemsCache[cat.id] = Pair(allSize, matched)
        return matched
    }

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

        ensureTvIndex()
        val postIndexed = hexToIdMap[clean]
        if (postIndexed != null) return postIndexed

        return hexOrRaw
    }

    @Synchronized
    private fun ensureTvIndex() {
        val all = tgClient.getAllMedia()
        if (all.size == cachedTvMediaCount) return

        showsByHex.clear()
        showsBySlug.clear()
        seasonsByHex.clear()
        episodesById.clear()
        episodeToShow.clear()

        val tvMedia = all.filter { !it.isHidden && (isSeries(it) || isAnime(it)) }
        val grouped = tvMedia.groupBy { getSeriesSlug(it) }

        for ((slug, items) in grouped) {
            if (slug.isBlank() || items.isEmpty()) continue
            val seriesRawId = "series_$slug"
            val seriesHex = toHexId(seriesRawId)
            registerHexId(seriesRawId, seriesHex)
            val isAni = items.any { isAnime(it) }
            val showTitle = items.firstOrNull { it.title.isNotBlank() }?.title ?: slug.replace("_", " ")

            val seasonMap = mutableMapOf<Int, TvSeasonIndex>()
            val episodesGroupedBySeason = items.groupBy { it.season ?: 1 }

            for ((sNum, eps) in episodesGroupedBySeason) {
                val epsByEpisodeNumber = eps.groupBy { it.episode ?: 1 }
                val deduplicatedEps = epsByEpisodeNumber.entries.sortedBy { it.key }.map { (_, versions) ->
                    versions.sortedWith(
                        compareByDescending<MediaItem> { if (it.quality.contains("4k", true) || it.qualityBadge.contains("4k", true)) 4 else if (it.quality.contains("1080", true)) 3 else if (it.quality.contains("720", true)) 2 else 1 }
                            .thenByDescending { it.sizeBytes }
                    ).first()
                }

                val seasonRawId = "${seriesRawId}_s$sNum"
                val seasonHex = toHexId(seasonRawId)
                registerHexId(seasonRawId, seasonHex)
                val seasonIndex = TvSeasonIndex(
                    seasonNumber = sNum,
                    seasonRawId = seasonRawId,
                    seasonHexId = seasonHex,
                    seriesHexId = seriesHex,
                    seriesTitle = showTitle,
                    episodes = deduplicatedEps,
                    allVersions = eps
                )
                seasonMap[sNum] = seasonIndex
                seasonsByHex[seasonHex] = seasonIndex
            }

            val sortedAllEps = seasonMap.values.flatMap { it.episodes }.sortedWith(
                compareBy<MediaItem> { it.season ?: 1 }
                    .thenBy { it.episode ?: 1 }
            )

            val showIndex = TvShowIndex(
                slug = slug,
                title = showTitle,
                seriesRawId = seriesRawId,
                seriesHexId = seriesHex,
                seasons = seasonMap,
                allEpisodes = sortedAllEps,
                allVersions = items,
                firstEpisode = sortedAllEps.firstOrNull(),
                isAnime = isAni
            )

            showsByHex[seriesHex] = showIndex
            showsBySlug[slug] = showIndex

            for (ep in items) {
                val epHex = toHexId(ep.id)
                registerHexId(ep.id, epHex)
                episodesById[ep.id] = ep
                episodesById[epHex] = ep
                episodeToShow[ep.id] = showIndex
                episodeToShow[epHex] = showIndex
            }
        }

        cachedTvMediaCount = all.size
    }

    private fun findShow(idOrSlug: String): TvShowIndex? {
        ensureTvIndex()
        val clean = idOrSlug.replace("-", "").lowercase().trim()
        if (clean.isBlank()) return null

        showsByHex[clean]?.let { return it }
        showsBySlug[clean]?.let { return it }
        seasonsByHex[clean]?.let { return showsByHex[it.seriesHexId] }

        val raw = fromHexId(clean)
        showsByHex[raw]?.let { return it }
        showsBySlug[raw]?.let { return it }

        val rawWithoutSeason = raw.replace(seasonSuffixRegex, "")
        val targetSlug = when {
            rawWithoutSeason.startsWith("series_") -> rawWithoutSeason.removePrefix("series_")
            clean.startsWith("series_") -> clean.replace(seasonSuffixRegex, "").removePrefix("series_")
            else -> rawWithoutSeason
        }
        if (targetSlug.isNotBlank()) {
            showsBySlug[targetSlug]?.let { return it }
            val normSlug = targetSlug.replace(seriesSlugCleanRegex, "")
            showsBySlug.values.firstOrNull { it.slug.replace(seriesSlugCleanRegex, "") == normSlug }?.let { return it }
        }

        return showsBySlug.values.firstOrNull {
            it.title.equals(clean, ignoreCase = true) || it.title.equals(raw, ignoreCase = true)
        }
    }

    private fun findSeason(seriesId: String, seasonIdOrNum: String): TvSeasonIndex? {
        ensureTvIndex()
        val cleanSeasonId = seasonIdOrNum.replace("-", "").lowercase().trim()
        seasonsByHex[cleanSeasonId]?.let { return it }

        val show = findShow(seriesId) ?: return null
        val sNum = seasonIdOrNum.toIntOrNull()
            ?: seasonSuffixRegex.find(fromHexId(seasonIdOrNum))?.groupValues?.getOrNull(1)?.toIntOrNull()
            ?: 1
        return show.seasons[sNum] ?: show.seasons.values.firstOrNull()
    }

    private fun buildSeriesDto(
        show: TvShowIndex,
        localIp: String,
        port: Int,
        isInfuse: Boolean = false,
        isReefin: Boolean = false
    ): Map<String, Any?> {
        val sCount = if (show.seasons.isNotEmpty()) show.seasons.size else 1
        val epCount = show.allEpisodes.size
        val firstEp = show.firstEpisode
        val yearInt = firstEp?.year?.toIntOrNull() ?: 2024
        val parentLibHex = if (show.isAnime) libAnimeHex else libSeriesHex

        val providerIds = mutableMapOf<String, String>()
        firstEp?.imdbId?.takeIf { it.isNotBlank() }?.let { providerIds["Imdb"] = it }
        firstEp?.tmdbId?.takeIf { it > 0 }?.let { providerIds["Tmdb"] = it.toString() }
        firstEp?.kitsuId?.takeIf { it > 0 }?.let { providerIds["Kitsu"] = it.toString() }

        val hasLogo = (firstEp?.imdbId?.startsWith("tt") == true) ||
                posterService.hasCachedLogo(show.title)
        val imageTags = mutableMapOf(
            "Primary" to show.seriesHexId,
            "Thumb" to show.seriesHexId
        )
        if (hasLogo) {
            imageTags["Logo"] = show.seriesHexId
        }

        return mapOf(
            "Name" to show.title,
            "OriginalTitle" to show.title,
            "SortName" to show.title,
            "ForcedSortName" to show.title,
            "ServerId" to serverId,
            "Id" to show.seriesHexId,
            "ParentId" to parentLibHex,
            "Type" to "Series",
            "IsFolder" to true,
            "Status" to "Continuing",
            "AirDays" to listOf("Monday"),
            "DisplayPreferencesId" to show.seriesHexId,
            "ProductionYear" to yearInt,
            "PremiereDate" to "${yearInt}-01-01T00:00:00.0000000Z",
            "Overview" to (firstEp?.overview ?: ""),
            "CommunityRating" to (firstEp?.rating?.takeIf { it > 0.0 } ?: 8.0),
            "Genres" to (if (show.isAnime) listOf("Animation", "Anime") else listOf("Drama", "Series")),
            "ChildCount" to sCount,
            "SeasonCount" to sCount,
            "EpisodeCount" to epCount,
            "RecursiveItemCount" to epCount,
            "PrimaryImageAspectRatio" to 0.6666667,
            "SeriesPrimaryImageTag" to show.seriesHexId,
            "ParentLogoItemId" to show.seriesHexId,
            "ParentBackdropItemId" to show.seriesHexId,
            "ParentBackdropImageTags" to listOf(show.seriesHexId),
            "BackdropImageTags" to listOf(show.seriesHexId),
            "ImageTags" to imageTags,
            "ProviderIds" to providerIds,
            "CanDelete" to false,
            "CanDownload" to false,
            "SupportsDirectPlay" to false,
            "SupportsDirectStream" to false,
            "SupportsTranscoding" to false,
            "UserData" to mapOf(
                "PlaybackPositionTicks" to 0L,
                "PlayCount" to 0,
                "IsFavorite" to false,
                "Played" to false,
                "UnplayedItemCount" to epCount
            )
        )
    }

    private fun buildSeasonDto(
        season: TvSeasonIndex,
        localIp: String,
        port: Int,
        isInfuse: Boolean = false,
        isReefin: Boolean = false
    ): Map<String, Any?> {
        val epCount = season.episodes.size
        return mapOf(
            "Name" to "Season ${season.seasonNumber}",
            "SeasonName" to "Season ${season.seasonNumber}",
            "SortName" to "Season ${season.seasonNumber}",
            "ForcedSortName" to "Season ${season.seasonNumber}",
            "ServerId" to serverId,
            "Id" to season.seasonHexId,
            "ParentId" to season.seriesHexId,
            "SeriesId" to season.seriesHexId,
            "SeriesName" to season.seriesTitle,
            "IndexNumber" to season.seasonNumber,
            "Type" to "Season",
            "IsFolder" to true,
            "ChildCount" to epCount,
            "RecursiveItemCount" to epCount,
            "PrimaryImageAspectRatio" to 0.6666667,
            "SeriesPrimaryImageTag" to season.seriesHexId,
            "ParentLogoItemId" to season.seriesHexId,
            "ParentLogoImageTag" to season.seriesHexId,
            "ParentBackdropItemId" to season.seriesHexId,
            "ParentBackdropImageTags" to listOf(season.seriesHexId),
            "BackdropImageTags" to listOf(season.seriesHexId),
            "ImageTags" to mapOf("Primary" to season.seriesHexId, "Thumb" to season.seriesHexId),
            "CanDelete" to false,
            "CanDownload" to false,
            "SupportsDirectPlay" to false,
            "SupportsDirectStream" to false,
            "SupportsTranscoding" to false,
            "UserData" to mapOf(
                "UnplayedItemCount" to epCount,
                "PlaybackPositionTicks" to 0L,
                "PlayCount" to 0,
                "IsFavorite" to false,
                "Played" to false
            )
        )
    }

    private fun buildEpisodeDto(
        item: MediaItem,
        show: TvShowIndex,
        seasonIndex: TvSeasonIndex,
        localIp: String,
        port: Int,
        includeMediaSources: Boolean = true,
        isInfuse: Boolean = false,
        isReefin: Boolean = false
    ): Map<String, Any?> {
        val hexId = toHexId(item.id)
        val sNum = seasonIndex.seasonNumber
        val epNum = item.episode ?: 1
        val matchingVersions = seasonIndex.allVersions.filter { (it.episode ?: 1) == epNum }
        val durationTicks = resolveEpisodeDurationTicks(item, matchingVersions)
        val rawFileName = buildCleanFileName(item)
        val safeFileName = try {
            URLEncoder.encode(rawFileName, "UTF-8").replace("+", "%20")
        } catch (_: Exception) {
            "video.mkv"
        }
        val streamUrl = "http://$localIp:$port/stream/video/${item.channelId}/${item.id}/$safeFileName"
        val ext = rawFileName.substringAfterLast(".", "mkv").lowercase()
        val displayName = item.episodeTitle?.takeIf { it.isNotBlank() } ?: "Episode $epNum"
        val virtualPath = if (isReefin) streamUrl else "/TV Shows/${show.title}/Season $sNum/$rawFileName"

        val providerIds = mutableMapOf<String, String>()
        item.imdbId?.takeIf { it.isNotBlank() }?.let { providerIds["Imdb"] = it }
        item.tmdbId?.takeIf { it > 0 }?.let { providerIds["Tmdb"] = it.toString() }
        item.kitsuId?.takeIf { it > 0 }?.let { providerIds["Kitsu"] = it.toString() }

        val map = mutableMapOf<String, Any?>(
            "Name" to displayName,
            "SortName" to displayName,
            "ForcedSortName" to displayName,
            "ServerId" to serverId,
            "Id" to hexId,
            "ParentId" to seasonIndex.seasonHexId,
            "Type" to "Episode",
            "MediaType" to "Video",
            "IsFolder" to false,
            "IndexNumber" to epNum,
            "ParentIndexNumber" to sNum,
            "SeriesId" to show.seriesHexId,
            "SeriesName" to show.title,
            "SeasonId" to seasonIndex.seasonHexId,
            "SeasonName" to "Season $sNum",
            "SeriesPrimaryImageTag" to show.seriesHexId,
            "SeasonPrimaryImageTag" to seasonIndex.seasonHexId,
            "ParentBackdropItemId" to show.seriesHexId,
            "ParentBackdropImageTags" to listOf(show.seriesHexId),
            "BackdropImageTags" to listOf(show.seriesHexId),
            "PrimaryImageAspectRatio" to 1.7777778,
            "Container" to ext,
            "LocationType" to if (isReefin) "Remote" else "FileSystem",
            "Path" to virtualPath,
            "DirectStreamUrl" to streamUrl,
            "CanDelete" to false,
            "CanDownload" to true,
            "SupportsDirectPlay" to true,
            "SupportsDirectStream" to true,
            "SupportsTranscoding" to !isReefin,
            "Overview" to (item.overview ?: ""),
            "CommunityRating" to (if (item.rating > 0) item.rating else 8.0),
            "ImageTags" to mapOf("Primary" to hexId, "Thumb" to hexId),
            "ProviderIds" to providerIds,
            "UserData" to mapOf(
                "PlaybackPositionTicks" to 0L,
                "PlayCount" to 0,
                "IsFavorite" to false,
                "Played" to false
            )
        )

        if (durationTicks != null && durationTicks > 0) {
            map["RunTimeTicks"] = durationTicks
        }

        if (includeMediaSources) {
            val mediaSources = buildEpisodeMediaSources(item, matchingVersions, show.title, sNum, localIp, port, isReefin)
            map["MediaSources"] = mediaSources
            val primarySource = mediaSources.firstOrNull()
            if (primarySource != null) {
                primarySource["DirectStreamUrl"]?.let { map["DirectStreamUrl"] = it }
                primarySource["Path"]?.let { map["Path"] = it }
                primarySource["RunTimeTicks"]?.let { map["RunTimeTicks"] = it }
            }
        }

        return map
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
        if (uri.startsWith("/jellyfin", ignoreCase = true)) return true
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
     * Detects if the current request originates from Infuse media player.
     */
    private fun isInfuseClient(headers: Map<String, String>): Boolean {
        val ua = headers["user-agent"] ?: headers["User-Agent"] ?: ""
        val auth = headers["x-emby-authorization"] ?: headers["X-Emby-Authorization"] ?: headers["authorization"] ?: ""
        return ua.contains("Infuse", ignoreCase = true) || auth.contains("Infuse", ignoreCase = true)
    }

    /**
     * Detects if the current request originates from Reefin media player (Android / Android TV / Google TV).
     * Recognizes Reefin via User-Agent, X-Emby-Authorization, X-Emby-Client, query params, and package signatures.
     */
    private fun isReefinClient(headers: Map<String, String>, remoteIp: String? = null, params: Map<String, String> = emptyMap()): Boolean {
        if (!remoteIp.isNullOrBlank() && reefinClientIps.contains(remoteIp)) return true
        val ua = headers["user-agent"] ?: headers["User-Agent"] ?: ""
        val auth = headers["x-emby-authorization"] ?: headers["X-Emby-Authorization"] ?: headers["authorization"] ?: ""
        val client = headers["x-emby-client"] ?: headers["X-Emby-Client"] ?: params["Client"] ?: params["client"] ?: ""
        val isMatch = ua.contains("Reefin", ignoreCase = true) ||
                ua.contains("com.shady.reefin", ignoreCase = true) ||
                ua.contains("shady.reefin", ignoreCase = true) ||
                auth.contains("Reefin", ignoreCase = true) ||
                client.contains("Reefin", ignoreCase = true)
        if (isMatch && !remoteIp.isNullOrBlank()) {
            reefinClientIps.add(remoteIp)
        }
        return isMatch
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
        var cleanUri = uri
        if (cleanUri.startsWith("/jellyfin", ignoreCase = true)) {
            cleanUri = cleanUri.substring("/jellyfin".length)
        }
        if (cleanUri.startsWith("/emby", ignoreCase = true)) {
            cleanUri = cleanUri.substring("/emby".length)
        }
        cleanUri = cleanUri.trimEnd('/')
        if (cleanUri.isBlank()) cleanUri = "/"
        val lowerUri = cleanUri.lowercase()
        val params = session.parameters.mapValues { it.value.firstOrNull() ?: "" }
        val localIp = localIpProvider()
        val config = configProvider()
        val isInfuse = isInfuseClient(headers)
        val isReefin = isReefinClient(headers, session.remoteIpAddress, params)

        StreamLog.i(TAG, "[$method] $cleanUri params=$params (from ${session.remoteIpAddress ?: "unknown"}) [isInfuse=$isInfuse, isReefin=$isReefin]")

        return try {
            when {
                // Root landing / server handshake / sign-in picker
                lowerUri == "/" || lowerUri == "" || lowerUri == "/login" || lowerUri.matches(rootHexPathRegex) -> {
                    handleSystemInfo(localIp, config.port, isPublic = true)
                }

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
                    handleUsersPublic(isReefin)
                }
                lowerUri == "/users/authenticatebyname" -> {
                    handleAuthenticateByName(session, params, headers, isInfuse, isReefin)
                }
                lowerUri == "/users/me" -> {
                    handleGetUser(defaultUserName, isReefin)
                }
                // Specific user lookup: /Users/{id}
                lowerUri.matches(Regex("/users/[^/]+")) && !lowerUri.contains("/views") && !lowerUri.contains("/items") && !lowerUri.contains("/groupingoptions") -> {
                    handleGetUser(defaultUserName, isReefin)
                }

                // ================= 3. Library Views & Grouping Options =================
                lowerUri.endsWith("/groupingoptions") -> {
                    handleGroupingOptions()
                }
                lowerUri.endsWith("/views") || lowerUri == "/userviews" -> {
                    handleViews(isInfuse, isReefin)
                }
                lowerUri.startsWith("/library/virtualfolders") -> {
                    handleVirtualFolders(isInfuse, isReefin)
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
                lowerUri.endsWith("/items/resume") || lowerUri.endsWith("/useritems/resume") -> {
                    jsonResponse(mapOf("Items" to emptyList<Any>(), "TotalRecordCount" to 0, "StartIndex" to 0))
                }
                lowerUri.endsWith("/items/latest") || lowerUri.endsWith("/useritems/latest") -> {
                    handleLatestItems(params, localIp, config.port, isInfuse, isReefin)
                }

                // ================= 5. TV Show Hierarchy: Seasons & Episodes =================
                lowerUri.contains("/shows/nextup") || lowerUri.endsWith("/nextup") -> {
                    handleShowsNextUp(cleanUri, params, localIp, config.port, isInfuse, isReefin)
                }
                lowerUri.contains("/shows/") && lowerUri.contains("/seasons") -> {
                    val urlSeriesId = extractSegmentBetween(cleanUri, "shows/", "/seasons").substringBefore("/")
                    val seriesId = if (urlSeriesId.isBlank() || urlSeriesId.equals("seasons", ignoreCase = true)) {
                        getParam(params, "seriesId", "SeriesId", "seriesID", "id", "Id")
                    } else urlSeriesId
                    handleShowSeasons(seriesId, localIp, config.port, isInfuse, isReefin)
                }
                lowerUri.contains("/shows/") && lowerUri.contains("/episodes") -> {
                    val urlSeriesId = extractSegmentBetween(cleanUri, "shows/", "/episodes").substringBefore("/")
                    val seriesId = if (urlSeriesId.isBlank() || urlSeriesId.equals("episodes", ignoreCase = true)) {
                        getParam(params, "seriesId", "SeriesId", "seriesID", "id", "Id")
                    } else urlSeriesId
                    val seasonNum = getParam(params, "season", "Season", "seasonNumber", "SeasonNumber")
                        .toIntOrNull()
                    val seasonId = getParam(params, "seasonId", "SeasonId")
                    handleShowEpisodes(seriesId, seasonNum, seasonId, localIp, config.port, isInfuse, isReefin)
                }
                lowerUri.startsWith("/seasons") -> {
                    val seriesId = getParam(params, "seriesId", "SeriesId", "seriesID", "id", "Id")
                    handleShowSeasons(seriesId, localIp, config.port, isInfuse, isReefin)
                }
                lowerUri.startsWith("/episodes") -> {
                    val seriesId = getParam(params, "seriesId", "SeriesId", "seriesID", "id", "Id")
                    val seasonNum = getParam(params, "season", "Season", "seasonNumber", "SeasonNumber")
                        .toIntOrNull()
                    val seasonId = getParam(params, "seasonId", "SeasonId")
                    handleShowEpisodes(seriesId, seasonNum, seasonId, localIp, config.port, isInfuse, isReefin)
                }
                (lowerUri.startsWith("/shows/") || (lowerUri.contains("/shows/") && !lowerUri.contains("/seasons") && !lowerUri.contains("/episodes") && !lowerUri.contains("/nextup"))) && !lowerUri.contains("/images") -> {
                    val seriesId = extractSegmentBetween(cleanUri, "shows/", "/").ifBlank { cleanUri.substringAfterLast("/") }
                    handleGetSingleItem(seriesId, localIp, config.port, isInfuse, isReefin)
                }

                // ================= 6. Similar Items & Recommendations =================
                lowerUri.endsWith("/similar") -> {
                    handleSimilarItems(cleanUri, params, localIp, config.port, isInfuse, isReefin)
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
                    handleGetSingleItem(itemId, localIp, config.port, isInfuse, isReefin)
                }

                // ================= 7. Library Catalog Queries =================
                lowerUri == "/items" || lowerUri.matches(Regex(".*/users/[^/]+/items")) -> {
                    handleQueryItems(params, localIp, config.port, isInfuse, isReefin)
                }

                // ================= 8. Playback Info (Direct Play Sources) =================
                lowerUri.contains("/playbackinfo") -> {
                    val urlItemId = extractSegmentBetween(cleanUri, "items/", "/playbackinfo")
                        .ifBlank { extractSegmentBetween(cleanUri, "videos/", "/playbackinfo") }
                    val itemId = urlItemId.ifBlank {
                        getParam(params, "ItemId", "itemId", "Id", "id")
                    }
                    handlePlaybackInfo(itemId, localIp, config.port, isReefin)
                }

                // ================= 9. Video Direct Stream Redirect =================
                (lowerUri.contains("/videos/") || lowerUri.contains("/audio/")) &&
                        (lowerUri.contains("/stream") || lowerUri.contains("/master") || lowerUri.contains("/main") || lowerUri.endsWith(".mkv") || lowerUri.endsWith(".mp4")) -> {
                    val itemId = extractSegmentBetween(cleanUri, "videos/", "/stream")
                        .ifBlank { extractSegmentBetween(cleanUri, "videos/", "/master") }
                        .ifBlank { extractSegmentBetween(cleanUri, "videos/", "/main") }
                        .ifBlank { extractSegmentBetween(cleanUri, "videos/", "/") }
                        .ifBlank { extractSegmentBetween(cleanUri, "audio/", "/stream") }
                        .ifBlank { extractSegmentBetween(cleanUri, "audio/", "/") }
                        .removeSuffix(".mkv")
                        .removeSuffix(".mp4")
                        .removeSuffix(".m3u8")
                    handleVideoStreamRedirect(itemId, localIp, config.port)
                }

                // ================= 10. Poster & Backdrop Image Redirect =================
                lowerUri.contains("/images/") -> {
                    val itemId = extractSegmentBetween(cleanUri, "items/", "/images")
                        .ifBlank { extractSegmentBetween(cleanUri, "shows/", "/images") }
                        .ifBlank { extractSegmentBetween(cleanUri, "seasons/", "/images") }
                    val imageType = lowerUri.substringAfter("/images/").substringBefore("/")
                    handleImageRedirect(itemId, imageType, isInfuse)
                }

                // ================= 11. Sessions, Capabilities & Sync Stubs =================
                lowerUri.startsWith("/sessions/playing") -> {
                    noContentResponse()
                }
                lowerUri.startsWith("/sessions/capabilities") -> {
                    noContentResponse()
                }
                lowerUri.startsWith("/displaypreferences") -> {
                    handleDisplayPreferences(isInfuse, isReefin)
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
                    NanoHTTPD.newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Not Found")
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
            "Version" to SERVER_VERSION,
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
            "QuickConnectAvailable" to false,
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

    private fun buildUserDto(username: String, isReefin: Boolean = false): Map<String, Any?> {
        val isoNow = getIsoTimestamp()
        return mapOf(
            "Name" to username,
            "ServerId" to serverId,
            "ServerName" to "NoxMedia",
            "Id" to defaultUserId,
            "HasPassword" to isReefin,
            "HasConfiguredPassword" to isReefin,
            "HasConfiguredEasyPassword" to false,
            "EnableAutoLogin" to !isReefin,
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

    private fun handleUsersPublic(isReefin: Boolean = false): Response {
        return jsonResponse(listOf(buildUserDto(defaultUserName, isReefin)))
    }

    private fun handleAuthenticateByName(
        session: IHTTPSession,
        params: Map<String, String>,
        headers: Map<String, String> = emptyMap(),
        isInfuse: Boolean = false,
        isReefin: Boolean = false
    ): Response {
        var requestedUsername = defaultUserName
        var receivedPassword = ""
        try {
            val map = HashMap<String, String>()
            session.parseBody(map)
            val postData = map["postData"] ?: ""
            if (postData.isNotBlank()) {
                val json = JsonParser.parseString(postData).asJsonObject
                val u = json.get("Username")?.asString ?: json.get("username")?.asString
                val p = json.get("Pw")?.asString ?: json.get("pw")?.asString ?: json.get("Password")?.asString ?: json.get("password")?.asString
                if (!u.isNullOrBlank()) requestedUsername = u
                if (!p.isNullOrBlank()) receivedPassword = p
            }
        } catch (_: Exception) {}

        val paramUser = getParam(params, "Username", "username")
        if (paramUser.isNotBlank()) requestedUsername = paramUser
        val paramPw = getParam(params, "Pw", "pw", "Password", "password")
        if (paramPw.isNotBlank()) receivedPassword = paramPw

        val isoNow = getIsoTimestamp()
        val userObj = buildUserDto(requestedUsername, isReefin)

        val authHeader = headers["x-emby-authorization"] ?: headers["X-Emby-Authorization"] ?: headers["authorization"] ?: ""
        val detectedClient = when {
            isReefin -> "Reefin"
            isInfuse -> "Infuse"
            else -> {
                val match = Regex("""Client="?([^",]+)"?""", RegexOption.IGNORE_CASE).find(authHeader)
                match?.groupValues?.getOrNull(1)?.takeIf { it.isNotBlank() } ?: "Jellyfin Client"
            }
        }
        val detectedDevice = Regex("""Device="?([^",]+)"?""", RegexOption.IGNORE_CASE).find(authHeader)?.groupValues?.getOrNull(1)
            ?: (if (isReefin) "Android / Google TV" else "Client Device")
        val detectedDeviceId = Regex("""DeviceId="?([^",]+)"?""", RegexOption.IGNORE_CASE).find(authHeader)?.groupValues?.getOrNull(1)
            ?: (if (isReefin) "reefin_device_1" else "nox_device_1")

        if (isReefin && !session.remoteIpAddress.isNullOrBlank()) {
            reefinClientIps.add(session.remoteIpAddress)
        }

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
            "Client" to detectedClient,
            "LastActivityDate" to isoNow,
            "DeviceName" to detectedDevice,
            "DeviceId" to detectedDeviceId,
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

    private fun handleGetUser(username: String, isReefin: Boolean = false): Response {
        return jsonResponse(buildUserDto(username, isReefin))
    }

    private fun handleGroupingOptions(): Response {
        val options = listOf(
            mapOf("Name" to "Movies", "Id" to "movies"),
            mapOf("Name" to "TV Shows", "Id" to "shows")
        )
        return jsonResponse(options)
    }

    private fun handleVirtualFolders(isInfuse: Boolean = false, isReefin: Boolean = false): Response {
        val folders = mutableListOf(
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
        if (isInfuse || isReefin) {
            val customCats = catalogRepo.getCatalogs().filter { !it.isHidden }
            for (cat in customCats) {
                val catHex = toHexId("cat_${cat.id}")
                val colType = if (cat.filter.mediaType == "series") "tvshows" else "movies"
                folders.add(
                    mapOf(
                        "Name" to cat.name,
                        "Locations" to listOf("/media/${cat.name}"),
                        "CollectionType" to colType,
                        "ItemId" to catHex,
                        "Guid" to catHex,
                        "RefreshProgress" to 0,
                        "RefreshStatus" to "Idle"
                    )
                )
            }
        }
        return jsonResponse(folders)
    }

    private fun handleViews(isInfuse: Boolean = false, isReefin: Boolean = false): Response {
        val all = tgClient.getAllMedia().filter { !it.isHidden }
        val movieCount = all.count { isMovie(it) }
        val seriesCount = all.filter { isSeries(it) }.distinctBy { it.title.lowercase().trim() }.size
        val animeCount = all.filter { isAnime(it) }.distinctBy { it.title.lowercase().trim() }.size

        val views = mutableListOf(
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

        if (isInfuse || isReefin) {
            val customCats = catalogRepo.getCatalogs().filter { !it.isHidden }
            for (cat in customCats) {
                val catHex = toHexId("cat_${cat.id}")
                val colType = if (cat.filter.mediaType == "series") "tvshows" else "movies"
                views.add(
                    mapOf(
                        "Name" to cat.name,
                        "ServerId" to serverId,
                        "Id" to catHex,
                        "CollectionType" to colType,
                        "Type" to "CollectionFolder",
                        "IsFolder" to true,
                        "CanDelete" to false,
                        "CanDownload" to false,
                        "UserData" to mapOf(
                            "UnplayedItemCount" to 1,
                            "PlaybackPositionTicks" to 0L,
                            "PlayCount" to 0,
                            "IsFavorite" to false,
                            "Played" to false
                        ),
                        "ImageTags" to mapOf("Primary" to catHex),
                        "BackdropImageTags" to emptyList<String>()
                    )
                )
            }
        }

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

    private fun getMovieKey(item: MediaItem): String {
        if (item.tmdbId != null && item.tmdbId!! > 0) return "tmdb_${item.tmdbId}"
        if (!item.imdbId.isNullOrBlank()) return "imdb_${item.imdbId!!.lowercase().trim()}"
        val cleanTitle = item.title.lowercase().replace(seriesSlugCleanRegex, "")
        val yr = item.year ?: ""
        return "title_${cleanTitle}_$yr"
    }

    private fun handleLatestItems(params: Map<String, String>, localIp: String, port: Int, isInfuse: Boolean = false, isReefin: Boolean = false): Response {
        val parentIdParam = getParam(params, "ParentId", "parentId", "parentID", "folderId")
        val cleanParentId = parentIdParam.replace("-", "").lowercase().trim()
        val rawParentId = if (cleanParentId.isNotBlank()) fromHexId(cleanParentId) else ""

        val limit = getParam(params, "Limit", "limit").toIntOrNull() ?: 20
        val includeItemTypes = getParam(params, "IncludeItemTypes", "includeItemTypes", "itemTypes")
            .lowercase().split(",").map { it.trim() }.filter { it.isNotBlank() }

        var allMedia = tgClient.getAllMedia().filter { !it.isHidden }

        val isMoviesView = cleanParentId == libMoviesHex || rawParentId == "lib_movies" ||
                (includeItemTypes.contains("movie") && !includeItemTypes.contains("series") && !includeItemTypes.contains("episode"))
        val isSeriesView = cleanParentId == libSeriesHex || rawParentId == "lib_series" ||
                (includeItemTypes.contains("series") && !includeItemTypes.contains("movie"))
        val isAnimeView = cleanParentId == libAnimeHex || rawParentId == "lib_anime"

        val latestItems: List<Map<String, Any?>> = when {
            isMoviesView -> {
                allMedia.filter { isMovie(it) }
                    .sortedByDescending { it.dateAdded }
                    .distinctBy { getMovieKey(it) }
                    .take(limit)
                    .map { toJellyfinItem(it, localIp, port, isSeriesOverview = false, includeMediaSources = false, isInfuse = isInfuse, isReefin = isReefin) }
            }
            isSeriesView -> {
                ensureTvIndex()
                showsByHex.values.filter { !it.isAnime }
                    .sortedByDescending { it.allEpisodes.maxOfOrNull { ep -> ep.dateAdded } ?: (it.firstEpisode?.dateAdded ?: 0L) }
                    .take(limit)
                    .map { buildSeriesDto(it, localIp, port, isInfuse, isReefin) }
            }
            isAnimeView -> {
                ensureTvIndex()
                showsByHex.values.filter { it.isAnime }
                    .sortedByDescending { it.allEpisodes.maxOfOrNull { ep -> ep.dateAdded } ?: (it.firstEpisode?.dateAdded ?: 0L) }
                    .take(limit)
                    .map { buildSeriesDto(it, localIp, port, isInfuse, isReefin) }
            }
            else -> {
                // Global latest: deduplicate series by title and movies by movie key
                val sorted = allMedia.sortedByDescending { it.dateAdded }
                val distinctLatest = mutableListOf<MediaItem>()
                val seenKeys = mutableSetOf<String>()
                for (item in sorted) {
                    val key = when {
                        isAnime(item) || isSeries(item) -> "show_${item.title.lowercase().trim()}"
                        else -> "movie_${getMovieKey(item)}"
                    }
                    if (seenKeys.add(key)) {
                        distinctLatest.add(item)
                        if (distinctLatest.size >= limit) break
                    }
                }
                distinctLatest.map { item ->
                    val isShow = isSeries(item) || (isAnime(item) && (item.season != null || item.episode != null || item.type == "series" || item.type == "anime"))
                    if (isShow) {
                        val show = findShow(getSeriesSlug(item))
                        if (show != null) buildSeriesDto(show, localIp, port, isInfuse, isReefin)
                        else toJellyfinItem(item, localIp, port, isSeriesOverview = true, includeMediaSources = false, isInfuse = isInfuse, isReefin = isReefin)
                    } else {
                        toJellyfinItem(item, localIp, port, isSeriesOverview = false, includeMediaSources = false, isInfuse = isInfuse, isReefin = isReefin)
                    }
                }
            }
        }

        // Jellyfin OpenAPI specification returns a JSON array of BaseItemDto for Latest items
        return jsonResponse(latestItems)
    }

    private fun handleQueryItems(params: Map<String, String>, localIp: String, port: Int, isInfuse: Boolean = false, isReefin: Boolean = false): Response {
        val parentIdParam = getParam(params, "ParentId", "parentId", "parentID", "folderId", "id")
        val cleanParentId = parentIdParam.replace("-", "").lowercase().trim()
        val rawParentId = if (cleanParentId.isNotBlank()) fromHexId(cleanParentId) else ""

        val idsParam = getParam(params, "Ids", "ids", "itemIds", "ItemIds")
        val seriesIdParam = getParam(params, "SeriesId", "seriesId", "seriesID")
        val seasonIdParam = getParam(params, "SeasonId", "seasonId", "seasonID")

        val searchTerm = getParam(params, "SearchTerm", "searchTerm", "search", "q")
        val includeItemTypes = getParam(params, "IncludeItemTypes", "includeItemTypes", "itemTypes")
            .split(",")
            .map { it.trim().lowercase() }
            .filter { it.isNotBlank() }
        val startIndex = getParam(params, "StartIndex", "startIndex").toIntOrNull() ?: 0
        val limit = getParam(params, "Limit", "limit").toIntOrNull() ?: 100

        var allMedia = tgClient.getAllMedia().filter { !it.isHidden }

        // 0. Query specific items by IDs (Used heavily by Streamyfin, Jellyfin Web, Infuse)
        if (idsParam.isNotBlank()) {
            val requestedIds = idsParam.split(",").map { it.trim() }.filter { it.isNotBlank() }
            val foundItems = requestedIds.mapNotNull { id ->
                val raw = fromHexId(id)
                when {
                    raw == "lib_movies" || id.replace("-", "").lowercase() == libMoviesHex -> {
                        mapOf(
                            "Name" to "Movies",
                            "ServerId" to serverId,
                            "Id" to libMoviesHex,
                            "Type" to "CollectionFolder",
                            "IsFolder" to true,
                            "ImageTags" to mapOf("Primary" to libMoviesHex)
                        )
                    }
                    raw == "lib_series" || id.replace("-", "").lowercase() == libSeriesHex -> {
                        mapOf(
                            "Name" to "TV Shows",
                            "ServerId" to serverId,
                            "Id" to libSeriesHex,
                            "Type" to "CollectionFolder",
                            "IsFolder" to true,
                            "ImageTags" to mapOf("Primary" to libSeriesHex)
                        )
                    }
                    raw == "lib_anime" || id.replace("-", "").lowercase() == libAnimeHex -> {
                        mapOf(
                            "Name" to "Anime",
                            "ServerId" to serverId,
                            "Id" to libAnimeHex,
                            "Type" to "CollectionFolder",
                            "IsFolder" to true,
                            "ImageTags" to mapOf("Primary" to libAnimeHex)
                        )
                    }
                    raw.startsWith("cat_") || catalogRepo.getCatalogs().any { toHexId("cat_${it.id}") == id.replace("-", "").lowercase() } -> {
                        val cat = catalogRepo.getCatalogs().find { toHexId("cat_${it.id}") == id.replace("-", "").lowercase() || "cat_${it.id}" == raw }
                        val name = cat?.name ?: "Catalog"
                        val catHex = if (cat != null) toHexId("cat_${cat.id}") else id.replace("-", "").lowercase()
                        val colType = if (cat?.filter?.mediaType == "series") "tvshows" else "movies"
                        mapOf(
                            "Name" to name,
                            "ServerId" to serverId,
                            "Id" to catHex,
                            "CollectionType" to colType,
                            "Type" to "CollectionFolder",
                            "IsFolder" to true,
                            "ImageTags" to mapOf("Primary" to catHex)
                        )
                    }
                    raw.startsWith("series_") && !raw.contains("_s") -> {
                        val episodes = getEpisodesForSeries(raw)
                        val match = episodes.firstOrNull() ?: allMedia.firstOrNull { isSeries(it) || isAnime(it) }
                        match?.let { toJellyfinItem(it, localIp, port, isSeriesOverview = true, includeMediaSources = false, isInfuse = isInfuse, isReefin = isReefin) }
                    }
                    else -> {
                        val m = findMedia(id)
                        if (m != null) {
                            toJellyfinItem(m, localIp, port, isSeriesOverview = false, includeMediaSources = true, isInfuse = isInfuse, isReefin = isReefin)
                        } else {
                            val eps = getEpisodesForSeries(id)
                            eps.firstOrNull()?.let { toJellyfinItem(it, localIp, port, isSeriesOverview = true, includeMediaSources = false, isInfuse = isInfuse, isReefin = isReefin) }
                        }
                    }
                }
            }
            return jsonResponse(mapOf("Items" to foundItems, "TotalRecordCount" to foundItems.size, "StartIndex" to 0))
        }

        val effectiveParentId = if (cleanParentId.isNotBlank()) cleanParentId else seriesIdParam.replace("-", "").lowercase().trim()
        val effectiveRawParentId = if (effectiveParentId.isNotBlank()) fromHexId(effectiveParentId) else ""
        val effectiveSeasonId = if (seasonIdParam.isNotBlank()) seasonIdParam else effectiveParentId
        val effectiveRawSeasonId = if (effectiveSeasonId.isNotBlank()) fromHexId(effectiveSeasonId) else ""

        if (searchTerm.isNotBlank()) {
            allMedia = allMedia.filter {
                it.title.contains(searchTerm, ignoreCase = true) ||
                        it.fileName.contains(searchTerm, ignoreCase = true) ||
                        (it.episodeTitle?.contains(searchTerm, ignoreCase = true) == true)
            }
        }

        ensureTvIndex()
        val isEpisodeQuery = includeItemTypes.contains("episode")
        val isSeasonQuery = includeItemTypes.contains("season")
        val isSeriesQuery = includeItemTypes.contains("series")
        val isMovieQuery = includeItemTypes.contains("movie")

        val isParentSeason = isSeasonRawId(effectiveRawSeasonId) ||
                isSeasonRawId(effectiveRawParentId) ||
                seasonsByHex.containsKey(cleanParentId)

        val isParentSeries = !isParentSeason && (
                isSeriesRawId(effectiveRawParentId) ||
                showsByHex.containsKey(cleanParentId) ||
                (!seriesIdParam.isNullOrBlank() && (isSeriesRawId(fromHexId(seriesIdParam.replace("-","").lowercase().trim())) || showsByHex.containsKey(seriesIdParam.replace("-","").lowercase().trim())))
        )

        when {
            // 0. Custom NoxMedia Catalogs (Infuse Custom Shelf Drill-Down)
            cleanParentId.startsWith("cat_") || rawParentId.startsWith("cat_") || catalogRepo.getCatalogs().any { toHexId("cat_${it.id}") == cleanParentId } -> {
                val customCat = catalogRepo.getCatalogs().find {
                    toHexId("cat_${it.id}") == cleanParentId || "cat_${it.id}" == rawParentId || it.id == cleanParentId || it.id == rawParentId
                }
                if (customCat != null) {
                    val matched = getCatalogGroupsCached(customCat)
                    val total = matched.size
                    val pagedGroups = matched.drop(startIndex).take(limit)
                    val items = mutableListOf<Map<String, Any?>>()
                    for (group in pagedGroups) {
                        if (group.type == "series") {
                            val show = findShow(group.title) ?: showsByHex.values.firstOrNull { it.title.equals(group.title, ignoreCase = true) }
                            if (show != null) {
                                items.add(buildSeriesDto(show, localIp, port, isInfuse, isReefin))
                            } else {
                                val firstEp = group.items.firstOrNull()
                                if (firstEp != null) {
                                    if (firstEp.posterUrl.isNullOrBlank() && !group.posterUrl.isNullOrBlank()) {
                                        firstEp.posterUrl = group.posterUrl
                                    }
                                    items.add(toJellyfinItem(firstEp, localIp, port, isSeriesOverview = true, includeMediaSources = false, isInfuse = isInfuse, isReefin = isReefin))
                                }
                            }
                        } else {
                            val movie = group.items.firstOrNull() ?: allMedia.firstOrNull { it.id == group.key }
                            if (movie != null) {
                                if (movie.posterUrl.isNullOrBlank() && !group.posterUrl.isNullOrBlank()) {
                                    movie.posterUrl = group.posterUrl
                                }
                                items.add(toJellyfinItem(movie, localIp, port, isSeriesOverview = false, includeMediaSources = false, isInfuse = isInfuse, isReefin = isReefin))
                            }
                        }
                    }
                    return jsonResponse(mapOf("Items" to items, "TotalRecordCount" to total, "StartIndex" to startIndex))
                } else {
                    return jsonResponse(mapOf("Items" to emptyList<Any>(), "TotalRecordCount" to 0, "StartIndex" to 0))
                }
            }

            // 1. LOCKED MOVIE PIPELINE: Movies Folder / View (Deduplicated multi-versions)
            cleanParentId == libMoviesHex || rawParentId == "lib_movies" ||
                    (cleanParentId.isBlank() && isMovieQuery && !isSeriesQuery && !isEpisodeQuery) -> {
                val movies = allMedia.filter { isMovie(it) }.distinctBy { getMovieKey(it) }
                val total = movies.size
                val paged = movies.drop(startIndex).take(limit)
                    .map { toJellyfinItem(it, localIp, port, isSeriesOverview = false, includeMediaSources = false, isInfuse = isInfuse, isReefin = isReefin) }
                return jsonResponse(mapOf("Items" to paged, "TotalRecordCount" to total, "StartIndex" to startIndex))
            }

            // 2. TV EPISODES QUERY (Infuse Season Drill-Down OR Full Library Sync)
            isEpisodeQuery || isParentSeason -> {
                val episodes: List<Map<String, Any?>> = when {
                    // Specific Season drill-down
                    isParentSeason || seasonsByHex.containsKey(cleanParentId) -> {
                        val season = seasonsByHex[cleanParentId]
                            ?: findSeason(seriesIdParam, effectiveSeasonId)
                        if (season != null) {
                            val show = showsByHex[season.seriesHexId]
                            if (show != null) {
                                season.episodes.map { buildEpisodeDto(it, show, season, localIp, port, includeMediaSources = true, isInfuse = isInfuse, isReefin = isReefin) }
                            } else emptyList()
                        } else emptyList()
                    }
                    // Specific Series drill-down
                    isParentSeries || showsByHex.containsKey(effectiveParentId) -> {
                        val show = findShow(effectiveParentId)
                        if (show != null) {
                            val targetSeasonNum = seasonSuffixRegex.find(fromHexId(seasonIdParam))?.groupValues?.getOrNull(1)?.toIntOrNull()
                                ?: seasonIdParam.toIntOrNull()
                            if (targetSeasonNum != null) {
                                val s = show.seasons[targetSeasonNum]
                                if (s != null) s.episodes.map { buildEpisodeDto(it, show, s, localIp, port, includeMediaSources = true, isInfuse = isInfuse, isReefin = isReefin) }
                                else emptyList()
                            } else {
                                show.allEpisodes.map { ep ->
                                    val s = show.seasons[ep.season ?: 1] ?: show.seasons.values.first()
                                    buildEpisodeDto(ep, show, s, localIp, port, includeMediaSources = true, isInfuse = isInfuse, isReefin = isReefin)
                                }
                            }
                        } else emptyList()
                    }
                    // Anime Library Sync
                    cleanParentId == libAnimeHex || rawParentId == "lib_anime" -> {
                        showsByHex.values.filter { it.isAnime }.flatMap { show ->
                            show.allEpisodes.map { ep ->
                                val s = show.seasons[ep.season ?: 1] ?: show.seasons.values.first()
                                buildEpisodeDto(ep, show, s, localIp, port, includeMediaSources = true, isInfuse = isInfuse, isReefin = isReefin)
                            }
                        }
                    }
                    // Global or TV Shows Library Sync (ParentId == libSeriesHex or empty)
                    else -> {
                        showsByHex.values.filter { !it.isAnime }.flatMap { show ->
                            show.allEpisodes.map { ep ->
                                val s = show.seasons[ep.season ?: 1] ?: show.seasons.values.first()
                                buildEpisodeDto(ep, show, s, localIp, port, includeMediaSources = true, isInfuse = isInfuse, isReefin = isReefin)
                            }
                        }
                    }
                }
                val total = episodes.size
                val paged = episodes.drop(startIndex).take(limit)
                return jsonResponse(mapOf("Items" to paged, "TotalRecordCount" to total, "StartIndex" to startIndex))
            }

            // 3. TV SEASONS QUERY (IncludeItemTypes=Season)
            isSeasonQuery -> {
                val targetSeries = if (seriesIdParam.isNotBlank()) seriesIdParam else effectiveParentId
                if (targetSeries.isNotBlank() && targetSeries != libSeriesHex && targetSeries != libAnimeHex) {
                    return handleShowSeasons(targetSeries, localIp, port, isInfuse, isReefin)
                }
                val shows = if (cleanParentId == libAnimeHex || rawParentId == "lib_anime") {
                    showsByHex.values.filter { it.isAnime }
                } else {
                    showsByHex.values.filter { !it.isAnime }
                }
                val seasonItems = mutableListOf<Map<String, Any?>>()
                for (show in shows) {
                    for (season in show.seasons.values.sortedBy { it.seasonNumber }) {
                        seasonItems.add(buildSeasonDto(season, localIp, port, isInfuse, isReefin))
                    }
                }
                val total = seasonItems.size
                val paged = seasonItems.drop(startIndex).take(limit)
                return jsonResponse(mapOf("Items" to paged, "TotalRecordCount" to total, "StartIndex" to startIndex))
            }

            // 4. Parent is a Series -> Return Seasons
            isParentSeries -> {
                val targetSeriesId = when {
                    effectiveRawParentId.startsWith("series_") -> effectiveRawParentId
                    seriesIdParam.isNotBlank() -> seriesIdParam
                    cleanParentId.isNotBlank() -> cleanParentId
                    else -> effectiveParentId
                }
                return handleShowSeasons(targetSeriesId, localIp, port, isInfuse, isReefin)
            }

            // 5. TV Shows Folder / View (Returns Series overview cards)
            cleanParentId == libSeriesHex || rawParentId == "lib_series" ||
                    (cleanParentId.isBlank() && isSeriesQuery && !isMovieQuery) -> {
                val shows = showsByHex.values.filter { !it.isAnime }
                val total = shows.size
                val paged = shows.drop(startIndex).take(limit).map { buildSeriesDto(it, localIp, port, isInfuse, isReefin) }
                return jsonResponse(mapOf("Items" to paged, "TotalRecordCount" to total, "StartIndex" to startIndex))
            }

            // 6. Anime Folder / View
            cleanParentId == libAnimeHex || rawParentId == "lib_anime" -> {
                val shows = showsByHex.values.filter { it.isAnime }
                val total = shows.size
                val paged = shows.drop(startIndex).take(limit).map { buildSeriesDto(it, localIp, port, isInfuse, isReefin) }
                return jsonResponse(mapOf("Items" to paged, "TotalRecordCount" to total, "StartIndex" to startIndex))
            }

            // 7. Global Catalog Query / Fallback
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
                val unique = filtered.distinctBy { if (isSeries(it)) it.title.lowercase().trim() else getMovieKey(it) }
                val total = unique.size
                val paged = unique.drop(startIndex).take(limit).map { item ->
                    val isShow = isSeries(item) || (isAnime(item) && (item.season != null || item.episode != null || item.type == "series" || item.type == "anime"))
                    if (isShow) {
                        val show = findShow(getSeriesSlug(item))
                        if (show != null) buildSeriesDto(show, localIp, port, isInfuse, isReefin)
                        else toJellyfinItem(item, localIp, port, isSeriesOverview = true, includeMediaSources = false, isInfuse = isInfuse, isReefin = isReefin)
                    } else {
                        toJellyfinItem(item, localIp, port, isSeriesOverview = false, includeMediaSources = false, isInfuse = isInfuse, isReefin = isReefin)
                    }
                }
                return jsonResponse(mapOf("Items" to paged, "TotalRecordCount" to total, "StartIndex" to startIndex))
            }
        }
    }

    private fun handleShowsNextUp(
        cleanUri: String,
        params: Map<String, String>,
        localIp: String,
        port: Int,
        isInfuse: Boolean = false,
        isReefin: Boolean = false
    ): Response {
        ensureTvIndex()
        val seriesIdParam = getParam(params, "seriesId", "SeriesId", "id", "Id")
            .ifBlank { extractSegmentBetween(cleanUri, "shows/", "/nextup").substringBefore("/") }
            .let { if (it.equals("nextup", ignoreCase = true)) "" else it }

        if (seriesIdParam.isNotBlank()) {
            val show = findShow(seriesIdParam)
            val nextEp = show?.firstEpisode
            val items = if (show != null && nextEp != null) {
                val season = show.seasons[nextEp.season ?: 1] ?: show.seasons.values.first()
                listOf(buildEpisodeDto(nextEp, show, season, localIp, port, includeMediaSources = true, isInfuse = isInfuse, isReefin = isReefin))
            } else emptyList()
            return jsonResponse(mapOf("Items" to items, "TotalRecordCount" to items.size, "StartIndex" to 0))
        }

        // Global NextUp: 1st episode of each show
        val nextItems = showsByHex.values.mapNotNull { show ->
            val first = show.firstEpisode ?: return@mapNotNull null
            val season = show.seasons[first.season ?: 1] ?: show.seasons.values.firstOrNull() ?: return@mapNotNull null
            buildEpisodeDto(first, show, season, localIp, port, includeMediaSources = true, isInfuse = isInfuse, isReefin = isReefin)
        }.take(20)

        return jsonResponse(mapOf("Items" to nextItems, "TotalRecordCount" to nextItems.size, "StartIndex" to 0))
    }

    private fun handleShowSeasons(seriesId: String, localIp: String, port: Int, isInfuse: Boolean = false, isReefin: Boolean = false): Response {
        val show = findShow(seriesId)
        if (show == null) {
            return jsonResponse(mapOf("Items" to emptyList<Any>(), "TotalRecordCount" to 0, "StartIndex" to 0))
        }
        val seasonList = show.seasons.values
            .sortedBy { it.seasonNumber }
            .map { buildSeasonDto(it, localIp, port, isInfuse, isReefin) }
        return jsonResponse(mapOf("Items" to seasonList, "TotalRecordCount" to seasonList.size, "StartIndex" to 0))
    }

    private fun handleShowEpisodes(
        seriesId: String,
        seasonNum: Int?,
        seasonId: String?,
        localIp: String,
        port: Int,
        isInfuse: Boolean = false,
        isReefin: Boolean = false
    ): Response {
        ensureTvIndex()
        val show = findShow(seriesId)
            ?: (if (!seasonId.isNullOrBlank()) findShow(seasonId) else null)

        if (show == null) {
            return jsonResponse(mapOf("Items" to emptyList<Any>(), "TotalRecordCount" to 0, "StartIndex" to 0))
        }

        val targetSeasonNum = seasonNum
            ?: (if (!seasonId.isNullOrBlank()) {
                seasonsByHex[seasonId.replace("-", "").lowercase().trim()]?.seasonNumber
                    ?: seasonSuffixRegex.find(fromHexId(seasonId))?.groupValues?.getOrNull(1)?.toIntOrNull()
            } else null)

        val episodeDtos = if (targetSeasonNum != null) {
            val season = show.seasons[targetSeasonNum]
            if (season != null) {
                season.episodes.map { buildEpisodeDto(it, show, season, localIp, port, includeMediaSources = true, isInfuse = isInfuse, isReefin = isReefin) }
            } else emptyList()
        } else {
            show.allEpisodes.map { ep ->
                val s = show.seasons[ep.season ?: 1] ?: show.seasons.values.first()
                buildEpisodeDto(ep, show, s, localIp, port, includeMediaSources = true, isInfuse = isInfuse, isReefin = isReefin)
            }
        }

        return jsonResponse(mapOf("Items" to episodeDtos, "TotalRecordCount" to episodeDtos.size, "StartIndex" to 0))
    }

    private fun handleGetSingleItem(itemId: String, localIp: String, port: Int, isInfuse: Boolean = false, isReefin: Boolean = false): Response {
        val cleanId = itemId.replace("-", "").lowercase().trim()
        val rawId = fromHexId(cleanId)

        // 1. Library Root Folders
        if (rawId == "lib_movies" || cleanId == libMoviesHex) {
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
        if (rawId == "lib_series" || cleanId == libSeriesHex) {
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
        if (rawId == "lib_anime" || cleanId == libAnimeHex) {
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

        val singleCat = catalogRepo.getCatalogs().find { toHexId("cat_${it.id}") == cleanId || "cat_${it.id}" == rawId || it.id == cleanId }
        if (singleCat != null) {
            val catHex = toHexId("cat_${singleCat.id}")
            val colType = if (singleCat.filter.mediaType == "series") "tvshows" else "movies"
            return jsonResponse(
                mapOf(
                    "Name" to singleCat.name,
                    "ServerId" to serverId,
                    "Id" to catHex,
                    "CollectionType" to colType,
                    "Type" to "CollectionFolder",
                    "IsFolder" to true,
                    "ImageTags" to mapOf("Primary" to catHex)
                )
            )
        }

        ensureTvIndex()

        // 2. TV Episode Lookup (Exact match on Episode ID or Hex ID)
        val ep = episodesById[cleanId] ?: episodesById[rawId]
        if (ep != null) {
            val parentShow = episodeToShow[cleanId] ?: episodeToShow[ep.id] ?: episodeToShow[toHexId(ep.id)]
            if (parentShow != null) {
                val s = parentShow.seasons[ep.season ?: 1] ?: parentShow.seasons.values.first()
                return jsonResponse(buildEpisodeDto(ep, parentShow, s, localIp, port, includeMediaSources = true, isInfuse = isInfuse, isReefin = isReefin))
            }
        }

        // 3. TV Season Lookup
        val season = seasonsByHex[cleanId] ?: seasonsByHex[toHexId(rawId)]
        if (season != null) {
            return jsonResponse(buildSeasonDto(season, localIp, port, isInfuse, isReefin))
        }

        // 4. TV Series Lookup
        val show = findShow(cleanId)
        if (show != null) {
            return jsonResponse(buildSeriesDto(show, localIp, port, isInfuse, isReefin))
        }

        // 5. Standalone Movie Media Item (LOCKED MOVIE PIPELINE)
        val movieItem = findMedia(cleanId) ?: findMedia(itemId)
        if (movieItem != null) {
            return jsonResponse(toJellyfinItem(movieItem, localIp, port, isSeriesOverview = false, includeMediaSources = true, isInfuse = isInfuse, isReefin = isReefin))
        }

        return NanoHTTPD.newFixedLengthResponse(Response.Status.NOT_FOUND, "application/json", """{"Message":"Item not found"}""")
    }

    private fun handleSimilarItems(cleanUri: String, params: Map<String, String>, localIp: String, port: Int, isInfuse: Boolean = false, isReefin: Boolean = false): Response {
        val itemId = extractSegmentBetween(cleanUri, "items/", "/similar")
            .ifBlank { extractSegmentBetween(cleanUri, "shows/", "/similar") }
            .ifBlank { cleanUri.substringBeforeLast("/").substringAfterLast("/") }
        val target = findMedia(itemId) ?: getEpisodesForSeries(itemId).firstOrNull()
        val limit = getParam(params, "Limit", "limit").toIntOrNull() ?: 12
        val all = tgClient.getAllMedia().filter { !it.isHidden }

        val isTargetSeries = target != null && (isSeries(target) || isAnime(target))
        val targetTitle = target?.title ?: ""

        val similarItems = if (isTargetSeries) {
            all.filter { (isSeries(it) || isAnime(it)) && !it.title.equals(targetTitle, ignoreCase = true) }
                .distinctBy { it.title.lowercase().trim() }
                .take(limit)
                .map { toJellyfinItem(it, localIp, port, isSeriesOverview = true, isInfuse = isInfuse, isReefin = isReefin) }
        } else {
            all.filter { isMovie(it) && it.id != target?.id }
                .distinctBy { it.title.lowercase().trim() }
                .take(limit)
                .map { toJellyfinItem(it, localIp, port, isSeriesOverview = false, isInfuse = isInfuse, isReefin = isReefin) }
        }

        return jsonResponse(
            mapOf(
                "Items" to similarItems,
                "TotalRecordCount" to similarItems.size,
                "StartIndex" to 0
            )
        )
    }

    private fun buildCleanFileName(item: MediaItem): String {
        val ext = if (item.fileName.contains(".")) item.fileName.substringAfterLast(".").lowercase() else "mkv"
        val isSer = item.type.equals("series", ignoreCase = true) || isSeries(item) || isAnime(item) || item.season != null || item.episode != null
        return if (isSer && (item.season != null || item.episode != null)) {
            val s = (item.season ?: 1).toString().padStart(2, '0')
            val e = (item.episode ?: 1).toString().padStart(2, '0')
            val cleanTitle = item.title.replace(Regex("(?i)[._-]"), " ").trim()
            val yr = if (!item.year.isNullOrBlank() && !cleanTitle.contains(item.year!!)) " (${item.year})" else ""
            "$cleanTitle$yr S${s}E$e.$ext"
        } else {
            val cleanTitle = item.title.replace(Regex("(?i)[._-]"), " ").trim()
            val yr = if (!item.year.isNullOrBlank() && !cleanTitle.contains(item.year!!)) " (${item.year})" else ""
            "$cleanTitle$yr.$ext"
        }
    }

    private fun resolveMovieDurationTicks(item: MediaItem, matching: List<MediaItem> = emptyList()): Long? {
        if (item.durationSeconds in 2400..18000) return item.durationSeconds.toLong() * 10_000_000L
        val otherDur = matching.firstOrNull { it.durationSeconds in 2400..18000 }?.durationSeconds
        if (otherDur != null && otherDur > 0) {
            item.durationSeconds = otherDur
            return otherDur.toLong() * 10_000_000L
        }
        val enriched = com.tgserver.streamer.metadata.MetadataEnricher.resolveRuntimeSeconds(item)
        if (enriched in 2400..18000) {
            item.durationSeconds = enriched
            return enriched.toLong() * 10_000_000L
        }
        return null
    }

    private fun resolveEpisodeDurationTicks(item: MediaItem, matching: List<MediaItem> = emptyList()): Long? {
        if (item.durationSeconds in 900..7200) return item.durationSeconds.toLong() * 10_000_000L
        val otherDur = matching.firstOrNull { it.durationSeconds in 900..7200 }?.durationSeconds
        if (otherDur != null && otherDur > 0) {
            item.durationSeconds = otherDur
            return otherDur.toLong() * 10_000_000L
        }
        val enriched = com.tgserver.streamer.metadata.MetadataEnricher.resolveRuntimeSeconds(item)
        if (enriched in 900..7200) {
            item.durationSeconds = enriched
            return enriched.toLong() * 10_000_000L
        }
        return null
    }

    private fun buildEpisodeMediaSources(
        item: MediaItem,
        versions: List<MediaItem>,
        showTitle: String,
        sNum: Int,
        localIp: String,
        port: Int,
        isReefin: Boolean = false
    ): List<Map<String, Any?>> {
        val list = if (versions.isNotEmpty()) versions else listOf(item)
        val sortedVersions = list.sortedWith(
            compareByDescending<MediaItem> { it.id == item.id }
                .thenByDescending { if (it.quality.contains("4k", true) || it.qualityBadge.contains("4k", true)) 4 else if (it.quality.contains("1080", true)) 3 else if (it.quality.contains("720", true)) 2 else 1 }
                .thenByDescending { it.sizeBytes }
        )
        return sortedVersions.map { ver ->
            val hexId = toHexId(ver.id)
            val rawFileName = buildCleanFileName(ver)
            val safeFileName = try {
                URLEncoder.encode(rawFileName, "UTF-8").replace("+", "%20")
            } catch (_: Exception) {
                "video.mkv"
            }
            val streamUrl = "http://$localIp:$port/stream/video/${ver.channelId}/${ver.id}/$safeFileName"
            val durationTicks = resolveEpisodeDurationTicks(ver, sortedVersions)
            val ext = rawFileName.substringAfterLast(".", "mkv").lowercase()
            val (vStream, aStream) = resolveMediaStreams(ver)

            val qualityLabel = buildString {
                val q = if (ver.qualityBadge.isNotBlank() && ver.qualityBadge != "Unknown") ver.qualityBadge else ver.quality
                if (q.isNotBlank() && q != "Unknown") append(q)
                val sz = ver.formattedSize
                if (sz.isNotBlank() && sz != "0 MB") {
                    if (length > 0) append(" • ")
                    append(sz)
                }
                if (length == 0) append(ver.title)
            }

            val bitrate = if (ver.durationSeconds > 0 && ver.sizeBytes > 0) (ver.sizeBytes * 8) / ver.durationSeconds else 10_000_000L
            val virtualPath = if (isReefin) streamUrl else "/TV Shows/$showTitle/Season $sNum/$rawFileName"

            val sourceMap = mutableMapOf<String, Any?>(
                "Id" to hexId,
                "Name" to qualityLabel,
                "Path" to virtualPath,
                "Protocol" to "Http",
                "Container" to ext,
                "Size" to ver.sizeBytes,
                "Bitrate" to bitrate,
                "SupportsDirectPlay" to true,
                "SupportsDirectStream" to true,
                "SupportsTranscoding" to !isReefin,
                "DirectStreamUrl" to streamUrl,
                "MediaStreams" to listOf(vStream, aStream)
            )
            if (durationTicks != null && durationTicks > 0) {
                sourceMap["RunTimeTicks"] = durationTicks
            }
            sourceMap
        }
    }

    private fun buildMediaSourcesForItem(
        item: MediaItem,
        localIp: String,
        port: Int,
        isReefin: Boolean = false
    ): List<Map<String, Any?>> {
        val all = tgClient.getAllMedia().filter { !it.isHidden }
        val matching: List<MediaItem> = if (isMovie(item)) {
            all.filter {
                isMovie(it) && (it.id == item.id ||
                    (!item.imdbId.isNullOrBlank() && it.imdbId.equals(item.imdbId, ignoreCase = true)) ||
                    (item.tmdbId != null && item.tmdbId!! > 0 && it.tmdbId == item.tmdbId) ||
                    (it.title.equals(item.title, ignoreCase = true) && (it.year.isNullOrBlank() || item.year.isNullOrBlank() || it.year == item.year)))
            }
        } else {
            val show = findShow(getSeriesSlug(item))
            val versions = show?.allVersions?.filter {
                (it.season ?: 1) == (item.season ?: 1) && (it.episode ?: 1) == (item.episode ?: 1)
            }
            if (!versions.isNullOrEmpty()) versions else listOf(item)
        }

        val versions = if (matching.isNotEmpty()) matching.distinctBy { it.id } else listOf(item)
        val sortedVersions = versions.sortedWith(
            compareByDescending<MediaItem> { it.id == item.id }
                .thenByDescending { if (it.quality.contains("4k", true) || it.qualityBadge.contains("4k", true)) 4 else if (it.quality.contains("1080", true)) 3 else if (it.quality.contains("720", true)) 2 else 1 }
                .thenByDescending { it.sizeBytes }
        )
        return sortedVersions.map { ver ->
            val hexId = toHexId(ver.id)
            val rawFileName = buildCleanFileName(ver)
            val safeFileName = try {
                URLEncoder.encode(rawFileName, "UTF-8").replace("+", "%20")
            } catch (_: Exception) {
                "video.mkv"
            }
            val streamUrl = "http://$localIp:$port/stream/video/${ver.channelId}/${ver.id}/$safeFileName"
            val durationTicks = if (isMovie(ver)) resolveMovieDurationTicks(ver, sortedVersions) else resolveEpisodeDurationTicks(ver, sortedVersions)
            val ext = rawFileName.substringAfterLast(".", "mkv").lowercase()
            val (vStream, aStream) = resolveMediaStreams(ver)

            val qualityLabel = buildString {
                val q = if (ver.qualityBadge.isNotBlank() && ver.qualityBadge != "Unknown") ver.qualityBadge else ver.quality
                if (q.isNotBlank() && q != "Unknown") append(q)
                val sz = ver.formattedSize
                if (sz.isNotBlank() && sz != "0 MB") {
                    if (length > 0) append(" • ")
                    append(sz)
                }
                if (length == 0) append(ver.title)
            }

            val bitrate = if (ver.durationSeconds > 0 && ver.sizeBytes > 0) (ver.sizeBytes * 8) / ver.durationSeconds else 10_000_000L

            val virtualPath = if (isMovie(ver) || isReefin) streamUrl else "/TV Shows/${ver.title}/Season ${ver.season ?: 1}/$rawFileName"

            val sourceMap = mutableMapOf<String, Any?>(
                "Id" to hexId,
                "Name" to qualityLabel,
                "Path" to virtualPath,
                "Protocol" to "Http",
                "Container" to ext,
                "Size" to ver.sizeBytes,
                "Bitrate" to bitrate,
                "SupportsDirectPlay" to true,
                "SupportsDirectStream" to true,
                "SupportsTranscoding" to !isReefin,
                "DirectStreamUrl" to streamUrl,
                "MediaStreams" to listOf(vStream, aStream)
            )
            if (durationTicks != null && durationTicks > 0) {
                sourceMap["RunTimeTicks"] = durationTicks
            }
            sourceMap
        }
    }

    private fun handlePlaybackInfo(
        itemId: String,
        localIp: String,
        port: Int,
        isReefin: Boolean = false
    ): Response {
        ensureTvIndex()
        val clean = itemId.replace("-", "").lowercase().trim()

        // 1. If it's an episode
        val ep = episodesById[clean] ?: episodesById[fromHexId(clean)]
        if (ep != null) {
            val mediaSources = buildMediaSourcesForItem(ep, localIp, port, isReefin)
            return jsonResponse(
                mapOf(
                    "MediaSources" to mediaSources,
                    "PlaySessionId" to toHexId("session_${ep.id}"),
                    "ErrorCode" to null
                )
            )
        }

        // 2. If it's a series or season, fallback to its first episode!
        val show = findShow(clean)
        if (show != null && show.firstEpisode != null) {
            val firstEp = show.firstEpisode
            val mediaSources = buildMediaSourcesForItem(firstEp, localIp, port, isReefin)
            return jsonResponse(
                mapOf(
                    "MediaSources" to mediaSources,
                    "PlaySessionId" to toHexId("session_${firstEp.id}"),
                    "ErrorCode" to null
                )
            )
        }

        // 3. Movie playback (LOCKED MOVIE PIPELINE)
        val movie = findMedia(clean) ?: findMedia(itemId)
        if (movie != null) {
            val mediaSources = buildMediaSourcesForItem(movie, localIp, port, isReefin)
            return jsonResponse(
                mapOf(
                    "MediaSources" to mediaSources,
                    "PlaySessionId" to toHexId("session_${movie.id}"),
                    "ErrorCode" to null
                )
            )
        }

        return jsonResponse(
            mapOf(
                "MediaSources" to emptyList<Any>(),
                "ErrorCode" to null
            )
        )
    }

    private fun handleVideoStreamRedirect(itemId: String, localIp: String, port: Int): Response {
        ensureTvIndex()
        val clean = itemId.replace("-", "").lowercase().trim()

        val item = episodesById[clean]
            ?: episodesById[fromHexId(clean)]
            ?: findShow(clean)?.firstEpisode
            ?: findMedia(clean)
            ?: findMedia(itemId)

        if (item != null) {
            val rawFileName = buildCleanFileName(item)
            val safeFileName = try {
                URLEncoder.encode(rawFileName, "UTF-8").replace("+", "%20")
            } catch (_: Exception) {
                "video.mkv"
            }
            val redirectUrl = "http://$localIp:$port/stream/video/${item.channelId}/${item.id}/$safeFileName"
            val resp = NanoHTTPD.newFixedLengthResponse(Response.Status.TEMPORARY_REDIRECT, "text/plain", "")
            resp.addHeader("Location", redirectUrl)
            resp.addHeader("Accept-Ranges", "bytes")
            resp.addHeader("Access-Control-Allow-Origin", "*")
            return resp
        }
        return NanoHTTPD.newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Video Not Found")
    }

    private fun handleImageRedirect(itemId: String, imageType: String, isInfuse: Boolean = false): Response {
        val clean = itemId.replace("-", "").lowercase().trim()
        val raw = fromHexId(clean)

        val imgCat = catalogRepo.getCatalogs().find { toHexId("cat_${it.id}") == clean || "cat_${it.id}" == raw || it.id == clean }
        if (imgCat != null) {
            val matched = getCatalogGroupsCached(imgCat)
            val firstPoster = matched.firstNotNullOfOrNull { it.posterUrl?.takeIf { p -> p.isNotBlank() } }
                ?: "https://images.metahub.space/poster/medium/placeholder/img"
            val resp = NanoHTTPD.newFixedLengthResponse(Response.Status.REDIRECT, "text/plain", "")
            resp.addHeader("Location", firstPoster)
            resp.addHeader("Cache-Control", "public, max-age=86400, immutable")
            return resp
        }

        ensureTvIndex()
        val ep = episodesById[clean] ?: episodesById[raw]
        val season = seasonsByHex[clean] ?: seasonsByHex[toHexId(raw)]
        val show = if (ep == null && season == null) findShow(clean) else null
        val movie = if (show == null && ep == null && season == null) findMedia(itemId) else null

        val item = ep ?: show?.firstEpisode ?: movie
        val config = configProvider()
        val title = show?.title ?: season?.seriesTitle ?: item?.title ?: ""

        // 1. Logo / ClearArt Image Redirect (Transparent PNG title treatment)
        if (imageType.equals("Logo", ignoreCase = true) || imageType.equals("Art", ignoreCase = true)) {
            val resolvedLogo = if (title.isNotBlank()) {
                posterService.resolveLogo(title, item?.imdbId, item?.tmdbId, config.tmdbApiKey)
            } else null
            if (!resolvedLogo.isNullOrBlank()) {
                val resp = NanoHTTPD.newFixedLengthResponse(Response.Status.REDIRECT, "text/plain", "")
                resp.addHeader("Location", resolvedLogo)
                resp.addHeader("Cache-Control", "public, max-age=86400, immutable")
                return resp
            }
            return NanoHTTPD.newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Logo Not Found")
        }

        // 2. Backdrop Image Redirect (16:9 fanart background)
        if (imageType.equals("Backdrop", ignoreCase = true)) {
            val localBackdrop = item?.backdropUrl?.takeIf { it.isNotBlank() && !it.contains("/poster/") }
                ?: show?.allEpisodes?.firstNotNullOfOrNull { it.backdropUrl?.takeIf { b -> b.isNotBlank() && !b.contains("/poster/") } }
            if (localBackdrop != null) {
                val resp = NanoHTTPD.newFixedLengthResponse(Response.Status.REDIRECT, "text/plain", "")
                resp.addHeader("Location", localBackdrop)
                resp.addHeader("Cache-Control", "public, max-age=86400, immutable")
                return resp
            }
            val resolvedBackdrop = if (title.isNotBlank()) {
                posterService.resolveBackdrop(title, item?.imdbId, item?.tmdbId, config.tmdbApiKey)
            } else null
            val targetUrl = resolvedBackdrop ?: "https://images.metahub.space/background/medium/placeholder/img"
            val resp = NanoHTTPD.newFixedLengthResponse(Response.Status.REDIRECT, "text/plain", "")
            resp.addHeader("Location", targetUrl)
            resp.addHeader("Cache-Control", "public, max-age=86400, immutable")
            return resp
        }

        val isSeriesOverview = show != null && ep == null
        val isEp = ep != null

        val showPoster = if (show != null) {
            show.allEpisodes.firstOrNull { !it.posterUrl.isNullOrBlank() && it.posterUrl != it.episodeThumbnailUrl }?.posterUrl
                ?: show.firstEpisode?.posterUrl
        } else null

        val localPoster = showPoster ?: item?.posterUrl

        // 3. Thumb Image Redirect (Episode screenshot / still or show backdrop)
        if (imageType.equals("Thumb", ignoreCase = true)) {
            val thumbUrl = if (isEp && item != null) {
                item.episodeThumbnailUrl
                    ?: item.backdropUrl
                    ?: localPoster
                    ?: posterService.resolveEpisodeStill(item.title, item.tmdbId, item.season ?: 1, item.episode ?: 1, config.tmdbApiKey)
            } else {
                item?.backdropUrl ?: localPoster
            }
            val targetUrl = thumbUrl ?: "https://images.metahub.space/background/medium/placeholder/img"
            val resp = NanoHTTPD.newFixedLengthResponse(Response.Status.REDIRECT, "text/plain", "")
            resp.addHeader("Location", targetUrl)
            resp.addHeader("Cache-Control", "public, max-age=86400, immutable")
            return resp
        }

        // 4. Primary Image Redirect (Episode still if Episode, or 2:3 vertical poster if Movie/Show)
        val targetUrl = if (isEp && item != null) {
            item.episodeThumbnailUrl
                ?: item.backdropUrl
                ?: localPoster
                ?: "https://images.metahub.space/poster/medium/placeholder/img"
        } else if (!localPoster.isNullOrBlank()) {
            localPoster
        } else if (title.isNotBlank()) {
            val resolved = posterService.resolvePoster(
                title,
                config.customPosterUrl,
                config.customPosterApiKey,
                config.tmdbApiKey,
                config.tvdbApiKey
            )
            if (resolved != null && item != null) {
                item.posterUrl = resolved
                posterService.cachePoster(title, resolved)
            }
            resolved ?: "https://images.metahub.space/poster/medium/placeholder/img"
        } else {
            "https://images.metahub.space/poster/medium/placeholder/img"
        }

        val resp = NanoHTTPD.newFixedLengthResponse(Response.Status.REDIRECT, "text/plain", "")
        resp.addHeader("Location", targetUrl)
        resp.addHeader("Cache-Control", "public, max-age=86400, immutable")
        return resp
    }

    private fun handleDisplayPreferences(isInfuse: Boolean = false, isReefin: Boolean = false): Response {
        val clientName = when {
            isReefin -> "Reefin"
            isInfuse -> "Infuse"
            else -> "Jellyfin Client"
        }
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
            "Client" to clientName
        )
        return jsonResponse(prefs)
    }

    // --- Helper Functions ---

    private fun getSeriesSlug(item: MediaItem): String {
        return item.title.lowercase().replace(seriesSlugCleanRegex, "_").trim('_')
    }

    private fun getSeriesId(item: MediaItem): String {
        return "series_${getSeriesSlug(item)}"
    }

    private fun getSeriesTitleFromId(seriesId: String): String {
        val show = findShow(seriesId)
        if (show != null) return show.title
        val raw = fromHexId(seriesId)
        val slug = raw.removePrefix("series_").substringBefore("_s")
        val all = tgClient.getAllMedia()
        val match = all.firstOrNull { getSeriesSlug(it) == slug }
        return match?.title ?: slug.replace("_", " ")
    }

    private fun getEpisodesForSeries(seriesOrSeasonId: String): List<MediaItem> {
        ensureTvIndex()
        val clean = seriesOrSeasonId.replace("-", "").lowercase().trim()
        val season = seasonsByHex[clean]
        if (season != null) return season.episodes
        val show = findShow(clean)
        if (show != null) return show.allEpisodes
        return emptyList()
    }

    private fun findMedia(id: String): MediaItem? {
        val cleanId = id.trim().removeSuffix(".mkv")
        if (cleanId.isBlank()) return null
        val unhyphenated = cleanId.replace("-", "").lowercase()
        val rawId = fromHexId(unhyphenated)
        val all = tgClient.getAllMedia().filter { !it.isHidden }
        val direct = all.firstOrNull { it.id == rawId }
            ?: all.firstOrNull { it.id == unhyphenated }
            ?: all.firstOrNull { it.id == cleanId }
            ?: all.firstOrNull { toHexId(it.id) == unhyphenated }
            ?: all.firstOrNull { toHexId(it.id) == cleanId }
            ?: (if (rawId.isNotBlank()) all.firstOrNull { it.id.endsWith(rawId) } else null)
            ?: (if (unhyphenated.isNotBlank()) all.firstOrNull { it.id.endsWith(unhyphenated) } else null)
            ?: (if (cleanId.isNotBlank()) all.firstOrNull { it.id.endsWith(cleanId) } else null)
            ?: (if (rawId.isNotBlank()) all.firstOrNull { it.imdbId != null && it.imdbId.equals(rawId, ignoreCase = true) } else null)
        if (direct != null) return direct

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
        val cleanFn = item.fileName.lowercase()
            .replace(Regex("""(?i)4khdhub(\.com|\.org|\.net|\.in)?"""), "")
            .replace(Regex("""(?i)[\s._\-\(\[]*momstealer[\s._\-\)\]]*"""), "")
        val fn = (cleanFn + " " + item.quality + " " + item.qualityBadge).lowercase()

        // 1. Resolution
        val is1080 = fn.contains("1080") || Regex("""\b(fhd)\b""").containsMatchIn(fn)
        val is720 = fn.contains("720") || Regex("""\b(hd)\b""").containsMatchIn(fn)
        val is480 = fn.contains("480") || Regex("""\b(sd)\b""").containsMatchIn(fn)
        val is4K = fn.contains("2160") || (Regex("""\b(4k|uhd)\b""").containsMatchIn(fn) && !is1080 && !is720)

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
        isSeriesOverview: Boolean = false,
        includeMediaSources: Boolean = false,
        precomputedEpisodeCount: Int? = null,
        precomputedSeasonCount: Int? = null,
        isInfuse: Boolean = false,
        isReefin: Boolean = false
    ): Map<String, Any?> {
        val isSer = isSeries(item)
        val isAni = isAnime(item)
        val rawItemId = if (isSeriesOverview) getSeriesId(item) else item.id
        val hexId = toHexId(rawItemId)

        val rawFileName = buildCleanFileName(item)
        val safeFileName = try {
            URLEncoder.encode(rawFileName, "UTF-8").replace("+", "%20")
        } catch (_: Exception) {
            "video.mkv"
        }
        val streamUrl = "http://$localIp:$port/stream/video/${item.channelId}/${item.id}/$safeFileName"
        val durationTicks = if (isSeriesOverview) null else if (isMovie(item)) resolveMovieDurationTicks(item) else resolveEpisodeDurationTicks(item)
        val yearInt = item.year?.toIntOrNull() ?: 2024
        val ext = rawFileName.substringAfterLast(".", "mkv").lowercase()

        val displayName = when {
            isSeriesOverview -> item.title
            (isSer || isAni || item.episode != null) && item.episode != null -> item.episodeTitle ?: "Episode ${item.episode}"
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
        if (isSeriesOverview && (providerIds["Imdb"].isNullOrBlank() || providerIds["Tmdb"].isNullOrBlank())) {
            val eps = getEpisodesForSeries(rawItemId)
            if (providerIds["Imdb"].isNullOrBlank()) {
                eps.firstNotNullOfOrNull { it.imdbId?.takeIf { id -> id.isNotBlank() } }?.let {
                    providerIds["Imdb"] = it
                }
            }
            if (providerIds["Tmdb"].isNullOrBlank()) {
                eps.firstNotNullOfOrNull { it.tmdbId?.takeIf { id -> id > 0 } }?.let {
                    providerIds["Tmdb"] = it.toString()
                }
            }
        }

        val imageTags = mutableMapOf(
            "Primary" to hexId,
            "Thumb" to hexId,
            "Logo" to hexId,
            "Art" to hexId
        )

        val map = mutableMapOf<String, Any?>(
            "Name" to displayName,
            "OriginalTitle" to item.title,
            "ServerId" to serverId,
            "Id" to hexId,
            "Overview" to (item.overview ?: "${item.qualityBadge} • ${item.formattedSize}"),
            "ProductionYear" to yearInt,
            "CommunityRating" to (if (item.rating > 0) item.rating else 8.0),
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
            "ImageTags" to imageTags,
            "BackdropImageTags" to listOf(hexId),
            "MediaType" to "Video"
        )

        if (durationTicks != null && durationTicks > 0) {
            map["RunTimeTicks"] = durationTicks
        }

        if (isSeriesOverview) {
            val epCount = precomputedEpisodeCount ?: getEpisodesForSeries(rawItemId).size
            val sCount = precomputedSeasonCount ?: 1
            map["Type"] = "Series"
            map["IsFolder"] = true
            map["Status"] = "Continuing"
            map["AirDays"] = listOf("Monday")
            map["DisplayPreferencesId"] = hexId
            map["ChildCount"] = sCount
            map["SeasonCount"] = sCount
            map["EpisodeCount"] = epCount
            map["RecursiveItemCount"] = epCount
            map["PrimaryImageAspectRatio"] = 0.6666667
            map.remove("MediaType")
            map.remove("Container")
            map.remove("Path")
            map.remove("MediaSources")
            map.remove("RunTimeTicks")
            map.remove("CanDownload")
            map.remove("SupportsDirectPlay")
            map.remove("SupportsDirectStream")
            map.remove("SupportsTranscoding")
            map.remove("CollectionType")
            map["UserData"] = mapOf(
                "PlaybackPositionTicks" to 0L,
                "PlayCount" to 0,
                "IsFavorite" to false,
                "Played" to false,
                "UnplayedItemCount" to epCount
            )
        } else if (isSer || isAni || item.episode != null || item.season != null) {
            val seriesRawId = getSeriesId(item)
            val sNum = item.season ?: 1
            val epNum = item.episode ?: 1
            val seasonRawId = "${seriesRawId}_s$sNum"
            val seriesHex = toHexId(seriesRawId)
            val seasonHex = toHexId(seasonRawId)
            map["Type"] = "Episode"
            map["IsFolder"] = false
            map["IndexNumber"] = epNum
            map["ParentIndexNumber"] = sNum
            map["SeriesName"] = item.title
            map["SeriesId"] = seriesHex
            map["SeasonId"] = seasonHex
            map["SeasonName"] = "Season $sNum"
            map["SeriesPrimaryImageTag"] = seriesHex
            map["SeasonPrimaryImageTag"] = seriesHex
            map["ParentLogoItemId"] = seriesHex
            map["ParentLogoImageTag"] = seriesHex
            map["ParentBackdropItemId"] = seriesHex
            map["ParentBackdropImageTags"] = listOf(seriesHex)
            map["BackdropImageTags"] = listOf(seriesHex)
            map["ImageTags"] = mapOf("Primary" to hexId, "Thumb" to hexId)
        } else {
            map["Type"] = "Movie"
            map["IsFolder"] = false
        }

        if (!isSeriesOverview) {
            map["Path"] = streamUrl
            if (includeMediaSources) {
                val mediaSources = buildMediaSourcesForItem(item, localIp, port, isReefin)
                map["MediaSources"] = mediaSources
            }
        }

        return map
    }

    private fun jsonResponse(data: Any, status: Response.Status = Response.Status.OK): Response {
        val json = gson.toJson(data)
        val resp = NanoHTTPD.newFixedLengthResponse(status, "application/json; charset=utf-8", json)
        resp.addHeader("X-Application-Version", SERVER_VERSION)
        return resp
    }

    private fun noContentResponse(): Response {
        val resp = NanoHTTPD.newFixedLengthResponse(Response.Status.NO_CONTENT, "text/plain", "")
        resp.addHeader("X-Application-Version", SERVER_VERSION)
        return resp
    }
}
