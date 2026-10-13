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
    val isAnime: Boolean = false
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
    val type: String = "movie", // "movie" or "series"
    var year: String? = null,
    val season: Int? = null,
    val episode: Int? = null,
    var episodeTitle: String? = null,
    val quality: String = "1080p", // "4K", "1080p", "720p", "HDR"
    val qualityBadge: String = quality,
    val sizeBytes: Long = 0L,
    val durationSeconds: Int = 0,
    val mimeType: String = "video/mp4",
    val fileName: String = "",
    var posterUrl: String? = null,
    var backdropUrl: String? = null,
    var overview: String? = null,
    var imdbId: String? = null,
    var tmdbId: Int? = null,
    var kitsuId: Int? = null,
    val isSplit: Boolean = false,
    val parts: List<SplitPart> = emptyList(),
    var isHidden: Boolean = false
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
    var autoBackupIntervalHours: Int = 12
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
