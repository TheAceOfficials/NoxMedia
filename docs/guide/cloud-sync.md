# ☁️ True Cloud Sync & 24/7 Standby

NoxMedia is designed to run seamlessly in the background as an always-available home media server, keeping your watch states and library synchronized across all your screens.

---

## 🔄 True Cloud Sync

Whether you watch on an Apple TV in your living room, an iPad in bed, or your Android phone on the go, NoxMedia keeps your viewing session unified:

- **Resume Playback**: Pause on Infuse Apple TV, resume at the exact second on Stremio mobile or Nuvio.
- **Watched / Unwatched State**: Marking an episode or movie as watched reflects everywhere automatically.
- **Cross-Device Scrobbling**: Supports Jellyfin watch session reporting and optional Trakt scrobbling.

---

## ⚡ 24/7 Background Standby Mode

Android's aggressive battery optimizations can sometimes put background processes to sleep. NoxMedia includes built-in safeguards to maintain an uninterrupted server uptime:

### 1. Foreground Service
When the server toggle is enabled, NoxMedia launches a low-overhead Android Foreground Service with a persistent notification, preventing the OS from terminating the process.

### 2. Battery Optimization Exemption
For true 24/7 reliability (especially when using an old phone as a dedicated server):
1. Go to your phone's **Settings** → **Apps** → **NoxMedia**.
2. Tap **Battery** → Select **Unrestricted** (or "Don't Optimize").
3. Disable any manufacturer-specific aggressive RAM cleaners (e.g., Xiaomi MIUI/HyperOS Autostart, Samsung Put to Sleep).

### 3. Wi-Fi Power Management
Ensure your host device stays connected to Wi-Fi while sleeping:
- Keep the device connected to a charger when acting as a permanent home hub.
- In Wi-Fi advanced settings, set Wi-Fi sleep policy to **Always on during sleep**.
