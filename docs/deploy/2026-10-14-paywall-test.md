# Deploy: paywall test — hard 1-day trial vs 1 free order (2026-10-14)

Owner's call (2026-10-14): a 50/50 test for **brand-new accounts only**. Anyone
who has ever logged in before — on any phone, app version or the website —
never sees it and keeps exactly what they have today.

| | hard_trial (50%) | free1 (50%) |
|---|---|---|
| When the paywall shows | right after login, before setup, **no close button** | after the first order is saved **and** the guide is finished or skipped (skipped before the order → waits for the order); once, closable; then New order is blocked (1 free order) |
| Free use | 1-day trial: ₹2 today (refunded), ₹199 charged after 1 day | 1 free order |
| Price | ₹199/month only | ₹199/month only, charged on approval |
| Paywall | dark video paywall | dark video paywall |
| Guide | always on | always on |

## Who's in it

An account is in the test only if **all** hold (server, `services/laundry-paywall-test.js`):
- created at or after `LAUNDRY_PAYWALL_TEST_START`;
- created from the Android app **1.0.12+** — the app sends `x-laundry-app-version`
  and the server stores it once, at account creation, in
  `laundry_users.signup_app_version` (new nullable column);
- not a per-phone free-orders shop (`LAUNDRY_FREE_ORDERS_BY_PHONE`);
- the ₹199 plan id (`LAUNDRY_RAZORPAY_PLAN_MONTHLY_199`) is set.

