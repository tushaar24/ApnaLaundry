# Staff (owner + staff logins) and sync v2

Status: 🚧 Building (decided with the owner on 4 and 9 Nov 2026).

## Problem

One phone is one account, and the shop's data lives on that phone. A shop with a counter
person, ironing staff or a delivery boy can't let them take orders, deliver or collect money
from their own phones. And if it could, today's sync would break: order and customer numbers
are counted on each phone, and conflicts are settled by phone-clock time.

## Solution

The owner adds staff in Settings with a name, a phone number, an optional occupation and
permission switches. Staff install the normal app, log in with their own number and an OTP,
and land straight in the owner's shop. Buttons they aren't allowed to use are hidden, and the
server checks the same permissions. The owner sees each staff member's last 7 days of activity.

Staff is a paid feature. To make several phones per shop safe, sync moves to a server
sequence: push first, then pull, and the last change to reach the server wins.

## Decisions (owner)

| # | Decision |
|---|---|
| 1 | Staff: name (required), phone (required, 10 digits), occupation (optional), permissions. |
| 2 | Entry point: the "Coming later: Staff logins" card in Settings becomes **Staff**. Only the owner sees it. |
| 3 | **Paid**, with no free staff member. When the owner's plan ends, staff see "Sharma Laundry's plan has ended. Ask the owner to renew." and can't use the shop. Their logins and permissions are kept. |
| 4 | Activity: kept 7 days, then deleted. |
| 5 | **Deliver and Collect money are separate switches.** Delivering without Collect money sends the whole bill to the customer's baaki. |
| 6 | No "Taken by" on orders; the activity log covers it. |
| 7 | **Expenses** is a switch, off by default. Without "See earnings", the Expenses screen hides the Profit card. |
| 8 | A phone can be an owner and staff, in either direction, and staff at more than one shop. The switcher has "Your shops" and "Staff at …" groups. |
| 9 | First staff login goes **straight into the owner's shop** and skips all owner setup. |
| 10 | Sync: **last sync to reach the server wins.** A server sequence replaces the time checkpoint. Push first, then pull. Deletes are final. |

## Permissions

Every staff member can always see orders, mark Picked up / Ready, print tags and search customers.

| Key | Switch | Unlocks | Counter | Washing / Ironing | Delivery | Manager |
|---|---|---|---|---|---|---|
| `take_orders` | Take orders | New order, edit clothes / count, add or edit customers | on | off | off | on |
| `deliver` | Mark delivered | Mark delivered (without `collect_money`, the bill goes to baaki) | on | off | on | on |
| `collect_money` | Collect money | Cash / UPI at delivery, Paying now, khata payments, advance | on | off | off | on |
| `discounts_cancel` | Give discounts and cancel orders | Discount field, cancel order | off | off | off | on |
| `delete_orders` | Delete orders | Delete order | off | off | off | off |
| `see_earnings` | See earnings | Earnings tab and totals, Profit on Expenses | off | off | off | on |
| `edit_rates` | Edit rates | Services and prices | off | off | off | on |
| `see_phones` | See customer phone numbers | Numbers, Call and WhatsApp buttons. Off: the server masks numbers before they reach the phone | on | off | on | on |
| `expenses` | Expenses | Add, see and edit expenses | off | off | off | off |

Owner only, never a switch: shop name, bill design and logo, subscription, add or delete
shops, staff management, and the Ask assistant (v1).

## User stories

- As an owner, I add Ramesh (98xxxxxx21, Counter). The switches fill themselves, I save, and I share the app link on WhatsApp.
- As Ramesh, I install the app, log in with my number and an OTP, and I'm in Sharma Laundry's Home with a welcome card telling me what I can do.
- As Ramesh, I don't see buttons I'm not allowed to use. The owner's change to my switches reaches my phone within a minute.
- As an owner, I open Ramesh and see the last 7 days: orders taken, delivered, money collected, and a log by day.
- As an owner, I remove Ramesh. His phone loses the shop at its next sync; his own shop, if he has one, stays.
- As someone who owns a shop and is staff elsewhere, I switch between "Your shops" and "Staff at Gupta Dry Clean" without logging in again.
- As staff, when the owner's plan has ended, I see "Ask the owner to renew", not a paywall.

## Implementation decisions

### Server (courses/backend)

- **Tables**
  - `laundry_staff`: owner `user_id`, `shop_id`, `id`, `phone`, `name`, `occupation`, `permissions` (JSON of booleans), `api_token` (unique), `active`, `last_seen_at`, timestamps.
  - Unique on (`user_id`, `shop_id`, `phone`) among active rows.
- **Auth.** The middleware looks the bearer up in `laundry_users`, then in `laundry_staff`. For staff:
  - `req.laundryUserId` = the owner, so every existing route keeps working.
  - It adds `req.laundryStaff` (id, permissions, `shop_id`).
  - It pins `req.laundryShopId` to the staff member's shop, so `X-Laundry-Shop` can't point elsewhere.
  - A removed or inactive staff member gets 401.
