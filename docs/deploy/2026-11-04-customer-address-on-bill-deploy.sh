#!/usr/bin/env bash
# Owner, 4 Nov 2026: "Add fields to your bill" gets a Customer address switch. When on, the customer's SAVED
# address prints under their name on every bill design (shared image, combined bill, thermal print, the
# /b/<token> web page); customers without an address print as before. Shop setting custAddressOnBill
# (Android Room v10, web, backend laundry_shops.cust_address_on_bill — additive, older apps never send it).
# Backend: prisma schema + migration, services/laundry-shop-details.js (sanitiser + billShop),
# services/laundry-bill-links.js (customer.address only when the shop prints it). Web: domain/models.ts,
# domain/billDetails.ts, domain/billReceipt.ts, data/rows.ts, data/store.ts, ui/billRender.ts, ui/onboarding/billFields.tsx.
#   DRY_RUN=1 bash docs/deploy/2026-11-04-customer-address-on-bill-deploy.sh   # copy nothing, show what would run
#   bash docs/deploy/2026-11-04-customer-address-on-bill-deploy.sh
set -euo pipefail
HERE=$(cd "$(dirname "$0")/../.." && pwd)
STAMP=$(date -u +%Y%m%d-%H%M%S)
MIG=20261104120000_laundry_shop_cust_address_on_bill
B="$HERE/courses/backend"
WEB_FILES=(src/domain/models.ts src/domain/billDetails.ts src/domain/billReceipt.ts src/data/rows.ts src/data/store.ts src/ui/billRender.ts src/ui/onboarding/billFields.tsx)
if [ "${DRY_RUN:-0}" = "1" ]; then
  echo "would copy backend: prisma/schema.prisma prisma/migrations/$MIG services/laundry-shop-details.js services/laundry-bill-links.js"
  echo "would run: migrate deploy, up -d --build backend, then web files ${WEB_FILES[*]} + up -d --build laundry"
  exit 0
fi
# backend files (+ a file backup on the server), migration, rebuild
scp -q "$B/prisma/schema.prisma" "$B/services/laundry-shop-details.js" "$B/services/laundry-bill-links.js" "$B/prisma/migrations/$MIG/migration.sql" courses-aws:/tmp/
ssh -o BatchMode=yes courses-aws "MIG=$MIG STAMP=$STAMP bash -s" <<'REMOTE'
set -euo pipefail
cd ~/shweta-makeup-monorepo/backend
mkdir -p ~/backend-filebak/$STAMP-cust-address "prisma/migrations/$MIG"
cp prisma/schema.prisma services/laundry-shop-details.js services/laundry-bill-links.js ~/backend-filebak/$STAMP-cust-address/
cp /tmp/schema.prisma prisma/ && cp /tmp/laundry-shop-details.js /tmp/laundry-bill-links.js services/ && cp /tmp/migration.sql "prisma/migrations/$MIG/"
rm -f /tmp/schema.prisma /tmp/laundry-shop-details.js /tmp/laundry-bill-links.js /tmp/migration.sql
cd ..
docker compose -f docker-compose.prod.yml run --rm --build migrate 2>&1 | tail -3
docker compose -f docker-compose.prod.yml up -d --build backend 2>&1 | tail -1
sleep 12; docker ps --format '{{.Names}} {{.Status}}' | grep backend-1
docker exec shweta-makeup-monorepo-backend-1 node -e 'const p=require("/app/db");p.$queryRawUnsafe("select count(*)::int as n from information_schema.columns where table_name=\x27laundry_shops\x27 and column_name=\x27cust_address_on_bill\x27").then(r=>{console.log("column present:",r[0].n===1);return p.$disconnect()})'
curl -s -o /dev/null -w "api health %{http_code}\n" https://shwetamakeover.online/api/health
REMOTE
# web (scp chokes on [..] folders elsewhere: pipe each file through ssh), rebuild the laundry service
cd "$HERE/web"
ssh -o BatchMode=yes courses-aws "mkdir -p ~/apnalaundry-web-filebak/$STAMP-cust-address && cd ~/apnalaundry-web && cp --parents ${WEB_FILES[*]} ~/apnalaundry-web-filebak/$STAMP-cust-address/"
for f in "${WEB_FILES[@]}"; do ssh -o BatchMode=yes courses-aws "cat > \"apnalaundry-web/$f\"" < "$f"; done
ssh -o BatchMode=yes courses-aws 'cd ~/shweta-makeup-monorepo && docker compose -f docker-compose.prod.yml up -d --build laundry 2>&1 | tail -2 && sleep 8 && curl -s -o /dev/null -w "mylaundry.work: %{http_code}\n" https://mylaundry.work/'
