#!/usr/bin/env bash
# shwetamakeover.online's TLS certificate expired 2026-10-21 16:07 UTC (21:37 IST): certbot had
# no renewal entry for it (only admin.shwetamakeover.online, mylaundry.work, admin.mylaundry.work),
# so the app's API calls failed from then on. This re-issues it the same way as the others
# (webroot /var/www/certbot, already served by nginx for both names), which also creates the
# missing renewal entry so certbot.timer renews it from now on, then reloads nginx.
#
#   bash docs/deploy/2026-10-22-shwetamakeover-cert-fix.sh
set -euo pipefail

ssh -o BatchMode=yes courses-aws 'bash -s' <<'REMOTE'
set -euo pipefail
TS=$(date -u +%Y%m%d-%H%M%S)
BAK=/root/certbak-$TS-shwetamakeover
sudo -n mkdir -p "$BAK"
# certbot refuses to reuse a live/ dir it has no renewal entry for — move the expired one aside.
sudo -n mv /etc/letsencrypt/live/shwetamakeover.online "$BAK/live"
sudo -n mv /etc/letsencrypt/archive/shwetamakeover.online "$BAK/archive"
if ! sudo -n certbot certonly --webroot -w /var/www/certbot \
    -d shwetamakeover.online -d www.shwetamakeover.online \
    --cert-name shwetamakeover.online --non-interactive --agree-tos; then
  echo "issuance failed — restoring the old (expired) files"
  sudo -n mv "$BAK/live" /etc/letsencrypt/live/shwetamakeover.online
  sudo -n mv "$BAK/archive" /etc/letsencrypt/archive/shwetamakeover.online
  exit 1
fi
docker exec shweta-makeup-monorepo-web-1 nginx -t
docker exec shweta-makeup-monorepo-web-1 nginx -s reload
sudo -n test -f /etc/letsencrypt/renewal/shwetamakeover.online.conf && echo "renewal entry: present"
echo "backup of the expired files: $BAK"
echo "served now: $(echo | openssl s_client -connect shwetamakeover.online:443 -servername shwetamakeover.online 2>/dev/null | openssl x509 -noout -enddate)"
curl -s -o /dev/null -w "API over https: %{http_code} (401 = up, needs a token)\n" https://shwetamakeover.online/api/laundry/billing/status
REMOTE
