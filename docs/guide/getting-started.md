# Getting Started with NoxMedia

Welcome to NoxMedia, a self-hosted personal streaming server that converts your Telegram channels and cloud media into a high-performance local media server compatible with Infuse, Vesper, and Nuvio.

---

## Architecture Overview

Traditional media servers require large local hard drives and constant file downloads. NoxMedia operates on an on-demand cloud streaming architecture:

- **Zero Local Storage Waste**: Stream directly from cloud Telegram storage into your media players without saving gigabytes of video files to disk.
- **Universal Client Support**: Connect from Apple TV, iPhone, iPad, and Mac via Infuse and Vesper, or from Android, Windows, and Web via Nuvio.
- **Hardware-Decoded DirectPlay**: No server-side transcoding required. Your player hardware handles high-bitrate 4K, HDR, Dolby Vision, and Atmos natively.

---

## Supported Clients & Platforms

| Client | Supported Operating Systems | Connection Mode |
| :--- | :--- | :--- |
| **Infuse** | Apple TV (tvOS), iPhone & iPad (iOS), Mac (macOS), Apple Vision Pro (visionOS) | Jellyfin Bridge (Direct Mode) |
| **Vesper** | Mac (macOS), iPhone & iPad (iOS), Apple TV (tvOS) | Jellyfin Bridge |
| **Nuvio** | Android, Windows, macOS, Web | Stremio v3 Addon Protocol |
| **Kodi / EmbyCon** | Android TV, Google TV, Shield TV, Fire TV, PC | Jellyfin Addon / HTTP Stream |
| **Android TV / Google TV App** | Android TV, Google TV | Native App (Coming Soon) |
| **Windows Desktop** | Windows 10, 11 | Native Client (Coming Soon) |
| **Linux Client / Server** | Ubuntu, Debian, Arch | Headless Server (Coming Soon) |

---

## Prerequisites

Before setting up NoxMedia, ensure you have:
1. An Android device running Android 7.0 or higher (phone, tablet, or TV box) to host the server.
2. An active Telegram account.
3. Your Telegram API credentials (`api_id` and `api_hash`).

---

## Step 1: Obtain Telegram API Credentials

To stream media through Telegram's high-speed MTProto network:
1. Visit [my.telegram.org](https://my.telegram.org) in any web browser.
2. Log in using your Telegram phone number (include international country code).
3. Click on **API development tools**.
4. Fill in the **App title** and **Short name** (for example, `NoxMedia`).
5. Copy your **`api_id`** (an integer) and **`api_hash`** (a 32-character string).

---

## Step 2: Install and Authenticate NoxMedia

1. Download the latest release from the [GitHub Releases](https://github.com/TheAceOfficials/NoxMedia/releases/latest) page.
2. Install the APK on your Android host device.
3. Open NoxMedia and enter your `api_id`, `api_hash`, and your phone number.
4. Enter the verification code sent to your official Telegram app.
5. If Two-Step Verification (2FA) is enabled on your account, enter your cloud password when prompted.

---

## Step 3: Start the Server

1. From the NoxMedia home screen, toggle the **Server Switch** to ON.
2. Grant battery optimization exemptions and notification permissions so Android does not terminate the background service.
3. NoxMedia will display your local server address (e.g., `http://192.168.1.15:8096`).

---

## Next Steps

Now that your server is active, proceed to connect your streaming clients:

- [Connect Apple TV, iOS & Mac via Infuse and Vesper](/clients/infuse)
- [Connect Nuvio Streaming Client](/clients/stremio-nuvio)
- [Connect Android TV & Kodi](/clients/android-tv-kodi)
- [View Project Roadmap & Progress](/roadmap)
