<p align="center">
  <img src="image_assets/NoxMediaBanner.png" alt="NoxMedia Banner" width="100%">
</p>

<p align="center">
  <strong>Transform your Android device into a private, high-performance media server powered by Telegram's infinite cloud storage.</strong>
</p>

<p align="center">
  <a href="https://github.com/TheAceOfficials/NoxMedia/releases"><img src="https://img.shields.io/badge/Platform-Android%207.0%2B-3DDC84?style=flat-square&logo=android&logoColor=white" alt="Platform"></a>
  <a href="https://kotlinlang.org"><img src="https://img.shields.io/badge/Kotlin-2.3-7F52FF?style=flat-square&logo=kotlin&logoColor=white" alt="Kotlin"></a>
  <a href="https://chaquo.com/chaquopy/"><img src="https://img.shields.io/badge/Python-3.11%20(Chaquopy)-3776AB?style=flat-square&logo=python&logoColor=white" alt="Python"></a>
  <a href="https://stremio.com"><img src="https://img.shields.io/badge/Stremio%20%2F%20Nuvio-Addon%20v3-7A55C7?style=flat-square&logo=stremio&logoColor=white" alt="Stremio / Nuvio"></a>
  <a href="https://www.patreon.com/cw/TheAceOfficials"><img src="https://img.shields.io/badge/Patreon-Support-FF424D?style=flat-square&logo=patreon&logoColor=white" alt="Patreon"></a>
  <a href="#-license"><img src="https://img.shields.io/badge/License-AGPLv3-blue.svg?style=flat-square" alt="License"></a>
</p>

---

## 📑 Table of Contents

