import asyncio
from pyrogram import Client

async def generate():
    print("=" * 50)
    print("  Telegram Session String Generator (100% Safe)")
    print("=" * 50)
    
    api_id_str = input("\nEnter API ID: ").strip()
    api_hash = input("Enter API HASH: ").strip()
    
    if not api_id_str or not api_hash:
        print("Error: API ID and API HASH cannot be empty.")
        return
        
    api_id = int(api_id_str)
    
    print("\nConnecting to Telegram official servers...")
    async with Client(":memory:", api_id=api_id, api_hash=api_hash, in_memory=True) as app:
        session_str = await app.export_session_string()
        
        print("\n" + "=" * 50)
        print("  ✓ SUCCESS! YOUR SESSION STRING:")
        print("=" * 50)
        print(f"\n{session_str}\n")
        print("=" * 50)
        
        # Also send to Saved Messages for easy copy-paste on mobile
        try:
            await app.send_message("me", f"**NoxMedia Session String:**\n\n`{session_str}`")
            print("✓ Sent to your Telegram 'Saved Messages' chat as well!")
        except Exception:
            pass
            
        print("\nCopy this string and paste it into NoxMedia app.\n")

if __name__ == "__main__":
    asyncio.run(generate())
