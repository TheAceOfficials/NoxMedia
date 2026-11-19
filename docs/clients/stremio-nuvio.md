# 🍿 Stremio & Nuvio Setup

NoxMedia implements the official **Stremio Addon v3 Protocol**, allowing you to integrate your Telegram libraries and AI custom catalogs into [Stremio](https://stremio.com) and [Nuvio](https://github.com/nuvio-app).

---

## 🚀 Quick Setup for Stremio

### 1. Get Your Manifest URL
Ensure your NoxMedia server is running. Your Stremio manifest URL is:

```http
http://<YOUR_SERVER_IP>:8096/manifest.json
```

*(e.g., `http://192.168.1.15:8096/manifest.json` or `http://127.0.0.1:8096/manifest.json` if running locally on the same Android TV / phone).*

### 2. Install Addon in Stremio
1. Open **Stremio** on your PC, Android TV, Mobile, or Web.
2. Click the **Addons** puzzle icon (🧩).
3. In the search box or address bar, paste your full manifest URL:
   ```
   http://192.168.1.15:8096/manifest.json
   ```
4. Click **Install**.
5. NoxMedia will now appear under your installed Community Addons.

---

## 🌟 Nuvio Setup

[Nuvio](https://github.com/nuvio-app) is a modern, lightweight streaming client designed for seamless metadata exploration.

1. Open **Nuvio** settings.
2. Go to **Providers / Addons** → **Add Custom Provider**.
3. Paste `http://<YOUR_SERVER_IP>:8096/manifest.json`.
4. Tap **Connect**.
5. Your custom shelves and Telegram channels will immediately populate the home feed.

---

## 🎯 What You Get

| Feature | Support | Details |
| :--- | :---: | :--- |
| **Catalog Exploration** | ✅ | Default Movies, Series, Anime + All Custom AI Shelves |
| **Rich Metadata** | ✅ | TMDB synopses, cast, ratings, and high-res posters |
| **Stream Resolving** | ✅ | Direct HTTP streaming without local disk caching |
| **Multiple Audio / Subtitles**| ✅ | Embedded tracks detected and passed directly to the player |
| **Multi-Quality Sources** | ✅ | 1080p, 4K, 720p stream selector dialogs |

---

## 💡 Pro Tips

> [!TIP]
> **Android TV Stremio Experience**:
> For the smoothest 4K HDR playback on Android TV boxes (such as Chromecast with Google TV or Xiaomi Box), set Stremio's default player to **ExoPlayer** with **Hardware Acceleration** turned on in settings.
