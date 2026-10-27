import re
from typing import Optional, Dict, Any, Tuple, List

_VIDEO_EXTENSIONS = r'mkv|mp4|avi|ts|m4v|mov|wmv|webm|flv|m2ts|mpg|mpeg'
_ARCHIVE_EXTENSIONS = r'zip|rar|7z'
_ALL_MEDIA_EXTENSIONS = rf'{_VIDEO_EXTENSIONS}|{_ARCHIVE_EXTENSIONS}'

# Pattern 1: e.g. .mkv.001, .mp4.02, .rar.003, or bare .001 at end
_SPLIT_PART_PATTERN_1 = re.compile(rf'(?i)(?:\.({_ALL_MEDIA_EXTENSIONS}))?\.(\d{{2,4}})$')
# Pattern 2: e.g. .part001.mkv, .part1.mkv, _part01.mp4, - part 2.mkv, .pt01.mkv, [Part 1].mkv, .cd1.avi, .disc2.mkv
_SPLIT_PART_PATTERN_2 = re.compile(rf'(?i)[\s._\-\(\[]*(?:part|pt|cd|disc)[\s._\-]*(\d{{1,4}})[\s._\-\)\]]*\.({_ALL_MEDIA_EXTENSIONS})$')

_RELEASE_GROUP_RE = re.compile(r"\[[^\]]{1,40}\]")

# Resolutions
_RES_PATTERNS = [
    (re.compile(r"(?i)\b(2160p|4k|uhd)\b"), "4K 2160p"),
    (re.compile(r"(?i)\b(1080p|fhd)\b"), "1080p"),
    (re.compile(r"(?i)\b(720p|hd)\b"), "720p"),
    (re.compile(r"(?i)\b(480p|sd)\b"), "480p"),
    (re.compile(r"(?i)\b(360p|240p)\b"), "360p"),
]

# HDR / Visual formats
_VISUAL_RE = re.compile(r"(?i)\b(dolby\s*vision|dv|dovi|hdr10\+|hdr10|hlg|hdr|10bit|8bit)\b")

# Video codecs
_CODEC_RE = re.compile(r"(?i)\b(hevc|x265|h\.?265|avc|x264|h\.?264|av1|vp9)\b")

# Sources
_SOURCE_RE = re.compile(r"(?i)\b(bluray|blu-ray|bdrip|brrip|web-?dl|webrip|hdtv|dvdrip|remux)\b")

# Audio formats & channels
_AUDIO_CODEC_RE = re.compile(r"(?i)\b(truehd|atmos|ddp(?:\s*5\.1|\s*2\.0|\s*7\.1)?|dd\+?|eac3|ac3|dts-hd|dts|aac|flac)\b")
_AUDIO_CHANNELS_RE = re.compile(r"(?i)\b(7\.1|5\.1|2\.0)\b")

_LANGUAGES_MAP = {
    "hin": "Hin", "hindi": "Hin",
    "tam": "Tam", "tamil": "Tam",
    "tel": "Tel", "telugu": "Tel",
    "mal": "Mal", "malayalam": "Mal",
    "kan": "Kan", "kannada": "Kan",
    "eng": "Eng", "english": "Eng",
    "jap": "Jap", "japanese": "Jap", "jpn": "Jap",
    "kor": "Kor", "korean": "Kor",
    "spa": "Spa", "spanish": "Spa",
    "fre": "Fre", "french": "Fre",
    "ger": "Ger", "german": "Ger",
    "ita": "Ita", "italian": "Ita",
    "rus": "Rus", "russian": "Rus",
    "chi": "Chi", "chinese": "Chi",
    "multi": "Multi", "dual": "Dual"
}


# Season & Episode patterns
_SXXEXX_RE = re.compile(r"(?i)(?<![a-zA-Z0-9])(?:s|season\s*)(\d{1,2})[\s._-]*(?:e|ep|episode\s*)(\d{1,4})(?:v\d+)?(?![a-zA-Z0-9])")
_NXNN_RE = re.compile(r"(?i)(?<![a-zA-Z0-9])(\d{1,2})x(\d{2,4})(?:v\d+)?(?![a-zA-Z0-9])")
_SEASON_ONLY_RE = re.compile(r"(?i)(?<![a-zA-Z0-9])(?:s|season\s*)(\d{1,2})(?![a-zA-Z0-9])")
_EPISODE_ONLY_RE = re.compile(r"(?i)(?<![a-zA-Z0-9])(?:e|ep|episode\s*|#\s*)(\d{1,4})(?:v\d+)?(?![a-zA-Z0-9])")

