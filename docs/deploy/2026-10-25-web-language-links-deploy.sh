#!/usr/bin/env bash
# Language links on mylaundry.work: /open/<lang>[/<screen>] and ?lang=<lang> open the app in that
# language (mylaundry://<screen>?lang=…), and the Play Store link carries referrer lang=… for a
# fresh install. Replaces ONLY these 3 files in the server's ~/apnalaundry-web (checked on
# 2026-10-25: the live copies were identical to the repo before this change), backs them up,
# then rebuilds the laundry service. See docs/language-links.md.
#
#   bash docs/deploy/2026-10-25-web-language-links-deploy.sh
set -euo pipefail
cd "$(dirname "$0")/../../web"
STAMP=$(date -u +%Y%m%d-%H%M%S)

ssh -o BatchMode=yes courses-aws "cd ~/apnalaundry-web && mkdir -p ~/apnalaundry-web-filebak/$STAMP && tar cf ~/apnalaundry-web-filebak/$STAMP/files.tar src/core/openApp.ts 'src/app/open/[[...screen]]/route.ts' 'src/app/pay/[[...code]]/route.ts' && echo backup ~/apnalaundry-web-filebak/$STAMP/files.tar"
# scp chokes on the [[...x]] folders: pipe each file through ssh instead.
for f in "src/core/openApp.ts" "src/app/open/[[...screen]]/route.ts" "src/app/pay/[[...code]]/route.ts"; do
  ssh -o BatchMode=yes courses-aws "cat > \"apnalaundry-web/$f\"" < "$f"
done
ssh -o BatchMode=yes courses-aws 'cd ~/shweta-makeup-monorepo && docker compose -f docker-compose.prod.yml up -d --build laundry 2>&1 | tail -3 && sleep 8 \
  && curl -s -o /dev/null -w "mylaundry.work: %{http_code}\n" https://mylaundry.work/ \
  && curl -s -A "Mozilla/5.0 (Linux; Android 14)" https://mylaundry.work/open/hi | grep -o "intent://[^\"]*" | head -1'
