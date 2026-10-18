#!/bin/sh
# Owner, 2026-10-18: every NEW account → hard_trial (100%); on app 1.0.13+ the offer is
# Yearly only (₹2 today, ₹4,999/yr after LAUNDRY_PAYWALL_YEARLY_TRIAL_DAYS, default 7).
# Accounts created before the switch keep their group and offer. Backup → patch (dry-run
# first) → syntax check → switch time = now → rebuild backend → check the running code.
#   sh docs/deploy/2026-10-18-hard-yearly-deploy.sh
set -e
HERE=$(cd "$(dirname "$0")" && pwd)
ssh courses-aws 'set -e
cd ~/shweta-makeup-monorepo/backend
B=~/backend-filebak/$(date -u +%Y%m%d-%H%M%S)-hard-yearly; mkdir -p $B
cp -a services/laundry-paywall-test.js routes/laundry-billing.js .env $B/; echo "backup: $B"
cat > /tmp/hard-yearly.patch
patch -p1 --dry-run < /tmp/hard-yearly.patch && patch -p1 < /tmp/hard-yearly.patch && rm -f /tmp/hard-yearly.patch
for f in services/laundry-paywall-test.js routes/laundry-billing.js; do docker run --rm -v $PWD:/x shweta-makeup-monorepo-backend node --check /x/$f; done; echo "syntax ok"
NOW=$(date -u +%Y-%m-%dT%H:%M:%SZ)
grep -q "^LAUNDRY_PAYWALL_HARD_ALL_FROM=" .env || printf "\nLAUNDRY_PAYWALL_HARD_ALL_FROM=%s\nLAUNDRY_PAYWALL_YEARLY_TRIAL_DAYS=7\n" "$NOW" >> .env
grep "^LAUNDRY_PAYWALL_HARD_ALL_FROM\|^LAUNDRY_PAYWALL_YEARLY_TRIAL_DAYS\|^LAUNDRY_RAZORPAY_PLAN_ANNUAL" .env | sed "s/plan_.*/plan_<set>/"
cd .. && git add backend/services/laundry-paywall-test.js backend/routes/laundry-billing.js && git commit -q -m "laundry: hard_trial for every new account; Yearly ₹4,999 only on app 1.0.13+" && git log --oneline -1
docker compose -f docker-compose.prod.yml up -d --build backend > /tmp/backend-build.log 2>&1 || { tail -20 /tmp/backend-build.log; exit 1; }
sleep 8; docker ps --filter name=backend-1 --format "{{.Names}} {{.Status}}"
docker exec shweta-makeup-monorepo-backend-1 node -e "
  const pt = require(\"/app/services/laundry-paywall-test\");
  const now = new Date().toISOString(), old = \"2026-10-15T10:00:00Z\";
  const u = (v, at) => ({ id: \"chk-\" + v + at, phone: \"9800000001\", signupPlatform: \"android\", signupAppVersion: v, createdAt: at });
  const show = (label, user) => { const o = pt.offer(user); console.log(label, \"→\", o ? o.group + \" · \" + o.plan + \" ₹\" + o.amount / 100 + \" · trial \" + o.trialDays + \"d\" : \"not in test\"); };
  show(\"new account, app 1.0.13\", u(13, now));
  show(\"new account, app 1.0.12\", u(12, now));
  show(\"account from 15 Oct (keeps its group)\", u(12, old));
"' < "$HERE/2026-10-18-backend-hard-trial-all-yearly.patch"
