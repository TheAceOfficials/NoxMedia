<p align="center">
  <img src="image_assets/NoxMediaBanner.png" alt="NoxMedia Banner" width="100%">
</p>

<p align="center">
  <strong>Transform your Android device into a private, high-performance media server powered by Telegram's infinite cloud storage.</strong>
</p>

<p align="center">
  <a href="https://theaceofficials.github.io/NoxMedia/"><img src="https://img.shields.io/badge/Documentation-NoxMedia%20Docs-9333ea?style=flat-square&logo=gitbook&logoColor=white" alt="Documentation"></a>
  <a href="https://www.reddit.com/r/NoxMedia/s/z2jwRxwK7v"><img src="https://img.shields.io/badge/Reddit-r%2FNoxMedia-FF4500?style=flat-square&logo=reddit&logoColor=white" alt="Reddit Community"></a>
  <a href="https://github.com/TheAceOfficials/NoxMedia/releases/latest"><img src="https://img.shields.io/badge/Platform-Android%207.0%2B-3DDC84?style=flat-square&logo=android&logoColor=white" alt="Platform"></a>
  <a href="https://kotlinlang.org"><img src="https://img.shields.io/badge/Kotlin-2.3-7F52FF?style=flat-square&logo=kotlin&logoColor=white" alt="Kotlin"></a>
  <a href="https://chaquo.com/chaquopy/"><img src="https://img.shields.io/badge/Python-3.11%20(Chaquopy)-3776AB?style=flat-square&logo=python&logoColor=white" alt="Python"></a>
  <a href="https://theaceofficials.github.io/NoxMedia/guide/getting-started#jellyfin-bridge"><img src="https://img.shields.io/badge/Jellyfin%20Bridge-Infuse%20%7C%20Vesper-a855f7?style=flat-square" alt="Jellyfin Bridge"></a>
  <a href="https://www.patreon.com/cw/TheAceOfficials"><img src="https://img.shields.io/badge/Patreon-Support-FF424D?style=flat-square&logo=patreon&logoColor=white" alt="Patreon"></a>
  <a href="#license"><img src="https://img.shields.io/badge/License-AGPLv3-blue.svg?style=flat-square" alt="License"></a>
</p>

<p align="center">
  <a href="https://theaceofficials.github.io/NoxMedia/"><strong>Explore the Official Documentation & Setup Guides &rarr;</strong></a>
</p>

---

## What is NoxMedia?

**NoxMedia** is an advanced, standalone Android application that transforms your smartphone or tablet into a private, self-hosted media streaming server and Jellyfin bridge.

By interfacing directly with Telegram's content delivery network through an embedded MTProto pipeline, NoxMedia parses, catalogs, enriches, and serves media files stored in your monitored Telegram channels straight to modern media players—with zero-latency byte-range seeking, automatic anime episode mapping, and zero external PC or hosting requirements.

---

## Why NoxMedia?

- **Unlimited Cloud Media Vault**: Upload or forward your 4K HDR movies, series, and anime to private Telegram channels. NoxMedia streams them on demand without consuming local device storage.
- **Hardware-Decoded DirectPlay**: Streams raw byte chunks directly to client hardware decoders (ExoPlayer, Infuse, Vesper), preserving native 4K HDR10, Dolby Vision, and multi-channel audio tracks with zero server transcoding overhead.
- **Under 5W Power Consumption**: Run 24/7 on a spare Android phone or tablet without needing noisy NAS enclosures, expensive hard drives, or home servers.
- **AI-Powered & Custom Shelves**: Curate themed catalogs using Google Gemini or OpenRouter LLM engines, or build rules based on resolution, IMDb scores, and genres.
- **Cross-Platform Ecosystem**: Stream seamlessly to Apple TV, iPhone, iPad, Mac, Windows, Linux, Android TV, and Smart TVs.

---

## Supported Players & Clients

| Player | Supported Hardware | Connection Protocol | Setup Guide |
| :--- | :--- | :--- | :--- |
| **Firecore Infuse** | Apple TV 4K, iPhone, iPad, Mac, Vision Pro | Jellyfin Direct Mode (`:7788`) | [Infuse Guide &rarr;](https://theaceofficials.github.io/NoxMedia/clients/infuse) |
| **Vesper** | macOS, iOS, iPadOS, tvOS, visionOS | Jellyfin Server (`:7788`) | [Vesper Guide &rarr;](https://theaceofficials.github.io/NoxMedia/clients/infuse#vesper-setup-guide) |
| **Nuvio** | Android (Phone/TV), Windows, Linux, macOS, Smart TVs | Stremio Addon Protocol (`:7788/manifest.json`) | [Nuvio Guide &rarr;](https://theaceofficials.github.io/NoxMedia/clients/stremio-nuvio) |

---

## Quick Start

1. **Install NoxMedia**: Download the latest APK from [GitHub Releases](https://github.com/TheAceOfficials/NoxMedia/releases/latest) on your Android device (Android 7.0+).
2. **Log in**: Enter your Telegram API credentials (`api_id` & `api_hash` from [my.telegram.org](https://my.telegram.org)) and authenticate with your phone number or Pyrogram session string.
3. **Connect Channels**: Add your public channel (`@channelname`) or private channel ID (`-100...`). NoxMedia will automatically index your media.
4. **Stream**: Start the server and connect your preferred client (Infuse, Vesper, or Nuvio) using your local IP (e.g., `http://192.168.1.15:7788`).

> For comprehensive step-by-step instructions, finding private channel IDs, and configuring Tailscale for 24/7 remote streaming, visit the **[Official Documentation Website](https://theaceofficials.github.io/NoxMedia/)**.

---

## Community & Support

Join the official **[r/NoxMedia Subreddit](https://www.reddit.com/r/NoxMedia/s/z2jwRxwK7v)** to ask questions, share setups, request features, and connect with other users.

NoxMedia is 100% free and open-source under the AGPLv3 license. If you love using NoxMedia or want to support future developments by **@TheAceOfficials**, consider backing on Patreon:

<p align="center">
  <a href="https://www.patreon.com/cw/TheAceOfficials">
    <img src="https://img.shields.io/badge/Patreon-SUPPORT%20%40THEACEOFFICIALS-FF424D?style=for-the-badge&logo=patreon&logoColor=white" alt="Support on Patreon">
  </a>
</p>

- **Coffee Supporter ($3/mo)**: Supporter credit in GitHub & App UI + early dev updates.
- **Priority Feature Requester ($5/mo)**: Fast-track feature requests & direct roadmap input.
- **Supporter Hall of Fame ($10/mo)**: Official Sponsor Logo/Link across all @TheAceOfficials projects.

---

## Acknowledgements & Roots

- **[Telegram-Stremio](https://github.com/weebzone/Telegram-Stremio)**: For the foundational concept of streaming Telegram media directly to media players.
- **[Pyrogram](https://github.com/pyrogram/pyrogram)**: Elegant, high-performance Telegram MTProto framework in Python.
- **[Chaquopy](https://chaquo.com/chaquopy/)**: The Python SDK for Android enabling native embedded MTProto pipelines inside Jetpack Compose.
- **[Firecore Infuse](https://firecore.com/infuse), [Vesper](https://watchvesper.app) & [Nuvio](https://nuvioapp.com)**: For providing world-class player ecosystems across mobile, desktop, and home theater screens.

---

## License

This project is licensed under the **GNU Affero General Public License v3.0 (AGPLv3)**.  
Built with passion by **[TheAceOfficials](https://github.com/TheAceOfficials)**.
