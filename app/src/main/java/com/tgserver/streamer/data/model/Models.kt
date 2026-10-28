package com.tgserver.streamer.data.model

data class ChannelInfo(
    val id: Long,
    val title: String,
    val username: String? = null,
    val messageCount: Int = 0,
    val videoCount: Int = 0,
    val isActive: Boolean = true,
    val autoIndex: Boolean = true,
    val lastSyncTime: Long = 0L,
    val isAnime: Boolean = false,
    val lastScannedMsgId: Int = 0,
    val avatarUrl: String? = null
) {
    val scanStatusText: String
        get() {
            if (lastSyncTime <= 0L || videoCount == 0) {
                return "never scanned"
            }
            val elapsedSec = (System.currentTimeMillis() - lastSyncTime) / 1000
            val timeAgo = when {
                elapsedSec < 60 -> "just now"
                elapsedSec < 3600 -> "${elapsedSec / 60}m ago"
                elapsedSec < 86400 -> "${elapsedSec / 3600}h ago"
                else -> "${elapsedSec / 86400}d ago"
            }
            return "$videoCount videos • scanned $timeAgo"
        }
}

data class SplitPart(
    val messageId: Int,
    val partNumber: Int,
    val sizeBytes: Long,
    val cumStart: Long
)

data class MediaItem(
    val id: String, // e.g. "tg_channelId_messageId"
    val channelId: Long,
    val messageId: Int,
    var title: String,
    var type: String = "movie", // "movie" or "series"
    var year: String? = null,
    val season: Int? = null,
    val episode: Int? = null,
    var episodeTitle: String? = null,
    val quality: String = "1080p", // "4K", "1080p", "720p", "HDR"
    val qualityBadge: String = quality,
    val sizeBytes: Long = 0L,
    var durationSeconds: Int = 0,
    val mimeType: String = "video/mp4",
    val fileName: String = "",
    var posterUrl: String? = null,
    var backdropUrl: String? = null,
    var overview: String? = null,
    var imdbId: String? = null,
    var tmdbId: Int? = null,
    var tvdbId: Int? = null,
    var kitsuId: Int? = null,
    var episodeThumbnailUrl: String? = null,
    val isSplit: Boolean = false,
    val parts: List<SplitPart> = emptyList(),
    var isHidden: Boolean = false,
    var dateAdded: Long = 0L,
    var rating: Double = 0.0,
    var isManuallyMatched: Boolean = false,
    var lastModified: Long = 0L
) {
    val formattedSize: String
        get() {
            if (sizeBytes <= 0) return "0 MB"
            val gb = sizeBytes.toDouble() / (1024 * 1024 * 1024)
            return if (gb >= 1.0) {
                String.format("%.2f GB", gb)
            } else {
                val mb = sizeBytes.toDouble() / (1024 * 1024)
                String.format("%.1f MB", mb)
            }
        }

    val formattedDuration: String
        get() {
            if (durationSeconds <= 0) return "--"
            val h = durationSeconds / 3600
            val m = (durationSeconds % 3600) / 60
            return if (h > 0) "${h}h ${m}m" else "${m}m"
        }
}

fun formatShortBadge(badge: String): String {
    if (badge.isBlank()) return badge
    return badge
        .replace("English", "Eng", ignoreCase = true)
        .replace("Hindi", "Hin", ignoreCase = true)
        .replace("Kannada", "Kan", ignoreCase = true)
        .replace("Malayalam", "Mal", ignoreCase = true)
        .replace("Tamil", "Tam", ignoreCase = true)
        .replace("Telugu", "Tel", ignoreCase = true)
        .replace("Japanese", "Jap", ignoreCase = true)
        .replace("Korean", "Kor", ignoreCase = true)
        .replace("Spanish", "Spa", ignoreCase = true)
        .replace("French", "Fre", ignoreCase = true)
        .replace("German", "Ger", ignoreCase = true)
        .replace("Italian", "Ita", ignoreCase = true)
        .replace("Russian", "Rus", ignoreCase = true)
        .replace("Chinese", "Chi", ignoreCase = true)
        .replace("Multi Audio", "Multi", ignoreCase = true)
        .replace("Dual Audio", "Dual", ignoreCase = true)
}

