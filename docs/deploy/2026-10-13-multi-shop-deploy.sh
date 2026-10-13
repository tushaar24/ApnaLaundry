#!/usr/bin/env bash
# Multi-shop backend deploy (2026-10-13) — runs ON courses-aws:
#   scp docs/deploy/2026-10-13-backend-multi-shop.patch courses-aws:/tmp/multi-shop.patch
#   scp docs/deploy/2026-10-13-multi-shop-deploy.sh courses-aws:/tmp/multi-shop-deploy.sh
#   ssh courses-aws 'nohup bash /tmp/multi-shop-deploy.sh > /tmp/multi-shop-deploy.log 2>&1 &'
#   ssh courses-aws 'tail -f /tmp/multi-shop-deploy.log'
# Stops at the first failure. Backs up the laundry tables and the changed files
# first; builds the new image BEFORE migrating, so old code meets the new keys
# only for the seconds of the container swap (an old app's sync that fails then
# is retried later — nothing is lost).
set -euo pipefail
cd ~/shweta-makeup-monorepo
C="docker compose -f docker-compose.prod.yml"
TS=$(date -u +%Y%m%d-%H%M%S)

echo "== 1. database backup (laundry tables)"
mkdir -p ~/db-backup
URL=$($C exec -T backend sh -c 'printf %s "${DIRECT_URL:-$DATABASE_URL}"')
OUT=~/db-backup/laundry-$TS.dump
for v in 17 18; do
  if docker run --rm -e PGURL="$URL" postgres:$v-alpine sh -c 'pg_dump "$PGURL" --format=custom --table="laundry_*"' > "$OUT"; then break; fi
done
test -s "$OUT"
echo "db backup: $OUT ($(du -h "$OUT" | cut -f1))"

echo "== 2. files backup + patch (dry-run first)"
B=~/backend-filebak/$TS; mkdir -p "$B"
cd backend
cp -a app.js prisma/schema.prisma routes/laundry.js routes/laundry-support.js services/laundry-bill-links.js services/laundry-support-shops.js "$B/"
patch -p1 --dry-run < /tmp/multi-shop.patch
patch -p1 < /tmp/multi-shop.patch
cd ..
echo "files backup: $B"

echo "== 3. build the new image (the old container keeps serving)"
$C build backend

echo "== 4. migrate, then swap the container"
$C run --rm --build migrate
$C up -d backend
docker inspect -f '{{.Created}}' "$($C ps -q backend)"

echo "== 5. commit on the server's main (only this change's files)"
git add backend/app.js backend/prisma/schema.prisma backend/prisma/migrations/20261013120000_laundry_multi_shop \
  backend/routes/laundry.js backend/routes/laundry-shops.js backend/routes/laundry-support.js \
  backend/services/laundry-bill-links.js backend/services/laundry-shops.js backend/services/laundry-support-shops.js
git commit -q -m "laundry: multi-shop — shop_id on shop data, X-Laundry-Shop sync, /api/laundry/shops"
git log --oneline -1
echo "== done"
