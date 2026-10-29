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
        version = "1.3",
        releaseTitle = "Content Detection Overhaul, Performance & UI Polish",
        releaseDate = "October 2026",
        items = listOf(
            WhatsNewItem(
                category = ChangeCategory.NEW,
                title = "Smart Content Detection Overhaul",
                description = "Overhauled parser with smart year matching, symbol cleanup, multi-format syntax (12x05, 15x3), and international title diacritics normalization."
            ),
            WhatsNewItem(
                category = ChangeCategory.NEW,
                title = "Multi-Select in Unsupported Media",
                description = "Select multiple unrecognized entries to bulk match, re-categorize, or manage in a single batch operation."
            ),
            WhatsNewItem(
                category = ChangeCategory.NEW,
                title = "Hold to Hide / Unhide Content",
                description = "Long press any content card in your library to quickly toggle hidden status."
            ),
            WhatsNewItem(
                category = ChangeCategory.NEW,
                title = "Real-Time Dead Entries Progress Bar",
                description = "Added live progression tracking (checked/total, percentage) and a visual progress bar during dead entries scan."
            ),
            WhatsNewItem(
                category = ChangeCategory.IMPROVEMENT,
                title = "Optimized for Low-End & Budget Devices",
                description = "Reduced memory footprint and CPU overhead for fluid navigation and faster catalog loading on budget hardware."
            ),
            WhatsNewItem(
                category = ChangeCategory.IMPROVEMENT,
                title = "Streaming Playback Pipeline Enhancements",
                description = "Smoother byte-range chunk streaming pipeline minimizing buffering and frame drops."
            ),
            WhatsNewItem(
                category = ChangeCategory.IMPROVEMENT,
                title = "Early Beta Access Guidance",
                description = "Updated settings notice with developer recommendations for everyday streaming."
            ),
            WhatsNewItem(
                category = ChangeCategory.FIX,
                title = "Fix Metadata Candidate Preservation",
                description = "Manual candidate selections now strictly query by exact ID, preventing fuzzy search overrides."
            ),
            WhatsNewItem(
                category = ChangeCategory.FIX,
                title = "Eliminated Phantom Duplicate Cards",
                description = "Pruned duplicate cards across mirrored channel streams and duplicate uploads."
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
