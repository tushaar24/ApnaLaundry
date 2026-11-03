#!/usr/bin/env bash
# Owner, 3 Nov 2026: restrict POST /api/laundry/offers/grant to CleverTap's published webhook IPs
# (34.249.27.35, 34.250.139.131, 52.209.30.153, 54.72.72.185, 3.251.214.224/27) on top of the key.
# Copies routes/laundry-offers.js, sets LAUNDRY_OFFER_WEBHOOK_IPS, rebuilds the backend, checks that a
# call from the server itself is refused (403) — CleverTap's own calls are the positive test.
#   bash docs/deploy/2026-11-03-offer-webhook-ip-allowlist-deploy.sh
set -euo pipefail
HERE=$(cd "$(dirname "$0")/../.." && pwd)
scp -q "$HERE/courses/backend/routes/laundry-offers.js" courses-aws:/tmp/laundry-offers.js
ssh -o BatchMode=yes courses-aws "IPS='${IPS:-34.249.27.35,34.250.139.131,52.209.30.153,54.72.72.185,3.251.214.224/27}' bash -s" <<'REMOTE'
set -euo pipefail
cd ~/shweta-makeup-monorepo/backend
C=shweta-makeup-monorepo-backend-1
B=~/backend-filebak/$(date -u +%Y%m%d-%H%M%S)-offer-webhook-ips; mkdir -p "$B"; cp .env "$B/.env"; cp routes/laundry-offers.js "$B/"
cp /tmp/laundry-offers.js routes/laundry-offers.js; rm -f /tmp/laundry-offers.js
grep -q "^LAUNDRY_OFFER_WEBHOOK_IPS=" .env && sed -i "s#^LAUNDRY_OFFER_WEBHOOK_IPS=.*#LAUNDRY_OFFER_WEBHOOK_IPS=$IPS#" .env || printf "LAUNDRY_OFFER_WEBHOOK_IPS=%s\n" "$IPS" >> .env
grep "^LAUNDRY_OFFER_WEBHOOK_IPS=" .env
KEY=$(grep -o "^LAUNDRY_OFFER_WEBHOOK_KEY=.*" .env | cut -d= -f2-)
cd ~/shweta-makeup-monorepo
docker compose -f docker-compose.prod.yml up -d --build backend 2>&1 | tail -1
sleep 12; docker ps --format '{{.Names}} {{.Status}}' | grep backend-1
echo "--- from this server (not a CleverTap IP), right key → expect 403:"
curl -s -o /dev/null -w "HTTP %{http_code}\n" -X POST "https://shwetamakeover.online/api/laundry/offers/grant?offer=yearly799&key=$KEY" -H "Content-Type: application/json" -d '{"profiles":[]}'
docker logs --since 1m "$C" 2>&1 | grep -c "non-allow-listed IP" | sed 's/^/refusals logged: /'
echo "backup: $B"
REMOTE
