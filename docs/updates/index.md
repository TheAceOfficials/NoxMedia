# Releases & Changelog

Stay up to date with the latest features, performance improvements, and fixes across NoxMedia releases.

---

## [v1.4.325 - Infuse & Vesper Custom Shelves & Instant Caching](/updates/v1-4-325)
*November 19, 2026* &nbsp;•&nbsp; <span class="badge-pill badge-feature">Feature</span> <span class="badge-pill badge-perf">Performance</span>

- **Infuse & Vesper Custom Catalogs**: AI-curated custom shelves now appear directly inside Infuse under `Files -> NoxMedia` and within Vesper libraries. Star any shelf to pin it as a Home screen favorite.
- **Zero-Latency Catalog Caching**: Precomputed in-memory and disk cache for media groups and catalog filters. Eliminated previous delays when opening large catalogs.
- **Direct Runtime Fallbacks**: Episode runtime and poster mapping resolved instantly without blocking synchronous HTTP network calls during folder browsing.

[Read Full Release Notes](/updates/v1-4-325)

---

## [v1.4.320 - AI Catalog Curation with Gemini & OpenRouter](/updates/v1-4-320)
*November 17, 2026* &nbsp;•&nbsp; <span class="badge-pill badge-feature">Feature</span> <span class="badge-pill badge-fix">Enhancement</span>

- **AI Catalog Generation**: Describe desired shelf vibes in plain language (such as *"90s cyberpunk anime"* or *"high-stakes psychological thrillers"*) and NoxMedia automatically clusters matching titles.
- **OpenRouter & Gemini 1.5 Pro**: Direct API key support with configurable prompt temperature.
- **Multi-Rule Filtering**: Combine AI prompts with exact resolution filters (e.g., 4K HDR Remux only).

[Read Full Release Notes](/updates/v1-4-320)

---

## [v1.4.318 - Precision Runtime & Metadata Fallbacks](/updates/v1-4-318)
*November 15, 2026* &nbsp;•&nbsp; <span class="badge-pill badge-fix">Bug Fix</span> <span class="badge-pill badge-perf">Performance</span>

- **Accurate Media Runtime**: Resolves exact episode and movie duration from Telegram metadata and TMDB episode indices.
- **Smart Title Disambiguation**: Resolved edge cases in multi-part anime OVA titles and anime season naming.
- **Android Foreground Reliability**: Hardened background server worker against Android 14 and 15 battery sleep cycles.

[Read Full Release Notes](/updates/v1-4-318)
