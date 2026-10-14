package com.tgserver.streamer.metadata

import android.util.Log
import com.google.gson.JsonParser
import com.tgserver.streamer.data.model.MediaItem
import okhttp3.OkHttpClient
import okhttp3.Request
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
    val kitsuId: Int? = null
)

class MetadataEnricher {

    companion object {
        private val defaultInstance = MetadataEnricher()

        fun enrich(
            item: MediaItem,
            isAnimeChannel: Boolean = false,
            tmdbKey: String = "",
            tvdbKey: String = ""
        ): MediaItem {
            defaultInstance.enrich(item, isAnimeChannel, tmdbKey, tvdbKey)
            return item
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

    fun clearCache() {
        cache.clear()
        showCache.clear()
    }

    fun enrich(
        item: MediaItem,
        isAnimeChannel: Boolean = false,
        tmdbKey: String = "",
        tvdbKey: String = ""
    ) {
        val queryTitle = sanitizeTitle(item.title)
        if (queryTitle.isBlank()) return

        val cacheKey = "$queryTitle:${item.type}:${item.season}:${item.episode}:${isAnimeChannel}"
        cache[cacheKey]?.let { cached ->
            applyMetadata(item, cached)
            return
        }

        // Fast path for series: If show metadata is already cached, reuse it!
        val showKey = "${queryTitle.lowercase()}:${item.type}"
        if (item.type == "series") {
            showCache[showKey]?.let { cachedShow ->
                var epTitle: String? = null
                if (tmdbKey.isNotBlank() && item.season != null && item.episode != null && cachedShow.tmdbId != null) {
                    epTitle = fetchTmdbEpisodeTitle(cachedShow.tmdbId, item.season, item.episode, tmdbKey)
                }
                val enriched = cachedShow.copy(episodeTitle = epTitle ?: cachedShow.episodeTitle)
                cache[cacheKey] = enriched
                applyMetadata(item, enriched)
                return
            }
        }

        var result: EnrichedMetadata? = null

        // 1. Anime Pipeline: Kitsu first
        if (isAnimeChannel) {
            result = tryKitsu(queryTitle)
        }

        // 2. TMDB Pipeline (if API key configured)
        if (tmdbKey.isNotBlank()) {
            val tmdbRes = tryTmdb(queryTitle, item.type, item.season, item.episode, tmdbKey)
            if (tmdbRes != null) {
                result = if (result != null) {
                    result.copy(
                        title = tmdbRes.title ?: result.title,
                        posterUrl = tmdbRes.posterUrl ?: result.posterUrl,
                        backdropUrl = tmdbRes.backdropUrl ?: result.backdropUrl,
                        overview = tmdbRes.overview ?: result.overview,
                        episodeTitle = tmdbRes.episodeTitle ?: result.episodeTitle,
                        tmdbId = tmdbRes.tmdbId,
                        imdbId = tmdbRes.imdbId ?: result.imdbId,
                        year = tmdbRes.year ?: result.year
                    )
                } else {
                    tmdbRes
                }
            }
        }

        // 3. Cinemeta Fallback & IMDb ID Resolver (Free, no key needed)
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
                } else {
                    cinemetaRes
                }
            }
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
        if (!meta.imdbId.isNullOrBlank()) item.imdbId = meta.imdbId
        if (meta.tmdbId != null && meta.tmdbId > 0) item.tmdbId = meta.tmdbId
        if (meta.kitsuId != null && meta.kitsuId > 0) item.kitsuId = meta.kitsuId
    }

    private fun fetchTmdbEpisodeTitle(tmdbId: Int, season: Int, episode: Int, apiKey: String): String? {
        return try {
            val epUrl = "https://api.themoviedb.org/3/tv/$tmdbId/season/$season/episode/$episode?api_key=$apiKey"
            val epReq = Request.Builder().url(epUrl).build()
            httpClient.newCall(epReq).execute().use { epResp ->
                if (epResp.isSuccessful) {
                    val epBody = epResp.body?.string()
                    if (!epBody.isNullOrBlank()) {
                        val epObj = JsonParser.parseString(epBody).asJsonObject
                        epObj.get("name")?.asString
                    } else null
                } else null
            }
        } catch (_: Exception) {
            null
        }
    }

