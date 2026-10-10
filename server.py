import asyncio
import os
import uvicorn
from fastapi import FastAPI, HTTPException
from pyrogram import Client, errors

app = FastAPI(title="TeleStremio Core Auth Server")

# Initialize Pyrogram Client (it will create a tg_session.session file)
API_ID = os.environ.get("TG_API_ID", "0") # Replace with your API ID or set env
API_HASH = os.environ.get("TG_API_HASH", "")

tg_client = Client("tg_session", api_id=int(API_ID), api_hash=API_HASH, in_memory=False)

# Global Auth State
auth_state = {
    "phone_number": "",
    "phone_code_hash": "",
    "is_logged_in": False
}

@app.get("/")
async def root():
    return {"status": "running", "logged_in": auth_state["is_logged_in"]}

@app.post("/auth/sendCode")
async def send_code(phone_number: str):
    if not API_HASH:
        raise HTTPException(status_code=400, detail="API_ID and API_HASH not configured in server.")
    
    await tg_client.connect()
    try:
        sent_code = await tg_client.send_code(phone_number)
        auth_state["phone_number"] = phone_number
        auth_state["phone_code_hash"] = sent_code.phone_code_hash
        return {"status": "success", "phone_code_hash": sent_code.phone_code_hash}
    except Exception as e:
        await tg_client.disconnect()
        raise HTTPException(status_code=400, detail=str(e))

@app.post("/auth/verifyCode")
async def verify_code(code: str):
    if not auth_state["phone_code_hash"]:
        raise HTTPException(status_code=400, detail="Must call sendCode first")
    
    try:
        user = await tg_client.sign_in(
            auth_state["phone_number"], 
            auth_state["phone_code_hash"], 
            code
        )
        auth_state["is_logged_in"] = True
        
        # Export Session String so Android can use it
        session_string = await tg_client.export_session_string()
        
        await tg_client.disconnect()
        return {
            "status": "success", 
            "user_id": user.id, 
            "first_name": user.first_name,
            "session_string": session_string
        }
    except Exception as e:
        await tg_client.disconnect()
        raise HTTPException(status_code=400, detail=str(e))

if __name__ == "__main__":
    print("Starting TeleStremio Core Server...")
    print("If you haven't installed requirements: pip install fastapi uvicorn pyrogram tgcrypto")
    uvicorn.run(app, host="0.0.0.0", port=7789)
