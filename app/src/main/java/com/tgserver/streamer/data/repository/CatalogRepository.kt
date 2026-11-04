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
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
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

    fun sanitizeCatalog(cat: CatalogDefinition): CatalogDefinition {
        val f = cat.filter ?: CatalogFilterCriteria()
        val safeFilter = f.copy(
            personQuery = f.personQuery ?: "",
            studioQuery = f.studioQuery ?: "",
            personTmdbId = f.personTmdbId,
            studioTmdbId = f.studioTmdbId,
            genres = f.genres ?: emptyList(),
            languages = f.languages ?: emptyList(),
            keywords = f.keywords ?: emptyList(),
            mediaType = f.mediaType ?: "all"
        )
        return cat.copy(
            title = cat.title ?: "Catalog",
            filter = safeFilter,
            sortOrder = cat.sortOrder
        )
    }

    fun getCatalogs(): List<CatalogDefinition> {
        val rawJson = prefs.getString(PREF_KEY_CATALOGS, null)
        if (rawJson.isNullOrBlank()) {
            return emptyList()
        }

        return try {
            val type = object : TypeToken<List<CatalogDefinition>>() {}.type
            val list: List<CatalogDefinition> = gson.fromJson(rawJson, type) ?: emptyList()
            // Clean up any legacy starter catalogs if present and sanitize fields safely
            val cleaned = list.filterNotNull().filter { !it.id.startsWith("starter_") }.map { sanitizeCatalog(it) }
            if (cleaned.size != list.size) {
                saveCatalogs(cleaned)
            }
            cleaned.sortedBy { it.sortOrder }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse catalogs json: ${e.message}", e)
            emptyList()
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

    private var tvdbToken: String? = null
    private var tvdbTokenExpiry: Long = 0L

    private fun getTvdbToken(apiKey: String): String? {
        val now = System.currentTimeMillis()
        if (!tvdbToken.isNullOrBlank() && now < tvdbTokenExpiry) return tvdbToken
        if (apiKey.isBlank()) return null
        return try {
            val jsonBody = """{"apikey":"$apiKey"}"""
            val body = jsonBody.toRequestBody("application/json".toMediaType())
            val req = Request.Builder()
                .url("https://api4.thetvdb.com/v4/login")
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
                .header("Accept", "application/json")
                .post(body)
                .build()
            httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val respStr = resp.body?.string().orEmpty()
                    val root = JSONObject(respStr)
                    val token = root.optJSONObject("data")?.optString("token")
                    if (!token.isNullOrBlank()) {
                        tvdbToken = token
                        tvdbTokenExpiry = now + 24 * 3600 * 1000L
                        token
                    } else null
                } else null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun searchTvdbPeople(query: String, apiKey: String): List<TmdbPersonItem> {
        val token = getTvdbToken(apiKey) ?: return emptyList()
        return try {
            val encoded = URLEncoder.encode(query.trim(), "UTF-8")
            val url = "https://api4.thetvdb.com/v4/search?query=$encoded&type=people"
            val req = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $token")
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
                .header("Accept", "application/json")
                .get()
                .build()
            httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body?.string().orEmpty()
                    val root = JSONObject(body)
                    val data = root.optJSONArray("data") ?: return emptyList()
                    val list = mutableListOf<TmdbPersonItem>()
                    for (i in 0 until minOf(data.length(), 6)) {
                        val obj = data.getJSONObject(i)
                        val name = obj.optString("name")
                        val idStr = obj.optString("id").removePrefix("people-").removePrefix("person-")
                        val id = idStr.toIntOrNull() ?: (i + 1)
                        val img = obj.optString("image_url").takeIf { it.isNotBlank() }
                        if (name.isNotBlank()) {
                            list.add(TmdbPersonItem(id = id, name = name, department = "Cast / Crew", profilePath = img))
                        }
                    }
                    list
                } else emptyList()
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun searchTvdbCompanies(query: String, apiKey: String): List<TmdbCompanyItem> {
        val token = getTvdbToken(apiKey) ?: return emptyList()
        return try {
            val encoded = URLEncoder.encode(query.trim(), "UTF-8")
            val url = "https://api4.thetvdb.com/v4/search?query=$encoded&type=company"
            val req = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $token")
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
                .header("Accept", "application/json")
                .get()
                .build()
            httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body?.string().orEmpty()
                    val root = JSONObject(body)
                    val data = root.optJSONArray("data") ?: return emptyList()
                    val list = mutableListOf<TmdbCompanyItem>()
                    for (i in 0 until minOf(data.length(), 6)) {
                        val obj = data.getJSONObject(i)
                        val name = obj.optString("name")
                        val idStr = obj.optString("id").removePrefix("company-")
                        val id = idStr.toIntOrNull() ?: (i + 1)
                        if (name.isNotBlank()) {
                            list.add(TmdbCompanyItem(id = id, name = name, logoPath = null))
                        }
                    }
                    list
                } else emptyList()
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun searchTvmazePeople(query: String): List<TmdbPersonItem> {
        val clean = query.trim()
        if (clean.length < 2) return emptyList()
        val queriesToTry = mutableListOf(clean)
        if (clean.equals("shahrukh khan", ignoreCase = true) || clean.equals("shahrukh", ignoreCase = true) || clean.equals("srk", ignoreCase = true)) {
            queriesToTry.add("shah rukh khan")
        }
        for (q in queriesToTry) {
            try {
                val encoded = URLEncoder.encode(q, "UTF-8")
                val url = "https://api.tvmaze.com/search/people?q=$encoded"
                val req = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
                    .header("Accept", "application/json")
                    .get()
                    .build()
                httpClient.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val body = resp.body?.string().orEmpty()
                        val arr = JSONArray(body)
                        if (arr.length() > 0) {
                            val items = mutableListOf<TmdbPersonItem>()
                            for (i in 0 until minOf(arr.length(), 8)) {
                                val personObj = arr.getJSONObject(i).optJSONObject("person") ?: continue
                                val name = personObj.optString("name")
                                val id = personObj.optInt("id", -(i + 1))
                                val imgObj = personObj.optJSONObject("image")
                                val img = imgObj?.optString("medium") ?: imgObj?.optString("original")
                                if (name.isNotBlank()) {
                                    items.add(
                                        TmdbPersonItem(
                                            id = id,
                                            name = name,
                                            department = "Cast & Crew",
                                            profilePath = img
                                        )
                                    )
                                }
                            }
                            if (items.isNotEmpty()) return items
                        }
                    }
                }
            } catch (_: Exception) {}
        }
        return emptyList()
    }

    suspend fun searchPeople(query: String, tmdbKey: String, tvdbKey: String): List<TmdbPersonItem> = withContext(Dispatchers.IO) {
        val clean = query.trim()
        if (clean.isBlank()) return@withContext emptyList()
        val results = mutableListOf<TmdbPersonItem>()
        val seenNames = mutableSetOf<String>()

        val normalizedQuery = when (clean.lowercase()) {
            "srk", "shahrukh", "shahrukh khan" -> "Shah Rukh Khan"
            "salman", "bhai" -> "Salman Khan"
            "aamir" -> "Aamir Khan"
            "akshay" -> "Akshay Kumar"
            "amitabh", "big b" -> "Amitabh Bachchan"
            "hrithik" -> "Hrithik Roshan"
            "ranbir" -> "Ranbir Kapoor"
            "ranveer" -> "Ranveer Singh"
            "deepika" -> "Deepika Padukone"
            "priyanka" -> "Priyanka Chopra"
            "alia" -> "Alia Bhatt"
            "nolan" -> "Christopher Nolan"
            "zack", "snyder" -> "Zack Snyder"
            "tarantino" -> "Quentin Tarantino"
            "scorsese" -> "Martin Scorsese"
            "spielberg" -> "Steven Spielberg"
            "fincher" -> "David Fincher"
            "cameron" -> "James Cameron"
            "villeneuve" -> "Denis Villeneuve"
            "leo", "dicaprio" -> "Leonardo DiCaprio"
            "cruise" -> "Tom Cruise"
            "keanu" -> "Keanu Reeves"
            "bale" -> "Christian Bale"
            "rdj", "downey" -> "Robert Downey Jr."
            "cillian" -> "Cillian Murphy"
            else -> clean
        }

        // 1. Instant match from Curated Directory (top Indian + Hollywood directors/actors)
        CURATED_PEOPLE.filter {
            it.name.contains(clean, ignoreCase = true) || it.name.contains(normalizedQuery, ignoreCase = true)
        }.forEach {
            if (seenNames.add(it.name.lowercase())) {
                results.add(it)
            }
        }

        // 2. Search TMDb if key is present
        if (tmdbKey.isNotBlank()) {
            val tmdbItems = searchTmdbPeople(normalizedQuery, tmdbKey)
            tmdbItems.forEach {
                if (seenNames.add(it.name.lowercase())) {
                    results.add(it)
                }
            }
            if (clean != normalizedQuery) {
                searchTmdbPeople(clean, tmdbKey).forEach {
                    if (seenNames.add(it.name.lowercase())) {
                        results.add(it)
                    }
                }
            }
        }

        // 3. Search TVmaze (Free open public directory - No API key required!)
        if (results.size < 8) {
            val tvmazeItems = searchTvmazePeople(normalizedQuery)
            tvmazeItems.forEach {
                if (seenNames.add(it.name.lowercase())) {
                    results.add(it)
                }
            }
        }

        // 4. Search TVDb if key is present and results < 8
        if (tvdbKey.isNotBlank() && results.size < 8) {
            val tvdbItems = searchTvdbPeople(normalizedQuery, tvdbKey)
            tvdbItems.forEach {
                if (seenNames.add(it.name.lowercase())) {
                    results.add(it)
                }
            }
        }

        // 5. Always provide an option to tag exact query if not already exactly matched
        if (clean.length >= 2 && seenNames.none { it.equals(clean.lowercase(), true) }) {
            results.add(TmdbPersonItem(id = -1, name = clean, department = "Custom Person"))
        }

        results
    }

    suspend fun searchCompanies(query: String, tmdbKey: String, tvdbKey: String): List<TmdbCompanyItem> = withContext(Dispatchers.IO) {
        val clean = query.trim()
        if (clean.isBlank()) return@withContext emptyList()
        val results = mutableListOf<TmdbCompanyItem>()
        val seenNames = mutableSetOf<String>()

        val normalizedQuery = when (clean.lowercase()) {
            "hullu", "hulu" -> "Hulu"
            "peacock" -> "Peacock"
            "paramount", "paramount+", "paramount plus" -> "Paramount+"
            "showtime" -> "Showtime"
            "jio", "jiocinema" -> "JioCinema"
            "sonyliv", "sony liv" -> "Sony LIV"
            "zee", "zee5" -> "ZEE5"
            "hotstar", "disney hotstar", "disney+ hotstar" -> "Disney+ Hotstar"
            "prime", "prime video", "amazon", "amazon prime" -> "Prime Video"
            "apple", "appletv", "apple tv", "apple tv+" -> "Apple TV+"
            "netflix", "nf" -> "Netflix"
            "hbo", "hbomax", "max" -> "HBO"
            "crunchyroll", "cr" -> "Crunchyroll"
            "warner", "warner bros", "wb" -> "Warner Bros."
            "ghibli", "studio ghibli" -> "Studio Ghibli"
            "marvel", "mcu" -> "Marvel Studios"
            "dc", "dcu" -> "DC Studios"
            else -> clean
        }

        // 1. Instant match from Curated Directory (All global + Indian + Asian OTT & Studios)
        CURATED_STUDIOS.filter {
            it.name.contains(clean, ignoreCase = true) || it.name.contains(normalizedQuery, ignoreCase = true)
        }.forEach {
            if (seenNames.add(it.name.lowercase())) {
                results.add(it)
            }
        }

        // 2. Search TMDb if key is present
        if (tmdbKey.isNotBlank()) {
            val tmdbItems = searchTmdbCompanies(normalizedQuery, tmdbKey)
            tmdbItems.forEach {
                if (seenNames.add(it.name.lowercase())) {
                    results.add(it)
                }
            }
            if (clean != normalizedQuery) {
                searchTmdbCompanies(clean, tmdbKey).forEach {
                    if (seenNames.add(it.name.lowercase())) {
                        results.add(it)
                    }
                }
            }
        }

        // 3. Search TVDb if key is present and results < 8
        if (tvdbKey.isNotBlank() && results.size < 8) {
            val tvdbItems = searchTvdbCompanies(normalizedQuery, tvdbKey)
            tvdbItems.forEach {
                if (seenNames.add(it.name.lowercase())) {
                    results.add(it)
                }
            }
        }

        // 4. Always provide an option to tag exact query if not already exactly matched
        if (clean.length >= 2 && seenNames.none { it.equals(clean.lowercase(), true) }) {
            results.add(TmdbCompanyItem(id = -1, name = clean, logoPath = null))
        }

        results
    }

    suspend fun searchTmdbPeople(query: String, apiKey: String): List<TmdbPersonItem> = withContext(Dispatchers.IO) {
        if (query.isBlank() || apiKey.isBlank()) return@withContext emptyList()
        try {
            val encoded = URLEncoder.encode(query.trim(), "UTF-8")
            val url = "https://api.themoviedb.org/3/search/person?api_key=$apiKey&query=$encoded&language=en-US&include_adult=false"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
                .header("Accept", "application/json")
                .get()
                .build()
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
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
                .header("Accept", "application/json")
                .get()
                .build()
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

    suspend fun getCompanyFilmographyIds(companyId: Int, apiKey: String): Set<Int> = withContext(Dispatchers.IO) {
        if (companyId <= 0 || apiKey.isBlank()) return@withContext emptySet()
        companyFilmographyCache[companyId]?.let { return@withContext it }

        try {
            val idSet = mutableSetOf<Int>()
            // 1. Discover movies with this company
            val movieUrl = "https://api.themoviedb.org/3/discover/movie?api_key=$apiKey&with_companies=$companyId&page=1"
            httpClient.newCall(Request.Builder().url(movieUrl).build()).execute().use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body?.string().orEmpty()
                    val results = JSONObject(body).optJSONArray("results")
                    if (results != null) {
                        for (i in 0 until results.length()) {
                            val id = results.getJSONObject(i).optInt("id", 0)
                            if (id > 0) idSet.add(id)
                        }
                    }
                }
            }
            // 2. Discover TV series with this company
            val tvUrl = "https://api.themoviedb.org/3/discover/tv?api_key=$apiKey&with_companies=$companyId&page=1"
            httpClient.newCall(Request.Builder().url(tvUrl).build()).execute().use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body?.string().orEmpty()
                    val results = JSONObject(body).optJSONArray("results")
                    if (results != null) {
                        for (i in 0 until results.length()) {
                            val id = results.getJSONObject(i).optInt("id", 0)
                            if (id > 0) idSet.add(id)
                        }
                    }
                }
            }
            // 3. Known network discover fallback (e.g. HBO=49 / 3186)
            val networkId = when (companyId) {
                3268 -> 49 // HBO
                1782 -> 213 // Netflix
                2739 -> 2739 // Disney+
                2552 -> 2552 // Apple TV+
                20580 -> 1024 // Prime Video
                else -> null
            }
            if (networkId != null) {
                val netUrl = "https://api.themoviedb.org/3/discover/tv?api_key=$apiKey&with_networks=$networkId&page=1"
                httpClient.newCall(Request.Builder().url(netUrl).build()).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val body = resp.body?.string().orEmpty()
                        val results = JSONObject(body).optJSONArray("results")
                        if (results != null) {
                            for (i in 0 until results.length()) {
                                val id = results.getJSONObject(i).optInt("id", 0)
                                if (id > 0) idSet.add(id)
                            }
                        }
                    }
                }
            }

            companyFilmographyCache[companyId] = idSet
            idSet
        } catch (e: Exception) {
            Log.e(TAG, "getCompanyFilmographyIds error: ${e.message}")
            emptySet()
        }
    }

    /**
     * Filters media groups using the catalog filter criteria.
     * High performance, single pass filtering with zero UI stutter.
     */
    fun filterMediaForCatalog(
        catalog: CatalogDefinition,
        mediaGroups: List<MediaGroup>,
        personTmdbIds: Set<Int> = emptySet(),
        studioTmdbIds: Set<Int> = emptySet()
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
                val tokens = cleanPersonQuery.split(" ", "_", ".").filter { it.length >= 3 }
                val tokenMatch = tokens.size >= 2 && tokens.all { t ->
                    group.title.contains(t, true) || group.overview?.contains(t, true) == true || group.items.any { it.fileName.contains(t, true) }
                }
                if (!inTitle && !inOverview && !inItems && !tokenMatch) return@filter false
            }

            // Studio / Network match
            if (cleanStudioQuery.isNotBlank() || studioTmdbIds.isNotEmpty()) {
                val matchesPlatform = when {
                    studioTmdbIds.isNotEmpty() && group.tmdbId != null && group.tmdbId in studioTmdbIds -> true
                    cleanStudioQuery.contains("ghibli") -> group.title.contains("ghibli", true) || group.items.any { it.fileName.contains("ghibli", true) }
                    cleanStudioQuery.contains("hbo") || cleanStudioQuery.contains("max") -> group.hasHbo
                    cleanStudioQuery.contains("netflix") -> group.hasNetflix
                    cleanStudioQuery.contains("prime") || cleanStudioQuery.contains("amazon") -> group.hasPrimeVideo
                    cleanStudioQuery.contains("disney") || cleanStudioQuery.contains("hotstar") -> group.hasDisney
                    cleanStudioQuery.contains("apple") -> group.hasAppleTv
                    cleanStudioQuery.contains("crunchyroll") -> group.hasCrunchyroll
                    cleanStudioQuery.contains("zee5") || cleanStudioQuery.contains("zee") -> group.hasZee5
                    cleanStudioQuery.contains("warner") -> group.hasWarnerBros
                    cleanStudioQuery.contains("marvel") -> group.title.contains("marvel", true) || group.items.any { it.fileName.contains("marvel", true) }
                    cleanStudioQuery.contains("hulu") -> group.items.any { it.fileName.contains("hulu", true) || it.fileName.contains(".hulu.", true) }
                    cleanStudioQuery.contains("peacock") -> group.items.any { it.fileName.contains("peacock", true) }
                    cleanStudioQuery.contains("paramount") -> group.items.any { it.fileName.contains("paramount", true) }
                    cleanStudioQuery.contains("jiocinema") || cleanStudioQuery.contains("jio") -> group.items.any { it.fileName.contains("jio", true) }
                    cleanStudioQuery.contains("sonyliv") || cleanStudioQuery.contains("sony liv") -> group.items.any { it.fileName.contains("sonyliv", true) || it.fileName.contains("sony", true) }
                    cleanStudioQuery.contains("a24") -> group.items.any { it.fileName.contains(Regex("""(?i)[\W_]a24[\W_]""")) }
                    cleanStudioQuery.isNotBlank() -> {
                        val wordRegex = Regex("""(?i)\b${Regex.escape(cleanStudioQuery)}\b""")
                        wordRegex.containsMatchIn(group.title) || group.items.any { wordRegex.containsMatchIn(it.fileName) }
                    }
                    else -> false
                }
                if (!matchesPlatform) return@filter false
            }

            // Audio / Language match
            val effectiveLanguages = languagesLower.filter { it != "any" }
            if (effectiveLanguages.isNotEmpty()) {
                val hasLang = effectiveLanguages.any { lang ->
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

            // Genres match (multi-select combo: item must match all selected genres)
            if (genresLower.isNotEmpty()) {
                val overviewLower = group.overview?.lowercase().orEmpty()
                val titleLower = group.title.lowercase()
                val hasAllGenres = genresLower.all { g ->
                    val cleanG = g.trim().lowercase()
                    when {
                        cleanG == "anime" -> group.type == "anime" || overviewLower.contains("anime") || group.items.any { it.fileName.contains("anime", true) }
                        cleanG in listOf("animation", "animated") -> group.type == "anime" || overviewLower.contains("animation") || overviewLower.contains("animated")
                        cleanG in listOf("science fiction", "sci-fi", "scifi") -> overviewLower.contains("sci-fi") || overviewLower.contains("science fiction") || titleLower.contains("sci-fi")
                        cleanG in listOf("time travel") -> overviewLower.contains("time travel") || titleLower.contains("time travel") || group.items.any { it.fileName.contains("time travel", true) }
                        cleanG in listOf("mystery") -> overviewLower.contains("mystery") || titleLower.contains("mystery") || group.items.any { it.fileName.contains("mystery", true) }
                        cleanG in listOf("murder") -> overviewLower.contains("murder") || titleLower.contains("murder") || group.items.any { it.fileName.contains("murder", true) }
                        cleanG in listOf("horror") -> overviewLower.contains("horror") || titleLower.contains("horror") || group.items.any { it.fileName.contains("horror", true) }
                        cleanG in listOf("thriller") -> overviewLower.contains("thriller") || titleLower.contains("thriller") || group.items.any { it.fileName.contains("thriller", true) }
                        cleanG in listOf("crime") -> overviewLower.contains("crime") || titleLower.contains("crime") || group.items.any { it.fileName.contains("crime", true) }
                        else -> overviewLower.contains(cleanG) || titleLower.contains(cleanG) || group.items.any { it.fileName.contains(cleanG, ignoreCase = true) }
                    }
                }
                if (!hasAllGenres) return@filter false
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

private val CURATED_PEOPLE = listOf(
    // Indian Icons & Directors
    TmdbPersonItem(35742, "Shah Rukh Khan", "Acting", "https://static.tvmaze.com/uploads/images/medium_portrait/519/1297756.jpg"),
    TmdbPersonItem(52763, "Salman Khan", "Acting", null),
    TmdbPersonItem(52764, "Aamir Khan", "Acting", null),
    TmdbPersonItem(35741, "Amitabh Bachchan", "Acting", null),
    TmdbPersonItem(35745, "Akshay Kumar", "Acting", null),
    TmdbPersonItem(78875, "Hrithik Roshan", "Acting", null),
    TmdbPersonItem(85034, "Ranbir Kapoor", "Acting", null),
    TmdbPersonItem(111116, "Ranveer Singh", "Acting", null),
    TmdbPersonItem(93703, "Deepika Padukone", "Acting", null),
    TmdbPersonItem(53655, "Priyanka Chopra", "Acting", null),
    TmdbPersonItem(1108120, "Alia Bhatt", "Acting", null),
    TmdbPersonItem(85035, "Katrina Kaif", "Acting", null),
    TmdbPersonItem(35743, "Kareena Kapoor", "Acting", null),
    TmdbPersonItem(14887, "Prabhas", "Acting", null),
    TmdbPersonItem(23788, "Allu Arjun", "Acting", null),
    TmdbPersonItem(1184318, "Ram Charan", "Acting", null),
    TmdbPersonItem(1184319, "Jr NTR", "Acting", null),
    TmdbPersonItem(135899, "Thalapathy Vijay", "Acting", null),
    TmdbPersonItem(52762, "Rajinikanth", "Acting", null),
    TmdbPersonItem(52761, "Kamal Haasan", "Acting", null),
    TmdbPersonItem(14888, "Dhanush", "Acting", null),
    TmdbPersonItem(14889, "Suriya", "Acting", null),
    TmdbPersonItem(19888, "Yash", "Acting", null),
    TmdbPersonItem(14890, "S.S. Rajamouli", "Directing", null),
    TmdbPersonItem(19889, "Prashanth Neel", "Directing", null),
    TmdbPersonItem(14891, "Anurag Kashyap", "Directing", null),
    TmdbPersonItem(14892, "Sanjay Leela Bhansali", "Directing", null),
    TmdbPersonItem(14893, "Rohit Shetty", "Directing", null),
    TmdbPersonItem(14894, "Mani Ratnam", "Directing", null),
    TmdbPersonItem(19890, "Lokesh Kanagaraj", "Directing", null),
    TmdbPersonItem(14895, "Rajkumar Hirani", "Directing", null),

    // Hollywood & Global Directors & Creators
    TmdbPersonItem(15217, "Zack Snyder", "Directing", "/en46a8D99o2B99n9j33tZ27bM81.jpg"),
    TmdbPersonItem(525, "Christopher Nolan", "Directing", "/xuAIuYSmsUzKlUMBFGVZaWsY3Z5.jpg"),
    TmdbPersonItem(138, "Quentin Tarantino", "Directing", "/9O9GgV0jK0U4Z4Lw9f5lZ0o2B1P.jpg"),
    TmdbPersonItem(137427, "Denis Villeneuve", "Directing", "/k9F6gE86qN6t620vO6oA1yK4M3z.jpg"),
    TmdbPersonItem(1032, "Martin Scorsese", "Directing", "/9U9Y5G0jK0U4Z4Lw9f5lZ0o2B1P.jpg"),
    TmdbPersonItem(488, "Steven Spielberg", "Directing", null),
    TmdbPersonItem(2710, "James Cameron", "Directing", null),
    TmdbPersonItem(7467, "David Fincher", "Directing", null),
    TmdbPersonItem(578, "Ridley Scott", "Directing", null),
    TmdbPersonItem(608, "Hayao Miyazaki", "Directing", null),
    TmdbPersonItem(12519, "Makoto Shinkai", "Directing", null),
    TmdbPersonItem(240, "Stanley Kubrick", "Directing", null),
    TmdbPersonItem(2636, "Alfred Hitchcock", "Directing", null),
    TmdbPersonItem(510, "Tim Burton", "Directing", null),
    TmdbPersonItem(5655, "Wes Anderson", "Directing", null),
    TmdbPersonItem(10828, "Guillermo del Toro", "Directing", null),
    TmdbPersonItem(7623, "Sam Raimi", "Directing", null),
    TmdbPersonItem(108, "Peter Jackson", "Directing", null),
    TmdbPersonItem(1, "George Lucas", "Directing", null),
    TmdbPersonItem(956, "Guy Ritchie", "Directing", null),
    TmdbPersonItem(21684, "Bong Joon-ho", "Directing", null),
    TmdbPersonItem(19271, "Anthony & Joe Russo", "Directing", null),
    TmdbPersonItem(15277, "James Gunn", "Directing", null),

    // Hollywood Stars
    TmdbPersonItem(6193, "Leonardo DiCaprio", "Acting", null),
    TmdbPersonItem(500, "Tom Cruise", "Acting", null),
    TmdbPersonItem(6384, "Keanu Reeves", "Acting", null),
    TmdbPersonItem(3894, "Christian Bale", "Acting", null),
    TmdbPersonItem(3223, "Robert Downey Jr.", "Acting", null),
    TmdbPersonItem(2037, "Cillian Murphy", "Acting", null),
    TmdbPersonItem(287, "Brad Pitt", "Acting", null),
    TmdbPersonItem(30614, "Ryan Gosling", "Acting", null),
    TmdbPersonItem(31, "Tom Hanks", "Acting", null),
    TmdbPersonItem(85, "Johnny Depp", "Acting", null),
    TmdbPersonItem(1810, "Heath Ledger", "Acting", null),
    TmdbPersonItem(6968, "Hugh Jackman", "Acting", null),
    TmdbPersonItem(10859, "Ryan Reynolds", "Acting", null),
    TmdbPersonItem(74568, "Chris Hemsworth", "Acting", null),
    TmdbPersonItem(16828, "Chris Evans", "Acting", null),
    TmdbPersonItem(1245, "Scarlett Johansson", "Acting", null),
    TmdbPersonItem(234352, "Margot Robbie", "Acting", null),
    TmdbPersonItem(54693, "Emma Stone", "Acting", null),
    TmdbPersonItem(505710, "Zendaya", "Acting", null),
    TmdbPersonItem(1190668, "Timothée Chalamet", "Acting", null),
    TmdbPersonItem(73968, "Henry Cavill", "Acting", null),
    TmdbPersonItem(1253360, "Pedro Pascal", "Acting", null)
)

private val CURATED_STUDIOS = listOf(
    // Global OTT & Streaming
    TmdbCompanyItem(2, "Hulu", null),
    TmdbCompanyItem(1782, "Netflix", "/wwemzKWzjKYJFfCeiB57q3r4Bcm.png"),
    TmdbCompanyItem(20580, "Prime Video", "/ifhbNuuVnlwYy5oXA05XaM5Xi7P.png"),
    TmdbCompanyItem(2739, "Disney+", "/gJ8VX6JSu3ci4ewTuRztJiq9CuB.png"),
    TmdbCompanyItem(3919, "Disney+ Hotstar", null),
    TmdbCompanyItem(3268, "HBO / Max", "/tuomPhY2UtuPTqqFnKMVHvSb724.png"),
    TmdbCompanyItem(2552, "Apple TV+", "/4KAy34EHvRM25Ih8Mz8Bm9VOtwP.png"),
    TmdbCompanyItem(3353, "Peacock", null),
    TmdbCompanyItem(4330, "Paramount+", null),
    TmdbCompanyItem(67, "Showtime", null),
    TmdbCompanyItem(9991, "JioCinema", null),
    TmdbCompanyItem(9992, "Sony LIV", null),
    TmdbCompanyItem(9993, "ZEE5", null),
    TmdbCompanyItem(11111, "Crunchyroll", null),
    TmdbCompanyItem(4353, "Discovery+", null),

    // Major Hollywood Studios
    TmdbCompanyItem(174, "Warner Bros.", "/ky0xOc5maQswTVI8VOYEN0mX094.png"),
    TmdbCompanyItem(33, "Universal Pictures", "/8lvHyhjr8oUKOOy2dKXoALWKdp0.png"),
    TmdbCompanyItem(4, "Paramount Pictures", null),
    TmdbCompanyItem(34, "Sony Pictures", null),
    TmdbCompanyItem(420, "Marvel Studios", "/hUzeosd33nzE5MCNsZxCGEKTXaQ.png"),
    TmdbCompanyItem(9994, "DC Studios", null),
    TmdbCompanyItem(41077, "A24", "/1ZXVjXYUsCU99Yjh6CiPVYEx3BK.png"),
    TmdbCompanyItem(1632, "Lionsgate", null),
    TmdbCompanyItem(25, "20th Century Studios", null),
    TmdbCompanyItem(21, "MGM", null),
    TmdbCompanyItem(3, "Pixar", null),
    TmdbCompanyItem(1, "Lucasfilm", null),
    TmdbCompanyItem(521, "DreamWorks Animation", null),
    TmdbCompanyItem(3172, "Blumhouse", null),

    // Indian Production Houses
    TmdbCompanyItem(1569, "Yash Raj Films", null),
    TmdbCompanyItem(1935, "Dharma Productions", null),
    TmdbCompanyItem(7212, "Red Chillies Entertainment", null),
    TmdbCompanyItem(3522, "T-Series", null),
    TmdbCompanyItem(9995, "Hombale Films", null),
    TmdbCompanyItem(9996, "Mythri Movie Makers", null),
    TmdbCompanyItem(9997, "Lyca Productions", null),
    TmdbCompanyItem(9998, "Geetha Arts", null),

    // Anime & Animation Studios
    TmdbCompanyItem(10342, "Studio Ghibli", "/7rwSXluNWZAlKL2qi0GtIPJuqih.png"),
    TmdbCompanyItem(13589, "MAPPA", null),
    TmdbCompanyItem(12693, "Ufotable", null),
    TmdbCompanyItem(5542, "Toei Animation", null),
    TmdbCompanyItem(3734, "Madhouse", null),
    TmdbCompanyItem(2883, "Bones", null),
    TmdbCompanyItem(5267, "Kyoto Animation", null),
    TmdbCompanyItem(20779, "Wit Studio", null),
    TmdbCompanyItem(9999, "CloverWorks", null)
)