/**
 * Grouped media entity representing a single Movie or TV Show title
 */
data class MediaGroup(
    val key: String,
    val title: String,
    val type: String, // "movie", "series", "anime"
    val year: String?,
    val posterUrl: String?,
    val backdropUrl: String?,
    val overview: String?,
    val imdbId: String?,
    val tmdbId: Int?,
    val tvdbId: Int? = null,
    val kitsuId: Int? = null,
    val qualityBadge: String,
    val items: List<MediaItem>,
    val dateAdded: Long = 0L,
    val rating: Double = 0.0
) {
    val distinctEpisodeCount: Int
        get() = items.map { Pair(it.season ?: 1, it.episode ?: 1) }.distinct().size

    val formattedTotalSize: String
        get() {
            val totalBytes = items.sumOf { it.sizeBytes }
            if (totalBytes <= 0L) return "0 MB"
            val tb = totalBytes.toDouble() / (1024.0 * 1024.0 * 1024.0 * 1024.0)
            if (tb >= 1.0) {
                return String.format(java.util.Locale.US, "%.1f TB", tb)
            }
            val gb = totalBytes.toDouble() / (1024.0 * 1024.0 * 1024.0)
            if (gb >= 1.0) {
                return if (gb >= 100.0) String.format(java.util.Locale.US, "%.0f GB", gb)
                else String.format(java.util.Locale.US, "%.1f GB", gb)
            }
            val mb = totalBytes.toDouble() / (1024.0 * 1024.0)
            return String.format(java.util.Locale.US, "%.0f MB", mb)
        }

    val isHidden: Boolean
        get() = items.isNotEmpty() && items.all { it.isHidden }

    // Pre-computed filter tags so opening filters and applying filters is instant (0ms freeze)
    val has4K: Boolean = items.any {
        val q = it.quality.lowercase()
        val fnClean = it.fileName.lowercase().replace(Regex("""(?i)4khdhub(\.com|\.org|\.net|\.in)?"""), "")
        q.contains("2160") || Regex("""(?i)\b(4k|uhd)\b""").containsMatchIn(q) ||
        fnClean.contains("2160p") || Regex("""(?i)\b(4k|uhd)\b""").containsMatchIn(fnClean)
    }
    val has1080p: Boolean = items.any { it.quality.contains("1080", true) || it.quality.contains("FHD", true) || it.fileName.contains("1080p", true) }
    val has720p: Boolean = items.any { it.quality.contains("720", true) || it.quality.contains("HD", true) || it.fileName.contains("720p", true) }
    val hasSD: Boolean = items.any { it.quality.contains("480", true) || it.fileName.contains("480p", true) }

    val hasIMAX: Boolean = items.any { it.fileName.contains("IMAX", true) }
    val hasBluRay: Boolean = items.any { it.fileName.contains("bluray", true) || it.fileName.contains("bdrip", true) || it.fileName.contains("brrip", true) }
    val hasWebDl: Boolean = items.any { it.fileName.contains("web-dl", true) || it.fileName.contains("webdl", true) || it.fileName.contains("webrip", true) || it.fileName.contains("web-rip", true) }
    val hasHdrDv: Boolean = items.any { it.fileName.contains("hdr", true) || it.fileName.contains("dovi", true) || it.fileName.contains("dv", true) || it.fileName.contains("dolby vision", true) }
    val has10Bit: Boolean = items.any { it.fileName.contains("10bit", true) || it.fileName.contains("10-bit", true) || it.fileName.contains("hevc", true) || it.fileName.contains("x265", true) }
    val hasRemux: Boolean = items.any { it.fileName.contains("remux", true) }

    val hasNetflix: Boolean = items.any { it.fileName.contains("netflix", true) || it.fileName.contains(".nf.", true) || it.fileName.contains("-nf", true) }
    val hasCrunchyroll: Boolean = items.any { it.fileName.contains("crunchyroll", true) || it.fileName.contains(".cr.", true) }
    val hasPrimeVideo: Boolean = items.any { it.fileName.contains("amazon", true) || it.fileName.contains("prime", true) || it.fileName.contains(".amzn.", true) }
    val hasDisney: Boolean = items.any { it.fileName.contains("disney", true) || it.fileName.contains("hotstar", true) || it.fileName.contains(".dsnp.", true) }
    val hasAppleTv: Boolean = items.any { it.fileName.contains("apple", true) || it.fileName.contains(".atvp.", true) }
    val hasZee5: Boolean = items.any { it.fileName.contains("zee5", true) }

    val hasHindi: Boolean = items.any { it.fileName.contains("hindi", true) || it.fileName.contains(".hin.", true) }
    val hasEnglish: Boolean = items.any { it.fileName.contains("english", true) || it.fileName.contains(".eng.", true) }
    val hasJapanese: Boolean = items.any { it.fileName.contains("japanese", true) || it.fileName.contains(".jap.", true) || it.fileName.contains(".jpn.", true) }
    val hasDualAudio: Boolean = items.any { it.fileName.contains("dual audio", true) || it.fileName.contains("dual", true) }
    val hasMultiAudio: Boolean = items.any { it.fileName.contains("multi audio", true) || it.fileName.contains("multi", true) }
}

