package com.tgserver.streamer.data.model

import java.util.UUID

/**
 * Supported AI providers for catalog curation
 */
enum class AiProvider(val id: String, val displayName: String) {
    GEMINI("gemini", "Gemini API"),
    OPENROUTER("openrouter", "OpenRouter API")
}

/**
 * Sort options for catalogs
 */
enum class CatalogSortBy(val id: String, val label: String) {
    RECENT("recent", "Recently Added"),
    RATING("rating", "IMDb / TMDB Rating"),
    YEAR("year", "Release Year"),
    TITLE("title", "Alphabetical (Title)")
}

/**
 * Filter criteria used by both Normal and AI-Powered Catalogs
 */
data class CatalogFilterCriteria(
    val mediaType: String = "all", // "all", "movie", "series"
    val personQuery: String = "", // e.g. "Christopher Nolan"
    val personTmdbId: Int? = null,
    val personRole: String? = null, // "Directing", "Acting", etc.
    val personProfilePath: String? = null,
    val studioQuery: String = "", // e.g. "Studio Ghibli", "Warner Bros", "Netflix"
    val studioTmdbId: Int? = null,
    val studioLogoPath: String? = null,
    val genres: List<String> = emptyList(), // e.g. ["Sci-Fi", "Animation"]
    val languages: List<String> = emptyList(), // e.g. ["English", "Hindi", "Japanese"]
    val ratingFilterEnabled: Boolean = false,
    val minRating: Float = 7.0f,
    val maxRating: Float = 10.0f,
    val yearFilterEnabled: Boolean = false,
    val minYear: Int = 1990,
    val maxYear: Int = 2026,
    val sortBy: CatalogSortBy = CatalogSortBy.RECENT,
    val sortAscending: Boolean = false,
    val keywords: List<String> = emptyList() // Semantic keywords for AI or overview search
)

/**
 * Master Catalog Definition
 */
data class CatalogDefinition(
    val id: String = UUID.randomUUID().toString(),
    var name: String,
    var isAiPowered: Boolean = false,
    var aiPrompt: String? = null,
    var isHidden: Boolean = false,
    var pinToHome: Boolean = true,
    var sortOrder: Int = 0,
    var filter: CatalogFilterCriteria = CatalogFilterCriteria(),
    var createdAt: Long = System.currentTimeMillis()
)

/**
 * TMDB Person search result item
 */
data class TmdbPersonItem(
    val id: Int,
    val name: String,
    val department: String? = null, // "Directing", "Acting"
    val profilePath: String? = null
) {
    val fullProfileUrl: String?
        get() = if (!profilePath.isNullOrBlank()) "https://image.tmdb.org/t/p/w185$profilePath" else null
}

/**
 * TMDB Studio / Production Company search result item
 */
data class TmdbCompanyItem(
    val id: Int,
    val name: String,
    val logoPath: String? = null
) {
    val fullLogoUrl: String?
        get() = if (!logoPath.isNullOrBlank()) "https://image.tmdb.org/t/p/w185$logoPath" else null
}

/**
 * Pre-made default catalogs for a brand new user (empty by default)
 */
fun getDefaultStarterCatalogs(): List<CatalogDefinition> {
    return emptyList()
}
