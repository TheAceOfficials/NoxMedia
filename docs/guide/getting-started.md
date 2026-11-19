# Getting Started with NoxMedia

Welcome to **NoxMedia** — a self-hosted personal streaming server that converts your Telegram channels and cloud media into a high-performance local media server compatible with **Infuse**, **Stremio**, **Nuvio**, and **Android TV**.

---

## 🌟 What makes NoxMedia different?

Traditional media servers require massive hard drives and constant file downloads. NoxMedia operates on an on-demand streaming architecture:

- **Zero Local Storage Waste:** Stream directly from cloud telegram storage into your favorite media players.
- **Universal Client Support:** Connect from Apple TV via **Infuse**, from Windows/Mac/Linux via **Stremio**, or from mobile via **Nuvio**.
- **Hardware-Decoded DirectPlay:** No server-side transcoding required. Your player decodes high-bitrate 4K, HDR, Dolby Vision, and Atmos natively.

---

## 📋 Prerequisites

1. An Android device (Phone, Tablet, or Android TV / Google TV / Shield TV) running **Android 7.0 (Nougat) or newer**.
2. A Telegram account with access to media channels containing your movies and series.
3. Your own free **Telegram API ID & Hash** (detailed below).

---

## 🚀 Step 1: Download the NoxMedia APK

Download the latest version corresponding to your device architecture from the [GitHub Releases](https://github.com/TheAceOfficials/NoxMedia/releases/latest):

- **NoxMedia-arm64-v8a.apk** *(Recommended)*: For modern 64-bit phones, tablets, and Google TV with Chromecast.
- **NoxMedia-armeabi-v7a.apk**: For older 32-bit devices and budget Android TV boxes (Mi Box, older Fire TV sticks).
- **NoxMedia-universal.apk**: Universal build containing all CPU architectures.

---

## 🔑 Step 2: Get your Telegram API Credentials

To stream media through MTProto, Telegram requires official developer API credentials (which are 100% free):

1. Open your browser and navigate to **[my.telegram.org](https://my.telegram.org)**.
2. Log in with your Telegram account phone number and submit the confirmation code sent to your Telegram app.
3. Tap on **API development tools**.
4. Fill in an **App title** and **Short name** (e.g., `NoxMedia` and `nox`).
5. Tap **Create application**.
6. Copy your **`api_id`** (numbers) and **`api_hash`** (letters and numbers).

> [!TIP]
> Keep your API Hash private. NoxMedia stores these keys locally in encrypted private storage on your device.

---

## 📱 Step 3: Sign in to NoxMedia

1. Launch the NoxMedia app on your Android device.
2. Enter your **`api_id`** and **`api_hash`** on the setup screen.
3. Enter your phone number with your country code (e.g., `+919876543210`).
4. Enter the verification code sent to your Telegram app.
5. If you have Two-Step Verification (cloud password) enabled, enter your password.

---

## 📂 Step 4: Add Media Channels

1. Once logged in, tap on the **Channels** tab.
2. Select the channels containing your movies, TV shows, and anime.
3. NoxMedia will automatically index your messages, parse file names, detect multi-part episodes, and assign rich TMDB/TVDB posters and metadata.

---

## 🌐 Step 5: Start Streaming

Go to the **Home** tab and tap **Start Server**. 

NoxMedia will display your local server address (e.g., `http://192.168.1.10:8096`). You can now connect your favorite players:

- [🍎 Connect Apple TV & iOS via Infuse](/clients/infuse)
- [🍿 Connect Stremio & Nuvio](/clients/stremio-nuvio)
- [📺 Connect Android TV & Kodi](/clients/android-tv-kodi)
