# Android TV & Kodi Guide

NoxMedia can serve media to any Android TV, Google TV, NVIDIA Shield, Fire TV, or Kodi setup across your local network.

---

## Upcoming: Native Android TV App

> [!TIP]
> **Native Android TV & Google TV App Coming Soon**:
> We are actively developing a standalone Android TV application with a native 10-foot D-pad interface, ExoPlayer direct hardware acceleration, and integrated home screen recommendation rows. Check the [Roadmap](/roadmap) for development progress.

---

## Current Setup Options

You can stream to your television through the following methods:

### Method 1: Nuvio for Android TV
Install Nuvio on your Android TV or Google TV box, add the NoxMedia manifest URL (`http://<YOUR_IP>:7788/manifest.json`), and browse with remote control navigation.

### Method 2: Kodi via Jellyfin Addon
1. Open Kodi on your TV device.
2. Install the **Jellyfin for Kodi** addon from the official Kodi / Jellyfin repository.
3. Enter your server connection:
   ```
   http://192.168.1.15:7788
   ```
4. Choose **Add-on (HTTP Stream)** mode for live cloud streaming.
5. Enter `NoxMedia` as the username and leave the password blank.
6. Kodi will populate your TV interface with posters, episode groupings, and custom shelves.

---

## Recommended TV Players

- **Nuvio**: Clean, fast catalog explorer with direct stream launching.
- **Nova Video Player**: Open-source, supports Jellyfin/UPnP shares, and offers hardware acceleration on lower-end chipsets.
- **Just Player**: Minimalist ExoPlayer-based player with HDR10+ and Dolby Vision passthrough support.
