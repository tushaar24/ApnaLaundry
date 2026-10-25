#!/usr/bin/env bash
# The paywall right after the first order again (owner, 2026-10-25): new accounts from the
# moment this runs get variant first_order_monthly_499 = free1_monthly_499 (Monthly ₹499,
# paid upfront, 1 free order) with gate "first_order" — the paywall shows once after the
# first order is saved and the guide is finished or skipped (the server sends paywallGroup
# "free1", which app 1.0.13+ draws), then New order asks for the plan as before.
# Accounts already in free1_monthly_499 keep it (the paywall at the 2nd New order).
# Note: docs/deploy/2026-10-25-first-order-paywall.md
#
#   DRY_RUN=1 bash docs/deploy/2026-10-25-first-order-paywall-deploy.sh   # shows the change, changes nothing
#   bash docs/deploy/2026-10-25-first-order-paywall-deploy.sh             # applies, recreates backend + laundry-dash
set -euo pipefail

ssh -o BatchMode=yes courses-aws "DRY_RUN=${DRY_RUN:-0} bash -s" <<'REMOTE'
set -euo pipefail
cd ~/shweta-makeup-monorepo
ENV=backend/.env
FROM=$(date -u +%Y-%m-%dT%H:%M:%SZ)
python3 - "$ENV" "$FROM" "$DRY_RUN" <<'PY'
import json, shutil, sys
path, frm, dry = sys.argv[1], sys.argv[2], sys.argv[3] == "1"
lines = open(path).read().split("\n")
i = next(i for i, l in enumerate(lines) if l.startswith("LAUNDRY_PAYWALL_CONFIG="))
cfg = json.loads(lines[i].split("=", 1)[1])
name = "first_order_monthly_499"
if name in cfg["variants"]:
    sys.exit(f"{name} is already in the config — nothing to do")
cfg["variants"][name] = {**cfg["variants"]["free1_monthly_499"], "gate": "first_order"}
cfg["splits"].append({"from": frm, "split": {name: 100}})
print("new variant:", json.dumps(cfg["variants"][name]))
print("new split:  ", json.dumps(cfg["splits"][-1]))
if dry:
    sys.exit(0)
shutil.copy(path, path + ".bak-first-order-" + frm.replace(":", ""))
lines[i] = "LAUNDRY_PAYWALL_CONFIG=" + json.dumps(cfg, separators=(",", ":"))
open(path, "w").write("\n".join(lines))
print("saved (backup next to it)")
PY
if [ "$DRY_RUN" = "1" ]; then echo "dry run: nothing changed"; exit 0; fi
docker compose -f docker-compose.prod.yml up -d --force-recreate backend
sleep 10
docker logs --since 1m shweta-makeup-monorepo-backend-1 2>&1 | grep "\[paywall\]" || echo "[paywall] config accepted (no errors logged)"
# The admin dashboard labels owners with the backend's rule; it reads the setting at start.
docker compose -f docker-compose.prod.yml up -d --force-recreate --no-deps laundry-dash
docker compose -f docker-compose.prod.yml ps backend --format '{{.Status}}'
REMOTE
