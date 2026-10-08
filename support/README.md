# MyLaundry Support (customer-service app)

Internal Android app for the team that calls MyLaundry shop owners. Installed
as an APK on team phones (not on Play). Backend: `/api/laundry/support/*` in the
backend repo (`routes/laundry-support.js`).

## What it does

- **Shops** — every shop owner, with search, billing-state chips (with counts),
  filters (setup, orders, last active, called / never called, called by, call
  tags) and sorting (e.g. "Not called longest").
- **Shop detail** — billing, payments, usage, full call history with playback,
  and a **Call** button.
- **Calls** — every call by the team (or just mine), newest first.
- **Tags** — create / rename / recolour / delete call tags.
- After each call the notes screen opens: outcome, notes, tags. The call's real
  talk time comes from the call log, and the dialer's recording is found and
  uploaded to Supabase Storage in the background. Unanswered calls are marked
  "No answer" automatically.

No login: each agent types their name once (shown on their calls). The app
talks to the backend with a shared key.

## Build

1. Put the server's `LAUNDRY_SUPPORT_KEY` in the repo's `local.properties`
   (gitignored):

   ```
   support.apiKey=<same value as LAUNDRY_SUPPORT_KEY on the server>
   ```

2. Build (with Android Studio's JDK, see the main app's notes):

   ```
   ./gradlew :support:assembleRelease
   ```

   APK: `support/build/outputs/apk/release/support-release.apk` (debug-signed —
   fine for sideloading). Share it to team phones and install.

## Call recording on team phones

Android doesn't let apps record calls themselves, so the phone's own dialer
records and this app uploads that file:

1. In the phone's dialer, turn on **automatic call recording for all calls**.
2. In the app's setup, pick the folder the dialer saves recordings in:
   Samsung `Recordings/Call` · Xiaomi/Redmi `MIUI/sound_recorder/call_rec` ·
   Realme/Oppo/OnePlus `Music/Recordings/Call Recordings` or `Recordings/Call` ·
   Vivo `Record/Call`.
3. Google's Phone app (Pixel, Motorola, some Nokia) keeps recordings private —
   those phones can't upload recordings. Use another brand for the team.

If a recording isn't found (40 min of retries), the call shows "Recording not
found" and the agent can attach the file from the call's notes.
