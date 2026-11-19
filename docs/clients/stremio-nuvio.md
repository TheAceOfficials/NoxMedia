# Nuvio Streaming Guide

NoxMedia implements the official Addon v3 Protocol for modern streaming clients, featuring full support for [Nuvio](https://github.com/nuvio-app).

---

## Important Notice: Stremio Support Status

> [!NOTE]
> **Stremio Integration Temporarily On Hold**:
> Direct playback in Stremio is currently on hold while we resolve an issue related to Stremio's handling of local HTTP streaming URLs on localhost configurations. A dedicated fix is actively in progress and will arrive in an upcoming release.
>
> In the interim, **Nuvio** is the officially recommended client for addon-based streaming on Android, Windows, macOS, and Web.

---

## Nuvio Setup

[Nuvio](https://github.com/nuvio-app) is a sleek, modern streaming client designed for fast exploration and playback of custom media catalogs.

### 1. Retrieve Manifest URL
With the NoxMedia server active on your host device, your manifest endpoint is:

```http
http://<YOUR_SERVER_IP>:8096/manifest.json
```

*(For example: `http://192.168.1.15:8096/manifest.json` or `http://127.0.0.1:8096/manifest.json` if running locally on the same device).*

### 2. Add Provider in Nuvio
1. Open **Nuvio** on your Android, Windows, Mac, or Web client.
2. Navigate to **Settings** -> **Providers & Addons** -> **Add Custom Provider**.
3. Paste your full manifest URL:
   ```
   http://192.168.1.15:8096/manifest.json
   ```
4. Click **Connect / Install**.
5. Your Telegram libraries and AI custom shelves will populate the home feed immediately.

---

## Capabilities in Nuvio

| Feature | Support | Details |
| :--- | :---: | :--- |
| **Catalog Exploration** | Supported | Movies, TV Series, Anime, and all Custom AI Shelves |
| **Rich Metadata** | Supported | Synopses, cast info, ratings, and high-resolution posters |
| **Direct Stream Resolving** | Supported | Real-time byte streaming without local storage caching |
| **Multiple Audio & Subtitles** | Supported | Embedded audio tracks and subtitle selection |
| **Multi-Quality Sources** | Supported | 1080p, 4K, 720p, and Remux stream selectors |
