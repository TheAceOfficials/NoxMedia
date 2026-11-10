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
        version = "1.4",
        releaseTitle = "Streaming Engine Overhaul, Catalogs & Metadata Control",
        releaseDate = "November 2026",
        items = listOf(
            WhatsNewItem(
                category = ChangeCategory.NEW,
                title = "Custom & AI-Powered Catalogs",
                description = "Create custom shelves or let Gemini/OpenRouter curate collections directly from your library."
            ),
            WhatsNewItem(
                category = ChangeCategory.NEW,
                title = "Manual Database ID Overrides",
                description = "Assign TMDB, TVDB, or Kitsu IDs per title to prioritize specific metadata and numbering."
            ),
            WhatsNewItem(
                category = ChangeCategory.NEW,
                title = "Content Management Overhaul",
                description = "Redesigned series screen with edge-to-edge season pills, 16:9 episode accordions, and quick stream copy."
            ),
            WhatsNewItem(
                category = ChangeCategory.IMPROVEMENT,
                title = "Reconstructed Streaming Engine",
                description = "Up to 3.5x faster throughput with 70% lower buffering latency and zero-stall seeking."
            ),
            WhatsNewItem(
                category = ChangeCategory.IMPROVEMENT,
                title = "Refined Settings & Visual Polish",
                description = "Minimal Apple-style verified badges, masked AI keys with eye toggle, and collapsible beta info."
            ),
            WhatsNewItem(
                category = ChangeCategory.IMPROVEMENT,
                title = "True Cloud Sync for All-Time Usage",
                description = "All-time streaming bandwidth is permanently preserved and synchronized across all devices."
            ),
            WhatsNewItem(
                category = ChangeCategory.FIX,
                title = "Instant External Catalog Loading",
                description = "Precomputed 15ms cache eliminates Nuvio and Stremio catalog delays and blank skeletons."
            ),
            WhatsNewItem(
                category = ChangeCategory.FIX,
                title = "Home Standby Status Accuracy",
                description = "Server status card now correctly displays In Standby Mode when Alexa standby is listening."
            ),
            WhatsNewItem(
                category = ChangeCategory.FIX,
                title = "Edge-to-Edge Season Slider",
                description = "Fixed boundary clipping so season selector scrolls seamlessly across full screen width."
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
