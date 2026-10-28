#!/usr/bin/env bash
# Diwali offer (owner, 2026-10-29): ₹999 for a year, for 24 hours, for every shop without an
# active plan. Backend patch 2026-10-29-backend-diwali-999-offer.patch (services/laundry-paywall-offer.js,
# routes/laundry-billing.js) + LAUNDRY_PAYWALL_OFFER in .env. Creates the ₹999 yearly Razorpay plan
# on the laundry account if PLAN_ID is not given. Note: docs/deploy/2026-10-29-diwali-999-offer.md
#
#   DRY_RUN=1 bash docs/deploy/2026-10-29-diwali-999-offer-deploy.sh              # patch check only
#   FROM=2026-10-29T04:30:00Z HOURS=24 bash docs/deploy/2026-10-29-diwali-999-offer-deploy.sh
#   PLAN_ID=plan_… to reuse a plan; PRICE=999 (rupees) is the default.
# To end the offer early: set "until" in LAUNDRY_PAYWALL_OFFER to now and rebuild. To re-run with
# a new window: edit from/until in .env and rebuild (the code stays).
set -euo pipefail
cd "$(dirname "$0")"
PATCH=2026-10-29-backend-diwali-999-offer.patch
scp -q -o BatchMode=yes "$PATCH" courses-aws:/tmp/diwali-offer.patch

ssh -o BatchMode=yes courses-aws "DRY_RUN=${DRY_RUN:-0} FROM='${FROM:-}' HOURS='${HOURS:-24}' PLAN_ID='${PLAN_ID:-}' PRICE='${PRICE:-999}' bash -s" <<'REMOTE'
set -euo pipefail
cd ~/shweta-makeup-monorepo/backend
C=shweta-makeup-monorepo-backend-1
if grep -q "laundry-paywall-offer" routes/laundry-billing.js; then echo "route already patched — only the env window will be (re)written"; PATCHED=1; else PATCHED=0; patch -p2 --dry-run < /tmp/diwali-offer.patch; fi
if [ "$DRY_RUN" = 1 ]; then echo "dry run ok — nothing changed"; exit 0; fi

