# Roadmap & Future Directions

This document outlines the development trajectory of NoxMedia, including completed milestones, active engineering priorities, and upcoming platform expansions.

---

<section class="doc-section" id="platform-availability">

## Platform Availability Overview

| Platform / Client | Target Ecosystem | Status |
| :--- | :--- | :--- |
| **Infuse (Apple)** | <DeviceGroup devices="iphone, ipad, mac, appletv, visionpro" /> | Available Now |
| **Vesper (Apple)** | <DeviceGroup devices="iphone, ipad, mac, appletv, visionpro" /> | Available Now |
| **Nuvio Streaming** | <DeviceGroup devices="android-phone, android-tv, windows, linux, mac, smart-tv, iphone, appletv" /> | Available Now |
| **Native Android TV & Google TV** | <DeviceGroup devices="android-tv" /> | In Development (Coming Soon) |
| **Windows Desktop Native** | <DeviceGroup devices="windows" /> | Coming Soon |
| **Linux Client & Server** | <DeviceGroup devices="linux" /> | Coming Soon |
| **Stremio Direct Playback Bridge** | <DeviceGroup devices="android-phone, android-tv, windows, mac, linux" /> | In Progress |

</section>

---

<section class="doc-section" id="active-priorities">

## Active Development & Priorities

### Stremio Direct Playback Bridge
- **Status**: In Progress
- **Focus**: Stremio currently encounters limitations with local HTTP loopback stream endpoints on certain Android configurations. We are refining the internal streaming bridge and CORS dispatch to restore seamless direct playback on Stremio clients.
- **Current Recommendation**: Use **Nuvio** for Stremio-based library browsing and direct streaming.

### Native Android TV & Google TV Client
- **Status**: In Development
- **Focus**: A dedicated Android TV application optimized for 10-foot D-pad navigation, featuring native playback via ExoPlayer, hardware-accelerated 4K HDR10/Dolby Vision passthrough, and home screen recommendation channels.

### Multi-User Support & Profile Isolation
- **Status**: Planned
- **Focus**: Distinct viewing profiles, separate watch histories, individual resume points, and isolated Telegram channel permissions across family members.

</section>

---

<section class="doc-section" id="completed-milestones">

## Completed Milestones

- **v1.5.0**: Added Infuse & Vesper Support (Media Server via Jellyfin), performance boost, and minor bug fixes.
- **v1.4.325**: Dynamic Infuse & Vesper Custom Catalogs via Jellyfin Bridge with zero-latency in-memory caching.
- **v1.4.320**: Semantic AI catalog curation using Google Gemini and OpenRouter LLM engines.
- **v1.4.318**: Fallback runtime precision, anime multi-title sanitization, and 24/7 foreground standby reliability.
- **v1.4.300**: Chaquopy Python MTProto bridge integration with zero-disk seeking and cloud backup synchronization.

</section>

