import asyncio
import json
import math
import os
import threading
import time
import uuid
import traceback
import re
from collections import OrderedDict
from typing import List, Dict, Any, Optional, Tuple
from pyrogram import Client, errors, raw
import pyrogram.errors.rpc_error
from pyrogram.file_id import FileId, FileType
from pyrogram.session import Session, Auth
import media_parser

# Prevent Pyrogram from writing to read-only Android filesystem on unknown Telegram RPC errors
def _safe_rpc_error_init(self, value=None, rpc_name=None, is_unknown=False, is_signed=False):
    super(pyrogram.errors.rpc_error.RPCError, self).__init__("Telegram says: [{}{} {}] - {} {}".format(
        "-" if is_signed else "",
        self.CODE,
        self.ID or self.NAME,
        self.MESSAGE.format(value=value),
        f'(caused by "{rpc_name}")' if rpc_name else ""
    ))
    try:
        self.value = int(value)
    except (ValueError, TypeError):
        self.value = value
    if is_unknown:
        print(f"[Pyrogram RPC] Unknown Telegram error: {value} (caused by {rpc_name})", flush=True)

pyrogram.errors.rpc_error.RPCError.__init__ = _safe_rpc_error_init

def _format_auth_error(e: Exception) -> str:
    msg = str(e)
    if "SEND_CODE_UNAVAILABLE" in msg:
        return "SMS delivery is currently not available for this session. Please check your official Telegram app."
    if "PHONE_CODE_EXPIRED" in msg:
        return "Verification code expired. Please request a new code."
    if "PHONE_CODE_INVALID" in msg:
        return "Invalid confirmation code. Please check and re-enter."
    if "PHONE_NUMBER_APP_SIGNUP_FORBIDDEN" in msg:
        return "Telegram requires you to register on the official Telegram app first."
    if "AUTH_KEY_UNREGISTERED" in msg or "SESSION_REVOKED" in msg:
        return "Session expired. Please request a new code."
    if "FLOOD_WAIT" in msg:
        m = re.search(r'(\d+)', msg)
        sec = m.group(1) if m else "some"
        return f"Too many attempts. Please wait {sec} seconds."
    # Strip raw MTProto noise: 'Telegram says: [400 Bad Request] - [400 ...]'
    clean = re.sub(r'Telegram says: \[\d+ [^\]]+\] - \[\d+ ([^\]]+)\]', r'\1', msg)
    clean = re.sub(r'\(caused by "[^"]+"\)', '', clean).strip()
    return clean if clean else msg

# Global persistent client instances
_auth_client = None
_stream_client = None
_auth_state = {
    "api_id": 0,
    "api_hash": "",
    "phone": "",
    "phone_code_hash": ""
}

# Active streaming sessions and cache
_active_streams: Dict[str, Any] = {}
_file_id_cache: Dict[Tuple[int, int], FileId] = {}
_media_session_lock = asyncio.Lock()
_channel_peer_lock = asyncio.Lock()
_resolved_channels: Dict[int, int] = {}
_file_id_lock = asyncio.Lock()
_pending_file_resolutions: Dict[Tuple[int, int], asyncio.Task] = {}

# High-Performance In-Memory LRU Chunk Cache
# 48 chunks x 1MB = ~48 MB RAM (blazing fast seeking and repeat header reads)
_CHUNK_CACHE_MAX_ENTRIES = 48
_chunk_cache: OrderedDict[Tuple[int, int, int], bytes] = OrderedDict()
_fetch_dedup_lock = asyncio.Lock()
_pending_fetches: Dict[Tuple[int, int, int], asyncio.Task] = {}

# Persistent On-Disk Chunk Cache & Buffer Pipeline Config
_disk_cache_dir: Optional[str] = None
_disk_cache_max_mb: int = 2048
_buffer_pipeline_mb: int = 20
_last_prune_time: float = 0.0

def configure_cache(cache_dir: str, max_disk_mb: int, buffer_mb: int):
    global _disk_cache_dir, _disk_cache_max_mb, _buffer_pipeline_mb
    _disk_cache_dir = cache_dir
    _disk_cache_max_mb = int(max_disk_mb)
    _buffer_pipeline_mb = max(2, int(buffer_mb))
    if _disk_cache_dir and not os.path.exists(_disk_cache_dir):
        try:
            os.makedirs(_disk_cache_dir, exist_ok=True)
        except Exception:
            pass

def _save_chunk_to_disk_sync(chat_id: int, msg_id: int, fetch_off: int, limit: int, data: bytes):
    global _last_prune_time
    try:
        if not _disk_cache_dir or _disk_cache_max_mb <= 0:
            return
        chunk_file = os.path.join(_disk_cache_dir, f"{abs(chat_id)}_{msg_id}_{fetch_off}_{limit}.bin")
        temp_file = chunk_file + f".{uuid.uuid4().hex[:6]}.tmp"
        with open(temp_file, "wb") as f:
            f.write(data)
        os.replace(temp_file, chunk_file)

        now = time.time()
        if _disk_cache_max_mb > 0 and (now - _last_prune_time > 30):
            _last_prune_time = now
            _prune_disk_cache()
    except Exception as e:
        print(f"[DiskCache] Write error: {e}", flush=True)

def _save_chunk_to_disk(chat_id: int, msg_id: int, fetch_off: int, limit: int, data: bytes):
    if not _disk_cache_dir or _disk_cache_max_mb <= 0:
        return
    try:
        loop = _ensure_event_loop()
        loop.run_in_executor(None, _save_chunk_to_disk_sync, chat_id, msg_id, fetch_off, limit, data)
    except Exception:
        pass

def _prune_disk_cache():
    try:
        if not _disk_cache_dir or _disk_cache_max_mb <= 0:
            return
        max_bytes = _disk_cache_max_mb * 1024 * 1024
        entries = []
        total_size = 0
        for entry in os.scandir(_disk_cache_dir):
            if entry.is_file() and entry.name.endswith(".bin"):
                try:
                    stat = entry.stat()
                    total_size += stat.st_size
                    entries.append((stat.st_mtime, stat.st_size, entry.path))
                except Exception:
                    pass

        if total_size > max_bytes:
            entries.sort(key=lambda x: x[0])
            target_size = int(max_bytes * 0.85)
            for _, size, path in entries:
                try:
                    os.remove(path)
                    total_size -= size
                    if total_size <= target_size:
                        break
                except Exception:
                    pass
    except Exception as e:
        print(f"[DiskCache] Prune error: {e}", flush=True)

def _cache_get(key: Tuple[int, int, int]) -> Optional[bytes]:
    if key in _chunk_cache:
        _chunk_cache.move_to_end(key)
        return _chunk_cache[key]
    return None

def _cache_put(key: Tuple[int, int, int], data: bytes):
    if not data:
        return
    _chunk_cache[key] = data
    _chunk_cache.move_to_end(key)
    while len(_chunk_cache) > _CHUNK_CACHE_MAX_ENTRIES:
        _chunk_cache.popitem(last=False)

# Dedicated persistent asyncio event loop on a background daemon thread
_main_loop = None
_main_thread = None
_loop_lock = threading.Lock()

def _ensure_event_loop():
    global _main_loop, _main_thread
    with _loop_lock:
        if _main_loop is None or not _main_loop.is_running():
            _main_loop = asyncio.new_event_loop()
            def _loop_runner(l):
                asyncio.set_event_loop(l)
                l.run_forever()
            _main_thread = threading.Thread(target=_loop_runner, args=(_main_loop,), daemon=True, name="PyrogramMainLoop")
            _main_thread.start()
    return _main_loop

