# Infuse & Vesper Setup (Apple Ecosystem)

NoxMedia features native high-performance integration with both [Firecore Infuse](https://firecore.com/infuse) and [Vesper](https://watchvesper.app), providing an Apple-grade home theater experience across Apple TV, iPhone, iPad, Mac, and Apple Vision Pro.

Through NoxMedia's **Emulated Jellyfin Bridge**, both players receive full DirectPlay capabilities, 4K HDR10, Dolby Vision passthrough, Spatial Audio, and instant metadata resolution.

---

## Supported Apple Platforms

| Player | Supported Devices | Recommended Mode |
| :--- | :--- | :--- |
| **Firecore Infuse** | Apple TV (tvOS), iPhone & iPad (iOS), Mac (macOS), Apple Vision Pro (visionOS) | Jellyfin Share (Direct Mode in Files) |
| **Vesper** | Mac (macOS), iPhone & iPad (iOS), Apple TV (tvOS) | Jellyfin Server Connection |

---

## Infuse Setup Guide

### 1. Identify Server IP
1. Open NoxMedia on your Android host device.
2. Ensure the server toggle is active.
3. Note the local address displayed on the dashboard (e.g., `http://192.168.1.15:8096`).

### 2. Connect in Infuse
1. Open **Infuse** on your Apple TV, iPhone, iPad, or Mac.
2. Navigate to **Settings** (Gear icon) -> **Shares** -> **Add Media...**
3. Choose **Other...** or select **Jellyfin**.
4. Enter the connection parameters:
   - **Protocol**: `Jellyfin` (or `Emby`)
   - **Address**: `192.168.1.15` *(enter your device's actual IP)*
   - **Port**: `8096`
   - **Username**: `NoxMedia` *(or any username)*
   - **Password**: *(leave blank)*
5. Tap **Save**. Infuse will connect to your server immediately.

### 3. Direct Mode vs. Library Mode

> [!IMPORTANT]
> **Use Direct Mode (Files Tab) in Infuse.**
> In Infuse Settings -> **Library**, do not enable full background crawling on the NoxMedia share. Infuse's background crawler attempts to recursively inspect every single item at once, which can trigger network timeouts. Direct Mode (browsing via the **Files** tab) queries metadata on-demand with instant zero-latency responses.

### 4. Custom Shelves & Home Screen Favorites
1. Open Infuse and navigate to the **Files** tab.
2. Select the **NoxMedia** share.
3. You will see default libraries (**Movies**, **TV Shows**, **Anime**) alongside all your custom AI-curated catalogs (e.g., *Murder Mystery Dark*, *Oscar Winners*, *Sci-Fi Universe*).
4. Long-press or select the **Star** icon on any custom shelf to mark it as a **Favorite**.
5. Starred shelves appear as dedicated horizontal rows on your Apple TV and iOS Home Screen.

---

## Vesper Setup Guide

[Vesper](https://watchvesper.app) is an ultra-fast, modern media player designed specifically for macOS, iOS, and tvOS with a streamlined, minimalist interface.

### Connection Steps:
1. Open **Vesper** on your Mac, iPhone, iPad, or Apple TV.
2. Go to **Settings** -> **Servers** -> **Add Server**.
3. Select **Jellyfin**.
4. Enter your host server details:
   - **Server URL**: `http://192.168.1.15:8096`
   - **Username**: `NoxMedia`
   - **Password**: *(leave blank)*
5. Confirm the connection. Vesper will synchronize your libraries and display posters, episode layouts, and custom shelves.

---

## Codec & Audio Capabilities

- **DirectPlay Everything**: Infuse and Vesper handle HEVC/H.265, AV1, VP9, H.264, DTS-HD, Dolby TrueHD Atmos, and EAC3 natively without server-side transcoding.
- **Embedded Tracks**: Switch between multiple audio streams and subtitle tracks on the fly.
- **Dolby Vision & HDR10+**: Automatically mapped to match display profiles on Apple TV 4K, ProMotion iPads, and Liquid Retina XDR displays.
