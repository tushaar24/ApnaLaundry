#!/usr/bin/env bash
# First-customer A/B (Android 1.0.15): GET /api/laundry/experiments starts sending
# customerFlow = v1_auto_sheet | v2_no_address | v3_tap_to_add (~34/33/33) for accounts
# whose FIRST login is on Android 1.0.15+ from the moment this runs. Everyone else gets
# the same response as today. Note: docs/deploy/2026-10-24-customer-flow-ab.md
#
#   DRY_RUN=1 bash docs/deploy/2026-10-24-customer-flow-ab-deploy.sh  # patch + check a copy, changes nothing
#   bash docs/deploy/2026-10-24-customer-flow-ab-deploy.sh            # apply
#   FORCE='{"1000000028":"v2_no_address"}' bash docs/deploy/...       # also pin QA phones to a variant
set -euo pipefail
cd "$(dirname "$0")"
PATCH=2026-10-24-backend-customer-flow-ab.patch
scp -q -o BatchMode=yes "$PATCH" courses-aws:/tmp/customer-flow-ab.patch

ssh -o BatchMode=yes courses-aws "DRY_RUN=${DRY_RUN:-0} FORCE='${FORCE:-}' bash -s" <<'REMOTE'
set -euo pipefail
cd ~/shweta-makeup-monorepo
ENV=backend/.env
FILE=backend/routes/laundry-experiments.js
C=shweta-makeup-monorepo-backend-1
FROM=$(date -u +%Y-%m-%dT%H:%M:%SZ)

grep -q 'customerFlowFor' "$FILE" && { echo "already patched"; exit 1; }
grep -q '^LAUNDRY_CUSTOMER_FLOW_START=' "$ENV" && { echo "LAUNDRY_CUSTOMER_FLOW_START is already in .env"; exit 1; }

# Patch a copy and check it with the live container's node before touching anything.
T=$(mktemp -d)
mkdir -p "$T/backend/routes" && cp "$FILE" "$T/backend/routes/"
(cd "$T" && patch -p1 --quiet < /tmp/customer-flow-ab.patch)
# Next to the real route, so its ../middleware and ../services requires resolve.
docker cp "$T/$FILE" "$C":/app/routes/laundry-experiments.check.js
docker exec -e FROM="$FROM" "$C" node -e '
process.env.LAUNDRY_CUSTOMER_FLOW_START = process.env.FROM;
const r = require("/app/routes/laundry-experiments.check.js");
const f = r.customerFlowFor;
const u = (v, ago, i = 1) => ({ id: "check-" + i + "-" + v + "-" + ago, phone: "9999999999", signupPlatform: "android", signupAppVersion: v, createdAt: new Date(Date.now() - ago) });
const n = {}; for (let i = 0; i < 3000; i++) { const g = f(u(15, -60e3, i)); n[g] = (n[g] || 0) + 1; }
const ok = f(u(14, -60e3)) === null && f(u(15, 86400e3)) === null && f({ ...u(15, -60e3), signupPlatform: "web" }) === null
  && Object.keys(n).sort().join() === "v1_auto_sheet,v2_no_address,v3_tap_to_add" && Object.values(n).every((x) => x > 800);
console.log(ok ? "check: OK — new 1.0.15 accounts split " + JSON.stringify(n) + "; 1.0.14, older and web accounts get nothing" : "check: FAILED " + JSON.stringify(n));
process.exit(ok ? 0 : 1);'
docker exec -u 0 "$C" rm -f /app/routes/laundry-experiments.check.js

if [ "$DRY_RUN" = 1 ]; then rm -rf "$T" /tmp/customer-flow-ab.patch; echo "DRY RUN — nothing changed"; exit 0; fi

TS=$(date -u +%Y%m%d-%H%M%S)
BAK=~/backend-filebak/$TS-customer-flow-ab
mkdir -p "$BAK" && cp "$ENV" "$BAK/.env" && cp "$FILE" "$BAK/" && echo "backup: $BAK"
cp "$T/$FILE" "$FILE"; rm -rf "$T" /tmp/customer-flow-ab.patch
{ echo "LAUNDRY_CUSTOMER_FLOW_START=$FROM"; echo "LAUNDRY_CUSTOMER_FLOW_SPLIT=34,33,33"; [ -n "$FORCE" ] && echo "LAUNDRY_CUSTOMER_FLOW_FORCE=$FORCE"; true; } >> "$ENV"

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
const g = f({ id: "live-check", phone: "9999999999", signupPlatform: "android", signupAppVersion: 15, createdAt: new Date(Date.now() + 60e3) });
if (!g || f({ id: "live-check", phone: "9999999999", signupPlatform: "android", signupAppVersion: 14, createdAt: new Date() }) !== null) process.exit(1);
console.log("live: new 1.0.15 account → " + g);' || rollback
{ git add "$FILE" && git commit -q -m "laundry: first-customer A/B (customerFlow on /experiments, LAUNDRY_CUSTOMER_FLOW_*)" -- "$FILE" && git log --oneline -1; } || echo "server git commit skipped (the deploy itself is done)"
echo "LIVE from $FROM: accounts whose first login is on Android 1.0.15+ get a customerFlow variant"
REMOTE
