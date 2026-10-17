package com.tgserver.streamer.server

/**
 * Identifies the client platform making HTTP requests (e.g. streaming, stream descriptor)
 * to allow isolated, client-specific stream handling without cross-platform regressions.
 */
enum class ClientPlatform {
    ANDROID,
    IOS,
    WEB,
    DESKTOP,
    OTHER;

    val isAndroid: Boolean get() = this == ANDROID
    val isIos: Boolean get() = this == IOS
}

object ClientPlatformDetector {
    /**
     * Determines the client platform from HTTP User-Agent and headers.
     */
    fun detect(userAgent: String?): ClientPlatform {
        if (userAgent.isNullOrBlank()) return ClientPlatform.OTHER
        val ua = userAgent.lowercase()

        // 1. iOS / Apple Ecosystem Indicators
        // AppleCoreMedia is the AVFoundation / AVPlayer user-agent
        // CFNetwork / Darwin is the iOS system networking framework
        // KSPlayer, MobileVLCKit, Outplayer, Infuse are iOS media players
        // Nuvio-iOS, Stremio iOS app identifiers
        if (ua.contains("iphone") ||
            ua.contains("ipad") ||
            ua.contains("ipod") ||
            ua.contains("applecoremedia") ||
            ua.contains("cfnetwork") ||
            ua.contains("darwin") ||
            ua.contains("ksplayer") ||
            ua.contains("mobilevlckit") ||
            ua.contains("outplayer") ||
            ua.contains("infuse") ||
            ua.contains("libmpv") ||
            ua.contains("mpv") ||
            ua.contains("lavf") ||
            ua.contains("vlc") ||
            ua.contains("nuvio-ios") ||
            ua.contains("nuvio/ios") ||
            ua.contains("stremio-ios") ||
            ua.contains("stremio/ios") ||
            (ua.contains("ios") && !ua.contains("bios"))) {
            return ClientPlatform.IOS
        }

        // 2. Android Indicators
        // ExoPlayer is Android's default media engine
        // Dalvik is Android's VM
        // Android / AndroidTV identifiers
        if (ua.contains("android") ||
            ua.contains("exoplayer") ||
            ua.contains("dalvik") ||
            ua.contains("nuvio-android") ||
            ua.contains("nuvio/android") ||
            ua.contains("stremio-android") ||
            ua.contains("stremio/android")) {
            return ClientPlatform.ANDROID
        }

        // 3. Web browsers (desktop or mobile browser)
        if (ua.contains("mozilla") || ua.contains("chrome") || ua.contains("safari") || ua.contains("edge")) {
            return ClientPlatform.WEB
        }

        return ClientPlatform.OTHER
    }
}
