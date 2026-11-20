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
2. Open NoxMedia and enter your `api_id` and `api_hash`. You have two authentication methods:
   - **Direct In-App Login**: Enter your phone number, receive the official confirmation code in your Telegram app, and enter it directly. If 2-Step Verification (2FA) is active, enter your cloud password.
   - **Session String Login**: Paste an existing Pyrogram session string generated via our [Google Colab Notebook](https://colab.research.google.com/github/TheAceOfficials/NoxMedia/blob/main/string_session.ipynb) or via `@StringSessionRobot` in Telegram.

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
- **Channel IDs**: Numeric identifier with `-100` prefix (e.g., `-1001234567890`).

### Finding Channel IDs (Private & Public)
To connect private channels without a public `@username`:
1. Open [@idbot](https://t.me/idbot) or [@userinfobot](https://t.me/userinfobot) in Telegram and tap **Start**.
2. Forward any video or message from your private channel into the bot chat.
3. The bot will instantly reply with the chat's unique numeric ID:
   ```
   Forwarded from chat: My Private Media
   ID: -1001234567890
   ```
4. Copy the full ID including the `-100` prefix and paste it into NoxMedia's **Channels** tab.

### Smart File Parsing & Assembly
During channel sync, NoxMedia's engine parses raw file names:
- **Title & Year**: Strips release group prefixes while preserving title and release year.
- **Season & Episode**: Normalizes standard naming formats (`S01E05`, `1x05`, `Part 1`).
- **Multi-Part Split Video Assembly**: Automatically discovers and transparently concatenates split parts (`part1.mp4`, `part2.mp4` / `pt1`, `pt2`) into a single unified stream on the fly.
- **Quality & Dynamic Range**: Detects `2160p (4K)`, `1080p`, `HDR10`, `Dolby Vision`, `HEVC`, and `AV1`.
- **Audio & Subtitles**: Recognizes embedded multi-language tracks (`Hindi`, `English`, `5.1 AAC`, `Atmos`).

### ZIP Archive Handling & Direct Streaming
NoxMedia natively scans and parses media files packed inside `.zip` archives directly from Telegram channels without downloading or extracting them to disk:
- **Uncompressed ZIPs (Store Mode / 0% compression)**: Direct real-time streaming and arbitrary byte seeking work seamlessly. Because media files (such as `.mkv` or `.mp4`) are already internally compressed, storing them into a ZIP using `Store` (no compression) allows NoxMedia to read random byte-ranges on demand directly from Telegram's cloud.
- **Compressed ZIPs (Deflate)**: NoxMedia will detect and catalog the media file in your library with full posters and metadata, but real-time playback is not supported as compressed archives require decompressing from byte 0.

### Metadata Providers
- **Movies**: Primary matching against TheMovieDatabase (TMDB).
- **TV Series**: Comprehensive episode listings via TheTVDB & TMDB.
- **Anime**: Romaji title resolution and seasonal mapping via Kitsu.io and AniList. Automatic absolute episode numbering (e.g., *One Piece 1130*, *Bleach 39*) ensures long-running anime map to correct season and episode structures.

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

NoxMedia unifies playback state across your living room and mobile screens with automated cloud persistence and reliable 24/7 background operation.

### Cross-Device Sync & Scrobbling
- **Playback Continuity**: Pause a stream on Apple TV via Infuse or Vesper, and resume at the exact second on your phone or PC via Nuvio.
- **Universal Watched History**: Scrobbling and watched status update in real time across all connected clients.

### Automated Telegram Cloud Backup
- **Saved Messages Backup**: Automatically archives all server settings, custom catalogs, and monitored channels directly to your Telegram **Saved Messages** (`#NoxMediaBackup`) on configurable intervals (1h, 2h, 6h, 12h, 24h).
- **Silent Restore**: When logging into NoxMedia on a new phone or tablet, NoxMedia automatically detects your cloud backup and restores your channel configurations in seconds.

### Remote Access & Mobile Data (Tailscale Mesh VPN)
When operating over cellular mobile data (4G/5G), telecom carriers place devices behind Carrier-Grade NAT (CGNAT) and dynamically rotate IP addresses whenever switching towers, causing local manifest URLs to break.

[Tailscale](https://tailscale.com) creates a secure, zero-config peer-to-peer mesh network:
1. Install Tailscale on your NoxMedia Android host device and client devices (TV, laptop, iPhone).
2. Sign in with the same account on both devices.
3. Tailscale assigns your host device a permanent, static **`100.x.y.z`** IP address that never changes.
4. NoxMedia automatically detects and displays this Tailscale address on the home dashboard:
   ```
   http://100.x.y.z:7788/manifest.json
   ```
5. Connect this permanent URL into Nuvio, Infuse, or Vesper for uninterrupted worldwide remote access.

### 24/7 Background Standby Best Practices
To ensure Android does not suspend NoxMedia when your screen is locked:
- **Set Battery to Unrestricted**: Go to Android **Settings** -> **Apps** -> **NoxMedia** -> **Battery** -> Select **Unrestricted**.
- **Lock in Recent Apps**: On devices with aggressive memory management (Xiaomi MIUI/HyperOS, OnePlus OxygenOS, Samsung OneUI), open the recent apps overview, long-press NoxMedia, and select the **Lock** icon.
- **Wi-Fi High-Performance Locks**: NoxMedia automatically requests high-performance Wi-Fi locks to prevent radio sleep during active playback.

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

## NoxMedia - Jellyfin Bridge (Infuse, Reefin, Vesper)

NoxMedia integrates directly with modern Jellyfin clients including [Firecore Infuse](https://firecore.com/infuse), [Reefin](https://play.google.com/store/apps/details?id=com.shady.reefin&hl=en_US) (Android / Android TV / Google TV), and [Vesper](https://watchvesper.app) through an emulated Jellyfin bridge, offering full DirectPlay capabilities, 4K HDR10, Dolby Vision, and Spatial Audio.

### Connection Parameters
- **Protocol**: `Jellyfin`
- **Host**: Your device IP (e.g., `192.168.1.15`)
- **Port**: `7788`
- **Username**: `NoxMedia`
- **Password**: *(leave blank)*

> [!IMPORTANT]
> **Use Direct Mode in Infuse.**
> Browse media via the **Files** tab. Avoid enabling full background library crawling under Infuse Settings -> Library. Direct Mode queries metadata on-demand with zero-latency response times.
>
> **Android & Android TV with Reefin.**
> In Reefin, connect to `http://<your-device-ip>:7788` with username `NoxMedia` (no password needed). Reefin automatically leverages hardware ExoPlayer DirectPlay with isolated remote streaming pipelines.

### Pinned Home Screen Favorites
1. Open the **Files** tab in Infuse or server view in Vesper/Reefin and select your NoxMedia server.
2. Locate any custom shelf (e.g., *Murder Mystery Dark*).
3. Long-press or click the **Star** icon to favorite.
4. The shelf will appear as a dedicated horizontal row on your Apple TV, Android TV, or mobile Home Screen.

### Jellyfin Compatible Players

| Player | Supported Devices | Distribution | Download Link |
| :--- | :--- | :--- | :--- |
| **Infuse** | <DeviceGroup devices="iphone, ipad, mac, appletv, visionpro" /> | Apple App Store <span class="badge-official">Official</span> | [Get on App Store &rarr;](https://apps.apple.com/app/infuse-7/id1136220934) |
| **Reefin** | <DeviceGroup devices="android-phone, android-tv" /> | Google Play Store <span class="badge-official">Official</span> | [Get on Google Play &rarr;](https://play.google.com/store/apps/details?id=com.shady.reefin&hl=en_US) |
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
