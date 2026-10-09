# Deploy: onboarding + bill designs (2026-10-09)

Order: **backend first** (additive — new columns have defaults, new routes are
unused by older apps), then the website. Android ships with the next Play build.

## 1. Backend (courses-aws, `~/shweta-makeup-monorepo/backend`)

`2026-10-09-backend-bill-details.patch` (next to this file) adds:
- migration `20261009120000_laundry_shop_bill_details` — bill-detail columns on
  `laundry_shops` + a `laundry_shop_logos` table;
- sync/push writes the new shop fields **only when the client sends them**
  (old app builds can't wipe them);
- `POST /api/laundry/shop/logo` (authed upload) and
  `GET /api/laundry/public/logos/:id` (public, immutable);
- the public bill (`/b/<token>`) payload gains the bill details, `paid` and
  `deliveryTime`.

```bash
scp docs/deploy/2026-10-09-backend-bill-details.patch courses-aws:/tmp/
ssh courses-aws
cd ~/shweta-makeup-monorepo/backend
patch -p1 --dry-run < /tmp/2026-10-09-backend-bill-details.patch   # must apply cleanly
patch -p1 < /tmp/2026-10-09-backend-bill-details.patch
cd .. && docker compose -f docker-compose.prod.yml run --rm --build migrate
docker compose -f docker-compose.prod.yml up -d --build backend
git add backend/prisma backend/routes/laundry.js backend/routes/laundry-public.js \
        backend/services/laundry-bill-links.js backend/services/laundry-shop-details.js
git commit -m "feat(laundry): shop bill details, logos, onboarding step"
```

A pre-patch copy of the 4 modified files is at
`~/backend-filebak/20261009-055444/files.tar`.

## 2. Website (from branch `prod`)

Dry-run first, then sync `web/` (keep the server-only `.env`, `node_modules`):

```bash
git archive prod web | tar -x -C /tmp/web-prod
rsync -rcn -i --exclude node_modules --exclude .next /tmp/web-prod/web/ courses-aws:apnalaundry-web/
# check only intended files differ, then rerun without -n, and:
ssh courses-aws 'cd ~/shweta-makeup-monorepo && docker compose -f docker-compose.prod.yml up -d --build laundry'
```
