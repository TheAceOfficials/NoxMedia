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
_SXXEXX_RE = re.compile(r"(?i)\b(?:s|season\s*)(\d{1,2})[\s._-]*(?:e|ep|episode\s*)(\d{1,3})(?:[._-]?v\d+)?\b")
_NXNN_RE = re.compile(r"(?i)\b(\d{1,2})x(\d{2,3})\b")
_SEASON_ONLY_RE = re.compile(r"(?i)\b(?:s|season\s*)(\d{1,2})\b")
_EPISODE_ONLY_RE = re.compile(r"(?i)\b(?:e|ep|episode\s*)(\d{1,4})\b")

# Anime Absolute Episode: e.g. "One Piece - 1120" or "One Piece 1120"
_ANIME_ABS_RE = re.compile(r"(?i)\s*[-–—]\s*(\d{2,4})(?:\s|$|\[|\()")
_YEAR_RE = re.compile(r"(?:^|[\s._\-\(\[])((?:19|20)\d{2})(?:[\s._\-\)\]]|$)")

# Bracket tags: quality, audio, codec, release group, CRC
_TAG_BRACKET_RE = re.compile(r'[\(\[]\s*(?:1080p|720p|2160p|4k|bd|bluray|web-?rip|web-?dl|dual|multi|flac|hevc|x264|x265|ddp[^\]\)]*|atmos|hdr|dv|h\.?264|h\.?265|aac)[^\)\]]*[\)\]]', re.I)
_REL_RE = re.compile(r'[\(\[](?:MomStealer|Anipulse|Judas|SubsPlease|Erai|YTS|PSA|TGx|Pahe|GalaxyRG)[^\)\]]*[\)\]]', re.I)
_CRC_RE = re.compile(r'\[[0-9A-Fa-f]{8}\]')

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

_INVISIBLE_CHARS_RE = re.compile(r"[\u200b-\u200f\u202a-\u202e\u2066-\u2069\ufeff\x00-\x1f\x7f-\x9f]")

def is_bare_episode_name(name: str) -> bool:
    cleaned = _INVISIBLE_CHARS_RE.sub("", name or "").strip()
    if re.match(r"^[#№]\s*\d+", cleaned):
        return True
    if re.match(r"(?i)^(?:ep|episode|e)\s*\d+[\s._\-]", cleaned):
        return True
    if re.match(r"^\d{1,4}\s*[-–—.]", cleaned):
        return True
    return False

