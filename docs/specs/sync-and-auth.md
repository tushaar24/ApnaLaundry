# Sync & real auth

The app is offline-first: Room stays the single source of truth for the UI,
and a sync engine mirrors it against a shared backend (the Express/Prisma/
Postgres API in the separate `courses` monorepo, mounted at `/api/laundry/*`).
Login is a real OTP flow ported from the HealthProduct identity service.

## Backend

Lives in `courses/backend` (its own repo, gitignored here). Everything is
namespaced so it can't collide with that project's course tables:

- Prisma models `LaundryUser/Shop/Service/Customer/Order/LedgerEntry/DayClose`
  (tables `laundry_*`), each scoped by `userId` and mirroring the Room entity
  field-for-field plus sync metadata: `updatedAt` (client epoch ms, the
  last-write-wins key), `deleted` (tombstone), `serverUpdatedAt` (pull cursor).
- Auth tables `laundry_otp_challenges`, `laundry_sessions`,
  `laundry_credentials` — the HealthProduct model: provider-owned OTP,
  challenge token presented as Bearer on verify, opaque random access
  (15 min) / refresh (60-day session, single-use rotating) tokens stored as
  SHA-256 digests. No JWT.
- Routes: `routes/laundry-auth.js` (`request-otp`, `verify-otp`, `refresh`,
  `logout`, `me`) and `routes/laundry.js` (`sync/push`, `sync/pull?since=`),
  gated by `middleware/laundry-auth.js`.
- OTP delivery is provider-owned (`services/laundry-otp.js`): `test` (fixed
  `LAUNDRY_TEST_OTP_CODE`, refused in production unless explicitly allowed) or
  `minimoth` (HTTP adapter). All laundry env vars are optional — the course
  stack boots without them and the laundry routes 503. App-review numbers
  `10000000xx` (xx = any two digits) bypass the provider in every environment:
  no SMS, OTP = `10000` + the phone's last digit (`1000000042` → `100002`);
  the app's phone validation accepts them alongside real 6-9 mobiles.

## Sync protocol

Push: client sends every dirty row; the server upserts unless it already holds
a newer `updatedAt` for that key (ties go to the incoming row, so re-pushes are
idempotent). Pull: `?since=<checkpoint>` returns rows whose `serverUpdatedAt`
is newer, minus a 2-minute overlap window so a row committed concurrently with
a pull is never missed; the client applies the same LWW rule, so re-received
rows are harmless. Deletes are tombstones in both directions.

## App side

- Every Room entity carries `updatedAt`/`dirty` (+ `deleted` where it didn't
  exist). DB is version 2 with a real migration (`AppDatabase.MIGRATION_1_2`,
  destructive fallback removed, `exportSchema` on). Migrated rows get
  `updatedAt = 0` — they lose LWW against any server copy — and `dirty = 1`.
- `SyncClock.now()` (monotonic wall clock) stamps writes; entity constructor
  defaults make every `toEntity()` write dirty automatically, and the few
  entity-`copy()` call sites in `LaundryRepository` stamp explicitly.
- `data/sync/`: `SyncManager` (mutex-guarded pull-then-push), `SyncApi`
  (Bearer + one 401-refresh-retry), `SyncScheduler` (2s debounce after every
  `ShopViewModel` command), `SyncWorker` (hourly WorkManager, network-gated).
- `TokenManager` refreshes the access token when <30s from expiry
  (single-flight); a 401 on refresh means the session is dead → credentials
  wiped, `loggedIn` flipped off, nav returns to login. Local data is kept and
  merges on the next login; logging into a *different* phone wipes it first.
- Undo (`restore`) re-stamps restored rows dirty and converts rows created
  after the snapshot into tombstones, so a rollback syncs instead of being
  resurrected by the next pull.
- Login (`AuthRepository.verifyOtp`): store credentials → initial sync → if
  the server already has this account's shop, `setupDone` is set (setup flow
  skipped); otherwise seed defaults (shop + rate card only — the demo
  customers/orders are no longer seeded) and push.
- Logout pushes first and refuses to proceed (session kept) if unsynced work
  can't be pushed for a transient reason; a dead session never blocks logout.
- Base URL: `BuildConfig.API_BASE_URL` — debug `http://10.0.2.2:5001/api/laundry`
  (cleartext allowed via the debug-only manifest), release
  `https://shwetamakeover.online/api/laundry`.

## Known limits (single-device-per-account assumption)

Shop counters (`nextOrder`/`nextCust`) and ledger `ts` ids merge last-write-
wins; two devices minting ids concurrently can collide. Tokens live in
DataStore (HealthProduct's Keystore-encrypted vault was not ported). The
pinned demo clock (`AppDate.TODAY`) still stamps order dates — replacing it
with the real clock (pending.md) is the main remaining ship-readiness item.

## Testing it locally

Backend: `npm run dev:db` then `npm run dev:backend` in `courses/`
(`backend/.env` is set up with the `test` OTP provider — any valid phone, OTP
`123456`). Verified end-to-end via curl: request/verify OTP (wrong-OTP
attempt decrement, resend cooldown 429), push (LWW skip of stale rows), pull
(checkpoint + tombstones), refresh rotation (old token dies), logout (401
after).
