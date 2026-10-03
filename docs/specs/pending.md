# Pending work & roadmap

What's built is in the feature docs. This is everything still open, grouped by type and rough priority.

## 1. Feature gaps vs PRODUCT_SPEC

### Partial (works, but a spec sub-behaviour is simplified)
- 🔶 **Earnings per-day bar chart** — tap-a-bar-to-see-that-day chart from the prototype isn't drawn
  (reconciliation block, service bars, stats, payments list are all present). Should read the live ledger.
- 🔶 **Reschedule sheet date/time** — still uses Today/Tomorrow/Day-after chips + a plain time field.
  Should use the same native date/time pickers now used in New order, for consistency.
- 🔶 **New-order quick bill on create** — a brand-new order can't attach a quick bill from the form
  (only in edit mode); on create it comes from the Quick-order sheet. Wire the create-path to accept a quick amount.
- 🔶 **New item propagation** — adding a rate-card item adds it to the selected service only; spec
  wants it added (blank-priced) to every per-piece service.
- 🔶 **Home "jump to any date"** — the 7-day strip works; a calendar button to pick an arbitrary date
  is not wired.

### Not built
- ⛔ **WhatsApp send** (ready message + bill) — currently a toast stub. Needs a real share intent
  (or Business API) + message templates. *Product decision still open: share-link vs Business API.*
- ⛔ **Download bill as PDF** — toast stub; needs real PDF generation.
- ⛔ **UPI QR** on the customer-facing bill — not rendered.
- ⛔ **Late-night ready-message queueing** — after 9 PM the UI says messages go at 9 AM, but there's
  no real scheduling/notification.
- ⛔ **Closing-time reminder** — the 30-min-before-close push notification isn't implemented.
- ⛔ **Go back a status** (Ready → Received) from Order detail — spec lists it as out of scope for v1; not built.

### Intentionally removed
- **Close today** end-of-day wizard + Home banner — removed at the user's request. The individual
  lifecycle actions (mark picked up / ready / delivered, reschedule) cover the same transitions.
  Re-add later if a batch end-of-day flow is wanted.

## 2. Testing (highest-value next step)

Only the pure domain layer is tested today (`AcceptanceTest`, 9 scenarios). Add:
- **Repository tests** (in-memory Room) for `saveOrder`, `saveCount`, `markReady`, `deliver`,
  `reschedule`, `cancelOrder`, `receivePayment`, `addOldBaaki`, `saveCustomer`, `deleteService` +
  the Undo `snapshot`/`restore` round-trip.
- **Customer-khata running-balance test** — seed a ledger, assert the per-row "balance after" sequence
  (including the pre-payment-excluded-until-delivered rule).
- **Auth test** — the demo `MockEngine` flow is gone (see sync-and-auth.md); test `AuthRepository`
  against the real request/verify contract instead (fake `AuthApi`).
- **Compose UI tests** — at least Home (tabs/filters), New order (save → bill), Collect payment.
- **Earnings** week/month reconciliation once historical seed data is richer.

## 3. Architecture & release

- **Real system clock** — replace the pinned `AppDate.TODAY` / `NOW_MINUTES` with the device clock
  for a production build (they exist to keep the demo data coherent).
- ~~**Room migrations**~~ — done: schema is version 2 with a real `MIGRATION_1_2` (sync columns),
  destructive fallback removed, `exportSchema` on (`app/schemas/`).
- **Release build** — currently debug-signed only. Add a signing config + `isMinifyEnabled`/ProGuard
  and produce a signed release APK/AAB for distribution.
- **App icon & splash** — still the default launcher icon; add a branded icon + splash.
- **Accessibility pass** — audit content descriptions, touch targets and contrast end-to-end.

## 4. Process / tracker

- **Open the PR** — branch `feat/apnalaundry-app` is pushed; `gh` isn't installed, so the PR wasn't
  opened. Create at: `https://github.com/tushaar24/ApnaLaundry/pull/new/feat/apnalaundry-app`.
- **Publish these specs as issues** — the project convention is GitHub issues via `gh` with the
  `ready-for-agent` label; do this once `gh auth login` is done.

## 5. Open product decisions (from PRODUCT_SPEC §16)

- Real default prices & ready times (research with local shops).
- WhatsApp integration approach + exact message templates.
- Whether a batch "close the day" flow is still wanted after its removal.

## Suggested order of attack

1. Repository + khata unit tests (locks in correctness before more UI work).
2. WhatsApp share intent + PDF bill + UPI QR (the owner's actual daily outputs).
3. Reschedule native pickers + new-order quick bill + item propagation (finish the partials).
4. Release signing + app icon + Room migrations (ship-readiness).
5. Earnings chart + notifications (polish).
