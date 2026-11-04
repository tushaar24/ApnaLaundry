#!/usr/bin/env bash
# Send-bill contacts ask switch (owner, 2026-11-04): GET /api/laundry/experiments returns
# billContactsAsk = ask | no_ask for every account; Android 1.0.18 asks for the Contacts permission
# at the first WhatsApp bill send only for "ask". LAUNDRY_BILL_CONTACTS_ASK_PCT = share that gets
# "ask" (0 = off for everyone, 100 = on for everyone). Older apps ignore the key.
# Patch: 2026-11-04-backend-bill-contacts-ask.patch (routes/laundry-experiments.js) + .env PCT.
# Note: docs/deploy/2026-11-04-bill-contacts-ask.md
#
#   DRY_RUN=1 bash docs/deploy/2026-11-04-bill-contacts-ask-deploy.sh   # patch + check a copy, changes nothing
#   bash docs/deploy/2026-11-04-bill-contacts-ask-deploy.sh             # apply (PCT=50 by default)
#   PCT=100 bash docs/deploy/2026-11-04-bill-contacts-ask-deploy.sh     # apply, everyone asked
set -euo pipefail
cd "$(dirname "$0")"
PATCH=2026-11-04-backend-bill-contacts-ask.patch
scp -q -o BatchMode=yes "$PATCH" courses-aws:/tmp/bill-contacts-ask.patch

ssh -o BatchMode=yes courses-aws "DRY_RUN=${DRY_RUN:-0} PCT='${PCT:-50}' bash -s" <<'REMOTE'
set -euo pipefail
cd ~/shweta-makeup-monorepo
ENV=backend/.env
FILE=backend/routes/laundry-experiments.js
C=shweta-makeup-monorepo-backend-1

grep -q 'billContactsAskFor' "$FILE" && { echo "already patched"; exit 1; }
grep -q '^LAUNDRY_BILL_CONTACTS_ASK_PCT=' "$ENV" && { echo "LAUNDRY_BILL_CONTACTS_ASK_PCT is already in .env"; exit 1; }

# Patch a copy and check it with the live container's node before touching anything.
T=$(mktemp -d)
mkdir -p "$T/backend/routes" && cp "$FILE" "$T/backend/routes/"
(cd "$T" && patch -p1 --quiet < /tmp/bill-contacts-ask.patch)
docker cp "$T/$FILE" "$C":/app/routes/laundry-experiments.check.js
docker exec -e PCT="$PCT" "$C" node -e '
const live = require("/app/routes/laundry-experiments");
const next = require("/app/routes/laundry-experiments.check.js");
const { PrismaClient } = require("@prisma/client"); const prisma = new PrismaClient();
(async () => {
  // 1) the other tests are untouched for real accounts
  const users = await prisma.laundryUser.findMany({ orderBy: { createdAt: "desc" }, take: 500, select: { id: true, phone: true, createdAt: true, signupPlatform: true, signupAppVersion: true } });
  const same = users.every((u) => next.customerFlowFor(u) === live.customerFlowFor(u) && next.servicesSetupFor(u) === live.servicesSetupFor(u));
  // 2) the split: PCT% ask, 0 = nobody, 100 = everybody, stable per account
  const split = (pct) => { process.env.LAUNDRY_BILL_CONTACTS_ASK_PCT = String(pct); const n = { ask: 0, no_ask: 0 }; for (let i = 0; i < 4000; i++) n[next.billContactsAskFor({ id: "chk-" + i, phone: "9999999999" })]++; return n; };
  const p0 = split(0), p100 = split(100), p = split(process.env.PCT);
  const near = Math.abs(p.ask / 4000 - Number(process.env.PCT) / 100) < 0.03;
  const stable = users.every((u) => next.billContactsAskFor(u) === next.billContactsAskFor(u));
  // 3) a forced QA phone wins
  process.env.LAUNDRY_BILL_CONTACTS_ASK_FORCE = JSON.stringify({ "9999999999": "no_ask" }); process.env.LAUNDRY_BILL_CONTACTS_ASK_PCT = "100";
  const forced = next.billContactsAskFor({ id: "chk-f", phone: "+919999999999" }) === "no_ask";
  const real = {}; process.env.LAUNDRY_BILL_CONTACTS_ASK_PCT = process.env.PCT; delete process.env.LAUNDRY_BILL_CONTACTS_ASK_FORCE;
  for (const u of users) { const g = next.billContactsAskFor(u); real[g] = (real[g] || 0) + 1; }
  const ok = same && p0.ask === 0 && p100.no_ask === 0 && near && stable && forced;
  console.log((ok ? "check: OK" : "check: FAILED") + ` — other tests unchanged: ${same}; PCT 0 → ${JSON.stringify(p0)}; 100 → ${JSON.stringify(p100)}; ${process.env.PCT} → ${JSON.stringify(p)}; force: ${forced}; last ${users.length} accounts → ${JSON.stringify(real)}`);
  await prisma.$disconnect(); process.exit(ok ? 0 : 1);
})().catch((e) => { console.error(e); process.exit(1); });'
docker exec -u 0 "$C" rm -f /app/routes/laundry-experiments.check.js

