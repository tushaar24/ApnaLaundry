#!/bin/sh
# MSG91 as a backend OTP provider (inert on deploy: LAUNDRY_OTP_PROVIDER stays
# minimoth, LAUNDRY_OTP_APP_METHOD unset = Firebase for app 1.0.14+).
# Adds: msg91 provider, secret-gated /api/laundry/auth/test-msg91/{send,verify},
# and request-otp {firebaseOk:true} → {method:'firebase'|'sms'} for app 1.0.14+.
# The MSG91 creds (LAUNDRY_MSG91_AUTH_KEY / _TEMPLATE_ID / _SENDER_ID) go into
# backend/.env separately over ssh stdin — never in this repo.
# Backup → patch (dry-run first) → syntax check → server commit (otp + env.js;
# laundry-auth.js has older uncommitted live edits, so it stays uncommitted) →
# rebuild → checks.
#   sh docs/deploy/2026-10-19-msg91-deploy.sh
set -e
HERE=$(cd "$(dirname "$0")" && pwd)
ssh courses-aws 'set -e
cd ~/shweta-makeup-monorepo/backend
B=~/backend-filebak/$(date -u +%Y%m%d-%H%M%S)-msg91; mkdir -p $B
cp -a services/laundry-otp.js routes/laundry-auth.js config/env.js .env $B/; echo "backup: $B"
cat > /tmp/msg91.patch
patch -p1 --dry-run < /tmp/msg91.patch && patch -p1 < /tmp/msg91.patch && rm -f /tmp/msg91.patch
for f in services/laundry-otp.js routes/laundry-auth.js config/env.js; do docker run --rm -v $PWD:/x shweta-makeup-monorepo-backend node --check /x/$f; done; echo "syntax ok"
grep "^LAUNDRY_MSG91_\|^LAUNDRY_OTP_" .env | sed "s/=.*/=<set>/"
cd .. && git add backend/services/laundry-otp.js backend/config/env.js && git commit -q -m "laundry: MSG91 OTP provider + test-msg91 routes; app login method switch (LAUNDRY_OTP_APP_METHOD)" && git log --oneline -1
docker compose -f docker-compose.prod.yml up -d --build backend > /tmp/backend-build.log 2>&1 || { tail -20 /tmp/backend-build.log; exit 1; }
sleep 8; docker ps --filter name=backend-1 --format "{{.Names}} {{.Status}}"
docker exec shweta-makeup-monorepo-backend-1 node -e "
  const otp = require(\"/app/services/laundry-otp\");
  console.log(\"msg91 configured:\", otp.msg91Test.configured());
  console.log(\"real number provider:\", (otp.providerFor(\"9876543210\") || {}).name, \"· review number:\", otp.providerFor(\"1000000028\").name);
"
H="content-type: application/json"; U=http://localhost:5001/api/laundry/auth/request-otp
echo "1.0.14 app (firebaseOk) →"; docker exec shweta-makeup-monorepo-backend-1 sh -c "wget -qO- --header \"$H\" --post-data \"{\\\"phone\\\":\\\"9800000001\\\",\\\"firebaseOk\\\":true}\" $U || true"; echo
echo "test-msg91 without secret →"; docker exec shweta-makeup-monorepo-backend-1 sh -c "wget -S -qO- --header \"$H\" --post-data \"{}\" http://localhost:5001/api/laundry/auth/test-msg91/send 2>&1 | grep \"HTTP/\" || true"
' < "$HERE/2026-10-19-backend-msg91-otp.patch"
