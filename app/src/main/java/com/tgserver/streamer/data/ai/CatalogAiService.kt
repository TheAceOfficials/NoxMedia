package com.tgserver.streamer.data.ai

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.tgserver.streamer.data.model.AiProvider
import com.tgserver.streamer.data.model.CatalogFilterCriteria
import com.tgserver.streamer.data.model.CatalogSortBy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

private const val TAG = "CatalogAiService"

data class GeneratedCatalogResult(
    val title: String,
    val filter: CatalogFilterCriteria
)

class CatalogAiService(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
) {
    private val gson = Gson()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun verifyApiKey(provider: AiProvider, apiKey: String): Result<String> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("API key cannot be empty"))
        }

        try {
            when (provider) {
                AiProvider.GEMINI -> {
                    val url = "https://generativelanguage.googleapis.com/v1beta/models?key=${apiKey.trim()}"
                    val request = Request.Builder().url(url).get().build()
                    val response = httpClient.newCall(request).execute()
                    if (response.isSuccessful) {
                        Result.success("Gemini API Key verified successfully")
                    } else {
                        val body = response.body?.string().orEmpty()
                        Result.failure(Exception("Gemini verification failed (HTTP ${response.code}): $body"))
                    }
                }
                AiProvider.OPENROUTER -> {
                    val url = "https://openrouter.ai/api/v1/auth/key"
                    val request = Request.Builder()
                        .url(url)
                        .header("Authorization", "Bearer ${apiKey.trim()}")
                        .get()
                        .build()
                    val response = httpClient.newCall(request).execute()
                    if (response.isSuccessful) {
                        Result.success("OpenRouter API Key verified successfully")
                    } else {
                        val body = response.body?.string().orEmpty()
                        Result.failure(Exception("OpenRouter verification failed (HTTP ${response.code}): $body"))
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Key verification error: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun generateCatalog(
        prompt: String,
        provider: AiProvider,
        apiKey: String
    ): Result<GeneratedCatalogResult> = withContext(Dispatchers.IO) {
        if (prompt.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Prompt cannot be empty"))
        }
        if (apiKey.isBlank()) {
            return@withContext Result.failure(IllegalStateException("No API key configured for ${provider.displayName}"))
        }

        try {
            val systemInstruction = """
                You are an expert movie and TV series curator for NoxMedia app.
                Analyze the user's prompt requesting a custom media category or catalog.
                Output ONLY a single valid JSON object with no commentary or markdown markdown wrapping.
                JSON structure must be:
                {
                  "title": "string (Short stylish title, 2-4 words, e.g. 'Mind-Bending Thrillers', '90s Sci-Fi Classics')",
                  "mediaType": "all" or "movie" or "series",
                  "personQuery": "string (actor/director name if explicitly or implicitly requested, else empty string)",
                  "studioQuery": "string (studio or OTT platform name like 'Studio Ghibli', 'HBO', 'Netflix', 'Warner Bros' if requested, else empty string)",
                  "genres": ["string"] (standard TMDB genres: Action, Adventure, Animation, Comedy, Crime, Documentary, Drama, Family, Fantasy, History, Horror, Music, Mystery, Romance, Science Fiction, Thriller, War, Western),
                  "languages": ["string"] (e.g. English, Hindi, Japanese, Korean, Spanish),
                  "ratingFilterEnabled": boolean,
                  "minRating": number between 0.0 and 10.0 (e.g. 7.5),
                  "maxRating": 10.0,
                  "yearFilterEnabled": boolean,
                  "minYear": integer (e.g. 1990),
                  "maxYear": integer (e.g. 2026),
                  "sortBy": "RATING" or "RECENT" or "YEAR" or "TITLE",
                  "sortAscending": boolean,
                  "keywords": ["string"] (3 to 8 semantic keywords describing themes, vibes or plot elements, e.g. "time travel", "dystopia", "heist", "cyberpunk")
                }
            """.trimIndent()

            val rawJson = when (provider) {
                AiProvider.GEMINI -> callGemini(prompt, systemInstruction, apiKey.trim())
                AiProvider.OPENROUTER -> callOpenRouter(prompt, systemInstruction, apiKey.trim())
            }

            val cleanedJson = cleanJsonResponse(rawJson)
            val parsedResult = parseJsonToResult(cleanedJson, prompt)
            Result.success(parsedResult)
        } catch (e: Exception) {
            Log.e(TAG, "Catalog generation failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    private fun callGemini(prompt: String, systemInstruction: String, apiKey: String): String {
        // Try gemini-2.0-flash, fallback to gemini-1.5-flash if needed
        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=$apiKey"

        val requestBodyJson = JsonObject().apply {
            val contentsArr = com.google.gson.JsonArray().apply {
                val userContent = JsonObject().apply {
                    addProperty("role", "user")
                    val partsArr = com.google.gson.JsonArray().apply {
                        add(JsonObject().apply {
                            addProperty("text", "$systemInstruction\n\nUser Catalog Request: \"$prompt\"")
                        })
                    }
                    add("parts", partsArr)
                }
                add(userContent)
            }
            add("contents", contentsArr)

            val genConfig = JsonObject().apply {
                addProperty("responseMimeType", "application/json")
                addProperty("temperature", 0.4)
            }
            add("generationConfig", genConfig)
        }

        val request = Request.Builder()
            .url(url)
            .post(requestBodyJson.toString().toRequestBody(jsonMediaType))
            .build()

        val response = httpClient.newCall(request).execute()
        val bodyString = response.body?.string().orEmpty()

        if (!response.isSuccessful) {
            // If 2.0-flash returned 404/unsupported, try 1.5-flash
            if (response.code == 404 || response.code == 400) {
                val fallbackUrl = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=$apiKey"
                val fallbackReq = Request.Builder()
                    .url(fallbackUrl)
                    .post(requestBodyJson.toString().toRequestBody(jsonMediaType))
                    .build()
                val fallbackResp = httpClient.newCall(fallbackReq).execute()
                val fallbackBody = fallbackResp.body?.string().orEmpty()
                if (!fallbackResp.isSuccessful) {
                    throw Exception("Gemini API error (${fallbackResp.code}): $fallbackBody")
                }
                return extractGeminiText(fallbackBody)
            }
            throw Exception("Gemini API error (${response.code}): $bodyString")
        }

        return extractGeminiText(bodyString)
    }

    private fun extractGeminiText(responseBody: String): String {
        val root = JsonParser.parseString(responseBody).asJsonObject
        val candidates = root.getAsJsonArray("candidates")
        if (candidates == null || candidates.size() == 0) {
            throw Exception("No response candidates returned by Gemini")
        }
        val content = candidates[0].asJsonObject.getAsJsonObject("content")
        val parts = content.getAsJsonArray("parts")
        if (parts == null || parts.size() == 0) {
            throw Exception("No text parts returned by Gemini")
        }
        return parts[0].asJsonObject.get("text").asString
    }

    private fun callOpenRouter(prompt: String, systemInstruction: String, apiKey: String): String {
        val url = "https://openrouter.ai/api/v1/chat/completions"

        val requestBodyJson = JsonObject().apply {
            addProperty("model", "google/gemini-2.0-flash-001")
            
            val respFormat = JsonObject().apply {
                addProperty("type", "json_object")
            }
            add("response_format", respFormat)

            val messages = com.google.gson.JsonArray().apply {
                add(JsonObject().apply {
                    addProperty("role", "system")
                    addProperty("content", systemInstruction)
                })
                add(JsonObject().apply {
                    addProperty("role", "user")
                    addProperty("content", prompt)
                })
            }
            add("messages", messages)
            addProperty("temperature", 0.4)
        }

        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $apiKey")
            .header("HTTP-Referer", "https://noxmedia.app")
            .header("X-Title", "NoxMedia")
            .post(requestBodyJson.toString().toRequestBody(jsonMediaType))
            .build()

        val response = httpClient.newCall(request).execute()
        val bodyString = response.body?.string().orEmpty()

        if (!response.isSuccessful) {
            throw Exception("OpenRouter API error (${response.code}): $bodyString")
        }

        val root = JsonParser.parseString(bodyString).asJsonObject
        val choices = root.getAsJsonArray("choices")
        if (choices == null || choices.size() == 0) {
            throw Exception("No choices returned from OpenRouter")
        }
        val message = choices[0].asJsonObject.getAsJsonObject("message")
        return message.get("content").asString
    }

    private fun cleanJsonResponse(raw: String): String {
        var trimmed = raw.trim()
        if (trimmed.startsWith("```json")) {
            trimmed = trimmed.removePrefix("```json")
        } else if (trimmed.startsWith("```")) {
            trimmed = trimmed.removePrefix("```")
        }
        if (trimmed.endsWith("```")) {
            trimmed = trimmed.removeSuffix("```")
        }
        return trimmed.trim()
    }

    private fun parseJsonToResult(json: String, originalPrompt: String): GeneratedCatalogResult {
        val obj = JsonParser.parseString(json).asJsonObject

        val title = obj.get("title")?.asString?.takeIf { it.isNotBlank() }
            ?: originalPrompt.take(24).trim().split(" ").joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }

        val mediaTypeRaw = obj.get("mediaType")?.asString?.lowercase() ?: "all"
        val mediaType = when {
            mediaTypeRaw.contains("movie") -> "movie"
            mediaTypeRaw.contains("series") || mediaTypeRaw.contains("tv") -> "series"
            else -> "all"
        }

        val personQuery = obj.get("personQuery")?.asString.orEmpty().trim()
        val studioQuery = obj.get("studioQuery")?.asString.orEmpty().trim()

        val genres = mutableListOf<String>()
        obj.getAsJsonArray("genres")?.forEach {
            val g = it.asString.trim()
            if (g.isNotBlank()) genres.add(g)
        }

        val languages = mutableListOf<String>()
        obj.getAsJsonArray("languages")?.forEach {
            val l = it.asString.trim()
            if (l.isNotBlank()) languages.add(l)
        }

        val ratingFilterEnabled = obj.get("ratingFilterEnabled")?.asBoolean ?: false
        val minRating = (obj.get("minRating")?.asFloat ?: 7.0f).coerceIn(0.0f, 10.0f)
        val maxRating = (obj.get("maxRating")?.asFloat ?: 10.0f).coerceIn(minRating, 10.0f)

        val yearFilterEnabled = obj.get("yearFilterEnabled")?.asBoolean ?: false
        val minYear = (obj.get("minYear")?.asInt ?: 1990).coerceIn(1920, 2030)
        val maxYear = (obj.get("maxYear")?.asInt ?: 2026).coerceIn(minYear, 2030)

        val sortByStr = obj.get("sortBy")?.asString?.uppercase() ?: "RATING"
        val sortBy = when {
            sortByStr.contains("RECENT") -> CatalogSortBy.RECENT
            sortByStr.contains("YEAR") -> CatalogSortBy.YEAR
            sortByStr.contains("TITLE") -> CatalogSortBy.TITLE
            else -> CatalogSortBy.RATING
        }
        val sortAscending = obj.get("sortAscending")?.asBoolean ?: false

        val keywords = mutableListOf<String>()
        obj.getAsJsonArray("keywords")?.forEach {
            val k = it.asString.trim().lowercase()
            if (k.isNotBlank()) keywords.add(k)
        }

        val filter = CatalogFilterCriteria(
            mediaType = mediaType,
            personQuery = personQuery,
            studioQuery = studioQuery,
            genres = genres,
            languages = languages,
            ratingFilterEnabled = ratingFilterEnabled,
            minRating = minRating,
            maxRating = maxRating,
            yearFilterEnabled = yearFilterEnabled,
            minYear = minYear,
            maxYear = maxYear,
            sortBy = sortBy,
            sortAscending = sortAscending,
            keywords = keywords
        )

        return GeneratedCatalogResult(title = title, filter = filter)
    }
}
