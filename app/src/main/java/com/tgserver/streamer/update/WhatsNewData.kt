package com.tgserver.streamer.update

/**
 * Categorization for changelog entries.
 * hexColor defines the accent tint for badges and bullet indicators.
 */
enum class ChangeCategory(
    val label: String,
    val emoji: String,
    val hexColor: Long
) {
    NEW("New Features", "🚀", 0xFF22C55E),       // Vibrant Green
    IMPROVEMENT("Improvements", "⚡", 0xFF38BDF8), // Cyan / Sky Blue
    FIX("Bug Fixes", "🐛", 0xFFF59E0B),           // Amber
    CHANGE("Changes", "🔄", 0xFFA855F7)           // Modern Purple
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
 * Whenever new features, improvements, or fixes are implemented,
 * simply update [currentRelease] with the corresponding items.
 * If a category has no items (e.g. no bug fixes or no new features in a commit),
 * it will automatically NOT be shown in the UI.
 */
object WhatsNewRepository {

    val currentRelease: WhatsNewRelease = WhatsNewRelease(
        version = "1.1",
        releaseTitle = "Cloud Sync & Premium Release",
        releaseDate = "October 2026",
        items = listOf(
            WhatsNewItem(
                category = ChangeCategory.NEW,
                title = "Apple TV+ Style Content Management Redesign",
                description = "Scratched-and-rebuilt Content Details screen with immersive hero backdrop, transparent ClearLogo artwork, 16:9 episode thumbnails, horizontal season selector, and expandable server file accordions."
            ),
            WhatsNewItem(
                category = ChangeCategory.NEW,
                title = "Telegram Cloud Backup & Scheduled Auto-Sync",
                description = "Automatically backs up connected channels, playback settings, and automation rules directly to your Telegram Saved Messages (#NoxMediaBackup) with scheduled intervals (1h – 24h)."
            ),
            WhatsNewItem(
                category = ChangeCategory.NEW,
                title = "Cross-Device Silent Auto-Restore",
                description = "Logging into NoxMedia on a new phone or tablet automatically fetches and restores your cloud backup seamlessly."
            ),
            WhatsNewItem(
                category = ChangeCategory.NEW,
                title = "Instant Cache-Wipe & Fresh Reset on Logout",
                description = "Logging out now completely terminates active streams, wipes the 48MB RAM chunk cache, clears channel IDs, and purges temporary disk and image caches."
            ),
            WhatsNewItem(
                category = ChangeCategory.NEW,
                title = "Alexa Voice Control Bridge",
                description = "Built-in Philips Hue emulator bridge allows Amazon Alexa to turn NoxMedia streaming ON or OFF via voice commands."
            ),
            WhatsNewItem(
                category = ChangeCategory.IMPROVEMENT,
                title = "Dynamic Stremio & Nuvio Manifest Sync",
                description = "Addon manifest.json is now dynamically synchronized with the installed app version and updated with the tagline 'NoxMedia - Designed for Your Media'."
            ),
            WhatsNewItem(
                category = ChangeCategory.IMPROVEMENT,
                title = "Dual-Tier Seek Cache (48MB RAM + Disk)",
                description = "Ultra-fast HTTP 206 byte-range scrubbing and timeline seeking for high-bitrate 4K HDR and 1080p media."
            ),
            WhatsNewItem(
                category = ChangeCategory.IMPROVEMENT,
                title = "Dynamic Multi-Part Split Video Assembly",
                description = "Seamlessly concatenates split video archives (part1, part2 / pt1, pt2) into a single playback stream on the fly."
            ),
            WhatsNewItem(
                category = ChangeCategory.IMPROVEMENT,
                title = "Native Tailscale Mesh Network Support",
                description = "Auto-detects Tailscale VPN interface to generate remote streaming URLs for secure out-of-home playback."
            ),
            WhatsNewItem(
                category = ChangeCategory.FIX,
                title = "Telegram-Stremio Repository Link",
                description = "Corrected upstream repository reference to https://github.com/weebzone/Telegram-Stremio in documentation."
            ),
            WhatsNewItem(
                category = ChangeCategory.FIX,
                title = "Channel List Merge & Deduplication Fix",
                description = "Resolved duplicate channel entries when importing or syncing backup configurations."
            ),
            WhatsNewItem(
                category = ChangeCategory.FIX,
                title = "Pyrogram MTProto Client Teardown",
                description = "Fixed potential background Python socket leaks when logging out or switching session credentials."
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
