# Releases & Changelog

Follow updates, performance optimizations, and platform releases for NoxMedia.

---

## [v1.4.325 - Infuse & Vesper Custom Shelves & Zero-Latency Caching](/updates/v1-4-325)
*November 19, 2026* &nbsp;&bull;&nbsp; 
<span class="badge-item badge-new"><svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M15 14c.2-1 .7-1.7 1.5-2.5 1-.9 1.5-2.2 1.5-3.5A6 6 0 0 0 6 8c0 1 .2 2.2 1.5 3.5.7.7 1.3 1.5 1.5 2.5"/><path d="M9 18h6"/><path d="M10 22h4"/></svg>New</span>
<span class="badge-item badge-improved"><svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="23 6 13.5 15.5 8.5 10.5 1 18"/><polyline points="17 6 23 6 23 12"/></svg>Improved</span>

- **Infuse & Vesper Custom Catalogs**: AI-curated custom shelves now expose directly within Infuse and Vesper. Star shelves to pin them to Apple TV and iOS Home Screens.
- **Zero-Latency In-Memory Caching**: Precomputed media groups and catalog filters eliminate previous directory load delays.
- **Direct Runtime Fallbacks**: Episode runtime and poster mappings resolve without blocking synchronous HTTP queries during folder exploration.

[Read Full Release Notes](/updates/v1-4-325)

---

## [v1.4.320 - AI Catalog Curation via Gemini & OpenRouter](/updates/v1-4-320)
*November 17, 2026* &nbsp;&bull;&nbsp; 
<span class="badge-item badge-new"><svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M15 14c.2-1 .7-1.7 1.5-2.5 1-.9 1.5-2.2 1.5-3.5A6 6 0 0 0 6 8c0 1 .2 2.2 1.5 3.5.7.7 1.3 1.5 1.5 2.5"/><path d="M9 18h6"/><path d="M10 22h4"/></svg>New</span>
<span class="badge-item badge-improved"><svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="23 6 13.5 15.5 8.5 10.5 1 18"/><polyline points="17 6 23 6 23 12"/></svg>Improved</span>

- **AI Prompt Shelf Builder**: Describe themes in plain English to automatically assemble cohesive collections.
- **Gemini & OpenRouter Engines**: Configure personal API keys with customizable generation temperature.
- **Granular Filter Combinations**: Mix AI suggestions with strict 4K HDR or Remux resolution constraints.

[Read Full Release Notes](/updates/v1-4-320)

---

## [v1.4.318 - Runtime Precision & Metadata Fallbacks](/updates/v1-4-318)
*November 15, 2026* &nbsp;&bull;&nbsp; 
<span class="badge-item badge-fixed"><svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="m8 2 1.88 1.88"/><path d="M14.12 3.88 16 2"/><path d="M9 7.13v-1a3.003 3.003 0 1 1 6 0v1"/><path d="M12 20c-3.3 0-6-2.7-6-6v-3a4 4 0 0 1 4-4h4a4 4 0 0 1 4 4v3c0 3.3-2.7 6-6 6"/><path d="M12 20v-9"/><path d="M6.53 9C4.6 8.8 3 7.1 3 5"/><path d="M6 13H2"/><path d="M3 21c0-2.1 1.7-3.9 3.8-4"/><path d="M20.97 5c0 2.1-1.6 3.8-3.5 4"/><path d="M22 13h-4"/><path d="M17.2 17c2.1.1 3.8 1.9 3.8 4"/></svg>Fixed</span>
<span class="badge-item badge-improved"><svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="23 6 13.5 15.5 8.5 10.5 1 18"/><polyline points="17 6 23 6 23 12"/></svg>Improved</span>

- **Accurate Runtime Fallback**: Media durations without embedded tags resolve from TMDB database listings.
- **Anime Title Sanitization**: Strips multi-language noise while preserving correct season and OVA indices.
- **Foreground WakeLocks**: Stabilizes background server operation during aggressive Android OS sleep cycles.

[Read Full Release Notes](/updates/v1-4-318)
