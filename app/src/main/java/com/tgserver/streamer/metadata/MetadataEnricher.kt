package com.tgserver.streamer.metadata

import android.util.Log
import com.google.gson.JsonParser
import com.tgserver.streamer.data.model.MediaItem
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

private const val TAG = "MetadataEnricher"

data class EnrichedMetadata(
    val title: String? = null,
    val type: String? = null,
    val year: String? = null,
    val overview: String? = null,
    val posterUrl: String? = null,
    val backdropUrl: String? = null,
    val episodeTitle: String? = null,
    val imdbId: String? = null,
    val tmdbId: Int? = null,
    val kitsuId: Int? = null,
    val runtimeMinutes: Int? = null,
    val rating: Double? = null,
    val episodeThumbnailUrl: String? = null,
    val tvdbId: Int? = null
)

data class EpisodeMetadata(
    val title: String? = null,
    val overview: String? = null,
    val thumbnailUrl: String? = null,
    val runtimeMinutes: Int? = null
)

data class FullMetadataResult(
    val metadata: EnrichedMetadata,
    val episodeTitles: Map<Pair<Int, Int>, String>,
    val episodeThumbnails: Map<Pair<Int, Int>, String> = emptyMap()
)

class MetadataEnricher {

    companion object {
        private val defaultInstance = MetadataEnricher()

        fun enrich(
            item: MediaItem,
            isAnimeChannel: Boolean = false,
            tmdbKey: String = "",
            tvdbKey: String = "",
            animePreference: String = "seasonal"
        ): MediaItem {
            defaultInstance.enrich(item, isAnimeChannel, tmdbKey, tvdbKey, animePreference)
            return item
        }

        fun resolveRuntimeSeconds(item: MediaItem): Int {
            return defaultInstance.resolveRuntimeSeconds(item)
        }

        fun clearCache() {
            defaultInstance.clearCache()
        }

        fun invalidateTitle(title: String) {
            defaultInstance.invalidateTitle(title)
        }

        fun fetchFullMetadataDetails(
            meta: EnrichedMetadata,
            tmdbKey: String = "",
            tvdbKey: String = ""
        ): FullMetadataResult {
            return defaultInstance.fetchFullMetadataDetails(meta, tmdbKey, tvdbKey)
        }

        fun searchCandidates(
            query: String,
            type: String,
            tmdbKey: String = ""
        ): List<EnrichedMetadata> {
            return defaultInstance.searchCandidates(query, type, tmdbKey)
        }

        fun calculateSimilarity(query: String, candidate: String): Double {
            return defaultInstance.calculateSimilarity(query, candidate)
        }
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    private val cache = ConcurrentHashMap<String, EnrichedMetadata>()
    private val showCache = ConcurrentHashMap<String, EnrichedMetadata>()
    private val runtimeCache = ConcurrentHashMap<String, Int>()

    fun clearCache() {
        cache.clear()
        showCache.clear()
        runtimeCache.clear()
    }

    fun invalidateTitle(title: String) {
        val clean = sanitizeTitle(title).lowercase()
        if (clean.isBlank()) return
        cache.keys.removeIf { it.lowercase().contains(clean) }
        showCache.keys.removeIf { it.lowercase().contains(clean) }
    }

    fun fetchFullMetadataDetails(
        meta: EnrichedMetadata,
        tmdbKey: String = "",
        tvdbKey: String = ""
    ): FullMetadataResult {
        val episodeMap = mutableMapOf<Pair<Int, Int>, String>()
        val episodeThumbMap = mutableMapOf<Pair<Int, Int>, String>()
        var resultMeta = meta
        val cType = if (meta.type == "movie") "movie" else "series"

        // 1. TVDB: If tvdbKey is available and type is series/anime
        if (tvdbKey.isNotBlank() && meta.type != "movie") {
            try {
                var tvdbId = meta.tvdbId
                if (tvdbId == null && !meta.title.isNullOrBlank()) {
                    val tvdbRes = tryTvdb(meta.title, meta.type ?: "series", tvdbKey)
                    if (tvdbRes != null && tvdbRes.tvdbId != null) {
                        tvdbId = tvdbRes.tvdbId
                        resultMeta = resultMeta.copy(
                            tvdbId = tvdbId,
                            title = resultMeta.title ?: tvdbRes.title,
                            year = resultMeta.year ?: tvdbRes.year,
                            overview = if (resultMeta.overview.isNullOrBlank()) tvdbRes.overview else resultMeta.overview,
                            posterUrl = resultMeta.posterUrl ?: tvdbRes.posterUrl
                        )
                    }
                }
                if (tvdbId != null) {
                    val token = getTvdbAuthToken(tvdbKey)
                    if (!token.isNullOrBlank()) {
                        val sUrl = "https://api4.thetvdb.com/v4/series/$tvdbId/episodes/default?page=0"
                        val sReq = Request.Builder().url(sUrl).addHeader("Authorization", "Bearer $token").build()
                        httpClient.newCall(sReq).execute().use { sResp ->
                            if (sResp.isSuccessful) {
                                val sBody = sResp.body?.string()
                                if (!sBody.isNullOrBlank()) {
                                    val root = JsonParser.parseString(sBody).asJsonObject
                                    val data = root.getAsJsonObject("data")?.getAsJsonArray("episodes")
                                    if (data != null) {
                                        for (epElem in data) {
                                            val epObj = epElem.asJsonObject
                                            val sNum = epObj.get("seasonNumber")?.asInt ?: 1
                                            val eNum = epObj.get("number")?.asInt
                                            val epName = epObj.get("name")?.asString
                                            var epImg = epObj.get("image")?.asString
                                            if (!epImg.isNullOrBlank()) {
                                                if (!epImg.startsWith("http://") && !epImg.startsWith("https://")) {
                                                    epImg = "https://artworks.thetvdb.com/" + epImg.removePrefix("/")
                                                }
                                                if (epImg.endsWith("_t.jpg")) {
                                                    epImg = epImg.replace(Regex("_t\\.jpg$"), ".jpg")
                                                } else if (epImg.contains("/_cache/")) {
                                                    epImg = epImg.replace("/_cache/", "/")
                                                }
                                            }
                                            if (eNum != null) {
                                                if (!epName.isNullOrBlank()) episodeMap[Pair(sNum, eNum)] = epName
                                                if (!epImg.isNullOrBlank()) episodeThumbMap[Pair(sNum, eNum)] = epImg
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.d(TAG, "TVDB full fetch skipped: ${e.message}")
            }
        }

        // 2. TMDB: If TMDB ID is available or IMDb can resolve TMDB ID
        val tmdbId = resultMeta.tmdbId
        if (tmdbKey.isNotBlank() && tmdbId != null && tmdbId > 0) {
            try {
                val ep = if (meta.type == "movie") "movie/$tmdbId" else "tv/$tmdbId"
                val url = "https://api.themoviedb.org/3/$ep?api_key=$tmdbKey&append_to_response=external_ids"
                val req = Request.Builder().url(url).build()
                httpClient.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val body = resp.body?.string()
                        if (!body.isNullOrBlank()) {
                            val obj = JsonParser.parseString(body).asJsonObject
                            val name = if (meta.type == "movie") obj.get("title")?.asString else obj.get("name")?.asString
                            val relDate = if (meta.type == "movie") obj.get("release_date")?.asString else obj.get("first_air_date")?.asString
                            val cleanYear = relDate?.split("-")?.firstOrNull()
                            val desc = obj.get("overview")?.asString
                            val posterPath = obj.get("poster_path")?.asString
                            val bgPath = obj.get("backdrop_path")?.asString
                            val extIds = obj.getAsJsonObject("external_ids")
                            val resolvedImdb = extIds?.get("imdb_id")?.asString

                            resultMeta = resultMeta.copy(
                                title = resultMeta.title ?: name,
                                year = cleanYear ?: resultMeta.year,
                                overview = if (resultMeta.overview.isNullOrBlank()) desc else resultMeta.overview,
                                posterUrl = resultMeta.posterUrl ?: posterPath?.let { "https://image.tmdb.org/t/p/w780$it" },
                                backdropUrl = resultMeta.backdropUrl ?: bgPath?.let { "https://image.tmdb.org/t/p/original$it" },
                                imdbId = resultMeta.imdbId ?: resolvedImdb
                            )

                            // Fetch season episode titles and still thumbnails from TMDB
                            if (meta.type != "movie" && obj.has("number_of_seasons")) {
                                val numSeasons = obj.get("number_of_seasons").asInt
                                for (s in 1..numSeasons) {
                                    val sUrl = "https://api.themoviedb.org/3/tv/$tmdbId/season/$s?api_key=$tmdbKey"
                                    val sReq = Request.Builder().url(sUrl).build()
                                    httpClient.newCall(sReq).execute().use { sResp ->
                                        if (sResp.isSuccessful) {
                                            val sBody = sResp.body?.string()
                                            if (!sBody.isNullOrBlank()) {
                                                val sObj = JsonParser.parseString(sBody).asJsonObject
                                                val eps = sObj.getAsJsonArray("episodes")
                                                if (eps != null) {
                                                    for (epElem in eps) {
                                                        val epObj = epElem.asJsonObject
                                                        val epNum = epObj.get("episode_number")?.asInt
                                                        val epName = epObj.get("name")?.asString
                                                        val stillPath = epObj.get("still_path")?.asString
                                                        val epThumb = if (!stillPath.isNullOrBlank()) "https://image.tmdb.org/t/p/w500$stillPath" else null
                                                        if (epNum != null) {
                                                            if (!epName.isNullOrBlank() && !episodeMap.containsKey(Pair(s, epNum))) {
                                                                episodeMap[Pair(s, epNum)] = epName
                                                            }
                                                            if (!epThumb.isNullOrBlank() && !episodeThumbMap.containsKey(Pair(s, epNum))) {
                                                                episodeThumbMap[Pair(s, epNum)] = epThumb
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        // 3. Kitsu: If Kitsu ID is available (anime fallback/supplement)
        val kitsuId = resultMeta.kitsuId ?: meta.kitsuId
        if (kitsuId != null && kitsuId > 0) {
            try {
                val animeUrl = "https://kitsu.io/api/edge/anime/$kitsuId"
                val req = Request.Builder().url(animeUrl).header("User-Agent", "NoxMedia/1.0").build()
                httpClient.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val body = resp.body?.string()
                        if (!body.isNullOrBlank()) {
                            val root = JsonParser.parseString(body).asJsonObject
                            val dataObj = root.getAsJsonObject("data")
                            val attr = dataObj?.getAsJsonObject("attributes")
                            if (attr != null) {
                                val cTitle = attr.get("canonicalTitle")?.asString
                                val synopsis = attr.get("synopsis")?.asString
                                val startDate = attr.get("startDate")?.asString
                                val cleanYear = startDate?.split("-")?.firstOrNull()
                                val posterObj = attr.getAsJsonObject("posterImage")
                                val poster = posterObj?.get("large")?.asString ?: posterObj?.get("original")?.asString ?: posterObj?.get("medium")?.asString
                                val coverObj = attr.getAsJsonObject("coverImage")
                                val bg = coverObj?.get("original")?.asString ?: coverObj?.get("large")?.asString

                                resultMeta = resultMeta.copy(
                                    title = resultMeta.title ?: cTitle,
                                    year = cleanYear ?: resultMeta.year,
                                    overview = if (resultMeta.overview.isNullOrBlank()) synopsis else resultMeta.overview,
                                    posterUrl = resultMeta.posterUrl ?: poster,
                                    backdropUrl = resultMeta.backdropUrl ?: bg,
                                    kitsuId = kitsuId
                                )
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.d(TAG, "Kitsu anime detail fetch failed: ${e.message}")
            }

            try {
                val epUrl = "https://kitsu.io/api/edge/anime/$kitsuId/episodes?page[limit]=50"
                val epReq = Request.Builder().url(epUrl).header("User-Agent", "NoxMedia/1.0").build()
                httpClient.newCall(epReq).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val body = resp.body?.string()
                        if (!body.isNullOrBlank()) {
                            val root = JsonParser.parseString(body).asJsonObject
                            val data = root.getAsJsonArray("data")
                            if (data != null) {
                                for (epElem in data) {
                                    val epObj = epElem.asJsonObject
                                    val epAttr = epObj.getAsJsonObject("attributes") ?: continue
                                    val epNum = epAttr.get("number")?.asInt ?: epAttr.get("relativeNumber")?.asInt
                                    val seasonNum = epAttr.get("seasonNumber")?.asInt ?: 1
                                    val titlesObj = epAttr.getAsJsonObject("titles")
                                    val epTitle = epAttr.get("canonicalTitle")?.asString
                                        ?: titlesObj?.get("en_us")?.asString
                                        ?: titlesObj?.get("en_jp")?.asString
                                        ?: titlesObj?.get("ja_jp")?.asString
                                    val thumbObj = epAttr.getAsJsonObject("thumbnail")
                                    val thumb = thumbObj?.get("original")?.asString
                                        ?: thumbObj?.get("large")?.asString
                                        ?: thumbObj?.get("medium")?.asString
                                    if (epNum != null) {
                                        if (!epTitle.isNullOrBlank() && !episodeMap.containsKey(Pair(seasonNum, epNum))) {
                                            episodeMap[Pair(seasonNum, epNum)] = epTitle
                                        }
                                        if (!thumb.isNullOrBlank() && !episodeThumbMap.containsKey(Pair(seasonNum, epNum))) {
                                            episodeThumbMap[Pair(seasonNum, epNum)] = thumb
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.d(TAG, "Kitsu episode fetch failed: ${e.message}")
            }
        }

        // 4. Cinemeta / Metahub fallback: If IMDb ID is available
        val imdbId = resultMeta.imdbId ?: meta.imdbId
        if (!imdbId.isNullOrBlank() && imdbId.matches(Regex("(?i)^tt\\d+$"))) {
            try {
                val url = "https://v3-cinemeta.strem.io/meta/$cType/$imdbId.json"
                val req = Request.Builder().url(url).header("User-Agent", "NoxMedia/1.0").build()
                httpClient.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val body = resp.body?.string()
                        if (!body.isNullOrBlank()) {
                            val root = JsonParser.parseString(body).asJsonObject
                            val metaObj = root.getAsJsonObject("meta")
                            if (metaObj != null) {
                                val name = metaObj.get("name")?.asString
                                val rawYear = metaObj.get("year")?.asString ?: metaObj.get("releaseInfo")?.asString
                                val cleanYear = Regex("(?:^|[^0-9])((?:19|20)\\d{2})(?:[^0-9]|$)").find(rawYear ?: "")?.groupValues?.get(1) ?: rawYear
                                val poster = metaObj.get("poster")?.asString
                                val bg = metaObj.get("background")?.asString
                                val desc = metaObj.get("description")?.asString
                                val mRating = metaObj.get("imdbRating")?.asString?.toDoubleOrNull() ?: metaObj.get("rating")?.asString?.toDoubleOrNull()

                                if (metaObj.has("videos")) {
                                    val vids = metaObj.getAsJsonArray("videos")
                                    for (v in vids) {
                                        val vo = v.asJsonObject
                                        val s = vo.get("season")?.asInt ?: 1
                                        val e = vo.get("episode")?.asInt ?: vo.get("number")?.asInt
                                        val epName = vo.get("name")?.asString ?: vo.get("title")?.asString
                                        val epThumb = vo.get("thumbnail")?.asString
                                        if (e != null) {
                                            if (!epName.isNullOrBlank() && !episodeMap.containsKey(Pair(s, e))) {
                                                episodeMap[Pair(s, e)] = epName
                                            }
                                            if (!epThumb.isNullOrBlank() && !episodeThumbMap.containsKey(Pair(s, e))) {
                                                episodeThumbMap[Pair(s, e)] = epThumb
                                            }
                                        }
                                    }
                                }

                                resultMeta = resultMeta.copy(
                                    title = resultMeta.title ?: name,
                                    year = resultMeta.year ?: cleanYear,
                                    overview = if (resultMeta.overview.isNullOrBlank()) desc else resultMeta.overview,
                                    posterUrl = resultMeta.posterUrl ?: poster,
                                    backdropUrl = resultMeta.backdropUrl ?: bg,
                                    imdbId = imdbId,
                                    rating = resultMeta.rating ?: mRating
                                )
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.d(TAG, "Cinemeta full fetch failed: ${e.message}")
            }

            // Fill Metahub episode thumbnails for any episode still missing a thumbnail
            for ((key, _) in episodeMap) {
                if (!episodeThumbMap.containsKey(key)) {
                    episodeThumbMap[key] = "https://episodes.metahub.space/$imdbId/${key.first}/${key.second}/w780.jpg"
                }
            }
        }

        // Final sanitation on year: extract 4 digits if present
        val finalYear = Regex("(?:^|[^0-9])((?:19|20)\\d{2})(?:[^0-9]|$)").find(resultMeta.year ?: "")?.groupValues?.get(1) ?: resultMeta.year
        resultMeta = resultMeta.copy(year = finalYear)

        return FullMetadataResult(resultMeta, episodeMap, episodeThumbMap)
    }

    fun enrich(
        item: MediaItem,
        isAnimeChannel: Boolean = false,
        tmdbKey: String = "",
        tvdbKey: String = "",
        animePreference: String = "seasonal"
    ) {
        val queryTitle = sanitizeTitle(item.title)
        if (queryTitle.isBlank()) return

        val cacheKey = "$queryTitle:${item.type}:${item.season}:${item.episode}:${isAnimeChannel}:$animePreference"
        cache[cacheKey]?.let { cached ->
            applyMetadata(item, cached)
            return
        }

        // Fast path for series: If show metadata is already cached, reuse it!
        val showKey = "${queryTitle.lowercase()}:${item.type}"
        if (item.type == "series") {
            showCache[showKey]?.let { cachedShow ->
                var epTitle: String? = null
                var epThumb: String? = null

                // 1. TVDB episode info
                if (tvdbKey.isNotBlank() && item.season != null && item.episode != null && cachedShow.tvdbId != null) {
                    val tvdbEp = fetchTvdbEpisodeInfo(cachedShow.tvdbId, item.season, item.episode, tvdbKey)
                    if (tvdbEp != null) {
                        epTitle = tvdbEp.title
                        epThumb = tvdbEp.thumbnailUrl
                    }
                }

                // 2. TMDB episode info
                if ((epTitle.isNullOrBlank() || epThumb.isNullOrBlank()) && tmdbKey.isNotBlank() && item.season != null && item.episode != null && cachedShow.tmdbId != null) {
                    val tmdbEp = fetchTmdbEpisodeInfo(cachedShow.tmdbId, item.season, item.episode, tmdbKey)
                    if (epTitle.isNullOrBlank() && !tmdbEp.title.isNullOrBlank()) epTitle = tmdbEp.title
                    if (epThumb.isNullOrBlank() && !tmdbEp.thumbnailUrl.isNullOrBlank()) epThumb = tmdbEp.thumbnailUrl
                }

                // 3. Kitsu episode info (for anime)
                if ((epTitle.isNullOrBlank() || epThumb.isNullOrBlank()) && item.episode != null && cachedShow.kitsuId != null) {
                    val kitsuEp = fetchKitsuEpisodeInfo(cachedShow.kitsuId, item.episode)
                    if (epTitle.isNullOrBlank() && !kitsuEp.title.isNullOrBlank()) epTitle = kitsuEp.title
                    if (epThumb.isNullOrBlank() && !kitsuEp.thumbnailUrl.isNullOrBlank()) epThumb = kitsuEp.thumbnailUrl
                }

                // 4. Metahub fallback for episode thumbnail if IMDb ID exists
                if (epThumb.isNullOrBlank() && !cachedShow.imdbId.isNullOrBlank() && cachedShow.imdbId!!.startsWith("tt", ignoreCase = true) && item.episode != null) {
                    epThumb = "https://episodes.metahub.space/${cachedShow.imdbId}/${item.season ?: 1}/${item.episode}/w780.jpg"
                }

                val enriched = cachedShow.copy(
                    episodeTitle = epTitle ?: cachedShow.episodeTitle,
                    episodeThumbnailUrl = epThumb ?: cachedShow.episodeThumbnailUrl
                )
                cache[cacheKey] = enriched
                applyMetadata(item, enriched)
                return
            }
        }

        var result: EnrichedMetadata? = null

        // Anime Pipeline: TVDB -> TMDB -> Kitsu -> Cinemeta
        if (isAnimeChannel) {
            // 1. TVDB
            if (tvdbKey.isNotBlank()) {
                val tvdbRes = tryTvdb(queryTitle, item.type, tvdbKey, item.season, item.episode)
                if (tvdbRes != null) result = tvdbRes
            }
            // 2. TMDB
            if (tmdbKey.isNotBlank()) {
                val tmdbRes = tryTmdb(queryTitle, item.type, item.season, item.episode, tmdbKey)
                if (tmdbRes != null) {
                    result = if (result != null) {
                        result.copy(
                            title = result.title ?: tmdbRes.title,
                            posterUrl = result.posterUrl ?: tmdbRes.posterUrl,
                            backdropUrl = result.backdropUrl ?: tmdbRes.backdropUrl,
                            overview = result.overview ?: tmdbRes.overview,
                            episodeTitle = result.episodeTitle ?: tmdbRes.episodeTitle,
                            episodeThumbnailUrl = result.episodeThumbnailUrl ?: tmdbRes.episodeThumbnailUrl,
                            tmdbId = tmdbRes.tmdbId,
                            imdbId = tmdbRes.imdbId ?: result.imdbId,
                            year = result.year ?: tmdbRes.year
                        )
                    } else tmdbRes
                }
            }
            // 3. Kitsu
            if (result == null) {
                val kitsuRes = tryKitsu(queryTitle, item.episode)
                if (kitsuRes != null) result = kitsuRes
            } else if (result.episodeThumbnailUrl.isNullOrBlank() && result.kitsuId != null && item.episode != null) {
                val kitsuEp = fetchKitsuEpisodeInfo(result.kitsuId, item.episode)
                if (!kitsuEp.thumbnailUrl.isNullOrBlank()) {
                    result = result.copy(episodeThumbnailUrl = kitsuEp.thumbnailUrl)
                }
            }
        } else {
            // Standard Series/Movie Pipeline: TVDB -> TMDB -> Cinemeta
            if (tvdbKey.isNotBlank() && item.type == "series") {
                val tvdbRes = tryTvdb(queryTitle, item.type, tvdbKey, item.season, item.episode)
                if (tvdbRes != null) result = tvdbRes
            }
            if (tmdbKey.isNotBlank()) {
                val tmdbRes = tryTmdb(queryTitle, item.type, item.season, item.episode, tmdbKey)
                if (tmdbRes != null) {
                    result = if (result != null) {
                        result.copy(
                            title = result.title ?: tmdbRes.title,
                            posterUrl = result.posterUrl ?: tmdbRes.posterUrl,
                            backdropUrl = result.backdropUrl ?: tmdbRes.backdropUrl,
                            overview = result.overview ?: tmdbRes.overview,
                            episodeTitle = result.episodeTitle ?: tmdbRes.episodeTitle,
                            episodeThumbnailUrl = result.episodeThumbnailUrl ?: tmdbRes.episodeThumbnailUrl,
                            tmdbId = tmdbRes.tmdbId,
                            imdbId = tmdbRes.imdbId ?: result.imdbId,
                            year = tmdbRes.year ?: result.year
                        )
                    } else tmdbRes
                }
            }
        }

        // Cinemeta Fallback & IMDb ID Resolver (Free, no key needed)
        if (result == null || result.imdbId.isNullOrBlank()) {
            val cinemetaRes = tryCinemeta(queryTitle, item.type)
            if (cinemetaRes != null) {
                result = if (result != null) {
                    result.copy(
                        imdbId = cinemetaRes.imdbId ?: result.imdbId,
                        posterUrl = result.posterUrl ?: cinemetaRes.posterUrl,
                        backdropUrl = result.backdropUrl ?: cinemetaRes.backdropUrl,
                        overview = if (result.overview.isNullOrBlank()) cinemetaRes.overview else result.overview,
                        year = result.year ?: cinemetaRes.year
                    )
                } else cinemetaRes
            }
        }

        // Metahub fallback for episode thumbnail if IMDb ID exists
        if (result != null && result.episodeThumbnailUrl.isNullOrBlank() && !result.imdbId.isNullOrBlank() && result.imdbId!!.startsWith("tt", ignoreCase = true) && item.episode != null) {
            result = result.copy(
                episodeThumbnailUrl = "https://episodes.metahub.space/${result.imdbId}/${item.season ?: 1}/${item.episode}/w780.jpg"
            )
        }

        if (result != null) {
            cache[cacheKey] = result
            if (item.type == "series") {
                showCache[showKey] = result
            }
            applyMetadata(item, result)
        }
    }

    private fun applyMetadata(item: MediaItem, meta: EnrichedMetadata) {
        if (!meta.title.isNullOrBlank()) item.title = meta.title
        if (!meta.type.isNullOrBlank()) item.type = meta.type
        if (!meta.year.isNullOrBlank()) item.year = meta.year
        if (!meta.overview.isNullOrBlank()) item.overview = meta.overview
        if (!meta.posterUrl.isNullOrBlank()) item.posterUrl = meta.posterUrl
        if (!meta.backdropUrl.isNullOrBlank()) item.backdropUrl = meta.backdropUrl
        if (!meta.episodeTitle.isNullOrBlank()) item.episodeTitle = meta.episodeTitle
        if (!meta.episodeThumbnailUrl.isNullOrBlank()) item.episodeThumbnailUrl = meta.episodeThumbnailUrl
        if (!meta.imdbId.isNullOrBlank()) item.imdbId = meta.imdbId
        if (meta.tmdbId != null && meta.tmdbId > 0) item.tmdbId = meta.tmdbId
        if (meta.kitsuId != null && meta.kitsuId > 0) item.kitsuId = meta.kitsuId
        if (item.durationSeconds <= 0 && meta.runtimeMinutes != null && meta.runtimeMinutes > 0) {
            item.durationSeconds = meta.runtimeMinutes * 60
        }
        if (meta.rating != null && meta.rating > 0.0) {
            item.rating = meta.rating
        }
    }

    fun resolveRuntimeSeconds(item: MediaItem): Int {
        if (item.durationSeconds > 60) return item.durationSeconds

        val imdbId = item.imdbId
        if (!imdbId.isNullOrBlank()) {
            runtimeCache[imdbId]?.let { cachedSec ->
                if (cachedSec > 0) {
                    item.durationSeconds = cachedSec
                    return cachedSec
                }
            }

            try {
                val cType = if (item.type == "movie") "movie" else "series"
                val url = "https://v3-cinemeta.strem.io/meta/$cType/$imdbId.json"
                val req = Request.Builder().url(url).header("User-Agent", "NoxMedia/1.0").build()
                httpClient.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val body = resp.body?.string()
                        if (!body.isNullOrBlank()) {
                            val root = JsonParser.parseString(body).asJsonObject
                            val metaObj = root.getAsJsonObject("meta")
                            var rtStr = metaObj?.get("runtime")?.asString
                            if (rtStr.isNullOrBlank() && item.type == "series" && metaObj != null && metaObj.has("videos")) {
                                val vids = metaObj.getAsJsonArray("videos")
                                for (v in vids) {
                                    val vo = v.asJsonObject
                                    val s = vo.get("season")?.asInt
                                    val e = vo.get("episode")?.asInt ?: vo.get("number")?.asInt
                                    if (s == item.season && e == item.episode) {
                                        rtStr = vo.get("runtime")?.asString
                                        break
                                    }
                                }
                            }
                            if (!rtStr.isNullOrBlank()) {
                                val mins = Regex("(\\d+)").find(rtStr)?.groupValues?.get(1)?.toIntOrNull()
                                if (mins != null && mins > 0) {
                                    val secs = mins * 60
                                    runtimeCache[imdbId] = secs
                                    item.durationSeconds = secs
                                    return secs
                                }
                            }
                        }
                    }
                }
            } catch (_: Exception) {}
        }
        return 0
    }

    private fun fetchTmdbEpisodeTitle(tmdbId: Int, season: Int, episode: Int, apiKey: String): String? {
        return fetchTmdbEpisodeInfo(tmdbId, season, episode, apiKey).title
    }

    private fun fetchTmdbEpisodeInfo(tmdbId: Int, season: Int, episode: Int, apiKey: String): EpisodeMetadata {
        return try {
            val epUrl = "https://api.themoviedb.org/3/tv/$tmdbId/season/$season/episode/$episode?api_key=$apiKey"
            val epReq = Request.Builder().url(epUrl).build()
            httpClient.newCall(epReq).execute().use { epResp ->
                if (epResp.isSuccessful) {
                    val epBody = epResp.body?.string()
                    if (!epBody.isNullOrBlank()) {
                        val epObj = JsonParser.parseString(epBody).asJsonObject
                        val epName = epObj.get("name")?.asString
                        val runtime = epObj.get("runtime")?.asInt
                        val stillPath = epObj.get("still_path")?.asString
                        val epThumb = if (!stillPath.isNullOrBlank()) "https://image.tmdb.org/t/p/w500$stillPath" else null
                        val epOverview = epObj.get("overview")?.asString
                        EpisodeMetadata(epName, epOverview, epThumb, runtime)
                    } else EpisodeMetadata()
                } else EpisodeMetadata()
            }
        } catch (_: Exception) {
            EpisodeMetadata()
        }
    }

    private fun fetchTvdbEpisodeInfo(tvdbId: Int, season: Int, episode: Int, apiKey: String): EpisodeMetadata? {
        val token = getTvdbAuthToken(apiKey) ?: return null
        return try {
            val url = "https://api4.thetvdb.com/v4/series/$tvdbId/episodes/default?season=$season&page=0"
            val req = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .build()
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val body = resp.body?.string() ?: return null
                val root = JsonParser.parseString(body).asJsonObject
                val dataObj = root.getAsJsonObject("data") ?: return null
                val epList = dataObj.getAsJsonArray("episodes") ?: return null
                for (epElem in epList) {
                    val epObj = epElem.asJsonObject
                    val epNum = epObj.get("number")?.asInt
                    val sNum = epObj.get("seasonNumber")?.asInt ?: season
                    if (sNum == season && epNum == episode) {
                        val epName = epObj.get("name")?.asString
                        val epOverview = epObj.get("overview")?.asString
                        val epRuntime = epObj.get("runtime")?.asInt
                        var epImg = epObj.get("image")?.asString
                        if (!epImg.isNullOrBlank()) {
                            if (!epImg.startsWith("http://") && !epImg.startsWith("https://")) {
                                epImg = "https://artworks.thetvdb.com/" + epImg.removePrefix("/")
                            }
                            if (epImg.endsWith("_t.jpg")) {
                                epImg = epImg.replace(Regex("_t\\.jpg$"), ".jpg")
                            } else if (epImg.contains("/_cache/")) {
                                epImg = epImg.replace("/_cache/", "/")
                            }
                        }
                        return EpisodeMetadata(
                            title = epName,
                            overview = epOverview,
                            thumbnailUrl = epImg,
                            runtimeMinutes = epRuntime
                        )
                    }
                }
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun fetchKitsuEpisodeInfo(kitsuId: Int, episode: Int): EpisodeMetadata {
        return try {
            val url = "https://kitsu.io/api/edge/episodes?filter[mediaId]=$kitsuId&filter[mediaType]=Anime&filter[number]=$episode"
            val req = Request.Builder().url(url).header("User-Agent", "NoxMedia/1.0").build()
            httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body?.string()
                    if (!body.isNullOrBlank()) {
                        val root = JsonParser.parseString(body).asJsonObject
                        val data = root.getAsJsonArray("data")
                        val epObj = data?.firstOrNull()?.asJsonObject
                        val attr = epObj?.getAsJsonObject("attributes")
                        if (attr != null) {
                            val canonical = attr.get("canonicalTitle")?.asString
                            val titles = attr.getAsJsonObject("titles")
                            val title = canonical
                                ?: titles?.get("en_us")?.asString
                                ?: titles?.get("en_jp")?.asString
                            val synopsis = attr.get("synopsis")?.asString
                            val thumbObj = attr.getAsJsonObject("thumbnail")
                            val thumb = thumbObj?.get("original")?.asString
                                ?: thumbObj?.get("large")?.asString
                                ?: thumbObj?.get("medium")?.asString
                            val length = attr.get("length")?.asInt
                            EpisodeMetadata(title, synopsis, thumb, length)
                        } else EpisodeMetadata()
                    } else EpisodeMetadata()
                } else EpisodeMetadata()
            }
        } catch (_: Exception) {
            EpisodeMetadata()
        }
    }

    // --- Kitsu API (Free, Anime Specialist) ---
    private fun tryKitsu(title: String, episode: Int? = null): EnrichedMetadata? {
        try {
            val enc = URLEncoder.encode(title, "UTF-8")
            val url = "https://kitsu.io/api/edge/anime?filter[text]=$enc&page[limit]=5"
            val req = Request.Builder().url(url).header("User-Agent", "NoxMedia/1.0").build()

            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val body = resp.body?.string() ?: return null
                val root = JsonParser.parseString(body).asJsonObject
                val data = root.getAsJsonArray("data") ?: return null
                if (data.size() == 0) return null

                var bestCandidate: EnrichedMetadata? = null
                var bestScore = 0.0

                for (i in 0 until minOf(data.size(), 5)) {
                    val itemObj = data[i].asJsonObject
                    val kitsuId = itemObj.get("id")?.asString?.toIntOrNull()
                    val attr = itemObj.getAsJsonObject("attributes") ?: continue

                    val canonicalTitle = attr.get("canonicalTitle")?.asString ?: ""
                    var matchScore = calculateSimilarity(title, canonicalTitle)

                    // Also check alternative titles in Kitsu
                    val titlesObj = attr.getAsJsonObject("titles")
                    if (titlesObj != null) {
                        for (key in listOf("en", "en_jp", "en_us", "ja_jp")) {
                            val altTitle = titlesObj.get(key)?.asString
                            if (!altTitle.isNullOrBlank()) {
                                val altScore = calculateSimilarity(title, altTitle)
                                if (altScore > matchScore) matchScore = altScore
                            }
                        }
                    }

                    if (matchScore > bestScore) {
                        bestScore = matchScore
                        val synopsis = attr.get("synopsis")?.asString
                        val startDate = attr.get("startDate")?.asString
                        val year = startDate?.split("-")?.firstOrNull()

                        val posterObj = attr.getAsJsonObject("posterImage")
                        val posterUrl = posterObj?.get("large")?.asString ?: posterObj?.get("original")?.asString ?: posterObj?.get("medium")?.asString

                        val coverObj = attr.getAsJsonObject("coverImage")
                        val backdropUrl = coverObj?.get("original")?.asString ?: coverObj?.get("large")?.asString

                        var epTitle: String? = null
                        var epThumb: String? = null
                        var epRuntime: Int? = null
                        if (kitsuId != null && episode != null) {
                            val epInfo = fetchKitsuEpisodeInfo(kitsuId, episode)
                            epTitle = epInfo.title
                            epThumb = epInfo.thumbnailUrl
                            epRuntime = epInfo.runtimeMinutes
                        }

                        bestCandidate = EnrichedMetadata(
                            title = canonicalTitle,
                            year = year,
                            overview = synopsis,
                            posterUrl = posterUrl,
                            backdropUrl = backdropUrl,
                            episodeTitle = epTitle,
                            episodeThumbnailUrl = epThumb,
                            kitsuId = kitsuId,
                            runtimeMinutes = epRuntime
                        )
                    }
                }

                if (bestScore >= 0.60) {
                    return bestCandidate
                }
                return null
            }
        } catch (e: Exception) {
            Log.d(TAG, "Kitsu lookup skipped: ${e.message}")
            return null
        }
    }

    // --- TheTVDB (TVDB v4) API ---
    private var tvdbToken: String? = null
    private var tvdbTokenExpiry: Long = 0L

    private fun getTvdbAuthToken(apiKey: String): String? {
        val now = System.currentTimeMillis()
        if (!tvdbToken.isNullOrBlank() && now < tvdbTokenExpiry) {
            return tvdbToken
        }
        try {
            val jsonBody = """{"apikey":"$apiKey"}"""
            val body = jsonBody.toRequestBody("application/json".toMediaType())
            val req = Request.Builder()
                .url("https://api4.thetvdb.com/v4/login")
                .post(body)
                .build()
            httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val respStr = resp.body?.string()
                    if (!respStr.isNullOrBlank()) {
                        val root = JsonParser.parseString(respStr).asJsonObject
                        val token = root.getAsJsonObject("data")?.get("token")?.asString
                        if (!token.isNullOrBlank()) {
                            tvdbToken = token
                            tvdbTokenExpiry = now + 24 * 3600 * 1000L
                            return token
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "TVDB login failed: ${e.message}")
        }
        return null
    }

    private fun tryTvdb(
        title: String,
        type: String,
        apiKey: String,
        season: Int? = null,
        episode: Int? = null
    ): EnrichedMetadata? {
        val token = getTvdbAuthToken(apiKey) ?: return null
        try {
            val enc = URLEncoder.encode(title, "UTF-8")
            val url = "https://api4.thetvdb.com/v4/search?query=$enc"
            val req = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .build()

            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val body = resp.body?.string() ?: return null
                val root = JsonParser.parseString(body).asJsonObject
                val data = root.getAsJsonArray("data") ?: return null
                if (data.size() == 0) return null

                var bestCandidate: EnrichedMetadata? = null
                var bestScore = 0.0

                for (i in 0 until minOf(data.size(), 8)) {
                    val cand = data[i].asJsonObject
                    val name = cand.get("name")?.asString ?: continue
                    val score = calculateSimilarity(title, name)
                    if (score > bestScore) {
                        bestScore = score
                        val year = cand.get("year")?.asString
                        val overview = cand.get("overview")?.asString
                        val rawImg = cand.get("image_url")?.asString ?: cand.get("thumbnail")?.asString
                        val imageUrl = if (!rawImg.isNullOrBlank()) {
                            var fixed = if (!rawImg.startsWith("http://") && !rawImg.startsWith("https://")) {
                                "https://artworks.thetvdb.com/" + rawImg.removePrefix("/")
                            } else {
                                rawImg
                            }
                            if (fixed.endsWith("_t.jpg")) {
                                fixed = fixed.replace(Regex("_t\\.jpg$"), ".jpg")
                            } else if (fixed.contains("/_cache/")) {
                                fixed = fixed.replace("/_cache/", "/")
                            }
                            fixed
                        } else null

                        val tvdbId = cand.get("tvdb_id")?.asString?.toIntOrNull()
                            ?: cand.get("id")?.asString?.removePrefix("series-")?.toIntOrNull()

                        var epTitle: String? = null
                        var epThumb: String? = null
                        var epRuntime: Int? = null
                        if (type == "series" && season != null && episode != null && tvdbId != null) {
                            val epInfo = fetchTvdbEpisodeInfo(tvdbId, season, episode, apiKey)
                            if (epInfo != null) {
                                epTitle = epInfo.title
                                epThumb = epInfo.thumbnailUrl
                                epRuntime = epInfo.runtimeMinutes
                            }
                        }

                        bestCandidate = EnrichedMetadata(
                            title = name,
                            year = year,
                            overview = overview,
                            posterUrl = imageUrl,
                            backdropUrl = null,
                            episodeTitle = epTitle,
                            episodeThumbnailUrl = epThumb,
                            tvdbId = tvdbId,
                            runtimeMinutes = epRuntime
                        )
                    }
                }

                if (bestScore >= 0.60) {
                    return bestCandidate
                }
                return null
            }
        } catch (e: Exception) {
            Log.d(TAG, "TVDB lookup skipped: ${e.message}")
            return null
        }
    }

    // --- TMDB API ---
    private fun tryTmdb(
        title: String,
        type: String,
        season: Int?,
        episode: Int?,
        apiKey: String
    ): EnrichedMetadata? {
        val direct = searchTmdbInternal(title, type, season, episode, apiKey)
        if (direct != null) return direct

        // Cross-type fallback: if movie was requested, try series, and vice-versa
        val altType = if (type == "movie") "series" else "movie"
        val altRes = searchTmdbInternal(title, altType, season, episode, apiKey)
        if (altRes != null) return altRes

        // Strip trailing year (e.g. "Bleach 2004" -> "Bleach")
        val noYear = title.replace(Regex("\\s+(19|20)\\d{2}$"), "").trim()
        if (noYear.isNotBlank() && noYear != title) {
            val noYearRes = searchTmdbInternal(noYear, type, season, episode, apiKey)
                ?: searchTmdbInternal(noYear, altType, season, episode, apiKey)
            if (noYearRes != null) return noYearRes
        }

        return null
    }

    private fun searchTmdbInternal(
        title: String,
        type: String,
        season: Int?,
        episode: Int?,
        apiKey: String
    ): EnrichedMetadata? {
        try {
            val enc = URLEncoder.encode(title, "UTF-8")
            val endpoint = if (type == "movie") "search/movie" else "search/tv"
            val url = "https://api.themoviedb.org/3/$endpoint?api_key=$apiKey&query=$enc"
            val req = Request.Builder().url(url).build()

            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val body = resp.body?.string() ?: return null
                val root = JsonParser.parseString(body).asJsonObject
                val results = root.getAsJsonArray("results") ?: return null
                if (results.size() == 0) return null

                var bestCandidate: EnrichedMetadata? = null
                var bestScore = 0.0

                for (i in 0 until minOf(results.size(), 8)) {
                    val cand = results[i].asJsonObject
                    val officialTitle = if (type == "movie") cand.get("title")?.asString else cand.get("name")?.asString
                    if (officialTitle.isNullOrBlank()) continue

                    val score = calculateSimilarity(title, officialTitle)
                    if (score > bestScore) {
                        bestScore = score
                        val tmdbId = cand.get("id").asInt
                        val overview = cand.get("overview")?.asString
                        val posterPath = cand.get("poster_path")?.asString
                        val backdropPath = cand.get("backdrop_path")?.asString
                        val releaseDate = if (type == "movie") cand.get("release_date")?.asString else cand.get("first_air_date")?.asString
                        val year = releaseDate?.split("-")?.firstOrNull()

                        val posterUrl = if (!posterPath.isNullOrBlank()) "https://image.tmdb.org/t/p/w780$posterPath" else null
                        val backdropUrl = if (!backdropPath.isNullOrBlank()) "https://image.tmdb.org/t/p/original$backdropPath" else null

                        var epTitle: String? = null
                        var epThumb: String? = null
                        var epRuntime: Int? = null
                        if (type == "series" && season != null && episode != null) {
                            val epInfo = fetchTmdbEpisodeInfo(tmdbId, season, episode, apiKey)
                            epTitle = epInfo.title
                            epThumb = epInfo.thumbnailUrl
                            epRuntime = epInfo.runtimeMinutes
                        }

                        // Resolve IMDb ID from TMDB external_ids
                        var tmdbImdbId: String? = null
                        try {
                            val extEndpoint = if (type == "movie") "movie" else "tv"
                            val extUrl = "https://api.themoviedb.org/3/$extEndpoint/$tmdbId/external_ids?api_key=$apiKey"
                            val extReq = Request.Builder().url(extUrl).build()
                            httpClient.newCall(extReq).execute().use { extResp ->
                                if (extResp.isSuccessful) {
                                    val extBody = extResp.body?.string()
                                    if (!extBody.isNullOrBlank()) {
                                        val extObj = JsonParser.parseString(extBody).asJsonObject
                                        tmdbImdbId = extObj.get("imdb_id")?.asString
                                    }
                                }
                            }
                        } catch (_: Exception) {}

                        val candRating = try { cand.get("vote_average")?.asDouble } catch (_: Exception) { null }

                        bestCandidate = EnrichedMetadata(
                            title = officialTitle,
                            year = year,
                            overview = overview,
                            posterUrl = posterUrl,
                            backdropUrl = backdropUrl,
                            episodeTitle = epTitle,
                            episodeThumbnailUrl = epThumb,
                            tmdbId = tmdbId,
                            imdbId = tmdbImdbId,
                            runtimeMinutes = epRuntime,
                            rating = candRating
                        )
                    }
                }

                if (bestScore >= 0.60) {
                    return bestCandidate
                }
                return null
            }
        } catch (e: Exception) {
            Log.d(TAG, "TMDB lookup skipped: ${e.message}")
            return null
        }
    }

    // --- Cinemeta Free API (Stremio Official) ---
    private fun tryCinemeta(title: String, type: String): EnrichedMetadata? {
        val direct = searchCinemetaInternal(title, type)
        if (direct != null) return direct

        // For series, fallback without year (e.g. "Bleach 2004" -> "Bleach")
        if (type == "series") {
            val noYear = title.replace(Regex("\\s+(19|20)\\d{2}$"), "").trim()
            if (noYear.isNotBlank() && noYear != title) {
                val noYearRes = searchCinemetaInternal(noYear, type)
                if (noYearRes != null) return noYearRes
            }
        }

        // Cross-type fallback: if movie was requested, try series, and vice-versa
        val altType = if (type == "movie") "series" else "movie"
        val altRes = searchCinemetaInternal(title, altType)
        if (altRes != null) return altRes

        val words = title.split(" ").filter { it.isNotBlank() }
        if (words.size > 2) {
            val shortened = words.take(3).joinToString(" ")
            val shortRes = searchCinemetaInternal(shortened, type)
            if (shortRes != null) return shortRes
        }
        return null
    }

    private fun searchCinemetaInternal(title: String, type: String): EnrichedMetadata? {
        try {
            val enc = URLEncoder.encode(title, "UTF-8")
            val cType = if (type == "movie") "movie" else "series"
            val url = "https://v3-cinemeta.strem.io/catalog/$cType/top/search=$enc.json"
            val req = Request.Builder().url(url).header("User-Agent", "NoxMedia/1.0").build()

            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val body = resp.body?.string() ?: return null
                val root = JsonParser.parseString(body).asJsonObject
                val metas = root.getAsJsonArray("metas") ?: return null
                if (metas.size() == 0) return null

                var bestCandidate: EnrichedMetadata? = null
                var bestScore = 0.0

                val checkCount = minOf(metas.size(), 8)
                for (i in 0 until checkCount) {
                    val obj = metas[i].asJsonObject
                    val name = obj.get("name")?.asString ?: continue
                    val score = calculateSimilarity(title, name)
                    if (score > bestScore) {
                        bestScore = score
                        val rawYear = obj.get("year")?.asString ?: obj.get("releaseInfo")?.asString
                        val year = Regex("(?:^|[^0-9])((?:19|20)\\d{2})(?:[^0-9]|$)").find(rawYear ?: "")?.groupValues?.get(1) ?: rawYear
                        val poster = obj.get("poster")?.asString
                        val background = obj.get("background")?.asString
                        val desc = obj.get("description")?.asString
                        val imdbId = obj.get("imdb_id")?.asString ?: obj.get("id")?.asString
                        val candRating = obj.get("imdbRating")?.asString?.toDoubleOrNull() ?: obj.get("rating")?.asString?.toDoubleOrNull()

                        bestCandidate = EnrichedMetadata(
                            title = name,
                            year = year,
                            overview = desc,
                            posterUrl = poster,
                            backdropUrl = background,
                            imdbId = imdbId,
                            rating = candRating
                        )
                    }
                }

                // Enforce minimum similarity threshold to reject loose/wrong matches (e.g. "08" -> "08/15")
                if (bestScore >= 0.60 && bestCandidate != null) {
                    val candImdb = bestCandidate.imdbId
                    if (!candImdb.isNullOrBlank()) {
                        try {
                            val metaUrl = "https://v3-cinemeta.strem.io/meta/$cType/$candImdb.json"
                            val mReq = Request.Builder().url(metaUrl).header("User-Agent", "NoxMedia/1.0").build()
                            httpClient.newCall(mReq).execute().use { mResp ->
                                if (mResp.isSuccessful) {
                                    val mBody = mResp.body?.string()
                                    if (!mBody.isNullOrBlank()) {
                                        val mRoot = JsonParser.parseString(mBody).asJsonObject
                                        val mObj = mRoot.getAsJsonObject("meta")
                                        val mRt = mObj?.get("runtime")?.asString
                                        if (!mRt.isNullOrBlank()) {
                                            val mins = Regex("(\\d+)").find(mRt)?.groupValues?.get(1)?.toIntOrNull()
                                            if (mins != null && mins > 0) {
                                                runtimeCache[candImdb] = mins * 60
                                                bestCandidate = bestCandidate.copy(runtimeMinutes = mins)
                                            }
                                        }
                                        val detailRating = mObj?.get("imdbRating")?.asString?.toDoubleOrNull() ?: mObj?.get("rating")?.asString?.toDoubleOrNull()
                                        if (detailRating != null && detailRating > 0.0) {
                                            bestCandidate = bestCandidate.copy(rating = detailRating)
                                        }
                                    }
                                }
                            }
                        } catch (_: Exception) {}
                    }
                    return bestCandidate
                }
                return null
            }
        } catch (e: Exception) {
            Log.d(TAG, "Cinemeta lookup skipped: ${e.message}")
            return null
        }
    }

    fun searchCandidates(
        query: String,
        type: String,
        tmdbKey: String = ""
    ): List<EnrichedMetadata> {
        val cleanQuery = query.trim()
        if (cleanQuery.isBlank()) return emptyList()

        val results = mutableListOf<EnrichedMetadata>()
        val seenImdb = mutableSetOf<String>()
        val seenTmdb = mutableSetOf<Int>()
        val seenKitsu = mutableSetOf<Int>()
        val seenTitleYear = mutableSetOf<String>()
        val isAnime = (type.lowercase() == "anime")
        val cType = if (type == "movie") "movie" else "series"

        // 1. Direct ID Detection (IMDb ID, TMDB ID / URL, or Kitsu ID / URL)
        val imdbMatch = Regex("(?i)(?:^|/|title/)(tt\\d{5,11})(?:/|\\?|$)").find(cleanQuery)?.groupValues?.get(1)
            ?: if (cleanQuery.matches(Regex("(?i)^tt\\d+$"))) cleanQuery else null

        val tmdbMatch = Regex("(?i)themoviedb\\.org/(?:movie|tv)/(\\d+)").find(cleanQuery)?.groupValues?.get(1)
            ?: Regex("(?i)^tmdb[:/\\-](\\d+)$").find(cleanQuery)?.groupValues?.get(1)
            ?: if (!isAnime && imdbMatch == null && cleanQuery.matches(Regex("^\\d{2,9}$"))) cleanQuery else null

        val kitsuMatch = Regex("(?i)kitsu\\.io/anime/(\\d+)").find(cleanQuery)?.groupValues?.get(1)
            ?: Regex("(?i)^kitsu[:/\\-](\\d+)$").find(cleanQuery)?.groupValues?.get(1)
            ?: if (isAnime && imdbMatch == null && cleanQuery.matches(Regex("^\\d{1,8}$"))) cleanQuery else null

        // 1A. Direct Kitsu Anime Lookup
        if (kitsuMatch != null) {
            val kId = kitsuMatch.toIntOrNull()
            if (kId != null && kId > 0 && !seenKitsu.contains(kId)) {
                try {
                    val url = "https://kitsu.io/api/edge/anime/$kId"
                    val req = Request.Builder().url(url).header("User-Agent", "NoxMedia/1.0").build()
                    httpClient.newCall(req).execute().use { resp ->
                        if (resp.isSuccessful) {
                            val body = resp.body?.string()
                            if (!body.isNullOrBlank()) {
                                val root = JsonParser.parseString(body).asJsonObject
                                val dataObj = root.getAsJsonObject("data")
                                if (dataObj != null) {
                                    val attr = dataObj.getAsJsonObject("attributes")
                                    val name = attr?.get("canonicalTitle")?.asString
                                    val startDate = attr?.get("startDate")?.asString
                                    val year = startDate?.split("-")?.firstOrNull()
                                    val overview = attr?.get("synopsis")?.asString
                                    val posterObj = attr?.getAsJsonObject("posterImage")
                                    val posterUrl = posterObj?.get("large")?.asString ?: posterObj?.get("original")?.asString ?: posterObj?.get("medium")?.asString
                                    val coverObj = attr?.getAsJsonObject("coverImage")
                                    val bgUrl = coverObj?.get("original")?.asString ?: coverObj?.get("large")?.asString

                                    val candidate = EnrichedMetadata(
                                        title = name,
                                        type = "anime",
                                        year = year,
                                        overview = overview,
                                        posterUrl = posterUrl,
                                        backdropUrl = bgUrl,
                                        kitsuId = kId
                                    )
                                    results.add(candidate)
                                    seenKitsu.add(kId)
                                    if (name != null) seenTitleYear.add("${name.lowercase().trim()}:${year ?: ""}")
                                }
                            }
                        }
                    }
                } catch (_: Exception) {}
            }
        }

        // 1B. Direct IMDb ID Lookup (TMDB Find API + Cinemeta)
        if (imdbMatch != null) {
            val targetImdb = imdbMatch.lowercase()
            // Priority 1: TMDB /find endpoint using IMDb ID
            if (tmdbKey.isNotBlank()) {
                try {
                    val url = "https://api.themoviedb.org/3/find/$targetImdb?api_key=$tmdbKey&external_source=imdb_id"
                    val req = Request.Builder().url(url).build()
                    httpClient.newCall(req).execute().use { resp ->
                        if (resp.isSuccessful) {
                            val body = resp.body?.string()
                            if (!body.isNullOrBlank()) {
                                val root = JsonParser.parseString(body).asJsonObject
                                val priList = if (type == "movie") root.getAsJsonArray("movie_results") else root.getAsJsonArray("tv_results")
                                val secList = if (type == "movie") root.getAsJsonArray("tv_results") else root.getAsJsonArray("movie_results")
                                val targetArray = if (priList != null && priList.size() > 0) priList else secList
                                if (targetArray != null && targetArray.size() > 0) {
                                    val obj = targetArray[0].asJsonObject
                                    val tId = obj.get("id").asInt
                                    val name = if (obj.has("title")) obj.get("title")?.asString else obj.get("name")?.asString
                                    val relDate = if (obj.has("release_date")) obj.get("release_date")?.asString else obj.get("first_air_date")?.asString
                                    val year = relDate?.split("-")?.firstOrNull()
                                    val overview = obj.get("overview")?.asString
                                    val posterPath = obj.get("poster_path")?.asString
                                    val bgPath = obj.get("backdrop_path")?.asString

                                    val candidate = EnrichedMetadata(
                                        title = name,
                                        type = type,
                                        year = year,
                                        overview = overview,
                                        posterUrl = posterPath?.let { "https://image.tmdb.org/t/p/w780$it" },
                                        backdropUrl = bgPath?.let { "https://image.tmdb.org/t/p/original$it" },
                                        imdbId = targetImdb,
                                        tmdbId = tId
                                    )
                                    results.add(candidate)
                                    seenImdb.add(targetImdb)
                                    seenTmdb.add(tId)
                                    if (name != null) seenTitleYear.add("${name.lowercase().trim()}:${year ?: ""}")
                                }
                            }
                        }
                    }
                } catch (_: Exception) {}
            }

            // Priority 2: Cinemeta direct meta lookup
            if (!seenImdb.contains(targetImdb)) {
                try {
                    val url = "https://v3-cinemeta.strem.io/meta/$cType/$targetImdb.json"
                    val req = Request.Builder().url(url).header("User-Agent", "NoxMedia/1.0").build()
                    httpClient.newCall(req).execute().use { resp ->
                        if (resp.isSuccessful) {
                            val body = resp.body?.string()
                            if (!body.isNullOrBlank()) {
                                val root = JsonParser.parseString(body).asJsonObject
                                val metaObj = root.getAsJsonObject("meta")
                                if (metaObj != null) {
                                    val name = metaObj.get("name")?.asString
                                    val rawYear = metaObj.get("year")?.asString ?: metaObj.get("releaseInfo")?.asString
                                    val year = Regex("(?:^|[^0-9])((?:19|20)\\d{2})(?:[^0-9]|$)").find(rawYear ?: "")?.groupValues?.get(1) ?: rawYear
                                    val poster = metaObj.get("poster")?.asString
                                    val bg = metaObj.get("background")?.asString
                                    val desc = metaObj.get("description")?.asString
                                    val candidate = EnrichedMetadata(
                                        title = name,
                                        type = type,
                                        year = year,
                                        overview = desc,
                                        posterUrl = poster,
                                        backdropUrl = bg,
                                        imdbId = targetImdb
                                    )
                                    results.add(candidate)
                                    seenImdb.add(targetImdb)
                                    if (name != null) seenTitleYear.add("${name.lowercase().trim()}:${year ?: ""}")
                                }
                            }
                        }
                    }
                } catch (_: Exception) {}
            }
        }

        // 1C. Direct TMDB ID Lookup (TMDB API /movie/{id} or /tv/{id} + Cinemeta)
        if (tmdbMatch != null) {
            val tId = tmdbMatch.toIntOrNull()
            if (tId != null && tId > 0 && !seenTmdb.contains(tId)) {
                if (tmdbKey.isNotBlank()) {
                    try {
                        val endpoints = if (type == "movie") listOf("movie", "tv") else listOf("tv", "movie")
                        for (ep in endpoints) {
                            if (seenTmdb.contains(tId)) break
                            val url = "https://api.themoviedb.org/3/$ep/$tId?api_key=$tmdbKey&append_to_response=external_ids"
                            val req = Request.Builder().url(url).build()
                            httpClient.newCall(req).execute().use { resp ->
                                if (resp.isSuccessful) {
                                    val body = resp.body?.string()
                                    if (!body.isNullOrBlank()) {
                                        val obj = JsonParser.parseString(body).asJsonObject
                                        val name = if (obj.has("title")) obj.get("title")?.asString else obj.get("name")?.asString
                                        if (!name.isNullOrBlank()) {
                                            val relDate = if (obj.has("release_date")) obj.get("release_date")?.asString else obj.get("first_air_date")?.asString
                                            val year = relDate?.split("-")?.firstOrNull()
                                            val overview = obj.get("overview")?.asString
                                            val posterPath = obj.get("poster_path")?.asString
                                            val bgPath = obj.get("backdrop_path")?.asString
                                            val extIds = obj.getAsJsonObject("external_ids")
                                            val resolvedImdb = extIds?.get("imdb_id")?.asString

                                            val candidate = EnrichedMetadata(
                                                title = name,
                                                type = type,
                                                year = year,
                                                overview = overview,
                                                posterUrl = posterPath?.let { "https://image.tmdb.org/t/p/w780$it" },
                                                backdropUrl = bgPath?.let { "https://image.tmdb.org/t/p/original$it" },
                                                imdbId = resolvedImdb,
                                                tmdbId = tId
                                            )
                                            results.add(candidate)
                                            seenTmdb.add(tId)
                                            if (resolvedImdb != null) seenImdb.add(resolvedImdb.lowercase())
                                            seenTitleYear.add("${name.lowercase().trim()}:${year ?: ""}")
                                        }
                                    }
                                }
                            }
                        }
                    } catch (_: Exception) {}
                }

                // Fallback: Cinemeta supports tmdb:$tId
                if (!seenTmdb.contains(tId)) {
                    try {
                        val url = "https://v3-cinemeta.strem.io/meta/$cType/tmdb:$tId.json"
                        val req = Request.Builder().url(url).header("User-Agent", "NoxMedia/1.0").build()
                        httpClient.newCall(req).execute().use { resp ->
                            if (resp.isSuccessful) {
                                val body = resp.body?.string()
                                if (!body.isNullOrBlank()) {
                                    val root = JsonParser.parseString(body).asJsonObject
                                    val metaObj = root.getAsJsonObject("meta")
                                    if (metaObj != null) {
                                        val name = metaObj.get("name")?.asString
                                        val rawYear = metaObj.get("year")?.asString ?: metaObj.get("releaseInfo")?.asString
                                        val year = Regex("(?:^|[^0-9])((?:19|20)\\d{2})(?:[^0-9]|$)").find(rawYear ?: "")?.groupValues?.get(1) ?: rawYear
                                        val poster = metaObj.get("poster")?.asString
                                        val bg = metaObj.get("background")?.asString
                                        val desc = metaObj.get("description")?.asString
                                        val resolvedImdb = metaObj.get("imdb_id")?.asString
                                        val candidate = EnrichedMetadata(
                                            title = name,
                                            type = type,
                                            year = year,
                                            overview = desc,
                                            posterUrl = poster,
                                            backdropUrl = bg,
                                            imdbId = resolvedImdb,
                                            tmdbId = tId
                                        )
                                        results.add(candidate)
                                        seenTmdb.add(tId)
                                        if (resolvedImdb != null) seenImdb.add(resolvedImdb.lowercase())
                                        if (name != null) seenTitleYear.add("${name.lowercase().trim()}:${year ?: ""}")
                                    }
                                }
                            }
                        }
                    } catch (_: Exception) {}
                }
            }
        }

        // 2. Kitsu Anime Search (Primary for type == "anime")
        if (isAnime) {
            try {
                val enc = URLEncoder.encode(cleanQuery, "UTF-8")
                val url = "https://kitsu.io/api/edge/anime?filter[text]=$enc&page[limit]=15"
                val req = Request.Builder().url(url).header("User-Agent", "NoxMedia/1.0").build()
                httpClient.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val body = resp.body?.string()
                        if (!body.isNullOrBlank()) {
                            val root = JsonParser.parseString(body).asJsonObject
                            val data = root.getAsJsonArray("data")
                            if (data != null) {
                                for (i in 0 until data.size()) {
                                    val itemObj = data[i].asJsonObject
                                    val kId = itemObj.get("id")?.asString?.toIntOrNull()
                                    if (kId != null && seenKitsu.contains(kId)) continue

                                    val attr = itemObj.getAsJsonObject("attributes") ?: continue
                                    val name = attr.get("canonicalTitle")?.asString ?: ""
                                    if (name.isBlank()) continue

                                    val startDate = attr.get("startDate")?.asString
                                    val year = startDate?.split("-")?.firstOrNull()
                                    val overview = attr.get("synopsis")?.asString

                                    val posterObj = attr.getAsJsonObject("posterImage")
                                    val posterUrl = posterObj?.get("large")?.asString ?: posterObj?.get("original")?.asString ?: posterObj?.get("medium")?.asString

                                    val coverObj = attr.getAsJsonObject("coverImage")
                                    val bgUrl = coverObj?.get("original")?.asString ?: coverObj?.get("large")?.asString

                                    val normKey = "${name.lowercase().trim()}:${year ?: ""}"
                                    if (seenTitleYear.contains(normKey)) continue

                                    if (kId != null) seenKitsu.add(kId)
                                    seenTitleYear.add(normKey)

                                    results.add(
                                        EnrichedMetadata(
                                            title = name,
                                            type = "anime",
                                            year = year,
                                            overview = overview,
                                            posterUrl = posterUrl,
                                            backdropUrl = bgUrl,
                                            kitsuId = kId
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        // 3. High-speed TMDB search (Priority for Movie/Series, Fallback for Anime)
        if (tmdbKey.isNotBlank() && (!isAnime || results.size < 3)) {
            try {
                val enc = URLEncoder.encode(cleanQuery, "UTF-8")
                val endpoint = if (type == "movie") "search/movie" else "search/tv"
                val url = "https://api.themoviedb.org/3/$endpoint?api_key=$tmdbKey&query=$enc"
                val req = Request.Builder().url(url).build()
                httpClient.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val body = resp.body?.string()
                        if (!body.isNullOrBlank()) {
                            val root = JsonParser.parseString(body).asJsonObject
                            val items = root.getAsJsonArray("results")
                            if (items != null) {
                                for (i in 0 until minOf(items.size(), 10)) {
                                    val obj = items[i].asJsonObject
                                    val tmdbId = obj.get("id").asInt
                                    val name = if (type == "movie") obj.get("title")?.asString else obj.get("name")?.asString
                                    if (name.isNullOrBlank()) continue
                                    val overview = obj.get("overview")?.asString
                                    val posterPath = obj.get("poster_path")?.asString
                                    val bgPath = obj.get("backdrop_path")?.asString
                                    val relDate = if (type == "movie") obj.get("release_date")?.asString else obj.get("first_air_date")?.asString
                                    val year = relDate?.split("-")?.firstOrNull()

                                    val posterUrl = if (!posterPath.isNullOrBlank()) "https://image.tmdb.org/t/p/w780$posterPath" else null
                                    val bgUrl = if (!bgPath.isNullOrBlank()) "https://image.tmdb.org/t/p/original$bgPath" else null

                                    seenTmdb.add(tmdbId)
                                    seenTitleYear.add("${name.lowercase().trim()}:${year ?: ""}")

                                    results.add(
                                        EnrichedMetadata(
                                            title = name,
                                            type = type,
                                            year = year,
                                            overview = overview,
                                            posterUrl = posterUrl,
                                            backdropUrl = bgUrl,
                                            tmdbId = tmdbId
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        // 4. Cinemeta catalog search (Fallback, deduplicating against TMDB results)
        if (!isAnime || results.size < 3) {
            try {
                val enc = URLEncoder.encode(cleanQuery, "UTF-8")
                val url = "https://v3-cinemeta.strem.io/catalog/$cType/top/search=$enc.json"
                val req = Request.Builder().url(url).header("User-Agent", "NoxMedia/1.0").build()
                httpClient.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val body = resp.body?.string()
                        if (!body.isNullOrBlank()) {
                            val root = JsonParser.parseString(body).asJsonObject
                            val metas = root.getAsJsonArray("metas")
                            if (metas != null) {
                                for (i in 0 until minOf(metas.size(), 12)) {
                                    val obj = metas[i].asJsonObject
                                    val name = obj.get("name")?.asString ?: continue
                                    val rawYear = obj.get("year")?.asString ?: obj.get("releaseInfo")?.asString
                                    val year = Regex("(?:^|[^0-9])((?:19|20)\\d{2})(?:[^0-9]|$)").find(rawYear ?: "")?.groupValues?.get(1) ?: rawYear
                                    val poster = obj.get("poster")?.asString
                                    val bg = obj.get("background")?.asString
                                    val desc = obj.get("description")?.asString
                                    val imdbId = obj.get("imdb_id")?.asString ?: obj.get("id")?.asString

                                    // Check if already seen via IMDb ID or exact Title+Year from TMDB
                                    val normKey = "${name.lowercase().trim()}:${year ?: ""}"
                                    if (imdbId != null && seenImdb.contains(imdbId.lowercase())) continue
                                    if (seenTitleYear.contains(normKey)) {
                                        // If we already have this from TMDB, enrich the existing item with imdbId if missing
                                        if (!imdbId.isNullOrBlank()) {
                                            val idx = results.indexOfFirst {
                                                it.title.equals(name, ignoreCase = true) && (it.year == year || it.year == null)
                                            }
                                            if (idx >= 0 && results[idx].imdbId.isNullOrBlank()) {
                                                results[idx] = results[idx].copy(imdbId = imdbId)
                                            }
                                        }
                                        continue
                                    }

                                    if (imdbId != null) seenImdb.add(imdbId.lowercase())
                                    seenTitleYear.add(normKey)

                                    results.add(
                                        EnrichedMetadata(
                                            title = name,
                                            type = type,
                                            year = year,
                                            overview = desc,
                                            posterUrl = poster,
                                            backdropUrl = bg,
                                            imdbId = imdbId
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        if (imdbMatch == null && tmdbMatch == null && kitsuMatch == null) {
            results.sortByDescending { calculateSimilarity(cleanQuery, it.title ?: "") }
        }
        return results
    }

    fun calculateSimilarity(query: String, candidate: String): Double {
        val nq = normalizeForComparison(query)
        val nc = normalizeForComparison(candidate)
        if (nq.isBlank() || nc.isBlank()) return 0.0
        if (nq == nc) return 1.0

        val qWords = nq.split(" ").filter { it.isNotBlank() }
        val cWords = nc.split(" ").filter { it.isNotBlank() }
        if (qWords.isEmpty() || cWords.isEmpty()) return 0.0

        val qSet = qWords.toSet()
        val cSet = cWords.toSet()

        val qAlpha = qSet.filter { !it.all { ch -> ch.isDigit() } }.toSet()
        val cAlpha = cSet.filter { !it.all { ch -> ch.isDigit() } }.toSet()

        // If query has alphabetic words, candidate MUST match at least one significant alphabetic word!
        if (qAlpha.isNotEmpty()) {
            val overlapAlpha = qAlpha.intersect(cAlpha)
            if (overlapAlpha.isEmpty()) {
                var substrFound = false
                for (qw in qAlpha) {
                    if (qw.length >= 3 && cAlpha.any { cw -> cw.length >= 3 && (cw.contains(qw) || qw.contains(cw)) }) {
                        substrFound = true
                        break
                    }
                }
                if (!substrFound) return 0.0
            }
        } else {
            // Query is purely numbers: Candidate must match exactly or be identical!
            if (nq != nc) return 0.0
        }

        // Dice coefficient
        val intersection = qSet.intersect(cSet).size
        var dice = 2.0 * intersection / (qSet.size + cSet.size)

        // Substring bonus
        if (nq.contains(nc) || nc.contains(nq)) {
            dice = maxOf(dice, 0.75)
        }

        return dice
    }

    private fun normalizeForComparison(s: String): String {
        return s.lowercase()
            .replace(Regex("^(the|a|an)\\s+"), "")
            .replace(Regex("['’‘`]"), "")
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun sanitizeTitle(name: String): String {
        return name
            .replace(Regex("(?i)\\.(mkv|mp4|avi|mov|webm)$"), "")
            .replace(Regex("(?i)(2160p|1080p|720p|480p|4k|uhd|hdr|hevc|x264|x265|aac|dts|bluray|web-?dl|web-?rip|proper|repack|hq)"), "")
            .replace(Regex("[\\[\\]\\(\\)\\._-]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}
