# NoxMedia 🚀
### Standalone Android Stremio Local Server for Telegram

**NoxMedia** is a standalone Android application that turns your mobile device into a high-speed local media streaming server for **Stremio**. Stream movies, TV series, and media files directly from your Telegram channels to any device (Android TV, Firestick, PC, Mac, Phone) on your local Wi-Fi network — **without needing any PC or external server running!**

---

## 🌟 Key Features

- 📱 **100% Standalone on Android:** Runs directly on your phone as a persistent Foreground Service. No PC or VPS required.
- ⚡ **Zero-OTP Session String Login:** 100% reliable login without OTP delivery failures, SMS rate-limits, or `FLOOD_WAIT` restrictions.
- 📺 **Universal Stremio Compatibility:** Works seamlessly with Stremio on Android TV, Google TV, Fire TV Stick, Windows, macOS, Linux, and iOS/Web.
- 🚀 **High-Speed HTTP 206 Streaming:** Built-in byte-range partial content engine for instant video seeking and playback.
- 📡 **Local Wi-Fi Broadcast:** Generates a universal `manifest.json` addon URL (e.g., `http://192.168.1.15:7788/manifest.json`) accessible by any device on your home network.

---

## 📋 Quick Setup Workflow

```
[1. Get API Credentials] ──▶ [2. Generate Session String] ──▶ [3. Paste in NoxMedia & Start] ──▶ [4. Stream in Stremio]
   (my.telegram.org)           (Mobile Bot or PC Script)          (Turn On Foreground Service)     (Addon Manifest URL)
```

---

## 🔑 Step 1: Get Telegram API ID & Hash (Takes 30 Seconds)

1. Open your browser and go to [my.telegram.org](https://my.telegram.org).
2. Log in with your phone number and the confirmation code sent to your Telegram app.
3. Tap on **"API development tools"**.
4. Create a new application (enter any app name and short name).
5. Copy your **`api_id`** (numbers) and **`api_hash`** (32-character string).

---

## 📱 Step 2: How to Generate Telegram Session String

A **Session String** is a secure, persistent token that lets NoxMedia stream media from your account without asking for phone codes every time.

### Option A: 1-Click Google Colab (Safest & 100% Transparent — Mobile & PC) ⭐ *Recommended*

[![Open In Colab](https://colab.research.google.com/assets/colab-badge.svg)](https://colab.research.google.com/github/TheAceOfficials/NoxMedia/blob/main/string_session.ipynb)

Run the generator in your own private Google Cloud sandbox without installing anything on your device:
1. Tap on the **[Open in Colab](https://colab.research.google.com/github/TheAceOfficials/NoxMedia/blob/main/string_session.ipynb)** button above.
2. Tap the **▶ Run** button on the code block.
3. Enter your `API ID`, `API HASH`, and `Phone Number`.
4. Enter the Telegram login OTP.
5. Your session string is printed and sent straight to your **Saved Messages**!

---

### Option B: On Mobile via Telegram Bot

1. Open your Telegram app and search for:
   - **`@StringSessionRobot`** or **`@SessionStringGeneratorBot`**
2. Tap **START** (`/start`).
3. Select **Pyrogram** (recommended) or **Telethon**.
4. When prompted, provide:
   - Your **API ID** (from Step 1)
   - Your **API HASH** (from Step 1)
   - Your **Phone Number** in international format (e.g., `+919876543210`)
5. Telegram will send an official login code to your Telegram app. Enter this code into the bot.
   *(If you have 2-Step Verification enabled, send your 2FA password).*
6. The bot will instantly generate your **Session String** and send it directly to your **Saved Messages** chat!
7. Long-press the session string token in Saved Messages and tap **Copy**.

> 💡 **Mobile Alternative (Termux on Android):**  
> If you have Termux installed on your Android device:  
> ```bash
> pkg update && pkg install python -y
> pip install pyrogram tgcrypto
> python -m pyrogram
> ```  
> Follow the on-screen prompt, and your session string will be printed right in Termux.

---

### Option B: On PC (Windows / Linux / macOS)

If you are on a computer, you can generate your string in 1 minute using Python:

#### 1. Open PowerShell (Windows) or Terminal (Linux/macOS) and install Pyrogram:
```bash
pip install pyrogram tgcrypto
```

#### 2. Run the 1-Line Generator:
```bash
python -m pyrogram
```
Enter your `API_ID`, `API_HASH`, and Phone number when prompted.

#### Or use this Quick Python Script:
```python
from pyrogram import Client

api_id = int(input("Enter API ID: "))
api_hash = input("Enter API HASH: ")

with Client("temp_session", api_id=api_id, api_hash=api_hash, in_memory=True) as app:
    session_string = app.export_session_string()
    print("\n--- YOUR SESSION STRING ---")
    print(session_string)
    print("---------------------------\n")
    # Send it directly to your Telegram Saved Messages
    app.send_message("me", f"**Your NoxMedia Session String:**\n`{session_string}`")
    print("✓ Also sent directly to your Telegram Saved Messages!")
```

Copy the generated session string token and paste it into NoxMedia.

---

## 🚀 Step 3: Connect in NoxMedia App & Start Server

1. Open **NoxMedia** on your Android phone.
2. Enter your **API ID** & **API Hash** (saved permanently).
3. On the **Connect Telegram** screen:
   - Tap the **Paste** button to paste your copied Session String.
   - Tap **Connect & Start Server**.
4. NoxMedia will instantly validate and connect to your Telegram account!
5. On the **Home Screen**, tap the **Server Switch** to toggle the server **ON**.
6. You will see your local manifest URL:
   ```
   http://192.168.1.15:7788/manifest.json
   ```

---

## 📺 Step 4: Stream in Stremio (Any Device)

1. Open **Stremio** on your Smart TV, Firestick, PC, Laptop, or Phone.
   *(Ensure the device is connected to the same Wi-Fi network as your phone).*
2. Go to **Addons** (Puzzle Icon).
3. Tap on **"Add Addon"** / paste the search bar.
4. Paste your server URL:
   ```
   http://<YOUR_PHONE_IP>:7788/manifest.json
   ```
5. Click **Install**.
6. Done! All media from your monitored Telegram channels will now appear in your Stremio library with rich metadata, posters, and instant seekable streaming!

---

## ⚙️ Best Practices for Android Background Streaming

- **Disable Battery Optimization:** In your phone's Android Settings -> Apps -> NoxMedia -> Battery -> Select **"Unrestricted"**. This prevents Android from putting the server to sleep when your screen is locked.
- **Keep Wi-Fi Active:** Make sure your phone stays connected to Wi-Fi while streaming. NoxMedia automatically holds a high-performance Wi-Fi wake lock while the server is active.

---

## ❓ Frequently Asked Questions (FAQ)

### Q: Why use Session String instead of Phone OTP login in the app?
**A:** Telegram restricts third-party client logins over raw HTTP and aggressively blocks phone numbers with `FLOOD_WAIT` or SMS delivery failure. A Session String authenticates directly via Telegram's official cryptographic MTProto protocol, bypassing all OTP and rate-limit issues.

### Q: Can my session string expire?
**A:** No, Telegram session strings remain valid indefinitely until you manually terminate the session from your Telegram app (*Settings -> Devices -> Terminate Session*).

### Q: Is my Telegram account safe?
**A:** Yes! The Session String stays 100% on your local Android device inside secure app storage (`SharedPreferences`). It is never sent to any external server or third party.

---

## 📄 License
MIT License. Built for the community.
