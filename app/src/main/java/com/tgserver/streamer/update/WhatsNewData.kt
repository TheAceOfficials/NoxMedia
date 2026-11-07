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
        version = "1.3.255-beta",
        releaseTitle = "MPV Playback Stability & 32-Bit Low-Memory Performance",
        releaseDate = "November 2026",
        items = listOf(
            WhatsNewItem(
                category = ChangeCategory.FIX,
                title = "Resolved MPV Playback Loading Error",
                description = "Fixed an issue where concurrent demuxer probe streams during player startup were prematurely terminated, resolving '[mpv] loading failed' on iOS/iPadOS."
            ),
            WhatsNewItem(
                category = ChangeCategory.IMPROVEMENT,
                title = "Dynamic 32-Bit & Budget Device Optimization",
                description = "Automatically scales down RAM buffer footprints from 112MB to 28MB on 32-bit and low-RAM devices, completely eliminating ART garbage collection freezes."
            ),
            WhatsNewItem(
                category = ChangeCategory.IMPROVEMENT,
                title = "Enhanced Stream Read Tolerance",
                description = "Increased buffer underrun tolerance to 15 seconds, preventing premature EOF disconnects during slower chunk downloads."
            ),
            WhatsNewItem(
                category = ChangeCategory.IMPROVEMENT,
                title = "Ultra-Fast Timeline Seeking",
                description = "Instant 17ms-200ms seek fulfillment across long media timelines with persistent MTProto sessions."
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
