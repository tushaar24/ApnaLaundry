#!/usr/bin/env bash
# Services-setup A/B (owner, 2026-10-28): brand-new accounts whose first login is on Android
# 1.0.17+ split 50/50 — "samples" (setup step 2 = the seeded sample list, as before) vs
# "scratch" (no services step, no sample services, "Add service" on New order, no guide).
# Patch: 2026-10-28-backend-services-setup-ab.patch (routes/laundry-experiments.js) +
# .env LAUNDRY_SERVICES_SETUP_START=<now> and LAUNDRY_SERVICES_SETUP_SCRATCH_PCT=50.
# Note: docs/deploy/2026-10-28-services-setup-ab.md
#
#   DRY_RUN=1 bash docs/deploy/2026-10-28-services-setup-ab-deploy.sh   # patch + check a copy, changes nothing
#   bash docs/deploy/2026-10-28-services-setup-ab-deploy.sh             # apply
#   PCT=50 is the default share that gets "scratch".
set -euo pipefail
cd "$(dirname "$0")"
PATCH=2026-10-28-backend-services-setup-ab.patch
scp -q -o BatchMode=yes "$PATCH" courses-aws:/tmp/services-setup.patch

ssh -o BatchMode=yes courses-aws "DRY_RUN=${DRY_RUN:-0} PCT='${PCT:-50}' bash -s" <<'REMOTE'
set -euo pipefail
cd ~/shweta-makeup-monorepo
ENV=backend/.env
FILE=backend/routes/laundry-experiments.js
C=shweta-makeup-monorepo-backend-1
FROM=$(date -u +%Y-%m-%dT%H:%M:%SZ)

grep -q 'servicesSetupFor' "$FILE" && { echo "already patched"; exit 1; }
grep -q '^LAUNDRY_SERVICES_SETUP_START=' "$ENV" && { echo "LAUNDRY_SERVICES_SETUP_START is already in .env"; exit 1; }

# Patch a copy and check it with the live container's node before touching anything.
T=$(mktemp -d)
mkdir -p "$T/backend/routes" && cp "$FILE" "$T/backend/routes/"
(cd "$T" && patch -p1 --quiet < /tmp/services-setup.patch)
docker cp "$T/$FILE" "$C":/app/routes/laundry-experiments.check.js
docker exec -e FROM="$FROM" -e PCT="$PCT" "$C" node -e '
const live = require("/app/routes/laundry-experiments");
const next = require("/app/routes/laundry-experiments.check.js");
const { PrismaClient } = require("@prisma/client"); const prisma = new PrismaClient();
(async () => {
  // 1) customerFlow untouched for real accounts
  const users = await prisma.laundryUser.findMany({ orderBy: { createdAt: "desc" }, take: 500, select: { id: true, phone: true, createdAt: true, signupPlatform: true, signupAppVersion: true } });
  const sameFlow = users.every((u) => next.customerFlowFor(u) === live.customerFlowFor(u));
  // 2) test off without the setting: nobody gets a group
  delete process.env.LAUNDRY_SERVICES_SETUP_START;
  const offAll = users.every((u) => next.servicesSetupFor(u) === null);
  // 3) test on: existing accounts (created before FROM) stay out; old apps stay out; new 1.0.17 accounts split ~PCT
  process.env.LAUNDRY_SERVICES_SETUP_START = process.env.FROM; process.env.LAUNDRY_SERVICES_SETUP_SCRATCH_PCT = process.env.PCT;
  const oldOut = users.every((u) => next.servicesSetupFor(u) === null);
  const later = new Date(Date.parse(process.env.FROM) + 60e3);
  const v16 = next.servicesSetupFor({ id: "chk-v16", phone: "9999999999", signupPlatform: "android", signupAppVersion: 16, createdAt: later });
  const web = next.servicesSetupFor({ id: "chk-web", phone: "9999999999", signupPlatform: "web", signupAppVersion: null, createdAt: later });
  const n = {}; for (let i = 0; i < 4000; i++) { const g = next.servicesSetupFor({ id: "chk-" + i, phone: "9999999999", signupPlatform: "android", signupAppVersion: 17, createdAt: later }); n[g] = (n[g] || 0) + 1; }
  const near = Math.abs((n.scratch || 0) / 4000 - Number(process.env.PCT) / 100) < 0.03 && (n.samples || 0) + (n.scratch || 0) === 4000;
  const ok = sameFlow && offAll && oldOut && v16 === null && web === null && near;
  console.log((ok ? "check: OK" : "check: FAILED") + ` — customerFlow unchanged: ${sameFlow}; off without START: ${offAll}; ${users.length} existing accounts out: ${oldOut}; app 16 → ${v16}; web → ${web}; new 1.0.17 accounts → ${JSON.stringify(n)}`);
  await prisma.$disconnect(); process.exit(ok ? 0 : 1);
})().catch((e) => { console.error(e); process.exit(1); });'
docker exec -u 0 "$C" rm -f /app/routes/laundry-experiments.check.js

if [ "$DRY_RUN" = 1 ]; then rm -rf "$T" /tmp/services-setup.patch; echo "DRY RUN — nothing changed"; exit 0; fi

TS=$(date -u +%Y%m%d-%H%M%S)
BAK=~/backend-filebak/$TS-services-setup-ab
mkdir -p "$BAK" && cp "$ENV" "$BAK/.env" && cp "$FILE" "$BAK/" && echo "backup: $BAK"
cp "$T/$FILE" "$FILE"; rm -rf "$T" /tmp/services-setup.patch
printf '\n# Services-setup A/B (2026-10-28): new accounts on Android 1.0.17+ from START; PCT%% get "scratch", the rest "samples"\nLAUNDRY_SERVICES_SETUP_START=%s\nLAUNDRY_SERVICES_SETUP_SCRATCH_PCT=%s\n' "$FROM" "$PCT" >> "$ENV"

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
const f = require("/app/routes/laundry-experiments").servicesSetupFor;
const later = new Date(Date.now() + 60e3);
const n = {}; for (let i = 0; i < 2000; i++) { const g = f({ id: "live-" + i, phone: "9999999999", signupPlatform: "android", signupAppVersion: 17, createdAt: later }); n[g] = (n[g] || 0) + 1; }
const old = f({ id: "live-old", phone: "9999999999", signupPlatform: "android", signupAppVersion: 17, createdAt: new Date(Date.now() - 864e5) });
const v16 = f({ id: "live-v16", phone: "9999999999", signupPlatform: "android", signupAppVersion: 16, createdAt: later });
if (!n.scratch || !n.samples || old !== null || v16 !== null) { console.log("live check FAILED", JSON.stringify({ n, old, v16 })); process.exit(1); }
console.log("live: new 1.0.17 accounts → " + JSON.stringify(n) + "; older account → " + old + "; app 16 → " + v16);' || rollback
{ git add "$FILE" && git commit -q -m "laundry: services-setup A/B (samples vs scratch) for new accounts on Android 1.0.17+ (LAUNDRY_SERVICES_SETUP_*)" -- "$FILE" && git log --oneline -1; } || echo "server git commit skipped (the deploy itself is done)"
echo "LIVE from $FROM: new Android 1.0.17+ accounts → scratch $PCT% / samples $((100 - PCT))%; everyone else unchanged"
REMOTE
