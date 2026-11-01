#!/usr/bin/env bash
# Owner, 1 Nov 2026 (22:00 IST): close ₹499 for NEW Android accounts → 0% ₹499, 90% ₹99/month,
# 10% ₹199/month (first_order_monthly_499 / _99 / _199, same first-order pop-up). Existing accounts keep
# their variant (dated splits apply to accounts created after them). No code change: appends a dated split
# to LAUNDRY_PAYWALL_CONFIG, recreates backend + laundry-dash, verifies the draw and an old account.
#   DRY_RUN=1 bash docs/deploy/2026-11-01-paywall-split-0-90-10.sh
#   bash docs/deploy/2026-11-01-paywall-split-0-90-10.sh
set -euo pipefail
ssh -o BatchMode=yes courses-aws "DRY_RUN=${DRY_RUN:-0} P499='${P499:-0}' P99='${P99:-90}' P199='${P199:-10}' bash -s" <<'REMOTE'
set -euo pipefail
cd ~/shweta-makeup-monorepo/backend
C=shweta-makeup-monorepo-backend-1
FROM=$(date -u +%Y-%m-%dT%H:%M:%SZ)
NEW=$(FROM="$FROM" python3 -c '
import json, os
env = open(".env").read()
line = next(l for l in env.splitlines() if l.startswith("LAUNDRY_PAYWALL_CONFIG="))
cfg = json.loads(line.split("=", 1)[1])
for v in ("first_order_monthly_499", "first_order_monthly_99", "first_order_monthly_199"): assert v in cfg["variants"], v
cfg["splits"].append({ "from": os.environ["FROM"], "split": { "first_order_monthly_499": int(os.environ["P499"]), "first_order_monthly_99": int(os.environ["P99"]), "first_order_monthly_199": int(os.environ["P199"]) } })
print(json.dumps(cfg, separators=(",", ":")))
')
echo "from: $FROM | split: 499 $P499% / 99 $P99% / 199 $P199%"
if [ "$DRY_RUN" = 1 ]; then echo "$NEW" | python3 -c 'import json,sys; c=json.load(sys.stdin); print(json.dumps(c["splits"], indent=1))'; echo "DRY RUN — nothing changed"; exit 0; fi
B=~/backend-filebak/$(date -u +%Y%m%d-%H%M%S)-paywall-split-0-90-10; mkdir -p "$B"; cp .env "$B/.env"
NEW="$NEW" python3 -c '
import os
new = os.environ["NEW"]
lines = open(".env").read().split("\n")
open(".env", "w").write("\n".join(("LAUNDRY_PAYWALL_CONFIG=" + new) if l.startswith("LAUNDRY_PAYWALL_CONFIG=") else l for l in lines))
'
cd ~/shweta-makeup-monorepo
docker compose -f docker-compose.prod.yml up -d --force-recreate backend 2>&1 | tail -1
sleep 10; docker ps --format '{{.Names}} {{.Status}}' | grep backend-1
if docker logs --since 1m "$C" 2>&1 | grep -q "\[paywall\]"; then echo "CONFIG REJECTED:"; docker logs --since 1m "$C" 2>&1 | grep "\[paywall\]"; cp "$B/.env" backend/.env; docker compose -f docker-compose.prod.yml up -d --force-recreate backend; exit 1; fi
docker exec -e P499="$P499" -e P99="$P99" -e P199="$P199" "$C" node -e '
const t = require("/app/services/laundry-paywall-test");
const later = new Date(Date.now() + 60e3), n = {};
const mk = (id, at) => ({ id, phone: "9999999999", signupPlatform: "android", signupAppVersion: 17, createdAt: at });
for (let i = 0; i < 10000; i++) { const g = t.groupFor(mk("chk-" + i, later)); n[g] = (n[g] || 0) + 1; }
const old = t.groupFor(mk("chk-old", new Date(Date.now() - 864e5)));
const pct = (k) => Math.round(100 * (n[k] || 0) / 10000);
const want = { first_order_monthly_499: +process.env.P499, first_order_monthly_99: +process.env.P99, first_order_monthly_199: +process.env.P199 };
console.log("new accounts →", JSON.stringify(Object.fromEntries(Object.keys(want).map((k) => [k, pct(k) + "%"]))), "| an account from yesterday →", old);
const ok = Object.entries(want).every(([k, v]) => Math.abs(pct(k) - v) <= 2) && Object.keys(n).every((k) => k in want && want[k] > 0);
for (const k of Object.keys(want).filter((k) => want[k] > 0)) { let o = null; for (let i = 0; i < 2000 && !o; i++) { const u = mk("chk-" + i, later); if (t.groupFor(u) === k) o = t.offer(u, 17); } console.log("  ", k, "→ monthly ₹" + (o && o.prices.monthly / 100), "plans", o && o.plans, "gate", o && o.gate); }
if (!ok) { console.log("CHECK FAILED"); process.exit(1); }'
docker compose -f docker-compose.prod.yml up -d --force-recreate --no-deps laundry-dash 2>&1 | tail -1
echo "backup: $B"
echo "LIVE from $FROM: new Android accounts → ₹499 $P499% / ₹99 $P99% / ₹199 $P199%"
REMOTE
