# Orders — Home, lifecycle, new/edit, count, quick order

Status: ✅ Built (a few sub-behaviours partial — noted inline)

## Problem statement

The owner's whole day is orders: taking them in, tracking pickup → ready → delivered, and finding
the next thing to do at a glance while standing at the counter with one free hand.

## Solution

A Home screen that shows today's pickups and deliveries with the next action on every card, a
one-screen new/edit order form, a 5-second Quick order, and a Count-clothes sheet that turns an
uncounted order into a bill.

## User stories

### Home
1. As an owner, I want today's pickups and deliveries in two tabs, so that I focus on one job type at a time.
2. As an owner, I want an orange badge of "still to do" on each tab, so that I know my workload.
3. As an owner, I want a date strip (yesterday + today + 5 days) with per-day counts, so that I can plan ahead.
4. As an owner, I want the selected day filled dark and "Today" highlighted, so that I never lose my place.
5. As an owner, I want filter tabs with counts (pickups: All/To pick up/Received/Cancelled; deliveries: All/Not ready/Ready/Delivered), so that I can narrow the list.
6. As an owner, I want a dashboard strip showing new orders + money collected for the selected day, so that I feel the day's pulse.
7. As an owner, I want an eye toggle to hide ₹ amounts, so that I can use the app in front of customers.
8. As an owner, on today I want a "Pending from earlier" (pickups) / "Late or no delivery date" (deliveries) group on top, so that overdue work surfaces first.
9. As an owner, I want each card to show the next-step button (Mark picked up → Mark ready → Mark delivered), so that acting is one tap.
10. As an owner, I want cards to show customer, EXPRESS tag, amount, item/service summary and payment status, so that I recognise the order fast.
11. As an owner, I want a warning line (Late / Not ready yet / No delivery date) with an orange dot, so that problems stand out.
12. As an owner, I want a Call button on home orders, so that I can reach the customer.
13. As an owner, I want a ⋯ menu per card, so that I can reach less-common actions.
14. As an owner, I want to search orders by name, phone or order number across all dates, so that I can find any order.
15. As an owner, I want floating Quick order and New order buttons, so that capturing is always one tap. *(Quick order sits above New order.)*
16. As an owner, I want a bottom nav (Orders / Customers / Earnings / Settings), so that I can move around.

### Order lifecycle
17. As an owner, I want walk-ins to start as Received and home pickups as To pick up, so that status matches reality.
18. As an owner, I want "Mark picked up" / "Mark ready" on an order with no clothes to open Count clothes first, so that the bill gets made.
19. As an owner, I want "Mark ready" to (say it will) send the ready WhatsApp, so that the customer knows.
20. As an owner, when it's after 9 PM, I want ready messages queued for 9 AM, so that I don't disturb customers at night.
21. As an owner, I want "Mark delivered" to open Collect payment, so that I take money at handover.
22. As an owner, I want to skip straight to delivered from the ⋯ menu, so that walk-in-and-collect is fast.
23. As an owner, I want to cancel a pickup (only before pickup) with an optional reason, so that no-shows are recorded.
24. As an owner, I want to reschedule a pickup or delivery (chips + calendar + optional time), so that plans can change; a pickup move shifts an auto delivery date by the same days.

### New / edit order
25. As an owner, I want one screen for both new and edit, so that I learn it once.
26. As an owner, I want to search or add a customer, so that every order is attributed.
27. As an owner, I want Pickup (At shop / From home) and Delivery (At shop / To home) as two big choices, so that routing is obvious.
28. As an owner, when either leg is home, I want the saved address shown and an optional pickup/delivery charge, so that home jobs are priced.
29. As an owner, I want a required pickup date (defaults to today) and optional time, so that the schedule is right.
30. As an owner, I want the delivery date auto-filled from the longest service ready time, with a hint, so that I rarely set it by hand.
31. As an owner, I want to set the delivery date by hand, remove it, or go back to automatic, so that I stay in control.
32. As an owner, I want date/time chosen via native pickers behind calendar/clock fields (dashed when empty), so that it matches the design and is quick.
33. As an owner, I want to add clothes per service (item steppers or bag weight), so that the bill builds itself.
34. As an owner, I want to override a piece price for this order (with a "rate ₹X" note), so that I can give ad-hoc prices.
35. As an owner, I want an Express switch (adds express % of clothes, editable ₹), so that rush orders price correctly.
36. As an owner, I want a discount in ₹, so that I can give a deal.
37. As an owner, I want a live bill card (clothes + express + fee − discount = total), so that I see the number as I go.
38. As an owner, I want the save button to say the right thing (Choose a customer / Add address first / Schedule pickup / Save order / Save & make bill), so that I know what happens next.
39. As an owner, saving with clothes takes me to the Bill; without clothes returns me Home with a confirming message.
40. As an owner, editing after delivery pushes the bill difference to khata, so that corrections stay honest.

