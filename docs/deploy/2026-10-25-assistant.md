# Assistant ("Ask") — Sarvam AI in the Android app (2026-10-25)

**What it is.** A new "Ask" screen (Home → dark **Ask** button, bottom left). The owner taps the mic and speaks, or types, in Hindi, Hinglish, English or any Indian language. It can:
- create an order (from the rate card), new customer included;
- answer questions about sales and analytics, an order's status, or a customer's khata and details;
- send a bill on WhatsApp;
- send order status updates on WhatsApp;
- send a baaki reminder;
- send any WhatsApp message, including to the owner's own Status;
- mark an order picked up or ready.

**Nothing is saved or sent without a tap.** Every write shows as a card with a button:
- **Save order** (it checks the free-orders limit first);
- **Send bill on WhatsApp**;
- **Send to … on WhatsApp**;
- **Remind on WhatsApp**;
- **Open WhatsApp**;
- **Mark ready** / **Mark picked up**. This is followed by the usual "Tell the customer?" dialog.

## How it works

- **App (1.0.16):** `ui/screens/assistant/` — `AssistantTools.kt` (13 tools), `AssistantViewModel.kt` (the loop and system prompt), `AssistantScreen.kt` and `VoiceRecorder.kt`. The API client is `data/assistant/AssistantApi.kt`.
  - Every tool runs on the phone against the shop's Room data. It uses the same maths as the screens: `LaundryMath`, `EarningsMath`, `StatusMessage`, `BillShare`.
  - Orders are saved through `ShopViewModel.saveOrder`, exactly like New order.
  - The model only sees what the tools return for that question.
- **Server:** `backend/routes/laundry-assistant.js`, mounted at `/api/laundry/assistant`. It is a logged-in relay, so the Sarvam key never ships in the APK.
  - `GET /config` → `{ enabled }`. The app saves it; Home shows **Ask** only when it's true.
  - `POST /chat` `{ messages, tools }` → Sarvam `/v1/chat/completions`, model `sarvam-105b-conversations` (about 0.3–0.8 s per call). The server caps `max_tokens` at 1024 and checks the request's size.
  - `POST /transcribe` `{ audio: base64 m4a }` → Sarvam `/speech-to-text`, `saaras:v4`, mode `translit`. Hindi comes back in English letters ("Ramesh ke teen shirt…"), so it matches customer names.

## Server settings (`backend/.env`)

| Var | Value | Meaning |
|---|---|---|
| `SARVAM_API_KEY` | set (not in git) | unset = assistant off everywhere (`/config` says false, calls get 403) |
| `LAUNDRY_ASSISTANT_PCT` | 100 | % of shops (stable hash of user id, `assistant_v1`) |
| `LAUNDRY_ASSISTANT_PHONES` | — | comma-separated 10-digit numbers that always get it (QA when PCT < 100) |
| `LAUNDRY_ASSISTANT_DAILY_LIMIT` | 150 | chat calls per user per IST day (one question = 1–3 calls); then 429 "back tomorrow" |
| `LAUNDRY_ASSISTANT_VOICE_LIMIT` | 60 | voice notes per user per IST day |
| `LAUNDRY_ASSISTANT_MODEL` / `_STT_MODEL` / `_STT_MODE` | defaults | swap models without an app release |

Change one, then `docker compose -f docker-compose.prod.yml up -d backend`. The app picks up `enabled` at its next start.

## Deployed 2026-10-25 ~15:25 IST

Done from the job, on the owner's ask ("make the backend is deployed"):

```sh
# server tree == running container (56 files, md5) checked first
scp docs/deploy/2026-10-25-backend-assistant.patch courses-aws:/tmp/laundry-assistant.patch
ssh courses-aws 'cd ~/shweta-makeup-monorepo && patch -p1 --dry-run < /tmp/laundry-assistant.patch && patch -p1 < /tmp/laundry-assistant.patch'
# backend/.env backed up to backend/.env.bak-assistant-20261025, then the vars above appended
# server-local commit 866fae3 (app.js + routes/laundry-assistant.js only)
docker compose -f docker-compose.prod.yml up -d --build backend
```

Checked on prod with the demo account (1000000055):
- `/config` → 200 `enabled:true`;
- `/chat` → 200 (Sarvam replied in 275 ms);
- no token → 401;
- `/experiments`, `/billing/status`, `/shops`, `/sync/pull`, `/health` → still 200.

**Rollback:** remove `SARVAM_API_KEY` from `backend/.env` and recreate the backend. Ask disappears from Home at the next app start, and everything else is untouched. To remove the code: `git -C backend revert 866fae3` on the server, then rebuild.

## Backward compatibility

- **Server:** the change is additive. A new route file plus one `app.use` line before the generic `/api/laundry` router. No migration, and no change to any existing response.
- **Older apps and the website** never call `/assistant`, so nothing changes for them.
- **App 1.0.16 against a server without the route:** `/config` returns 404, so the app saves "off" and Home looks exactly like 1.0.15.
- **Offline:** the app keeps the last saved value.
- **App data:** no change to synced data or Room. Orders made by the assistant are ordinary orders.
- **New permission:** `RECORD_AUDIO`. It is asked only when the owner first taps the mic; typing works without it.
  - Play Console: update the Data safety form. Audio is collected, sent to Sarvam AI to turn into text, and not stored.

Events: `docs/analytics-events.md` → "Assistant".
