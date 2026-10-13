#!/usr/bin/env bash
# Local multi-shop test: old backend + old schema -> seed like an old app ->
# migrate -> new backend -> backward-compat + multi-shop checks.
set -euo pipefail
M=/Users/tusharpal/.claude/jobs/eee08c3a/tmp/mshop
export DATABASE_URL=postgresql://postgres:pg@localhost:55432/laundry
export DIRECT_URL=$DATABASE_URL
export NODE_ENV=development
for v in RAZORPAY_KEY_ID RAZORPAY_KEY_SECRET RAZORPAY_WEBHOOK_SECRET EMAIL_USER EMAIL_PASS MIXPANEL_TOKEN \
  META_PIXEL_ID META_CAPI_ACCESS_TOKEN META_MARKETING_TOKEN META_AD_ACCOUNT_ID WHATSAPP_VERIFY_TOKEN \
  WHATSAPP_APP_SECRET WHATSAPP_ACCESS_TOKEN WHATSAPP_PHONE_NUMBER_ID BOLNA_API_KEY BOLNA_AGENT_ID BOLNA_TOOL_SECRET; do
  export "$v=dummy-local"
done

wait_port() { for i in $(seq 1 60); do curl -s -o /dev/null "http://localhost:$1/" && return 0; sleep 0.5; done; echo "server on $1 did not start"; return 1; }

docker exec mshop-pg psql -q -U postgres -c 'DROP DATABASE IF EXISTS laundry' -c 'CREATE DATABASE laundry'

echo "== old schema"
cd "$M/old" && npx prisma migrate deploy 2>&1 | grep -E 'migrations? (found|applied)|All migrations|Error' || true
PORT=5101 node server.js > "$M/old-server.log" 2>&1 & OLD=$!
wait_port 5101
echo "== seed as an old app (old backend)"
node "$M/test-multishop.js" seed http://localhost:5101
kill $OLD; wait $OLD 2>/dev/null || true

echo "== multi-shop migration"
cd "$M/new" && npx prisma migrate deploy 2>&1 | grep -E 'migrations? (found|applied)|laundry_multi_shop|Error' || true
echo "== schema drift (empty = migration matches schema.prisma)"
npx prisma migrate diff --from-url "$DATABASE_URL" --to-schema-datamodel prisma/schema.prisma --script | grep -v '^-- This is an empty migration' || true
PORT=5102 node server.js > "$M/new-server.log" 2>&1 & NEW=$!
wait_port 5102
echo "== checks (new backend)"
set +e
node "$M/test-multishop.js" check http://localhost:5102; RC=$?
kill $NEW; wait $NEW 2>/dev/null || true
exit $RC
