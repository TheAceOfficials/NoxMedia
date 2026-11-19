---
layout: home

hero:
  name: "NoxMedia"
  text: "Personal Media Server & Cloud Streaming Hub"
  tagline: "High-performance streaming engine bridging Telegram channels directly to Infuse, Stremio, Nuvio, and Android TV."
  image:
    src: /logo.png
    alt: NoxMedia Logo
  actions:
    - theme: brand
      text: 🚀 Get Started
      link: /guide/getting-started
    - theme: alt
      text: 🍎 Infuse Setup
      link: /clients/infuse
    - theme: alt
      text: ⬇️ Download APK
      link: https://github.com/TheAceOfficials/NoxMedia/releases/latest

features:
  - title: 🍎 Native Infuse Direct Mode
    details: Complete Jellyfin/Emby API emulation enabling seamless Apple TV, iPad, iPhone, and Mac streaming with 100% native DirectPlay.
    link: /clients/infuse
  - title: 🍿 Instant Stremio & Nuvio Addon
    details: Precomputed 0ms cache delivering zero-lag catalog loading and multi-audio stream selection on any Stremio-compatible client.
    link: /clients/stremio-nuvio
  - title: 🪄 AI & Custom Shelves
    details: Curate custom catalogs using AI prompts powered by Gemini & OpenRouter, or build custom filters by studio, 4K HDR, and actors.
    link: /guide/ai-catalogs
  - title: ⚡ 3.5x High-Throughput Engine
    details: Parallelized chunk prefetching pipeline delivering 70% lower latency on massive 4K Dolby Vision, IMAX, and Remux files.
    link: /updates/v1-4-325
  - title: ☁️ True Cloud Sync & Standby
    details: Seamlessly synchronize watch progress across all devices with battery-optimized 24/7 background standby server mode.
    link: /guide/cloud-sync
  - title: 🎨 Spatial Posters & Rich Metadata
    details: Automatic TMDB, TVDB, and Kitsu artwork enrichment with clear logo treatments, 16:9 episode accordions, and cast info.
    link: /guide/channels-setup
---

<div class="screenshot-frame">
  <img src="/banner.png" alt="NoxMedia Showcase Banner" />
  <div class="screenshot-caption">
    <span>🔥</span> <b>NoxMedia Ecosystem:</b> Android Local Server hosting for Apple TV Infuse, Stremio, Nuvio, and Android TV.
  </div>
</div>

<style>
:root {
  --vp-home-hero-name-color: transparent;
  --vp-home-hero-name-background: -webkit-linear-gradient(120deg, #ff5722 30%, #f59e0b);
}
</style>