enum class LibrarySortOrder(val label: String) {
    RECENT("Recently Added"),
    NAME_ASC("Name (A → Z)"),
    NAME_DESC("Name (Z → A)"),
    YEAR_DESC("Year (Newest)"),
    YEAR_ASC("Year (Oldest)"),
    SIZE_DESC("Size (Largest)"),
    SIZE_ASC("Size (Smallest)"),
    RATING_DESC("Rating (Highest)")
}

// Precompiled regexes to eliminate regex compilation overhead in large media lists (10k-100k items)
private val SPLIT_PART_CLEAN_REGEX = Regex("""[\s._-]*(?:part|pt)[\s._-]*\d+.*""", RegexOption.IGNORE_CASE)
private val SPLIT_EXT_CLEAN_REGEX = Regex("""\.(mkv|mp4|avi|webm|ts|m4v)(\.\d{2,4})?$""", RegexOption.IGNORE_CASE)
private val SPLIT_PART_DETECT_REGEX = Regex("""(?i)(?:part|pt|\.\d{2,4})""")

fun List<MediaItem>.toMediaGroups(): List<MediaGroup> {
    val allMedia = this
    val splitItems = allMedia.filter { it.isSplit }
    val effectiveMedia = if (splitItems.isEmpty()) {
        allMedia
    } else {
        val splitPartMsgIds = splitItems.flatMap { it.parts.map { p -> p.messageId } }.toSet()
        val splitCleanBases = splitItems.map {
            it.fileName.lowercase()
                .replace(SPLIT_PART_CLEAN_REGEX, "")
                .replace(SPLIT_EXT_CLEAN_REGEX, "")
                .trim()
        }.toSet()

        allMedia.filter { item ->
            if (item.isSplit) return@filter true
            if (splitPartMsgIds.contains(item.messageId)) return@filter false
            val itemClean = item.fileName.lowercase()
                .replace(SPLIT_PART_CLEAN_REGEX, "")
                .replace(SPLIT_EXT_CLEAN_REGEX, "")
                .trim()
            if (splitCleanBases.contains(itemClean)) {
                if (item.fileName.contains(SPLIT_PART_DETECT_REGEX)) {
                    return@filter false
                }
                val splitMatch = splitItems.find {
                    (it.channelId == item.channelId || it.channelId == 0L || item.channelId == 0L) &&
                    it.season == item.season && it.episode == item.episode
                }
                if (splitMatch != null && item.sizeBytes < splitMatch.sizeBytes) {
                    return@filter false
                }
            }
            true
        }
    }

    val skipExact = com.tgserver.streamer.service.StreamerForegroundService.activeConfig.skipExactDuplicates
    val finalMedia = if (skipExact) {
        val seenSizeKeys = mutableSetOf<String>()
        val seenNameKeys = mutableSetOf<String>()
        val sorted = effectiveMedia.sortedWith(
            compareByDescending<MediaItem> { it.isSplit }
                .thenBy { item ->
                    val fn = item.fileName.lowercase()
                    if (fn.contains("momstealer") || fn.contains("vegamovies") || fn.contains("katmovie") || fn.contains("4khdhub")) 1 else 0
                }
                .thenByDescending { it.sizeBytes }
        )
        sorted.filter { item ->
            val isSeries = item.type == "series" || item.type == "anime" || (item.season != null && item.episode != null)
            val s = item.season ?: 0
            val e = item.episode ?: 0
            val cleanTitle = item.title.trim().lowercase()
            val mediaId = item.imdbId ?: item.tmdbId?.toString() ?: cleanTitle

            val sizeKey = if (item.sizeBytes > 0L) {
                if (isSeries) "${mediaId}_s${s}_e${e}_${item.sizeBytes}" else "${mediaId}_${item.sizeBytes}"
            } else null

            val cleanFn = item.fileName.lowercase()
                .replace(Regex("""\.(mkv|mp4|avi|webm|ts|m4v)(\.\d{2,4})?$""", RegexOption.IGNORE_CASE), "")
                .replace(Regex("""[\(\[][^\)\]]*(?:momstealer|anipulse|toonworld|vegamovies|katmovie|4khdhub)[^\)\]]*[\)\]]""", RegexOption.IGNORE_CASE), "")
                .replace(Regex("""[\s._-]+"""), " ")
                .trim()
            val nameKey = if (isSeries) "${mediaId}_s${s}_e${e}_$cleanFn" else "${mediaId}_$cleanFn"

            val isDup = (sizeKey != null && seenSizeKeys.contains(sizeKey)) || seenNameKeys.contains(nameKey)
            if (!isDup) {
                if (sizeKey != null) seenSizeKeys.add(sizeKey)
                seenNameKeys.add(nameKey)
                true
            } else {
                false
            }
        }
    } else effectiveMedia

    // Propagate verified IDs within the same series title so all episodes group together
    val seriesIdMap = mutableMapOf<String, Triple<Int?, String?, Int?>>()
    for (item in finalMedia) {
        if (item.type != "movie") {
            val ct = item.title.lowercase().replace(Regex("[:\\-_.\\[\\]\\(\\)]"), " ").replace(Regex("\\s+"), " ").trim()
            if (ct.isNotBlank()) {
                val existing = seriesIdMap[ct]
                val tmdb = existing?.first ?: item.tmdbId?.takeIf { it > 0 }
                val imdb = existing?.second ?: item.imdbId?.takeIf { it.isNotBlank() }
                val tvdb = existing?.third ?: item.tvdbId?.takeIf { it > 0 }
                if (tmdb != null || imdb != null || tvdb != null) {
                    seriesIdMap[ct] = Triple(tmdb, imdb, tvdb)
                }
            }
        }
    }

    return finalMedia.groupBy { item ->
        val cleanTitle = item.title.lowercase().replace(Regex("[:\\-_.\\[\\]\\(\\)]"), " ").replace(Regex("\\s+"), " ").trim()
        val cleanYear = Regex("(?:^|[^0-9])((?:19|20)\\d{2})(?:[^0-9]|$)").find(item.year ?: "")?.groupValues?.get(1)
        val inherited = if (item.type != "movie") seriesIdMap[cleanTitle] else null
        val tmdb = item.tmdbId?.takeIf { it > 0 } ?: inherited?.first
        val imdb = item.imdbId?.takeIf { it.isNotBlank() } ?: inherited?.second
        val tvdb = item.tvdbId?.takeIf { it > 0 } ?: inherited?.third

        when {
            tmdb != null && tmdb > 0 -> "tmdb_${item.type}_$tmdb"
            !imdb.isNullOrBlank() -> "imdb_$imdb"
            tvdb != null && tvdb > 0 -> "tvdb_${item.type}_$tvdb"
            item.kitsuId != null && item.kitsuId!! > 0 -> "kitsu_${item.type}_${item.kitsuId}"
            item.type == "movie" && !cleanYear.isNullOrBlank() -> "title_movie_${cleanTitle}_$cleanYear"
            else -> "title_${item.type}_$cleanTitle"
        }
    }.map { (key, items) ->
        val primary = items.first()
        val bestTitle = items.firstOrNull { it.isManuallyMatched && it.title.isNotBlank() }?.title
            ?: items.firstOrNull { !it.title.isNullOrBlank() }?.title
            ?: primary.title

        val bestPoster = items.firstOrNull { !it.posterUrl.isNullOrBlank() }?.posterUrl ?: primary.posterUrl
        val bestBackdrop = items.firstOrNull { !it.backdropUrl.isNullOrBlank() }?.backdropUrl ?: primary.backdropUrl
        val bestOverview = items.firstOrNull { !it.overview.isNullOrBlank() }?.overview ?: primary.overview
        val bestYear = items.firstOrNull { !it.year.isNullOrBlank() }?.year ?: primary.year
        val bestImdb = items.firstOrNull { !it.imdbId.isNullOrBlank() }?.imdbId ?: primary.imdbId
        val bestTmdb = items.firstOrNull { it.tmdbId != null && it.tmdbId!! > 0 }?.tmdbId ?: primary.tmdbId
        val bestTvdb = items.firstOrNull { it.tvdbId != null && it.tvdbId!! > 0 }?.tvdbId ?: primary.tvdbId
        val bestKitsu = items.firstOrNull { it.kitsuId != null && it.kitsuId!! > 0 }?.kitsuId ?: primary.kitsuId
        val bestQuality = items.maxByOrNull {
            when {
                it.quality.contains("4K", ignoreCase = true) || it.quality.contains("2160", ignoreCase = true) -> 4
                it.quality.contains("1080", ignoreCase = true) -> 3
                it.quality.contains("720", ignoreCase = true) -> 2
                else -> 1
            }
        }?.qualityBadge ?: primary.qualityBadge
        val maxDateAdded = items.maxOfOrNull { it.dateAdded } ?: 0L
        val maxRating = items.map { it.rating }.maxOrNull() ?: 0.0

        MediaGroup(
            key = key,
            title = bestTitle,
            type = primary.type,
            year = bestYear,
            posterUrl = bestPoster,
            backdropUrl = bestBackdrop,
            overview = bestOverview,
            imdbId = bestImdb,
            tmdbId = bestTmdb,
            tvdbId = bestTvdb,
            kitsuId = bestKitsu,
            qualityBadge = formatShortBadge(bestQuality),
            items = items.sortedWith(compareBy({ it.season ?: 1 }, { it.episode ?: 1 })),
            dateAdded = maxDateAdded,
            rating = maxRating
        )
    }
}

