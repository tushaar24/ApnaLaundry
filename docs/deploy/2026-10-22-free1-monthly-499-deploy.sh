#!/usr/bin/env bash
# Every NEW account from now: free1_monthly_499 — 1 free order, then New order asks
# for ₹499/month (Monthly only, paid upfront, no ₹2 trial). Adds a split to
# LAUNDRY_PAYWALL_CONFIG from the moment this runs; existing accounts keep their variant.
# Note: docs/deploy/2026-10-22-free1-monthly-499.md
#
#   bash docs/deploy/2026-10-22-free1-monthly-499-deploy.sh            # apply
#   DRY_RUN=1 bash docs/deploy/2026-10-22-free1-monthly-499-deploy.sh  # build + check only, changes nothing
set -euo pipefail

ssh -o BatchMode=yes courses-aws "DRY_RUN=${DRY_RUN:-0} bash -s" <<'REMOTE'
set -euo pipefail
cd ~/shweta-makeup-monorepo
ENV=backend/.env
C=shweta-makeup-monorepo-backend-1
FROM=$(date -u +%Y-%m-%dT%H:%M:%SZ)
OLD=$(grep '^LAUNDRY_PAYWALL_CONFIG=' "$ENV")

# New config line, built with the container's node from the live one.
NEW=$(docker exec -e OLD="$OLD" -e FROM="$FROM" "$C" node -e '
const c = JSON.parse(process.env.OLD.slice("LAUNDRY_PAYWALL_CONFIG=".length));
if (c.variants && c.variants.free1_monthly_499) { console.error("free1_monthly_499 is already in the config"); process.exit(1); }
c.splits.push({ from: process.env.FROM, split: { free1_monthly_499: 100 } });
c.variants.free1_monthly_499 = { gate: "free_orders", plans: ["monthly"], default: "monthly", trialDays: 0, freeOrders: 1, prices: { monthly: 499 } };
process.stdout.write("LAUNDRY_PAYWALL_CONFIG=" + JSON.stringify(c));')
printf '%s\n' "$NEW" > /tmp/cfg-line.txt

# Same .env with only that line swapped.
awk -v f=/tmp/cfg-line.txt 'BEGIN { getline nl < f } /^LAUNDRY_PAYWALL_CONFIG=/ { print nl; next } { print }' "$ENV" > /tmp/env.new
[ "$(grep -c '^LAUNDRY_PAYWALL_CONFIG=' /tmp/env.new)" = 1 ] || { echo "config line count is wrong"; exit 1; }
[ "$(wc -l < /tmp/env.new)" = "$(wc -l < "$ENV")" ] || { echo "line count changed"; exit 1; }
diff <(grep -v '^LAUNDRY_PAYWALL_CONFIG=' "$ENV") <(grep -v '^LAUNDRY_PAYWALL_CONFIG=' /tmp/env.new) > /dev/null || { echo "other lines differ"; exit 1; }
echo "new: $NEW"

# Check the new value with the live service code before touching anything.
docker cp /tmp/cfg-line.txt "$C":/tmp/cfg-line.txt
docker exec "$C" node -e '
process.env.LAUNDRY_PAYWALL_CONFIG = require("fs").readFileSync("/tmp/cfg-line.txt", "utf8").trim().replace(/^LAUNDRY_PAYWALL_CONFIG=/, "");
let bad = 0; const ce = console.error; console.error = (...a) => { bad++; ce(...a); };
const pt = require("/app/services/laundry-paywall-test");
const u = (v, ago) => ({ id: "check-" + v + "-" + ago, phone: "9999999999", signupPlatform: "android", signupAppVersion: v, createdAt: new Date(Date.now() - ago) });
const o = pt.offer(u(14, -60e3), 14);
const ok = o && o.group === "free1_monthly_499" && o.gate === "free_orders" && o.freeOrders === 1 && !o.trial
  && o.plans.join() === "monthly" && o.prices.monthly === 49900
  && pt.groupFor(u(14, 86400e3)) === "hard_trial_yearly_plus_monthly_4999_499" && !bad;
console.log(ok ? "check: OK (new account → free1_monthly_499, ₹499 monthly, 1 free order; yesterday’s account unchanged)" : "check: FAILED " + JSON.stringify(o));
process.exit(ok ? 0 : 1);'
docker exec -u 0 "$C" rm -f /tmp/cfg-line.txt

if [ "$DRY_RUN" = 1 ]; then rm -f /tmp/env.new /tmp/cfg-line.txt; echo "DRY RUN — nothing changed"; exit 0; fi

TS=$(date -u +%Y%m%d-%H%M%S)
BAK=~/backend-filebak/$TS-free1-monthly-499
mkdir -p "$BAK" && cp "$ENV" "$BAK/.env" && echo "backup: $BAK/.env"
cat /tmp/env.new > "$ENV"; rm -f /tmp/env.new /tmp/cfg-line.txt
docker compose -f docker-compose.prod.yml up -d --force-recreate backend
sleep 10
if docker logs --since 1m "$C" 2>&1 | grep '\[paywall\]'; then
  echo "backend rejected the config — rolling back"
  cat "$BAK/.env" > "$ENV"
  docker compose -f docker-compose.prod.yml up -d --force-recreate backend
  exit 1
fi
docker compose -f docker-compose.prod.yml up -d --force-recreate --no-deps laundry-dash
echo "LIVE from $FROM: new accounts → free1_monthly_499"
REMOTE
