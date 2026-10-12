import re
from typing import Optional, Dict, Any, Tuple, List

_VIDEO_EXTENSIONS = r'mkv|mp4|avi|ts|m4v|mov|wmv|webm|flv|m2ts|mpg|mpeg'
_ARCHIVE_EXTENSIONS = r'zip|rar'
_SPLIT_PART_PATTERN = re.compile(rf'(?i)\.({_VIDEO_EXTENSIONS}|{_ARCHIVE_EXTENSIONS})\.(\d{{2,3}})(?=$|\D)')

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
_VISUAL_RE = re.compile(r"(?i)\b(dolby\s*vision|dv|hdr10\+|hdr10|hdr|10bit|8bit)\b")

# Video codecs
_CODEC_RE = re.compile(r"(?i)\b(hevc|x265|h\.?265|avc|x264|h\.?264|av1|vp9)\b")

# Sources
_SOURCE_RE = re.compile(r"(?i)\b(bluray|blu-ray|bdrip|brrip|web-?dl|webrip|hdtv|dvdrip|remux)\b")

# Audio formats & channels
_AUDIO_CODEC_RE = re.compile(r"(?i)\b(truehd|atmos|ddp(?:\s*5\.1|\s*2\.0|\s*7\.1)?|dd\+?|eac3|ac3|dts-hd|dts|aac|flac)\b")
_AUDIO_CHANNELS_RE = re.compile(r"(?i)\b(7\.1|5\.1|2\.0)\b")

_LANGUAGES_MAP = {
    "hin": "Hindi", "hindi": "Hindi",
    "tam": "Tamil", "tamil": "Tamil",
    "tel": "Telugu", "telugu": "Telugu",
    "mal": "Malayalam", "malayalam": "Malayalam",
    "kan": "Kannada", "kannada": "Kannada",
    "eng": "English", "english": "English",
    "jap": "Japanese", "japanese": "Japanese", "jpn": "Japanese",
    "kor": "Korean", "korean": "Korean",
    "spa": "Spanish", "spanish": "Spanish",
    "fre": "French", "french": "French",
    "ger": "German", "german": "German",
    "ita": "Italian", "italian": "Italian",
    "multi": "Multi Audio", "dual": "Dual Audio"
}

# Season & Episode patterns
_SXXEXX_RE = re.compile(r"(?i)\b(?:s|season\s*)(\d{1,2})[\s._-]*(?:e|ep|episode\s*)(\d{1,3})\b")
_NXNN_RE = re.compile(r"(?i)\b(\d{1,2})x(\d{2,3})\b")
_SEASON_ONLY_RE = re.compile(r"(?i)\b(?:s|season\s*)(\d{1,2})\b")
_EPISODE_ONLY_RE = re.compile(r"(?i)\b(?:e|ep|episode\s*)(\d{1,4})\b")

# Anime Absolute Episode: e.g. "One Piece - 1120" or "One Piece 1120"
_ANIME_ABS_RE = re.compile(r"(?i)\s*[-–—]\s*(\d{2,4})(?:\s|$|\[|\()")
_YEAR_RE = re.compile(r"(?:^|[\s._\-\(\[])((?:19|20)\d{2})(?:[\s._\-\)\]]|$)")

def parse_split_part(filename: str) -> Optional[Tuple[str, int]]:
    """
    Detects split parts like .mkv.001 or .rar.002.
    Returns (normalized_clean_base_filename, part_number) or None.
    """
    if not filename:
        return None
    m = _SPLIT_PART_PATTERN.search(filename)
    if m:
        start = m.start()
        ext = m.group(1)
        part_num = int(m.group(2))
        clean_name = filename[:start] + "." + ext
        return clean_name, part_num
    return None