- [What is NoxMedia?](#-what-is-noxmedia)
- [Why NoxMedia? (Use-Cases)](#-why-noxmedia-use-cases)
- [Key Features](#-key-features)
- [Platform Support & iOS Limitations](#-platform-support--compatibility)
- [Step-by-Step Setup Guide](#-step-by-step-setup-guide)
  - [Step 1: Get Telegram API Credentials](#step-1-get-telegram-api-credentials)
  - [Step 2: Authenticate Account](#step-2-authenticate-account)
  - [Step 3: Connect Monitored Channels](#step-3-connect-monitored-channels)
  - [Step 4: Stream in Stremio / Nuvio](#step-4-stream-in-stremio--nuvio)
- [Remote Streaming (Tailscale & Custom Domains)](#-remote-streaming-tailscale--custom-domains)
- [Background Streaming Best Practices](#-background-streaming-best-practices)
- [Support & Sponsorship](#-support--sponsorship)
- [Acknowledgements & Inspiration](#-acknowledgements--inspiration)
- [License](#-license)

---

## 📖 What is NoxMedia?

**NoxMedia** is an advanced, standalone Android application that transforms your smartphone or tablet into a private, self-hosted media streaming server and **Stremio / Nuvio Addon**.

By interfacing directly with Telegram's robust content delivery network via an embedded MTProto bridge, NoxMedia parses, catalogs, enriches, and serves media files stored in your monitored Telegram channels straight to modern media players—with zero-latency byte-range seeking, automatic anime episode mapping, and zero external PC or hosting requirements.

> [!NOTE]
> **Acknowledgement & Roots:**  
> NoxMedia is inspired by the pioneering work of [Telegram-Stremio](https://github.com/weebzone/Telegram-Stremio). While honoring that open-source foundation, NoxMedia has been completely re-architected from the ground up as a native Android-first ecosystem featuring an embedded Python runtime (Chaquopy), a custom multi-part video merge pipeline, an automated Telegram cloud backup system, smart battery management, and a sleek, Nothing OS-inspired Jetpack Compose interface.

---

## 💡 Why NoxMedia? (Use-Cases)

### 1. Unlimited Cloud Media Vault (The Zero-Cost Jellyfin Alternative)
Self-hosting media servers like Plex, Jellyfin, or Emby traditionally requires thousands of dollars in investment: dedicated NAS enclosures, multi-terabyte mechanical hard drives, RAID arrays, and continuous electricity bills. NoxMedia unlocks **Telegram’s virtually unlimited cloud storage** to serve as your personal media library. Upload or forward your 4K HDR movies, anime collections, and series to private channels, and NoxMedia serves them seamlessly on demand.

### 2. Zero Server Overhead & Hardware Independence
No dedicated Linux home lab or Raspberry Pi required. An old spare Android phone or tablet running NoxMedia consumes less than 5 Watts of power while operating 24/7 as an ultra-compact local streaming server on your home network.

### 3. Native Stremio & Nuvio Ecosystem Integration
Install the local addon manifest URL once into **Stremio** or **Nuvio**, and all indexed media appears instantly across your devices—Smart TVs, streaming sticks, laptops, PCs, or tablets—with movie posters, synopsis, season groupings, and episode streams.

---

## ⚡ Key Features

### 🚀 High-Performance Streaming Engine
- **HTTP 206 Partial Content Support:** Built-in byte-range seek engine provides instant scrubbing, fast forward, and rewind identical to local file playback.
- **Dual-Tier Dynamic Caching:** Combines an ultra-fast **48MB in-memory LRU chunk cache** for instant header/index seeks with a configurable on-disk chunk cache (512MB – 8GB).
- **Multi-Part Split Video Assembly:** Automatically discovers and transparently concatenates split archive parts (`part1.mp4`, `part2.mp4` / `pt1`, `pt2`) into a single unified stream on the fly.

### 🧠 Intelligent Catalog & Automation
- **Continuous Auto-Scan:** Automatically discovers and indexes newly uploaded media in monitored channels every 30 seconds without manual syncing.
- **Deduplication & Quality Filter:** Identifies identical content uploaded across channels and provides an automated switch to skip or clean redundant split duplicates and low-quality CAM/TS releases.
- **Dead Link Detection:** Scans for deleted Telegram messages or revoked media and purges inaccessible items from your library automatically.

### 🎨 Rich Metadata & Artwork Enrichment
- **Automated TMDB & TVDB Scraping:** Automatically extracts clean release titles, release years, plot summaries, and backdrops.
- **Kitsu Anime Database Integration:** Built-in absolute numbering support (e.g., *One Piece 1130*, *Bleach 39*) ensuring long-running anime map to correct season/episode structures.
- **SpatialPosters & Custom API Compatibility:** Plug in custom poster providers or third-party artwork APIs for high-resolution graphics.

### ☁️ Automated Telegram Cloud Backup & Cross-Device Restore
- **Telegram Saved Messages Backup:** Automatically backs up all server settings, automation preferences, and monitored channels directly to your Telegram **Saved Messages** (`#NoxMediaBackup`) on a scheduled interval (1h, 2h, 6h, 12h, 24h).
- **Silent Cross-Device Auto-Sync:** When logging into NoxMedia on a new device (e.g., switching to a new phone or tablet), NoxMedia silently detects and restores your channels and preferences from your Telegram cloud backup in seconds.

### 🎙️ Alexa Smart Home Voice Control
- **Hands-Free Control:** Built-in faux Philips Hue bridge allowing you to turn NoxMedia streaming services ON or OFF using Amazon Alexa voice commands (*"Alexa, turn on NoxMedia"*).

### 🔋 Smart Battery & Power Management
- **Auto-Sleep Watchdog:** Configurable inactivity timer that shuts down streaming listeners when no client is playing to maximize battery life on mobile devices.
- **Wi-Fi High-Performance Locks:** Prevents the Android kernel from entering low-power network throttling while a video stream is actively playing.

### 🌐 Remote Access & Mesh Networking
- **Native Tailscale Detection:** Automatically detects your device's Tailscale VPN IP address for secure, out-of-home streaming anywhere in the world.
- **Public Domain Tunneling:** Support for custom reverse proxies and remote URLs (Cloudflare Tunnels, ngrok, VPS proxies).

### 🧪 Jellyfin Bridge Mode
- **Jellyfin Compatibility Layer:** *(Currently on Hold / In Development — Planned for Future Stable Release)* — An experimental bridge architecture allowing native Jellyfin clients and apps to stream directly from NoxMedia's indexed catalog.

---

## 📱 Platform Support & Compatibility

| Platform | Support Status | Notes |
| :--- | :---: | :--- |
| **Android Phones & Tablets** | ✅ **Fully Supported** | Android 7.0 (API 24) to Android 16 (Touchscreen UI) |
| **Android TV / Firestick** | ⚠️ **Untested / No D-Pad UI** | NoxMedia server app is built for touchscreens; no TV D-pad remote navigation yet |
| **Apple iOS (iPhone / iPad)** | ❌ **Not Supported (Host Server)** | Technical architectural limitation (see below) |

> [!TIP]
> **Host Server vs. Playback Clients:**  
> NoxMedia runs as the **server host** on your Android touchscreen device (phone or tablet). However, you can enjoy watching your streams on **any playback screen** (Smart TVs, Fire TV, Apple TV, PC, Mac, iPad) simply by installing **Stremio** or **Nuvio** on that device and connecting your NoxMedia manifest link!

### ❓ Why isn't NoxMedia server available for iOS?
Running a dedicated, 24/7 background media streaming server requires:
1. Long-lived background TCP socket listeners bound to local ports.
2. Uninterrupted background multi-connection networking.
3. Persistent Wi-Fi CPU wake-locks that ignore screen lock states.

**Apple's iOS security architecture strictly forbids long-running background socket servers.** iOS suspends and kills background network tasks within 30 seconds of the device being locked or an app being minimized. While you cannot host the NoxMedia server *on* an iOS device, **you can seamlessly watch NoxMedia streams on iOS** by opening [Stremio Web](https://web.stremio.com) or **Nuvio** on your iPhone/iPad and connecting to your Android phone's NoxMedia manifest URL!

---

## 🚀 Step-by-Step Setup Guide

```
┌────────────────────────┐      ┌───────────────────────────┐      ┌─────────────────────────┐
│ 1. Get API Credentials │ ───▶ │ 2. Log in with Account    │ ───▶ │ 3. Connect Channels     │
│    (my.telegram.org)   │      │    (In-App or Session)    │      │    (Public or Private)  │
└────────────────────────┘      └───────────────────────────┘      └─────────────────────────┘
                                                                                │
                                                                                ▼
                                                                   ┌───────────────────────────┐
                                                                   │ 4. Install Stremio/Nuvio  │
                                                                   │    (manifest.json URL)    │
                                                                   └───────────────────────────┘
```

### Step 1: Get Telegram API Credentials
1. Open any web browser and navigate to [my.telegram.org](https://my.telegram.org).
2. Log in using your Telegram phone number and confirmation code.
3. Tap on **API development tools**.
4. Fill in any App title and short name, then submit.
5. Copy your **`api_id`** (numbers) and **`api_hash`** (32-character string).

### Step 2: Authenticate Account
Open NoxMedia on your Android device and enter your `API ID` and `API Hash`. You have two authentication methods:
- **Direct In-App Login:** Enter your phone number, receive the official code in your Telegram app, and enter it directly.
- **Session String Login:** Paste an existing Pyrogram session string generated via our [Google Colab Notebook](https://colab.research.google.com/github/TheAceOfficials/NoxMedia/blob/main/string_session.ipynb) or via `@StringSessionRobot` in Telegram.

### Step 3: Connect Monitored Channels
1. Navigate to the **Channels** tab.
2. Enter the channel username (e.g., `@MyMoviesChannel`), invite link, or private channel ID (`t.me/c/...`).
3. Tap **Connect Channel**. NoxMedia will resolve channel access and begin background media indexing immediately.

### Step 4: Stream in Stremio / Nuvio
1. On the **Home** tab, toggle the server switch to **RUNNING**.
2. Note your local server URL (e.g., `http://192.168.1.50:7788/manifest.json`).
3. Open **Stremio** or **Nuvio** on any TV, PC, phone, or tablet connected to the same Wi-Fi.
4. Install the Addon:
   - **In Stremio:** Go to **Addons** ➔ paste your `manifest.json` URL into the search bar ➔ tap **Install**.
   - **In Nuvio:** Go to **Plugins / Addons** ➔ paste your `manifest.json` URL ➔ save & enable.
5. Browse your movies, series, and anime with full posters, episode streams, and instant seeking!

---

## 🌐 Remote Streaming (Tailscale & Custom Domains)

Want to access your Telegram media library in Stremio or Nuvio outside your house over mobile 4G/5G data?

1. **Install Tailscale** on both your NoxMedia Android device and your client device (phone or laptop).
2. NoxMedia automatically detects and displays your 100.x.x.x Tailscale IP address on the home screen.
3. Use the Tailscale manifest link:
   ```
   http://100.x.y.z:7788/manifest.json
   ```
4. Alternatively, enter a Cloudflare Tunnel or reverse-proxy domain into **Settings ➔ Remote Access URL**.

---

## ⚙️ Background Streaming Best Practices

To ensure Android does not pause or terminate NoxMedia when your screen is locked:
- **Set Battery to Unrestricted:** Go to Android **Settings ➔ Apps ➔ NoxMedia ➔ Battery ➔ Select "Unrestricted"**.
- **Lock App in Recents:** On devices with aggressive memory management (Xiaomi MIUI/HyperOS, OnePlus OxygenOS, Samsung OneUI), open the recent apps screen, long-press NoxMedia, and tap the **Lock** icon.
- **Exclude from Memory Cleaners:** Add NoxMedia to your device's whitelist for background app killers.

---

## 💖 Support & Sponsorship

**NoxMedia** is built with passion and provided 100% free and open-source under the **AGPLv3** license.

If you love using NoxMedia or want to support future open-source projects created by **@TheAceOfficials**, consider backing on Patreon:

<p align="center">
  <a href="https://www.patreon.com/cw/TheAceOfficials">
    <img src="https://img.shields.io/badge/Patreon-SUPPORT%20%40THEACEOFFICIALS-FF424D?style=for-the-badge&logo=patreon&logoColor=white" alt="Support on Patreon">
  </a>
</p>

### 🌟 Membership Tiers

- ☕ **Coffee Supporter ($3/mo):** Supporter credit in GitHub README & App UI + Early dev updates.
- 🔥 **Priority Feature Requester ($5/mo):** Fast-track feature requests & shape future project roadmaps.
- 👑 **Supporter Hall of Fame ($10/mo):** Official Sponsor Logo/Link credit across all @TheAceOfficials projects.

---

## 🤝 Acknowledgements & Inspiration

- [Telegram-Stremio](https://github.com/weebzone/Telegram-Stremio) — For the visionary concept of streaming Telegram media directly to Stremio.
- [Pyrogram](https://github.com/pyrogram/pyrogram) — Elegant, high-speed Telegram MTProto framework in Python.
- [Chaquopy](https://chaquo.com/chaquopy/) — Python SDK for Android that brings native MTProto capabilities into Jetpack Compose.
- [Stremio](https://stremio.com) & [Nuvio](https://nuvioapp.com) — Modern media streaming player ecosystems supporting third-party addons.

---

## 📄 License

This project is licensed under the **GNU Affero General Public License v3.0 (AGPLv3)**.  
Built with ❤️ by **[TheAceOfficials](https://github.com/TheAceOfficials)**.
