# Frequently Asked Questions (FAQ)

Find answers to common questions regarding NoxMedia setup, streaming protocols, and client compatibility.

---

### Does NoxMedia download whole files to host device storage?
**No.** NoxMedia functions as a real-time streaming bridge. When you initiate playback on Infuse, Vesper, or Nuvio, NoxMedia requests stream byte chunks directly from Telegram's servers and pipes them over HTTP directly to your player. Host device storage is not consumed by media files.

---

### Is NoxMedia completely free?
**Yes.** NoxMedia is free and open-source under the AGPLv3 / MIT licenses. There are no subscriptions, paywalls, or feature tiers.

---

### Can I stream outside my home Wi-Fi network?
**Yes.** There are multiple supported methods to access your server remotely:
1. **Tailscale / WireGuard (Recommended)**: Install Tailscale on your host device and your remote device (iPhone, laptop). You obtain a secure private mesh IP (e.g., `http://100.x.y.z:8096`).
2. **Cloudflare Tunnels**: Route port 8096 through a Cloudflare tunnel to your personal domain.
3. **Router Port Forwarding**: Forward external port `8096` to your host device's local IP address in router administration.

---

### Why does Infuse show an indexing error in Library Mode?
In Infuse Settings -> **Library**, do not enable full background library indexing on the NoxMedia share.
Because Telegram channels often contain thousands of files, Infuse's recursive library crawler can exceed local network timeouts. Instead, browse through **Files -> NoxMedia** (**Direct Mode**), which streams folder structures on-demand with zero-latency caching.

---

### Does NoxMedia work with Vesper?
**Yes.** Vesper connects seamlessly using the Jellyfin server protocol (`http://<HOST_IP>:8096`), offering high-speed playback across macOS, iOS, iPadOS, and tvOS.

---

### What is the current status of Stremio support?
Direct playback in Stremio is temporarily on hold while we resolve an issue related to Stremio's handling of localhost HTTP streaming endpoints on Android. In the interim, **Nuvio** is the officially recommended client for addon-based playback on Android, Windows, Mac, and Web.

---

### What video and audio codecs are supported?
Because NoxMedia serves direct media streams, codec support is determined by your client player:
- **Infuse & Vesper**: Plays virtually all formats natively (4K HDR10, Dolby Vision, HEVC/H.265, AV1, DTS-HD MA, Dolby TrueHD Atmos, FLAC).
- **Nuvio**: Supports modern video formats via hardware decoders.
- **Kodi**: Plays all raw remuxes and multi-channel audio tracks.
