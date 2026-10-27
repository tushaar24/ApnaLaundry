# Test numbers: 1000000000 always new, 1000000001 yearly (28 Oct 2026)

Owner ask: use review number **1000000000** to "always feel new in the journey", and **1000000001** as a normal test account with a yearly subscription.

## Done directly on prod (28 Oct ~01:30 IST, DB only, no deploy needed)

- **1000000000**: account `c0b120f5-…` and all its data deleted (1 shop, 5 services, 5 customers, 6 orders, 5 bill links, 1 subscription row). The next login creates it afresh.
- **1000000001**: account kept (id `b99e0ec4-c293-42ac-95fd-affa4eccf68f`, same token), all data wiped (1 shop, 4 services, 2 subscription rows), then a DB-only `laundry_subscriptions` row added: plan annual, status active, notes `{"manual":true,"test":true}`, `current_end` **2027-10-27**. `/billing/status` verified: `hasActiveSubscription: true`, `paywallDue: false`. Logging in seeds a fresh shop and keeps data like any other account.

## Backend change (staged — needs the owner to run the deploy)

`2026-10-28-backend-always-new-phones.patch` against the live server tree (`~/shweta-makeup-monorepo/backend`, dry run passed):

- `services/laundry-user-wipe.js` (new): `wipeUserData(db, userId, {deleteUser})` deletes payments, subscriptions, bill links, orders, ledger, day closes, customers, services, logos, shops, support calls (tags cascade), assistant logs, credentials, sessions, and unlinks WhatsApp messages; FK order, no cascade in the schema. `isAlwaysNewPhone(phone)` reads `LAUNDRY_ALWAYS_NEW_PHONES` (comma-separated).
- `routes/laundry-auth.js` `loginPhone`: if the phone is always-new and an account exists, wipe + delete it inside the login transaction, then fall through to the normal create → new id, new token, `isNewUser: true` (the app skips the pull and seeds). Works for verify-otp and Firebase logins alike.
- `config/env.js`: `laundryAlwaysNewPhones` (documentation only; the service reads process.env).
- `.env`: `LAUNDRY_ALWAYS_NEW_PHONES=1000000000`.

Deploy (backs up the 3 files + .env to `~/backend-filebak/<ts>-always-new/`, commits on the server-local main, rebuilds the backend container, checks the service loads):

```
DRY_RUN=1 bash docs/deploy/2026-10-28-always-new-phones-deploy.sh
bash docs/deploy/2026-10-28-always-new-phones-deploy.sh
```

Rollback: copy the backed-up `routes/laundry-auth.js`, `config/env.js`, `.env` back, delete `services/laundry-user-wipe.js`, rebuild.

## Using the numbers

- 1000000000 (OTP 100000): **log out in the app before logging in again** — logout wipes the phone's local data; the server then hands back a brand-new account (new experiment buckets, free orders, paywall group as for a real new sign-up). Every login = day-0 journey. Don't keep anything on it. Until the deploy runs, only the one-off wipe above applies (the account persists again after the next login).
- 1000000001 (OTP 100001): data persists; yearly plan active to 27 Oct 2027. To reset it again, run the wipe with `deleteUser: false` and re-add the subscription row (see the patch's service).
- Both numbers are already excluded from the dashboards (`10000000xx`).