def run_async(coro, timeout=30):
    loop = _ensure_event_loop()
    future = asyncio.run_coroutine_threadsafe(coro, loop)
    return future.result(timeout=timeout)

# --- Authentication Bridge ---

def send_code(api_id: int, api_hash: str, phone_number: str) -> str:
    global _auth_client, _auth_state
    
    clean_phone = phone_number.strip().replace(" ", "")
    _auth_state["api_id"] = int(api_id)
    _auth_state["api_hash"] = api_hash.strip()
    _auth_state["phone"] = clean_phone
    
    async def _async_send():
        global _auth_client
        # Disconnect any existing auth client cleanly
        if _auth_client is not None:
            try:
                if _auth_client.is_connected:
                    await _auth_client.disconnect()
            except Exception:
                pass

        # Use Pyrogram defaults for app_version/device_model/system_version
        # Custom values cause Telegram to silently block OTP delivery
        _auth_client = Client(
            ":memory:",
            api_id=_auth_state["api_id"],
            api_hash=_auth_state["api_hash"],
            in_memory=True
        )
        await _auth_client.connect()
        sent_code = await _auth_client.send_code(clean_phone)
        _auth_state["phone_code_hash"] = sent_code.phone_code_hash
        sent_type = getattr(sent_code.type, "name", str(sent_code.type))
        next_type = getattr(sent_code.next_type, "name", str(sent_code.next_type)) if getattr(sent_code, "next_type", None) else ""
        return {
            "status": "success",
            "phone_code_hash": sent_code.phone_code_hash,
            "timeout": getattr(sent_code, "timeout", 60),
            "type": sent_type,
            "next_type": next_type
        }

    try:
        res = run_async(_async_send(), timeout=30)
        return json.dumps(res)
    except errors.FloodWait as e:
        return json.dumps({"status": "error", "error": f"Too many attempts. Please wait {e.value} seconds."})
    except errors.PhoneNumberInvalid:
        return json.dumps({"status": "error", "error": "Invalid phone number. Include country code (e.g. +91...)"})
    except errors.PhoneNumberBanned:
        return json.dumps({"status": "error", "error": "This phone number is banned from Telegram."})
    except Exception as e:
        return json.dumps({"status": "error", "error": _format_auth_error(e)})

def resend_code() -> str:
    global _auth_client, _auth_state
    
    async def _async_resend():
        global _auth_client
        # Reconnect if session was lost
        if not _auth_client:
            return {"status": "error", "error": "Session expired. Please request a new code."}
        if not _auth_client.is_connected:
            try:
                await _auth_client.connect()
            except Exception as e:
                return {"status": "error", "error": f"Could not reconnect: {e}"}

        res = await _auth_client.resend_code(
            _auth_state["phone"],
            _auth_state["phone_code_hash"]
        )
        _auth_state["phone_code_hash"] = res.phone_code_hash
        sent_type = getattr(res.type, "name", str(res.type))
        next_type = getattr(res.next_type, "name", str(res.next_type)) if getattr(res, "next_type", None) else ""
        return {
            "status": "success",
            "phone_code_hash": res.phone_code_hash,
            "timeout": getattr(res, "timeout", 60),
            "type": sent_type,
            "next_type": next_type
        }

    try:
        res = run_async(_async_resend(), timeout=30)
        return json.dumps(res)
    except errors.FloodWait as e:
        return json.dumps({"status": "error", "error": f"Please wait {e.value} seconds before requesting a new code."})
    except Exception as e:
        return json.dumps({"status": "error", "error": _format_auth_error(e)})

async def _download_user_avatar(client: Client, user, target_path: str = "") -> str:
    """Helper to download user profile photo using file_id or get_chat_photos."""
    if not target_path:
        target_path = "profile_avatar.jpg"
    try:
        if getattr(user, "photo", None):
            file_id = getattr(user.photo, "big_file_id", None) or getattr(user.photo, "small_file_id", None)
            if file_id:
                downloaded = await client.download_media(file_id, file_name=target_path)
                if downloaded and os.path.exists(downloaded):
                    return str(downloaded)
    except Exception as e:
        print(f"Avatar download via user.photo notice: {e}", flush=True)

    try:
        async for photo in client.get_chat_photos("me", limit=1):
            downloaded = await client.download_media(photo, file_name=target_path)
            if downloaded and os.path.exists(downloaded):
                return str(downloaded)
    except Exception as e:
        print(f"Avatar download via get_chat_photos notice: {e}", flush=True)

    return ""

async def _download_chat_photo(client: Client, chat, target_path: str = "") -> str:
    """Helper to download channel/chat photo using small_file_id/big_file_id or get_chat_photos."""
    if not target_path:
        return ""
    try:
        if getattr(chat, "photo", None):
            file_id = getattr(chat.photo, "small_file_id", None) or getattr(chat.photo, "big_file_id", None)
            if file_id:
                downloaded = await client.download_media(file_id, file_name=target_path)
                if downloaded and os.path.exists(downloaded):
                    return str(downloaded)
    except Exception as e:
        print(f"Chat photo download notice via chat.photo: {e}", flush=True)

    try:
        chat_id = getattr(chat, "id", None)
        if chat_id:
            async for photo in client.get_chat_photos(chat_id, limit=1):
                downloaded = await client.download_media(photo, file_name=target_path)
                if downloaded and os.path.exists(downloaded):
                    return str(downloaded)
    except Exception as e:
        print(f"Chat photo download notice via get_chat_photos: {e}", flush=True)

    return ""

def verify_code(code: str, target_avatar_path: str = "") -> str:
    global _auth_client, _auth_state
    
    async def _async_verify():
        global _auth_client
        if not _auth_client or not _auth_client.is_connected:
            return {"status": "error", "error": "Session expired. Please request a new code."}
            
        clean_code = code.strip()
        user = await _auth_client.sign_in(
            _auth_state["phone"],
            _auth_state["phone_code_hash"],
            clean_code
        )
        
        session_str = await _auth_client.export_session_string()
        photo_path = await _download_user_avatar(_auth_client, user, target_avatar_path)
        
        return {
            "status": "success",
            "user_id": user.id,
            "first_name": user.first_name or "Telegram User",
            "username": user.username or f"user_{user.id}",
            "session_string": session_str,
            "photo_path": photo_path
        }

    try:
        res = run_async(_async_verify(), timeout=30)
        return json.dumps(res)
    except errors.SessionPasswordNeeded:
        return json.dumps({"status": "2fa_required", "message": "2-Step verification password required"})
    except errors.PhoneCodeInvalid:
        return json.dumps({"status": "error", "error": "Invalid verification code. Please check and re-enter."})
    except errors.PhoneCodeExpired:
        return json.dumps({"status": "error", "error": "Verification code expired. Please request a new code."})
    except Exception as e:
        return json.dumps({"status": "error", "error": _format_auth_error(e)})

def check_password(password: str, target_avatar_path: str = "") -> str:
    global _auth_client
    
    async def _async_check_pwd():
        global _auth_client
        if not _auth_client or not _auth_client.is_connected:
            return {"status": "error", "error": "Session expired. Please request a new code."}
            
        user = await _auth_client.check_password(password.strip())
        session_str = await _auth_client.export_session_string()
        photo_path = await _download_user_avatar(_auth_client, user, target_avatar_path)
        
        return {
            "status": "success",
            "user_id": user.id,
            "first_name": user.first_name or "Telegram User",
            "username": user.username or f"user_{user.id}",
            "session_string": session_str,
            "photo_path": photo_path
        }

    try:
        res = run_async(_async_check_pwd(), timeout=30)
        return json.dumps(res)
    except errors.PasswordHashInvalid:
        return json.dumps({"status": "error", "error": "Incorrect 2FA password."})
    except Exception as e:
        return json.dumps({"status": "error", "error": _format_auth_error(e)})