    // --- Kitsu API (Free, Anime Specialist) ---
    private fun tryKitsu(title: String): EnrichedMetadata? {
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
                        val posterUrl = posterObj?.get("medium")?.asString ?: posterObj?.get("original")?.asString

                        val coverObj = attr.getAsJsonObject("coverImage")
                        val backdropUrl = coverObj?.get("original")?.asString ?: coverObj?.get("large")?.asString

                        bestCandidate = EnrichedMetadata(
                            title = canonicalTitle,
                            year = year,
                            overview = synopsis,
                            posterUrl = posterUrl,
                            backdropUrl = backdropUrl,
                            kitsuId = kitsuId
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

    // --- TMDB API ---
    private fun tryTmdb(
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

                        val posterUrl = if (!posterPath.isNullOrBlank()) "https://image.tmdb.org/t/p/w500$posterPath" else null
                        val backdropUrl = if (!backdropPath.isNullOrBlank()) "https://image.tmdb.org/t/p/original$backdropPath" else null

                        var epTitle: String? = null
                        if (type == "series" && season != null && episode != null) {
                            epTitle = fetchTmdbEpisodeTitle(tmdbId, season, episode, apiKey)
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

                        bestCandidate = EnrichedMetadata(
                            title = officialTitle,
                            year = year,
                            overview = overview,
                            posterUrl = posterUrl,
                            backdropUrl = backdropUrl,
                            episodeTitle = epTitle,
                            tmdbId = tmdbId,
                            imdbId = tmdbImdbId
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
                        val year = obj.get("year")?.asString
                        val poster = obj.get("poster")?.asString
                        val background = obj.get("background")?.asString
                        val desc = obj.get("description")?.asString
                        val imdbId = obj.get("imdb_id")?.asString ?: obj.get("id")?.asString

                        bestCandidate = EnrichedMetadata(
                            title = name,
                            year = year,
                            overview = desc,
                            posterUrl = poster,
                            backdropUrl = background,
                            imdbId = imdbId
                        )
                    }
                }

                // Enforce minimum similarity threshold to reject loose/wrong matches (e.g. "08" -> "08/15")
                if (bestScore >= 0.60) {
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
        val cType = if (type == "movie") "movie" else "series"

        // 1. Direct IMDb ID lookup if input looks like tt1234567
        if (cleanQuery.matches(Regex("(?i)^tt\\d+$"))) {
            try {
                val url = "https://v3-cinemeta.strem.io/meta/$cType/$cleanQuery.json"
                val req = Request.Builder().url(url).header("User-Agent", "NoxMedia/1.0").build()
                httpClient.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val body = resp.body?.string()
                        if (!body.isNullOrBlank()) {
                            val root = JsonParser.parseString(body).asJsonObject
                            val metaObj = root.getAsJsonObject("meta")
                            if (metaObj != null) {
                                val name = metaObj.get("name")?.asString
                                val year = metaObj.get("year")?.asString
                                val poster = metaObj.get("poster")?.asString
                                val bg = metaObj.get("background")?.asString
                                val desc = metaObj.get("description")?.asString
                                val imdbId = metaObj.get("imdb_id")?.asString ?: cleanQuery
                                val candidate = EnrichedMetadata(
                                    title = name,
                                    year = year,
                                    overview = desc,
                                    posterUrl = poster,
                                    backdropUrl = bg,
                                    imdbId = imdbId
                                )
                                results.add(candidate)
                                seenImdb.add(imdbId.lowercase())
                            }
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        // 2. Cinemeta catalog search
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
                                val year = obj.get("year")?.asString
                                val poster = obj.get("poster")?.asString
                                val bg = obj.get("background")?.asString
                                val desc = obj.get("description")?.asString
                                val imdbId = obj.get("imdb_id")?.asString ?: obj.get("id")?.asString
                                if (imdbId != null && !seenImdb.add(imdbId.lowercase())) {
                                    continue
                                }
                                results.add(
                                    EnrichedMetadata(
                                        title = name,
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

        // 3. TMDB search if api key available
        if (tmdbKey.isNotBlank()) {
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
                                for (i in 0 until minOf(items.size(), 8)) {
                                    val obj = items[i].asJsonObject
                                    val tmdbId = obj.get("id").asInt
                                    val name = if (type == "movie") obj.get("title")?.asString else obj.get("name")?.asString
                                    if (name.isNullOrBlank()) continue
                                    val overview = obj.get("overview")?.asString
                                    val posterPath = obj.get("poster_path")?.asString
                                    val bgPath = obj.get("backdrop_path")?.asString
                                    val relDate = if (type == "movie") obj.get("release_date")?.asString else obj.get("first_air_date")?.asString
                                    val year = relDate?.split("-")?.firstOrNull()

                                    val posterUrl = if (!posterPath.isNullOrBlank()) "https://image.tmdb.org/t/p/w500$posterPath" else null
                                    val bgUrl = if (!bgPath.isNullOrBlank()) "https://image.tmdb.org/t/p/original$bgPath" else null

                                    results.add(
                                        EnrichedMetadata(
                                            title = name,
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

        results.sortByDescending { calculateSimilarity(cleanQuery, it.title ?: "") }
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
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun sanitizeTitle(name: String): String {
        return name
            .replace(Regex("(?i)\\.(mkv|mp4|avi|mov|webm)$"), "")
            .replace(Regex("(?i)(2160p|1080p|720p|480p|4k|uhd|hdr|hevc|x264|x265|aac|dts|bluray|web-?dl|web-?rip|proper|repack)"), "")
            .replace(Regex("[\\[\\]\\(\\)\\._-]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}
