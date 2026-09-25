# Earnings

Status: ✅ Built · 🔶 per-day bar chart pending

## Problem statement

At the end of the day the owner wants to know one thing with total confidence: how much money came
in, where it came from, and whether the cash drawer should match — without any accounting jargon.

## Solution

An Earnings screen that shows money received (cash vs UPI), a reconciliation breakdown that must
add up, work-done-by-service bars, headline stats, baaki-in-market, and a filterable payments list.

## User stories

1. As an owner, I want Today / This week / This month periods, so that I can zoom in or out.
2. As an owner, I want a big "Money received" with a cash/UPI split ("should be in your drawer" / "in your bank account"), so that I can reconcile at a glance.
3. As an owner, I want a "How the money came in" block that adds up (work − went to khata + old baaki collected + advance taken + paid before delivery = money received), so that I trust the total.
4. As an owner, I want work-done-by-service bars with ₹ and %, plus express and pickup/delivery charges, so that I see what earns.
5. As an owner, I want a "− ₹X discounts given" line, so that giveaways are visible.
6. As an owner, I want headline stats (orders delivered · pieces · kg), so that I gauge volume.
7. As an owner, I want a "Baaki in market ₹X · N customers" card that opens the Baaki filter, so that I can chase debts.
8. As an owner, I want a payments-received list (All / Cash / UPI) that says what each was for, so that I can audit any entry.
9. As an owner, I want to tap a payment to open that customer's khata, so that I can follow up.
10. As an owner, I want the eye toggle to hide amounts (shared with Home), so that I can review privately.
11. As an owner, I want a WhatsApp share of a plain-text day summary, so that I can send it to myself or a partner.
12. As an owner, I want the money to reconcile for day, week and month after any action, so that I never chase a mismatch.

## Implementation decisions

- **Module:** `ui/screens/earnings/EarningsScreen`, maths in `domain/EarningsMath`.
- **Computed live from the ledger + orders** for a period predicate (today = `== TODAY`,
  week = `>= 2026-09-21`, month = `>= 2026-09-01`). Because everything is derived, the identity
  `received = work − baakiAdded + oldIn + advIn + prepaid` and `received = cash + upi` hold by construction.
- **`prepaid` is the remainder** (`received − (work − baakiAdded + oldIn + advIn)`) so the block
  always balances even across day boundaries.
- **Service bars** sorted with real services first (blue) then extras — Express / Pickup-delivery — (grey).
- **Share text** built from raw (unmasked) numbers so hiding amounts on-screen doesn't corrupt the message.

### Partial
- 🔶 The **per-day bar chart** (tap a bar → that day's amount) from the prototype is not drawn; the
  reconciliation block, service bars, stats and payments list are all present. The prototype's
  week/month history relied on hardcoded constants; a real chart should read the live ledger.

## Testing decisions

- Good test = the identity: seed a period's orders + ledger, call `EarningsMath.compute`, assert
  every field and both reconciliation equations.
- **Seam:** `EarningsMath` (pure). `AcceptanceTest.earningsReconciliationSeedDay` already asserts the
  seed-day figures (₹2,345 = 1,560 cash + 785 UPI = 1,315 work − 180 khata + 500 old + 100 advance + 610 prepaid).
- Add week/month reconciliation tests once historical seed data is richer.

## Out of scope

Profit/expenses, tax, and any period beyond the seeded September window.

## Further notes

Verified live: the Today view reproduced the spec's ₹2,345 reconciliation exactly on the emulator.
