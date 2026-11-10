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
        apiKey: String,
        librarySample: List<String> = emptyList()
    ): Result<GeneratedCatalogResult> = withContext(Dispatchers.IO) {
        if (prompt.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Prompt cannot be empty"))
        }
        if (apiKey.isBlank()) {
            return@withContext Result.failure(IllegalStateException("No API key configured for ${provider.displayName}"))
        }

        try {
            val systemInstruction = """
                You are an expert media curator for NoxMedia app.
                Analyze the user's natural language request to create a dynamic catalog of movies and TV series.
                Output ONLY a single valid JSON object with NO markdown formatting, NO backticks, and NO commentary.
                
                JSON SCHEMA:
                {
                  "title": "string (Short, stylish title, 2-4 words, e.g. 'Murder Mystery', '90s Sci-Fi Thrillers', 'Dark Fantasy')",
                  "mediaType": "all" | "movie" | "series",
                  "personQuery": "string (Actor or Director name ONLY if explicitly requested, else empty \"\")",
                  "studioQuery": "string (Studio or Platform name like 'HBO', 'Netflix', 'Studio Ghibli', 'A24', 'Marvel' ONLY if requested, else empty \"\")",
                  "genres": ["string"] (Standard TMDB genres: Action, Adventure, Animation, Comedy, Crime, Documentary, Drama, Family, Fantasy, History, Horror, Music, Mystery, Romance, Science Fiction, Thriller, War, Western),
                  "languages": ["string"] (CRITICAL: MUST BE empty [] unless the user explicitly demanded a specific language or audio in their prompt!),
                  "ratingFilterEnabled": boolean (CRITICAL: MUST BE false unless the user explicitly requested high/top rating or minimum score!),
                  "minRating": number between 0.0 and 10.0 (e.g. 7.5 if ratingFilterEnabled is true, else 0.0),
                  "maxRating": 10.0,
                  "yearFilterEnabled": boolean (CRITICAL: MUST BE false unless the user explicitly requested a specific year, decade, or era!),
                  "minYear": integer (e.g. 2010 if 'after 2010', or 1990 if '90s', else 1920),
                  "maxYear": integer (e.g. 1999 if '90s', else 2030),
                  "sortBy": "RATING" | "RECENT" | "YEAR" | "TITLE",
                  "sortAscending": boolean,
                  "keywords": ["string"] (CRITICAL: Provide 5 to 10 rich semantic keywords, synonyms, tropes, and plot concepts describing the theme. E.g. for 'Murder Mystery': ["murder", "mystery", "detective", "investigation", "whodunit", "homicide", "suspect", "sleuth", "crime scene"])
                }

                STRICT RULES:
                1. languages: Default to [] (empty array). NEVER add English, Hindi, or any language unless the user prompt explicitly requested it.
                2. ratingFilterEnabled: Default to false. Only true if user prompt asked for top rated / minimum score.
                3. yearFilterEnabled: Default to false. Only true if user prompt specified a year, decade, or release time.
                4. mediaType: Default to "all". Set to "movie" only if prompt specifies movie/film. Set to "series" only if prompt specifies series/show/anime.
                5. keywords: Generate diverse, powerful semantic keywords so newly added items matching this concept are automatically categorized without calling the AI API again.
            """.trimIndent()

            val promptWithContext = buildString {
                append("User Catalog Request: \"").append(prompt.trim()).append("\"\n")
                if (librarySample.isNotEmpty()) {
                    append("\nReference - Sample titles currently in user's library (use to understand user's library and optimize keywords):\n")
                    append(librarySample.take(50).joinToString(", "))
                    append("\n")
                }
            }

            val rawJson = when (provider) {
                AiProvider.GEMINI -> callGemini(promptWithContext, systemInstruction, apiKey.trim())
                AiProvider.OPENROUTER -> callOpenRouter(promptWithContext, systemInstruction, apiKey.trim())
            }

            val cleanedJson = cleanJsonResponse(rawJson)
            val parsedResult = parseJsonToResult(cleanedJson, prompt)
            Result.success(parsedResult)
        } catch (e: Exception) {
            Log.e(TAG, "Catalog generation failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    @Volatile
    private var cachedResolvedModel: String? = null

    private val geminiPriorityModels = listOf(
        "gemini-2.5-flash",
        "gemini-2.5-flash-lite",
        "gemini-2.0-flash",
        "gemini-2.0-flash-lite",
        "gemini-3.5-flash-lite",
        "gemini-3.1-flash-lite"
    )

    private fun resolveActiveGeminiModel(apiKey: String): String {
        cachedResolvedModel?.let { return it }

        try {
            val listUrl = "https://generativelanguage.googleapis.com/v1beta/models?key=$apiKey"
            val req = Request.Builder().url(listUrl).get().build()
            val resp = httpClient.newCall(req).execute()
            if (resp.isSuccessful) {
                val body = resp.body?.string().orEmpty()
                val json = JsonParser.parseString(body).asJsonObject
                val modelsArr = json.getAsJsonArray("models")
                if (modelsArr != null && modelsArr.size() > 0) {
                    val supportedModels = mutableListOf<String>()
                    for (m in modelsArr) {
                        val mObj = m.asJsonObject
                        val name = mObj.get("name")?.asString.orEmpty().removePrefix("models/")
                        val methods = mObj.getAsJsonArray("supportedGenerationMethods")?.map { it.asString } ?: emptyList()
                        if (methods.contains("generateContent")) {
                            supportedModels.add(name)
                        }
                    }

                    for (cand in geminiPriorityModels) {
                        val match = supportedModels.firstOrNull { it.equals(cand, ignoreCase = true) || it.startsWith(cand, ignoreCase = true) }
                        if (match != null) {
                            cachedResolvedModel = match
                            Log.i(TAG, "Resolved Gemini model via ListModels: $match")
                            return match
                        }
                    }

                    val anyFlash = supportedModels.firstOrNull { it.contains("flash", ignoreCase = true) }
                    if (anyFlash != null) {
                        cachedResolvedModel = anyFlash
                        return anyFlash
                    }

                    val anyGen = supportedModels.firstOrNull()
                    if (anyGen != null) {
                        cachedResolvedModel = anyGen
                        return anyGen
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to query Gemini ListModels: ${e.message}")
        }

        val fallback = "gemini-2.5-flash"
        cachedResolvedModel = fallback
        return fallback
    }

    private fun callGemini(prompt: String, systemInstruction: String, apiKey: String): String {
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

        // Build candidate cascade: resolved model first, then priority models
        val candidateModels = mutableListOf<String>()
        val discovered = resolveActiveGeminiModel(apiKey)
        if (discovered.isNotBlank()) {
            candidateModels.add(discovered)
        }
        for (m in geminiPriorityModels) {
            if (!candidateModels.contains(m)) {
                candidateModels.add(m)
            }
        }

        var lastError: Exception? = null

        for (model in candidateModels) {
            val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"
            val request = Request.Builder()
                .url(url)
                .post(requestBodyJson.toString().toRequestBody(jsonMediaType))
                .build()

            try {
                val response = httpClient.newCall(request).execute()
                val bodyString = response.body?.string().orEmpty()

                if (response.isSuccessful) {
                    cachedResolvedModel = model
                    return extractGeminiText(bodyString)
                }

                // If 404 (model not found) or 400 (not supported for generateContent), try next model in cascade
                if (response.code == 404 || response.code == 400) {
                    Log.w(TAG, "Gemini model '$model' returned HTTP ${response.code}, trying next model in cascade...")
                    lastError = Exception("Gemini API error (${response.code}) on '$model': $bodyString")
                    continue
                }

                // If 429 (rate limit) or 403 (invalid key / permission), fail fast
                throw Exception("Gemini API error (${response.code}): $bodyString")
            } catch (e: Exception) {
                if (e.message?.contains("Gemini API error (429)") == true || e.message?.contains("Gemini API error (403)") == true) {
                    throw e
                }
                lastError = e
            }
        }

        throw lastError ?: Exception("All candidate Gemini models failed to generate content.")
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

        val rawLanguages = mutableListOf<String>()
        obj.getAsJsonArray("languages")?.forEach {
            val l = it.asString.trim()
            if (l.isNotBlank()) rawLanguages.add(l)
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
            if (k.isNotBlank() && !keywords.contains(k)) keywords.add(k)
        }

        // Add prompt's thematic words to keywords so they are never missed
        val promptWords = originalPrompt.split(" ", "-", "_", "/", ",", ".")
            .map { it.trim().lowercase() }
            .filter { it.length >= 3 && it !in listOf("the", "and", "for", "with", "from", "movie", "movies", "series", "show", "shows", "film", "films") }
        for (pw in promptWords) {
            if (!keywords.contains(pw)) {
                keywords.add(pw)
            }
        }

        // --- STRICT GUARD RAILS ---
        val promptLower = originalPrompt.lowercase()

        // 1. Language Guard: Only apply languages if explicitly mentioned in user's prompt
        val commonLanguageKeywords = listOf(
            "english", "hindi", "japanese", "korean", "spanish", "french", "german",
            "tamil", "telugu", "malayalam", "bengali", "marathi", "kannada", "punjabi",
            "chinese", "mandarin", "cantonese", "russian", "italian", "audio", "dub",
            "dubbed", "sub", "subtitled"
        )
        val userExplicitlyRequestedLanguage = commonLanguageKeywords.any { promptLower.contains(it) }
        val effectiveLanguages = if (userExplicitlyRequestedLanguage) rawLanguages else emptyList()

        // 2. Rating Guard: Only apply rating filter if user explicitly asked for rating / score / top rated
        val ratingKeywords = listOf(
            "rating", "rated", "score", "top", "best", "acclaimed", "imdb", "tmdb",
            "critically", "stars", "star"
        )
        val userExplicitlyRequestedRating = ratingKeywords.any { promptLower.contains(it) } ||
                promptLower.contains(">") || promptLower.contains("+")
        val effectiveRatingEnabled = ratingFilterEnabled && userExplicitlyRequestedRating

        // 3. Year Guard: Only apply year filter if user explicitly mentioned year, decade, or era
        val yearRegex = Regex("""\b(19\d\d|20\d\d|\d0s|after|before|between|from|since|recent|classic|old|new|modern)\b""", RegexOption.IGNORE_CASE)
        val userExplicitlyRequestedYear = yearRegex.containsMatchIn(promptLower)
        val effectiveYearEnabled = yearFilterEnabled && userExplicitlyRequestedYear

        // 4. MediaType Guard:
        val effectiveMediaType = when {
            promptLower.contains("movie") || promptLower.contains("film") -> "movie"
            promptLower.contains("series") || promptLower.contains("show") || promptLower.contains("tv") || promptLower.contains("season") || promptLower.contains("anime") -> "series"
            else -> mediaType
        }

        val filter = CatalogFilterCriteria(
            mediaType = effectiveMediaType,
            personQuery = personQuery,
            studioQuery = studioQuery,
            genres = genres,
            languages = effectiveLanguages,
            ratingFilterEnabled = effectiveRatingEnabled,
            minRating = minRating,
            maxRating = maxRating,
            yearFilterEnabled = effectiveYearEnabled,
            minYear = minYear,
            maxYear = maxYear,
            sortBy = sortBy,
            sortAscending = sortAscending,
            keywords = keywords
        )

        return GeneratedCatalogResult(title = title, filter = filter)
    }
}
