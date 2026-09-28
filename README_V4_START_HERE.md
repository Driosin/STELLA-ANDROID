# STELLA V4 — START HERE

This is a clean Android V4 project. It is designed to work in two modes:

- **Standalone:** phone uses its own Groq API key and persistent local memory.
- **Laptop connected:** phone talks to Stella V4 on the laptop over the local network.

## IMPORTANT: uploading to GitHub

GitHub will not build a ZIP file as an Android project. Do this once:

1. Extract this ZIP on your PC.
2. Open the extracted `STELLA-ANDROID-V4-READY` folder.
3. Select **everything inside that folder** (including the `app` folder and `.github` folder).
4. In your `STELLA-ANDROID` GitHub repo, choose **Add file → Upload files**.
5. Drag those files/folders into GitHub and commit them to `main`.
6. Go to **Actions → Build Stella V4 APK → Run workflow**.

Do NOT upload only `build.gradle.kts`, `settings.gradle.kts`, or `gradle.properties`. The `app` folder is required.

## First laptop connection

The PC Stella V4 project already contains `sync_server.py`. Run `PC_ENABLE_SYNC.py` once in the PC Stella folder so the GUI starts that server automatically.

Then:

1. Start Stella V4 on the laptop.
2. Look at the Stella log for the **Phone Sync** one-time 6-digit pairing code.
3. Open the Android app.
4. Tap **PAIR / LAPTOP**.
5. Enter the 6-digit code.
6. Leave the IP blank if the phone and laptop are on the same Wi-Fi.

After pairing, the app saves the token and laptop address. It tries the laptop automatically when the app starts.

## Memory behavior

The phone keeps memories locally in private Android storage.

When the laptop is available, the app:

- imports saved PC facts from Stella's sync database;
- sends new phone memories to the laptop Stella;
- uses the laptop's normal persistent memory when chatting through the PC.

Example:

Phone: `Remember that my name is Laksh.`

The phone stores the fact immediately. When the laptop is connected, it syncs that fact to the PC Stella memory.

If you tell the laptop `My name is Laksh`, the next phone sync imports that PC fact too.

## Standalone mode

If the laptop is unavailable, the phone automatically falls back to its own Groq connection.

Tap **PHONE API KEY** once and enter your Groq key. The key is stored only in the app's private preferences; it is not included in this project or GitHub.

## Security note

The laptop sync server is intended for your trusted local network. Do not expose its port directly to the public internet.
