# NoxMedia Documentation

<section class="doc-section" id="introduction">

## Introduction

NoxMedia is a self-hosted personal streaming server that converts your Telegram channels and cloud media into a high-performance local media bridge compatible with Infuse, Vesper, and Nuvio.

Traditional media servers require massive storage drives and time-consuming downloads. NoxMedia operates on an on-demand cloud streaming architecture:

- **Zero Storage Waste**: Streams directly from Telegram cloud storage without saving large video files to disk.
- **Hardware DirectPlay**: Native 4K HDR10, Dolby Vision, and multi-track audio decoding performed directly on client hardware.
- **Zero Transcoding Overhead**: Lightweight byte-range HTTP pipeline runs effortlessly on modern smartphones, TV boxes, or servers.

</section>

<section class="doc-section" id="architecture">

## Architecture & Roots

NoxMedia interfaces directly with Telegram's content delivery network through a high-speed embedded MTProto pipeline:

- **Chaquopy Python Core**: Embedded Python 3.11 runtime executing optimized asynchronous MTProto protocol operations.
- **Real-Time Seeking**: Supports HTTP 206 Partial Content byte ranges, allowing instant random seeking across multi-gigabyte media files.
- **Roots**: Inspired by the foundational concepts of Telegram-Stremio, re-engineered from the ground up for native Android background hosting, automated backup synchronization, and zero-configuration local networking.

</section>

<section class="doc-section" id="installation">

## Installation & Login

### Prerequisites
1. An Android device running Android 7.0 or higher.
2. An active Telegram account.
3. Your Telegram API credentials (`api_id` and `api_hash`).