# --- Streaming Client & Media Session Management ---

async def _get_connected_stream_client(api_id: int, api_hash: str, session_str: str) -> Client:
    global _stream_client
    if _stream_client is not None and _stream_client.is_connected:
        return _stream_client

    if _stream_client is not None:
        try:
            await _stream_client.disconnect()
        except Exception:
            pass

    _stream_client = Client(
        ":memory:",
        api_id=int(api_id),
        api_hash=api_hash.strip(),
        session_string=session_str.strip(),
        in_memory=True
    )
    await _stream_client.start()

    # Pre-cache initial dialogs (limit=20, timeout 3s) so user's channel access hashes are known
    try:
        async def _quick_dialogs():
            async for _ in _stream_client.get_dialogs(limit=20):
                pass
        await asyncio.wait_for(_quick_dialogs(), timeout=3.0)
    except Exception as e:
        print(f"Pre-caching dialogs notice: {e}", flush=True)

    return _stream_client

def fetch_user_profile(api_id: int, api_hash: str, session_str: str, target_avatar_path: str = "") -> str:
    """Fetches user info (name, username, id) and downloads profile avatar to target_avatar_path."""
    async def _async_fetch():
        client = await _get_connected_stream_client(int(api_id), str(api_hash), str(session_str))
        me = await client.get_me()
        if not me:
            return {"status": "error", "error": "Could not retrieve user info"}

        photo_path = await _download_user_avatar(client, me, target_avatar_path)

        return {
            "status": "success",
            "user_id": me.id,
            "first_name": me.first_name or "Telegram User",
            "username": me.username or f"user_{me.id}",
            "photo_path": photo_path
        }

    try:
        res = run_async(_async_fetch(), timeout=30)
        return json.dumps(res)
    except Exception as e:
        print(f"Error in fetch_user_profile: {e}", flush=True)
        return json.dumps({"status": "error", "error": str(e)})

def parse_media(filename: str, caption: str = "") -> str:
    """Parses a media filename and returns JSON metadata."""
    res = media_parser.parse_media_filename(filename, caption)
    return json.dumps(res)

def resolve_channel(api_id: int, api_hash: str, session_str: str, identifier: str, target_avatar_path: str = "") -> str:
    """
    Connects to Telegram via Pyrogram and verifies/resolves the channel.
    Accepts @username, t.me link, private link (t.me/c/...), -100... ID, or bare numeric ID.
    Returns JSON with channel title, real ID, username, photo_path, or error.
    """
    async def _async_resolve():
        client = await _get_connected_stream_client(api_id, api_hash, session_str)
        raw = identifier.strip().strip("'\"")

        # 1. Private channel message link pattern: t.me/c/<channel_id>/...
        c_match = re.search(r"t\.me/c/(\d+)", raw)
        if c_match:
            chat_target = int(f"-100{c_match.group(1)}")
        else:
            target = raw
            if target.startswith("https://t.me/"):
                target = target.removeprefix("https://t.me/").strip()
            elif target.startswith("http://t.me/"):
                target = target.removeprefix("http://t.me/").strip()
            elif target.startswith("t.me/"):
                target = target.removeprefix("t.me/").strip()

            target = target.strip("/")

            # Check if private invite link: +hash or joinchat/hash
            if target.startswith("+") or target.startswith("joinchat/"):
                chat_target = target
            elif target.startswith("-100") and target[4:].isdigit():
                chat_target = int(target)
            elif target.startswith("-") and target[1:].isdigit():
                bare = target[1:]
                if not bare.startswith("100"):
                    chat_target = int(f"-100{bare}")
                else:
                    chat_target = int(target)
            elif target.isdigit():
                if target.startswith("100"):
                    chat_target = int(f"-{target}")
                else:
                    chat_target = int(f"-100{target}")
            else:
                if not target.startswith("@"):
                    target = f"@{target}"
                chat_target = target

        chat = None
        # 1. Try direct get_chat first
        try:
            chat = await client.get_chat(chat_target)
        except Exception:
            chat = None

        # 2. If get_chat failed, search the user's dialogs (vital for private/numeric channel IDs)
        if chat is None:
            target_id_variants = set()
            if isinstance(chat_target, int):
                target_id_variants.add(chat_target)
                bare = str(abs(chat_target)).removeprefix("100")
                if bare.isdigit():
                    b_int = int(bare)
                    target_id_variants.add(b_int)
                    target_id_variants.add(-b_int)
                    target_id_variants.add(int(f"-100{b_int}"))

            try:
                async for dialog in client.get_dialogs():
                    d_chat = dialog.chat
                    if not d_chat:
                        continue
                    d_id = d_chat.id
                    d_bare = str(abs(d_id)).removeprefix("100")

                    if d_id in target_id_variants or (isinstance(chat_target, int) and (str(abs(chat_target)).endswith(d_bare) or d_bare.endswith(str(abs(chat_target))))):
                        chat = d_chat
                        break

                    if isinstance(chat_target, str) and d_chat.username:
                        if d_chat.username.lower() == str(chat_target).removeprefix("@").lower():
                            chat = d_chat
                            break
            except Exception:
                pass

        # 3. If direct matching didn't catch it, retry get_chat now that dialogs populated peer cache
        if chat is None and isinstance(chat_target, int):
            try:
                chat = await client.get_chat(chat_target)
            except Exception:
                pass

        # 4. If still None and invite link, attempt join_chat
        if chat is None and isinstance(chat_target, str) and (chat_target.startswith("+") or chat_target.startswith("joinchat/")):
            try:
                chat = await client.join_chat(chat_target)
            except Exception:
                try:
                    chat = await client.get_chat(chat_target)
                except Exception:
                    pass

        if chat is not None:
            title = chat.title or getattr(chat, "first_name", None) or "Telegram Channel"
            uname = getattr(chat, "username", None) or str(chat.id)
            photo_path = ""
            if target_avatar_path:
                try:
                    photo_path = await _download_chat_photo(client, chat, target_avatar_path)
                except Exception as ex:
                    print(f"Chat photo download notice: {ex}", flush=True)
            return {
                "status": "success",
                "id": chat.id,
                "title": title,
                "username": uname,
                "type": str(getattr(chat, "type", "channel")),
                "photo_path": photo_path
            }
        else:
            if isinstance(chat_target, int):
                return {
                    "status": "error",
                    "error": f"Channel ID {chat_target} not found. Please ensure your Telegram account is a member of this channel."
                }
            else:
                return {
                    "status": "error",
                    "error": f"Channel '{raw}' not found. Please check the username or link."
                }

    try:
        res = run_async(_async_resolve(), timeout=30)
        return json.dumps(res)
    except errors.UsernameNotOccupied:
        return json.dumps({"status": "error", "error": "Channel not found. Check username or link."})
    except errors.ChatIdInvalid:
        return json.dumps({"status": "error", "error": "Invalid Telegram channel ID."})
    except errors.ChannelPrivate:
        return json.dumps({"status": "error", "error": "Channel is private and your account is not a member."})
    except Exception as e:
        print(f"Error in resolve_channel: {e}", flush=True)
        return json.dumps({"status": "error", "error": str(e)})