# Anime Absolute Episode: e.g. "One Piece - 1120" or "One Piece 1120"
_ANIME_ABS_RE = re.compile(r"(?i)\s*[-–—]\s*(\d{2,4})(?:\s|$|\[|\()")
_YEAR_RE = re.compile(r"(?:^|[\s._\-\(\[])((?:19|20)\d{2})(?:[\s._\-\)\]]|$)")

# Bracket tags: quality, audio, codec, release group, CRC
_TAG_BRACKET_RE = re.compile(r'[\(\[]\s*(?:1080p|720p|480p|360p|2160p|4k|uhd|bd|bluray|web-?rip|web-?dl|dual|multi|flac|hevc|x264|x265|ddp[^\]\)]*|atmos|hdr|dv|h\.?264|h\.?265|aac)[^\)\]]*[\)\]]', re.I)
_REL_RE = re.compile(r'[\(\[](?:MomStealer|Anipulse|Judas|SubsPlease|Erai|YTS|YIFY|PSA|TGx|Pahe|GalaxyRG|GalaxyTV|Toonworld4all|QxR|Vyndros|EMBER|Tigole|SAMPA|KaKa|rarbg|SEV|FLUX|MeGusta|ION10|EZTV|Vegamovies|KatmovieHD)[^\)\]]*[\)\]]', re.I)
_CRC_RE = re.compile(r'\[[0-9A-Fa-f]{8}\]')

_MEDIA_TAGS_RE = re.compile(
    r"(?i)\b(2160p|1080p|720p|480p|4k|uhd|hdr|hevc|x265|x264|h\.?265|h\.?264|bluray|blu-ray|bdrip|web-?dl|webrip|dvdrip|remux|dual|multi|hindi|english|flac|aac|dts|atmos|ddp|dd\+|truehd|hq)\b|"
    r"(?<![a-zA-Z0-9])(?:s|season\s*)\d{1,2}|"
    r"(?<![a-zA-Z0-9])(?:e|ep|episode\s*|#\s*)\d{1,4}|"
    r"(?<![a-zA-Z0-9])\d{1,2}x\d{2,4}|"
    r"(?:^|[\s._\-\(\[])(?:19|20)\d{2}(?:[\s._\-\)\]]|$)|"
    r"(?:MomStealer|Anipulse|Judas|SubsPlease|Erai|YTS|YIFY|PSA|TGx|Pahe|GalaxyRG|GalaxyTV|Toonworld4all|QxR|Vyndros|EMBER|Tigole|SAMPA|KaKa|Vegamovies|KatmovieHD)"
)

def is_media_like(text: str) -> bool:
    """Checks whether a string contains identifiable media indicators (season, episode, year, resolution, codecs, release groups)."""
    if not text:
        return False
    return bool(_MEDIA_TAGS_RE.search(text))

