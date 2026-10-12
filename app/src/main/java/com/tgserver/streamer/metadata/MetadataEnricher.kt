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
    val year: String? = null,
    val overview: String? = null,
    val posterUrl: String? = null,
    val backdropUrl: String? = null,
    val episodeTitle: String? = null,
    val imdbId: String? = null,
    val tmdbId: Int? = null
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
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    private val cache = ConcurrentHashMap<String, EnrichedMetadata>()

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

        var result: EnrichedMetadata? = null

        // 1. Anime Pipeline: Kitsu first
        if (isAnimeChannel) {
            result = tryKitsu(queryTitle)
        }

        // 2. TMDB Pipeline (if API key configured)
        if (tmdbKey.isNotBlank()) {
            val tmdbRes = tryTmdb(queryTitle, item.type, item.season, item.episode, tmdbKey)
            if (tmdbRes != null) {
                // Merge or prioritize TMDB
                result = if (result != null) {
                    result.copy(
                        title = tmdbRes.title ?: result.title,
                        posterUrl = tmdbRes.posterUrl ?: result.posterUrl,
                        backdropUrl = tmdbRes.backdropUrl ?: result.backdropUrl,
                        overview = tmdbRes.overview ?: result.overview,
                        episodeTitle = tmdbRes.episodeTitle ?: result.episodeTitle,
                        tmdbId = tmdbRes.tmdbId,
                        year = tmdbRes.year ?: result.year
                    )
                } else {
                    tmdbRes
                }
            }
        }

        // 3. Cinemeta Fallback (Free, no key needed)
        if (result == null || result.posterUrl.isNullOrBlank()) {
            val cinemetaRes = tryCinemeta(queryTitle, item.type)
            if (cinemetaRes != null) {
                result = if (result != null) {
                    result.copy(
                        posterUrl = result.posterUrl ?: cinemetaRes.posterUrl,
                        backdropUrl = result.backdropUrl ?: cinemetaRes.backdropUrl,
                        overview = result.overview ?: cinemetaRes.overview,
                        imdbId = cinemetaRes.imdbId ?: result.imdbId,
                        year = result.year ?: cinemetaRes.year
                    )
                } else {
                    cinemetaRes
                }
            }
        }

        if (result != null) {
            cache[cacheKey] = result
            applyMetadata(item, result)
        }
    }

    private fun applyMetadata(item: MediaItem, meta: EnrichedMetadata) {
        if (!meta.title.isNullOrBlank()) item.title = meta.title
        if (!meta.year.isNullOrBlank()) item.year = meta.year
        if (!meta.overview.isNullOrBlank()) item.overview = meta.overview
        if (!meta.posterUrl.isNullOrBlank()) item.posterUrl = meta.posterUrl
        if (!meta.backdropUrl.isNullOrBlank()) item.backdropUrl = meta.backdropUrl
        if (!meta.episodeTitle.isNullOrBlank()) item.episodeTitle = meta.episodeTitle
        if (!meta.imdbId.isNullOrBlank()) item.imdbId = meta.imdbId
        if (meta.tmdbId != null && meta.tmdbId > 0) item.tmdbId = meta.tmdbId
    }

    // --- Kitsu API (Free, Anime Specialist) ---
    private fun tryKitsu(title: String): EnrichedMetadata? {
        try {
            val enc = URLEncoder.encode(title, "UTF-8")
            val url = "https://kitsu.io/api/edge/anime?filter[text]=$enc&page[limit]=1"
            val req = Request.Builder().url(url).header("User-Agent", "TG-Server/1.0").build()

            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val body = resp.body?.string() ?: return null
                val root = JsonParser.parseString(body).asJsonObject
                val data = root.getAsJsonArray("data") ?: return null
                if (data.size() == 0) return null

                val first = data[0].asJsonObject
                val attr = first.getAsJsonObject("attributes") ?: return null

                val canonicalTitle = attr.get("canonicalTitle")?.asString
                val synopsis = attr.get("synopsis")?.asString
                val startDate = attr.get("startDate")?.asString
                val year = startDate?.split("-")?.firstOrNull()

                val posterObj = attr.getAsJsonObject("posterImage")
                val posterUrl = posterObj?.get("medium")?.asString ?: posterObj?.get("original")?.asString

                val coverObj = attr.getAsJsonObject("coverImage")
                val backdropUrl = coverObj?.get("original")?.asString ?: coverObj?.get("large")?.asString

                return EnrichedMetadata(
                    title = canonicalTitle,
                    year = year,
                    overview = synopsis,
                    posterUrl = posterUrl,
                    backdropUrl = backdropUrl
                )
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

                val first = results[0].asJsonObject
                val tmdbId = first.get("id").asInt
                val officialTitle = if (type == "movie") {
                    first.get("title")?.asString
                } else {
                    first.get("name")?.asString
                }
                val overview = first.get("overview")?.asString
                val posterPath = first.get("poster_path")?.asString
                val backdropPath = first.get("backdrop_path")?.asString
                val releaseDate = if (type == "movie") {
                    first.get("release_date")?.asString
                } else {
                    first.get("first_air_date")?.asString
                }
                val year = releaseDate?.split("-")?.firstOrNull()

                val posterUrl = if (!posterPath.isNullOrBlank()) "https://image.tmdb.org/t/p/w500$posterPath" else null
                val backdropUrl = if (!backdropPath.isNullOrBlank()) "https://image.tmdb.org/t/p/original$backdropPath" else null

                var epTitle: String? = null
                // Fetch specific episode title for series
                if (type == "series" && season != null && episode != null) {
                    try {
                        val epUrl = "https://api.themoviedb.org/3/tv/$tmdbId/season/$season/episode/$episode?api_key=$apiKey"
                        val epReq = Request.Builder().url(epUrl).build()
                        httpClient.newCall(epReq).execute().use { epResp ->
                            if (epResp.isSuccessful) {
                                val epBody = epResp.body?.string()
                                if (!epBody.isNullOrBlank()) {
                                    val epObj = JsonParser.parseString(epBody).asJsonObject
                                    epTitle = epObj.get("name")?.asString
                                }
                            }
                        }
                    } catch (_: Exception) {}
                }

                return EnrichedMetadata(
                    title = officialTitle,
                    year = year,
                    overview = overview,
                    posterUrl = posterUrl,
                    backdropUrl = backdropUrl,
                    episodeTitle = epTitle,
                    tmdbId = tmdbId
                )
            }
        } catch (e: Exception) {
            Log.d(TAG, "TMDB lookup skipped: ${e.message}")
            return null
        }
    }

    // --- Cinemeta Free API (Stremio Official) ---
    private fun tryCinemeta(title: String, type: String): EnrichedMetadata? {
        try {
            val enc = URLEncoder.encode(title, "UTF-8")
            val cType = if (type == "movie") "movie" else "series"
            val url = "https://v3-cinemeta.strem.io/catalog/$cType/top/search=$enc.json"
            val req = Request.Builder().url(url).header("User-Agent", "TG-Server/1.0").build()

            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val body = resp.body?.string() ?: return null
                val root = JsonParser.parseString(body).asJsonObject
                val metas = root.getAsJsonArray("metas") ?: return null
                if (metas.size() == 0) return null

                val first = metas[0].asJsonObject
                val name = first.get("name")?.asString
                val year = first.get("year")?.asString
                val poster = first.get("poster")?.asString
                val background = first.get("background")?.asString
                val desc = first.get("description")?.asString
                val imdbId = first.get("imdb_id")?.asString ?: first.get("id")?.asString

                return EnrichedMetadata(
                    title = name,
                    year = year,
                    overview = desc,
                    posterUrl = poster,
                    backdropUrl = background,
                    imdbId = imdbId
                )
            }
        } catch (e: Exception) {
            Log.d(TAG, "Cinemeta lookup skipped: ${e.message}")
            return null
        }
    }

    private fun sanitizeTitle(name: String): String {
        return name
            .replace(Regex("(?i)\\.(mkv|mp4|avi|mov|webm)$"), "")
            .replace(Regex("(?i)(2160p|1080p|720p|480p|4k|uhd|hdr|hevc|x264|x265|aac|dts|bluray|web-dl|webrip|proper|repack)"), "")
            .replace(Regex("[\\[\\]\\(\\)\\._-]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}
