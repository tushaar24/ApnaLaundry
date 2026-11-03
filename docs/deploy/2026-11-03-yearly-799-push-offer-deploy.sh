#!/usr/bin/env bash
# Owner, 3 Nov 2026: ₹799/year offer for owners a CleverTap journey pushes (₹99 pop-up viewers, 30 min
# after Paywall Shown). The journey calls POST /api/laundry/offers/grant (webhook); granted owners see
# Yearly ₹799 (pre-selected) + their monthly price on Plans / pop-up / wall for GRANT_HOURS. Replaces the
# finished Diwali allow-list offer. Creates the ₹799 yearly Razorpay plan if missing, writes the webhook
# key + LAUNDRY_PAYWALL_OFFER, rebuilds the backend (new route + service), verifies with the owner's test phone.
#   DRY_RUN=1 bash docs/deploy/2026-11-03-yearly-799-push-offer-deploy.sh
#   bash docs/deploy/2026-11-03-yearly-799-push-offer-deploy.sh
set -euo pipefail
HERE=$(cd "$(dirname "$0")/../.." && pwd)
scp -q "$HERE/courses/backend/services/laundry-paywall-offer.js" "$HERE/courses/backend/routes/laundry-offers.js" "$HERE/courses/backend/app.js" courses-aws:/tmp/
ssh -o BatchMode=yes courses-aws "DRY_RUN=${DRY_RUN:-0} GRANT_HOURS='${GRANT_HOURS:-72}' UNTIL='${UNTIL:-2026-12-31T18:29:59Z}' TEST_PHONE='${TEST_PHONE:-7676363793}' bash -s" <<'REMOTE'
set -euo pipefail
cd ~/shweta-makeup-monorepo/backend
C=shweta-makeup-monorepo-backend-1
PLAN_ID=$(docker exec -e DRY="$DRY_RUN" "$C" node -e '
const auth = "Basic " + Buffer.from(process.env.LAUNDRY_RAZORPAY_KEY_ID + ":" + process.env.LAUNDRY_RAZORPAY_KEY_SECRET).toString("base64");
(async () => {
  const list = await (await fetch("https://api.razorpay.com/v1/plans?count=100", { headers: { Authorization: auth } })).json();
  const have = (list.items || []).find((p) => p.period === "yearly" && p.interval === 1 && p.item.amount === 79900);
  if (have) { console.log(have.id); return; }
  if (process.env.DRY === "1") { console.log("plan_WOULD_CREATE"); return; }
  const r = await fetch("https://api.razorpay.com/v1/plans", { method: "POST", headers: { Authorization: auth, "Content-Type": "application/json" },
    body: JSON.stringify({ period: "yearly", interval: 1, item: { name: "Yearly ₹799", amount: 79900, currency: "INR", description: "MyLaundry yearly plan, ₹799 push offer" } }) });
  const j = await r.json(); if (!r.ok) { console.error(JSON.stringify(j)); process.exit(1); } console.log(j.id);
})().catch((e) => { console.error(e.message); process.exit(1); });')
KEY=$(grep -o "^LAUNDRY_OFFER_WEBHOOK_KEY=.*" .env | cut -d= -f2- || true)
[ -n "$KEY" ] || KEY=$(openssl rand -hex 24)
FROM=$(date -u +%Y-%m-%dT%H:%M:%SZ)
OFFER=$(PLAN_ID="$PLAN_ID" GRANT_HOURS="$GRANT_HOURS" UNTIL="$UNTIL" FROM="$FROM" python3 -c 'import json,os;e=os.environ;print(json.dumps({"name":"yearly799","audience":"tagged","tags":["push:yearly799"],"grantHours":int(e["GRANT_HOURS"]),"newAccounts":True,"from":e["FROM"],"until":e["UNTIL"],"plans":["annual","monthly"],"default":"annual","prices":{"annual":799},"planIds":{"annual":e["PLAN_ID"]}},separators=(",",":")))')
echo "plan: $PLAN_ID | offer: $OFFER"
if [ "$DRY_RUN" = 1 ]; then echo "DRY RUN — nothing changed"; exit 0; fi
B=~/backend-filebak/$(date -u +%Y%m%d-%H%M%S)-yearly799; mkdir -p "$B"; cp .env "$B/.env"; cp services/laundry-paywall-offer.js app.js "$B/"
cp /tmp/laundry-paywall-offer.js services/laundry-paywall-offer.js; cp /tmp/laundry-offers.js routes/laundry-offers.js; cp /tmp/app.js app.js; rm -f /tmp/laundry-paywall-offer.js /tmp/laundry-offers.js /tmp/app.js
OFFER="$OFFER" KEY="$KEY" python3 - <<'PY'
import os
lines = open(".env").read().split("\n")
out = [l for l in lines if not l.startswith("LAUNDRY_PAYWALL_OFFER=") and not l.startswith("LAUNDRY_OFFER_WEBHOOK_KEY=")]
while out and out[-1] == "": out.pop()
out += ["LAUNDRY_PAYWALL_OFFER=" + os.environ["OFFER"], "LAUNDRY_OFFER_WEBHOOK_KEY=" + os.environ["KEY"], ""]
open(".env", "w").write("\n".join(out))
PY
cd ~/shweta-makeup-monorepo
docker compose -f docker-compose.prod.yml up -d --build backend 2>&1 | tail -1
sleep 12; docker ps --format '{{.Names}} {{.Status}}' | grep backend-1
echo "--- webhook: bad key → 401, then grant the test phone:"
curl -s -o /dev/null -w "bad key -> HTTP %{http_code}\n" -X POST https://shwetamakeover.online/api/laundry/offers/grant -H "Content-Type: application/json" -d '{"phone":"'"$TEST_PHONE"'","offer":"yearly799"}'
curl -s -X POST https://shwetamakeover.online/api/laundry/offers/grant -H "Content-Type: application/json" -H "X-Laundry-Offer-Key: $KEY" -d '{"phone":"'"$TEST_PHONE"'","offer":"yearly799"}'; echo
echo "--- what Plans will show:"
docker exec -e TEST_PHONE="$TEST_PHONE" "$C" node -e '
const prisma = require("/app/db"); const t = require("/app/services/laundry-paywall-test"), o = require("/app/services/laundry-paywall-offer");
(async () => {
  const aud = await o.audience(prisma);
  const show = async (label, where) => {
    const u = await prisma.laundryUser.findFirst({ where, orderBy: { createdAt: "desc" } }); if (!u) return console.log(label, "no user");
    const sub = await prisma.laundrySubscription.findFirst({ where: { userId: u.id }, orderBy: { createdAt: "desc" } });
    const pt = o.apply(t.offer(u, Number(u.signupAppVersion) || 17), sub, Number(u.signupAppVersion) || 17, 5, u, aud);
    console.log(label.padEnd(34), "→", pt && pt.offer ? `OFFER ${pt.offer}: plans ${pt.plans} annual ₹${pt.prices.annual/100} monthly ₹${pt.prices.monthly/100} default ${pt.defaultPlan}` : `normal (${pt ? pt.group + " monthly ₹" + pt.prices.monthly/100 : "none"})`);
  };
  await show("granted test phone", { phone: { endsWith: process.env.TEST_PHONE } });
  const since = new Date("2026-11-01T16:30:20Z");
  await show("un-granted new ₹99 account", { signupPlatform: "android", createdAt: { gt: since }, NOT: { phone: { endsWith: process.env.TEST_PHONE } } });
  await show("Diwali allow-listed (paid)", { phone: { endsWith: "7602017890" } });
  console.log("granted so far:", aud && aud.grantedAt ? aud.grantedAt.size : 0);
  await prisma.$disconnect(); })();'
docker compose -f docker-compose.prod.yml up -d --force-recreate --no-deps laundry-dash 2>&1 | tail -1
echo "backup: $B"
echo "WEBHOOK URL: https://shwetamakeover.online/api/laundry/offers/grant"
echo "WEBHOOK KEY: $KEY"
REMOTE
