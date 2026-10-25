#!/usr/bin/env bash
# Assistant backend for app 1.0.16 in Indian languages (docs/deploy/2026-10-25-assistant.md):
#   POST /api/laundry/assistant/chat       takes an optional tool_choice ("required" = the app's
#                                          retry when a reply said "tap Save" but made no card)
#   POST /api/laundry/assistant/transcribe takes optional language (ta-IN…) and mode (codemix…)
# Without them, everything is exactly as before (older 1.0.16 builds, other apps).
#
#   DRY_RUN=1 bash docs/deploy/2026-10-25-assistant-languages-deploy.sh   # checks, changes nothing
#   bash docs/deploy/2026-10-25-assistant-languages-deploy.sh             # applies + rebuilds the backend
set -euo pipefail
cd "$(dirname "$0")"
scp -q -o BatchMode=yes 2026-10-25-backend-assistant-languages.patch courses-aws:/tmp/laundry-assistant-2.patch

ssh -o BatchMode=yes courses-aws "DRY_RUN=${DRY_RUN:-0} bash -s" <<'REMOTE'
set -euo pipefail
cd ~/shweta-makeup-monorepo
FILE=backend/routes/laundry-assistant.js
grep -q 'TOOL_CHOICES' "$FILE" && { echo "already patched"; exit 0; }
patch -p1 --dry-run < /tmp/laundry-assistant-2.patch
if [ "$DRY_RUN" = "1" ]; then echo "dry run: patch applies cleanly, nothing changed"; exit 0; fi
patch -p1 < /tmp/laundry-assistant-2.patch
git -C backend add routes/laundry-assistant.js
git -C backend commit -q -m "laundry assistant: optional tool_choice and speech language/mode from the app (1.0.16 languages)"
git -C backend log --oneline -1
docker compose -f docker-compose.prod.yml up -d --build backend
sleep 8
docker compose -f docker-compose.prod.yml ps backend --format '{{.Status}}'
curl -s -o /dev/null -w "public config without token (expect 401): %{http_code}\n" https://shwetamakeover.online/api/laundry/assistant/config
REMOTE
