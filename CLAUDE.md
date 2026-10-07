# ApnaLaundry

## Where things live

- **Android app** — `app/` (Kotlin + Compose, offline-first Room DB syncing to the backend). Build from the CLI with Android Studio's JDK: `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew ...` (system JDK 26 breaks Gradle).
- **Website** — `web/` (Next.js standalone, served at mylaundry.work). Its own Next server proxies `/api/laundry/*` to the backend. See `web/DEPLOY.md`.
- **Backend is NOT in this repo.** It lives in the `shweta-makeup-monorepo` (GitHub `tushaar24/courses`, Express + Prisma), shared with the Shweta Makeup course site. Local checkouts: `~/Documents/Documents - Tushar’s MacBook Air (2)/shweta-makeup-monorepo` (also cloned at `./courses/`, untracked here). Laundry code is namespaced:
  - `backend/routes/laundry-auth.js` (OTP login), `backend/routes/laundry.js` (sync push/pull), `backend/routes/laundry-billing.js` (subscription status/subscribe/cancel/webhook)
  - `backend/services/laundry-razorpay.js` — Razorpay plans + `TRIAL_DAYS` (the source of truth; `TRIAL_DAYS` in the web/Android paywalls is display-only and must match)
  - Prisma models `Laundry*` (tables `laundry_*`); env vars `LAUNDRY_*` in `backend/.env`
- **Billing design** — `docs/billing.md`.

## Deploy

All prod runs on one EC2 host, `ssh courses-aws` (ubuntu@3.109.137.181), as docker compose services in `~/shweta-makeup-monorepo` (`docker-compose.prod.yml`): nginx `web`, `backend`, `migrate`, `laundry` (this repo's website).

- **Backend:** merge/push to `main` of `tushaar24/courses` → on the server `cd ~/shweta-makeup-monorepo && git pull` → `docker compose -f docker-compose.prod.yml run --rm --build migrate` (only if the Prisma schema changed) → `docker compose -f docker-compose.prod.yml up -d --build backend`.
- **Web:** `rsync` this repo's `web/` (excluding `node_modules`/`.next`) to `courses-aws:/home/ubuntu/apnalaundry-web/` → on the server `docker compose -f docker-compose.prod.yml up -d --build laundry`.
- **Android:** build the release AAB/APK with the Studio JDK and upload to Play Console (manual).

## Agent skills

### Issue tracker

Issues and specs live as GitHub issues, managed via the `gh` CLI. See `docs/agents/issue-tracker.md`.

### Domain docs

Single-context — one `CONTEXT.md` + `docs/adr/` at the repo root. See `docs/agents/domain.md`.
