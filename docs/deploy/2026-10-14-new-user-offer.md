# Deploy: new-user offer — guide, 1 free order, Monthly-only ₹199 (2026-10-14)

Owner's call (2026-10-14, ~01:30 IST): for **new users from 14 Oct**, turn the
guide on for everyone, give **1 free order**, and the moment they try a second
order show the paywall: **₹199/month, no Yearly**, using the dark paywall with
the intro video (a new video is coming). **Everyone else stays exactly as
yesterday.** The 3 vs 5 free-orders test (`2026-10-14-free-orders-ab.md`, live
00:47 IST) is removed.

| Shop | Guide | Free orders | Paywall |
|---|---|---|---|
| New Android shop (created from 14 Oct 00:00 IST), app **1.0.12+** | always | **1** | dark video paywall, **Monthly ₹199 only** |
| New Android shop, app 1.0.11 or older | always | 5 | as before (Yearly ₹4,999 / Monthly ₹499) |
| Existing shops (any app) | 50/50 as before | 5 (or per-phone) | as before |
| Website | — | — | as before (₹2 trial) |
| Already paying | — | — | keep their plan and price |

Why new shops on 1.0.11 don't get it: 1.0.11 always shows the Yearly card and
picks it, and with ₹199 Monthly its "save" line would read negative. So the 1
free order and ₹199 apply only where the app can show them (1.0.12+). A new
shop that signs up on 1.0.11 and later updates to 1.0.12 then moves to 1 free
order — its next new order shows the ₹199 paywall.

## How it works

- **Server** (`docs/deploy/2026-10-14-backend-new-user-offer.patch`):
  - `services/laundry-free-orders.js` — new shop = Android sign-up created at or
    after `LAUNDRY_NEW_USER_OFFER_START` (per-phone shops excluded). The offer
    (1 free order) needs app version ≥ 12 (`x-laundry-app-version` header) and
    the ₹199 plan id; tags CleverTap profile **`New_User_Offer` =
    `free1_monthly199`** (login, `/billing/status`, `/experiments`, re-sent once
    a day). The 3 vs 5 code is gone.
  - `services/laundry-pricing.js` (new) — offer shops: Monthly ₹199 on
    `LAUNDRY_RAZORPAY_PLAN_MONTHLY_199`, whatever plan the app asks for; video
    URL from `LAUNDRY_PAYWALL_VIDEO_URL`. Everyone else: old plans and prices.
  - `routes/laundry-billing.js` — `/status` adds `monthlyOnly` and
    `paywallVideoUrl` (additive; old apps ignore them) and the shop's Monthly
    price; `/subscribe` uses the shop's plan; an unpaid ₹499 checkout isn't
    reused for ₹199 (reuse also needs the same Razorpay plan id).
  - `routes/laundry-experiments.js` — new shops always get `guide`; others keep
    their 50/50 group.
  - `routes/laundry-auth.js` — tags new shops at sign-up.
- **Razorpay:** a new ₹199 monthly plan, created (or found) by
  `2026-10-14-create-monthly-199-plan.js`.
- **App 1.0.12 (versionCode 12):** sends `x-laundry-app-version`; when `/status`
  says `monthlyOnly`, the paywall is the dark video design (`VideoOffer.kt`,
  `PaywallVideo.kt` — Media3 is back) with one Monthly card at the server's
  price (₹499 struck out) and "PAY ₹199 & CONTINUE". Otherwise the light
  two-plan paywall, unchanged. The free-orders gate already uses the server's
  number ("1 of 1 free orders used").

Backward compatible: no response field renamed or removed; requests without
the version header behave as before; older apps and the website see no change;
no migration.

## Apply on courses-aws

Deploy the backend first (safe at once: no app sends version 12 yet, so today
it only turns the guide on for new shops and ends the 3 vs 5 test). From the
app repo root:

