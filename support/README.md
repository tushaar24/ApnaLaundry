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
- **Call** goes through MyLaundry (Vobiz, since 1.1): the server rings the
  agent's phone, then the shop owner's, from the MyLaundry number. The server
  records the call, saves it to Supabase Storage and has Sarvam write it down;
  the notes screen follows the call live and shows the recording and "What was
  said" when they're ready. Talk time and No answer / Busy come from Vobiz.
  The agent's number must be in the backend's `LAUNDRY_VOBIZ_AGENT_PHONES`.
- **Incoming calls** (1.2): when an owner calls the MyLaundry number, the server
  rings the agent who last talked to them, then every agent, then takes a
  voicemail. While the phone rings, the app shows "<shop> is calling" with the
  shop's plan, orders and last call (needs phone + notification permissions);
  on picking up, Vobiz also says the shop's name. The call is logged under the
  shop as Incoming (or Missed call / left a message), recorded and transcribed;
  calls from numbers that aren't a shop show as "new number" in Calls.
- **WhatsApp** (1.3): the MyLaundry number's chats, from admin.mylaundry.work
  (sign in once with the admin site's login): read, reply with text / photo /
  file / template (templates only after the 24-hour window), WhatsApp button on
  each shop. Each new message shows as a notification (Firebase push from the
  server) — needs `support/google-services.json` (Firebase project
  shweta-makeover, app `com.dailyworks.mylaundry.support`) at build time and the
  backend's `LAUNDRY_FCM_SERVICE_ACCOUNT_B64`; without them everything else works.
- **Reminders** (1.4): "Remind me" on a shop or after a call (Call / Follow up,
  quick times or any date + time, note; picking "Call back later" offers it).
  Shared by the team on the server; the setter's phone rings an alarm at the
  time. Calls shows **Scheduled today** (overdue included) with Call / Done /
  Reschedule / Delete, above the call log; calling from a reminder ticks it off.
- **Call queue** (1.4): Shops › Select › tick owners › Call queue, or Calls ›
  Call all on today's scheduled — then Call, Call, Call: the next one is ready
  after each call's notes. Kept on the phone.
- **Shop details** (1.4): with the admin login (WhatsApp tab), each shop page
  shows what admin.mylaundry.work shows — total orders and sales, earnings,
  customers + baaki, recent orders, services, CleverTap identity / ID / props.
- While calling through MyLaundry isn't set up on the server, Call falls back to
  the phone's dialer: the call's real talk time comes from the call log, and the
  dialer's recording is found and uploaded in the background. Unanswered calls
  are marked "No answer" automatically.

No login and no API key (owner decision): each agent types their name and
mobile number once (name shown on their calls; the number is the phone that
rings when they tap Call).

## Build

Build with Android Studio's JDK (see the main app's notes):

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
