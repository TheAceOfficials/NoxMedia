package com.tgserver.streamer.posters

import com.google.gson.JsonParser
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class SpatialPosterService {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    private val posterCache = ConcurrentHashMap<String, String>()

    fun resolvePoster(
        rawTitle: String,
        customEndpoint: String,
        apiKey: String
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
            } catch (e: Exception) {
                // Custom endpoint failed, fall through to fallback
            }
        }

        // 2. Cinemeta / Fallback Open Source Catalog
        try {
            val encodedTitle = URLEncoder.encode(cleanQuery, "UTF-8")
            val cinemetaUrl = "https://v3-cinemeta.strem.io/catalog/movie/top/search=$encodedTitle.json"
            val request = Request.Builder().url(cinemetaUrl).build()

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

    private fun sanitizeTitle(name: String): String {
        return name
            .replace(Regex("(?i)\\.(mkv|mp4|avi|mov|webm)$"), "")
            .replace(Regex("(?i)(2160p|1080p|720p|480p|4k|uhd|hdr|hevc|x264|x265|aac|dts|bluray|web-dl|webrip|proper|repack)"), "")
            .replace(Regex("[\\[\\]\\(\\)\\._-]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}