def fetch_channel_avatar(api_id: int, api_hash: str, session_str: str, channel_id: int, target_avatar_path: str = "") -> str:
    """Downloads the avatar of a specific channel to target_avatar_path."""
    async def _async_fetch():
        client = await _get_connected_stream_client(int(api_id), str(api_hash), str(session_str))
        chat = None
        target_id = int(channel_id)
        try:
            chat = await client.get_chat(target_id)
        except Exception:
            pass

        if chat is None:
            target_id_variants = {target_id}
            bare = str(abs(target_id)).removeprefix("100")
            if bare.isdigit():
                b_int = int(bare)
                target_id_variants.add(b_int)
                target_id_variants.add(-b_int)
                target_id_variants.add(int(f"-100{b_int}"))
            try:
                async for dialog in client.get_dialogs():
                    if dialog.chat and (dialog.chat.id in target_id_variants):
                        chat = dialog.chat
                        break
            except Exception:
                pass

        if not chat:
            return {"status": "error", "error": f"Channel {target_id} not found"}

        photo_path = await _download_chat_photo(client, chat, target_avatar_path)
        return {"status": "success", "photo_path": photo_path}

    try:
        res = run_async(_async_fetch(), timeout=30)
        return json.dumps(res)
    except Exception as e:
        print(f"Error in fetch_channel_avatar: {e}", flush=True)
        return json.dumps({"status": "error", "error": str(e)})
    except Exception as e:
        return json.dumps({"status": "error", "error": str(e)})

def sync_channel_media(api_id: int, api_hash: str, session_str: str, channel_id: int, limit: int = 150, is_anime: bool = False) -> str:
    """
    Scans recent messages from channel, extracts video and document media,
    parses filenames, groups split files into virtual items, and returns JSON.
    """
    async def _async_sync():
        client = await _get_connected_stream_client(api_id, api_hash, session_str)
        chat_id = int(channel_id)
        
        # Ensure channel peer is known
        try:
            await client.get_chat(chat_id)
        except Exception:
            try:
                async for dialog in client.get_dialogs():
                    if dialog.chat and (dialog.chat.id == chat_id or str(abs(dialog.chat.id)).endswith(str(abs(chat_id)))):
                        break
            except Exception:
                pass

        items_map = {}
        split_groups = {}

        video_exts = (".mkv", ".mp4", ".avi", ".ts", ".m4v", ".mov", ".webm")
        
        async for msg in client.get_chat_history(chat_id, limit=int(limit)):
            media = msg.video or msg.document or msg.animation
            if not media:
                continue

            try:
                _file_id_cache[(chat_id, msg.id)] = FileId.decode(media.file_id)
            except Exception:
                pass

            file_name = getattr(media, "file_name", None) or f"video_{msg.id}.mp4"
            file_size = getattr(media, "file_size", 0)
            mime_type = getattr(media, "mime_type", "video/mp4") or "video/mp4"
            duration = getattr(media, "duration", 0) or 0

            caption = msg.caption or ""
            best_file_name = media_parser.extract_best_filename(file_name, caption)

            # Only process if video mime or video extension
            is_video = (
                mime_type.startswith("video/") or
                any(best_file_name.lower().endswith(ext) for ext in video_exts) or
                media_parser.parse_split_part(best_file_name) is not None
            )
            if not is_video or file_size <= 0:
                continue

            parsed = media_parser.parse_media_filename(best_file_name, caption, is_anime=bool(is_anime))
            
            clean_base = parsed["clean_base_name"]
            is_split = parsed["is_split"]
            part_num = parsed["part_number"]
            msg_date = int(msg.date.timestamp() * 1000) if hasattr(msg, "date") and msg.date else int(time.time() * 1000)

            if is_split:
                if clean_base not in split_groups:
                    split_groups[clean_base] = {
                        "parsed": parsed,
                        "mimeType": mime_type,
                        "duration": duration,
                        "dateAdded": msg_date,
                        "parts": []
                    }
                split_groups[clean_base]["parts"].append({
                    "messageId": msg.id,
                    "partNumber": part_num,
                    "sizeBytes": file_size
                })
            else:
                item_id = f"tg_{chat_id}_{msg.id}"
                items_map[item_id] = {
                    "id": item_id,
                    "channelId": chat_id,
                    "messageId": msg.id,
                    "title": parsed["title"],
                    "type": parsed["type"],
                    "year": parsed["year"],
                    "season": parsed["season"],
                    "episode": parsed["episode"],
                    "quality": parsed["quality"],
                    "qualityBadge": parsed["quality_badge"],
                    "sizeBytes": file_size,
                    "durationSeconds": duration,
                    "mimeType": mime_type,
                    "fileName": parsed["clean_base_name"],
                    "isSplit": False,
                    "parts": [],
                    "dateAdded": msg_date
                }

        # Process grouped split files
        for clean_base, group in split_groups.items():
            parsed = group["parsed"]
            sorted_parts = sorted(group["parts"], key=lambda p: p["partNumber"])
            cum_start = 0
            computed_parts = []
            for p in sorted_parts:
                computed_parts.append({
                    "messageId": p["messageId"],
                    "partNumber": p["partNumber"],
                    "sizeBytes": p["sizeBytes"],
                    "cumStart": cum_start
                })
                cum_start += p["sizeBytes"]

            first_msg_id = computed_parts[0]["messageId"]
            item_id = f"tg_{chat_id}_{first_msg_id}_split"
            items_map[item_id] = {
                "id": item_id,
                "channelId": chat_id,
                "messageId": first_msg_id,
                "title": parsed["title"],
                "type": parsed["type"],
                "year": parsed["year"],
                "season": parsed["season"],
                "episode": parsed["episode"],
                "quality": parsed["quality"],
                "qualityBadge": parsed["quality_badge"],
                "sizeBytes": cum_start,
                "durationSeconds": group["duration"],
                "mimeType": group["mimeType"],
                "fileName": clean_base,
                "isSplit": True,
                "parts": computed_parts,
                "dateAdded": group.get("dateAdded", int(time.time() * 1000))
            }

        return list(items_map.values())

    try:
        res = run_async(_async_sync(), timeout=60)
        return json.dumps({"status": "success", "media": res})
    except Exception as e:
        traceback.print_exc()
        return json.dumps({"status": "error", "error": str(e)})

