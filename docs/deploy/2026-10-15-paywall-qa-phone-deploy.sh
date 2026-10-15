#!/bin/sh
# Pins the QA phone 1000000028 to the paywall test's hard_trial group on the live
# backend: backup → patch (dry-run first) → syntax check → server commit →
# rebuild the backend → check the running code. See 2026-10-15-paywall-qa-phone.md.
#   sh docs/deploy/2026-10-15-paywall-qa-phone-deploy.sh
set -e
HERE=$(cd "$(dirname "$0")" && pwd)
ssh courses-aws 'set -e
cd ~/shweta-makeup-monorepo/backend
B=~/backend-filebak/$(date -u +%Y%m%d-%H%M%S)-paywall-qa-phone; mkdir -p $B
cp -a services/laundry-paywall-test.js $B/; echo "backup: $B"
cat > /tmp/paywall-qa.patch
patch -p1 --dry-run < /tmp/paywall-qa.patch && patch -p1 < /tmp/paywall-qa.patch && rm -f /tmp/paywall-qa.patch
docker run --rm -v $PWD:/x shweta-makeup-monorepo-backend node --check /x/services/laundry-paywall-test.js && echo "syntax ok"
cd .. && git add backend/services/laundry-paywall-test.js \
  && git commit -q -m "laundry: paywall test — QA phone 1000000028 pinned to hard_trial" && git log --oneline -1
docker compose -f docker-compose.prod.yml up -d --build backend
sleep 5
docker exec shweta-makeup-monorepo-backend-1 node -e "
  const pt = require(\"/app/services/laundry-paywall-test\");
  console.log(\"1000000028 →\", pt.groupFor({ id: \"x\", phone: \"1000000028\", createdAt: \"2026-01-01\" }));
  console.log(\"old account →\", pt.groupFor({ id: \"y\", phone: \"9800000000\", signupPlatform: \"android\", signupAppVersion: 11, createdAt: \"2026-01-01\" }));
"' < "$HERE/2026-10-15-backend-paywall-qa-phone.patch"
