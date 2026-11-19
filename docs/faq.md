# ❓ Frequently Asked Questions (FAQ)

Got questions? Here are the most common questions and answers about NoxMedia.

---

### Does NoxMedia download whole files to my phone's storage?
**No.** NoxMedia functions as a real-time streaming bridge. When you tap play on Infuse or Stremio, NoxMedia requests stream byte chunks directly from Telegram's servers and pipes them over HTTP to your player. Your device storage is not consumed by media files.

---

### Is NoxMedia completely free?
**Yes.** NoxMedia is 100% free and open-source under the Apache-2.0 / MIT license. There are no subscriptions, paywalls, or premium tiers.

---

### Can I stream outside my home Wi-Fi network?
**Yes!** There are several ways to access your NoxMedia server while traveling:
1. **Tailscale / WireGuard (Recommended)**: Install Tailscale on your NoxMedia host phone and your client (iPhone, laptop). You get an encrypted, zero-config private IP (e.g., `http://100.x.y.z:8096`).
2. **Cloudflare Tunnels**: Route port 8096 through a Cloudflare tunnel to your own domain name.
3. **Port Forwarding**: Forward external port `8096` to your host device's local IP in your router settings.

---

### Why does Infuse show an indexing error in Library Mode?
In Infuse Settings → **Library**, do not add NoxMedia as a library scan folder.
Because Telegram channels can contain thousands of media files, Infuse's automated recursive library crawler can exhaust network timeouts. Instead, browse through **Files → NoxMedia** (**Direct Mode**), which loads directories on-demand with **0ms latency**!

---

### Is my Telegram account safe?
**Yes.** NoxMedia connects using official Telegram Client API credentials (`api_id` and `api_hash`) via standard TDLib architecture. It respects Telegram's client rate limits and does not modify channel contents.

---

### What video and audio codecs are supported?
Since NoxMedia serves raw direct streams, codec support depends on your client player:
- **Infuse (Apple TV/iOS)**: Plays virtually everything natively (4K HDR10, Dolby Vision, HEVC/H.265, AV1, DTS-HD MA, TrueHD Atmos, FLAC).
- **Stremio (with ExoPlayer/VLC)**: Supports all common formats on Android TV and mobile.
- **Kodi**: Plays all raw remuxes and multi-channel audio tracks.