def scan_channel_batch(api_id: int, api_hash: str, session_str: str, channel_id: int, offset_id: int = 0, limit: int = 100, is_anime: bool = False, min_id: int = 0) -> str:
    """
    Scans a single batch (chunk) of messages from chat_history starting at offset_id.
    Returns JSON with items, split parts, skipped/unsupported files, last_msg_id, and has_more flag.
    Gracefully handles FloodWait.
    If min_id > 0, stops scanning once msg.id <= min_id (incremental scan for new messages only).
    """
    async def _async_scan():
        client = await _get_connected_stream_client(api_id, api_hash, session_str)
        chat_id = int(channel_id)
        peer = await _ensure_channel_peer(client, chat_id)

        items = []
        split_parts = []
        skipped = []
        last_msg_id = 0
        messages_scanned = 0
        first_msg_id = 0
        max_msg_id = 0
        reached_min = False
        min_msg_cutoff = int(min_id) if min_id else 0
        video_exts = (".mkv", ".mp4", ".avi", ".ts", ".m4v", ".mov", ".webm", ".flv", ".wmv", ".m2ts")

        try:
            async for msg in client.get_chat_history(peer, limit=int(limit), offset_id=int(offset_id)):
                if first_msg_id == 0:
                    first_msg_id = msg.id
                if min_msg_cutoff > 0 and msg.id <= min_msg_cutoff:
                    reached_min = True
                    break

                messages_scanned += 1
                last_msg_id = msg.id
                if msg.id > max_msg_id:
                    max_msg_id = msg.id

                media = msg.video or msg.document or msg.animation
                if not media:
                    continue

                try:
                    _file_id_cache[(chat_id, msg.id)] = FileId.decode(media.file_id)
                except Exception:
                    pass

                file_name = getattr(media, "file_name", None) or f"video_{msg.id}.mp4"
                file_size = getattr(media, "file_size", 0) or 0
                mime_type = getattr(media, "mime_type", "") or ""
                duration = getattr(media, "duration", 0) or 0
                caption = msg.caption or ""

                best_file_name = media_parser.extract_best_filename(file_name, caption)
                split_info = media_parser.parse_split_part(best_file_name)

                is_video = (
                    mime_type.startswith("video/") or
                    any(best_file_name.lower().endswith(ext) for ext in video_exts) or
                    split_info is not None
                )

                if not is_video or file_size <= 0:
                    skipped.append({
                        "messageId": msg.id,
                        "fileName": best_file_name,
                        "rawFileName": file_name,
                        "sizeBytes": file_size,
                        "mimeType": mime_type or "application/octet-stream",
                        "reason": "Unsupported format (non-video file)" if file_size > 0 else "Empty file (0 bytes)"
                    })
                    continue

                parsed = media_parser.parse_media_filename(best_file_name, caption, is_anime=bool(is_anime))
                clean_base = parsed["clean_base_name"]
                is_split = parsed["is_split"]
                part_num = parsed["part_number"]

                if is_split:
                    split_parts.append({
                        "messageId": msg.id,
                        "partNumber": part_num,
                        "sizeBytes": file_size,
                        "duration": duration,
                        "mimeType": mime_type or "video/mp4",
                        "cleanBase": clean_base,
                        "rawFileName": file_name,
                        "title": parsed["title"],
                        "type": parsed["type"],
                        "year": parsed["year"],
                        "season": parsed["season"],
                        "episode": parsed["episode"],
                        "quality": parsed["quality"],
                        "qualityBadge": parsed["quality_badge"]
                    })
                else:
                    item_id = f"tg_{chat_id}_{msg.id}"
                    items.append({
                        "id": item_id,
                        "channelId": chat_id,
                        "messageId": msg.id,
                        "title": parsed["title"],
                        "type": parsed["type"],
                        "year": parsed["year"],
                        "season": parsed["season"],
                        "episode": parsed["episode"],
                        "quality": parsed["quality"],
                        "qualityBadge": parsed["quality_badge"],
                        "sizeBytes": file_size,
                        "durationSeconds": duration,
                        "mimeType": mime_type or "video/mp4",
                        "fileName": parsed["clean_base_name"],
                        "rawFileName": file_name,
                        "isSplit": False,
                        "parts": []
                    })
        except errors.FloodWait as e:
            return {
                "status": "flood_wait",
                "wait_seconds": e.value,
                "last_msg_id": last_msg_id,
                "messages_scanned": messages_scanned,
                "first_msg_id": first_msg_id,
                "max_msg_id": max_msg_id,
                "reached_min": reached_min,
                "items": items,
                "split_parts": split_parts,
                "skipped": skipped
            }

        return {
            "status": "success",
            "items": items,
            "split_parts": split_parts,
            "skipped": skipped,
            "last_msg_id": last_msg_id,
            "messages_scanned": messages_scanned,
            "first_msg_id": first_msg_id,
            "max_msg_id": max_msg_id,
            "reached_min": reached_min,
            "has_more": (not reached_min) and messages_scanned >= int(limit) and last_msg_id > 0
        }

    try:
        res = run_async(_async_scan(), timeout=45)
        return json.dumps(res)
    except errors.FloodWait as e:
        return json.dumps({"status": "flood_wait", "wait_seconds": e.value, "last_msg_id": 0, "messages_scanned": 0, "items": [], "split_parts": [], "skipped": []})
    except Exception as e:
        traceback.print_exc()
        return json.dumps({"status": "error", "error": str(e)})

# --- Byte Streaming Across Single / Split Parts ---

async def _ensure_channel_peer(client: Client, chat_id: int) -> int:
    """
    Safely resolves and caches the channel's MTProto access_hash once in Pyrogram memory.
    Prevents repeated expensive dialog scans and concurrent request collisions.
    """
    if chat_id in _resolved_channels:
        return _resolved_channels[chat_id]

    async with _channel_peer_lock:
        if chat_id in _resolved_channels:
            return _resolved_channels[chat_id]

        target_ids = [chat_id]
        chat_str = str(chat_id)
        if chat_str.startswith("-100"):
            try:
                target_ids.append(int(chat_str[4:]))
            except Exception:
                pass
        elif not chat_str.startswith("-"):
            try:
                target_ids.append(int(f"-100{chat_str}"))
            except Exception:
                pass

        # 1. Try get_chat directly first (works for public channels or pre-cached peers)
        for tid in target_ids:
            try:
                c = await client.get_chat(tid)
                if c:
                    real_id = c.id
                    for x in set(target_ids) | {real_id}:
                        _resolved_channels[x] = real_id
                    return real_id
            except Exception:
                pass

        # 2. Iterate dialogs once to resolve and cache the peer in Pyrogram
        try:
            async for dialog in client.get_dialogs(limit=50):
                dchat = dialog.chat
                if dchat:
                    did = dchat.id
                    d_str = str(abs(did))
                    matched = False
                    for tid in target_ids:
                        t_str = str(abs(tid))
                        if did == tid or d_str.endswith(t_str) or t_str.endswith(d_str):
                            matched = True
                            break
                    if matched:
                        real_id = dchat.id
                        for x in set(target_ids) | {real_id}:
                            _resolved_channels[x] = real_id
                        print(f"[PeerResolve] Resolved chat {chat_id} -> {real_id} ({getattr(dchat, 'title', '')})", flush=True)
                        return real_id
        except Exception as e:
            print(f"[PeerResolve] Dialog scan notice for {chat_id}: {e}", flush=True)

        _resolved_channels[chat_id] = chat_id
        return chat_id

async def _get_media_session(client: Client, dc_id: int) -> Session:
    """
    Obtains or initializes a reliable authenticated media Session for datacenter dc_id.
    """
    if not hasattr(client, "media_sessions"):
        client.media_sessions = {}

    session = client.media_sessions.get(dc_id)
    if session and getattr(session, "is_started", None) and session.is_started.is_set():
        return session

    async with _media_session_lock:
        session = client.media_sessions.get(dc_id)
        if session and getattr(session, "is_started", None) and session.is_started.is_set():
            return session

        test_mode = await client.storage.test_mode()
        current_dc = await client.storage.dc_id()

        if dc_id != current_dc:
            auth_key = await Auth(client, dc_id, test_mode).create()
        else:
            auth_key = await client.storage.auth_key()

        session = Session(client, dc_id, auth_key, test_mode, is_media=True)
        session.no_updates = True
        session.timeout = 30
        session.sleep_threshold = 60

        await session.start()

        if dc_id != current_dc:
            exported_ok = False
            for _ in range(6):
                try:
                    exported = await client.invoke(raw.functions.auth.ExportAuthorization(dc_id=dc_id))
                    await session.invoke(raw.functions.auth.ImportAuthorization(id=exported.id, bytes=exported.bytes), timeout=15.0)
                    exported_ok = True
                    break
                except errors.AuthBytesInvalid:
                    await asyncio.sleep(0.5)
                except OSError:
                    await asyncio.sleep(1)
                except Exception:
                    await asyncio.sleep(0.5)

            if not exported_ok:
                try:
                    await session.stop()
                except Exception:
                    pass
                raise RuntimeError(f"Failed to export/import authorization for DC {dc_id}")

        client.media_sessions[dc_id] = session
        return session

