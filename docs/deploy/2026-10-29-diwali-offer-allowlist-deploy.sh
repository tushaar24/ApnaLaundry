#!/usr/bin/env bash
# Diwali offer allow-list (owner, 29 Oct 21:15 IST): rows in laundry_offer_sends with template 'manual_allow'
# get the ₹999 offer whatever their sign-up date. Ships the final services/laundry-paywall-offer.js from
# docs/deploy/2026-10-29-backend-diwali-999-offer.patch (routes file unchanged since the audience deploy).
#   bash docs/deploy/2026-10-29-diwali-offer-allowlist-deploy.sh
set -euo pipefail
cd "$(dirname "$0")"
python3 - <<'PY' > /tmp/laundry-paywall-offer.js
import re
p=open('2026-10-29-backend-diwali-999-offer.patch').read()
blk=p[p.index('+++ b/backend/services/laundry-paywall-offer.js'):]
lines=blk.split('\n')[2:]
print('\n'.join(l[1:] for l in lines if l.startswith('+')))
PY
scp -q -o BatchMode=yes /tmp/laundry-paywall-offer.js courses-aws:/tmp/laundry-paywall-offer.js; rm -f /tmp/laundry-paywall-offer.js
ssh -o BatchMode=yes courses-aws 'bash -s' <<'REMOTE'
set -euo pipefail
cd ~/shweta-makeup-monorepo/backend
B=~/backend-filebak/$(date -u +%Y%m%d-%H%M%S)-offer-allowlist; mkdir -p "$B"; cp services/laundry-paywall-offer.js "$B"/
cp /tmp/laundry-paywall-offer.js services/laundry-paywall-offer.js; rm -f /tmp/laundry-paywall-offer.js
grep -q "manual_allow" services/laundry-paywall-offer.js
git add services/laundry-paywall-offer.js && git commit -q -m "laundry billing: offer allow-list (laundry_offer_sends template manual_allow skips the sign-up-date rule)" || true
cd ~/shweta-makeup-monorepo
docker compose -f docker-compose.prod.yml up -d --build backend 2>&1 | tail -1
sleep 10; docker ps --format '{{.Names}} {{.Status}}' | grep backend-1
docker exec shweta-makeup-monorepo-backend-1 node -e '
const { PrismaClient } = require("@prisma/client"); const p = new PrismaClient(); const o = require("/app/services/laundry-paywall-offer");
(async () => {
  const aud = await o.audience(p); const cur = o.current();
  const [u] = await p.$queryRawUnsafe("select id, created_at from laundry_users where phone like $1", "%8796221599");
  const show = (x) => x ? "₹" + x.prices.annual / 100 + " yearly-only" : "standard paywall";
  const a = o.apply(null, null, 17, 5, { id: u.id, createdAt: u.created_at }, aud);
  const b = o.apply(null, null, 17, 5, { id: "someone-new", createdAt: new Date() }, aud);
  console.log("forced ids:", aud.forced.size, "| 8796221599 (created", u.created_at.toISOString().slice(0,16), ") →", show(a), "| another new sign-up →", show(b));
  await p.$disconnect(); process.exit(a && !b ? 0 : 1);
})().catch((e) => { console.error(e.message); process.exit(1); });' && echo VERIFIED
echo "backup: $B"
REMOTE