So existing accounts (any app), accounts created on 1.0.11 or older (even after
they update) and website accounts are never in it. Brand-new accounts on older
builds stay in the 3 vs 5 free-orders test (owner's choice). Group =
sha256(`paywall_v1:<userId>`) % 100 < `LAUNDRY_PAYWALL_TEST_PCT` (default 50)
→ hard_trial, else free1; it never changes for an account.

## What changes

**Backend** (`2026-10-14-backend-paywall-test.patch`, made against the live
files after the 3 vs 5 rollback, dry-run checked):
- `prisma/schema.prisma` + migration `20261014120000_laundry_signup_app_version` —
  `signup_app_version INTEGER` (nullable; old rows null).
- `services/laundry-paywall-test.js` (new) — eligibility, group, offer, CleverTap
  `Paywall_Variant` (hard_trial | free1).
- `routes/laundry-auth.js` — stores the app version when an account is created;
  tags test accounts in CleverTap.
- `routes/laundry-billing.js` — `/status` adds `paywallGroup`, `monthlyOnly`,
  `trialOffered`, `trialDays`, `paywallVideoUrl` (all additive; null/false/7 for
  everyone else) and serves test accounts ₹199 Monthly + their free orders (0 /
  1). `/subscribe` puts test accounts on the ₹199 plan; hard_trial gets the ₹2
  trial with the first charge 1 day later (`trialDays` in the subscription
  notes, so a cancelled 1-day trial's grace is 1 day, not 7). An unpaid ₹499
  checkout is never reused for ₹199.
- `routes/laundry-experiments.js` — test accounts always get the guide.
- `services/laundry-free-orders.js` — test accounts are not in 3 vs 5.
- `services/laundry-razorpay.js` — `createSubscription` takes `trialDays`
  (default `TRIAL_DAYS` = 7, so the website is unchanged).

**Android 1.0.12** (versionCode 12): sends `x-laundry-app-version`;
`HardTrialGate` (hard_trial paywall over the app right after login — a new
login waits for the first billing check, so setup never flashes first) and
`FirstOrderPaywall` (free1) in `ui/screens/paywall/PaywallTestGates.kt`; the
dark video paywall (`VideoOffer.kt`, `PaywallVideo.kt`, Media3) with trial
wording for hard_trial; Paywall Shown `from` = `hard_trial` / `first_order`.
Outside the test the app behaves exactly as 1.0.11.

## Apply on courses-aws

Order matters: patch → **migration** → rebuild (the new login code writes the
new column). From the app repo root:

```sh
cat docs/deploy/2026-10-14-backend-paywall-test.patch | ssh courses-aws 'set -e
cd ~/shweta-makeup-monorepo/backend
B=~/backend-filebak/$(date -u +%Y%m%d-%H%M%S)-paywall-test; mkdir -p $B
cp -a prisma/schema.prisma routes/laundry-auth.js routes/laundry-billing.js routes/laundry-experiments.js services/laundry-free-orders.js services/laundry-razorpay.js .env $B/; echo "backup: $B"
cat > /tmp/paywall-test.patch
patch -p1 --dry-run < /tmp/paywall-test.patch && patch -p1 < /tmp/paywall-test.patch
for f in services/laundry-paywall-test.js services/laundry-free-orders.js services/laundry-razorpay.js routes/laundry-billing.js routes/laundry-experiments.js routes/laundry-auth.js; do docker run --rm -v $PWD:/x shweta-makeup-monorepo-backend node --check /x/$f; done
cd .. && docker compose -f docker-compose.prod.yml run --rm --build migrate
cd backend
START=$(date -u -d "+15 min" +%Y-%m-%dT%H:%M:%SZ)
grep -q "^LAUNDRY_PAYWALL_TEST_START=" .env || printf "\nLAUNDRY_PAYWALL_TEST_START=%s\nLAUNDRY_PAYWALL_TEST_PCT=50\n" "$START" >> .env
grep -q "^LAUNDRY_RAZORPAY_PLAN_MONTHLY_199=" .env || printf "LAUNDRY_RAZORPAY_PLAN_MONTHLY_199=plan_TnWwZMQsOCML47\n" >> .env
grep -q "^LAUNDRY_PAYWALL_VIDEO_URL=" .env || printf "LAUNDRY_PAYWALL_VIDEO_URL=\n" >> .env
grep "^LAUNDRY_PAYWALL\|^LAUNDRY_RAZORPAY_PLAN_MONTHLY_199" .env
cd .. && git add backend/prisma backend/services/laundry-paywall-test.js backend/services/laundry-free-orders.js backend/routes/laundry-billing.js backend/routes/laundry-experiments.js && git commit -q -m "laundry: paywall test (hard 1-day trial vs 1 free order) for brand-new 1.0.12+ accounts" && git log --oneline -1
nohup docker compose -f docker-compose.prod.yml up -d --build backend > /tmp/backend-build.log 2>&1 &
echo started'
```

`routes/laundry-auth.js` and `services/laundry-razorpay.js` are patched but not
committed on the server (both already had uncommitted live edits). The ₹199
plan `plan_TnWwZMQsOCML47` was created on 2026-10-14 and still exists.

Safe to deploy before the app: until 1.0.12 is out no account is created with
version ≥ 12, so nobody is in the test.

Then release **Android 1.0.12** from `prod`. To add the video: host a 720p
faststart MP4, set `LAUNDRY_PAYWALL_VIDEO_URL=https://…/file.mp4` in
`backend/.env`, restart the backend (no app release).

## Check it works (after 1.0.12 is out)

- A new number on 1.0.12: either the dark paywall right after login (no ✕,
  "Start your 1-day FREE trial", ₹2 then ₹199) — or setup, the guide, one order,
  then the paywall once on Home; a second New order shows the paywall.
- CleverTap profile: `Paywall_Variant`, `Guide_Variant = guide`.
- `laundry_users.signup_app_version = 12` for that account; a hard_trial
  subscription row has `amount 19900`, `trial_amount 200`, notes `trialDays 1`.
- An existing account on 1.0.12: no change (5 free orders, light paywall).

## Turn off / roll back

- Stop adding accounts (test accounts so far keep their group): set
  `LAUNDRY_PAYWALL_TEST_END=<ISO time>` in `backend/.env`, restart.
- Everyone out (test accounts back to 5 free orders and the old paywall):
  remove `LAUNDRY_PAYWALL_TEST_START`, restart. hard_trial accounts that
  already started a trial keep their subscription.
- Full: restore the files from the backup folder, remove
  `services/laundry-paywall-test.js`, rebuild. The new column can stay.

## Analysis

Unit: an account in the test (CleverTap `Paywall_Variant`, or the server's
hash). Main measure: paid within 14 days (Razorpay-confirmed; hard_trial =
trial converted to the first ₹199 charge, free1 = first ₹199 charge). Also:
setup done, first order, 3+ orders in 7 days, day-2 / day-7 use, paywall
reached (`Paywall Shown {from: hard_trial | first_order | free_orders}`),
checkout started, trial cancelled before the charge.