### Step 1: Obtain Telegram API Credentials
1. Navigate to [my.telegram.org](https://my.telegram.org) in your browser.
2. Log in using your phone number with the international country code.
3. Select **API development tools**.
4. Enter an app title (e.g., `NoxMedia`) and short name.
5. Copy your **`api_id`** and **`api_hash`**.

### Step 2: Install and Authenticate
1. Download the latest release from the [GitHub Releases](https://github.com/TheAceOfficials/NoxMedia/releases/latest) page.
2. Open NoxMedia and enter your `api_id`, `api_hash`, and phone number.
3. Enter the login confirmation code sent to your official Telegram app.
4. If Two-Step Verification (2FA) is active, enter your cloud password.

### Step 3: Start Server
1. From the home screen, switch the **Server Toggle** to ON.
2. Grant requested background battery exemptions so Android does not suspend server operations.
3. Note your displayed local address (e.g., `http://192.168.1.15:8096`).

</section>

<section class="doc-section" id="channels-setup">

## Channels & Indexing

NoxMedia turns both private and public channels into a structured, searchable catalog.

### Supported Channel Identifiers
- **Public Channels**: Username format `@channelname` or URL `https://t.me/channelname`.
- **Private Channels**: Invite link `https://t.me/+inviteHash` (your authenticated account must be a member).
- **Channel IDs**: Numeric identifier (e.g., `-1001234567890`).

### Smart File Parsing
During channel sync, NoxMedia's engine parses raw file names:
- **Title & Year**: Strips release group prefixes while preserving title and release year.
- **Season & Episode**: Normalizes standard naming formats (`S01E05`, `1x05`, `Part 1`).
- **Quality & Dynamic Range**: Detects `2160p (4K)`, `1080p`, `HDR10`, `Dolby Vision`, `HEVC`, and `AV1`.
- **Audio & Subtitles**: Recognizes embedded multi-language tracks (`Hindi`, `English`, `5.1 AAC`, `Atmos`).

### Metadata Providers
- **Movies**: Primary matching against TheMovieDatabase (TMDB).
- **TV Series**: Comprehensive episode listings via TheTVDB & TMDB.
- **Anime**: Romaji title resolution and seasonal mapping via Kitsu.io and AniList.

</section>

<section class="doc-section" id="ai-catalogs">

## AI & Custom Shelves

Instead of rigid static genres, NoxMedia allows you to construct dynamic collections using artificial intelligence or explicit rules.

### AI Assistant Curation
1. Configure your free **Google Gemini API Key** or **OpenRouter API Key** in app settings.
2. Navigate to **Catalogs** -> **Create New Shelf** -> **AI Assistant**.
3. Provide a natural language description:
   > *"Find all psychological thrillers, neo-noir, and investigative murder mysteries with dark atmospheres and high suspense."*
4. NoxMedia analyzes your library's plot summaries and thematic tags to assemble the shelf.

### Rule-Based Criteria
- **Resolution**: Restrict to `4K UHD` or `Dolby Vision` only.
- **Ratings**: Filter by TMDB score (e.g., `Score >= 7.5`).
- **Genres & Keywords**: Target specific directors, production houses, or tags.

</section>

<section class="doc-section" id="cloud-sync">

## True Cloud Sync & Standby

NoxMedia unifies playback state across your living room and mobile screens.

- **Playback Continuity**: Pause a stream on Apple TV via Infuse or Vesper, and resume at the exact second on your phone or PC via Nuvio.
- **Watched History**: Scrobbling updates reflect universally across all connected clients.
- **24/7 Android Standby**:
  - Run as an Android Foreground Service with continuous wake lock protection.
  - Set device battery management to **Unrestricted** / **Don't Optimize**.
  - Ensure Wi-Fi sleep policy is set to **Always on during sleep**.

</section>

<section class="doc-section" id="infuse-vesper">

## Infuse & Vesper Setup (Apple Ecosystem)

NoxMedia emulates a Jellyfin server bridge, providing deep native integration with both [Firecore Infuse](https://firecore.com/infuse) and [Vesper](https://watchvesper.app).

### Connection Parameters
- **Protocol**: `Jellyfin`
- **Host**: Your host IP (e.g., `192.168.1.15`)
- **Port**: `8096`
- **Username**: `NoxMedia`
- **Password**: *(leave blank)*

### Infuse Direct Mode Recommendation

> [!IMPORTANT]
> **Use Direct Mode in Infuse.**
> Browse media via the **Files** tab. Avoid enabling full background library crawling under Infuse Settings -> Library. Direct Mode queries metadata on-demand with zero-latency response times.

### Pinned Home Screen Favorites
1. Open the **Files** tab in Infuse and select your NoxMedia server.
2. Locate any custom shelf (e.g., *Murder Mystery Dark*).
3. Long-press or click the **Star** icon to favorite.
4. The shelf will appear as a dedicated horizontal row on your Apple TV or iOS Home Screen.

</section>

<section class="doc-section" id="nuvio">

## Nuvio Streaming

NoxMedia exposes an Addon v3 manifest for [Nuvio](https://github.com/nuvio-app).

### Manifest URL
```http
http://<YOUR_HOST_IP>:8096/manifest.json
```

### Setup Steps
1. Open Nuvio on Android, Windows, macOS, or Web.
2. Navigate to **Settings** -> **Providers & Addons** -> **Add Custom Provider**.
3. Paste your manifest URL and click **Connect**.
4. Your Telegram library and custom shelves will populate the home feed.

### Notice on Stremio Support

> [!NOTE]
> Direct playback in Stremio is temporarily on hold while we resolve an issue related to Stremio's handling of localhost HTTP streaming URLs on Android. In the interim, **Nuvio** is the officially recommended client.

</section>

<section class="doc-section" id="android-tv-kodi">

## Android TV & Kodi Guide

### Method 1: Nuvio for Android TV
Install the Nuvio Android TV APK, add the manifest URL, and navigate catalogs using standard remote controls.

### Method 2: Kodi Jellyfin Addon
1. Install the official **Jellyfin for Kodi** addon.
2. Point server address to `http://<YOUR_IP>:8096`.
3. Select **Add-on (HTTP Stream)** mode.
4. Browse structured libraries and custom shelves directly from Kodi.

</section>

<section class="doc-section" id="platforms">

## Platform Compatibility

<div class="supported-ecosystem-showcase">
  <div class="device-showcase-item">
    <DeviceIcon name="iphone" />
    <span>iPhone</span>
  </div>
  <div class="device-showcase-item">
    <DeviceIcon name="ipad" />
    <span>iPad</span>
  </div>
  <div class="device-showcase-item">
    <DeviceIcon name="mac" />
    <span>Mac</span>
  </div>
  <div class="device-showcase-item">
    <DeviceIcon name="appletv" />
    <span>Apple TV 4K</span>
  </div>
  <div class="device-showcase-item">
    <DeviceIcon name="visionpro" />
    <span>Apple Vision Pro</span>
  </div>
  <div class="device-showcase-item">
    <DeviceIcon name="tv" />
    <span>TV / Monitor</span>
  </div>
</div>

| Client / Platform | Supported Devices | Connection Protocol | Status |
| :--- | :--- | :--- | :--- |
| **Infuse** | <DeviceGroup devices="iphone, ipad, mac, appletv, visionpro" /> | Jellyfin Bridge (Direct Mode) | Available Now |
| **Vesper** | <DeviceGroup devices="iphone, ipad, mac, appletv" /> | Jellyfin Bridge | Available Now |
| **Nuvio** | <DeviceGroup devices="iphone, ipad, mac, tv" /> | Stremio v3 Addon Protocol | Available Now |
| **Kodi / EmbyCon** | <DeviceGroup devices="tv, mac" /> | Jellyfin / Emby Addon | Available Now |
| **Native Android TV App** | <DeviceGroup devices="tv" /> | Native D-Pad Player | Coming Soon |
| **Windows Desktop Native** | <DeviceGroup devices="mac" /> | Standalone Client | Coming Soon |
| **Linux Client / Server** | <DeviceGroup devices="mac" /> | Standalone & Headless | Coming Soon |

</section>
