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
                title = "Smart Content Detection System Overhaul",
                description = "Comprehensive media parser upgrade: smart year detection (e.g. distinguishing Sherlock Holmes 2009 vs other versions), full support for 12x05 & 15x3 formats, intelligent symbol/punctuation filtering (';', ''', '-', '•'), international title diacritics normalization ('Shōgun' → 'Shogun', 'Pokémon' → 'Pokemon') for 100% exact API matching, and duplicate stream card elimination."
            ),
            WhatsNewItem(
                category = ChangeCategory.NEW,
                title = "Multi-Select in Unsupported Media",
                description = "Select multiple unrecognized media entries to bulk match, re-categorize, or manage in a single batch operation."
            ),
            WhatsNewItem(
                category = ChangeCategory.NEW,
                title = "Hold to Hide / Unhide Content",
                description = "Long press any content card in your library to quickly toggle hidden status without digging into nested menus."
            ),
            WhatsNewItem(
                category = ChangeCategory.NEW,
                title = "Real-Time Dead Entries Progress Bar",
                description = "Dead Entries cleanup now features live item progression tracking (e.g. 450/5638, 8%) alongside a smooth visual progress bar in Settings."
            ),
            WhatsNewItem(
                category = ChangeCategory.IMPROVEMENT,
                title = "Optimized for Low-End & Budget Devices",
                description = "Deep memory footprint optimizations and CPU efficiency improvements ensuring fluid navigation, faster catalog load times, and responsive UI on budget hardware."
            ),
            WhatsNewItem(
                category = ChangeCategory.IMPROVEMENT,
                title = "Streaming Playback Pipeline Enhancements",
                description = "Smoother byte-range chunk streaming on lower-spec hardware, minimizing buffering and frame drops during high-bitrate video playback."
            ),
            WhatsNewItem(
                category = ChangeCategory.IMPROVEMENT,
                title = "Early Beta Access Guidance & Settings Notice",
                description = "Removed beta badge clutter and added clear developer guidance recommending stable builds for everyday streaming."
            ),
            WhatsNewItem(
                category = ChangeCategory.FIX,
                title = "Fix Metadata Candidate Preservation",
                description = "Candidate selection in Fix Metadata Match now strictly queries TMDB and TVDB by exact IMDb ID, preventing selected candidates from being overridden by fuzzy title search mismatches."
            ),
            WhatsNewItem(
                category = ChangeCategory.FIX,
                title = "Eliminated Phantom Duplicate Cards",
                description = "Enhanced exact stream deduplication so video files from multiple channel mirrors or re-posts are cleanly unified without duplicate cards appearing in Library."
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
