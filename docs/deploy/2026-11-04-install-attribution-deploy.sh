#!/usr/bin/env bash
# Owner, 4 Nov 2026: which Meta ad (creative) an Android install came from. The app (1.0.18+) sends Play's
# install referrer once per account to POST /api/laundry/attribution/install; the backend decrypts Meta's
# ad / ad set / campaign ids (LAUNDRY_META_INSTALL_REFERRER_KEY — the app's Install Referrer Decryption Key
# from the Meta app dashboard; without it the raw referrer is still saved as meta_decrypt=no_key), looks the
# ad + creative up with META_MARKETING_TOKEN, saves it all in laundry_install_attributions and mirrors it on
# the CleverTap profile. Web: /open/… ad links pack Meta's dynamic URL parameters into the Play referrer.
# Backend: prisma schema + migration, config/env.js, app.js, routes/laundry-attribution.js,
# services/laundry-install-attribution.js, scripts/laundry-attribution-redecrypt.js (re-decrypt rows saved
# before the key was set: `docker exec shweta-makeup-monorepo-backend-1 node scripts/laundry-attribution-redecrypt.js --apply`).
# Web: src/core/openApp.ts, src/app/open/[[...screen]]/route.ts.
#   DRY_RUN=1 bash docs/deploy/2026-11-04-install-attribution-deploy.sh   # copy nothing, show what would run
#   bash docs/deploy/2026-11-04-install-attribution-deploy.sh
set -euo pipefail
HERE=$(cd "$(dirname "$0")/../.." && pwd)
STAMP=$(date -u +%Y%m%d-%H%M%S)
MIG=20261104200000_laundry_install_attributions
B="${COURSES_BACKEND:-$HERE/courses/backend}" # from a job worktree: COURSES_BACKEND=~/Developer/ApnaLaundry/courses/backend
WEB_FILES=(src/core/openApp.ts "src/app/open/[[...screen]]/route.ts")
if [ "${DRY_RUN:-0}" = "1" ]; then
  echo "would copy backend: prisma/schema.prisma prisma/migrations/$MIG config/env.js app.js routes/laundry-attribution.js services/laundry-install-attribution.js scripts/laundry-attribution-redecrypt.js"
  echo "would run: migrate deploy, up -d --build backend, then web files ${WEB_FILES[*]} + up -d --build laundry"
  exit 0
fi
# backend files (+ a file backup on the server), migration, rebuild
scp -q "$B/prisma/schema.prisma" "$B/config/env.js" "$B/app.js" "$B/routes/laundry-attribution.js" "$B/services/laundry-install-attribution.js" "$B/scripts/laundry-attribution-redecrypt.js" "$B/prisma/migrations/$MIG/migration.sql" courses-aws:/tmp/
ssh -o BatchMode=yes courses-aws "MIG=$MIG STAMP=$STAMP bash -s" <<'REMOTE'
set -euo pipefail
cd ~/shweta-makeup-monorepo/backend
mkdir -p ~/backend-filebak/$STAMP-install-attribution "prisma/migrations/$MIG"
cp prisma/schema.prisma config/env.js app.js ~/backend-filebak/$STAMP-install-attribution/
cp /tmp/schema.prisma prisma/ && cp /tmp/env.js config/ && cp /tmp/app.js . && cp /tmp/laundry-attribution.js routes/ && cp /tmp/laundry-install-attribution.js services/ && cp /tmp/laundry-attribution-redecrypt.js scripts/ && cp /tmp/migration.sql "prisma/migrations/$MIG/"
rm -f /tmp/schema.prisma /tmp/env.js /tmp/app.js /tmp/laundry-attribution.js /tmp/laundry-install-attribution.js /tmp/laundry-attribution-redecrypt.js /tmp/migration.sql
cd ..
docker compose -f docker-compose.prod.yml run --rm --build migrate 2>&1 | tail -3
docker compose -f docker-compose.prod.yml up -d --build backend 2>&1 | tail -1
sleep 12; docker ps --format '{{.Names}} {{.Status}}' | grep backend-1
# the earlier deploy today saw `up -d --build backend` keep the old container: check the new route is inside, else rebuild once more
if ! docker exec shweta-makeup-monorepo-backend-1 test -f /app/routes/laundry-attribution.js; then
  echo "backend container not recreated — rebuilding again"; docker compose -f docker-compose.prod.yml up -d --build backend 2>&1 | tail -1; sleep 12
fi
docker exec shweta-makeup-monorepo-backend-1 test -f /app/routes/laundry-attribution.js && echo "route file in container: yes"
docker exec shweta-makeup-monorepo-backend-1 node -e 'const p=require("/app/db");p.$queryRawUnsafe("select count(*)::int as n from information_schema.tables where table_name=\x27laundry_install_attributions\x27").then(r=>{console.log("table present:",r[0].n===1);return p.$disconnect()})'
curl -s -o /dev/null -w "api health %{http_code}\n" https://shwetamakeover.online/api/health
curl -s -o /dev/null -w "attribution route (401 = mounted, needs login): %{http_code}\n" -X POST https://shwetamakeover.online/api/laundry/attribution/install
REMOTE
# web (scp chokes on [..] folders: pipe each file through ssh), rebuild the laundry service detached and poll
cd "$HERE/web"
ssh -o BatchMode=yes courses-aws "BAK=\$HOME/apnalaundry-web-filebak/$STAMP-install-attribution; mkdir -p \"\$BAK/src/core\" \"\$BAK/src/app/open/[[...screen]]\" && cd ~/apnalaundry-web && cp src/core/openApp.ts \"\$BAK/src/core/\" && cp 'src/app/open/[[...screen]]/route.ts' \"\$BAK/src/app/open/[[...screen]]/\""
for f in "${WEB_FILES[@]}"; do ssh -o BatchMode=yes courses-aws "cat > \"apnalaundry-web/$f\"" < "$f"; done
ssh -o BatchMode=yes courses-aws 'cd ~/shweta-makeup-monorepo && nohup docker compose -f docker-compose.prod.yml up -d --build laundry > /tmp/laundry-build.log 2>&1 &'
for i in $(seq 1 40); do
  sleep 15
  if ssh -o BatchMode=yes courses-aws 'grep -qE "Started|Running|Error|error" /tmp/laundry-build.log'; then break; fi
done
ssh -o BatchMode=yes courses-aws 'tail -3 /tmp/laundry-build.log; sleep 5; curl -s -A "Mozilla/5.0 (Linux; Android 13)" "https://mylaundry.work/open/hi?ad_id=1&ad_name=Test%20ad" | grep -o "referrer=[^\"]*" | head -1'
