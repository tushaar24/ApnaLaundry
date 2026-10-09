# Deploy: email address on the bill (2026-10-09)

Adds an optional **Email address** to "+ Add fields" (onboarding "Your bill" and
Settings → Bill design & details). A valid email prints right under the phone on
all three designs; an invalid one is kept but not printed, like GSTIN.

Order: **backend first** (one additive column with a default — older apps never
send it and sync/push only writes keys a client sends), then the website.
Android ships with the next Play build (Room v7, `MIGRATION_6_7`).

## 1. Backend (courses-aws, `~/shweta-makeup-monorepo/backend`)

`2026-10-09-backend-shop-email.patch` (next to this file) was made against the
live tree as of the `20261009160000_laundry_order_pct_extras` migration. It adds:
- migration `20261009170000_laundry_shop_email` — `laundry_shops.email`
  (`TEXT NOT NULL DEFAULT ''`);
- `email` on the Prisma `LaundryShop` model;
- `services/laundry-shop-details.js`: a sanitiser for `email` (trim, no spaces,
  lower-case, ≤ 80) so sync/push stores it, and `email` in the public bill's shop.

```bash
scp docs/deploy/2026-10-09-backend-shop-email.patch courses-aws:/tmp/
ssh courses-aws
cd ~/shweta-makeup-monorepo/backend
patch -p1 --dry-run < /tmp/2026-10-09-backend-shop-email.patch   # must apply cleanly
patch -p1 < /tmp/2026-10-09-backend-shop-email.patch
cd .. && docker compose -f docker-compose.prod.yml run --rm --build migrate
docker compose -f docker-compose.prod.yml up -d --build backend
git add backend/prisma backend/services/laundry-shop-details.js
git commit -m "feat(laundry): email address on the shop bill"
```

Check: `GET /api/laundry/sync/pull` returns `shop.email` (`""` for every shop
until an owner adds one).

## 2. Website (from branch `prod`, after merging `feat/bill-email`)

Dry-run first, then sync `web/` (keep the server-only `.env`, `node_modules`):

```bash
git archive prod web | tar -x -C /tmp/web-prod
rsync -rcn -i --exclude node_modules --exclude .next /tmp/web-prod/web/ courses-aws:apnalaundry-web/
# check only intended files differ, then rerun without -n, and:
ssh courses-aws 'cd ~/shweta-makeup-monorepo && docker compose -f docker-compose.prod.yml up -d --build laundry'
```

Until the backend patch is live, the website still works: the field is kept
locally and pushed, and the server ignores the unknown key.
