# Deploy: MyLaundry WhatsApp Business webhook (2026-10-10)

Meta Cloud API webhook for the MyLaundry WhatsApp Business number, built like
the course site's `routes/whatsapp.js` but with its own Meta app, env and table.
Backend only.

- **Route** `routes/laundry-whatsapp.js`, mounted in `app.js` at
  `/api/laundry/whatsapp` (before the token-gated `/api/laundry` router, with a
  raw-body JSON parser):
  - `GET /webhook` — `hub.mode=subscribe` + `hub.verify_token` =
    `LAUNDRY_WHATSAPP_VERIFY_TOKEN` → echoes `hub.challenge`; else 403.
  - `POST /webhook` — checks `X-Hub-Signature-256` (HMAC-SHA256 of the raw body,
    key `LAUNDRY_WHATSAPP_APP_SECRET`; 403 until it's set), stores every inbound
    message (idempotent on the wamid) and every sent/delivered/read/failed
    status, then 200.
- **Table** `laundry_whatsapp_messages` (migration
  `20261010200000_laundry_whatsapp_messages`): wamid, our `phone_number_id`,
  counterparty `wa_id` + 10-digit `phone`, `laundry_user_id` when the number
  belongs to a shop owner, direction, type, body, status, error code/title,
  contact name, media id/mime, raw payload.
- **Env** (`backend/.env`):
  ```
  LAUNDRY_WHATSAPP_VERIFY_TOKEN=<random, also pasted into Meta>
  LAUNDRY_WHATSAPP_APP_SECRET=<Meta app → App settings → Basic → App secret>
  LAUNDRY_WHATSAPP_ACCESS_TOKEN=      # for sending, later
  LAUNDRY_WHATSAPP_PHONE_NUMBER_ID=   # for sending, later
  ```

Meta → WhatsApp → Configuration: Callback URL
`https://shwetamakeover.online/api/laundry/whatsapp/webhook`, Verify token as
above, then subscribe the `messages` field. (`mylaundry.work` only proxies to the
website, so the API host is used.)

## Backend (courses-aws, `~/shweta-makeup-monorepo/backend`)

Live 2026-10-10 16:23 UTC, server `main` after b965f18; file backup
`~/backend-filebak/20261010-162301/`.

```bash
scp docs/deploy/2026-10-10-backend-laundry-whatsapp.patch courses-aws:/tmp/
ssh courses-aws
cd ~/shweta-makeup-monorepo/backend
patch -p1 --dry-run < /tmp/2026-10-10-backend-laundry-whatsapp.patch
patch -p1 < /tmp/2026-10-10-backend-laundry-whatsapp.patch
# .env: LAUNDRY_WHATSAPP_VERIFY_TOKEN=..., LAUNDRY_WHATSAPP_APP_SECRET=...
cd .. && docker compose -f docker-compose.prod.yml run --rm --build migrate
docker compose -f docker-compose.prod.yml up -d --build backend
```

After adding/changing an env value only:
`docker compose -f docker-compose.prod.yml up -d --force-recreate --no-deps backend`.
