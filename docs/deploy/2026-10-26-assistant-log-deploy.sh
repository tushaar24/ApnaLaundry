#!/usr/bin/env bash
# Ask: save what owners ask in a new table laundry_assistant_logs (owner, 2026-10-26) — the owner's words
# (typed / chip / voice transcript), the reply text and tool names, one row per relay call. Not the shop
# data the app sends along. Additive: a new table + a fire-and-forget insert; LAUNDRY_ASSISTANT_LOG=0 stops it.
# Patch: 2026-10-26-backend-assistant-log.patch (route + schema + migration).
#
#   DRY_RUN=1 bash docs/deploy/2026-10-26-assistant-log-deploy.sh   # checks on a copy, changes nothing
#   bash docs/deploy/2026-10-26-assistant-log-deploy.sh             # apply
set -euo pipefail
cd "$(dirname "$0")"
PATCH=2026-10-26-backend-assistant-log.patch
scp -q -o BatchMode=yes "$PATCH" courses-aws:/tmp/assistant-log.patch

ssh -o BatchMode=yes courses-aws "DRY_RUN=${DRY_RUN:-0} bash -s" <<'REMOTE'
set -euo pipefail
cd ~/shweta-makeup-monorepo
C=shweta-makeup-monorepo-backend-1
ROUTE=backend/routes/laundry-assistant.js
SCHEMA=backend/prisma/schema.prisma
MIG=backend/prisma/migrations/20261026120000_laundry_assistant_logs
DC="docker compose -f docker-compose.prod.yml"

grep -q 'laundryAssistantLog' "$ROUTE" && { echo "already patched"; exit 1; }
[ -e "$MIG" ] && { echo "migration already there"; exit 1; }

# Patch a copy, then check it: schema valid, route syntax, and no OTHER migration pending on the live DB.
T=$(mktemp -d)
mkdir -p "$T/backend/routes" "$T/backend/prisma"
cp "$ROUTE" "$T/backend/routes/"; cp "$SCHEMA" "$T/backend/prisma/"
(cd "$T" && patch -p1 --quiet < /tmp/assistant-log.patch)
docker cp "$T/$ROUTE" "$C":/app/routes/laundry-assistant.check.js
docker exec "$C" node --check /app/routes/laundry-assistant.check.js && echo "route: syntax OK"
docker exec -u 0 "$C" rm -f /app/routes/laundry-assistant.check.js
# `run -T … < /dev/null`: this script arrives on stdin (bash -s); a compose run would swallow the rest of it.
$DC run -T --rm -v "$T/backend/prisma/schema.prisma:/tmp/check.prisma:ro" migrate npx prisma validate --schema /tmp/check.prisma < /dev/null 2>&1 | grep -iE "valid|error" | tail -2
STATUS=$($DC run -T --rm migrate npx prisma migrate status < /dev/null 2>&1 || true)
echo "$STATUS" | tail -2
echo "$STATUS" | grep -q "Database schema is up to date" || { echo "STOP: the live DB has other pending migrations — not applying"; exit 1; }

if [ "$DRY_RUN" = 1 ]; then rm -rf "$T" /tmp/assistant-log.patch; echo "DRY RUN — nothing changed"; exit 0; fi

TS=$(date -u +%Y%m%d-%H%M%S)
BAK=~/backend-filebak/$TS-assistant-log
mkdir -p "$BAK" && cp "$ROUTE" "$SCHEMA" "$BAK/" && echo "backup: $BAK"
cp "$T/$ROUTE" "$ROUTE"; cp "$T/backend/prisma/schema.prisma" "$SCHEMA"
mkdir -p "$MIG" && cp "$T/$MIG/migration.sql" "$MIG/"
rm -rf "$T" /tmp/assistant-log.patch

rollback() {
  echo "rolling back the code (the empty table stays; DROP TABLE laundry_assistant_logs to remove it)"
  cp "$BAK/laundry-assistant.js" "$ROUTE"; cp "$BAK/schema.prisma" "$SCHEMA"
  $DC up -d --build backend < /dev/null
  exit 1
}
$DC run -T --rm --build migrate < /dev/null 2>&1 | grep -E "applied|Applying|No pending|Error" || true
$DC up -d --build backend < /dev/null
sleep 10
# Live check: one real question through the relay as the demo account, its row saved, then removed.
docker exec "$C" node -e '
const prisma = require("/app/db");
(async () => {
  const u = await prisma.laundryUser.findUnique({ where: { phone: "1000000055" } });
  const r = await fetch("http://localhost:5001/api/laundry/assistant/chat", { method: "POST",
    headers: { authorization: "Bearer " + u.apiToken, "content-type": "application/json", "x-laundry-app-version": "16" },
    body: JSON.stringify({ messages: [{ role: "system", content: "Answer in one word." }, { role: "user", content: "deploy check: say ok" }] }) });
  await new Promise((s) => setTimeout(s, 1500));
  const row = await prisma.laundryAssistantLog.findFirst({ where: { laundryUserId: u.id, question: "deploy check: say ok" }, orderBy: { createdAt: "desc" } });
  if (!r.ok || !row) { console.log("live check FAILED", r.status, !!row); process.exit(1); }
  console.log("live: chat " + r.status + ", row saved (kind " + row.kind + ", reply " + JSON.stringify(row.reply) + ")");
  await prisma.laundryAssistantLog.delete({ where: { id: row.id } });
  process.exit(0);
})().catch((e) => { console.error(e.message); process.exit(1); });' || rollback
{ git -C backend add routes/laundry-assistant.js prisma/schema.prisma prisma/migrations/20261026120000_laundry_assistant_logs \
  && git -C backend commit -q -m "laundry assistant: save what owners ask (laundry_assistant_logs; LAUNDRY_ASSISTANT_LOG)" -- routes/laundry-assistant.js prisma/schema.prisma prisma/migrations/20261026120000_laundry_assistant_logs \
  && git -C backend log --oneline -1; } || echo "server git commit skipped (the deploy itself is done)"
echo "LIVE: Ask questions are saved in laundry_assistant_logs"
REMOTE
