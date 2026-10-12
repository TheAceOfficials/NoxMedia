import asyncio
import json
import threading
import uuid
import traceback
from typing import List, Dict, Any, Optional, Tuple
from pyrogram import Client, errors, raw
from pyrogram.file_id import FileId, FileType
from pyrogram.session import Session, Auth
import media_parser

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
_active_streams: Dict[str, "VirtualStreamSession"] = {}
_file_id_cache: Dict[Tuple[int, int], FileId] = {}
_media_session_lock = asyncio.Lock()

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
        if _auth_client is not None and _auth_client.is_connected:
            try:
                await _auth_client.disconnect()
            except Exception:
                pass
                
        _auth_client = Client(
            ":memory:",
            api_id=_auth_state["api_id"],
            api_hash=_auth_state["api_hash"],
            in_memory=True
        )
        await _auth_client.connect()
        sent_code = await _auth_client.send_code(clean_phone)
        _auth_state["phone_code_hash"] = sent_code.phone_code_hash
        return {
            "status": "success",
            "phone_code_hash": sent_code.phone_code_hash,
            "timeout": getattr(sent_code, "timeout", 60)
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
        return json.dumps({"status": "error", "error": str(e)})

def verify_code(code: str) -> str:
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
        
        return {
            "status": "success",
            "user_id": user.id,
            "first_name": user.first_name or "Telegram User",
            "username": user.username or f"user_{user.id}",
            "session_string": session_str
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
        return json.dumps({"status": "error", "error": str(e)})

def check_password(password: str) -> str:
    global _auth_client
    
    async def _async_check_pwd():
        global _auth_client
        if not _auth_client or not _auth_client.is_connected:
            return {"status": "error", "error": "Session expired. Please request a new code."}
            
        user = await _auth_client.check_password(password.strip())
        session_str = await _auth_client.export_session_string()
        
        return {
            "status": "success",
            "user_id": user.id,
            "first_name": user.first_name or "Telegram User",
            "username": user.username or f"user_{user.id}",
            "session_string": session_str
        }

    try:
        res = run_async(_async_check_pwd(), timeout=30)
        return json.dumps(res)
    except errors.PasswordHashInvalid:
        return json.dumps({"status": "error", "error": "Incorrect 2FA password."})
    except Exception as e:
        return json.dumps({"status": "error", "error": str(e)})

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

    # Pre-cache user's dialogs into Pyrogram's memory so all channel access hashes are known
    try:
        async for _ in _stream_client.get_dialogs(limit=100):
            pass
    except Exception as e:
        print(f"Pre-caching dialogs notice: {e}")

    return _stream_client

def parse_media(filename: str, caption: str = "") -> str:
    """Parses a media filename and returns JSON metadata."""
    res = media_parser.parse_media_filename(filename, caption)
    return json.dumps(res)

def resolve_channel(api_id: int, api_hash: str, session_str: str, identifier: str) -> str:
    """
    Connects to Telegram via Pyrogram and verifies/resolves the channel.
    Accepts @username, t.me link, -100... ID, or bare numeric ID.
    Returns JSON with channel title, real ID, username, or error.
    """
    async def _async_resolve():
        client = await _get_connected_stream_client(api_id, api_hash, session_str)
        target = identifier.strip()
        if target.startswith("https://t.me/"):
            target = target.replace("https://t.me/", "").replace("/", "").strip()
        elif target.startswith("t.me/"):
            target = target.replace("t.me/", "").replace("/", "").strip()

        # Handle numeric chat id (e.g. -100..., or bare id)
        if target.startswith("-100") and target[4:].isdigit():
            chat_target = int(target)
        elif target.startswith("-") and target[1:].isdigit():
            chat_target = int(target)
        elif target.isdigit():
            chat_target = int(f"-100{target}")
        else:
            if not target.startswith("@"):
                target = f"@{target}"
            chat_target = target

        chat = await client.get_chat(chat_target)
        title = chat.title or getattr(chat, "first_name", None) or "Telegram Channel"
        uname = getattr(chat, "username", None) or str(chat.id)

        return {
            "status": "success",
            "id": chat.id,
            "title": title,
            "username": uname,
            "type": str(getattr(chat, "type", "channel"))
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

            # Only process if video mime or video extension
            is_video = (
                mime_type.startswith("video/") or
                any(file_name.lower().endswith(ext) for ext in video_exts) or
                media_parser.parse_split_part(file_name) is not None
            )
            if not is_video or file_size <= 0:
                continue

            caption = msg.caption or ""
            parsed = media_parser.parse_media_filename(file_name, caption, is_anime=bool(is_anime))
            
            clean_base = parsed["clean_base_name"]
            is_split = parsed["is_split"]
            part_num = parsed["part_number"]

            if is_split:
                if clean_base not in split_groups:
                    split_groups[clean_base] = {
                        "parsed": parsed,
                        "mimeType": mime_type,
                        "duration": duration,
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
                    "fileName": file_name,
                    "isSplit": False,
                    "parts": []
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
                "parts": computed_parts
            }

        return list(items_map.values())

    try:
        res = run_async(_async_sync(), timeout=60)
        return json.dumps({"status": "success", "media": res})
    except Exception as e:
        traceback.print_exc()
        return json.dumps({"status": "error", "error": str(e)})

# --- Byte Streaming Across Single / Split Parts ---

async def _get_media_session(client: Client, dc_id: int) -> Session:
    if not hasattr(client, "media_sessions"):
        client.media_sessions = {}

    session = client.media_sessions.get(dc_id)
    if session:
        return session

    async with _media_session_lock:
        session = client.media_sessions.get(dc_id)
        if session:
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
            for _ in range(6):
                try:
                    exported = await client.invoke(raw.functions.auth.ExportAuthorization(dc_id=dc_id))
                    await session.send(raw.functions.auth.ImportAuthorization(id=exported.id, bytes=exported.bytes))
                    break
                except errors.AuthBytesInvalid:
                    await asyncio.sleep(0.5)
                except OSError:
                    await asyncio.sleep(1)
                except Exception:
                    break

        client.media_sessions[dc_id] = session
        return session

def _get_location_from_file_id(file_id_obj: FileId):
    if file_id_obj.file_type == FileType.PHOTO:
        return raw.types.InputPhotoFileLocation(
            id=file_id_obj.media_id,
            access_hash=file_id_obj.access_hash,
            file_reference=file_id_obj.file_reference,
            thumb_size=file_id_obj.thumbnail_size
        )
    return raw.types.InputDocumentFileLocation(
        id=file_id_obj.media_id,
        access_hash=file_id_obj.access_hash,
        file_reference=file_id_obj.file_reference,
        thumb_size=file_id_obj.thumbnail_size
    )

async def _resolve_file_id(client: Client, chat_id: int, msg_id: int) -> Optional[FileId]:
    key = (chat_id, msg_id)
    cached = _file_id_cache.get(key)
    if cached:
        return cached

    msg = None
    try:
        try:
            await client.get_chat(chat_id)
        except Exception:
            pass
        msg = await client.get_messages(chat_id, msg_id)
    except errors.PeerIdInvalid:
        try:
            async for _ in client.get_dialogs(limit=100):
                pass
            msg = await client.get_messages(chat_id, msg_id)
        except Exception as e:
            print(f"Error fetching message after get_dialogs for {chat_id}:{msg_id}: {e}")
            return None
    except Exception as e:
        print(f"Error fetching message for {chat_id}:{msg_id}: {e}")
        return None

    if not msg:
        return None
    media = msg.video or msg.document or msg.animation or msg.audio
    if not media:
        return None

    try:
        file_id_obj = FileId.decode(media.file_id)
        _file_id_cache[key] = file_id_obj
        return file_id_obj
    except Exception as e:
        print(f"Error decoding file_id for {chat_id}:{msg_id}: {e}")
        return None

class VirtualStreamSession:
    def __init__(self, client: Client, chat_id: int, parts: List[Dict[str, Any]], start_byte: int, end_byte: int):
        self.client = client
        self.chat_id = chat_id
        self.parts = sorted(parts, key=lambda p: p.get("partNumber", 1))
        self.start_byte = start_byte
        self.end_byte = end_byte
        self.total_to_read = max(0, end_byte - start_byte + 1)
        self.bytes_read = 0
        self.gen = self._stream_generator()
        self.buffer = bytearray()

    async def _stream_generator(self):
        CHUNK_SIZE = 1024 * 1024 # 1 MB MTProto chunk
        pos = self.start_byte
        end = self.end_byte

        for p in self.parts:
            part_size = p.get("sizeBytes") or p.get("size", 0)
            cum_start = p.get("cumStart") or p.get("cum_start", 0)
            cum_end = cum_start + part_size - 1

            if cum_end < pos:
                continue
            if cum_start > end:
                break

            local_start = max(pos, cum_start) - cum_start
            local_end = min(end, cum_end) - cum_start

            msg_id = p.get("messageId") or p.get("message_id")
            file_id_obj = await _resolve_file_id(self.client, self.chat_id, msg_id)
            if not file_id_obj:
                print(f"[VirtualStreamSession] Could not resolve file_id for {self.chat_id}:{msg_id}")
                continue

            dc_id = file_id_obj.dc_id
            session = await _get_media_session(self.client, dc_id)
            location = _get_location_from_file_id(file_id_obj)

            curr_pos = local_start
            while curr_pos <= local_end:
                fetch_off = curr_pos - (curr_pos % CHUNK_SIZE)
                cut = curr_pos - fetch_off
                bytes_left_in_part = local_end - curr_pos + 1
                fetch_limit = CHUNK_SIZE

                try:
                    r = await session.send(
                        raw.functions.upload.GetFile(
                            location=location,
                            offset=fetch_off,
                            limit=fetch_limit
                        )
                    )
                    raw_chunk = getattr(r, "bytes", b"") if r else b""
                except Exception as e:
                    err_str = str(e)
                    if "FILE_REFERENCE" in err_str or "file_reference" in err_str.lower():
                        _file_id_cache.pop((self.chat_id, msg_id), None)
                        refreshed = await _resolve_file_id(self.client, self.chat_id, msg_id)
                        if refreshed:
                            file_id_obj = refreshed
                            location = _get_location_from_file_id(file_id_obj)
                            r = await session.send(
                                raw.functions.upload.GetFile(
                                    location=location,
                                    offset=fetch_off,
                                    limit=fetch_limit
                                )
                            )
                            raw_chunk = getattr(r, "bytes", b"") if r else b""
                        else:
                            break
                    else:
                        print(f"[VirtualStreamSession] GetFile error at offset {fetch_off}: {e}")
                        break

                if not raw_chunk:
                    break

                if cut > 0:
                    raw_chunk = raw_chunk[cut:]

                if len(raw_chunk) > bytes_left_in_part:
                    raw_chunk = raw_chunk[:bytes_left_in_part]

                if raw_chunk:
                    yield raw_chunk
                    curr_pos += len(raw_chunk)
                    pos += len(raw_chunk)

    async def read_bytes(self, max_bytes: int) -> bytes:
        while len(self.buffer) < max_bytes:
            try:
                chunk = await self.gen.__anext__()
                self.buffer.extend(chunk)
            except StopAsyncIteration:
                break
            except Exception as e:
                print(f"[VirtualStreamSession] read_bytes iteration error -> {e}")
                break

        result = bytes(self.buffer[:max_bytes])
        self.buffer = self.buffer[max_bytes:]
        self.bytes_read += len(result)
        return result

def create_stream(api_id: int, api_hash: str, session_str: str, chat_id: int, parts_json: str, start_byte: int, end_byte: int) -> str:
    """
    Initializes a virtual byte stream and returns a unique stream_id.
    """
    global _active_streams

    async def _async_create():
        client = await _get_connected_stream_client(api_id, api_hash, session_str)
        parts = json.loads(parts_json)
        stream_id = str(uuid.uuid4())
        session = VirtualStreamSession(client, int(chat_id), parts, int(start_byte), int(end_byte))
        _active_streams[stream_id] = session
        return stream_id

    try:
        return run_async(_async_create(), timeout=20)
    except Exception as e:
        print(f"Error creating stream: {e}")
        traceback.print_exc()
        return ""

def read_stream_chunk(stream_id: str, max_bytes: int = 262144) -> bytes:
    """
    Reads the next slice of bytes for the given stream_id synchronously.
    Returns bytes or empty bytes(0) when finished.
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
    Closes and removes an active stream session.
    """
    global _active_streams
    session = _active_streams.pop(stream_id, None)
    if session and hasattr(session, "gen"):
        async def _close():
            try:
                await session.gen.aclose()
            except Exception:
                pass
        try:
            run_async(_close(), timeout=5)
        except Exception:
            pass
