# ApnaLaundry — specs

Retrospective specs for the ApnaLaundry Android app, synthesised from the build so far.
Each feature doc follows the standard spec template (Problem → Solution → User Stories →
Implementation / Testing Decisions → Out of Scope → Further Notes).

> **Tracker note:** the project convention (`docs/agents/issue-tracker.md`) is that specs live
> as GitHub issues via `gh`. `gh` isn't installed/authenticated in the build environment, so these
> are written as markdown docs instead. They can be published as issues (with the `ready-for-agent`
> label) once `gh auth login` is done.

## Vocabulary (from PRODUCT_SPEC.md + CLAUDE.md)

- **Baaki** — money a customer owes the shop (khata balance > 0, shown orange).
- **Advance** — money a customer paid extra (khata balance < 0, shown blue).
- **Khata** — the per-customer credit ledger.
- **Quick order** — capture-in-5-seconds walk-in order (customer only; amount/clothes later).
- **Quick bill** — a single "not itemised" line carrying a lump amount.
- **Ready time (TAT)** — a service's optional "ready in N days"; auto-fills the delivery date.
- **Express** — rush order; adds a % of the clothes total, sorts first.

## Documents

| Doc | Area | Status |
|---|---|---|
| [architecture.md](architecture.md) | Stack, layers, test seams | ✅ Built |
| [onboarding-auth.md](onboarding-auth.md) | Demo login + OTP, rate-card setup | ✅ Built |
| [orders.md](orders.md) | Home, order lifecycle, new/edit, count, quick order | ✅ Built |
| [billing-payments-khata.md](billing-payments-khata.md) | Bill, payments, khata ledger, customers | ✅ Built |
| [earnings.md](earnings.md) | Earnings reconciliation | ✅ Built (chart pending) |
| [rate-card-settings.md](rate-card-settings.md) | Rate card editor, settings | ✅ Built |
| [pending.md](pending.md) | What's left, out of scope, roadmap | — |

## Status legend

- ✅ **Done** — built and verified (compiles, unit-tested and/or driven on emulator).
- 🔶 **Partial** — works, but a sub-behaviour from the spec is simplified or missing.
- ⛔ **Not built** — in the product spec but not implemented yet.

## Product summary

An offline-first orders/billing/khata app for a small laundry owner in a tier 2–3 Indian city.
One screen per job, big touch targets, plain words, ₹ with Indian grouping, Undo instead of
confirm dialogs. Demo login (fixed credentials); all data seeded locally so the app is fully
usable offline.

## Build & run

```bash
export JAVA_HOME="/Users/tusharpal/Library/Java/JavaVirtualMachines/ms-17.0.15/Contents/Home"
export ANDROID_HOME="$HOME/Library/Android/sdk"
./gradlew :app:assembleDebug        # APK → app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:testDebugUnitTest    # domain acceptance tests
```

Demo login: phone **9876543210**, OTP **123456**. The app's "today" is pinned to
**2026-09-25** so the seeded demo data (pending / late / delivered orders) stays coherent.
