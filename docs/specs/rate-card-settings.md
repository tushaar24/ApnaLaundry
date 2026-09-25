# Rate card & settings

Status: ✅ Built · 🔶 one propagation detail simplified

## Problem statement

The owner's prices and shop preferences change over time, but old orders must never change with
them. Editing rates should be tactile and forgiving, and settings should be a short, plain list.

## Solution

A rate-card editor (used both at setup and later from Settings) with per-service pricing, ready
times, add/remove items, and an express %, plus a small Settings screen for shop name, closing time
and reset.

## User stories

### Rate card
1. As an owner, I want a 2-column grid of service tiles plus "+ New service", so that I see all services at once.
2. As an owner, I want each tile to show its mode and ready time ("Per piece · 2 days" / "₹60/kg"), so that I read it fast.
3. As an owner, I want to tap a tile to edit it in place, so that I don't navigate away.
4. As an owner, I want to rename a service inline, so that I use my own words.
5. As an owner, I want "Charge by: Per piece / By weight", so that each service is priced correctly.
6. As an owner, I want Dry Cleaning locked to per-piece with a reason, so that I don't misconfigure it.
7. As an owner, I want "Ready in" chips (Same day / 1 / 2 / 3 days / More), so that delivery dates auto-fill.
8. As an owner, I want tapping a selected ready chip to clear it, so that "no ready time" is easy.
9. As an owner (per piece), I want an item list with editable ₹ boxes, remove ✕, and an "Add item" row, so that I control the menu.
10. As an owner, I want blank-priced items hidden when taking orders, so that unsupported items don't appear.
11. As an owner (by weight), I want a rate/kg and a minimum weight, so that small bags are charged fairly.
12. As an owner, I want to delete a service (with Undo, keeping at least one), so that mistakes are safe.
13. As an owner, I want a "+ New service" form with name suggestions, charge-by and ready-in, so that adding is guided.
14. As an owner, I want to set the express charge %, so that rush pricing is mine.
15. As an owner, I want old orders to keep their service name + prices after I change or delete a service, so that history is honest.

### Settings
16. As an owner, I want to edit my shop name, so that it stays current on Home and bills.
17. As an owner, I want to open the rate card from Settings, so that I can update prices anytime.
18. As an owner, I want to set my closing time (8/9/10/11 PM) with a reminder note, so that day-update nudges are right.
19. As an owner, I want to see my phone number, so that I know which account I'm on.
20. As an owner, I want a "coming later" note (GST · logo · staff logins · Hindi), so that I know what's ahead.
21. As an owner, I want log out / restart, so that I can reset the demo.

## Implementation decisions

- **Modules:** `ui/screens/rates/RatesScreen` (setup + settings modes via a `from` arg),
  `ui/screens/settings/SettingsScreen`. Persistence via `LaundryRepository.upsertService`,
  `deleteService` (soft-delete flag + Undo), `updateShop`.
- **History immutability:** order lines snapshot `serviceName` + price at capture; deleting a
  service sets a `deleted` flag (rows keep rendering their snapshot), so lists/earnings still show
  the old name.
- **Reset:** Settings → log out clears DataStore flags and reseeds the DB to the demo dataset.

### Partial
- 🔶 Adding a new item currently adds it to the **selected** service only; the spec wants a new
  item propagated (blank-priced) to every per-piece service. Easy follow-up.

## Testing decisions

- Good test = persistence + immutability: upsert/delete a service via the repository (in-memory
  Room) and assert an existing order's lines are unchanged and still show the old service name.
- **Seam:** `LaundryRepository`. Not yet written.

## Out of scope

GST, shop logo, staff logins/roles, Hindi/regional languages (all "coming later").

## Further notes

Verified on emulator: setup rate card rendered all four seeded services (Wash & Fold ₹60/kg min 3kg,
Wash & Iron, Iron Only, Dry Cleaning) and saved through to Home.