def extract_best_filename(filename: str, caption: str = "") -> str:
    """
    Chooses the best filename between Telegram's document attribute and caption.
    Priority 1: Real Telegram document filename with show title.
    Priority 2: Caption fallback (used when filename is generic, truncated, bare episode name, or lacks series metadata present in caption).
    """
    fn = _INVISIBLE_CHARS_RE.sub("", filename or "").strip()
    cap = _INVISIBLE_CHARS_RE.sub("", caption or "").strip()

    is_generic = (
        not fn or
        fn.lower().startswith("video_") or
        fn.lower().startswith("document") or
        len(fn) < 8 or
        is_bare_episode_name(fn)
    )
    has_broken_bracket = (fn.count("[") != fn.count("]")) or (fn.count("(") != fn.count(")"))

    # Check if caption has explicit Season & Episode (e.g. S01E188, 1x188) while document filename does not
    fn_has_season_ep = bool(_SXXEXX_RE.search(fn) or _NXNN_RE.search(fn))
    cap_has_season_ep = bool(_SXXEXX_RE.search(cap) or _NXNN_RE.search(cap)) if cap else False

    # If fn is a real, complete Telegram document name with a show title (and caption does not contain richer SxxExx series metadata), prioritize it!
    if not is_generic and not has_broken_bracket and not (cap_has_season_ep and not fn_has_season_ep):
        return fn

    # Fallback to caption when filename is generic, truncated, or bare episode name
    if cap:
        ext_pattern = re.compile(r"(?i)\.(mkv|mp4|avi|ts|webm|m4v|mov|rar|zip|7z)(?:\.\d{3})?$")
        split_pattern = re.compile(r"(?i)\.\d{3}$")
        candidate_lines = []

        for line in cap.splitlines():
            cleaned = line.strip()
            # Remove leading decorative symbols/emojis
            cleaned = re.sub(r"^(?:📁|🎬|🎥|▶️|💾|🔹|🔸|▪️|▫️|•|[-*#])+\s*", "", cleaned).strip()
            # Check for explicit label prefix like "File Name:", "Title:", "Movie:", etc.
            has_label = bool(re.search(r"(?i)^(?:file(?:\s*name)?|name|title|movie|series)\s*[:：\-]\s*", cleaned))
            cleaned_val = re.sub(r"(?i)^(?:file(?:\s*name)?|name|title|movie|series)\s*[:：\-]\s*", "", cleaned).strip()
            if not cleaned_val:
                continue

            # Skip promo links, URLs, and channel usernames
            if re.match(r"(?i)^(?:https?://|t\.me/|@|join\b)", cleaned_val):
                continue

            # Skip standalone release group / watermark lines like "(MomStealer).mkv" or "[Judas].mkv"
            if re.match(r"^[\(\[][^\)\]]+[\)\]]\.(?:mkv|mp4|avi)$", cleaned_val, re.I):
                continue

            # Skip lines that are just bare numbers or bare episode titles without series
            if is_bare_episode_name(cleaned_val):
                continue

            # Check if this line is a valid filename or media title
            if ext_pattern.search(cleaned_val) or split_pattern.search(cleaned_val):
                candidate_lines.append(cleaned_val)
            elif any(ext in cleaned_val.lower() for ext in (".mkv", ".mp4", ".avi", ".ts", ".webm", ".m4v", ".mov")):
                candidate_lines.append(cleaned_val)
            elif has_label and len(cleaned_val) > 2:
                candidate_lines.append(cleaned_val)
            elif any(tag in cleaned_val.lower() for tag in ("1080p", "720p", "2160p", "4k", "s0", "e0", "ep", "season", "episode", "web-dl", "webrip", "bluray", "bdrip")):
                candidate_lines.append(cleaned_val)
            elif re.search(r"\b(19|20)\d{2}\b", cleaned_val) and len(cleaned_val.split()) >= 2:
                candidate_lines.append(cleaned_val)

        if candidate_lines:
            # Prefer candidates that have season/episode or year over bare lines
            best_cand = max(
                candidate_lines, 
                key=lambda c: (
                    bool(re.search(r"(?i)\b(?:s\d{1,2}|season|ep\d+|e\d+)\b", c)),
                    bool(ext_pattern.search(c) or split_pattern.search(c)),
                    len(c)
                )
            )
            best_cand = re.sub(r"[\s\-_]+$", "", best_cand).strip()

            has_ext = bool(re.search(r"\.[a-zA-Z0-9]{2,4}(?:\.\d{3})?$", best_cand))
            if not has_ext:
                m_ext = re.search(r"\.[a-zA-Z0-9]+(?:\.\d{3})?$", fn) if fn else None
                if m_ext:
                    best_cand += m_ext.group(0)
                else:
                    best_cand += ".mkv"

            return best_cand

    # Priority 2: Fallback to actual file name
    return fn if fn else (cap.splitlines()[0].strip() if cap else "")

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
    year_match = _YEAR_RE.search(base_no_ext)
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
    cleaned_work = re.sub(r"^\[[^\]]{1,40}\]\s*", "", cleaned_work)
    cleaned_work = re.sub(r"^\([^\)]{1,40}\)\s*", "", cleaned_work)

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
        clean_title = cleaned_work[:s_e_match.start()]
    else:
        nxnn = _NXNN_RE.search(cleaned_work)
        if nxnn:
            season = int(nxnn.group(1))
            episode = int(nxnn.group(2))
            media_type = "series"
            clean_title = cleaned_work[:nxnn.start()]
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
                            clean_title = cleaned_work[:s_only.start()]
                        elif ep_only:
                            episode = int(ep_only.group(1))
                            season = 1
                            media_type = "series"
                            clean_title = cleaned_work[:ep_only.start()]
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
    clean_title = re.sub(r"(?i)\b(2160p|1080p|720p|480p|4k|uhd|hdr|hevc|x264|x265|aac|dts|bluray|web-?dl|web-?rip|proper|repack)\b.*$", "", clean_title)

    # Clean delimiters
    clean_title = _INVISIBLE_CHARS_RE.sub("", clean_title)
    clean_title = re.sub(r"[\._\(\)\[\]\-]+", " ", clean_title).strip()
    clean_title = re.sub(r"\s+", " ", clean_title).strip()
    
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
