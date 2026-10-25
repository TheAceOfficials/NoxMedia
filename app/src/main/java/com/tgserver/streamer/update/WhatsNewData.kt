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
        version = "1.2",
        releaseTitle = "True Cloud Sync, Smart Library & Modern UI",
        releaseDate = "October 2026",
        items = listOf(
            WhatsNewItem(
                category = ChangeCategory.NEW,
                title = "True Cloud Sync & Multi-Device Backup",
                description = "Bi-directional true sync across all your phones, Android TVs, and tablets. Full library snapshots, title merges, and hidden states are compressed with Gzip (~90% smaller) and pushed automatically to Telegram without re-scanning."
            ),
            WhatsNewItem(
                category = ChangeCategory.NEW,
                title = "Dynamic Library Tabs & Multi-Select Filters",
                description = "Browse through smart categorical tabs (All, Movies, TV Shows, Anime, 4K UHD, Recent) with a stutter-free multi-tag filter bottom sheet for resolutions, sources, and sorting."
            ),
            WhatsNewItem(
                category = ChangeCategory.NEW,
                title = "Batch Selection & Manual Title Merge",
                description = "Long press any media card to enter selection mode: bulk hide, unhide, merge disparate episodes/parts under one title, or delete entries in a single tap."
            ),
            WhatsNewItem(
                category = ChangeCategory.NEW,
                title = "Alexa Voice Control & Quick Settings Tile",
                description = "Control streaming server on/off via Echo voice commands over local Wi-Fi, and toggle Alexa Standby mode directly from your phone's notification shade control center tile."
            ),
            WhatsNewItem(
                category = ChangeCategory.NEW,
                title = "Anime Indexing Preference (Seasonal vs Absolute)",
                description = "Select between Seasonal TVDB/TMDB (standard multi-season playback for Nuvio & Cinemeta) and Absolute Continuous (Kitsu 1..1000+) with balanced, equal-height OLED cards."
            ),
            WhatsNewItem(
                category = ChangeCategory.IMPROVEMENT,
                title = "Background Storage Cleanup (Unkillable Scans)",
                description = "Duplicate Media Scan and Dead Entries Scan now run on an independent background supervisor scope. Navigating away or locking your screen will no longer cancel active scans."
            ),
            WhatsNewItem(
                category = ChangeCategory.IMPROVEMENT,
                title = "Emil Kowalski Micro-Interactions & Physics",
                description = "Responsive spring scales (0.96x depression on press), organic dialog popups, Nothing OS typography, and refined tactile haptics across all buttons."
            ),
            WhatsNewItem(
                category = ChangeCategory.IMPROVEMENT,
                title = "Settings Screen Polish & Strict Beta Separation",
                description = "Redesigned sleek glowing 'BETA ACTIVE' pill tag, auto-fitting cloud action buttons without text cropping, sanitized changelog typography, and strict stable-only updates when Beta toggle is off."
            ),
            WhatsNewItem(
                category = ChangeCategory.FIX,
                title = "Hide/Unhide Cloud State Persistence",
                description = "Fixed hidden/unhidden status updates so timestamp changes immediately trigger background cloud backup and reflect accurately across all synced screens."
            ),
            WhatsNewItem(
                category = ChangeCategory.FIX,
                title = "Filter BottomSheet Zero-Lag Opening",
                description = "Pre-computed filter tags on media groups, completely eliminating UI stutter and freeze when opening the library filters sheet."
            ),
            WhatsNewItem(
                category = ChangeCategory.FIX,
                title = "Stremio & Nuvio Streaming Stability",
                description = "Streamlined HTTP streaming pipeline ensuring zero infinite loading loops and rock-solid playback across Stremio Mobile, Desktop, and Nuvio."
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
