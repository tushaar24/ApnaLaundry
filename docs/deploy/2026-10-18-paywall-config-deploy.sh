#!/bin/sh
# Server-set paywall variants (LAUNDRY_PAYWALL_CONFIG) — replaces today's
# LAUNDRY_PAYWALL_HARD_ALL_FROM / _YEARLY_TRIAL_DAYS with one JSON setting.
# Current variant (owner, 2026-10-18): hard_trial_yearly_plus_monthly_4999_499 for
# every account created from 07:58:42Z (the same switch time). Backup → patch
# (dry-run first) → syntax check → .env → server commit → rebuild → checks.
#   sh docs/deploy/2026-10-18-paywall-config-deploy.sh
set -e
HERE=$(cd "$(dirname "$0")" && pwd)
ssh courses-aws 'set -e
cd ~/shweta-makeup-monorepo/backend
B=~/backend-filebak/$(date -u +%Y%m%d-%H%M%S)-paywall-config; mkdir -p $B
cp -a services/laundry-paywall-test.js routes/laundry-billing.js .env $B/; echo "backup: $B"
cat > /tmp/paywall-config.patch
patch -p1 --dry-run < /tmp/paywall-config.patch && patch -p1 < /tmp/paywall-config.patch && rm -f /tmp/paywall-config.patch
for f in services/laundry-paywall-test.js routes/laundry-billing.js; do docker run --rm -v $PWD:/x shweta-makeup-monorepo-backend node --check /x/$f; done; echo "syntax ok"
sed -i "/^LAUNDRY_PAYWALL_HARD_ALL_FROM=/d; /^LAUNDRY_PAYWALL_YEARLY_TRIAL_DAYS=/d" .env
grep -q "^LAUNDRY_PAYWALL_CONFIG=" .env || cat >> .env <<'"'"'CFG'"'"'
LAUNDRY_PAYWALL_CONFIG={"splits":[{"from":"2026-10-18T07:58:42Z","split":{"hard_trial_yearly_plus_monthly_4999_499":100}}],"variants":{"hard_trial_yearly_plus_monthly_4999_499":{"gate":"login","plans":["annual","monthly"],"default":"annual","trialDays":1,"freeOrders":0,"prices":{"annual":4999,"monthly":499}}}}
CFG
grep "^LAUNDRY_PAYWALL_" .env | sed "s/VIDEO_URL\(.*\)=.*/VIDEO_URL\1=<set>/"
cd .. && git add backend/services/laundry-paywall-test.js backend/routes/laundry-billing.js && git commit -q -m "laundry: server-set paywall variants (LAUNDRY_PAYWALL_CONFIG); hard_trial_yearly_plus_monthly_4999_499" && git log --oneline -1
docker compose -f docker-compose.prod.yml up -d --build backend > /tmp/backend-build.log 2>&1 || { tail -20 /tmp/backend-build.log; exit 1; }
sleep 8; docker ps --filter name=backend-1 --format "{{.Names}} {{.Status}}"
docker logs --since 1m shweta-makeup-monorepo-backend-1 2>&1 | grep "\[paywall\]" || echo "config parsed (no [paywall] errors)"
docker exec shweta-makeup-monorepo-backend-1 node -e "
  const pt = require(\"/app/services/laundry-paywall-test\");
  const now = new Date().toISOString();
  const u = (v, at) => ({ id: \"chk-\" + v + at, phone: \"9800000001\", signupPlatform: \"android\", signupAppVersion: v, createdAt: at });
  const show = (label, user, client) => { const o = pt.offer(user, client); console.log(label, \"→\", o ? o.group + \" · gate \" + o.gate + \" · plans \" + o.plans.join(\"+\") + \" (default \" + o.defaultPlan + \") · \" + Object.entries(o.prices).map(([k, v]) => k + \" ₹\" + v / 100).join(\", \") + \" · \" + (o.trial ? o.trialDays + \"-day trial\" : \"upfront\") + \" · app draws it: \" + o.drawn : \"not in test\"); };
  show(\"new account, app 1.0.13      \", u(13, now), 13);
  show(\"same account, opened on 1.0.12\", u(13, now), 12);
  show(\"new account, app 1.0.12      \", u(12, now), 12);
  show(\"account from 15 Oct          \", u(12, \"2026-10-15T10:00:00Z\"), 12);
"' < "$HERE/2026-10-18-backend-paywall-config.patch"
