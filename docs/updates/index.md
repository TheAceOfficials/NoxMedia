# 🚀 What's New in NoxMedia

Stay up to date with the latest features, performance improvements, and fixes across NoxMedia releases.

---

## [v1.4.325 — Infuse Custom Shelves & 0ms Instant Loading](/updates/v1-4-325)
*November 19, 2026* &nbsp;•&nbsp; <span class="badge-pill badge-feature">Feature</span> <span class="badge-pill badge-perf">Performance</span>

- **🍎 Infuse Custom Catalogs**: AI-curated custom shelves now appear directly inside Infuse under `Files → NoxMedia`. Star any shelf to pin it as an Apple TV / iOS Home Screen favorite!
- **⚡ 0ms Catalog Caching**: Precomputed in-memory and disk cache for media groups and catalog filters. Eliminated the 1–2 minute delay when opening shelves in Infuse.
- **🎬 Smart Fallback Metadata**: Instant episode runtime and poster mapping without blocking synchronous HTTP network calls during folder browsing.

[Read Full Release Notes →](/updates/v1-4-325)

---

## [v1.4.320 — AI Catalog Curation with Gemini & OpenRouter](/updates/v1-4-320)
*November 17, 2026* &nbsp;•&nbsp; <span class="badge-pill badge-feature">Feature</span> <span class="badge-pill badge-fix">Enhancement</span>

- **🤖 AI Catalog Generation**: Describe your vibe in plain English (e.g. *"90s cyberpunk anime"* or *"high-stakes psychological thrillers"*) and NoxMedia automatically clusters matching titles.
- **🌐 OpenRouter & Gemini 1.5 Pro**: Full API key support with customizable prompt temperature and reasoning depth.
- **🔍 Multi-rule Filtering**: Combine AI prompts with exact resolution filters (e.g. 4K HDR Remux only).

[Read Full Release Notes →](/updates/v1-4-320)

---

## [v1.4.318 — Precision Runtime & TMDB Fallbacks](/updates/v1-4-318)
*November 15, 2026* &nbsp;•&nbsp; <span class="badge-pill badge-fix">Bug Fix</span> <span class="badge-pill badge-perf">Performance</span>

- **⏱️ Accurate Media Runtime**: Resolves exact episode/movie runtime from Telegram metadata and TMDB episode indices.
- **🔄 Smart Title Disambiguation**: Resolved edge cases in multi-part anime OVA titles and anime season naming.
- **🛡️ Android Foreground Reliability**: Hardened background server worker against Android 14/15 battery sleep cycles.

[Read Full Release Notes →](/updates/v1-4-318)
