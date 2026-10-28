#!/usr/bin/env bash
# Diwali offer, eligibility tightened (owner, 29 Oct 00:55 IST): only EXISTING owners (account created
# before the offer start) whose WhatsApp send Meta ACCEPTED see ₹999 (audience "messaged", from
# laundry_offer_sends). Everyone else — new sign-ups, owners who were not messaged — keeps the normal paywall.
# Writes the final services/laundry-paywall-offer.js + the two apply() call sites in routes/laundry-billing.js
# from docs/deploy/2026-10-29-backend-diwali-999-offer.patch's "b" versions, adds "audience":"messaged" to
# LAUNDRY_PAYWALL_OFFER, rebuilds the backend, verifies three cases inside the container.
#   bash docs/deploy/2026-10-29-diwali-offer-audience-deploy.sh
set -euo pipefail
cd "$(dirname "$0")"
scp -q -o BatchMode=yes 2026-10-29-backend-diwali-999-offer.patch courses-aws:/tmp/diwali-offer-final.patch
ssh -o BatchMode=yes courses-aws 'bash -s' <<'REMOTE'
set -euo pipefail
cd ~/shweta-makeup-monorepo/backend
B=~/backend-filebak/$(date -u +%Y%m%d-%H%M%S)-offer-audience; mkdir -p "$B"; cp services/laundry-paywall-offer.js routes/laundry-billing.js .env "$B"/
# Start from the pre-offer route (first backup of the day) and apply the final patch cleanly.
ORIG=$(ls -d ~/backend-filebak/*-diwali-offer | head -1)
cp "$ORIG/laundry-billing.js" routes/laundry-billing.js
rm -f services/laundry-paywall-offer.js
patch -p2 < /tmp/diwali-offer-final.patch
grep -q '"audience"' .env || sed -i 's|^LAUNDRY_PAYWALL_OFFER={"name":"diwali999"|LAUNDRY_PAYWALL_OFFER={"name":"diwali999","audience":"messaged"|' .env
grep -n '^LAUNDRY_PAYWALL_OFFER=' .env | cut -c1-110
git add services/laundry-paywall-offer.js routes/laundry-billing.js
git commit -q -m "laundry billing: price offer for existing, messaged owners only (audience: messaged; accounts created before the offer start)" || true
cd ~/shweta-makeup-monorepo
docker compose -f docker-compose.prod.yml up -d --build backend 2>&1 | tail -1
sleep 10; docker ps --format '{{.Names}} {{.Status}}' | grep backend-1
docker exec shweta-makeup-monorepo-backend-1 node -e '
const { PrismaClient } = require("@prisma/client"); const p = new PrismaClient();
const o = require("/app/services/laundry-paywall-offer");
(async () => {
  const cur = o.current(); const aud = await o.audience(p);
  const [m] = await p.$queryRawUnsafe("select s.user_id, u.created_at from laundry_offer_sends s join laundry_users u on u.id = s.user_id where s.wa_message_id is not null limit 1");
  const show = (x) => x ? "₹" + x.prices.annual / 100 + " yearly-only" : "standard paywall";
  const a = o.apply(null, null, 17, 5, { id: m.user_id, createdAt: m.created_at }, aud);
  const b = o.apply(null, null, 17, 5, { id: "nobody", createdAt: new Date(cur.from - 864e5) }, aud);
  const c = o.apply(null, null, 17, 5, { id: m.user_id, createdAt: new Date(cur.from + 3600e3) }, aud);
  console.log("audience", cur.audience, "size", aud.size, "| messaged existing →", show(a), "| not messaged →", show(b), "| new sign-up →", show(c));
  await p.$disconnect(); process.exit(a && !b && !c ? 0 : 1);
})().catch((e) => { console.error(e.message); process.exit(1); });' && echo VERIFIED
echo "backup: $B"
REMOTE
