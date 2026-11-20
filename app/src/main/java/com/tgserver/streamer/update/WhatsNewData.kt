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
        version = "1.5",
        releaseTitle = "Infuse & Vesper Support, Performance Boost",
        releaseDate = "November 2026",
        items = listOf(
            WhatsNewItem(
                category = ChangeCategory.NEW,
                title = "Added Infuse & Vesper Support (Media Server via Jellyfin)"
            ),
            WhatsNewItem(
                category = ChangeCategory.IMPROVEMENT,
                title = "Performance boost"
            ),
            WhatsNewItem(
                category = ChangeCategory.FIX,
                title = "Minor bug fixes"
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