```sh
ssh courses-aws 'cat > /tmp/create-monthly-199-plan.js' < docs/deploy/2026-10-14-create-monthly-199-plan.js
cat docs/deploy/2026-10-14-backend-new-user-offer.patch | ssh courses-aws 'set -e
cd ~/shweta-makeup-monorepo/backend
cat > /tmp/new-user-offer.patch
C=$(cd .. && docker compose -f docker-compose.prod.yml ps -q backend)
docker cp /tmp/create-monthly-199-plan.js $C:/app/create-monthly-199-plan.js
OUT=$(docker exec -w /app $C node create-monthly-199-plan.js); docker exec -u root $C rm -f /app/create-monthly-199-plan.js; rm -f /tmp/create-monthly-199-plan.js; echo "$OUT"
PLAN=$(echo "$OUT" | grep -o "plan_[A-Za-z0-9]*" | head -1); test -n "$PLAN"
B=~/backend-filebak/$(date -u +%Y%m%d-%H%M%S); mkdir -p $B; cp -a routes/laundry-auth.js routes/laundry-billing.js routes/laundry-experiments.js services/laundry-free-orders.js .env $B/; echo "backup: $B"
patch -p1 --dry-run < /tmp/new-user-offer.patch && patch -p1 < /tmp/new-user-offer.patch
sed -i "/^LAUNDRY_FREE_ORDERS_TEST_/d" .env
grep -q "^LAUNDRY_NEW_USER_OFFER_START=" .env || printf "\nLAUNDRY_NEW_USER_OFFER_START=2026-10-13T18:30:00Z\n" >> .env
grep -q "^LAUNDRY_RAZORPAY_PLAN_MONTHLY_199=" .env || printf "LAUNDRY_RAZORPAY_PLAN_MONTHLY_199=%s\n" "$PLAN" >> .env
grep -q "^LAUNDRY_PAYWALL_VIDEO_URL=" .env || printf "LAUNDRY_PAYWALL_VIDEO_URL=\n" >> .env
grep "^LAUNDRY_NEW_USER_OFFER_START\|^LAUNDRY_RAZORPAY_PLAN_MONTHLY_199\|^LAUNDRY_PAYWALL_VIDEO_URL" .env
for f in services/laundry-free-orders.js services/laundry-pricing.js routes/laundry-billing.js routes/laundry-experiments.js routes/laundry-auth.js; do docker run --rm -v $PWD:/x shweta-makeup-monorepo-backend node --check /x/$f; done
cd .. && git add backend/services/laundry-free-orders.js backend/services/laundry-pricing.js backend/routes/laundry-billing.js backend/routes/laundry-experiments.js && git commit -q -m "laundry: new-user offer (guide, 1 free order, Monthly-only 199 on 1.0.12+); end 3 vs 5 test" && git log --oneline -1
nohup docker compose -f docker-compose.prod.yml up -d --build backend > /tmp/backend-build.log 2>&1 &
echo started'
```

`2026-10-13T18:30:00Z` = 14 Oct 00:00 IST. `routes/laundry-auth.js` is patched
but not committed on the server (it has older uncommitted live edits).

Then build and release **Android 1.0.12** from `prod` (Play Console). New
shops get the offer once they're on 1.0.12.

## The video

`LAUNDRY_PAYWALL_VIDEO_URL` starts empty → the dark paywall shows without a
video. When the new video is ready, host it as a 720p faststart MP4 (e.g. on
mylaundry.work), set `LAUNDRY_PAYWALL_VIDEO_URL=https://…/file.mp4` in
`backend/.env` and restart the backend — no app release. It autoplays with
sound, loops, tap to pause, corner button mutes; `Paywall Video Started /
Unmuted / Completed` events are back.

## Check it works

- A new shop on 1.0.12: Settings/billing status says 1 free order; after one
  order, New order opens the dark paywall with one Monthly card at ₹199;
  Razorpay asks for ₹199; the `laundry_subscriptions` row has `amount = 19900`.
  CleverTap profile: `New_User_Offer = free1_monthly199`, `Guide_Variant = guide`.
- An existing shop on 1.0.12 or 1.0.11: 5 free orders, light paywall with
  Yearly ₹4,999 + Monthly ₹499.

## Roll back

- Offer off (guide back to 50/50 for new shops, 5 free orders, old paywall):
  remove `LAUNDRY_NEW_USER_OFFER_START` from `backend/.env`, restart.
- Full: restore the four route/service files from the backup folder, remove
  `services/laundry-pricing.js`, rebuild. ₹199 subscriptions already created
  keep running on Razorpay.
