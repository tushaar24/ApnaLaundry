#!/usr/bin/env bash
# All-screen deep links on mylaundry.work (app 1.0.18): /open/<screen>[/<id>] with ?plan= ?filter= ?cust= ?period=
# passed through to mylaundry://… — rates, add-service, settings, bill-design, bill-details, add-shop, demo,
# assistant, order/<id>, bill/<id>, customer/<id>, combined-bill/<id>, plus the five since 1.0.8. Replaces ONLY
# the two files in the server's ~/apnalaundry-web, backs them up, rebuilds the laundry service.
#   bash docs/deploy/2026-11-03-web-deep-links-all-screens-deploy.sh
set -euo pipefail
cd "$(dirname "$0")/../../web"
STAMP=$(date -u +%Y%m%d-%H%M%S)
ssh -o BatchMode=yes courses-aws "cd ~/apnalaundry-web && mkdir -p ~/apnalaundry-web-filebak/$STAMP && tar cf ~/apnalaundry-web-filebak/$STAMP/files.tar src/core/openApp.ts 'src/app/open/[[...screen]]/route.ts' && echo backup ~/apnalaundry-web-filebak/$STAMP/files.tar"
for f in "src/core/openApp.ts" "src/app/open/[[...screen]]/route.ts"; do
  ssh -o BatchMode=yes courses-aws "cat > \"apnalaundry-web/$f\"" < "$f"
done
ssh -o BatchMode=yes courses-aws 'cd ~/shweta-makeup-monorepo && docker compose -f docker-compose.prod.yml up -d --build laundry 2>&1 | tail -2 && sleep 8 \
  && curl -s -o /dev/null -w "mylaundry.work: %{http_code}\n" https://mylaundry.work/ \
  && for p in "open/order/1012" "open/subscription?plan=annual" "open/earnings?period=month" "open/hi/bill-details" "open/demo"; do printf "%-34s " "$p"; curl -s -A "Mozilla/5.0 (Linux; Android 14)" "https://mylaundry.work/$p" | grep -o "intent://[^#]*" | head -1; done'