### Count clothes & quick order
41. As an owner, I want a Count-clothes sheet (same tiles/steppers as new order), so that I bill an uncounted order in place.
42. As an owner, I want Count to recompute express from the counted clothes, so that the rush charge is right.
43. As an owner, I want a Quick order needing only the customer, so that I capture a walk-in in 5 seconds.
44. As an owner, I want to add an amount + pieces + "Paid now? Cash/UPI" to a quick order, so that a prepaid walk-in is one step.
45. As an owner, typing a new 10-digit number creates a customer named by the number, so that I add the name later.

## Implementation decisions

- **Modules:** `ui/screens/home/HomeScreen` (+ the home logic helpers), `ui/screens/neworder/NewOrderScreen`,
  `ui/components/OrderCard`, `ui/components/Clothes` (`ClothesState` + `ClothesEditor`, shared by
  new order & Count), and the sheets in `ui/sheets/` (Count, Reschedule, Cancel, Menu, Quick).
- **Statuses:** `created` (To pick up) · `received` · `ready` · `delivered` · `cancelled`.
  Grouping/sorting/badges ported from the prototype (`onDate`, `groupOf`, `pendingOf`, `byTodo`).
- **Next-step routing lives in the screen:** the card calls back `onAct`; Home decides
  created→(Count or markPickedUp), received→(Count or markReady), ready→Collect payment.
- **Auto delivery date:** `pickupDate + max(readyInDays of used services)`; hand-set marks it
  manual (won't move); rescheduling a pickup shifts an auto delivery date by the same day-delta.
- **Native date/time pickers:** `DatePickerDialog` (seeded to app-today, past dates disabled,
  picked date clamped to `minIso`) and `TimePickerDialog` (12-hour UI, stored as 24h "HH:mm",
  displayed as "5:00 PM"); empty fields render with a dashed border.
- **Commands** (repository, all undoable): `markPickedUp`, `markReady`, `saveCount`, `deliver`,
  `cancelOrder`, `reschedule`, `sendBill`, `createQuick`, `saveOrder`. Counters `nextOrder`/`nextCust`
  live on the `shop` row.

### Partial / simplified
- 🔶 A brand-new order's **Quick bill** box is only shown in edit mode; for a new order the quick
  bill comes from the Quick-order sheet (matches the prototype flow, but the repository create-path
  ignores a passed quick amount).
- 🔶 The New-order header shows the order number only when editing (the next number isn't in `LaundryState`).

## Testing decisions

- Good test = observable outcome: after a command, assert the order's status/lines/totals and any
  ledger entries — not the Compose tree.
- **Seam:** `LaundryRepository` (in-memory Room) for `saveOrder`/`saveCount`/`markReady`/`deliver`,
  and `LaundryMath` for the bill/express arithmetic (already covered by `AcceptanceTest`).
- Home's pure grouping/sorting/filter helpers are good candidates to extract and unit-test directly.
- Not yet written: repository/UI tests (see [pending.md](pending.md)).

## Out of scope

Going backwards a status (Ready → Received) from order detail; printing tags on thermal printers;
a delivery-boy view.

## Further notes

The Close-today end-of-day wizard and its Home banner were intentionally removed at the user's
request (the lifecycle actions above cover the same transitions individually).
