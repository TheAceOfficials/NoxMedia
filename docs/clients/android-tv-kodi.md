# 📺 Android TV & Kodi Guide

NoxMedia can serve media to any Android TV, Google TV, NVIDIA Shield, Fire TV stick, or Kodi setup across your local network.

---

## 📱 Standalone vs. Host Mode

You can run NoxMedia in two different configurations:

1. **Host on Phone / Server, Play on TV (Recommended)**:
   - Run NoxMedia on an always-on Android phone, tablet, or mini-PC.
   - Stream to your Android TV using **Stremio**, **Kodi**, or **Nova Video Player**.
   - Preserves TV storage and keeps background network activity off the TV processor.

2. **Run Directly on Android TV**:
   - Sideload NoxMedia APK directly onto your Android TV or NVIDIA Shield.
   - NoxMedia will run as a local background service on `127.0.0.1:8096`.
   - Any player installed on the TV can connect locally via loopback.

---

## 🦎 Kodi Setup (via Jellyfin Addon)

Kodi provides great codec versatility for vintage or high-bitrate remux content.

### Method 1: Official Jellyfin Addon for Kodi
1. In Kodi, install the **Jellyfin for Kodi** addon from the official Jellyfin repository.
2. When prompted for server address, enter:
   ```
   http://192.168.1.15:8096
   ```
   *(replace with your NoxMedia host IP)*
3. Choose **Native (Direct Paths)** or **Add-on (HTTP Stream)** mode. Add-on mode is recommended for Telegram streams.
4. Enter any username (e.g. `NoxMedia`) and leave password empty.
5. Kodi will sync your libraries and present posters, episode listings, and custom shelves.

---

## 🚀 Recommended Android TV Players

- **Stremio for Android TV**: Sleek modern UI, poster grids, automatic subtitle fetchers, and remote-friendly navigation.
- **Nova Video Player**: Open-source, supports SMB/UPnP and Jellyfin, automatic movie scrapers, and hardware-accelerated playback on low-end chipsets.
- **Just Player**: Minimalist Android media player based on ExoPlayer with HDR10+ and Dolby Vision support.
