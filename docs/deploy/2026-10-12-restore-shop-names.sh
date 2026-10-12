#!/bin/sh
# Runs restore-shop-names.js inside the prod backend container, then removes it.
#   sh docs/deploy/2026-10-12-restore-shop-names.sh           # dry run (writes nothing)
#   APPLY=1 sh docs/deploy/2026-10-12-restore-shop-names.sh   # apply
set -e
HERE=$(cd "$(dirname "$0")" && pwd)
C=shweta-makeup-monorepo-backend-1
scp -q "$HERE/2026-10-12-restore-shop-names.js" courses-aws:/tmp/restore-shop-names.js
ssh courses-aws "
  grep -q 'existing.updatedAt > updatedAt' ~/shweta-makeup-monorepo/backend/routes/laundry.js \
    || { echo 'Server shop merge rule changed - check routes/laundry.js before running.'; exit 1; }
  docker cp /tmp/restore-shop-names.js $C:/app/restore-shop-names.js &&
  docker exec -e APPLY=${APPLY:-0} -w /app $C node restore-shop-names.js;
  docker exec -u root $C rm -f /app/restore-shop-names.js /app/la_q.js; rm -f /tmp/restore-shop-names.js"
