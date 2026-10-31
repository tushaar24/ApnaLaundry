#!/usr/bin/env bash
# Owner, 31 Oct 2026: re-open the ₹999 yearly Diwali price for ONE shop (LAUNDRY POINT, 7602017890) without
# re-opening it for the 259 messaged owners. Adds audience "allowlist" to services/laundry-paywall-offer.js
# (only laundry_offer_sends rows with template manual_allow see the offer), adds the shop's allow row, sets
# LAUNDRY_PAYWALL_OFFER to audience allowlist until 7 Nov 23:59 IST, recreates the backend and verifies.
#   bash docs/deploy/2026-10-31-diwali-offer-allowlist-only.sh
set -euo pipefail
PHONE="${PHONE:-7602017890}"; NAME="${NAME:-LAUNDRY POINT}"; UNTIL="${UNTIL:-2026-11-07T18:29:59Z}"
scp -q "$(dirname "$0")/../../courses/backend/services/laundry-paywall-offer.js" courses-aws:/tmp/laundry-paywall-offer.js
ssh -o BatchMode=yes courses-aws "PHONE='$PHONE' NAME='$NAME' UNTIL='$UNTIL' bash -s" <<'REMOTE'
set -euo pipefail
cd ~/shweta-makeup-monorepo
C=shweta-makeup-monorepo-backend-1
B=~/backend-filebak/$(date -u +%Y%m%d-%H%M%S)-offer-allowlist; mkdir -p "$B"; cp backend/.env "$B/.env"; cp backend/services/laundry-paywall-offer.js "$B/"
cp /tmp/laundry-paywall-offer.js backend/services/laundry-paywall-offer.js; rm -f /tmp/laundry-paywall-offer.js
# 1. allow row (idempotent)
docker exec -e PHONE="$PHONE" -e NAME="$NAME" "$C" node -e '
const { PrismaClient } = require("@prisma/client"); const p = new PrismaClient();
(async () => {
  const u = (await p.$queryRawUnsafe("select id from laundry_users where phone like $1 order by created_at limit 1", "%" + process.env.PHONE))[0];
  if (!u) { console.log("NO USER"); process.exit(1); }
  const n = await p.$executeRawUnsafe("insert into laundry_offer_sends (phone, user_id, tier, name, batch, status, template, wa_message_id, sent_at) values ($1, $2, 0, $3, $4, $5, $6, $7, now()) on conflict (phone, template) do nothing",
    process.env.PHONE, u.id, process.env.NAME, "manual", "manual", "manual_allow", "manual:" + process.env.PHONE);
  console.log("allow row for", process.env.PHONE, u.id, n ? "inserted" : "already there");
  await p.$disconnect(); })();'
# 2. env: audience allowlist, new until
UNTIL="$UNTIL" python3 - <<'PY'
import json, os
lines = open("backend/.env").read().split("\n")
out = []
for l in lines:
    if l.startswith("LAUNDRY_PAYWALL_OFFER="):
        cfg = json.loads(l.split("=", 1)[1]); cfg["audience"] = "allowlist"; cfg["until"] = os.environ["UNTIL"]
        l = "LAUNDRY_PAYWALL_OFFER=" + json.dumps(cfg, separators=(",", ":"))
        print(l)
    out.append(l)
open("backend/.env", "w").write("\n".join(out))
PY
# 3. image rebuild (the service file is baked into the image) + recreate
docker compose -f docker-compose.prod.yml build -q backend 2>&1 | tail -1 || true
docker compose -f docker-compose.prod.yml up -d --force-recreate backend 2>&1 | tail -1
sleep 12; docker ps --format '{{.Names}} {{.Status}}' | grep backend-1
docker exec "$C" grep -c "allowlist" /app/services/laundry-paywall-offer.js
# 4. verify: the shop sees ₹999 yearly only; a messaged-but-not-allowed shop and a new account do not
docker exec -e PHONE="$PHONE" "$C" node -e '
const { PrismaClient } = require("@prisma/client"); const p = new PrismaClient();
const t = require("/app/services/laundry-paywall-test"), o = require("/app/services/laundry-paywall-offer");
(async () => {
  const aud = await o.audience(p);
  const show = async (label, phone) => {
    const u = await p.laundryUser.findFirst({ where: { phone: { endsWith: phone } }, orderBy: { createdAt: "asc" } });
    if (!u) return console.log(label, "no user");
    const sub = await p.laundrySubscription.findFirst({ where: { userId: u.id }, orderBy: { createdAt: "desc" } });
    const pt = o.apply(t.offer(u, Number(u.signupAppVersion) || 0), sub, Number(u.signupAppVersion) || 0, 5, u, aud);
    console.log(label, phone, "→", pt && pt.offer ? `OFFER ${pt.offer} plans ${pt.plans} annual ₹${pt.prices.annual/100} default ${pt.defaultPlan}` : `normal paywall (${pt ? pt.group : "none"})`);
  };
  await show("allow-listed  ", process.env.PHONE);
  await show("messaged only ", "9024525189");
  await show("new account   ", "9822582961");
  console.log("audience size", aud ? aud.size : null, "forced", aud && aud.forced ? [...aud.forced].length : null);
  await p.$disconnect(); })();'
docker compose -f docker-compose.prod.yml up -d --force-recreate --no-deps laundry-dash 2>&1 | tail -1
echo "backup: $B"
REMOTE
