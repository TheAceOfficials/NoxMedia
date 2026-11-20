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
3. Note your displayed local address (e.g., `http://192.168.1.15:7788`).

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

<section class="doc-section" id="nuvio">

## NoxMedia - Nuvio

NoxMedia provides seamless streaming through the official Stremio Addon v3 Protocol, designed for [Nuvio](https://github.com/nuvio-app).

### Setup Instructions
1. Open NoxMedia on your host Android device and verify the server is running.
2. Note your local manifest endpoint:
   ```http
   http://<YOUR_HOST_IP>:7788/manifest.json
   ```
3. Open Nuvio on your phone, television, or computer.
4. Navigate to **Settings** -> **Providers & Addons** -> **Add Custom Provider**.
5. Paste your manifest URL and tap **Connect**. Your library and AI custom shelves will populate immediately.

> [!NOTE]
> Direct playback in Stremio is temporarily on hold while we resolve an issue related to Stremio's handling of localhost HTTP streaming URLs on Android. In the interim, **Nuvio** is the officially recommended client.

### Nuvio Supported Platforms & Downloads

| Platform / Environment | Distribution | Download Link |
| :--- | :--- | :--- |
| **Android Phone, Tablet & TV** <br><DeviceGroup devices="android-phone, android-tablet, android-tv" /> | Google Play Store <span class="badge-official">Official</span> | [Get on Google Play &rarr;](https://play.google.com/store/apps/details?id=com.nuvio.app) |
| **Android Mobile APK** <br><DeviceGroup devices="android-phone" /> | GitHub Releases <span class="badge-sideload">Sideloading</span> | [Download Mobile APK &rarr;](https://github.com/NuvioMedia/NuvioMobile/releases) |
| **Android TV & Google TV APK** <br><DeviceGroup devices="android-tv" /> | GitHub Releases <span class="badge-sideload">Sideloading</span> | [Download TV APK &rarr;](https://github.com/NuvioMedia/NuvioTV/releases) |
| **Desktop (Windows, Linux, macOS)** <br><DeviceGroup devices="windows, linux, mac" /> | GitHub Releases <span class="badge-official">Standalone</span> | [Download Desktop &rarr;](https://github.com/NuvioMedia/NuvioDesktop/releases) |
| **Smart TV Packages (Tizen, webOS)** <br><DeviceGroup devices="smart-tv" /> | GitHub Releases <span class="badge-sideload">Sideloading</span> | [Get TV Packages &rarr;](https://github.com/NuvioMedia/NuvioTVSmart/releases) |
| **iPhone & iPad (Enhanced)** <br><DeviceGroup devices="iphone, ipad" /> | GitHub Releases <span class="badge-sideload">Sideloading</span> | [Download iOS IPA &rarr;](https://github.com/luqmanfadlli/NuvioMobile-Enhanced/releases) |
| **Apple TV (tvOS)** <br><DeviceGroup devices="appletv" /> | GitHub Releases <span class="badge-sideload">Sideloading</span> | [Download tvOS Package &rarr;](https://github.com/bobsupra/NuvioTVOS) |

</section>

<section class="doc-section" id="jellyfin-bridge">

## NoxMedia - Jellyfin Bridge (Apple Ecosystem)

NoxMedia integrates directly with [Firecore Infuse](https://firecore.com/infuse) and [Vesper](https://watchvesper.app) through an emulated Jellyfin bridge, offering full DirectPlay capabilities, 4K HDR10, Dolby Vision, and Spatial Audio.

### Connection Parameters
- **Protocol**: `Jellyfin`
- **Host**: Your device IP (e.g., `192.168.1.15`)
- **Port**: `7788`
- **Username**: `NoxMedia`
- **Password**: *(leave blank)*

> [!IMPORTANT]
> **Use Direct Mode in Infuse.**
> Browse media via the **Files** tab. Avoid enabling full background library crawling under Infuse Settings -> Library. Direct Mode queries metadata on-demand with zero-latency response times.

### Pinned Home Screen Favorites
1. Open the **Files** tab in Infuse or server view in Vesper and select your NoxMedia server.
2. Locate any custom shelf (e.g., *Murder Mystery Dark*).
3. Long-press or click the **Star** icon to favorite.
4. The shelf will appear as a dedicated horizontal row on your Apple TV or iOS Home Screen.

### Jellyfin Compatible Players

| Player | Supported Devices | Distribution | Download Link |
| :--- | :--- | :--- | :--- |
| **Infuse** | <DeviceGroup devices="iphone, ipad, mac, appletv, visionpro" /> | Apple App Store <span class="badge-official">Official</span> | [Get on App Store &rarr;](https://apps.apple.com/app/infuse-7/id1136220934) |
| **Vesper** | <DeviceGroup devices="iphone, ipad, mac, appletv, visionpro" /> | Apple TestFlight <span class="badge-testflight">TestFlight</span> | [Join TestFlight &rarr;](https://testflight.apple.com/join/mcCk473Y) |

> More players and platform integrations are in active development and will be added soon.

</section>

<section class="doc-section" id="native-apps">

## NoxMedia Native Apps (Coming Soon)

We are actively developing standalone, first-party NoxMedia client applications tailored for specific hardware ecosystems:

| Application | Target Ecosystem | Focus & Highlights | Status |
| :--- | :--- | :--- | :--- |
| **Native Android TV & Google TV** | <DeviceGroup devices="android-tv" /> | 10-foot D-Pad interface, hardware ExoPlayer acceleration, home screen channels | Coming Soon |
| **Windows Desktop Native** | <DeviceGroup devices="windows" /> | Standalone desktop application with embedded core runtime | Coming Soon |
| **Linux Desktop & Headless** | <DeviceGroup devices="linux" /> | Dedicated GUI media client and headless background server daemon | Coming Soon |

</section>