def _get_location_from_file_id(file_id_obj: FileId):
    if file_id_obj.file_type == FileType.PHOTO:
        return raw.types.InputPhotoFileLocation(
            id=file_id_obj.media_id,
            access_hash=file_id_obj.access_hash,
            file_reference=file_id_obj.file_reference,
            thumb_size=file_id_obj.thumbnail_size or ""
        )
    return raw.types.InputDocumentFileLocation(
        id=file_id_obj.media_id,
        access_hash=file_id_obj.access_hash,
        file_reference=file_id_obj.file_reference,
        thumb_size=file_id_obj.thumbnail_size or ""
    )

async def _resolve_file_id(client: Client, chat_id: int, msg_id: int) -> Optional[FileId]:
    key = (chat_id, msg_id)
    cached = _file_id_cache.get(key)
    if cached:
        return cached

    async with _file_id_lock:
        cached = _file_id_cache.get(key)
        if cached:
            return cached
        task = _pending_file_resolutions.get(key)
        if task is None:
            task = asyncio.create_task(_do_resolve_file_id(client, chat_id, msg_id, key))
            _pending_file_resolutions[key] = task

    try:
        return await asyncio.shield(task)
    except Exception as e:
        print(f"[ResolveFileId] Error resolving {key}: {e}", flush=True)
        return None
    finally:
        async with _file_id_lock:
            if _pending_file_resolutions.get(key) is task and task.done():
                _pending_file_resolutions.pop(key, None)

async def _do_resolve_file_id(client: Client, chat_id: int, msg_id: int, key: Tuple[int, int]) -> Optional[FileId]:
    resolved_chat_id = await _ensure_channel_peer(client, chat_id)

    target_peers = [resolved_chat_id, chat_id]
    chat_str = str(chat_id)
    if chat_str.startswith("-100"):
        try:
            target_peers.append(int(chat_str[4:]))
        except Exception:
            pass
    elif not chat_str.startswith("-"):
        try:
            target_peers.append(int(f"-100{chat_str}"))
        except Exception:
            pass

    msg = None
    for peer in target_peers:
        try:
            msg = await client.get_messages(peer, msg_id)
            if msg and (msg.video or msg.document or msg.animation or msg.audio):
                break
        except Exception:
            msg = None

    if not msg:
        try:
            async for dialog in client.get_dialogs(limit=50):
                dchat = dialog.chat
                if dchat and (dchat.id == chat_id or str(abs(dchat.id)).endswith(str(abs(chat_id))) or str(abs(chat_id)).endswith(str(abs(dchat.id)))):
                    try:
                        msg = await client.get_messages(dchat.id, msg_id)
                        if msg and (msg.video or msg.document or msg.animation or msg.audio):
                            break
                    except Exception:
                        pass
        except Exception as e:
            print(f"[ResolveFileId] Dialog fallback notice: {e}", flush=True)

    if not msg:
        print(f"[ResolveFileId] Failed to resolve message {msg_id} in chat {chat_id} (resolved={resolved_chat_id})", flush=True)
        return None

    media = msg.video or msg.document or msg.animation or msg.audio
    if not media:
        return None

    try:
        file_id_obj = FileId.decode(media.file_id)
        _file_id_cache[key] = file_id_obj
        for peer in set(target_peers) | {resolved_chat_id}:
            _file_id_cache[(peer, msg_id)] = file_id_obj
        return file_id_obj
    except Exception as e:
        print(f"Error decoding file_id for {chat_id}:{msg_id}: {e}", flush=True)
        return None

