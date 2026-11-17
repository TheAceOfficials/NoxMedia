package com.tgserver.streamer.posters

import android.util.Log
import com.google.gson.JsonParser
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

private const val TAG = "SpatialPosterService"

class SpatialPosterService {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .build()

    private val posterCache = ConcurrentHashMap<String, String>()

    fun cachePoster(title: String, posterUrl: String) {
        val cleanQuery = sanitizeTitle(title)
        if (cleanQuery.isNotBlank() && posterUrl.isNotBlank()) {
            posterCache[cleanQuery] = posterUrl
        }
    }

    fun getCachedPoster(title: String): String? {
        val cleanQuery = sanitizeTitle(title)
        return if (cleanQuery.isNotBlank()) posterCache[cleanQuery] else null
    }

    fun resolvePoster(
        rawTitle: String,
        customEndpoint: String = "",
        apiKey: String = "",
        tmdbApiKey: String = "",
        tvdbApiKey: String = ""
    ): String? {
        val cleanQuery = sanitizeTitle(rawTitle)
        if (cleanQuery.isBlank()) return null

        posterCache[cleanQuery]?.let { return it }

        // 1. Try Custom SpatialPosters Endpoint if configured
        if (customEndpoint.isNotBlank()) {
            try {
                val encodedTitle = URLEncoder.encode(cleanQuery, "UTF-8")
                val baseUrl = customEndpoint.trimEnd('/')
                val url = if (baseUrl.contains("?")) {
                    "$baseUrl&title=$encodedTitle"
                } else if (baseUrl.contains("{id}")) {
                    baseUrl.replace("{id}", encodedTitle)
                } else {
                    "$baseUrl/api/posters?title=$encodedTitle"
                }

                val reqBuilder = Request.Builder().url(url)
                if (apiKey.isNotBlank()) {
                    reqBuilder.addHeader("X-Goog-Api-Key", apiKey)
                    reqBuilder.addHeader("X-Api-Key", apiKey)
                    reqBuilder.addHeader("Authorization", "Bearer $apiKey")
                }

                httpClient.newCall(reqBuilder.build()).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string()
                        if (!body.isNullOrBlank()) {
                            val json = JsonParser.parseString(body)
                            if (json.isJsonObject) {
                                val obj = json.asJsonObject
                                val poster = when {
                                    obj.has("posterUrl") -> obj.get("posterUrl").asString
                                    obj.has("poster") -> obj.get("poster").asString
                                    obj.has("url") -> obj.get("url").asString
                                    obj.has("imageUrl") -> obj.get("imageUrl").asString
                                    else -> null
                                }
                                if (!poster.isNullOrBlank()) {
                                    posterCache[cleanQuery] = poster
                                    return poster
                                }
                            }
                        }
                    }
                }
            } catch (_: Exception) {
                // Custom endpoint failed, fall through to TMDB
            }
        }

        // 2. High-speed Official TMDB Multi-Search (Ultra-fast CDN ~40-80ms)
        if (tmdbApiKey.isNotBlank()) {
            try {
                val encodedTitle = URLEncoder.encode(cleanQuery, "UTF-8")
                val tmdbUrl = "https://api.themoviedb.org/3/search/multi?api_key=$tmdbApiKey&query=$encodedTitle&include_adult=false"
                val request = Request.Builder().url(tmdbUrl).build()

                httpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string()
                        if (!body.isNullOrBlank()) {
                            val json = JsonParser.parseString(body).asJsonObject
                            val results = json.getAsJsonArray("results")
                            if (results != null && results.size() > 0) {
                                for (i in 0 until minOf(results.size(), 5)) {
                                    val item = results[i].asJsonObject
                                    val posterPath = if (item.has("poster_path") && !item.get("poster_path").isJsonNull) {
                                        item.get("poster_path").asString
                                    } else null

                                    if (!posterPath.isNullOrBlank()) {
                                        val posterUrl = "https://image.tmdb.org/t/p/w780$posterPath"
                                        posterCache[cleanQuery] = posterUrl
                                        return posterUrl
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.d(TAG, "TMDB fast poster resolve failed: ${e.message}")
            }
        }

        // 3. Cinemeta / Fallback Open Source Catalog
        try {
            val encodedTitle = URLEncoder.encode(cleanQuery, "UTF-8")
            val cinemetaUrl = "https://v3-cinemeta.strem.io/catalog/movie/top/search=$encodedTitle.json"
            val request = Request.Builder().url(cinemetaUrl).header("User-Agent", "NoxMedia/1.0").build()

            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string()
                    if (!body.isNullOrBlank()) {
                        val json = JsonParser.parseString(body).asJsonObject
                        if (json.has("metas") && json.getAsJsonArray("metas").size() > 0) {
                            val firstMeta = json.getAsJsonArray("metas")[0].asJsonObject
                            if (firstMeta.has("poster")) {
                                val poster = firstMeta.get("poster").asString
                                if (!poster.isNullOrBlank()) {
                                    posterCache[cleanQuery] = poster
                                    return poster
                                }
                            }
                        }
                    }
                }
            }
        } catch (_: Exception) {
        }

        return null
    }

    private val backdropCache = ConcurrentHashMap<String, String>()
    private val logoCache = ConcurrentHashMap<String, String>()
    private val episodeStillCache = ConcurrentHashMap<String, String>()

    fun hasCachedLogo(rawTitle: String): Boolean {
        val clean = sanitizeTitle(rawTitle)
        return clean.isNotBlank() && logoCache.containsKey(clean)
    }

    fun hasCachedBackdrop(rawTitle: String): Boolean {
        val clean = sanitizeTitle(rawTitle)
        return clean.isNotBlank() && backdropCache.containsKey(clean)
    }

    fun resolveBackdrop(
        rawTitle: String,
        imdbId: String? = null,
        tmdbId: Int? = null,
        tmdbApiKey: String = ""
    ): String? {
        val cleanQuery = sanitizeTitle(rawTitle)
        if (cleanQuery.isBlank() && imdbId.isNullOrBlank()) return null

        if (cleanQuery.isNotBlank()) {
            backdropCache[cleanQuery]?.let { return it }
        }

        // 1. Direct high-speed Metahub CDN by IMDb ID (instant, no rate limit)
        val validImdb = imdbId?.trim()?.lowercase()
        if (!validImdb.isNullOrBlank() && validImdb.startsWith("tt")) {
            val bgUrl = "https://images.metahub.space/background/medium/$validImdb/img"
            if (cleanQuery.isNotBlank()) backdropCache[cleanQuery] = bgUrl
            return bgUrl
        }

        // 2. TMDB Backdrop Resolution
        if (tmdbApiKey.isNotBlank()) {
            try {
                if (tmdbId != null && tmdbId > 0) {
                    val url = "https://api.themoviedb.org/3/movie/$tmdbId?api_key=$tmdbApiKey"
                    val req = Request.Builder().url(url).build()
                    httpClient.newCall(req).execute().use { resp ->
                        if (resp.isSuccessful) {
                            val body = resp.body?.string()
                            if (!body.isNullOrBlank()) {
                                val obj = JsonParser.parseString(body).asJsonObject
                                val bgPath = if (obj.has("backdrop_path") && !obj.get("backdrop_path").isJsonNull) obj.get("backdrop_path").asString else null
                                if (!bgPath.isNullOrBlank()) {
                                    val bgUrl = "https://image.tmdb.org/t/p/original$bgPath"
                                    if (cleanQuery.isNotBlank()) backdropCache[cleanQuery] = bgUrl
                                    return bgUrl
                                }
                            }
                        }
                    }
                    val tvUrl = "https://api.themoviedb.org/3/tv/$tmdbId?api_key=$tmdbApiKey"
                    val tvReq = Request.Builder().url(tvUrl).build()
                    httpClient.newCall(tvReq).execute().use { resp ->
                        if (resp.isSuccessful) {
                            val body = resp.body?.string()
                            if (!body.isNullOrBlank()) {
                                val obj = JsonParser.parseString(body).asJsonObject
                                val bgPath = if (obj.has("backdrop_path") && !obj.get("backdrop_path").isJsonNull) obj.get("backdrop_path").asString else null
                                if (!bgPath.isNullOrBlank()) {
                                    val bgUrl = "https://image.tmdb.org/t/p/original$bgPath"
                                    if (cleanQuery.isNotBlank()) backdropCache[cleanQuery] = bgUrl
                                    return bgUrl
                                }
                            }
                        }
                    }
                } else if (cleanQuery.isNotBlank()) {
                    val encodedTitle = URLEncoder.encode(cleanQuery, "UTF-8")
                    val searchUrl = "https://api.themoviedb.org/3/search/multi?api_key=$tmdbApiKey&query=$encodedTitle&include_adult=false"
                    val req = Request.Builder().url(searchUrl).build()
                    httpClient.newCall(req).execute().use { resp ->
                        if (resp.isSuccessful) {
                            val body = resp.body?.string()
                            if (!body.isNullOrBlank()) {
                                val root = JsonParser.parseString(body).asJsonObject
                                val results = root.getAsJsonArray("results")
                                if (results != null && results.size() > 0) {
                                    for (i in 0 until minOf(results.size(), 3)) {
                                        val itm = results[i].asJsonObject
                                        val bgPath = if (itm.has("backdrop_path") && !itm.get("backdrop_path").isJsonNull) itm.get("backdrop_path").asString else null
                                        if (!bgPath.isNullOrBlank()) {
                                            val bgUrl = "https://image.tmdb.org/t/p/original$bgPath"
                                            backdropCache[cleanQuery] = bgUrl
                                            return bgUrl
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.d(TAG, "resolveBackdrop failed: ${e.message}")
            }
        }

        // 3. Cinemeta Fallback
        if (cleanQuery.isNotBlank()) {
            try {
                for (cat in listOf("movie", "series")) {
                    val encodedTitle = URLEncoder.encode(cleanQuery, "UTF-8")
                    val cinemetaUrl = "https://v3-cinemeta.strem.io/catalog/$cat/top/search=$encodedTitle.json"
                    val req = Request.Builder().url(cinemetaUrl).header("User-Agent", "NoxMedia/1.0").build()
                    httpClient.newCall(req).execute().use { resp ->
                        if (resp.isSuccessful) {
                            val body = resp.body?.string()
                            if (!body.isNullOrBlank()) {
                                val json = JsonParser.parseString(body).asJsonObject
                                if (json.has("metas") && json.getAsJsonArray("metas").size() > 0) {
                                    val firstMeta = json.getAsJsonArray("metas")[0].asJsonObject
                                    val bg = if (firstMeta.has("background") && !firstMeta.get("background").isJsonNull) firstMeta.get("background").asString else null
                                    if (!bg.isNullOrBlank()) {
                                        backdropCache[cleanQuery] = bg
                                        return bg
                                    }
                                    val cImdb = if (firstMeta.has("imdb_id") && !firstMeta.get("imdb_id").isJsonNull) firstMeta.get("imdb_id").asString else null
                                    if (!cImdb.isNullOrBlank() && cImdb.startsWith("tt")) {
                                        val metahubBg = "https://images.metahub.space/background/medium/$cImdb/img"
                                        backdropCache[cleanQuery] = metahubBg
                                        return metahubBg
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (_: Exception) {
            }
        }

        return null
    }

    fun resolveLogo(
        rawTitle: String,
        imdbId: String? = null,
        tmdbId: Int? = null,
        tmdbApiKey: String = ""
    ): String? {
        val cleanQuery = sanitizeTitle(rawTitle)
        if (cleanQuery.isBlank() && imdbId.isNullOrBlank()) return null

        if (cleanQuery.isNotBlank()) {
            logoCache[cleanQuery]?.let { return it }
        }

        // 1. Direct high-speed Metahub CDN by IMDb ID (instant transparent PNG logo)
        val validImdb = imdbId?.trim()?.lowercase()
        if (!validImdb.isNullOrBlank() && validImdb.startsWith("tt")) {
            val logoUrl = "https://images.metahub.space/logo/medium/$validImdb/img"
            if (cleanQuery.isNotBlank()) logoCache[cleanQuery] = logoUrl
            return logoUrl
        }

        // 2. TMDB Logo Resolution
        if (tmdbApiKey.isNotBlank() && tmdbId != null && tmdbId > 0) {
            try {
                for (ep in listOf("movie/$tmdbId", "tv/$tmdbId")) {
                    val url = "https://api.themoviedb.org/3/$ep/images?api_key=$tmdbApiKey"
                    val req = Request.Builder().url(url).build()
                    httpClient.newCall(req).execute().use { resp ->
                        if (resp.isSuccessful) {
                            val body = resp.body?.string()
                            if (!body.isNullOrBlank()) {
                                val obj = JsonParser.parseString(body).asJsonObject
                                val logos = obj.getAsJsonArray("logos")
                                if (logos != null && logos.size() > 0) {
                                    var chosenPath: String? = null
                                    for (l in logos) {
                                        val lObj = l.asJsonObject
                                        val lang = lObj.get("iso_639_1")?.asString
                                        val fPath = lObj.get("file_path")?.asString
                                        if (!fPath.isNullOrBlank()) {
                                            if (lang == "en") {
                                                chosenPath = fPath
                                                break
                                            } else if (chosenPath == null) {
                                                chosenPath = fPath
                                            }
                                        }
                                    }
                                    if (!chosenPath.isNullOrBlank()) {
                                        val logoUrl = "https://image.tmdb.org/t/p/w500$chosenPath"
                                        if (cleanQuery.isNotBlank()) logoCache[cleanQuery] = logoUrl
                                        return logoUrl
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.d(TAG, "resolveLogo failed: ${e.message}")
            }
        }

        // 3. Cinemeta Logo Fallback
        if (cleanQuery.isNotBlank()) {
            try {
                for (cat in listOf("movie", "series")) {
                    val encodedTitle = URLEncoder.encode(cleanQuery, "UTF-8")
                    val cinemetaUrl = "https://v3-cinemeta.strem.io/catalog/$cat/top/search=$encodedTitle.json"
                    val req = Request.Builder().url(cinemetaUrl).header("User-Agent", "NoxMedia/1.0").build()
                    httpClient.newCall(req).execute().use { resp ->
                        if (resp.isSuccessful) {
                            val body = resp.body?.string()
                            if (!body.isNullOrBlank()) {
                                val json = JsonParser.parseString(body).asJsonObject
                                if (json.has("metas") && json.getAsJsonArray("metas").size() > 0) {
                                    val firstMeta = json.getAsJsonArray("metas")[0].asJsonObject
                                    val lg = if (firstMeta.has("logo") && !firstMeta.get("logo").isJsonNull) firstMeta.get("logo").asString else null
                                    if (!lg.isNullOrBlank()) {
                                        logoCache[cleanQuery] = lg
                                        return lg
                                    }
                                    val cImdb = if (firstMeta.has("imdb_id") && !firstMeta.get("imdb_id").isJsonNull) firstMeta.get("imdb_id").asString else null
                                    if (!cImdb.isNullOrBlank() && cImdb.startsWith("tt")) {
                                        val metahubLogo = "https://images.metahub.space/logo/medium/$cImdb/img"
                                        logoCache[cleanQuery] = metahubLogo
                                        return metahubLogo
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (_: Exception) {
            }
        }

        return null
    }

    fun resolveEpisodeStill(
        rawTitle: String,
        tmdbId: Int? = null,
        season: Int = 1,
        episode: Int = 1,
        tmdbApiKey: String = ""
    ): String? {
        val cleanQuery = sanitizeTitle(rawTitle)
        val cacheKey = "${cleanQuery}_s${season}_e${episode}"
        episodeStillCache[cacheKey]?.let { return it }

        if (tmdbApiKey.isNotBlank() && tmdbId != null && tmdbId > 0) {
            try {
                val url = "https://api.themoviedb.org/3/tv/$tmdbId/season/$season/episode/$episode?api_key=$tmdbApiKey"
                val req = Request.Builder().url(url).build()
                httpClient.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val body = resp.body?.string()
                        if (!body.isNullOrBlank()) {
                            val obj = JsonParser.parseString(body).asJsonObject
                            val stillPath = if (obj.has("still_path") && !obj.get("still_path").isJsonNull) obj.get("still_path").asString else null
                            if (!stillPath.isNullOrBlank()) {
                                val stillUrl = "https://image.tmdb.org/t/p/w500$stillPath"
                                episodeStillCache[cacheKey] = stillUrl
                                return stillUrl
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.d(TAG, "resolveEpisodeStill failed: ${e.message}")
            }
        }

        return null
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
