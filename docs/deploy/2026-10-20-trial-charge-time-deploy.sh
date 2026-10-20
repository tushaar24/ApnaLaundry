#!/bin/sh
# Trial charge time: every new ₹2 trial's first plan charge at 14:00 IST, never
# before the promised trial days, with Razorpay's pre-debit notice (24 h before
# the charge) at least 16 h after the ₹2 — so it never lands on the day of the ₹2
# or in the evening. All users (web + every app version); trials already running,
# upfront plans and renewals are untouched. Rollback: LAUNDRY_TRIAL_CHARGE_HOUR_IST=off
# in backend/.env + recreate the backend.
# Backup → patch (dry-run first) → syntax check → server commit (env.js only;
# laundry-razorpay.js has older uncommitted live edits) → rebuild → checks.
#   sh docs/deploy/2026-10-20-trial-charge-time-deploy.sh
set -e
HERE=$(cd "$(dirname "$0")" && pwd)
ssh courses-aws 'set -e
cd ~/shweta-makeup-monorepo/backend
B=~/backend-filebak/$(date -u +%Y%m%d-%H%M%S)-trial-charge-time; mkdir -p $B
cp -a services/laundry-razorpay.js config/env.js $B/; echo "backup: $B"
cat > /tmp/trial-charge-time.patch
patch -p1 --dry-run < /tmp/trial-charge-time.patch && patch -p1 < /tmp/trial-charge-time.patch && rm -f /tmp/trial-charge-time.patch
for f in services/laundry-razorpay.js config/env.js; do docker run --rm -v $PWD:/x shweta-makeup-monorepo-backend node --check /x/$f; done; echo "syntax ok"
grep "^LAUNDRY_TRIAL_" .env | sed "s/AMOUNT_PAISE=.*/AMOUNT_PAISE=<set>/" || echo "no LAUNDRY_TRIAL_* overrides: defaults 14:00 IST / 16 h"
cd .. && git add backend/config/env.js && git commit -q -m "laundry: trial charge time settings (LAUNDRY_TRIAL_CHARGE_HOUR_IST / _MIN_NOTICE_HOURS)" && git log --oneline -1
docker compose -f docker-compose.prod.yml up -d --build backend > /tmp/backend-build.log 2>&1 || { tail -20 /tmp/backend-build.log; exit 1; }
sleep 8; docker ps --filter name=backend-1 --format "{{.Names}} {{.Status}}"
docker exec shweta-makeup-monorepo-backend-1 node -e "
  const rzp = require(\"/app/services/laundry-razorpay\");
  const ist = (s) => new Date(s * 1000 + 5.5 * 3600e3).toISOString().slice(5, 16).replace(\"T\", \" \");
  const now = Date.now();
  for (const d of [1, 7]) { const s = rzp.trialStartAt(now, d); console.log(d + \"-day trial started now (\" + ist(now / 1000) + \" IST): notice \" + ist(s - 86400) + \" · charge \" + ist(s) + \" IST\"); }
"' < "$HERE/2026-10-20-backend-trial-charge-time.patch"