FROM_TS=${FROM:-$(date -u +%Y-%m-%dT%H:%M:%SZ)}
UNTIL_TS=$(date -u -d "$FROM_TS + $HOURS hours" +%Y-%m-%dT%H:%M:%SZ)
if [ -z "$PLAN_ID" ]; then
  PLAN_ID=$(docker exec -e PRICE="$PRICE" "$C" node -e '
const auth = "Basic " + Buffer.from(process.env.LAUNDRY_RAZORPAY_KEY_ID + ":" + process.env.LAUNDRY_RAZORPAY_KEY_SECRET).toString("base64");
const amount = Math.round(Number(process.env.PRICE) * 100);
(async () => {
  const list = await (await fetch("https://api.razorpay.com/v1/plans?count=100", { headers: { Authorization: auth } })).json();
  const have = (list.items || []).find((p) => p.period === "yearly" && p.interval === 1 && p.item.amount === amount);
  if (have) { console.log(have.id); return; }
  const r = await fetch("https://api.razorpay.com/v1/plans", { method: "POST", headers: { Authorization: auth, "Content-Type": "application/json" },
    body: JSON.stringify({ period: "yearly", interval: 1, item: { name: "Diwali Annual ₹" + process.env.PRICE, amount, currency: "INR", description: "MyLaundry yearly plan, Diwali offer" } }) });
  const j = await r.json(); if (!r.ok) { console.error(JSON.stringify(j)); process.exit(1); } console.log(j.id);
})().catch((e) => { console.error(e.message); process.exit(1); });')
fi
echo "plan: $PLAN_ID | window: $FROM_TS → $UNTIL_TS (UTC) | price ₹$PRICE/year"
OFFER="{\"name\":\"diwali999\",\"from\":\"$FROM_TS\",\"until\":\"$UNTIL_TS\",\"plans\":[\"annual\"],\"default\":\"annual\",\"prices\":{\"annual\":$PRICE},\"planIds\":{\"annual\":\"$PLAN_ID\"}}"

B=~/backend-filebak/$(date -u +%Y%m%d-%H%M%S)-diwali-offer
mkdir -p "$B"; cp routes/laundry-billing.js .env "$B"/; [ -f services/laundry-paywall-offer.js ] && cp services/laundry-paywall-offer.js "$B"/ || true
[ "$PATCHED" = 1 ] || patch -p2 < /tmp/diwali-offer.patch
if grep -q '^LAUNDRY_PAYWALL_OFFER=' .env; then sed -i "s|^LAUNDRY_PAYWALL_OFFER=.*|LAUNDRY_PAYWALL_OFFER=$OFFER|" .env; else printf '\n# Time-boxed price offer for shops without a plan (services/laundry-paywall-offer.js)\nLAUNDRY_PAYWALL_OFFER=%s\n' "$OFFER" >> .env; fi
grep -n '^LAUNDRY_PAYWALL_OFFER=' .env
git add -A routes/laundry-billing.js services/laundry-paywall-offer.js
git commit -q -m "laundry billing: time-boxed price offer for shops without a plan (LAUNDRY_PAYWALL_OFFER; Diwali ₹999/year)" || true

cd ~/shweta-makeup-monorepo
# Campaign tables (no Prisma migration): offer views from /billing/status, and the WhatsApp sends.
docker exec "$C" node -e '
const { PrismaClient } = require("@prisma/client"); const p = new PrismaClient();
(async () => {
  await p.$executeRawUnsafe("create table if not exists laundry_offer_events (id bigserial primary key, user_id text not null, offer text not null, kind text not null, at timestamptz not null default now())");
  await p.$executeRawUnsafe("create index if not exists laundry_offer_events_user_idx on laundry_offer_events (offer, user_id, at)");
  await p.$executeRawUnsafe("create table if not exists laundry_offer_sends (phone text primary key, user_id text, tier int, name text, batch text, wa_message_id text, status text, error text, sent_at timestamptz not null default now())");
  console.log("campaign tables ok"); await p.$disconnect();
})().catch((e) => { console.error(e.message); process.exit(1); });'
docker compose -f docker-compose.prod.yml up -d --build backend 2>&1 | tail -1
sleep 10
docker ps --format '{{.Names}} {{.Status}}' | grep backend-1
docker exec "$C" node -e '
const o = require("/app/services/laundry-paywall-offer");
const cur = o.current(); if (!cur) { console.log("offer NOT active now (check from/until):", process.env.LAUNDRY_PAYWALL_OFFER); process.exit(1); }
const pt = o.apply(null, null, 16, 5);
console.log("offer:", cur.name, "until", new Date(cur.until).toISOString(), "| legacy shop on 1.0.16 →", JSON.stringify({ plans: pt.plans, annual: pt.prices.annual, planId: pt.planIds.annual, drawn: pt.drawn, trial: pt.trial }));
const kept = o.apply(null, { status: "active" }, 16, 5); if (kept !== null) { console.log("FAILED: active shop got the offer"); process.exit(1); }
if (pt.prices.annual !== Math.round(Number(process.env.PRICE_CHECK||0)*100) && process.env.PRICE_CHECK) process.exit(1);
console.log("ok");' || { echo "check failed — rolling back"; cp "$B/routes/laundry-billing.js" backend/routes/; cp "$B/.env" backend/; rm -f backend/services/laundry-paywall-offer.js; docker compose -f docker-compose.prod.yml up -d --build backend; exit 1; }
echo "backup: $B"
echo "LIVE: ₹$PRICE/year (plan $PLAN_ID) for shops without a plan, $FROM_TS → $UNTIL_TS UTC. Link: https://mylaundry.work/pay/diwali999"
REMOTE
