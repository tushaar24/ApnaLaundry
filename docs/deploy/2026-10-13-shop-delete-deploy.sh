#!/usr/bin/env bash
# Shop delete (2026-10-13) — runs ON courses-aws:
#   scp docs/deploy/2026-10-13-backend-shop-delete.patch courses-aws:/tmp/shop-delete.patch
#   scp docs/deploy/2026-10-13-shop-delete-deploy.sh courses-aws:/tmp/shop-delete-deploy.sh
#   ssh courses-aws 'nohup bash /tmp/shop-delete-deploy.sh > /tmp/shop-delete-deploy.log 2>&1 &'
# Additive migration (laundry_shops.deleted_at, nullable). Backs up the changed
# files, dry-runs the patch, builds the image before migrating, swaps, commits.
set -euo pipefail
cd ~/shweta-makeup-monorepo
C="docker compose -f docker-compose.prod.yml"
B=~/backend-filebak/$(date -u +%Y%m%d-%H%M%S); mkdir -p "$B"

echo "== 1. files backup + patch (dry-run first)"
cd backend
cp -a prisma/schema.prisma routes/laundry.js routes/laundry-shops.js services/laundry-shops.js "$B/"
patch -p1 --dry-run < /tmp/shop-delete.patch
patch -p1 < /tmp/shop-delete.patch
cd ..
echo "files backup: $B"

echo "== 2. build, migrate, swap"
$C build backend
$C run --rm --build migrate
$C up -d backend
docker inspect -f '{{.Created}}' "$($C ps -q backend)"

echo "== 3. commit on the server's main (only this change's files)"
git add backend/prisma/schema.prisma backend/prisma/migrations/20261013150000_laundry_shop_deleted \
  backend/routes/laundry.js backend/routes/laundry-shops.js backend/services/laundry-shops.js
git commit -q -m "laundry: delete an extra shop (soft — hidden, sync refused, rows kept)"
git log --oneline -1
echo "== done"
