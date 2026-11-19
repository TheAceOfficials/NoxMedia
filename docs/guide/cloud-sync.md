# True Cloud Sync & 24/7 Standby

NoxMedia is designed to run silently in the background as an always-available home media server, keeping your watch states and library synchronized across all your screens.

---

## True Cloud Sync

Whether you watch on an Apple TV in your living room, an iPad in bed, or your computer via Nuvio, NoxMedia keeps your viewing session unified:

- **Resume Playback**: Pause on Infuse or Vesper on Apple TV, resume at the exact second on your phone or desktop via Nuvio.
- **Watched & Unwatched States**: Marking an episode or movie as watched reflects everywhere automatically.
- **Cross-Device Scrobbling**: Supports Jellyfin watch session reporting and optional Trakt scrobbling.

---

## 24/7 Background Standby Hosting

Android power management can sometimes suspend background processes. NoxMedia includes built-in safeguards to maintain an uninterrupted server uptime:

### 1. Low-Overhead Foreground Service
When the server toggle is enabled, NoxMedia launches an Android Foreground Service with a persistent notification, ensuring the operating system does not terminate the process.

### 2. Battery Optimization Exemption
For 24/7 reliability (especially when using an old phone or TV box as a dedicated server):
1. Navigate to your device's **Settings** -> **Apps** -> **NoxMedia**.
2. Select **Battery** -> Choose **Unrestricted** (or "Don't Optimize").
3. Disable manufacturer-specific task killers (such as Xiaomi MIUI/HyperOS Autostart or Samsung Put to Sleep).

### 3. Wi-Fi Power Management
Ensure your host device stays connected to Wi-Fi while sleeping:
- Keep the device connected to a power supply when acting as a permanent home hub.
- In advanced Wi-Fi settings, set Wi-Fi sleep policy to **Always on during sleep**.
