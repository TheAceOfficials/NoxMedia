package com.tgserver.streamer.update

/**
 * Categorization for changelog entries.
 * hexColor defines the accent tint for badges and bullet indicators.
 */
enum class ChangeCategory(
    val label: String,
    val hexColor: Long
) {
    NEW("New Features", 0xFF22C55E),       // Vibrant Green
    IMPROVEMENT("Improvements", 0xFF38BDF8), // Cyan / Sky Blue
    FIX("Bug Fixes", 0xFFF59E0B),           // Amber
    CHANGE("Changes", 0xFFA855F7)           // Modern Purple
}

/**
 * Individual changelog line item.
 */
data class WhatsNewItem(
    val category: ChangeCategory,
    val title: String,
    val description: String? = null
)

/**
 * Represents a release version with metadata and structured changes.
 */
data class WhatsNewRelease(
    val version: String,
    val releaseTitle: String,
    val releaseDate: String,
    val items: List<WhatsNewItem>
)

/**
 * Central repository for NoxMedia "What's New!" release notes.
 *
 * NOTE FOR COMMITS & RELEASES:
 * Whenever new features, improvements, or fixes are pushed,
 * completely REPLACE [items] with ONLY the changes in that specific release.
 * Do NOT append to the old list — past release notes are wiped on every update
 * so that users only see what's newly added/fixed in the current version.
 * If a category has no items in a release, it will automatically NOT be shown in the UI.
 */
object WhatsNewRepository {

    val currentRelease: WhatsNewRelease = WhatsNewRelease(
        version = "1.1-beta",
        releaseTitle = "Anime Kitsu Match & True Cloud Sync",
        releaseDate = "October 2026",
        items = listOf(
            WhatsNewItem(
                category = ChangeCategory.NEW,
                title = "Anime (Kitsu) Metadata Matching",
                description = "Dedicated Anime toggle in both Fix Metadata Match and Unmatched Content dialogs. Searches Kitsu anime database with full episode names, artwork, and direct Kitsu ID support."
            ),
            WhatsNewItem(
                category = ChangeCategory.NEW,
                title = "Overhauled Unmatched Content Rematch",
                description = "Rematching unmatched files now features snappy 350ms debounced live search, Nothing OS aesthetics, clear query icon, and direct IMDb/TMDB/Kitsu ID lookup."
            ),
            WhatsNewItem(
                category = ChangeCategory.NEW,
                title = "True Multi-Device Cloud Sync",
                description = "Bi-directional true sync across all your phones, Android TVs, and tablets. Pre-indexed library and manual rematch fixes are compressed with Gzip (~90% smaller) and synced automatically without re-scanning. Supports custom Telegram backup channels."
            ),
            WhatsNewItem(
                category = ChangeCategory.NEW,
                title = "Movie ↔ Series Category Switching",
                description = "Easily convert misplaced media between Movie and Series. Automatically sets up season and episode numbering and fetches episode-specific metadata."
            ),
            WhatsNewItem(
                category = ChangeCategory.NEW,
                title = "Early Beta Access Update Channel",
                description = "New setting toggle allows opting into bleeding-edge GitHub pre-release beta builds with automatic in-app updates."
            ),
            WhatsNewItem(
                category = ChangeCategory.NEW,
                title = "Anime Indexing Preference (Seasonal vs Absolute)",
                description = "Choose between Seasonal TVDB/TMDB (recommended for Nuvio & Season 2+ stream matching) and Absolute Kitsu numbering with real-time pipeline visualization and workflow explainer."
            ),
            WhatsNewItem(
                category = ChangeCategory.IMPROVEMENT,
                title = "Tactile Press Physics & Emil Kowalski Micro-Animations",
                description = "Snappy 180ms spring switches, physical 0.96x press depression on posters and cards, organic dialog popups, and fluid 160ms bottom tab crossfades."
            ),
            WhatsNewItem(
                category = ChangeCategory.IMPROVEMENT,
                title = "Apple TV+ 'Fix Metadata Match' Button",
                description = "Replaced ambiguous play/stream label and search icon with a prominent, labeled 'Fix Metadata Match' pill button on media detail screens."
            ),
            WhatsNewItem(
                category = ChangeCategory.FIX,
                title = "Metadata Candidate Deduplication",
                description = "Eliminated duplicate candidate cards from showing during rescan. Cross-source deduplication merges TMDB and Cinemeta results, keeping TMDB at top priority."
            )
        )
    )

    /**
     * Groups items by category preserving insertion order.
     * Only categories that actually contain items will exist in the returned map.
     */
    fun getItemsByCategory(release: WhatsNewRelease = currentRelease): Map<ChangeCategory, List<WhatsNewItem>> {
        return release.items.groupBy { it.category }
    }
}
