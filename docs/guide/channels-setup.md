# Telegram Channels & Indexing

NoxMedia transforms your private and public Telegram channels into a unified, cleanly structured streaming library.

---

## Adding Channels

NoxMedia supports multiple channel link formats:

1. **Public Channels**:
   - Enter the channel username: `@channelname` or `https://t.me/channelname`.
2. **Private Channels**:
   - Enter your invite link: `https://t.me/+inviteCode...` or `https://t.me/joinchat/...`.
   - Your logged-in Telegram account must already be a member of the channel.
3. **Channel ID**:
   - Enter the raw channel identifier (e.g., `-1001234567890`).

---

## Smart Metadata Extraction

When you index a channel, NoxMedia runs an intelligent title parser that cleans release names, stripping spam while extracting essential media tags:

- **Title & Release Year**: Parsed from file names, captions, or message headers.
- **Season & Episode Numbers**: Supports formats like `S01E05`, `1x05`, `E05`, or multi-episode archives.
- **Resolution & Video Quality**: Identifies `2160p (4K)`, `1080p (FHD)`, `720p (HD)`, `Remux`, `WEB-DL`, `BluRay`.
- **Codecs & Dynamic Range**: Detects `HEVC / H.265`, `AV1`, `HDR10`, `Dolby Vision (DV)`.
- **Audio Tracks**: Recognizes dual-audio and multi-language streams (`5.1 AAC`, `DTS-HD`, `Dolby Atmos`).

---

## Metadata Providers

NoxMedia queries trusted databases to enrich your media with posters, backdrops, cast listings, and synopses:

| Media Type | Primary Provider | Fallback / Anime |
| :--- | :--- | :--- |
| **Movies** | TheMovieDatabase (TMDB) | File metadata & title search |
| **TV Shows** | TheTVDB & TMDB | Episode titles & air dates |
| **Anime** | Kitsu.io & AniList | Romaji titles & seasonal episode mapping |

---

## Manual Match & Title Overrides

If a Telegram post has an obscure or unstandardized filename:
1. Open NoxMedia and navigate to the **Library / Index** screen.
2. Locate the media item.
3. Select **Manual Match**.
4. Search for the exact movie or television show title on TMDB.
5. Select the correct entry. NoxMedia will re-link all episodes and artwork immediately.

---

## Automatic Channel Updates

NoxMedia listens for new posts in your connected channels. When new episodes or movies are uploaded to Telegram, they are indexed in the background and become available in your streaming clients within seconds.