def parse_media_filename(filename: str, caption: str = "") -> Dict[str, Any]:
    """
    Parses a Telegram file name and optional caption to extract clean metadata.
    Detects movies, TV series, anime, split files, video codecs, audio tracks, etc.
    """
    raw_name = (filename or "").strip()
    if not raw_name and caption:
        raw_name = caption.strip().split("\n")[0]
        
    split_info = parse_split_part(raw_name)
    is_split = split_info is not None
    part_number = split_info[1] if split_info else 1
    target_name = split_info[0] if split_info else raw_name
    
    # Strip extension
    base_no_ext = re.sub(r"\.[a-zA-Z0-9]{2,4}$", "", target_name)
    
    # Extract Year
    year = None
    year_match = _YEAR_RE.search(base_no_ext)
    if year_match:
        year = year_match.group(1)
        
    # Extract Season & Episode
    season = None
    episode = None
    media_type = "movie"
    anime_match = None
    
    s_e_match = _SXXEXX_RE.search(base_no_ext)
    if s_e_match:
        season = int(s_e_match.group(1))
        episode = int(s_e_match.group(2))
        media_type = "series"
    else:
        nxnn = _NXNN_RE.search(base_no_ext)
        if nxnn:
            season = int(nxnn.group(1))
            episode = int(nxnn.group(2))
            media_type = "series"
        else:
            s_only = _SEASON_ONLY_RE.search(base_no_ext)
            ep_only = _EPISODE_ONLY_RE.search(base_no_ext)
            if s_only and ep_only:
                season = int(s_only.group(1))
                episode = int(ep_only.group(1))
                media_type = "series"
            elif ep_only:
                episode = int(ep_only.group(1))
                season = 1
                media_type = "series"
            else:
                # Check anime absolute episode
                anime_match = _ANIME_ABS_RE.search(base_no_ext)
                if anime_match:
                    episode = int(anime_match.group(1))
                    season = 1
                    media_type = "series"
                    
    # Quality / Resolution
    quality = "1080p"
    for pat, label in _RES_PATTERNS:
        if pat.search(base_no_ext):
            quality = label
            break
            
    # Visual tags (HDR, DV, 10bit)
    visuals = set()
    for v in _VISUAL_RE.findall(base_no_ext):
        v_upper = v.upper().replace("DOLBY VISION", "DV")
        visuals.add(v_upper)
        
    # Video codecs
    codecs = set()
    for c in _CODEC_RE.findall(base_no_ext):
        codecs.add(c.upper().replace("X265", "HEVC").replace("H.265", "HEVC").replace("X264", "AVC").replace("H.264", "AVC"))
        
    # Audio codecs
    audio_tags = set()
    for a in _AUDIO_CODEC_RE.findall(base_no_ext):
        audio_tags.add(a.upper().strip())
        
    # Audio channels
    channels = set()
    for ch in _AUDIO_CHANNELS_RE.findall(base_no_ext):
        channels.add(ch)
        
    # Languages detected
    languages = set()
    words = re.split(r"[\s._\-+\[\](),]+", (base_no_ext + " " + caption).lower())
    for w in words:
        if w in _LANGUAGES_MAP:
            languages.add(_LANGUAGES_MAP[w])
            
    # Extract clean Title
    clean_title = base_no_ext
    # Remove release group tags at front like [SubsPlease], [Judas]
    clean_title = re.sub(r"^\[[^\]]+\]\s*", "", clean_title)
    
    # Split anchors: Year or Season or Quality or Anime Ep
    cutoff_indices = []
    y_m = _YEAR_RE.search(clean_title)
    if y_m:
        cutoff_indices.append(y_m.start())
    s_m = _SXXEXX_RE.search(clean_title)
    if s_m:
        cutoff_indices.append(s_m.start())
    n_m = _NXNN_RE.search(clean_title)
    if n_m:
        cutoff_indices.append(n_m.start())
    for pat, _ in _RES_PATTERNS:
        m = pat.search(clean_title)
        if m:
            cutoff_indices.append(m.start())
            
    a_m = _ANIME_ABS_RE.search(clean_title)
    if a_m and not s_m and not y_m:
        cutoff_indices.append(a_m.start())
        
    if cutoff_indices:
        earliest = min(cutoff_indices)
        clean_title = clean_title[:earliest]
        
    # Clean delimiters
    clean_title = re.sub(r"[\._\(\)\[\]\-]+", " ", clean_title).strip()
    clean_title = re.sub(r"\s+", " ", clean_title)
    
    # Build quality badge string for Stremio stream items
    badge_parts = [quality]
    if "DV" in visuals:
        badge_parts.append("DV")
    if any(h in visuals for h in ("HDR", "HDR10", "HDR10+")):
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
