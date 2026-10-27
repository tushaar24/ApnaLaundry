# Services-setup A/B: samples vs scratch (28 Oct 2026)

Owner ask: 50/50 test of the current setup services step (seeded sample list with sample prices) against a new flow where the shop starts with **no services** and adds them, with its own prices, from New order. Eligible: **brand-new accounts whose first login is on Android 1.0.17+** (both conditions). Older apps, older accounts and the website are untouched.

Why: 28 Oct CleverTap readout — owners who changed anything on the sample list during setup made a first order at 61% vs 36% for owners who tapped Next untouched, in every build and variant; the untouched ones stall at the clothes step. The scratch flow makes the price moment unavoidable.

## App (1.0.17, this commit)

- `GET /api/laundry/experiments` → `servicesSetup: "samples" | "scratch"` (absent = samples). Fetched at login for a new account before setup draws (`AuthRepository.finishLogin`, 5 s bound), again at every app start; saved in `Prefs.servicesSetup`; CleverTap profile `Services_Setup` + `services_setup` on every event (`Analytics.setServicesSetup`).
- **scratch**: `OnboardingFlow` goes name → bill and removes the untouched sample services (`LaundryRepository.removeSampleServices`, no analytics); `NewOrderScreen` shows "No services yet · Add service" when the shop has none, a "+ Add service" tile after the service tiles and a folded "+ Add cloth" row (`ClothesEditor(onAddService, simpleAdd)`); the add form is `AddServiceSimplePage` (route `add-service?from=`), also used by the Services page's "Add a new service" in this group; the last service can be deleted (`deleteService(allowEmpty)`); `ShopViewModel.guideOn` is false (no guide, no Settings replay). Edit service and Add service open with the name focused and the keyboard up.
- **samples**: unchanged.
- New strings translated into the 10 languages (`tools/i18n/translate.py`; Hindi "Charge by" overridden to "किस हिसाब से दाम").
- `preview` build type (package `.preview`, label "MyLaundry Preview") for side-by-side testing on the owner's phone; `app/google-services.json` carries a client for it. Not a release artefact.

## Backend — `2026-10-28-backend-services-setup-ab.patch` (`routes/laundry-experiments.js`)

- `servicesSetupFor(user)`: `LAUNDRY_SERVICES_SETUP_FORCE` pin by phone → else null unless `LAUNDRY_SERVICES_SETUP_START` is set, `signup_platform = android`, `signup_app_version ≥ 17`, `created_at ≥ START`; then `scratch` when the user-id bucket (`services_setup_v1`) is below `LAUNDRY_SERVICES_SETUP_SCRATCH_PCT` (default 50), else `samples`.
- `GET /experiments`: `guide` is forced to `no_guide` for scratch; `Services_Setup` saved to CleverTap once per process per user; the key is only present for test accounts.
- Deploy: `DRY_RUN=1 bash docs/deploy/2026-10-28-services-setup-ab-deploy.sh` then without `DRY_RUN` (backs up to `~/backend-filebak/<ts>-services-setup-ab/`, appends `LAUNDRY_SERVICES_SETUP_START=<now>` + `LAUNDRY_SERVICES_SETUP_SCRATCH_PCT=50` to `.env`, rebuilds, checks, commits on the server-local main). Rollback: restore the two backed-up files, rebuild.
- Pin a QA phone: `LAUNDRY_SERVICES_SETUP_FORCE='{"1000000000":"scratch"}'` in `.env` + rebuild. Change the split: edit `LAUNDRY_SERVICES_SETUP_SCRATCH_PCT` (moves only accounts not yet bucketed in effect, since a bucket is a stable hash: lowering it moves some scratch accounts to samples — do it only between tests). Stop the test: unset `LAUNDRY_SERVICES_SETUP_START`.

## Reading the test

Split every funnel by `services_setup` (event prop) or `Services_Setup` (profile): `Setup Completed` → `New Order Started` → `Service Added` → `Order Saved` → `Order Saved {orders_count ≥ 3}` → `Checkout Succeeded`. Expect scratch to have no `Onboarding Step Viewed {step: services}` and no `Tour *` events. The 28 Oct baseline for the samples flow: 50% of setup completers make a first order (61% touched / 36% untouched).