- **Login.**
  - The OTP endpoints are unchanged. New apps send `staffOk: true`.
  - For a phone with active staff rows, the response adds `contexts: [{ kind: 'staff', staffId, token, ownerName, shopId, shopName, permissions }]`.
  - The own account (user + token) is returned only if one already exists. **A staff number with no account doesn't get one created.**
  - Old apps (no `staffOk`) with a staff number and no own account get `code: STAFF_UPDATE_APP`. Numbers that already own a shop log in as before.
- **Staff API** (owner token only; staff tokens get 403):
  - `GET/POST /api/laundry/staff`, `PATCH/DELETE /api/laundry/staff/:id`.
  - `GET /api/laundry/staff/:id/activity` (7 days).
  - `GET /api/laundry/me/contexts`, so the app notices it was added as staff.
- **Plan gate.** Creating staff needs an active plan (403 `PLAN_REQUIRED`). Staff requests while the owner has no plan get 402 `OWNER_PLAN_ENDED`.
- **Sync v2**
  - Every synced table gets `server_seq BIGINT`. Each write takes the next number from a per-shop counter row (`laundry_sync_seq`), locked inside the push transaction, so sequence order = commit order and a pull never skips a row.
  - `GET /sync/pull?afterSeq=N` returns rows with `server_seq > N` and the max seq.
  - `?since=` (time) stays for old apps and the web. A new app's first call sends both, gets the changes since its old checkpoint, and switches to seq.
  - **Conflicts:** the last push to arrive wins. The server stores `updated_at = max(incoming, existing + 1)`, so old apps, which compare `updatedAt`, still converge. A tombstone is final: a later non-deleted push of a deleted row is ignored.
  - **Permission backstop on push** (staff only):
    - services need `edit_rates`; the shop row is dropped.
    - Ledger GOT rows need `collect_money`; an order turning DELIVERED needs `deliver`; expenses need `expenses`.
    - An order tombstone needs `delete_orders`; a changed discount or a cancel needs `discounts_cancel`.
    - Customers' `phone` is kept server-side when `see_phones` is off.
    - Refused rows come back in the push reply (`refused`) with the server copy.
  - The pull masks customer phones for staff without `see_phones`.
- **Order / customer numbers.**
  - `POST /api/laundry/ids/reserve {kind, count}` returns a block from server-owned counters (seeded from the max of the shop row and existing ids).
  - Once a shop has staff, every device on it mints from its reserved pool. The pool holds 20, which covers an offline shift.
- **Activity.**
  - The push carries an additive `activity` array: label, order/customer ref, amount, method, client time.
  - The server stamps the actor from the token, writes `laundry_staff_activity`, and prunes rows older than 7 days on push and in a daily sweep.

### Android

- **Contexts** (like multi-shop): own shops plus staff contexts. Each has its own Room file, token and sync checkpoint. The Settings switcher groups them.
- **Staff mode**
  - A "Ramesh · Sharma Laundry" strip.
  - Buttons the permissions don't allow are hidden.
  - Settings shows only Language, Help, Switch and Log out.
  - No paywall: "Ask the owner to renew" instead.
  - Removed staff → that context is wiped.
- **Owner screens:** the Settings Staff row → Staff list → Add / edit (occupation chips pre-fill the switches) → Staff page (edit, 7-day tiles and log, Remove).
- **Sync v2:** push first, then pull `afterSeq`; pulled rows overwrite unless the local row is dirty; refused rows are restored from the push reply.
- **Analytics:** staff-mode events carry `role: staff` and `owner_shop`. Staff logins send `Staff Logged In` and no sign-up, Meta or install-attribution events.

## Build order

1. [x] Server: sync v2 (seq columns + counter, `afterSeq`, last-arrival-wins, deletes final). Additive; old apps unchanged. *(courses eae74ca — LIVE 10 Nov 2026)*
2. [x] Server: staff table, auth middleware, login contexts, staff API, plan gate, permission backstop, phone masking, id reservation, activity + pruning. *(courses 6b149e5 + bf14d3d: `experiments.staff` for owners in `LAUNDRY_STAFF_PHONES`; LIVE 10 Nov 2026 via deploy/2026-11-10-staff-sync-v2.sh, server 3107162. Activity is derived on the server from each push, not sent by the app.)*
3. [x] Android: sync v2 client. *(20de145)*
4. [x] Android: owner screens (Staff list, add / edit, staff page with activity). *(9ec2fc2)*
5. [x] Android: contexts + staff login + staff mode (permission gating, strip, renew screen, wipe on removal), id pool, analytics role. *(4eb84e2 … cd4c3c1; activity is server-derived, so the app records nothing)*
6. [~] Translations done (27ae5f9). Old-app check: the old-style pull works on the live server (deploy smoke). Left: two-phone test, AAB release of 1.0.20.

## Testing decisions

- Server: unit tests for the permission backstop, last-arrival-wins + `updated_at` bump, delete-final, seq ordering (two concurrent pushes), the contexts login cases (own / staff / both / none, old app), and pruning.
- Android: compile, then on the owner's phone with two accounts (owner + staff number).
- Before release: an older app version against the new server (sync, login).

## Out of scope (v1)

Website staff (phase 2), "Taken by" on orders, staff using the Ask assistant, masked calling
through Vobiz (later: [[staff-number-masking]]).