data class UnmatchedItem(
    val id: String, // e.g. "unmatched_${channelId}_${messageId}"
    val channelId: Long,
    val channelTitle: String = "",
    val messageId: Int,
    val fileName: String,
    val rawFileName: String = fileName,
    val sizeBytes: Long = 0L,
    val scannedAt: Long = System.currentTimeMillis(),
    val reason: String = "No match found on TMDB / TVDB / Kitsu",
    val mimeType: String = "video/mp4",
    val isVideo: Boolean = true,
    val parsedMediaItem: MediaItem? = null
) {
    val formattedSize: String
        get() {
            if (sizeBytes <= 0) return "0 MB"
            val gb = sizeBytes.toDouble() / (1024 * 1024 * 1024)
            return if (gb >= 1.0) {
                String.format("%.2f GB", gb)
            } else {
                val mb = sizeBytes.toDouble() / (1024 * 1024)
                String.format("%.1f MB", mb)
            }
        }

    val formattedDate: String
        get() {
            val elapsedSec = (System.currentTimeMillis() - scannedAt) / 1000
            return when {
                elapsedSec < 60 -> "just now"
                elapsedSec < 3600 -> "${elapsedSec / 60}m ago"
                elapsedSec < 86400 -> "${elapsedSec / 3600}h ago"
                else -> {
                    val sdf = java.text.SimpleDateFormat("dd MMM yyyy, HH:mm", java.util.Locale.getDefault())
                    sdf.format(java.util.Date(scannedAt))
                }
            }
        }
}

