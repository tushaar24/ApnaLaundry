# Deploy: Android free orders, no trial (2026-10-12)

The Android app drops the post-login paywall and the ₹2 trial. A shop logs in,
sets up and makes **5 free orders** (never mentioned in the app). Opening New
order after that shows "Your free orders are over" → the paywall (Yearly picked,
Monthly offered), and the plan is **charged in full on approval**.

The website is unchanged (still the ₹2-trial hard gate).

## Backend — deploy BEFORE releasing the Android build

`POST /api/laundry/billing/subscribe` now honours `{ "trial": false }`: no ₹2
addon, no `start_at` delay, the plan bills on approval. Without the field
(the website) the one-per-shop ₹2 trial is unchanged.

The new Android build always sends `trial: false` and **refuses to open
Checkout if the response still has `trialAmount > 0`** (shows "Could not start
your plan"), so an un-patched server blocks payments rather than charging terms
the screen doesn't show.

Patch: `docs/deploy/2026-10-12-backend-subscribe-no-trial.patch` (one hunk in
`routes/laundry-billing.js`, made against the live server file on 2026-10-12).

Note: the server tree has uncommitted work in the same file (client
`platform` tracking + migration `20261012120000_laundry_client_platform`).
Rebuilding the backend ships that too — make sure it's meant to go live (and
run `migrate` first if it hasn't been).

```sh
scp docs/deploy/2026-10-12-backend-subscribe-no-trial.patch courses-aws:/tmp/
ssh courses-aws
cd ~/shweta-makeup-monorepo/backend
patch -p1 --dry-run < /tmp/2026-10-12-backend-subscribe-no-trial.patch
patch -p1 < /tmp/2026-10-12-backend-subscribe-no-trial.patch
cd .. && docker compose -f docker-compose.prod.yml up -d --build backend
```

## Check

- A logged-in user with no plan: `POST /billing/subscribe {"plan":"annual","trial":false}`
  → `trialAmount: 0`, `amount: 499900`.
- Same without `trial` → `trialAmount: 200` while the shop is still trial-eligible.
