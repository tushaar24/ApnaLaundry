#!/usr/bin/env bash
# The Invoice bill design on mylaundry.work (website + the public /b/<token> bill page the
# WhatsApp "Download PDF" link opens), so a shop on Invoice gets the same bill there as in the
# Android picture. Replaces ONLY these 5 files in the server's ~/apnalaundry-web (checked on
# 2026-10-25: the live copies were identical to the repo before this change), backs them up,
# then rebuilds the laundry service.
#
#   bash docs/deploy/2026-10-25-web-invoice-bill-deploy.sh
set -euo pipefail
cd "$(dirname "$0")/../../web"
FILES="src/core/appdate.ts src/domain/billDetails.ts src/domain/billReceipt.ts src/domain/models.ts src/ui/billRender.ts"
STAMP=$(date -u +%Y%m%d-%H%M%S)

ssh -o BatchMode=yes courses-aws "cd ~/apnalaundry-web && mkdir -p ~/apnalaundry-web-filebak/$STAMP && tar cf ~/apnalaundry-web-filebak/$STAMP/files.tar $FILES && echo backup ~/apnalaundry-web-filebak/$STAMP/files.tar"
for f in $FILES; do scp -q -o BatchMode=yes "$f" "courses-aws:apnalaundry-web/$f"; done
ssh -o BatchMode=yes courses-aws 'cd ~/shweta-makeup-monorepo && docker compose -f docker-compose.prod.yml up -d --build laundry 2>&1 | tail -3 && sleep 8 && curl -s -o /dev/null -w "mylaundry.work: %{http_code}\n" https://mylaundry.work/'
