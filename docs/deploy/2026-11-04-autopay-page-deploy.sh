#!/usr/bin/env bash
# Standalone pay page (owner, 4 Nov 2026): mylaundry.work/autopay/<userId> — a YEAR paid once (Razorpay Order +
# Checkout in the browser, no mandate), for an owner who can't finish UPI AutoPay in the app. Backend:
# routes/laundry-billing.js (subscribeFor), routes/laundry-public.js (GET /autopay/:userId, POST …/order, POST …/confirm),
# services/laundry-razorpay.js (orders). Web: src/app/autopay/[id]/page.tsx, src/ui/autopayPage.tsx, src/data/billing.ts.
# Rebuilds backend, then the laundry web service. With PHONE_799=<10 digits>: creates that account if it doesn't exist
# yet and allow-lists it for the ₹799 yearly offer (manual_allow row), then prints its link.
#   bash docs/deploy/2026-11-04-autopay-page-deploy.sh                         # TEST_PHONE=7676363793 (prints its link)
#   PHONE_799=7252939004 bash docs/deploy/2026-11-04-autopay-page-deploy.sh    # + enable ₹799 and print that owner's link
set -euo pipefail
HERE=$(cd "$(dirname "$0")/../.." && pwd)
STAMP=$(date -u +%Y%m%d-%H%M%S)
# backend
scp -q "$HERE/courses/backend/routes/laundry-billing.js" "$HERE/courses/backend/routes/laundry-public.js" "$HERE/courses/backend/services/laundry-razorpay.js" courses-aws:/tmp/
ssh -o BatchMode=yes courses-aws "cd ~/shweta-makeup-monorepo/backend && mkdir -p ~/backend-filebak/$STAMP-autopay && cp routes/laundry-billing.js routes/laundry-public.js services/laundry-razorpay.js ~/backend-filebak/$STAMP-autopay/ && cp /tmp/laundry-billing.js /tmp/laundry-public.js routes/ && cp /tmp/laundry-razorpay.js services/ && rm -f /tmp/laundry-billing.js /tmp/laundry-public.js /tmp/laundry-razorpay.js && cd .. && docker compose -f docker-compose.prod.yml up -d --build backend 2>&1 | tail -1 && sleep 12 && docker ps --format '{{.Names}} {{.Status}}' | grep backend-1"
# web (scp chokes on the [id] folder: pipe each file through ssh)
cd "$HERE/web"
ssh -o BatchMode=yes courses-aws "mkdir -p ~/apnalaundry-web-filebak/$STAMP 'apnalaundry-web/src/app/autopay/[id]'"
for f in "src/app/autopay/[id]/page.tsx" "src/ui/autopayPage.tsx" "src/data/billing.ts"; do ssh -o BatchMode=yes courses-aws "cat > \"apnalaundry-web/$f\"" < "$f"; done
ssh -o BatchMode=yes courses-aws 'cd ~/shweta-makeup-monorepo && docker compose -f docker-compose.prod.yml up -d --build laundry 2>&1 | tail -2 && sleep 8 && curl -s -o /dev/null -w "mylaundry.work: %{http_code}\n" https://mylaundry.work/'
# verify with the owner's test phone: data endpoint via the site proxy, and the page
ssh -o BatchMode=yes courses-aws "TEST_PHONE='${TEST_PHONE:-7676363793}' PHONE_799='${PHONE_799:-}' bash -s" <<'REMOTE'
set -e
if [ -n "$PHONE_799" ]; then
  docker exec -e PH="$PHONE_799" shweta-makeup-monorepo-backend-1 node -e '
const prisma = require("/app/db");
(async () => {
  const ph = String(process.env.PH).replace(/\D/g, "").slice(-10);
  let u = await prisma.laundryUser.findFirst({ where: { phone: { endsWith: ph } }, orderBy: { createdAt: "asc" } });
  if (!u) { u = await prisma.laundryUser.create({ data: { phone: ph, signupPlatform: "web" } }); console.log("account created for", ph, u.id); } else console.log("account exists", ph, u.id);
  await prisma.$executeRawUnsafe("insert into laundry_offer_sends (phone, user_id, tier, name, batch, status, template, wa_message_id, sent_at) values ($1, $2, 0, $3, $4, $5, $6, $7, now()) on conflict (phone, template) do nothing", ph, u.id, "", "manual", "manual", "manual_allow", "manual:" + ph);
  console.log("₹799 allow-list row in place for", ph);
  console.log("LINK_799: https://mylaundry.work/autopay/" + u.id);
  await prisma.$disconnect(); })().catch((e) => { console.error(e.message); process.exit(1); });'
  U799=$(docker exec -e PH="$PHONE_799" shweta-makeup-monorepo-backend-1 node -e 'const p=require("/app/db");p.laundryUser.findFirst({where:{phone:{endsWith:process.env.PH}},orderBy:{createdAt:"asc"}}).then(u=>{console.log(u?u.id:"");return p.$disconnect()})' | tail -1)
  sleep 2; curl -s "https://mylaundry.work/api/laundry/public/autopay/$U799" | cut -c1-260; echo
fi
UID_=$(docker exec -e TP="$TEST_PHONE" shweta-makeup-monorepo-backend-1 node -e 'const p=require("/app/db");p.laundryUser.findFirst({where:{phone:{endsWith:process.env.TP}},orderBy:{createdAt:"asc"}}).then(u=>{console.log(u?u.id:"");return p.$disconnect()})' | tail -1)
echo "test user: $UID_"
curl -s "https://mylaundry.work/api/laundry/public/autopay/$UID_" | cut -c1-320; echo
curl -s -o /dev/null -w "page HTTP %{http_code}\n" "https://mylaundry.work/autopay/$UID_"
curl -s -o /dev/null -w "bad id HTTP %{http_code}\n" "https://mylaundry.work/api/laundry/public/autopay/not-a-uuid"
echo "LINK: https://mylaundry.work/autopay/$UID_"
REMOTE
