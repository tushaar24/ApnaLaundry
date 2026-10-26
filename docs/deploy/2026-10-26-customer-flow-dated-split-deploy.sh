#!/usr/bin/env bash
# First-customer A/B: new sign-ups from now get v1 10% / v2 45% / v3 45% (owner, 2026-10-26). Accounts
# already in the test keep their variant — the new LAUNDRY_CUSTOMER_FLOW_SPLITS only applies to accounts
# created at/after its `from` (patch: 2026-10-26-backend-customer-flow-dated-split.patch).
#
#   DRY_RUN=1 bash docs/deploy/2026-10-26-customer-flow-dated-split-deploy.sh   # patch + check a copy, changes nothing
#   bash docs/deploy/2026-10-26-customer-flow-dated-split-deploy.sh             # apply
#   SPLIT=10,45,45 is the default; pass SPLIT=… for another one.
set -euo pipefail
cd "$(dirname "$0")"
PATCH=2026-10-26-backend-customer-flow-dated-split.patch
scp -q -o BatchMode=yes "$PATCH" courses-aws:/tmp/cf-dated-split.patch

ssh -o BatchMode=yes courses-aws "DRY_RUN=${DRY_RUN:-0} SPLIT='${SPLIT:-10,45,45}' bash -s" <<'REMOTE'
set -euo pipefail
cd ~/shweta-makeup-monorepo
ENV=backend/.env
FILE=backend/routes/laundry-experiments.js
C=shweta-makeup-monorepo-backend-1
FROM=$(date -u +%Y-%m-%dT%H:%M:%SZ)
SPLITS="[{\"from\":\"$FROM\",\"split\":[$SPLIT]}]"

grep -q 'LAUNDRY_CUSTOMER_FLOW_SPLITS' "$FILE" && { echo "already patched"; exit 1; }
grep -q '^LAUNDRY_CUSTOMER_FLOW_SPLITS=' "$ENV" && { echo "LAUNDRY_CUSTOMER_FLOW_SPLITS is already in .env"; exit 1; }

# Patch a copy and check it with the live container's node + real accounts before touching anything.
T=$(mktemp -d)
mkdir -p "$T/backend/routes" && cp "$FILE" "$T/backend/routes/"
(cd "$T" && patch -p1 --quiet < /tmp/cf-dated-split.patch)
docker cp "$T/$FILE" "$C":/app/routes/laundry-experiments.check.js
docker exec -e SPLITS="$SPLITS" -e FROM="$FROM" "$C" node -e '
const live = require("/app/routes/laundry-experiments").customerFlowFor;
const { PrismaClient } = require("@prisma/client"); const prisma = new PrismaClient();
(async () => {
  // 1) without the new setting the patched code answers exactly like the live one
  delete require.cache[require.resolve("/app/routes/laundry-experiments.check.js")];
  const plain = require("/app/routes/laundry-experiments.check.js").customerFlowFor;
  const users = await prisma.laundryUser.findMany({ where: { createdAt: { gte: new Date(process.env.LAUNDRY_CUSTOMER_FLOW_START) } }, select: { id: true, phone: true, createdAt: true, signupPlatform: true, signupAppVersion: true } });
  const same1 = users.every((u) => plain(u) === live(u));
  // 2) with it: every existing account keeps its variant
  process.env.LAUNDRY_CUSTOMER_FLOW_SPLITS = process.env.SPLITS;
  const next = require("/app/routes/laundry-experiments.check.js").customerFlowFor;
  const moved = users.filter((u) => next(u) !== live(u)).length;
  const inTest = users.filter((u) => live(u)).length;
  // 3) new accounts split by the new numbers
  const later = new Date(Date.parse(process.env.FROM) + 60e3);
  const n = {}; for (let i = 0; i < 4000; i++) { const g = next({ id: "chk-" + i, phone: "9999999999", signupPlatform: "android", signupAppVersion: 16, createdAt: later }); n[g] = (n[g] || 0) + 1; }
  const want = JSON.parse(process.env.SPLITS)[0].split, tot = want.reduce((a, x) => a + x, 0);
  const near = ["v1_auto_sheet", "v2_no_address", "v3_tap_to_add"].every((k, i) => Math.abs((n[k] || 0) / 4000 - want[i] / tot) < 0.03);
  const ok = same1 && moved === 0 && near;
  console.log((ok ? "check: OK" : "check: FAILED") + ` — unchanged without the setting: ${same1}; ${inTest} accounts in the test, moved: ${moved}; new accounts → ${JSON.stringify(n)}`);
  await prisma.$disconnect(); process.exit(ok ? 0 : 1);
})().catch((e) => { console.error(e); process.exit(1); });'
docker exec -u 0 "$C" rm -f /app/routes/laundry-experiments.check.js

if [ "$DRY_RUN" = 1 ]; then rm -rf "$T" /tmp/cf-dated-split.patch; echo "DRY RUN — nothing changed"; exit 0; fi

TS=$(date -u +%Y%m%d-%H%M%S)
BAK=~/backend-filebak/$TS-customer-flow-dated-split
mkdir -p "$BAK" && cp "$ENV" "$BAK/.env" && cp "$FILE" "$BAK/" && echo "backup: $BAK"
cp "$T/$FILE" "$FILE"; rm -rf "$T" /tmp/cf-dated-split.patch
echo "LAUNDRY_CUSTOMER_FLOW_SPLITS=$SPLITS" >> "$ENV"

rollback() {
  echo "rolling back"
  cp "$BAK/.env" "$ENV"; cp "$BAK/laundry-experiments.js" "$FILE"
  docker compose -f docker-compose.prod.yml up -d --build backend
  exit 1
}
docker compose -f docker-compose.prod.yml up -d --build backend
sleep 10
docker exec "$C" node -e '
const f = require("/app/routes/laundry-experiments").customerFlowFor;
const n = {}; for (let i = 0; i < 2000; i++) { const g = f({ id: "live-" + i, phone: "9999999999", signupPlatform: "android", signupAppVersion: 16, createdAt: new Date(Date.now() + 60e3) }); n[g] = (n[g] || 0) + 1; }
if (!n.v2_no_address || !n.v3_tap_to_add || (n.v1_auto_sheet || 0) > 400) process.exit(1);
console.log("live: new accounts → " + JSON.stringify(n));' || rollback
{ git add "$FILE" && git commit -q -m "laundry: dated customer-flow splits (LAUNDRY_CUSTOMER_FLOW_SPLITS; new sign-ups only)" -- "$FILE" && git log --oneline -1; } || echo "server git commit skipped (the deploy itself is done)"
echo "LIVE from $FROM: new sign-ups split $SPLIT (v1,v2,v3); existing accounts unchanged"
REMOTE
