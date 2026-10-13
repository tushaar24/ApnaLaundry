# Deploy: backend for Android 1.0.10 (2026-10-13)

Two backend changes, shipped together, both needed by Android 1.0.10:

1. **Guide A/B test** — `GET /api/laundry/experiments` (new
   `routes/laundry-experiments.js` + one mount line in `app.js`). Details:
   `2026-10-13-guide-ab.md`. Patch: `2026-10-13-backend-guide-ab.patch`.
2. **More free orders for some shops** — `/api/laundry/billing/status` now
   returns `freeOrders`: 5, or the number listed for the shop's 10-digit login
   phone in `LAUNDRY_FREE_ORDERS_BY_PHONE` (backend `.env`,
   `"8400042643:50,9876543210:20"`). The Android free-orders gate uses it from
   1.0.10 (older apps keep their built-in 5); the website ignores it. Patch:
   `2026-10-13-backend-free-orders.patch` (only `routes/laundry-billing.js`).
   First entry: **8400042643 → 50** (owner, 2026-10-13).

No migration, no schema change.

## Apply on courses-aws

From the app repo root. Backs up `app.js`, `laundry-billing.js` and `.env`,
dry-runs both patches (stops if the server's files differ), adds the free-orders
line to `.env` if missing, syntax-checks, commits on the server's `main`, and
rebuilds the backend in the background:

```sh
cat docs/deploy/2026-10-13-backend-guide-ab.patch docs/deploy/2026-10-13-backend-free-orders.patch | ssh courses-aws 'set -e
cd ~/shweta-makeup-monorepo/backend
B=~/backend-filebak/$(date -u +%Y%m%d-%H%M%S); mkdir -p $B; cp -a app.js routes/laundry-billing.js .env $B/; echo "backup: $B"
cat > /tmp/release-1010.patch
patch -p1 --dry-run < /tmp/release-1010.patch && patch -p1 < /tmp/release-1010.patch
grep -q "^LAUNDRY_FREE_ORDERS_BY_PHONE=" .env || printf "\nLAUNDRY_FREE_ORDERS_BY_PHONE=8400042643:50\n" >> .env
for f in app.js routes/laundry-experiments.js routes/laundry-billing.js; do docker run --rm -v $PWD:/x shweta-makeup-monorepo-backend node --check /x/$f; done
cd .. && git add backend/app.js backend/routes/laundry-experiments.js backend/routes/laundry-billing.js && git commit -q -m "laundry: guide A/B endpoint + per-phone free orders" && git log --oneline -1
nohup docker compose -f docker-compose.prod.yml up -d --build backend > /tmp/backend-build.log 2>&1 &
echo started'
```

Then wait for the backend container to be recreated
(`docker inspect -f {{.Created}} $(docker compose -f docker-compose.prod.yml ps -q backend)`).

To give another shop more free orders later: add `,<10-digit phone>:<orders>`
to `LAUNDRY_FREE_ORDERS_BY_PHONE` in `backend/.env` and rebuild/restart the
backend; the shop's app picks it up on its next start.

Roll back: restore the three files from the backup folder, remove
`routes/laundry-experiments.js`, rebuild the backend.
