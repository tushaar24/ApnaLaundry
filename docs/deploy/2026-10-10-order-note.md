# Deploy: a note on every order (2026-10-10)

The owner can type a free-text note while creating or editing an order
("Starch the shirts", "stain on the collar"). It shows on the order page (a
yellow box) and as one line on the Home order card, on web and Android, and
syncs between devices.

- **Backend**: `laundry_orders.note` (`TEXT NOT NULL DEFAULT ''`), migration
  `20261010120000_laundry_order_note`; `routes/laundry.js` sync push stores
  `note` (max 500 chars) only when a client sends it — older clients that
  don't know the field never wipe a stored note. Pull already returns every
  column, so no change there.
- **Web**: `Order.note`, the order DTO, New order "Note" box, order page,
  order card.
- **Android**: Room v8 → v9 (`MIGRATION_8_9` adds `orders.note`), DTO,
  New order "Note" box, order page, order card.

Order: **backend** first (until then a note is kept on the device that wrote it
but the server ignores the field), then **website**, then the next Android build.

## 1. Backend (courses-aws, `~/shweta-makeup-monorepo/backend`)

`2026-10-10-backend-order-note.patch` was diffed against the live files (after
`2026-10-09-backend-gst.patch` and the one-trial change).

```bash
scp docs/deploy/2026-10-10-backend-order-note.patch courses-aws:/tmp/
ssh courses-aws
cd ~/shweta-makeup-monorepo/backend
patch -p1 --dry-run < /tmp/2026-10-10-backend-order-note.patch   # must apply cleanly
patch -p1 < /tmp/2026-10-10-backend-order-note.patch
cd .. && docker compose -f docker-compose.prod.yml run --rm --build migrate
docker compose -f docker-compose.prod.yml up -d --build backend
git add backend/prisma backend/routes/laundry.js
git commit -m "feat(laundry): order note (laundry_orders.note)"
```

## 2. Website

The usual dry-run + per-file upload of the changed `web/src` files, then
`docker compose -f docker-compose.prod.yml up -d --build laundry`.