async def _fetch_chunk_bytes(client: Client, chat_id: int, msg_id: int, file_id_obj: FileId, offset: int, limit: int) -> Optional[bytes]:
    location = _get_location_from_file_id(file_id_obj)
    dc_id = file_id_obj.dc_id

    tries = 0
    while tries < 4:
        try:
            session = await _get_media_session(client, dc_id)
            r = await asyncio.wait_for(
                session.invoke(
                    raw.functions.upload.GetFile(
                        location=location,
                        offset=offset,
                        limit=limit
                    ),
                    timeout=20.0
                ),
                timeout=25.0
            )

            if isinstance(r, raw.types.upload.File):
                return r.bytes
            elif isinstance(r, raw.types.upload.FileCdnRedirect):
                cdn_session = Session(
                    client, r.dc_id, await Auth(client, r.dc_id, await client.storage.test_mode()).create(),
                    await client.storage.test_mode(), is_media=True, is_cdn=True
                )
                try:
                    await cdn_session.start()
                    r2 = await cdn_session.invoke(
                        raw.functions.upload.GetCdnFile(
                            file_token=r.file_token,
                            offset=offset,
                            limit=limit
                        ),
                        timeout=20.0
                    )
                    from pyrogram.crypto import aes
                    decrypted_chunk = aes.ctr256_decrypt(
                        r2.bytes,
                        r.encryption_key,
                        bytearray(r.encryption_iv[:-4] + (offset // 16).to_bytes(4, "big"))
                    )
                    return decrypted_chunk
                finally:
                    await cdn_session.stop()
            else:
                raw_chunk = getattr(r, "bytes", None)
                if raw_chunk is not None:
                    return raw_chunk
                return None
        except asyncio.CancelledError:
            raise
        except (asyncio.TimeoutError, TimeoutError):
            tries += 1
            await asyncio.sleep(0.3)
        except Exception as e:
            err_str = str(e)
            if "FILE_REFERENCE" in err_str or "file_reference" in err_str.lower():
                _file_id_cache.pop((chat_id, msg_id), None)
                refreshed = await _resolve_file_id(client, chat_id, msg_id)
                if refreshed:
                    file_id_obj = refreshed
                    location = _get_location_from_file_id(refreshed)
            elif "FloodWait" in type(e).__name__ or "flood" in err_str.lower():
                m = re.search(r'(\d+)', err_str)
                w = int(m.group(1)) if m else 3
                await asyncio.sleep(w + 0.5)
            elif any(x in err_str for x in ["AuthKey", "SessionRevoked", "ConnectionReset", "BrokenPipe", "Bad file descriptor", "OSError"]):
                if hasattr(client, "media_sessions"):
                    client.media_sessions.pop(dc_id, None)
                try:
                    await session.stop()
                except Exception:
                    pass
            tries += 1
            await asyncio.sleep(0.3)
    return None

async def _get_or_fetch_chunk(client: Client, chat_id: int, msg_id: int, fetch_off: int, limit: int) -> Optional[bytes]:
    key = (chat_id, msg_id, fetch_off)
    # L1: Memory Cache
    cached = _cache_get(key)
    if cached is not None:
        return cached

    # L2: Fast Local Disk Cache (0ms replay/seeking)
    if _disk_cache_dir and _disk_cache_max_mb != 0:
        chunk_file = os.path.join(_disk_cache_dir, f"{abs(chat_id)}_{msg_id}_{fetch_off}_{limit}.bin")
        if os.path.isfile(chunk_file):
            try:
                os.utime(chunk_file, None)
                with open(chunk_file, "rb") as f:
                    disk_bytes = f.read()
                if disk_bytes:
                    _cache_put(key, disk_bytes)
                    return disk_bytes
            except Exception as e:
                print(f"[DiskCache] Read error: {e}")

    # L3: Deduplicated Network Fetch
    async with _fetch_dedup_lock:
        task = _pending_fetches.get(key)
        if task is None:
            task = asyncio.create_task(_do_fetch_chunk_network(client, chat_id, msg_id, fetch_off, limit, key))
            _pending_fetches[key] = task

    try:
        return await task
    except asyncio.CancelledError:
        async with _fetch_dedup_lock:
            if _pending_fetches.get(key) is task:
                _pending_fetches.pop(key, None)
                if not task.done():
                    task.cancel()
        raise
    except Exception:
        return None
    finally:
        async with _fetch_dedup_lock:
            if _pending_fetches.get(key) is task and task.done():
                _pending_fetches.pop(key, None)

async def _do_fetch_chunk_network(client: Client, chat_id: int, msg_id: int, fetch_off: int, limit: int, key: Tuple[int, int, int]) -> Optional[bytes]:
    try:
        file_id_obj = await _resolve_file_id(client, chat_id, msg_id)
        if not file_id_obj:
            return None
        chunk = await _fetch_chunk_bytes(client, chat_id, msg_id, file_id_obj, fetch_off, limit)
        if chunk:
            _cache_put(key, chunk)
            if _disk_cache_dir and _disk_cache_max_mb != 0:
                _save_chunk_to_disk(chat_id, msg_id, fetch_off, limit, chunk)
        return chunk
    except asyncio.CancelledError:
        raise
    except Exception as e:
        print(f"[ChunkFetch] Error fetching chunk {key}: {e}")
        return None

class VirtualStreamSession:
    def __init__(self, client: Client, chat_id: int, parts: List[Dict[str, Any]], start_byte: int, end_byte: int):
        self.client = client
        self.chat_id = chat_id
        # Dynamically sort parts by partNumber and compute cumStart from sizeBytes reliably
        sorted_parts = sorted(parts, key=lambda p: int(p.get("partNumber", 1)))
        running_cum = 0
        computed = []
        for p in sorted_parts:
            p_size = int(p.get("sizeBytes") or p.get("size", 0))
            computed.append({
                "messageId": int(p.get("messageId") or p.get("message_id")),
                "partNumber": int(p.get("partNumber", 1)),
                "sizeBytes": p_size,
                "cumStart": running_cum
            })
            running_cum += p_size
        self.parts = computed
        self.start_byte = start_byte
        self.end_byte = end_byte
        self.total_to_read = max(0, end_byte - start_byte + 1)
        self.bytes_read = 0
        self.buffer = bytearray()
        # Pipeline buffer: queue maxsize dynamically based on buffer_mb (default 20MB)
        q_size = max(4, min(64, _buffer_pipeline_mb))
        self.queue: asyncio.Queue = asyncio.Queue(maxsize=q_size)
        self._closed = False
        self._eof = False
        self._last_active = time.time()
        self.tasks = self._plan_tasks()
        self.prefetch_task: Optional[asyncio.Task] = None
        self.active_tasks: Set[asyncio.Task] = set()
        print(f"[VirtualStreamSession] Init stream chat={chat_id}, parts_count={len(self.parts)}, range={start_byte}-{end_byte}, total_tasks={len(self.tasks)}, buffer_mb={_buffer_pipeline_mb}")

    def _plan_tasks(self) -> List[Dict[str, Any]]:
        CHUNK_SIZE = 1024 * 1024 # 1 MB MTProto chunk
        pos = self.start_byte
        end = self.end_byte
        tasks = []

        for p in self.parts:
            part_size = int(p.get("sizeBytes", 0))
            cum_start = int(p.get("cumStart", 0))
            cum_end = cum_start + part_size - 1

            if cum_end < pos:
                continue
            if cum_start > end:
                break

            local_start = max(pos, cum_start) - cum_start
            local_end = min(end, cum_end) - cum_start
            msg_id = int(p.get("messageId", 0))

            offset = local_start - (local_start % CHUNK_SIZE)
            first_part_cut = local_start - offset
            last_part_cut = (local_end % CHUNK_SIZE) + 1
            part_count = math.ceil((local_end + 1) / CHUNK_SIZE) - (offset // CHUNK_SIZE)

            for i in range(part_count):
                fetch_off = offset + (i * CHUNK_SIZE)
                if fetch_off >= part_size:
                    break
                curr_idx = i + 1
                if part_count == 1:
                    s_start = first_part_cut
                    s_end = last_part_cut
                elif curr_idx == 1:
                    s_start = first_part_cut
                    s_end = CHUNK_SIZE
                elif curr_idx == part_count:
                    s_start = 0
                    s_end = last_part_cut
                else:
                    s_start = 0
                    s_end = CHUNK_SIZE

                tasks.append({
                    "msg_id": msg_id,
                    "fetch_off": fetch_off,
                    "limit": CHUNK_SIZE,
                    "s_start": s_start,
                    "s_end": s_end
                })

            pos = cum_end + 1
            if pos > end:
                break

        return tasks

    def start_prefetch(self):
        if not self._closed and self.prefetch_task is None:
            self.prefetch_task = asyncio.create_task(self._prefetch_loop())

    async def _fetch_task_slice(self, t_info: Dict[str, Any]) -> Optional[bytes]:
        msg_id = t_info["msg_id"]
        fetch_off = t_info["fetch_off"]
        limit = t_info["limit"]
        s_start = t_info["s_start"]
        s_end = t_info["s_end"]

        raw_chunk = await _get_or_fetch_chunk(self.client, self.chat_id, msg_id, fetch_off, limit)
        if raw_chunk is None:
            return None

        chunk_len = len(raw_chunk)
        if s_start >= chunk_len:
            return b""
        end_idx = min(chunk_len, s_end) if s_end is not None else chunk_len
        return raw_chunk[s_start:end_idx]

    async def _prefetch_loop(self):
        in_flight: List[Tuple[int, asyncio.Task]] = []
        try:
            # Pre-resolve part 1 message file_id cleanly so chunk 0 begins downloading with 0ms delay
            if self.tasks:
                first_msg_id = self.tasks[0]["msg_id"]
                await _resolve_file_id(self.client, self.chat_id, first_msg_id)

            MAX_IN_FLIGHT = min(3, max(2, _buffer_pipeline_mb // 8))
            task_idx = 0
            total_tasks = len(self.tasks)

            while (task_idx < total_tasks or in_flight) and not self._closed:
                while task_idx < total_tasks and len(in_flight) < MAX_IN_FLIGHT and not self._closed:
                    t_info = self.tasks[task_idx]
                    t_idx = task_idx
                    task_idx += 1
                    t = asyncio.create_task(self._fetch_task_slice(t_info))
                    self.active_tasks.add(t)
                    in_flight.append((t_idx, t))

                if not in_flight:
                    break

                t_idx, next_task = in_flight.pop(0)
                try:
                    slice_bytes = await next_task
                except asyncio.CancelledError:
                    break
                except Exception as e:
                    print(f"[VirtualStreamSession] Slice error on task {t_idx}: {e}", flush=True)
                    slice_bytes = None
                finally:
                    self.active_tasks.discard(next_task)

                if self._closed:
                    break

                if slice_bytes is not None:
                    if len(slice_bytes) > 0:
                        await self.queue.put(slice_bytes)
                else:
                    # Retry task with backoff before giving up!
                    retried_ok = False
                    for retry_attempt in range(1, 4):
                        print(f"[VirtualStreamSession] Task {t_idx} returned None, retry {retry_attempt}/3...", flush=True)
                        await asyncio.sleep(0.3 * retry_attempt)
                        if self._closed:
                            break
                        try:
                            t_retry = asyncio.create_task(self._fetch_task_slice(self.tasks[t_idx]))
                            self.active_tasks.add(t_retry)
                            try:
                                slice_bytes = await t_retry
                            finally:
                                self.active_tasks.discard(t_retry)
                            if slice_bytes is not None:
                                if len(slice_bytes) > 0:
                                     await self.queue.put(slice_bytes)
                                retried_ok = True
                                break
                        except asyncio.CancelledError:
                            break
                        except Exception as e:
                            print(f"[VirtualStreamSession] Retry {retry_attempt} error on task {t_idx}: {e}", flush=True)

                    if not retried_ok:
                        print(f"[VirtualStreamSession] Task {t_idx} failed all retries, closing prefetch", flush=True)
                        break
        except asyncio.CancelledError:
            pass
        except Exception as e:
            print(f"[VirtualStreamSession] prefetch loop error: {e}", flush=True)
            traceback.print_exc()
        finally:
            for _, fut in in_flight:
                fut.cancel()
            for t in list(self.active_tasks):
                if not t.done():
                    t.cancel()
            self.active_tasks.clear()
            await self.queue.put(None)

    async def read_bytes(self, max_bytes: int) -> bytes:
        self._last_active = time.time()
        # Drain all items already queued into buffer
        while not self.queue.empty():
            item = self.queue.get_nowait()
            if item is None:
                self._eof = True
                break
            self.buffer.extend(item)

        # If buffer is empty and stream is active, wait for the first incoming chunk
        if not self.buffer and not self._closed and not self._eof:
            try:
                item = await asyncio.wait_for(self.queue.get(), timeout=30.0)
                if item is None:
                    self._eof = True
                else:
                    self.buffer.extend(item)
                    # Drain any additional items that completed in the meantime
                    while not self.queue.empty():
                        extra = self.queue.get_nowait()
                        if extra is None:
                            self._eof = True
                            break
                        self.buffer.extend(extra)
            except asyncio.TimeoutError:
                print(f"[VirtualStreamSession] Timeout waiting for stream queue", flush=True)
                self._eof = True

        if not self.buffer:
            return b""
        to_take = min(max_bytes, len(self.buffer))
        result = bytes(self.buffer[:to_take])
        del self.buffer[:to_take]
        self.bytes_read += len(result)
        return result

    def close(self):
        if not self._closed:
            self._closed = True
            self._eof = True
            if self.prefetch_task and not self.prefetch_task.done():
                self.prefetch_task.cancel()
            for t in list(self.active_tasks):
                if not t.done():
                    t.cancel()
            self.active_tasks.clear()
            self.buffer.clear()
            while not self.queue.empty():
                try:
                    self.queue.get_nowait()
                except Exception:
                    break

def create_stream(api_id: int, api_hash: str, session_str: str, chat_id: int, parts_json: str, start_byte: int, end_byte: int) -> str:
    """
    Initializes a virtual byte stream with concurrent pre-buffering and returns a unique stream_id.
    Supports concurrent HTTP 206 range requests (header demuxing, cues indexing, and playback).
    """
    global _active_streams

    async def _async_create():
        now = time.time()
        # Clean up any streams that have been idle for > 60 seconds
        expired = [sid for sid, s in list(_active_streams.items()) if now - s._last_active > 60]
        for sid in expired:
            s = _active_streams.pop(sid, None)
            if s:
                s.close()

        parts = json.loads(parts_json)
        client = await _get_connected_stream_client(api_id, api_hash, session_str)
        # Ensure channel peer access hash is resolved in Pyrogram's memory cache before tasks start
        await _ensure_channel_peer(client, int(chat_id))

        stream_id = str(uuid.uuid4())
        session = VirtualStreamSession(client, int(chat_id), parts, int(start_byte), int(end_byte))
        session.start_prefetch()
        _active_streams[stream_id] = session
        return stream_id

    try:
        return run_async(_async_create(), timeout=25)
    except Exception as e:
        print(f"Error creating stream: {e}", flush=True)
        traceback.print_exc()
        return ""

def read_stream_chunk(stream_id: str, max_bytes: int = 1048576) -> bytes:
    """
    Reads the next slice of bytes from pre-buffered queue for the given stream_id synchronously.
    Returns bytes or empty bytes(0) when finished. Matches 1MB MTProto chunking.
    """
    global _active_streams
    session = _active_streams.get(stream_id)
    if not session:
        return b""

    try:
        return run_async(session.read_bytes(int(max_bytes)), timeout=40)
    except Exception as e:
        print(f"Error reading chunk for stream {stream_id}: {e}")
        traceback.print_exc()
        return b""

def close_stream(stream_id: str):
    """
    Closes and cancels an active stream session and cleans up its prefetch buffer.
    """
    global _active_streams
    session = _active_streams.pop(stream_id, None)
    if session:
        session.close()

def check_dead_messages(api_id: int, api_hash: str, session_str: str, chat_id: int, message_ids_json: str) -> str:
    """
    Checks whether the specified message IDs in a channel still exist and contain media on Telegram.
    Returns JSON string with 'dead_ids' and 'alive_ids'.
    """
    async def _async_check():
        client = await _get_connected_stream_client(api_id, api_hash, session_str)
        peer = await _ensure_channel_peer(client, int(chat_id))

        try:
            raw_ids = json.loads(message_ids_json)
            msg_ids = [int(x) for x in raw_ids if int(x) > 0]
        except Exception as e:
            return json.dumps({"status": "error", "error": f"Invalid IDs: {e}", "dead_ids": [], "alive_ids": []})

        if not msg_ids:
            return json.dumps({"status": "ok", "dead_ids": [], "alive_ids": []})

        dead_ids = []
        alive_ids = []

        # Batch in chunks of 100 messages (Pyrogram limit per RPC call)
        batch_size = 100
        for i in range(0, len(msg_ids), batch_size):
            batch = msg_ids[i:i + batch_size]
            try:
                messages = await client.get_messages(peer, batch)
                if not isinstance(messages, list):
                    messages = [messages]

                for idx, m in enumerate(messages):
                    orig_id = batch[idx] if idx < len(batch) else (getattr(m, "id", None) or 0)
                    if m is None or getattr(m, "empty", False):
                        dead_ids.append(orig_id)
                    else:
                        has_media = bool(getattr(m, "video", None) or getattr(m, "document", None) or getattr(m, "audio", None) or getattr(m, "media", None))
                        if not has_media:
                            dead_ids.append(orig_id)
                        else:
                            alive_ids.append(orig_id)
            except Exception as e:
                print(f"Error checking message batch {batch} in chat {chat_id}: {e}", flush=True)

        return json.dumps({
            "status": "ok",
            "dead_ids": dead_ids,
            "alive_ids": alive_ids
        })

    try:
        return run_async(_async_check(), timeout=45)
    except Exception as e:
        print(f"Error checking dead messages: {e}", flush=True)
        return json.dumps({"status": "error", "error": str(e), "dead_ids": [], "alive_ids": []})

