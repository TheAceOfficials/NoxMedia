import asyncio
import json
import uuid
from typing import List, Dict, Any, Optional
from pyrogram import Client, errors
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

# Active streaming sessions
_active_streams: Dict[str, "VirtualStreamSession"] = {}

def _get_or_create_loop():
    try:
        loop = asyncio.get_event_loop()
        if loop.is_closed():
            loop = asyncio.new_event_loop()
            asyncio.set_event_loop(loop)
    except RuntimeError:
        loop = asyncio.new_event_loop()
        asyncio.set_event_loop(loop)
    return loop

# --- Authentication Bridge ---

def send_code(api_id: int, api_hash: str, phone_number: str) -> str:
    global _auth_client, _auth_state
    
    clean_phone = phone_number.strip().replace(" ", "")
    _auth_state["api_id"] = int(api_id)
    _auth_state["api_hash"] = api_hash.strip()
    _auth_state["phone"] = clean_phone
    
    loop = _get_or_create_loop()
    
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
        res = loop.run_until_complete(_async_send())
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
    loop = _get_or_create_loop()
    
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
        res = loop.run_until_complete(_async_verify())
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
    loop = _get_or_create_loop()
    
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
        res = loop.run_until_complete(_async_check_pwd())
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
    return _stream_client

def parse_media(filename: str, caption: str = "") -> str:
    """Parses a media filename and returns JSON metadata."""
    res = media_parser.parse_media_filename(filename, caption)
    return json.dumps(res)

def sync_channel_media(api_id: int, api_hash: str, session_str: str, channel_id: int, limit: int = 150) -> str:
    """
    Scans recent messages from channel, extracts video and document media,
    parses filenames, groups split files into virtual items, and returns JSON.
    """
    loop = _get_or_create_loop()

    async def _async_sync():
        client = await _get_connected_stream_client(api_id, api_hash, session_str)
        chat_id = int(channel_id)
        
        items_map = {} # clean_base_name or id -> item
        split_groups = {} # clean_base_name -> list of parts

        video_exts = (".mkv", ".mp4", ".avi", ".ts", ".m4v", ".mov", ".webm")
        
        async for msg in client.get_chat_history(chat_id, limit=int(limit)):
            media = msg.video or msg.document or msg.animation
            if not media:
                continue

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
            parsed = media_parser.parse_media_filename(file_name, caption)
            
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
        res = loop.run_until_complete(_async_sync())
        return json.dumps({"status": "success", "media": res})
    except Exception as e:
        return json.dumps({"status": "error", "error": str(e)})

# --- Byte Streaming Across Single / Split Parts ---

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

            offset_chunk = local_start // CHUNK_SIZE
            first_cut = local_start % CHUNK_SIZE
            part_bytes_needed = local_end - local_start + 1

            msg_id = p.get("messageId") or p.get("message_id")
            msg = await self.client.get_messages(self.chat_id, msg_id)
            if not msg:
                continue

            media = msg.video or msg.document or msg.animation
            if not media:
                continue

            total_chunks = ((local_end // CHUNK_SIZE) - offset_chunk) + 1
            remaining_in_part = part_bytes_needed

            async for raw_chunk in self.client.stream_media(msg, offset=offset_chunk, limit=total_chunks):
                if first_cut > 0:
                    raw_chunk = raw_chunk[first_cut:]
                    first_cut = 0

                if len(raw_chunk) > remaining_in_part:
                    raw_chunk = raw_chunk[:remaining_in_part]

                if raw_chunk:
                    yield raw_chunk
                    remaining_in_part -= len(raw_chunk)
                    pos += len(raw_chunk)

                if remaining_in_part <= 0:
                    break

    async def read_bytes(self, max_bytes: int) -> bytes:
        while len(self.buffer) < max_bytes:
            try:
                chunk = await self.gen.__anext__()
                self.buffer.extend(chunk)
            except StopAsyncIteration:
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
    loop = _get_or_create_loop()

    async def _async_create():
        client = await _get_connected_stream_client(api_id, api_hash, session_str)
        parts = json.loads(parts_json)
        stream_id = str(uuid.uuid4())
        session = VirtualStreamSession(client, int(chat_id), parts, int(start_byte), int(end_byte))
        _active_streams[stream_id] = session
        return stream_id

    try:
        return loop.run_until_complete(_async_create())
    except Exception as e:
        print(f"Error creating stream: {e}")
        return ""

def read_stream_chunk(stream_id: str, max_bytes: int = 65536) -> bytes:
    """
    Reads the next slice of bytes for the given stream_id synchronously.
    Returns bytes or empty bytes(0) when finished.
    """
    global _active_streams
    session = _active_streams.get(stream_id)
    if not session:
        return b""

    loop = _get_or_create_loop()
    try:
        return loop.run_until_complete(session.read_bytes(int(max_bytes)))
    except Exception as e:
        print(f"Error reading chunk for stream {stream_id}: {e}")
        return b""

def close_stream(stream_id: str):
    """
    Closes and removes an active stream session.
    """
    global _active_streams
    _active_streams.pop(stream_id, None)
