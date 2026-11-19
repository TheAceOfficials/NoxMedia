# AI & Custom Catalogs

One of NoxMedia's core capabilities is **Dynamic Custom Shelves**. Rather than relying on generic genre lists, you can create tailored collections curated by AI or fine-tuned rules that reflect your exact viewing tastes.

---

## What are Custom Catalogs?

Custom catalogs are dynamic playlists that index and filter your Telegram library on the fly. 

Examples of popular shelves:
- *Murder Mystery & Dark Investigative*
- *Sci-Fi Space Operas & Cyberpunk*
- *A24 & Independent Cinema*
- *Peak Animation Anime*
- *4K Dolby Vision Reference Quality*

---

## AI-Powered Curation (Gemini & OpenRouter)

NoxMedia integrates directly with large language models to curate playlists from your indexed media:

1. In NoxMedia Settings, add your free **Google Gemini API Key** or **OpenRouter API Key**.
2. Navigate to **Catalogs** -> **Create New Shelf** -> **AI Assistant**.
3. Enter your prompt in plain language:
   > *"Find all psychological thrillers, neo-noir, and investigative murder mysteries with dark atmospheres and high suspense."*
4. NoxMedia's AI analyzes your indexed database, cross-references movie themes, plots, and tags, and builds your shelf automatically.

---

## Manual Rule-Based Filters

If you prefer exact control, you can create shelves based on explicit criteria:

- **Genre Matching**: Include or exclude genres (e.g., `Sci-Fi` + `Thriller`, exclude `Horror`).
- **Resolution & Quality**: Filter strictly for `4K UHD` or `HDR10 / Dolby Vision` files.
- **Year & Decades**: Create era-specific collections (e.g., *80s Action*, *90s Nostalgia*).
- **Ratings Threshold**: Minimum TMDB score (e.g., `Score >= 7.5`).
- **Keywords & Directors**: Filter by tags like `Christopher Nolan`, `Studio Ghibli`, `Time Travel`.

---

## Cross-Client Synchronization

Once a shelf is created in NoxMedia, it is immediately broadcast across your connected players:

- **Infuse**: Appears as a dedicated folder in **Files -> NoxMedia**. Star it to display it directly on your Apple TV or iPad Home Screen.
- **Vesper**: Appears inside your connected server libraries.
- **Nuvio**: Seamlessly integrates into your custom provider feeds on the home board.