def clean_caption_text(caption: str) -> str:
    """
    Cleans raw Telegram message caption:
    - Strips URLs (http, https, t.me, tg://, www)
    - Strips Telegram channel handles and mentions (@username)
    - Strips emojis and decorative pictographs
    - Strips spam/promo lines (join channel, download link, etc.) and decorative divider lines
    - Strips leading labels (Title:, File Name:, Name:, etc.)
    Returns cleaned, normalized caption string or "" if no media info remains.
    """
    if not caption or not caption.strip():
        return ""

    # 1. Strip URLs and @usernames first
    cap = re.sub(r"https?://\S+|t\.me/\S+|tg://\S+|www\.\S+", " ", caption)
    cap = re.sub(r"(?:^|\s)@[\w_]+", " ", cap)

    lines = []
    video_ext_re = re.compile(r"(?i)\.(?:mkv|mp4|avi|ts|webm|m4v|mov|flv|wmv)(?:\.\d{2,4})?$")

    for raw_line in cap.splitlines():
        # Remove emojis and decorative unicode symbols
        line = re.sub(r"[\U00010000-\U0010ffff\u2600-\u27bf\u2300-\u23ff\u2b50\u2b55\u200d\ufe0f]", " ", raw_line)
        line = re.sub(r"^(?:📁|🎬|🎥|▶️|💾|🔹|🔸|▪️|▫️|•|[-*#])+\s*", "", line).strip()

        if not line:
            continue
        # Divider lines (e.g. -------, =======, ━━━━━━━, ••••)
        if re.match(r"^[\s\-_=*#~━─—–•·:|]+$", line):
            continue
        # Promotional / spam lines
        if re.search(r"(?i)^\s*(?:join|subscribe|forwarded\s*from|channel|download\s*link|how\s*to\s*download|backup\s*channel|contact|admin|owner|direct\s*link|share\s*link|links?)\b", line):
            continue
        # Remove label prefixes (Title:, File Name:, Name:, etc.)
        cleaned = re.sub(r"(?i)^(?:file(?:\s*name)?|name|title)\s*[:：\-]\s*", "", line).strip()
        if not cleaned:
            continue
        # Must have at least 2 alphanumeric characters
        if not re.search(r"[a-zA-Z0-9]{2,}", cleaned):
            continue
        lines.append(cleaned)

    if not lines:
        return ""

    # Check if a single line is a complete media file name with video extension and media tags
    for l in lines:
        if video_ext_re.search(l) and re.search(r"(?i)(?:s\d+|season|ep|episode|\b(?:19|20)\d{2}\b)", l):
            return l

    # Otherwise join multi-line captions into a single line
    joined = " ".join(lines)
    return re.sub(r"\s+", " ", joined).strip()

def parse_split_part(filename: str) -> Optional[Tuple[str, int]]:
    """
    Detects all variations of split parts:
    - .part001.mkv, .part1.mkv, [Part 1].mkv, _part01.mp4, .pt01.mkv, .cd1.avi
    - .mkv.001, .mp4.002, .rar.001, .001
    Returns (normalized_clean_base_filename, part_number) or None.
    """
    if not filename:
        return None
    m2 = _SPLIT_PART_PATTERN_2.search(filename)
    if m2:
        start = m2.start()
        part_num = int(m2.group(1))
        ext = m2.group(2)
        clean_name = filename[:start] + "." + ext
        return clean_name, part_num
    m1 = _SPLIT_PART_PATTERN_1.search(filename)
    if m1:
        start = m1.start()
        ext = m1.group(1) or "mkv"
        part_num = int(m1.group(2))
        clean_name = filename[:start] + "." + ext
        return clean_name, part_num
    return None

def extract_best_filename(filename: str, caption: str = "") -> str:
    """
    Chooses the best filename between Telegram's document attribute filename and message caption.
    Priority 1: Message caption (cleaned of emojis, links, spam), since users often fix/edit
    captions when Telegram filenames are obfuscated/random, or bots write complete release names in captions.
    Priority 2: Telegram document attribute filename (as fallback if caption is missing, just a link, or empty).
    """
    fn = (filename or "").strip()
    cap = (caption or "").strip()

    cleaned_cap = clean_caption_text(cap)

    if cleaned_cap:
        cap_is_media = is_media_like(cleaned_cap)
        fn_is_media = is_media_like(fn)
        # Caption is Priority 1 if media-like or if fn is generic/obfuscated/incomplete
        if cap_is_media or not fn_is_media:
            has_ext = bool(re.search(r"(?i)\.(mkv|mp4|avi|ts|webm|m4v|mov|rar|zip|7z)(?:\.\d{2,4})?$", cleaned_cap))
            if not has_ext and fn:
                m_ext = re.search(r"\.(mkv|mp4|avi|ts|webm|m4v|mov|rar|zip|7z)(?:\.\d{2,4})?$", fn, re.I)
                if m_ext:
                    cleaned_cap = cleaned_cap + m_ext.group(0)
                else:
                    cleaned_cap = cleaned_cap + ".mp4"
            elif not has_ext:
                cleaned_cap = cleaned_cap + ".mp4"
            return cleaned_cap

    return fn

