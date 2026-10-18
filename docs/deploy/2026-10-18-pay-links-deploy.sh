#!/bin/sh
# Adds /pay/<code> (the WhatsApp "Plan shuru karein" button → the app's Plans screen,
# taps logged) and moves /open/<screen> onto the shared core/openApp.ts. Copies only
# these 3 files into the live site copy, tags a rollback image, builds (next build
# typechecks), switches, and checks the links.
#   sh docs/deploy/2026-10-18-pay-links-deploy.sh
set -e
cd "$(dirname "$0")/../.."
ssh courses-aws 'mkdir -p "$HOME/apnalaundry-web/src/app/pay/[[...code]]"'
cat web/src/core/openApp.ts | ssh courses-aws 'cat > "$HOME/apnalaundry-web/src/core/openApp.ts"'
cat "web/src/app/open/[[...screen]]/route.ts" | ssh courses-aws 'cat > "$HOME/apnalaundry-web/src/app/open/[[...screen]]/route.ts"'
cat "web/src/app/pay/[[...code]]/route.ts" | ssh courses-aws 'cat > "$HOME/apnalaundry-web/src/app/pay/[[...code]]/route.ts"'
ssh courses-aws 'set -e
IMG=shweta-makeup-monorepo-laundry; TAG="$IMG:rollback-$(date -u +%Y%m%d-%H%M%S)"; docker tag "$IMG" "$TAG"; echo "rollback tag: $TAG"
cd ~/shweta-makeup-monorepo && docker compose -f docker-compose.prod.yml build laundry > /tmp/laundry-build.log 2>&1 || { tail -30 /tmp/laundry-build.log; exit 1; }
grep -E "Compiled|/open|/pay" /tmp/laundry-build.log | tail -4
docker compose -f docker-compose.prod.yml up -d --no-deps laundry 2>&1 | tail -1; sleep 6'
A="Mozilla/5.0 (Linux; Android 14; Pixel 7) AppleWebKit/537.36 Chrome/129.0 Mobile Safari/537.36"
for p in /open/subscription /open/new-order /pay/test123 /pay; do
  printf "%-20s → %s\n" "$p" "$(curl -s -A "$A" "https://mylaundry.work$p" | grep -o 'intent://[a-z-]*#' | head -1)"
done
for p in / /login; do printf "%-20s → %s\n" "$p" "$(curl -s -o /dev/null -w '%{http_code}' "https://mylaundry.work$p")"; done
