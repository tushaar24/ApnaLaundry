# Billing, payments & khata

Status: ✅ Built

## Problem statement

The owner runs on trust and credit ("khata"): customers pay part now, part later, sometimes in
advance. Bills must be exact, never sent by accident, and every rupee must be traceable to a
customer's running balance.

## Solution

A Bill screen shown after an order with clothes is saved, a Collect-payment sheet at delivery with
a clear allocation rule, and a per-customer khata ledger that always reconciles.

## User stories

### Bill
1. As an owner, I want the bill made only when clothes are added, so that empty orders don't nag me.
2. As an owner, I want a bill summary (customer, route, lines, express, fee, discount, total, ready/delivery date), so that I can check it.
3. As an owner, I want View bill / Download / Send on WhatsApp buttons, so that I choose how to share.
4. As an owner, I want the bill NOT sent automatically, with a "Not sent yet" → "Sent to X ✓" status, so that I stay in control.
5. As an owner, I want editing the total to reset the bill to "not sent", so that customers get the corrected bill.
6. As an owner, I want an optional "Paying now? Got cash/UPI" on the bill, so that a prepaid order is recorded.
7. As an owner, I want a customer-facing "View bill" (shop details, lines, total, what's paid, thank-you), so that the customer sees a clean bill.
8. As an owner, I want bill actions from Order detail and the ⋯ menu too, so that I can reach them anywhere.

### Collect payment (at delivery)
9. As an owner, I want to see "This bill", "Old baaki"/"Advance already paid" and "Total to collect", so that I know the full ask.
10. As an owner, I want the amount pre-filled to the full total with chips (Full / Only this bill), so that the common case is one tap.
11. As an owner, I want a live preview ("₹X will stay in khata" / "₹X extra kept as advance" / "all clear"), so that I understand the outcome before confirming.
12. As an owner, I want Got cash / Got UPI / "Nothing now · add all to khata", so that every real situation is covered.
13. As an owner, I want overpayment kept as advance and underpayment left as baaki, so that the balance is always correct.

### Khata & customers
14. As an owner, I want a customers list with a search, so that I can find anyone.
15. As an owner, I want All / Baaki ₹X / Advance ₹X tiles that are also filters, so that I can chase who owes me.
16. As an owner, I want each row to show initials, name, last order, order count, phone and balance, so that I recognise them.
17. As an owner, I want a customer khata page with a running balance after every entry, so that I can explain any number.
18. As an owner, I want the khata to list bills, payments, old baaki and bill-changes newest-first, so that the story reads top-down.
19. As an owner, I want a prepayment to count only once its order is delivered, so that in-progress orders don't distort the balance.
20. As an owner, I want "advance used" noted when a new bill eats into advance, so that the customer sees where it went.
21. As an owner, I want Receive payment (prefilled to full baaki, chips, preview, cash/UPI), so that collecting old baaki is quick.
22. As an owner, I want to add old baaki from my notebook, so that pre-app debts are captured.
23. As an owner, I want "Orders in progress · not in khata yet" listed separately, so that I don't double-count.
24. As an owner, I want a manual "Send reminder on WhatsApp", so that nudging is my choice, not automatic.

### Combined bill (many orders of one customer, one bill)
25. As an owner, I want a "Combined bill" row on a customer's khata (shown once they have a counted,
    non-cancelled order), so that monthly-paying customers get one bill instead of many.
26. As an owner, I want chips — Custom dates, Last 7 days, Last 30 days, This month (default), Last
    month — that tick the orders whose **pickup date** falls in the range, plus Select all / Clear all
    and ticking by hand (which turns the chip off), so that picking is one tap in the common case.
27. As an owner, I want a preview of the one bill in my chosen design (logo, GSTIN, UPI, terms), with
    a "Show every item" switch, and Download / Send on WhatsApp (PDF), so that it looks like my bills.
28. As an owner, I want the combined bill to be a document only — it never adds to the khata or changes
    the balance — so that customers are never charged twice.

## Implementation decisions

- **Modules:** `ui/screens/bill/BillScreen`, `ui/sheets/CollectPaymentSheet`, `BillViewSheet`,
  `ReceivePaymentSheet`, `AddOldBaakiSheet`, `ui/screens/customers/CustomersScreen`,
  `ui/screens/customer/CustomerScreen`. Maths in `domain/LaundryMath`.
- **Bill maths (fixed order):** `clothes + express(if on) + fee − discount = total`;
  `express = round(clothes × pct / 100)`. Total is computed, never stored.
- **Delivery allocation** (`deliverAllocation`): `billDue = max(0, total − prepaid)`;
  `cover = min(received, billDue)`; `toOld = min(rest, max(0, oldBaaki))`; `toAdv = rest − toOld`;
  `newBalance = oldBaaki + total − prepaid − received`. Writes a `bill` and (if paid) a `got` ledger entry.
- **Khata balance** (`balance`): `Σ bill + Σ old + Σ adj − Σ payments`, but a `pre`-tagged payment
  is excluded until its order is `delivered`.
- **Payment label:** paid ≥ total → Paid; paid > 0 → Part paid; else In khata.
- **Edit after delivery:** the total difference is written as an `adj` ledger entry (can be negative)
  and the bill is marked not-sent.
- **WhatsApp / Download / UPI-QR:** stubbed as toasts ("Opening WhatsApp…", "Bill-N.pdf saved").
  The real integrations are pending.
- **Combined bill** (`domain/CombinedBill`, `ui/screens/customer/CombinedBillScreen`, drawn by
  `BillRender.layoutCombined`, sent by `BillShare.sendCombinedBillOnWhatsApp`):
  - Eligible orders = not cancelled and with lines; the pickup date decides the month.
  - Per order: amount = the usual bill total; paid = `min(total, delivered ? paid : pre)`; the row tag is
    Paid / "₹X due" / "In progress · Ready · Paid — not delivered".
  - Totals: `Total of N orders → Already paid → To pay`. The UPI QR prints only when to-pay > 0 and the
    UPI id is valid (`upi://pay?…&am=<to pay>&tn=Combined bill <period>`); otherwise a "Fully paid —
    thank you!" strip. No "previous balance" line: the bill covers the ticked orders only.
  - Period: the month for This / Last month; the date range for the other chips; for hand-picked
    orders the first–last pickup date. PDF name `Combined-bill-<FirstName>-<period>.pdf`.
  - Sending writes nothing to the khata (no ledger entry, no "sent" flag); it fires
    `Combined Bill Sent` in analytics.

## Testing decisions

- Good test = the numbers: given a bill, prepaid, old balance and an amount, assert the allocation
  (cover/toOld/toAdv/newBalance) and the customer's resulting khata balance.
- **Seam:** `LaundryMath` (pure) — already the home of the allocation/balance tests in
  `AcceptanceTest` (part payment with old baaki, overpayment, advance used, prepaid, edit-after-delivery).
- The running-balance ledger view (Customer khata) should get a repository-level test that seeds a
  ledger and asserts the per-row "balance after" sequence. Not yet written.

## Out of scope

Real WhatsApp Business API, PDF generation, UPI QR rendering, GST invoices.

## Further notes

Verified on emulator: Customers list showed live Baaki ₹2,140 / Advance ₹150 matching hand
computation; Vikas Jain's khata read Old baaki +₹950 → payment −₹500 → Baaki ₹450 with correct
running balances and the in-progress order excluded.