def parse_media_filename(filename: str, caption: str = "", is_anime: bool = False) -> Dict[str, Any]:
    """
    Parses a Telegram file name and optional caption to extract clean metadata.
    Detects movies, TV series, anime, split files, video codecs, audio tracks, etc.
    """
    raw_name = extract_best_filename(filename, caption)
        
    split_info = parse_split_part(raw_name)
    is_split = split_info is not None
    part_number = split_info[1] if split_info else 1
    target_name = split_info[0] if split_info else raw_name
    
    # Strip extension
    base_no_ext = re.sub(r"\.[a-zA-Z0-9]{2,4}$", "", target_name)
    
    # Extract Year
    year = None
    year_match = _YEAR_RE.search(base_no_ext) or (caption and _YEAR_RE.search(caption))
    if year_match:
        year = year_match.group(1)

    # Visual tags (HDR, DV, 10bit)
    visuals = set()
    for v in _VISUAL_RE.findall(base_no_ext + " " + caption):
        v_upper = v.upper().replace("DOLBY VISION", "DV")
        visuals.add(v_upper)
        
    # Video codecs
    codecs = set()
    for c in _CODEC_RE.findall(base_no_ext + " " + caption):
        codecs.add(c.upper().replace("X265", "HEVC").replace("H.265", "HEVC").replace("X264", "AVC").replace("H.264", "AVC"))
        
    # Audio codecs
    audio_tags = set()
    for a in _AUDIO_CODEC_RE.findall(base_no_ext + " " + caption):
        audio_tags.add(a.upper().strip())
        
    # Audio channels
    channels = set()
    for ch in _AUDIO_CHANNELS_RE.findall(base_no_ext + " " + caption):
        channels.add(ch)
        
    # Languages detected
    languages = set()
    words = re.split(r"[\s._\-+\[\](),]+", (base_no_ext + " " + caption).lower())
    for w in words:
        if w in _LANGUAGES_MAP:
            languages.add(_LANGUAGES_MAP[w])

    # Quality / Resolution
    quality = "1080p"
    for pat, label in _RES_PATTERNS:
        if pat.search(base_no_ext + " " + caption):
            quality = label
            break

    # Clean working name for title and season/episode parsing
    cleaned_work = _CRC_RE.sub("", base_no_ext)
    cleaned_work = _TAG_BRACKET_RE.sub("", cleaned_work)
    cleaned_work = _REL_RE.sub("", cleaned_work)
    
    # Strip leading uploader/channel brackets if actual title follows (e.g. [Toonworld4all] Dexters Lab...)
    leading_bracket = re.match(r"^(\[[^\]]+\]|\([^\)]+\)|【[^】]+】|\{[^\}]+\})\s*", cleaned_work)
    if leading_bracket:
        rest = cleaned_work[leading_bracket.end():]
        if re.search(r"[a-zA-Z]{2,}", rest):
            cleaned_work = rest

    # Strip trailing release group brackets
    cleaned_work = re.sub(r"\s*[\(\[][A-Za-z0-9_\-\.\s]{1,30}[\)\]]\s*$", "", cleaned_work)

    # Extract Season & Episode
    season = None
    episode = None
    media_type = "series" if is_anime else "movie"
    clean_title = cleaned_work

    s_e_match = _SXXEXX_RE.search(cleaned_work)
    if s_e_match:
        season = int(s_e_match.group(1))
        episode = int(s_e_match.group(2))
        media_type = "series"
        p = cleaned_work[:s_e_match.start()].strip(" ._-\t[]()")
        s = cleaned_work[s_e_match.end():].strip(" ._-\t[]()")
        clean_title = p if re.search(r"[a-zA-Z]{2,}", p) else s
    else:
        nxnn = _NXNN_RE.search(cleaned_work)
        if nxnn:
            season = int(nxnn.group(1))
            episode = int(nxnn.group(2))
            media_type = "series"
            p = cleaned_work[:nxnn.start()].strip(" ._-\t[]()")
            s = cleaned_work[nxnn.end():].strip(" ._-\t[]()")
            clean_title = p if re.search(r"[a-zA-Z]{2,}", p) else s
        else:
            # Bracketed episode e.g. [12 - The Ramparts of Ice]
            b_ep = re.search(r"^\[\s*(\d{1,4})\s*[-–—]\s*([^\]]+)\]", cleaned_work)
            if b_ep:
                episode = int(b_ep.group(1))
                season = 1
                media_type = "series"
                clean_title = b_ep.group(2)
            else:
                # Anime hyphen episode: "Dandadan - 02" or "BTYBWTC 08 -"
                a_ep = re.search(r"^([A-Za-z0-9\s._\-+]+?)\s*[-–—]\s*0*(\d{1,4})(?:\s|$|\[|\()", cleaned_work)
                if a_ep:
                    clean_title = a_ep.group(1).strip()
                    episode = int(a_ep.group(2))
                    season = 1
                    media_type = "series"
                else:
                    # Title followed by number: "BTYBWTC 08"
                    t_ep = re.search(r"^([A-Za-z0-9\s._\-+]+?)\s+(?:ep|episode|e)?0*(\d{1,4})(?:\s*[-–—]|\s*$)", cleaned_work)
                    if t_ep:
                        clean_title = t_ep.group(1).strip()
                        episode = int(t_ep.group(2))
                        season = 1
                        media_type = "series"
                    else:
                        s_only = _SEASON_ONLY_RE.search(cleaned_work)
                        ep_only = _EPISODE_ONLY_RE.search(cleaned_work)
                        if s_only and ep_only:
                            season = int(s_only.group(1))
                            episode = int(ep_only.group(1))
                            media_type = "series"
                            first_pos = min(s_only.start(), ep_only.start())
                            p = cleaned_work[:first_pos].strip(" ._-\t[]()")
                            clean_title = p if re.search(r"[a-zA-Z]{2,}", p) else cleaned_work[max(s_only.end(), ep_only.end()):].strip(" ._-\t[]()")
                        elif ep_only:
                            episode = int(ep_only.group(1))
                            season = 1
                            media_type = "series"
                            p = cleaned_work[:ep_only.start()].strip(" ._-\t[]()")
                            clean_title = p if re.search(r"[a-zA-Z]{2,}", p) else cleaned_work[ep_only.end():].strip(" ._-\t[]()")
                        elif is_anime:
                            bare_nums = re.findall(r"(?:^|[\s\-_])0*(\d{1,4})(?:[\s\-_]|$)", cleaned_work)
                            if bare_nums:
                                try:
                                    episode = int(bare_nums[-1])
                                    season = 1
                                    media_type = "series"
                                    clean_title = re.sub(rf"(?:^|[\s\-_])0*{episode}(?:[\s\-_]|$).*$", "", cleaned_work)
                                except ValueError:
                                    pass

    # Strip year from clean_title if present
    y_in_title = _YEAR_RE.search(clean_title)
    if y_in_title:
        clean_title = clean_title[:y_in_title.start()]

    # Cut off at resolution / quality indicators or sources
    clean_title = re.sub(r"(?i)\b(2160p|1080p|720p|480p|4k|uhd|hdr|hevc|x264|x265|aac|dts|bluray|web-?dl|web-?rip|proper|repack|hq)\b.*$", "", clean_title)

    # Clean delimiters
    clean_title = re.sub(r"[\._\(\)\[\]\-]+", " ", clean_title).strip()
    clean_title = re.sub(r"\s+", " ", clean_title)

    # Ensure clean_title is never empty
    if not clean_title:
        clean_title = re.sub(r"[\._\(\)\[\]\-]+", " ", base_no_ext).strip()
    
    # Build quality badge string for Stremio stream items
    badge_parts = [quality]
    if "DV" in visuals or "DOVI" in visuals or "DOLBY VISION" in visuals:
        badge_parts.append("DV")
    if "HLG" in visuals:
        badge_parts.append("HLG")
    elif any(h in visuals for h in ("HDR", "HDR10", "HDR10+")):
        badge_parts.append("HDR")
    if "10BIT" in visuals:
        badge_parts.append("10bit")
    if codecs:
        badge_parts.append(list(codecs)[0])
    if languages:
        badge_parts.append("/".join(sorted(languages)))
        
    quality_badge = " • ".join(badge_parts)

    return {
        "title": clean_title,
        "type": media_type,
        "year": year,
        "season": season,
        "episode": episode,
        "quality": quality,
        "quality_badge": quality_badge,
        "is_split": is_split,
        "part_number": part_number,
        "visuals": list(visuals),
        "codecs": list(codecs),
        "languages": list(languages),
        "clean_base_name": target_name
    }
