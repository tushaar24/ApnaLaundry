#!/usr/bin/env bash
# Review number 1000000000 starts over on every login: the backend deletes the
# account and all its data at login and creates a fresh one (new user id, new
# token, isNewUser), so the owner replays the brand-new-shop journey each time.
# Adds services/laundry-user-wipe.js, a hook in loginPhone (routes/laundry-auth.js),
# env key LAUNDRY_ALWAYS_NEW_PHONES=1000000000, then rebuilds the backend.
# Note: docs/deploy/2026-10-28-always-new-phones.md
#
#   bash docs/deploy/2026-10-28-always-new-phones-deploy.sh            # apply
#   DRY_RUN=1 bash docs/deploy/2026-10-28-always-new-phones-deploy.sh  # patch --dry-run only, changes nothing
set -euo pipefail
cd "$(dirname "$0")/../.."
scp -q -o BatchMode=yes docs/deploy/2026-10-28-backend-always-new-phones.patch courses-aws:/tmp/always-new.patch

ssh -o BatchMode=yes courses-aws "DRY_RUN=${DRY_RUN:-0} bash -s" <<'REMOTE'
set -euo pipefail
cd ~/shweta-makeup-monorepo/backend
patch -p1 --dry-run < /tmp/always-new.patch
if [ "$DRY_RUN" = 1 ]; then echo "dry run ok — nothing changed"; exit 0; fi

B=~/backend-filebak/$(date -u +%Y%m%d-%H%M%S)-always-new
mkdir -p "$B"; cp routes/laundry-auth.js config/env.js .env "$B"/
patch -p1 < /tmp/always-new.patch
if ! grep -q '^LAUNDRY_ALWAYS_NEW_PHONES=' .env; then
  printf '\n# Review numbers whose account is deleted + recreated on every login (owner replays the new-shop journey)\nLAUNDRY_ALWAYS_NEW_PHONES=1000000000\n' >> .env
fi
grep -n '^LAUNDRY_ALWAYS_NEW_PHONES=' .env
git add -A routes/laundry-auth.js config/env.js services/laundry-user-wipe.js
git commit -q -m "laundry auth: always-new review numbers (LAUNDRY_ALWAYS_NEW_PHONES) — account wiped + recreated at login" || true

cd ~/shweta-makeup-monorepo
docker compose -f docker-compose.prod.yml up -d --build backend 2>&1 | tail -2
sleep 8
docker ps --format '{{.Names}} {{.Status}}' | grep backend-1
docker logs --since 40s shweta-makeup-monorepo-backend-1 2>&1 | grep -iE 'error|listen' | tail -5 || true
# The service loads and sees the number.
docker exec shweta-makeup-monorepo-backend-1 node -e '
const w = require("/app/services/laundry-user-wipe");
console.log("always-new phones:", w.alwaysNewPhones(), "1000000000 ->", w.isAlwaysNewPhone("1000000000"));
if (!w.isAlwaysNewPhone("1000000000")) process.exit(1);'
echo "backup: $B"
REMOTE
