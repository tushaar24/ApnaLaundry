# Deploy: 3 vs 5 free orders A/B test (2026-10-14)

New Android shops get **3 or 5 free orders**, 50/50, before the paywall. The
test runs entirely on the backend: Android 1.0.10+ already reads `freeOrders`
from `GET /api/laundry/billing/status`, and its paywall already says
"3 of 3 free orders used". No app release.

- **Who's in it:** accounts created at or after `LAUNDRY_FREE_ORDERS_TEST_START`
  (and before `LAUNDRY_FREE_ORDERS_TEST_END`, if set) whose sign-up came from
  the Android app (`laundry_users.signup_platform = 'android'`).
- **Never in it:** shops created before the start, website sign-ups, sign-ups
  from apps that send no platform header, and phones listed in
  `LAUNDRY_FREE_ORDERS_BY_PHONE` (their number always wins).
- **Split:** bucket = sha256(`free_orders_v1:<userId>`) % 100 — stable per shop,
  independent of the guide test (`guide_v1`). Bucket < `LAUNDRY_FREE_ORDERS_TEST_PCT`
  (default 50) → 3 free orders, else 5.
- **CleverTap:** profile property **`Free_Orders_Variant`** = `free_3` | `free_5`,
  set by the server at sign-up (login), and again from `/billing/status` and
  `/experiments`, which the app calls at every start (re-sent at most once a
  day per shop, since a failed CleverTap upload isn't reported back).
- **Old apps:** 1.0.9 and older keep their built-in 5 whatever the group, so
  the analysis counts only shops that signed up on 1.0.10+.
- **Backward compatible:** with no start time set the code changes nothing; no
  response field is added or renamed; existing shops and the website are
  untouched. No migration, no schema change.

Code: `services/laundry-free-orders.js` (new; also now holds the per-phone
free orders that were in `routes/laundry-billing.js`), plus one call each in
`routes/laundry-billing.js` (`/status`), `routes/laundry-experiments.js` and
`routes/laundry-auth.js` (`sendLogin`). Patch: `2026-10-14-backend-free-orders-ab.patch`,
made against the live server files on 2026-10-14 and dry-run checked.

## Apply on courses-aws

From the app repo root. Backs up the touched files and `.env`, dry-runs the
patch (stops if the server's files differ), sets the start to **15 minutes
from now** (so nobody signs up on the old code and later switches from 5 to 3),
syntax-checks in the backend image, commits on the server's `main`, and
rebuilds the backend in the background:

```sh
cat docs/deploy/2026-10-14-backend-free-orders-ab.patch | ssh courses-aws 'set -e
cd ~/shweta-makeup-monorepo/backend
B=~/backend-filebak/$(date -u +%Y%m%d-%H%M%S); mkdir -p $B; cp -a routes/laundry-auth.js routes/laundry-billing.js routes/laundry-experiments.js .env $B/; echo "backup: $B"
cat > /tmp/free-orders-ab.patch
patch -p1 --dry-run < /tmp/free-orders-ab.patch && patch -p1 < /tmp/free-orders-ab.patch
START=$(date -u -d "+15 min" +%Y-%m-%dT%H:%M:%SZ)
grep -q "^LAUNDRY_FREE_ORDERS_TEST_START=" .env || printf "\nLAUNDRY_FREE_ORDERS_TEST_START=%s\nLAUNDRY_FREE_ORDERS_TEST_PCT=50\n" "$START" >> .env
grep "^LAUNDRY_FREE_ORDERS_TEST" .env
for f in services/laundry-free-orders.js routes/laundry-billing.js routes/laundry-experiments.js routes/laundry-auth.js; do docker run --rm -v $PWD:/x shweta-makeup-monorepo-backend node --check /x/$f; done
cd .. && git add backend/services/laundry-free-orders.js backend/routes/laundry-billing.js backend/routes/laundry-experiments.js && git commit -q -m "laundry: 3 vs 5 free orders A/B for new Android shops" && git log --oneline -1
nohup docker compose -f docker-compose.prod.yml up -d --build backend > /tmp/backend-build.log 2>&1 &
echo started'
```

`routes/laundry-auth.js` is patched but **not** committed on the server: it
already had uncommitted live changes before this patch, and committing it
would sweep those in. Note the printed start time — it's the test's day 0.

Then wait for the backend container to be recreated
(`docker inspect -f {{.Created}} $(docker compose -f docker-compose.prod.yml ps -q backend)`)
before the start time passes.

## Check it works (first day)

- After the first new Android sign-ups: their CleverTap profiles show
  `Free_Orders_Variant`, about half `free_3`.
- A `free_3` shop on 1.0.11 reaches the paywall at its 4th order
  (`Free Orders Over Shown {orders: 3}` / paywall "3 of 3 free orders used");
  a `free_5` shop at its 6th.
- Existing shops still get 5 (`/billing/status` → `freeOrders: 5`).

## Stop, pause, roll back

- **Stop adding shops** (keep everyone's group): add
  `LAUNDRY_FREE_ORDERS_TEST_END=<ISO time>` to `backend/.env`, restart the backend.
- **Give everyone in it 5 again:** `LAUNDRY_FREE_ORDERS_TEST_PCT=0`, restart.
  Raising a shop from 3 to 5 is safe; going the other way blocks shops that
  already have 4–5 orders.
- **Test off entirely:** remove `LAUNDRY_FREE_ORDERS_TEST_START`, restart. The
  code stays and does nothing.
- **Full roll back:** restore the three route files from the backup folder,
  remove `services/laundry-free-orders.js`, rebuild the backend.

## How it's read

Unit: a new Android shop created after the start, first seen on app 1.0.10+
(CleverTap `CT App Version`), test and per-phone numbers excluded. Group from
the user id with the same hash (or `Free_Orders_Variant`); every shop counts in
its group whether or not it ever reaches the paywall.

| | Measure |
|---|---|
| Main | paid within 14 days of sign-up (`laundry_subscriptions` authenticated/active, Razorpay-confirmed) and ₹ per shop |
| Supporting | reached the paywall, started checkout, checkout success |
| Guardrails | 3+ real orders within 7 days, real work on day 2 and day 7, orders kept per shop, shops past their limit by deleting |

- Days 1–3: split near 50/50, paywall at the right order count, no `free_3`
  shop stuck on 5.
- ~Day 7: early read (paywall reach, checkouts, day-7 use).
- ~750 shops per group (about 9 days at ~85 analysable per group a day): stop
  adding shops with `LAUNDRY_FREE_ORDERS_TEST_END`.
- Final read 14 days after the last shop joined.
- Decide: 3 wins if more shops pay and day-7 use is at most a few points
  lower; same payments → keep 5; stop early if `free_3`'s day-2 return is
  about half of `free_5`'s.

Known noise: Android checkout fails often (10 of 101 owners succeeded,
7–13 Oct) — equal in both groups, but slows the read. Deleting an order frees
a slot (live orders count), which helps `free_3` shops more; tracked as a
guardrail.
