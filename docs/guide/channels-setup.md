# 📡 Telegram Channels & Indexing

NoxMedia transforms your private and public Telegram channels into a unified, beautifully organized streaming library.

---

## 🔗 Adding Channels

NoxMedia supports multiple channel link formats:

1. **Public Channels**:
   - Enter the channel username: `@channelname` or `https://t.me/channelname`.
2. **Private Channels**:
   - Enter your invite link: `https://t.me/+inviteCode...` or `https://t.me/joinchat/...`.
   - Your logged-in Telegram account must already be a member of the channel.
3. **Channel ID**:
   - Enter the raw channel identifier (e.g. `-1001234567890`).

---

## 🧠 Smart Metadata Extraction

When you index a channel, NoxMedia runs an intelligent title parser that cleans up release names, stripping clutter while preserving key media tags:

- **Movie / Series Title & Year**: Parsed from file names, captions, or message headers.
- **Season & Episode Numbers**: Supports formats like `S01E05`, `1x05`, `E05`, or multi-episode packs.
- **Resolution & Video Quality**: Identifies `2160p (4K)`, `1080p (FHD)`, `720p (HD)`, `Remux`, `WEB-DL`, `BluRay`.
- **Codecs & HDR**: Flags `HEVC / H.265`, `AV1`, `HDR10`, `Dolby Vision (DV)`.
- **Audio Tracks**: Detects dual-audio / multi-audio languages (`Hindi`, `English`, `Japanese`, `5.1 AAC`, `Atmos`).

---

## 🌐 Metadata Providers

NoxMedia queries trusted databases to enrich your media with high-resolution posters, backdrops, cast information, and plot summaries:

| Media Type | Primary Provider | Fallback / Anime |
| :--- | :--- | :--- |
| **Movies** | TheMovieDatabase (TMDB) | File metadata & title search |
| **TV Shows** | TheTVDB & TMDB | Episode titles & air dates |
| **Anime** | Kitsu.io & AniList | Japanese / Romaji titles & season mapping |

---

## ✍️ Manual Match & Overrides

If a Telegram post has an obscure or unstandardized name:
1. Open NoxMedia and go to the **Index / Library** screen.
2. Locate the media item.
3. Tap the **Three Dots (⋮)** → **Manual Match**.
4. Search for the exact movie or show title on TMDB.
5. Select the correct entry. NoxMedia will re-link all episodes and artwork immediately.

---

## 🔄 Automatic Channel Updates

NoxMedia listens for new posts in your connected channels. When new episodes or movies are uploaded to Telegram, they are indexed in the background and become available in your streaming clients in seconds.
