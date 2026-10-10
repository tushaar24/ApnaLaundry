# Deploy: cancel subscription fixes (2026-10-10)

Two bugs in Settings → Subscription → Cancel:

1. **Lost paid period (clients).** The backend gives a cancelled plan access
   until the period it paid for ends (`paywallDue: false`), but the web and
   Android gates only let `hasActiveSubscription` in, so a cancelled shop hit
   the paywall on its next launch. Both gates now also let in a **cancelled**
   plan whose `paywallDue` is false (only `cancelled` — "billing unconfigured"
   still can't get in). The offline "active" cache counts that grace too.
2. **Silent cancel failure (backend).** If Razorpay's cancel call failed, the
   route logged it and marked the plan cancelled anyway — the mandate stayed
   live, Razorpay could keep charging, and the webhook ignores events for
   cancelled rows. Now it re-fetches the subscription: if Razorpay says it's
   already over (cancelled/completed/expired) it carries on, otherwise it
   returns `502 CANCEL_FAILED` and leaves the plan untouched. Both clients
   already show the server's `message` ("Could not cancel right now — please
   try again").

Order: **backend** (no schema change), then the **website**. Android ships with
the next Play build.

## 1. Backend (courses-aws, `~/shweta-makeup-monorepo/backend`)

`2026-10-10-backend-cancel-safe.patch` was diffed against
`routes/laundry-billing.js` as it is on the server today (copied via ssh).

```bash
scp docs/deploy/2026-10-10-backend-cancel-safe.patch courses-aws:/tmp/
ssh courses-aws
cd ~/shweta-makeup-monorepo/backend
patch -p1 --dry-run < /tmp/2026-10-10-backend-cancel-safe.patch   # must apply cleanly
patch -p1 < /tmp/2026-10-10-backend-cancel-safe.patch
cd .. && docker compose -f docker-compose.prod.yml up -d --build backend
git add backend/routes/laundry-billing.js
git commit -m "fix(laundry): don't mark a subscription cancelled unless Razorpay cancelled it"
```

## 2. Website

Usual rsync of `web/` to `courses-aws:/home/ubuntu/apnalaundry-web/` then
`docker compose -f docker-compose.prod.yml up -d --build laundry`.