if [ "$DRY_RUN" = 1 ]; then rm -rf "$T" /tmp/bill-contacts-ask.patch; echo "DRY RUN — nothing changed"; exit 0; fi

TS=$(date -u +%Y%m%d-%H%M%S)
BAK=~/backend-filebak/$TS-bill-contacts-ask
mkdir -p "$BAK" && cp "$ENV" "$BAK/.env" && cp "$FILE" "$BAK/" && echo "backup: $BAK"
cp "$T/$FILE" "$FILE"; rm -rf "$T" /tmp/bill-contacts-ask.patch
printf '\n# Send-bill contacts ask (2026-11-04, Android 1.0.18): PCT%% of accounts get "ask" (0 = off for everyone, 100 = on)\nLAUNDRY_BILL_CONTACTS_ASK_PCT=%s\n' "$PCT" >> "$ENV"

rollback() {
  echo "rolling back"
  cp "$BAK/.env" "$ENV"; cp "$BAK/laundry-experiments.js" "$FILE"
  docker compose -f docker-compose.prod.yml up -d --build backend
  exit 1
}
docker compose -f docker-compose.prod.yml up -d --build backend 2>&1 | tail -2
sleep 10
docker ps --format '{{.Names}} {{.Status}}' | grep backend-1
docker exec "$C" node -e '
const f = require("/app/routes/laundry-experiments").billContactsAskFor;
const pct = parseInt(process.env.LAUNDRY_BILL_CONTACTS_ASK_PCT, 10);
const n = { ask: 0, no_ask: 0 }; for (let i = 0; i < 2000; i++) n[f({ id: "live-" + i, phone: "9999999999" })]++;
if (!Number.isFinite(pct) || Math.abs(n.ask / 2000 - pct / 100) > 0.04) { console.log("live check FAILED", JSON.stringify({ pct, n })); process.exit(1); }
console.log("live: LAUNDRY_BILL_CONTACTS_ASK_PCT=" + pct + " → " + JSON.stringify(n));' || rollback
curl -s -o /dev/null -w "api health %{http_code}\n" https://shwetamakeover.online/api/health
{ git add "$FILE" && git commit -q -m "laundry: billContactsAsk switch on /experiments (LAUNDRY_BILL_CONTACTS_ASK_PCT) for the Android 1.0.18 send-bill contacts ask" -- "$FILE" && git log --oneline -1; } || echo "server git commit skipped (the deploy itself is done)"
echo "LIVE: billContactsAsk ask $PCT% / no_ask $((100 - PCT))% (takes effect for 1.0.18 shops at their next app start)"
REMOTE
