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
1. **Tailscale / WireGuard (Recommended)**: Install Tailscale on your host device and your remote device (iPhone, laptop). You obtain a secure private mesh IP (e.g., `http://100.x.y.z:7788`).
2. **Cloudflare Tunnels**: Route port 7788 through a Cloudflare tunnel to your personal domain.
3. **Router Port Forwarding**: Forward external port `7788` to your host device's local IP address in router administration.

---

### Why does Infuse show an indexing error in Library Mode?
In Infuse Settings -> **Library**, do not enable full background library indexing on the NoxMedia share.
Because Telegram channels often contain thousands of files, Infuse's recursive library crawler can exceed local network timeouts. Instead, browse through **Files -> NoxMedia** (**Direct Mode**), which streams folder structures on-demand with zero-latency caching.

---

### Does NoxMedia work with Vesper?
**Yes.** Vesper connects seamlessly using the Jellyfin server protocol (`http://<HOST_IP>:7788`), offering high-speed playback across macOS, iOS, iPadOS, and tvOS.

---

### What is the current status of Stremio support?
Direct playback in Stremio is temporarily on hold while we resolve an issue related to Stremio's handling of localhost HTTP streaming endpoints on Android. In the interim, **Nuvio** is the officially recommended client for addon-based playback on Android, Windows, Mac, and Web.

---

### What video and audio codecs are supported?
Because NoxMedia serves direct media streams, codec support is determined by your client player:
- **Infuse & Vesper**: Plays virtually all formats natively (4K HDR10, Dolby Vision, HEVC/H.265, AV1, DTS-HD MA, Dolby TrueHD Atmos, FLAC).
- **Nuvio**: Supports modern video formats via hardware decoders.
- **Kodi**: Plays all raw remuxes and multi-channel audio tracks.

---

### Can NoxMedia stream files packed inside `.zip` archives?
**Yes, for uncompressed archives.**
- **Store Mode (0% Compression)**: Works seamlessly. Because media files (like `.mkv` or `.mp4`) are already compressed, packing them with `Store` mode allows NoxMedia to perform random byte seeks on-demand directly from Telegram's cloud.
- **Compressed Archives (Deflate)**: Detected and cataloged in your library with full artwork and metadata, but real-time random seeking is not supported as decompression must begin from byte 0.

---

### Why can't I host the NoxMedia server on iPhone or iPad?
Apple's iOS security architecture strictly prohibits long-running background network socket listeners. iOS suspends or terminates background processes within 30 seconds of the device being locked or minimized.
However, **you can seamlessly watch NoxMedia streams on iOS** by connecting your Apple device (iPhone, iPad, Mac, Apple TV, Vision Pro) to your Android host server using **Infuse**, **Vesper**, or **Nuvio**!

---

### How do I find private Telegram channel IDs?
1. Open [@idbot](https://t.me/idbot) or [@userinfobot](https://t.me/userinfobot) in Telegram.
2. Forward any video or message from your private channel into the bot.
3. The bot will reply with the channel's unique ID (e.g., `-1001234567890`).
4. Copy the entire ID including the `-100` prefix and paste it into NoxMedia's **Channels** tab.

---

### How do I keep the server running 24/7 without Android killing it?
1. **Set Battery to Unrestricted**: Go to Android **Settings** -> **Apps** -> **NoxMedia** -> **Battery** -> Select **Unrestricted**.
2. **Lock in Recent Apps**: Open the Android task switcher, long-press NoxMedia, and tap the **Lock** icon (on MIUI/HyperOS, OxygenOS, or OneUI).
3. **Exclude from App Killers**: Add NoxMedia to your OEM's background whitelist so battery optimizers do not terminate the background service.
