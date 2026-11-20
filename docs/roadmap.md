# Roadmap & Future Directions

This document outlines the development trajectory of NoxMedia, including completed milestones, active engineering priorities, and upcoming platform expansions.

---

## Platform Availability Overview

| Platform / Client | Status | Connection Protocol | Target Ecosystem |
| :--- | :--- | :--- | :--- |
| **Infuse** | Available Now | Jellyfin Bridge (Direct Mode) | <DeviceGroup devices="iphone, ipad, mac, appletv, visionpro" /> |
| **Vesper** | Available Now | Jellyfin Bridge | <DeviceGroup devices="iphone, ipad, mac, appletv" /> |
| **Nuvio** | Available Now | Stremio v3 Addon Protocol | <DeviceGroup devices="iphone, ipad, mac, tv" /> |
| **Kodi / EmbyCon** | Available Now | Jellyfin / Emby Addon | <DeviceGroup devices="tv, mac" /> |
| **Android TV & Google TV Native App** | In Development | Native Android TV Interface | <DeviceGroup devices="tv" /> |
| **Windows Desktop Native Client** | Coming Soon | Embedded Core Runtime | <DeviceGroup devices="mac" /> |
| **Linux Native Client / Server** | Coming Soon | Headless Daemon & CLI | <DeviceGroup devices="mac" /> |
| **Stremio Integration Fix** | In Progress | Localhost HTTP Protocol Bridge | <DeviceGroup devices="iphone, ipad, mac, tv" /> |

---

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

---

## Completed Milestones

- **v1.4.325**: Dynamic Infuse & Vesper Custom Catalogs via Jellyfin Bridge with zero-latency in-memory caching.
- **v1.4.320**: Semantic AI catalog curation using Google Gemini and OpenRouter LLM engines.
- **v1.4.318**: Fallback runtime precision, anime multi-title sanitization, and 24/7 foreground standby reliability.
- **v1.4.300**: Chaquopy Python MTProto bridge integration with zero-disk seeking and cloud backup synchronization.

