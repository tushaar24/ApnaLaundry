# Deploy: multi-shop (2026-10-13)

An owner can run several shops (paid). Each shop has its own customers, khata,
orders, bills, services and counters. Android only (1.0.11); the website is
unchanged.

## Backend (`2026-10-13-backend-multi-shop.patch`)

- **Migration** `20261013120000_laundry_multi_shop`: `shop_id TEXT NOT NULL
  DEFAULT ''` on `laundry_shops` (+ `created_at`), `laundry_services`,
  `laundry_customers`, `laundry_orders`, `laundry_ledger`, `laundry_day_close`,
  `laundry_bill_links`, `laundry_shop_logos`; keys become (user, shop, id).
  `''` = the owner's **main shop** — every existing row.
- **Sync** (`routes/laundry.js`): the shop comes from the `X-Laundry-Shop`
  header (`services/laundry-shops.js`). **No header = the main shop**, so
  older Android builds and the website read and write exactly the rows they
  did before. An unknown / someone else's shop id → 404 `SHOP_NOT_FOUND`
  (never a fallback to the main shop). Pull responses don't include `shopId`
  / `createdAt`, so they're byte-for-byte what old clients got.
- **Shops** (`routes/laundry-shops.js`, `/api/laundry/shops`): `GET` lists the
  owner's shops (`"main"` first); `POST {name}` adds one — **403
  PLAN_REQUIRED without a plan** (same rule as billing status).
- **Bill links / sample bill / logos** are per shop; old `/b/<token>` links
  keep working (their shop_id is `''`). Logos keep the last 3 per shop.
- **Support app**: still one row per owner, with the main shop's name.

## Backward compatibility — tested locally

`2026-10-13-multi-shop-test.sh` (Postgres 16 in Docker): old schema → data
pushed through the **live** backend code like an old app → this migration →
new code. 19/19 checks pass, among them: an old app's pull is byte-for-byte
the same after the migration and after a second shop exists; old bill-link
tokens, the public bill page and the sample bill are unchanged; an old app's
edits land in the main shop only; a second shop with the same order /
customer ids stays fully separate; unknown and other owners' shop ids → 404;
adding a shop without a plan → 403. `prisma migrate diff` against the
migrated DB shows no drift from this change.

## Apply on courses-aws

```sh
scp docs/deploy/2026-10-13-backend-multi-shop.patch courses-aws:/tmp/multi-shop.patch
scp docs/deploy/2026-10-13-multi-shop-deploy.sh courses-aws:/tmp/multi-shop-deploy.sh
ssh courses-aws 'nohup bash /tmp/multi-shop-deploy.sh > /tmp/multi-shop-deploy.log 2>&1 &'
ssh courses-aws 'tail -f /tmp/multi-shop-deploy.log'
```

It backs up the laundry tables (`~/db-backup/laundry-<ts>.dump`, pg_dump
custom format) and the changed files (`~/backend-filebak/<ts>/`), dry-runs the
patch, builds the new image, migrates, swaps the container and commits on the
server's `main`.

Roll back: restore the files from the backup folder and rebuild the backend;
the extra columns are harmless to the old code only while no second shop
exists — after that, restore the tables from the dump
(`pg_restore --clean --table=...`) before going back.

## Follow-up (same day): the 2nd shop is free

Owner: 2 shops free, a 3rd and more need a plan. `routes/laundry-shops.js`
counts the owner's extra shops (+ the main one) and asks for a plan only from
the 3rd — `2026-10-13-backend-free-2nd-shop.patch` (that file only, no
migration). Local test 20/20 (2nd shop free, 3rd refused without a plan,
created with one; every old-app check unchanged).

```sh
cat docs/deploy/2026-10-13-backend-free-2nd-shop.patch | ssh courses-aws 'set -e; cd ~/shweta-makeup-monorepo/backend; B=~/backend-filebak/$(date -u +%Y%m%d-%H%M%S); mkdir -p $B; cp -a routes/laundry-shops.js $B/; cat > /tmp/free2.patch; patch -p1 --dry-run < /tmp/free2.patch && patch -p1 < /tmp/free2.patch; docker run --rm -v $PWD:/x shweta-makeup-monorepo-backend node --check /x/routes/laundry-shops.js; cd .. && git add backend/routes/laundry-shops.js && git commit -q -m "laundry: 2nd shop free, 3rd+ needs a plan"; nohup docker compose -f docker-compose.prod.yml up -d --build backend > /tmp/backend-build.log 2>&1 & echo started'
```
