# Architecture & testing

Status: ✅ Built

## Problem statement

The app must work fully offline (owners update late at night, often on flaky networks),
keep money perfectly reconciled, and stay easy to extend screen-by-screen.

## Solution

A single-module Android app (Jetpack Compose) with a clean, layered architecture:
a pure domain layer holds all money rules; Room persists everything locally; a reactive
`LaundryState` flows up into one shared ViewModel that every screen reads.

## Implementation decisions

### Stack

- **UI:** Jetpack Compose (Material 3), custom design-system components. Fonts bundled as
  variable TTFs — Bricolage Grotesque (headings) + Figtree (body).
- **Persistence:** Room. Offline-first; the DB is the source of truth.
- **Network:** Ktor client — used only for the demo auth (OTP request/verify) via a
  `MockEngine` backend, so it is real Ktor request/response plumbing that runs offline.
- **DI:** Koin.
- **Prefs:** DataStore for `loggedIn`, `setupDone`, `hideAmounts` flags.
- **Navigation:** Navigation-Compose behind a thin typed `AppNavigator`.

### Layers (dependencies point downward)

- `core/` — `Money` (Indian grouping), `AppDate` (pinned today/now, date maths). No Android deps beyond stdlib/java.time.
- `domain/` — pure Kotlin models + `LaundryMath`, `EarningsMath`, `SeedData`. No Android, no storage.
- `data/` — Room entities/DAOs/DB, `Mappers`, `LaundryRepository` (commands + reactive state + undo),
  `remote/AuthApi` (Ktor), `Prefs`, `AuthRepository`.
- `di/` — Koin module + `ApnaLaundryApp` (starts Koin, seeds on first launch).
- `ui/` — `theme/`, `components/`, `sheets/`, `screens/`, `nav/`, `Selectors`, `ShopViewModel`.

### Key architectural decisions

- **One shared `ShopViewModel`** holds the reactive `LaundryState`, the global toast + Undo,
  and `hideAmounts`. Screens keep only transient form state locally and call VM commands.
  This mirrors the prototype's single-state design and avoids cross-screen plumbing bugs.
- **Everything derived, nothing double-stored.** Order totals, khata balances and earnings are
  computed from lines + ledger, never stored as running totals — so money reconciles by construction.
- **Snapshots power Undo.** Undoable commands capture a `Snapshot` of the mutable tables;
  `undo()` restores it. This is the app-wide replacement for "Are you sure?" dialogs.
- **History is immutable.** Order lines snapshot the service name + price at capture time, so
  deleting a service or changing a rate never rewrites past orders or khata.
- **Pinned clock.** `AppDate.TODAY = 2026-09-25` and `NOW_MINUTES = 21:10` so the seeded demo
  data (late pickups, delivered-today, etc.) reads correctly. This is the one place to change
  for a real "system clock" build.
- **JSON columns.** Service items and order lines are stored as JSON strings in Room (via
  kotlinx-serialization in the mappers) rather than child tables — chosen after Room's KSP
  processor rejected generic `List<>` `@TypeConverter`s.

### Data model (persisted)

`shop` (single row, holds `nextOrder`/`nextCust` counters) · `services` · `customers` ·
`orders` (lines as JSON) · `ledger` · `day_close`.

## Testing decisions

### What makes a good test here

Test **external behaviour** (money in → money out), not implementation details. The money
rules are the highest-risk, highest-value surface, and they are pure functions — so they are the
primary seam.

### Seams (highest first)

1. **Domain maths (`LaundryMath`, `EarningsMath`)** — the primary seam. Pure functions with no
   Android/storage deps; the acceptance scenarios run here. *This is the seam to prefer for new
   money behaviour.*
2. **`LaundryRepository`** — commands over Room. Testable with an in-memory Room DB
   (`Room.inMemoryDatabaseBuilder`) driving `androidx.test`. Not yet exercised (see pending).
3. **Compose UI** — screen-level tests via `createAndroidComposeRule`. Not yet present.

### Prior art

`app/src/test/java/.../AcceptanceTest.kt` covers PRODUCT_SPEC §14 against seam 1 — the pattern
for any new money rule: build domain inputs, assert the numeric result and the reconciliation
identity `received = work − baakiAdded + oldIn + advIn + prepaid = cash + upi`.

## Out of scope

Multi-module split and a real system clock remain deferred. A real backend and
offline-first sync have since been built — see [sync-and-auth.md](sync-and-auth.md).

## Further notes

Verified end-to-end on a Pixel 8 Pro emulator (login → setup → home → earnings → customers →
khata → count sheet). The seed day reproduces the spec's ₹2,345 reconciliation exactly, both in
unit tests and live in Earnings.
