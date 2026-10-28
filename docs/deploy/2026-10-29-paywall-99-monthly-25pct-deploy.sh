#!/usr/bin/env bash
# Paywall test (owner, 29 Oct 2026 01:30 IST): NEW Android accounts from now → 25% "first_order_monthly_99"
# (same first-order pop-up, monthly only, ₹99/month) / 75% "first_order_monthly_499" (unchanged). Existing
# accounts keep their variant (a new dated split only applies to accounts created after it).
# Creates the ₹99 monthly Razorpay plan on the laundry account if PLAN_ID is not given, appends the variant +
# split to LAUNDRY_PAYWALL_CONFIG, recreates backend + laundry-dash, verifies the split and the old accounts.
#   DRY_RUN=1 bash docs/deploy/2026-10-29-paywall-99-monthly-25pct-deploy.sh   # shows the new config, changes nothing
#   bash docs/deploy/2026-10-29-paywall-99-monthly-25pct-deploy.sh
set -euo pipefail
ssh -o BatchMode=yes courses-aws "DRY_RUN=${DRY_RUN:-0} PLAN_ID='${PLAN_ID:-}' PCT='${PCT:-25}' bash -s" <<'REMOTE'
set -euo pipefail
cd ~/shweta-makeup-monorepo/backend
C=shweta-makeup-monorepo-backend-1
if [ -z "$PLAN_ID" ]; then
  PLAN_ID=$(docker exec -e DRY="$DRY_RUN" "$C" node -e '
const auth = "Basic " + Buffer.from(process.env.LAUNDRY_RAZORPAY_KEY_ID + ":" + process.env.LAUNDRY_RAZORPAY_KEY_SECRET).toString("base64");
(async () => {
  const list = await (await fetch("https://api.razorpay.com/v1/plans?count=100", { headers: { Authorization: auth } })).json();
  const have = (list.items || []).find((p) => p.period === "monthly" && p.interval === 1 && p.item.amount === 9900);
  if (have) { console.log(have.id); return; }
  if (process.env.DRY === "1") { console.log("plan_WOULD_CREATE"); return; }
  const r = await fetch("https://api.razorpay.com/v1/plans", { method: "POST", headers: { Authorization: auth, "Content-Type": "application/json" },
    body: JSON.stringify({ period: "monthly", interval: 1, item: { name: "Monthly ₹99", amount: 9900, currency: "INR", description: "MyLaundry monthly plan, ₹99 test" } }) });
  const j = await r.json(); if (!r.ok) { console.error(JSON.stringify(j)); process.exit(1); } console.log(j.id);
})().catch((e) => { console.error(e.message); process.exit(1); });')
fi
FROM=$(date -u +%Y-%m-%dT%H:%M:%SZ)
NEW=$(PLAN="$PLAN_ID" FROM="$FROM" PCT="$PCT" python3 -c '
import json, os
plan, frm, pct = os.environ["PLAN"], os.environ["FROM"], int(os.environ["PCT"])
env = open(".env").read()
line = next(l for l in env.splitlines() if l.startswith("LAUNDRY_PAYWALL_CONFIG="))
cfg = json.loads(line.split("=", 1)[1])
base = cfg["variants"]["first_order_monthly_499"]
cfg["variants"]["first_order_monthly_99"] = { **base, "prices": { "monthly": 99 }, "planIds": { "monthly": plan } }
cfg["splits"].append({ "from": frm, "split": { "first_order_monthly_499": 100 - pct, "first_order_monthly_99": pct } })
print(json.dumps(cfg, separators=(",", ":")))
')
echo "plan: $PLAN_ID | from: $FROM | split: 499 $((100 - PCT))% / 99 $PCT%"
if [ "$DRY_RUN" = 1 ]; then echo "$NEW" | python3 -m json.tool | tail -22; echo "DRY RUN — nothing changed"; exit 0; fi
B=~/backend-filebak/$(date -u +%Y%m%d-%H%M%S)-paywall-99; mkdir -p "$B"; cp .env "$B/.env"
NEW="$NEW" python3 -c '
import os
new = os.environ["NEW"]
lines = open(".env").read().split("\n")
out = [("LAUNDRY_PAYWALL_CONFIG=" + new) if l.startswith("LAUNDRY_PAYWALL_CONFIG=") else l for l in lines]
open(".env", "w").write("\n".join(out))
'
cd ~/shweta-makeup-monorepo
docker compose -f docker-compose.prod.yml up -d --force-recreate backend 2>&1 | tail -1
sleep 10; docker ps --format '{{.Names}} {{.Status}}' | grep backend-1
if docker logs --since 1m "$C" 2>&1 | grep -q "\[paywall\]"; then echo "CONFIG REJECTED:"; docker logs --since 1m "$C" 2>&1 | grep "\[paywall\]"; cp "$B/.env" backend/.env; docker compose -f docker-compose.prod.yml up -d --force-recreate backend; exit 1; fi
docker exec -e PCT="$PCT" "$C" node -e '
const t = require("/app/services/laundry-paywall-test");
const later = new Date(Date.now() + 60e3), n = {};
const mk = (id, at) => ({ id, phone: "9999999999", signupPlatform: "android", signupAppVersion: 17, createdAt: at });
for (let i = 0; i < 4000; i++) { const g = t.groupFor(mk("chk-" + i, later)); n[g] = (n[g] || 0) + 1; }
const old = t.groupFor(mk("chk-old", new Date(Date.now() - 864e5)));
const ninety = Object.keys(n).find((k) => k.endsWith("_99"));
let o99 = null; for (let i = 0; i < 400 && !o99; i++) { const u = mk("chk-" + i, later); if (t.groupFor(u) === ninety) o99 = t.offer(u, 17); }
console.log("new accounts →", JSON.stringify(n), "| yesterday\x27s account →", old, "| ₹99 variant →", JSON.stringify(o99 && { gate: o99.gate, plans: o99.plans, monthly: o99.prices.monthly / 100, planId: o99.planIds.monthly, trial: o99.trial, freeOrders: o99.freeOrders }));
const share = (n[ninety] || 0) / 4000;
if (!o99 || Math.abs(share - Number(process.env.PCT) / 100) > 0.03 || old !== "first_order_monthly_499" || o99.prices.monthly !== 9900) { console.log("CHECK FAILED"); process.exit(1); }'
docker compose -f docker-compose.prod.yml up -d --force-recreate --no-deps laundry-dash 2>&1 | tail -1
echo "backup: $B"
echo "LIVE from $FROM: new Android accounts → first_order_monthly_99 $PCT% / first_order_monthly_499 $((100 - PCT))%"
REMOTE