data class ScanProgressState(
    val channelId: Long,
    val channelTitle: String = "",
    val scannedMessages: Int = 0,
    val indexedMedia: Int = 0,
    val totalBytes: Long = 0L,
    val currentBatch: Int = 0,
    val isScanning: Boolean = false,
    val statusMessage: String = "",
    val isCooldown: Boolean = false,
    val cooldownSeconds: Int = 0
) {
    val formattedSize: String
        get() {
            if (totalBytes <= 0) return "0 MB"
            val gb = totalBytes.toDouble() / (1024 * 1024 * 1024)
            return if (gb >= 1.0) {
                String.format("%.2f GB", gb)
            } else {
                val mb = totalBytes.toDouble() / (1024 * 1024)
                String.format("%.1f MB", mb)
            }
        }
}

data class ServerConfig(
    var port: Int = 7788,
    var isRunning: Boolean = true,
    var customPosterUrl: String = "",
    var customPosterApiKey: String = "",
    var telegramApiId: Int = 0,
    var telegramApiHash: String = "",
    var bufferChunkSizeKb: Int = 512,
    var diskCacheMb: Int = 2048,
    var bufferPipelineMb: Int = 20,
    var cacheDir: String = "",
    var tmdbApiKey: String = "",
    var tvdbApiKey: String = "",
    var preferredQuality: String = "Any",
    var hideCamTs: Boolean = false,
    var remotePublicUrl: String = "",
    var startOnBoot: Boolean = false,
    var autoScanNewContent: Boolean = true,
    var replaceSameQuality: Boolean = false,
    var skipExactDuplicates: Boolean = true,
    var autoBackupEnabled: Boolean = false,
    var autoBackupIntervalHours: Int = 12,
    var autoSleepEnabled: Boolean = false,
    var autoSleepTimeoutMinutes: Int = 30,
    var alexaVoiceControlEnabled: Boolean = false,
    var alexaDeviceName: String = "NoxMedia",
    var receiveBetaUpdates: Boolean = false,
    var animeMetadataPreference: String = "seasonal",
    var backupDestination: String = "",
    var cloudSyncLibrary: Boolean = true
)

data class AuthSession(
    var isLoggedIn: Boolean = false,
    var phoneNumber: String = "",
    var firstName: String = "",
    var username: String = "",
    var userId: Long = 0L,
    var sessionString: String = "",
    var phoneCodeHash: String = "",
    var avatarUrl: String? = null,
    var lastError: String? = null
) {
    val initials: String
        get() {
            if (firstName.isNotBlank()) {
                val parts = firstName.trim().split(" ")
                return if (parts.size >= 2) {
                    "${parts[0].first()}${parts[1].first()}".uppercase()
                } else {
                    parts[0].take(2).uppercase()
                }
            }
            if (username.isNotBlank()) {
                return username.take(2).uppercase()
            }
            return "TG"
        }
}

data class ServerTelemetry(
    val isRunning: Boolean,
    val port: Int,
    val localIp: String,
    val manifestUrl: String,
    val uptimeSeconds: Long,
    val memoryUsedMb: Long,
    val memoryTotalMb: Long,
    val cpuPercent: Double,
    val totalChannels: Int,
    val totalMedia: Int,
    val activeStreams: Int
)
