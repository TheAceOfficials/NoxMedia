package com.tgserver.streamer.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.tgserver.streamer.data.model.CatalogDefinition
import com.tgserver.streamer.data.model.CatalogSortBy
import com.tgserver.streamer.data.model.MediaGroup
import com.tgserver.streamer.data.model.TmdbCompanyItem
import com.tgserver.streamer.data.model.TmdbPersonItem
import com.tgserver.streamer.data.model.getDefaultStarterCatalogs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

private const val TAG = "CatalogRepository"
private const val PREF_KEY_CATALOGS = "custom_catalogs_v1"

class CatalogRepository(
    context: Context,
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
) {
    private val prefs: SharedPreferences = context.getSharedPreferences("tg_server_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()

    // In-memory cache of Person TMDB ID -> Set of TMDB Media IDs
    private val personFilmographyCache = ConcurrentHashMap<Int, Set<Int>>()

    // In-memory cache of Company TMDB ID -> Set of TMDB Media IDs
    private val companyFilmographyCache = ConcurrentHashMap<Int, Set<Int>>()

    fun getCatalogs(): List<CatalogDefinition> {
        val rawJson = prefs.getString(PREF_KEY_CATALOGS, null)
        if (rawJson.isNullOrBlank()) {
            val defaults = getDefaultStarterCatalogs()
            saveCatalogs(defaults)
            return defaults
        }

        return try {
            val type = object : TypeToken<List<CatalogDefinition>>() {}.type
            val list: List<CatalogDefinition> = gson.fromJson(rawJson, type) ?: getDefaultStarterCatalogs()
            list.sortedBy { it.sortOrder }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse catalogs json: ${e.message}", e)
            val defaults = getDefaultStarterCatalogs()
            saveCatalogs(defaults)
            defaults
        }
    }

    fun saveCatalogs(catalogs: List<CatalogDefinition>) {
        val json = gson.toJson(catalogs)
        prefs.edit().putString(PREF_KEY_CATALOGS, json).apply()
    }

    fun addCatalog(catalog: CatalogDefinition): List<CatalogDefinition> {
        val current = getCatalogs().toMutableList()
        catalog.sortOrder = current.size
        current.add(catalog)
        saveCatalogs(current)
        return current
    }

    fun updateCatalog(updated: CatalogDefinition): List<CatalogDefinition> {
        val current = getCatalogs().toMutableList()
        val index = current.indexOfFirst { it.id == updated.id }
        if (index != -1) {
            current[index] = updated
            saveCatalogs(current)
        }
        return current
    }

    fun deleteCatalogs(ids: Set<String>): List<CatalogDefinition> {
        val current = getCatalogs().filter { it.id !in ids }.toMutableList()
        current.forEachIndexed { idx, item -> item.sortOrder = idx }
        saveCatalogs(current)
        return current
    }

    fun reorderCatalogs(orderedIds: List<String>): List<CatalogDefinition> {
        val current = getCatalogs().associateBy { it.id }.toMutableMap()
        val reordered = mutableListOf<CatalogDefinition>()
        orderedIds.forEachIndexed { idx, id ->
            current.remove(id)?.let {
                it.sortOrder = idx
                reordered.add(it)
            }
        }
        // Append any remaining that weren't in orderedIds
        current.values.forEachIndexed { extraIdx, item ->
            item.sortOrder = reordered.size + extraIdx
            reordered.add(item)
        }
        saveCatalogs(reordered)
        return reordered
    }

    fun toggleHide(id: String): List<CatalogDefinition> {
        val current = getCatalogs().toMutableList()
        val index = current.indexOfFirst { it.id == id }
        if (index != -1) {
            val item = current[index]
            current[index] = item.copy(isHidden = !item.isHidden)
            saveCatalogs(current)
        }
        return current
    }

    fun togglePin(id: String): List<CatalogDefinition> {
        val current = getCatalogs().toMutableList()
        val index = current.indexOfFirst { it.id == id }
        if (index != -1) {
            val item = current[index]
            current[index] = item.copy(pinToHome = !item.pinToHome)
            saveCatalogs(current)
        }
        return current
    }

    suspend fun searchTmdbPeople(query: String, apiKey: String): List<TmdbPersonItem> = withContext(Dispatchers.IO) {
        if (query.isBlank() || apiKey.isBlank()) return@withContext emptyList()
        try {
            val encoded = URLEncoder.encode(query.trim(), "UTF-8")
            val url = "https://api.themoviedb.org/3/search/person?api_key=$apiKey&query=$encoded&language=en-US&include_adult=false"
            val request = Request.Builder().url(url).get().build()
            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) return@withContext emptyList()

            val body = response.body?.string().orEmpty()
            val root = JSONObject(body)
            val results = root.optJSONArray("results") ?: return@withContext emptyList()

            val items = mutableListOf<TmdbPersonItem>()
            for (i in 0 until results.length().coerceAtMost(8)) {
                val obj = results.getJSONObject(i)
                items.add(
                    TmdbPersonItem(
                        id = obj.optInt("id"),
                        name = obj.optString("name"),
                        department = obj.optString("known_for_department"),
                        profilePath = obj.optString("profile_path").takeIf { it.isNotBlank() }
                    )
                )
            }
            items
        } catch (e: Exception) {
            Log.e(TAG, "searchTmdbPeople error: ${e.message}")
            emptyList()
        }
    }

    suspend fun getPersonFilmographyIds(personId: Int, apiKey: String): Set<Int> = withContext(Dispatchers.IO) {
        if (personId <= 0 || apiKey.isBlank()) return@withContext emptySet()
        personFilmographyCache[personId]?.let { return@withContext it }

        try {
            val url = "https://api.themoviedb.org/3/person/$personId/combined_credits?api_key=$apiKey&language=en-US"
            val request = Request.Builder().url(url).get().build()
            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) return@withContext emptySet()

            val body = response.body?.string().orEmpty()
            val root = JSONObject(body)
            val idSet = mutableSetOf<Int>()

            root.optJSONArray("cast")?.let { castArr ->
                for (i in 0 until castArr.length()) {
                    val id = castArr.getJSONObject(i).optInt("id", 0)
                    if (id > 0) idSet.add(id)
                }
            }

            root.optJSONArray("crew")?.let { crewArr ->
                for (i in 0 until crewArr.length()) {
                    val id = crewArr.getJSONObject(i).optInt("id", 0)
                    if (id > 0) idSet.add(id)
                }
            }

            personFilmographyCache[personId] = idSet
            idSet
        } catch (e: Exception) {
            Log.e(TAG, "getPersonFilmographyIds error: ${e.message}")
            emptySet()
        }
    }

    suspend fun searchTmdbCompanies(query: String, apiKey: String): List<TmdbCompanyItem> = withContext(Dispatchers.IO) {
        if (query.isBlank() || apiKey.isBlank()) return@withContext emptyList()
        try {
            val encoded = URLEncoder.encode(query.trim(), "UTF-8")
            val url = "https://api.themoviedb.org/3/search/company?api_key=$apiKey&query=$encoded"
            val request = Request.Builder().url(url).get().build()
            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) return@withContext emptyList()

            val body = response.body?.string().orEmpty()
            val root = JSONObject(body)
            val results = root.optJSONArray("results") ?: return@withContext emptyList()

            val items = mutableListOf<TmdbCompanyItem>()
            for (i in 0 until results.length().coerceAtMost(8)) {
                val obj = results.getJSONObject(i)
                items.add(
                    TmdbCompanyItem(
                        id = obj.optInt("id"),
                        name = obj.optString("name"),
                        logoPath = obj.optString("logo_path").takeIf { it.isNotBlank() }
                    )
                )
            }
            items
        } catch (e: Exception) {
            Log.e(TAG, "searchTmdbCompanies error: ${e.message}")
            emptyList()
        }
    }

    /**
     * Filters media groups using the catalog filter criteria.
     * High performance, single pass filtering with zero UI stutter.
     */
    fun filterMediaForCatalog(
        catalog: CatalogDefinition,
        mediaGroups: List<MediaGroup>,
        personTmdbIds: Set<Int> = emptySet()
    ): List<MediaGroup> {
        val f = catalog.filter
        val cleanPersonQuery = f.personQuery.trim().lowercase()
        val cleanStudioQuery = f.studioQuery.trim().lowercase()
        val genresLower = f.genres.map { it.lowercase() }
        val keywordsLower = f.keywords.map { it.lowercase() }
        val languagesLower = f.languages.map { it.lowercase() }

        val filtered = mediaGroups.filter { group ->
            // Exclude hidden groups
            if (group.isHidden) return@filter false

            // Media Type match
            when (f.mediaType) {
                "movie" -> if (group.type != "movie") return@filter false
                "series" -> if (group.type != "series" && group.type != "anime") return@filter false
            }

            // Rating match
            if (f.ratingFilterEnabled) {
                if (group.rating < f.minRating || group.rating > f.maxRating) return@filter false
            }

            // Year match
            if (f.yearFilterEnabled) {
                val yearInt = group.year?.toIntOrNull() ?: 0
                if (yearInt == 0 || yearInt < f.minYear || yearInt > f.maxYear) return@filter false
            }

            // Person match (if person ID matched or query present)
            if (personTmdbIds.isNotEmpty()) {
                val hasMatch = (group.tmdbId != null && group.tmdbId in personTmdbIds)
                if (!hasMatch) return@filter false
            } else if (cleanPersonQuery.isNotBlank()) {
                val inTitle = group.title.contains(cleanPersonQuery, ignoreCase = true)
                val inOverview = group.overview?.contains(cleanPersonQuery, ignoreCase = true) == true
                val inItems = group.items.any { it.fileName.contains(cleanPersonQuery, ignoreCase = true) }
                if (!inTitle && !inOverview && !inItems) return@filter false
            }

            // Studio / Network match
            if (cleanStudioQuery.isNotBlank()) {
                val matchesPlatform = when {
                    cleanStudioQuery.contains("ghibli") -> group.title.contains("ghibli", true) || group.overview?.contains("ghibli", true) == true || group.items.any { it.fileName.contains("ghibli", true) }
                    cleanStudioQuery.contains("netflix") -> group.hasNetflix
                    cleanStudioQuery.contains("prime") || cleanStudioQuery.contains("amazon") -> group.hasPrimeVideo
                    cleanStudioQuery.contains("disney") || cleanStudioQuery.contains("hotstar") -> group.hasDisney
                    cleanStudioQuery.contains("apple") -> group.hasAppleTv
                    cleanStudioQuery.contains("crunchyroll") -> group.hasCrunchyroll
                    cleanStudioQuery.contains("zee5") -> group.hasZee5
                    else -> {
                        group.overview?.contains(cleanStudioQuery, ignoreCase = true) == true ||
                        group.items.any { it.fileName.contains(cleanStudioQuery, ignoreCase = true) }
                    }
                }
                if (!matchesPlatform) return@filter false
            }

            // Audio / Language match
            if (languagesLower.isNotEmpty()) {
                val hasLang = languagesLower.any { lang ->
                    when {
                        lang.contains("hindi") -> group.hasHindi
                        lang.contains("english") -> group.hasEnglish
                        lang.contains("japanese") -> group.hasJapanese
                        lang.contains("dual") -> group.hasDualAudio
                        lang.contains("multi") -> group.hasMultiAudio
                        else -> group.items.any { it.fileName.contains(lang, ignoreCase = true) }
                    }
                }
                if (!hasLang) return@filter false
            }

            // Genres match (check overview or title or anime type)
            if (genresLower.isNotEmpty()) {
                val overviewLower = group.overview?.lowercase().orEmpty()
                val titleLower = group.title.lowercase()
                val hasGenre = genresLower.any { g ->
                    overviewLower.contains(g) || titleLower.contains(g) ||
                    (g.contains("anime") && group.type == "anime") ||
                    (g.contains("animation") && (group.type == "anime" || overviewLower.contains("animation") || overviewLower.contains("animated")))
                }
                if (!hasGenre) return@filter false
            }

            // Keywords / Semantic match (e.g. 4k, cyberpunk, time travel)
            if (keywordsLower.isNotEmpty()) {
                val overviewLower = group.overview?.lowercase().orEmpty()
                val titleLower = group.title.lowercase()
                val hasKeyword = keywordsLower.any { kw ->
                    when (kw) {
                        "4k", "2160p", "uhd" -> group.has4K
                        "imax" -> group.hasIMAX
                        "remux" -> group.hasRemux
                        "hdr", "dolby vision", "dv" -> group.hasHdrDv
                        else -> titleLower.contains(kw) || overviewLower.contains(kw) || group.items.any { it.fileName.contains(kw, ignoreCase = true) }
                    }
                }
                if (!hasKeyword) return@filter false
            }

            true
        }

        // Sort items
        return when (f.sortBy) {
            CatalogSortBy.RECENT -> if (f.sortAscending) filtered.sortedBy { it.dateAdded } else filtered.sortedByDescending { it.dateAdded }
            CatalogSortBy.RATING -> if (f.sortAscending) filtered.sortedBy { it.rating } else filtered.sortedByDescending { it.rating }
            CatalogSortBy.YEAR -> if (f.sortAscending) filtered.sortedBy { it.year?.toIntOrNull() ?: 0 } else filtered.sortedByDescending { it.year?.toIntOrNull() ?: 0 }
            CatalogSortBy.TITLE -> if (f.sortAscending) filtered.sortedBy { it.title.lowercase() } else filtered.sortedByDescending { it.title.lowercase() }
        }
    }
}
