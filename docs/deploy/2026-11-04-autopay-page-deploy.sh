#!/usr/bin/env bash
# Standalone AutoPay page (owner, 4 Nov 2026): mylaundry.work/autopay/<userId> opens Razorpay Checkout in the
# browser for an owner who can't finish UPI AutoPay in the app, with the hosted link's QR as a second route.
# Backend: routes/laundry-billing.js (subscribeFor extracted), routes/laundry-public.js (GET/POST /autopay/:userId).
# Web: src/app/autopay/[id]/page.tsx + src/ui/autopayPage.tsx. Rebuilds backend, then the laundry web service.
#   bash docs/deploy/2026-11-04-autopay-page-deploy.sh            # TEST_PHONE=7676363793 by default (prints its link)
set -euo pipefail
HERE=$(cd "$(dirname "$0")/../.." && pwd)
STAMP=$(date -u +%Y%m%d-%H%M%S)
# backend
scp -q "$HERE/courses/backend/routes/laundry-billing.js" "$HERE/courses/backend/routes/laundry-public.js" courses-aws:/tmp/
ssh -o BatchMode=yes courses-aws "cd ~/shweta-makeup-monorepo/backend && mkdir -p ~/backend-filebak/$STAMP-autopay && cp routes/laundry-billing.js routes/laundry-public.js ~/backend-filebak/$STAMP-autopay/ && cp /tmp/laundry-billing.js /tmp/laundry-public.js routes/ && rm -f /tmp/laundry-billing.js /tmp/laundry-public.js && cd .. && docker compose -f docker-compose.prod.yml up -d --build backend 2>&1 | tail -1 && sleep 12 && docker ps --format '{{.Names}} {{.Status}}' | grep backend-1"
# web (scp chokes on the [id] folder: pipe each file through ssh)
cd "$HERE/web"
ssh -o BatchMode=yes courses-aws "mkdir -p ~/apnalaundry-web-filebak/$STAMP 'apnalaundry-web/src/app/autopay/[id]'"
for f in "src/app/autopay/[id]/page.tsx" "src/ui/autopayPage.tsx"; do ssh -o BatchMode=yes courses-aws "cat > \"apnalaundry-web/$f\"" < "$f"; done
ssh -o BatchMode=yes courses-aws 'cd ~/shweta-makeup-monorepo && docker compose -f docker-compose.prod.yml up -d --build laundry 2>&1 | tail -2 && sleep 8 && curl -s -o /dev/null -w "mylaundry.work: %{http_code}\n" https://mylaundry.work/'
# verify with the owner's test phone: data endpoint via the site proxy, and the page
ssh -o BatchMode=yes courses-aws "TEST_PHONE='${TEST_PHONE:-7676363793}' bash -s" <<'REMOTE'
set -e
UID_=$(docker exec -e TP="$TEST_PHONE" shweta-makeup-monorepo-backend-1 node -e 'const p=require("/app/db");p.laundryUser.findFirst({where:{phone:{endsWith:process.env.TP}},orderBy:{createdAt:"asc"}}).then(u=>{console.log(u?u.id:"");return p.$disconnect()})' | tail -1)
echo "test user: $UID_"
curl -s "https://mylaundry.work/api/laundry/public/autopay/$UID_" | cut -c1-320; echo
curl -s -o /dev/null -w "page HTTP %{http_code}\n" "https://mylaundry.work/autopay/$UID_"
curl -s -o /dev/null -w "bad id HTTP %{http_code}\n" "https://mylaundry.work/api/laundry/public/autopay/not-a-uuid"
echo "LINK: https://mylaundry.work/autopay/$UID_"
REMOTE
