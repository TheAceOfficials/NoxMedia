import asyncio
import json
from pyrogram import Client, errors

# Global persistent client instance for authentication
_client = None
_auth_state = {
    "api_id": 0,
    "api_hash": "",
    "phone": "",
    "phone_code_hash": ""
}

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

def send_code(api_id: int, api_hash: str, phone_number: str) -> str:
    """
    Sends official Telegram login code to the phone number.
    Returns JSON string with {status: 'success', phone_code_hash: ...} or {status: 'error', error: ...}
    """
    global _client, _auth_state
    
    clean_phone = phone_number.strip().replace(" ", "")
    _auth_state["api_id"] = int(api_id)
    _auth_state["api_hash"] = api_hash.strip()
    _auth_state["phone"] = clean_phone
    
    loop = _get_or_create_loop()
    
    async def _async_send():
        global _client
        if _client is not None and _client.is_connected:
            try:
                await _client.disconnect()
            except Exception:
                pass
                
        _client = Client(
            ":memory:",
            api_id=_auth_state["api_id"],
            api_hash=_auth_state["api_hash"],
            in_memory=True
        )
        await _client.connect()
        sent_code = await _client.send_code(clean_phone)
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
    """
    Verifies the login code sent to the user's Telegram app.
    Returns JSON string with user session details or 2FA required.
    """
    global _client, _auth_state
    loop = _get_or_create_loop()
    
    async def _async_verify():
        global _client
        if not _client or not _client.is_connected:
            return {"status": "error", "error": "Session expired. Please request a new code."}
            
        clean_code = code.strip()
        user = await _client.sign_in(
            _auth_state["phone"],
            _auth_state["phone_code_hash"],
            clean_code
        )
        
        session_str = await _client.export_session_string()
        
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
    """
    Verifies 2FA password if user has 2-step verification enabled.
    """
    global _client
    loop = _get_or_create_loop()
    
    async def _async_check_pwd():
        global _client
        if not _client or not _client.is_connected:
            return {"status": "error", "error": "Session expired. Please request a new code."}
            
        user = await _client.check_password(password.strip())
        session_str = await _client.export_session_string()
        
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
