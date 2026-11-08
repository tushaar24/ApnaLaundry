# Analytics — CleverTap event tracking plan

One shared taxonomy for **both** clients (the Next.js website in `web/` and the
Android app in `app/`). Same event names and property keys on both, so funnels
in CleverTap are directly comparable across platforms.

Platform is distinguished by a profile/event property `platform` = `web` |
`android`, stamped automatically on every event — never add it by hand.

## Identity

On a successful login both clients call CleverTap's `onUserLogin` with:

| Field      | Value                              |
| ---------- | ---------------------------------- |
| `Identity` | the server `userId`                |
| `Phone`    | `+91` + the 10-digit mobile        |
| `Name`     | the shop name (once known)         |

This ties every subsequent event to one shop owner across sessions and devices.
`Name` is refreshed when the shop name is set or edited. Android calls it as
soon as the login's credentials are saved — before the app counts as logged
in, so the billing check, the experiments fetch and the paywall test never
fire on the profile of whoever was logged in on the phone before — and again
with the shop name after the first sync.

## Core funnels

1. **Activation** — `Screen Viewed {screen:"login"}` → `OTP Requested` →
   `OTP Submitted` → `Logged In` → `Paywall Shown` →
   `Checkout Started` → `Checkout Succeeded` (or `Checkout Unconfirmed`) →
   `Onboarding Step Viewed {step}` ×4 → `Setup Completed` →
   `New Order Started` → `Order Saved` → `Order Delivered`.
2. **Order creation** — `New Order Started` → `Order Saved {has_bill}` →
   `Bill Sent` → `Order Delivered`.
3. **Order fulfilment** — `Order Picked Up` → `Clothes Counted` →
   `Order Marked Ready` → `Order Delivered`.
4. **Cash collection** — `Order Delivered` → `Payment Received` /
   `Old Baaki Added` (chase baaki) → `Reminder Sent`.
5. **Revenue** — `Charged` (CleverTap's reserved revenue event) on every
   delivery, for LTV / revenue reports.
6. **Guide** (Android) — `Tour Started {source}` →
   `Tour Step Viewed {step, part}` → `Tour Part Completed {part}` ×5
   (1 `new_order`, 2 `ready`, 3 `deliver`, 4 `khata`, 5 `earnings`) →
   `Tour Completed`. Drop-off: `Tour Later` (paused on the order's card) /
   `Tour Skipped {step}`. Voice: `sound` on every step view, plus
   `Tour Sound Off` / `Tour Sound On`.

## Events

Custom property keys are `snake_case`. `Charged` uses CleverTap's reserved
`Amount` / `Items` keys.

### Onboarding & auth

| Event | Properties | Fired when |
| ----- | ---------- | ---------- |
| `OTP Requested` | `phone_type` (`real`\|`review`), `is_resend` | user taps Continue / Resend on the phone step |
| `OTP Request Failed` | `reason` | request-otp returned an error |
| `OTP Submitted` | — | user taps Verify |
| `OTP Verification Failed` | `reason`, `attempts_remaining?` | verify-otp failed |
| `Logged In` | `is_new_user`, `needs_setup` | verify-otp succeeded (also fires `onUserLogin`) |
| `Onboarding Step Viewed` | `step` (`intro`\|`name`\|`services`\|`bill`), `step_index` (0–3) | each onboarding step shown (Android), incl. a resumed step |
| `Logo Uploaded` | — | shop logo uploaded (onboarding or Settings) (Android) |
| `Test Bill Sent` | `template`, `from` (`onboarding`\|`settings`) | "Test on WhatsApp" sample bill (Android) |
| `Setup Completed` | `services_count`, `express_pct` | last onboarding step finished |
| `Logged Out` | — | user logs out from Settings |

### Order creation & lifecycle

| Event | Properties | Fired when |
| ----- | ---------- | ---------- |
| `New Order Started` | `source` (`home`\|`empty_home`\|`customer`), `is_edit` | New/Edit order screen opened |
| `New Order Abandoned` | `entry_point`, `is_edit`, `stage` (`customer`\|`clothes`\|`save`), `customer_set`, `customer_added`, `customer_form_opened`, `clothes_added`, `pieces`, `kg`, `total`, `seconds_on_screen` | left New/Edit order without saving (Android); `stage` = furthest step: no customer → no clothes → never tapped save. Not sent on rotation or if the app is killed in the background |
| `Order Saved` | `order_id`, `is_edit`, `has_bill`, `pickup`, `delivery`, `express`, `discount`, `fee`, `pieces`, `kg`, `services_count`, `total`, `quick_bill`, `orders_count` (Android, new orders only: orders after this one, deleted not counted) | order created or edited |
| `Order Picked Up` | `order_id` | mark picked up (already-counted order) |
| `Clothes Counted` | `order_id`, `next_status`, `total` | Count-clothes sheet saved |
| `Order Marked Ready` | `order_id` | mark ready (already-counted order) |
| `Order Delivered` | `order_id`, `total`, `amount_received`, `method`, `to_khata`, `from_advance` | delivery + payment collected |
| `Order Cancelled` | `order_id`, `reason` | pickup cancelled |
| `Order Rescheduled` | `order_id`, `kind` (`pickup`\|`delivery`), `notify` | reschedule saved |

### Payments & khata

| Event | Properties | Fired when |
| ----- | ---------- | ---------- |
| `Payment Received` | `amount`, `method` (`cash`\|`upi`), `type` (`delivery`\|`prepay`\|`khata`), `customer_id`, `order_id?`, `from?` (Android 1.0.19, a prepay from a "Paying now?" card: `order_detail` = the order page, `new` = the bill right after creating, else where the bill was opened from), `split?` (true = one part of a part-cash, part-UPI payment before delivery, Android 1.0.19) | any money-in |
| `Paying Now Split Tapped` | `order_id`, `from` (`order_detail` or the bill's entry) | "Part cash, part UPI" on a "Paying now?" card — the split sheet opens (Android 1.0.19) |
| `Old Baaki Added` | `amount`, `customer_id` | pre-app debt recorded |
| `Charged` | `Amount` (bill total), `payment_method`, `order_id`, `Items` (per-service) | on every `Order Delivered` — revenue event |

### Customers, bills, rates, engagement

| Event | Properties | Fired when |
| ----- | ---------- | ---------- |
| `Customer Added` | `source` (`order`\|`list`), `with_old_baaki` | new customer saved |
| `Reminder Sent` | `customer_id`, `amount` | WhatsApp baaki reminder |
| `Bill Sent` | `order_id`, `channel` (`whatsapp`), `mode` (`image` = picture into the chat \| `text` = wa.me message + bill link, the number isn't a saved contact; 1.0.18+), `contacts` (1.0.18+: `granted` \| `granted_now` \| `denied_now` \| `denied` \| `off` — see "Send-bill contacts ask") | send bill |
| `Bill Viewed` | `order_id` | customer-facing bill preview opened |
| `Bill Downloaded` | `order_id`, `from` (`bill`\|`order_detail`) | bill PDF opened in the share sheet (Android) |
| `Tags Printed` | `order_id`, `tags`, `mode` (`piece`\|`order`), `paper` (`58mm`\|`80mm`), `from` (`bill`\|`order_detail`\|`menu`) | laundry tags printed on a Bluetooth printer (Android) |
| `Tags Print Failed` | `order_id`, `reason` (`connect`\|`write`\|`bluetooth_off`\|`permission`\|`no_bluetooth`), `from` | tag printing failed (Android) |
| `Bill Printed` | `order_id`, `paper` (`58mm`\|`80mm`), `from` (`bill`\|`order_detail`\|`menu`) | the customer's bill printed on the Bluetooth printer (Android 1.0.18, print/ReceiptRender) |
| `Bill Print Failed` | `order_id`, `reason` (as Tags Print Failed), `from` | bill printing failed (Android 1.0.18) |
| `Combined Bill Sent` | `customer_id`, `orders`, `total`, `to_pay`, `channel` (`whatsapp`), `mode` (`image`\|`text`, 1.0.18+), `contacts` (as `Bill Sent`, 1.0.18+) | many orders of one customer sent as one bill |
| `Summary Shared` | `period` | earnings day/week/month summary shared (Android ≤1.0.18; the share button is gone in 1.0.19) |
| `Rates Opened` | `from` (`home`\|`settings`) | rate card opened |
| `Service Added` | `mode` (`PIECE`\|`WEIGHT`) | new service saved |
| `Service Edited` | `service_id` | service edited |
| `Service Deleted` | `service_id` | service deleted |
| `Screen Viewed` | `screen` | each main screen is shown (`login`, `setup`, `home`, `customers`, `earnings`, `settings`, `new_order`, `order_detail`, `customer_khata`, `combined_bill`, `bill`, `rates`) |
| `Earnings Period Changed` | `period` (`today`\|`week`\|`month`) | period toggle on Earnings |
| `Expenses Entry Tapped` | `from` (`settings`\|`earnings`\|`earnings_card`) | an Expenses entry point tapped — the Settings row, the Earnings top-right button, the Earnings Profit card (Android 1.0.19) |
| `Expenses Opened` | `from` (as Entry Tapped, or `push` = a `mylaundry://expenses` link), `expenses` (how many the shop has) | the Expenses screen shown (Android 1.0.19; also `Screen Viewed` `expenses`) |
| `Expenses Period Changed` | `period` (`today`\|`week`\|`month`\|`all`\|`custom`) | period chips on Expenses (Android 1.0.19) |
| `Expenses Custom Date Set` | `which` (`from`\|`to`), `days` (days the period covers) | a Custom period's From / To picked (Android 1.0.19) |
| `Expense Add Tapped` | `expenses` (how many the shop has) | "+ Add expense" tapped (Android 1.0.19) |
| `Expense Row Tapped` | `category` (`rent`\|`salary`\|`electricity`\|`supplies`\|`fuel`\|`water`\|`packing`\|`transport`\|`repairs`\|`other`, or the name of a category the owner added), `custom` (bool), `amount` | an expense in the list tapped to change or delete it (Android 1.0.19) |
| `Expense Page Shown` | `mode` (`new`\|`edit`) | the add / change expense page shown (Android 1.0.19) |
| `Expense Date Changed` | `mode`, `days_ago` | the sheet's date changed (Android 1.0.19) |
| `Expense Category Tapped` | `mode`, `action` (`add` = "+ Add category" \| `change` = the chosen category's box) | the page's category button tapped — opens the half-screen category list (Android 1.0.19) |
| `Expense Category Picked` | `mode`, `category` (`rent`\|`salary`\|`electricity`\|`supplies`\|`fuel`\|`water`\|`packing`\|`transport`\|`repairs`\|`other`, or the name of a category the owner added), `custom` (bool), `searched` (bool) | a category tapped in the list (Android 1.0.19) |
| `Expense New Category Tapped` | `mode`, `from` (`button` = "+ New" beside the search — opens the name box \| `search_row` = "Add … as a new category" — adds at once), `typed` (bool — the search had text) | a new category asked for in the list (Android 1.0.19) |
| `Expense New Category Cancelled` | `mode`, `typed` (bool — a name was in the box) | Cancel on the new-category name box — back to the list (Android 1.0.19) |
| `Expense Category Added` | `mode`, `name`, `from` (as New Category Tapped) | a new category added — the name the owner typed (Android 1.0.19) |
| `Expense Category Edit Tapped` | `mode`, `category`, `expenses` (how many use it) | the edit button on one of the shop's own categories tapped — the common ones have none (Android 1.0.19) |
| `Expense Category Edit Cancelled` | `mode`, `category` | Cancel on the edit-category box (Android 1.0.19) |
| `Expense Category Renamed` | `mode`, `from`, `to`, `expenses` (moved), `merged` (bool — the new name was an existing category) | a category renamed; all its expenses carry the new name (Android 1.0.19) |
| `Expense Category Delete Tapped` | `mode`, `category`, `expenses` | "Delete category" tapped — the confirm opens (Android 1.0.19) |
| `Expense Category Delete Cancelled` | `mode`, `category` | the delete confirm's Cancel / a tap outside (Android 1.0.19) |
| `Expense Category Deleted` | `mode`, `category`, `expenses` (moved to Other) | a category deleted; its expenses move to Other (Android 1.0.19) |
| `Expense Category Sheet Closed` | `mode`, `how` (`picked`\|`added`\|`dismissed`), `query` (what was in the search box, "" = nothing) | the category list closed (Android 1.0.19) |
| `Expense Save Blocked` | `mode`, `missing` (`amount` — the only thing Save needs) | the greyed Save tapped (Android 1.0.19) |
| `Expense Added` | `category` (`rent`\|`salary`\|`electricity`\|`supplies`\|`fuel`\|`water`\|`packing`\|`transport`\|`repairs`\|`other`, or the name of a category the owner added), `custom` (bool), `amount`, `has_note` (bool), `days_ago`, `category_picked` (bool — false: no category chosen, saved as `other`) | a new expense saved (Android 1.0.19) |
| `Expense Edited` | as `Expense Added` | an expense changed and saved (Android 1.0.19) |
| `Expense Page Closed` | `mode`, `amount_entered` (bool), `category_picked` (bool) | the page left without Save / Delete — back arrow or the phone's Back (Android 1.0.19) |
| `Expense Delete Tapped` | `category`, `amount` | "Delete expense" tapped — the confirm dialog opens (Android 1.0.19) |
| `Expense Delete Cancelled` | `category`, `amount` | the confirm dialog's Cancel, or a tap outside it (Android 1.0.19) |
| `Expense Deleted` | `category`, `amount` | an expense deleted (Android 1.0.19) |
| `Order Search Opened` | — | search opened on Home |

### Staff — the owner's side (Android 1.0.20, docs/specs/staff.md)

Shown only when the server sends `experiments.staff` (LAUNDRY_STAFF_PHONES). The staff members' own events are in the next section.

| Event | Properties | When |
|---|---|---|
| `Staff Entry Tapped` | `from` (`settings`), `staff` (how many, -1 = not loaded) | the Settings → Staff row tapped |
| `Staff Opened` | `from`, `staff` | the Staff list shown |
| `Staff Plan Required Shown` | `from` | the owner has no plan: Staff shows "needs a MyLaundry plan" |
| `Staff Add Tapped` | `staff` | "+ Add staff" tapped (opens the paywall without a plan) |
| `Staff Row Tapped` | `staff_id` | a staff member tapped in the list |
| `Staff Form Shown` | `mode` (`new`\|`edit`) | the add / edit staff page shown |
| `Staff Occupation Picked` | `mode`, `occupation` (`counter`\|`washing`\|`ironing`\|`delivery`\|`manager`\|`other`) | a work chip tapped (fills the switches) |
| `Staff Permission Toggled` | `mode`, `permission` (`take_orders`\|`deliver`\|`collect_money`\|`discounts_cancel`\|`delete_orders`\|`see_earnings`\|`edit_rates`\|`see_phones`\|`expenses`), `on` | a switch flipped |
| `Staff Added` | `staff_id`, `occupation`, `permissions` (comma-separated keys), `permissions_on` | a staff member added |
| `Staff Edited` | as `Staff Added` | a staff member's name / work / switches saved |
| `Staff Save Failed` | `mode`, `code` (`PLAN_REQUIRED`\|`PHONE_TAKEN`\|`OWN_NUMBER`\|`PHONE_INVALID`\|`STAFF_LIMIT`\|`NETWORK`\|`name`\|`phone` = greyed Save tapped) | save refused / failed |
| `Staff Form Closed` | `mode`, `name_entered`, `phone_entered` | the form left without saving |
| `Staff App Link Shared` | `staff_id`, `from` (`added`\|`member`) | "Share the app on WhatsApp" |
| `Staff Activity Viewed` | `staff_id`, `rows` | a staff member's page with the 7-day log loaded |
| `Staff Remove Tapped` | `staff_id` | "Remove staff" tapped (confirm opens) |
| `Staff Remove Cancelled` | `staff_id` | the confirm's Cancel / tap outside |
| `Staff Removed` | `staff_id` | removed — their phone loses the shop |

### First customer on New order — A/B (Android 1.0.15, 2026-10-24)

How a brand-new shop adds its first customer. Assigned by the backend
(`GET /api/laundry/experiments` → `customerFlow`, see
`docs/deploy/2026-10-24-customer-flow-ab.md`) to accounts whose first login is
on Android 1.0.15+ after `LAUNDRY_CUSTOMER_FLOW_START`, about a third each.
Everyone else (older accounts, older apps, the website) keeps the 1.0.14 flow.

| `customer_flow` | What the owner sees |
| --- | --- |
| `v1_auto_sheet` | New order opens the New customer sheet (name, phone, optional address) by itself — with the guide on too; the guide's tip shows inside the sheet |
| `v2_no_address` | The sheet opens by itself with only name + phone. Home pickup / delivery asks for the address in its own "Customer's address" sheet: when a customer is set while home is on, when the route switches to home, from "Add" next to Address, or the greyed "Add address first" button |
| `v3_tap_to_add` | The sheet never opens by itself; the owner taps "Add new customer" (with the guide, its card points there) |
| *(absent)* | not in the test: the sheet opens by itself unless the guide is on |

Profile property **`Customer_Flow`** (server + app) and event property
**`customer_flow`** on **every** Android event of a test account, so any
funnel or raw export splits by it. Main funnel: `New Order Started` →
`Customer Sheet Opened` → `Customer Sheet Closed {how: saved}` (=
`Customer Added`) → `Order Saved`; drop-off detail below.

| Event | Properties | Fired when |
| ----- | ---------- | ---------- |
| `Customer Sheet Opened` | `ctx` (`order`\|`list`), `mode` (`new`\|`edit`), `trigger` (`auto`\|`add_button`\|`add_with_query`\|`address_card`\|`list`\|`list_with_query`\|`list_empty` (the empty Customers screen's button, 1.0.16; `list` = the bottom bar's Add customer)\|`customer_page`\|`other`), `address_shown`, `address_needed`, `prefilled` (`none`\|`name`\|`phone`), `customers` (saved so far), `guide_on` | the customer sheet appears (all versions of the flow) |
| `Customer Sheet Field Edited` | `field` (`name`\|`phone`\|`address`\|`old_khata`), `ctx`, `seconds_since_open` | first typing in that field (once per field per sheet) |
| `Customer Sheet Field Focused` | `field`, `ctx`, `seconds_since_open` | a field tapped into, typed in or not (once per field per sheet; 1.0.16) — "tapped Name but never typed" = keyboard trouble |
| `Customer Phone Trimmed` | `digits`, `lead` (`91`\|`0`\|`other`) | more than 10 digits typed/pasted in Phone (it keeps the first 10 — "+91 98…" saves wrong) |
| `Customer Contacts Tapped` | `permission` (`granted`\|`asking`) | "Choose from contacts" |
| `Contacts Permission Asked` | `from` (`customer_sheet`\|`bill_send`), `screen` (`order`\|`list` for the customer sheet; `bill`\|`order_detail`\|`combined_bill`\|`assistant` for a bill send) — 1.0.18+ | the system contacts permission dialog requested |
| `Contacts Permission Answered` | `granted`; 1.0.18+: `from`, `screen` (as Asked), `ms` (request → answer; a few ms = Android refused by itself, no dialog shown) | the contacts permission dialog answered (customer sheet: no → Android's own picker; bill send: the bill goes either way) |
| `Customer Contact Picked` | `source` (`in_app`\|`system`), `ok` (had a 10-digit mobile), `already_saved` | a contact tapped |
| `Customer Contacts Closed` | `source`, `contacts` (listed, in_app), `searched` | the contact list left without picking |
| `Customer Duplicate Shown` | `ctx` | "Already saved as …" (the number is a customer already) |
| `Customer Save Blocked` | `reason` (`name`\|`phone_empty`\|`phone_short`\|`duplicate`\|`address`), `ctx` (`order`\|`list`\|`address_sheet`), `name_filled`, `phone_digits`, `address_filled` | the greyed "Save customer" / "Save address" tapped |
| `Customer Sheet Closed` | `ctx`, `mode`, `trigger`, `how` (`saved`\|`used_existing`\|`dismissed`), `seconds_on_screen`, `name_filled`, `phone_digits`, `address_shown`, `address_filled`, `contacts_tapped`, `contact_picked`, `blocked_taps`, `missing` (what still blocked Save on a dismiss, else `none`), `method` (`typed`\|`contacts`, on save); 1.0.16+: `interacted` (did anything at all), `fields_focused` / `fields_edited` (in order, e.g. `name,phone`), `first_action_secs` (-1 = nothing), `keyboard_shown`, `duplicate_shown`, `steps` (the whole trail, ≤ 300 chars: `focus_<field>`, `type_<field>`, `contacts`, `contact_picked`, `contact_none`, `contact_no_mobile`, `dup_shown`, `use_existing`, `save_blocked_<reason>`, `save` — e.g. `focus_name>type_name>focus_phone>type_phone>save_blocked_phone_short`) | the sheet goes away — saved, "Use this" on a duplicate, or back / swipe / tap outside |
| `Address Sheet Opened` | `trigger` (`route`\|`customer_saved`\|`customer_picked`\|`address_card`\|`save_button`), `had_address`, `guide_on` | v2's address sheet appears |
| `Address Sheet Closed` | `trigger`, `how` (`saved`\|`dismissed`), `seconds_on_screen`, `edited`, `filled`, `blocked_taps`; 1.0.16+: `focused` (the field tapped into), `keyboard_shown` | v2's address sheet goes away |
| `Customer Selected` | `from` (`recent`\|`search`), `query_length` | an existing customer picked on New order |
| `Customer Search Used` | — | New order's customer search typed in (once per screen) |
| `Order Route Changed` | `leg` (`pickup`\|`delivery`), `to` (`shop`\|`home`), `customer_set`, `has_address` | At shop ↔ home switched on New order |
| `Order Save Blocked` | `reason` (`customer`\|`address`\|`clothes`), `is_edit` | the greyed bottom button ("Choose a customer" / "Add address first" / "Add clothes first") tapped |

`New Order Abandoned` also carries `customer_sheet_opens`, `save_blocked_taps`,
`address_needed`, `address_sheet_opened` (1.0.15+). The guide adds the step
`order_address_form` (v2's address sheet tip); its `order_route` and
`order_customer_form` cards word themselves for the variant.

### Services setup — A/B (Android 1.0.17, 2026-10-28)

How a brand-new shop gets its services. Assigned by the backend
(`GET /api/laundry/experiments` → `servicesSetup`, see
`docs/deploy/2026-10-28-services-setup-ab.md`) to accounts whose first login
is on Android 1.0.17+ after `LAUNDRY_SERVICES_SETUP_START`, 50/50. Everyone
else (older accounts, older apps, the website) keeps the sample list.

| `services_setup` | What the owner sees |
| --- | --- |
| `samples` | Setup step 2 is the seeded sample list with sample prices (as before) |
| `scratch` | Setup is name → bill (no services step); the sample services are removed; New order's Clothes section shows "No services yet · Add service", then a "+ Add service" tile and a folded "+ Add cloth" row; the simple "Add new service" form (name, per piece / per kg, clothes + prices); the last service can be deleted; **no guide** |
| *(absent)* | not in the test: the sample list |

Why: in the 28 Oct readout, owners who changed anything on the sample list in
setup made a first order at 61% against 36% for owners who tapped Next
untouched, in every build and variant (memory: setup-services-touch).

Profile property **`Services_Setup`** (server + app) and event property
**`services_setup`** on **every** Android event of a test account. Compare the
groups on `Onboarding Step Viewed` (scratch has no `services` step) →
`Setup Completed {services_count}` (0 for scratch) → `New Order Started` →
`Service Added {mode}` (scratch: the first service, from New order) →
`Order Saved` → `Checkout Succeeded`. `Service Deleted` does not fire for the
quiet removal of the samples. The scratch group fires no `Tour *` events.

### Send-bill contacts ask — server switch (Android 1.0.18, 2026-11-04)

Whether the first WhatsApp bill send asks for the Contacts permission (to send
the picture to saved numbers and a wa.me text + bill link to unsaved ones).
Every account gets a group from `GET /api/laundry/experiments` →
`billContactsAsk`; `LAUNDRY_BILL_CONTACTS_ASK_PCT` on the backend is the share
that gets `ask` (0 = off for everyone, 100 = on for everyone), on a stable hash
of the user id. See `docs/deploy/2026-11-04-bill-contacts-ask.md`.

| `bill_contacts_ask` | What the owner sees |
| --- | --- |
| `ask` | The first bill send on the phone shows Android's Contacts dialog; a "no" is never asked again there |
| `no_ask` | Never asked at a bill send; bills go as a picture, as in 1.0.17 |
| *(absent)* | An app that never got the group (or older than 1.0.18): no ask |

A permission already given ("Choose from contacts") is used in both groups.

Profile properties **`Bill_Contacts_Ask`** (server + app) and
**`Contacts_Permission`** (`granted` \| `not_granted`, app: every app start and
after each answer); event property **`bill_contacts_ask`** on **every**
Android 1.0.18+ event. `contacts` on `Bill Sent` / `Combined Bill Sent`:

| `contacts` | Meaning | Lookup used |
| --- | --- | --- |
| `granted` | already allowed (earlier send, or "Choose from contacts") | yes |
| `granted_now` | asked at this send: yes | yes |
| `denied_now` | asked at this send: no | no — picture |
| `denied` | asked at an earlier send on this phone, not allowed: not asked again | no — picture |
| `off` | the switch says don't ask (`no_ask` / not fetched) and not allowed | no — picture |

How to read it (`ask` vs `no_ask`, same sign-up window, 1.0.18 events only):
the ask itself → `Contacts Permission Asked {from: bill_send}` →
`Contacts Permission Answered {from: bill_send, granted, ms}` → `Bill Sent
{contacts: granted_now|denied_now}` (the bill still goes after the dialog).
Harm checks per group: `Bill Sent` per shop-day, shops that send a 2nd bill,
`New Order Started` → `Order Saved` after the first send, next-day return,
`Tour *` drop at the bill step for new shops, `Checkout Succeeded`. Delivery
side: `Bill Sent {mode}` — `text` = an unsaved number that would have landed on
WhatsApp's picker as a picture.

### Guide & support (Android)

The guide walks a new shop through its first order end to end: New order →
Mark ready → Mark delivered + payment → Khata → Earnings, every card read out
loud (sound on by default). Parts 2–3 follow that order's real status, so they
can finish hours later; `Tour Part Completed` fires whichever way the status
changed.

**A/B (2026-10-13, Android 1.0.10):** the guide is a 50/50 test assigned by the
backend (`GET /api/laundry/experiments`, `docs/deploy/2026-10-13-guide-ab.md`).
Profile property **`Guide_Variant`** = `guide` | `no_guide` (set by the server
and the app) — split activation funnels (Order Saved, Order Delivered,
`orders_count`) by it. Only the `guide` group fires `Tour *` events.

| Event | Properties | Fired when |
| ----- | ---------- | ---------- |
| `Tour Started` | `source` (`auto`\|`settings`) | a new shop's first Home (no orders yet), or Settings → How to use the app |
| `Tour Step Viewed` | `step`, `part` (1–5), `part_name`, `sound` | a guide card / in-sheet tip is shown. `step`: `home_new_order`, `order_customer`, `order_customer_form`, `order_route`, `order_dates`, `order_clothes`, `order_save`, `bill_send`, `bill_next`, `status_picked_up`, `status_ready`, `ready_sheet`, `whatsapp_update`, `status_deliver`, `pay_sheet`, `khata_tab`, `khata_tile`, `khata_customer`, `earnings_tab`, `earnings_sales`, `earnings_money`, `tour_done` |
| `Tour Part Completed` | `part` (1–5), `part_name` (`new_order`\|`ready`\|`deliver`\|`khata`\|`earnings`) | order saved / order ready / order delivered / khata seen / Finish |
| `Tour Later` | `step`, `part` | "Later" on the order's card — hidden until the app is opened again |
| `Tour Skipped` | `step`, `part` | "Skip guide" — the guide ends (Settings can replay it) |
| `Tour Completed` | — | Finish on the last card |
| `Tour Sound Off` / `Tour Sound On` | `step` | the guide's sound button (remembered on the phone) |
| `Support Contacted` | `method` (`call`\|`whatsapp`), `from` (`settings`\|`tour_end`) | Settings → Need help? Call us / WhatsApp us, or the guide's last card |

### Multi-shop (Android 1.0.11, paid)

An owner can run several shops: 2 are free, a 3rd and more need a plan. The
paywall opens straight from New order when the free orders (counted across all
shops) run out — `Paywall Shown {from}`: `free_orders` | `plan_ended` (more
than 2 shops, no plan) | `add_shop` | `settings`. Each shop has its own
customers, khata, orders, bills and services; the app shows one at a time.
Profile property **`Shop_Count`** = how many shops the owner has.

| Event | Properties | Fired when |
| ----- | ---------- | ---------- |
| `Add Shop Tapped` | `from` (`home`\|`settings`), `has_plan`, `free` | shops sheet / Settings "Add another shop". `free` = the 2nd shop (2 shops are free); neither → paywall `from: add_shop` |
| `Shop Added` | `shop_count` | a shop was created (server-checked plan) |
| `Shop Switched` | `shop_count` | the owner moved to another shop |
| `Shop Deleted` | `shop_count` (left after it) | 🗑 on an extra shop's card → Delete shop (the main shop can't be deleted) |
| `Earnings Shops Changed` | `selection` (`this`\|`all`\|`some`), `shops` | Earnings shop chips |

### Assistant — "Ask" (Android 1.0.16, 2026-10-25)

Sarvam AI through `/api/laundry/assistant`. It is shown only where the server's `/config` says it's on. Details: `docs/deploy/2026-10-25-assistant.md`. The owner's words are never sent to CleverTap; only lengths and tool names are.

| Event | Properties | Fired when |
| ----- | ---------- | ---------- |
| `Assistant Opened` | `from` (`nav` = the bottom bar — `Bottom Bar Tapped` says which tab \| `push`), `first_order` (the shop has no order yet: Ask shows only "create your first order") | the Ask screen opens (bottom bar, or `mylaundry://assistant`) |
| `Assistant Speak Toggled` | `on` | the 🔊 at the top: answers read aloud on / off |
| `Assistant Answer Replayed` | — | the 🔊 next to an answer |
| `Assistant New Chat` | `messages` | "New chat" cleared the conversation |
| `Assistant Asked` | `via` (`text`\|`voice`\|`chip`), `chars` | a question is sent |
| `Assistant Answered` | `rounds`, `tools` (comma-separated tool names), `cards`, `ms` | the reply arrived |
| `Assistant Failed` | `reason` (`network`\|`server`\|`limit`\|`off`\|`login`) | no reply (the owner sees why) |
| `Assistant Voice` | `seconds`, `ok`, `reason` (`short`\|`empty`\|`mic`\|`network`\|`server`\|`limit`\|`off`) | a voice note was stopped and turned (or not) into words |
| `Assistant Card Shown` | `card` (`new_order`\|`send_bill`\|`status_update`\|`reminder`\|`whatsapp_message`\|`mark_order`) | a reply put a card to tap |
| `Assistant Card Used` | `card` | its button was tapped: order saved, WhatsApp opened, status changed, payment recorded… |
| `Assistant Nudged` | — | a reply said to tap a button but no tool made a card; it was sent back once to act (or just answer) |

### Language (Android 1.0.16, 2026-10-25)

The app comes in English + 10 Indian languages (`docs/i18n.md`). The choice is saved on the phone, not the account. Profile property **`Language`** = the code (`en`, `hi`, `ta`…).

| Event | Properties | Fired when |
| ----- | ---------- | ---------- |
| `Language Changed` | `from`, `to` (codes), `where` (`login_first` = the picker on the first open \| `login_open` = the picker that opens with the login screen after that \| `login` = the chip at the top right \| `settings` \| `link` = a language link opened the app \| `install_link` = the first open after a Play install from a language link — docs/language-links.md) | a language was picked (also the first pick) |
| `Language Dialog Shown` | `where` (`login_first`\|`login_open`\|`login`\|`settings`), `current` | the language dialog appears (every login screen opens with it) |
| `Language Dialog Dismissed` | `where`, `current` | closed without a pick (back / tap outside) |

### Bottom bar, Home and new orders (Android 1.0.16, 2026-10-25)

| Event | Properties | Fired when |
| ----- | ---------- | ---------- |
| `Bottom Bar Tapped` | `button` (`orders`\|`customers`\|`earnings`\|`settings`\|`ask`\|`new_order`\|`add_customer`), `on` (the tab it was tapped on), `style` (`pills` = Ask + New order / Add customer over the tabs \| `notched` = Ask in the middle \| `flat` = no Ask) | any bottom-bar button |
| `Demo Video Opened` | `from` (`home`) | Home's "Demo" chip (YouTube, in the app; link from `/api/laundry/app-config`) |
| `Demo Video Closed` | `from`, `seconds` (open ≈ watched) | its dialog closed |
| `First Order Help Shown` | — | back on New order before the shop's first order: "Confused? Watch the video" |
| `First Order Help Choice` | `choice` (`stay`\|`leave`) | its buttons |
| `Order Created Dialog Closed` | `order_id`, `how` (`auto` after 3 s \| `ok` \| `outside`) | the "Order created!" dialog (with its sound) after every new order |
| `Bill Design Saved` | `template` (`classic`…`invoice`), `from` (`settings`\|`onboarding`), `changed` | Bill design: Save (Settings) / Start taking orders (setup) |

The underlying actions still fire their usual events: `Order Saved`, `Bill Sent`, `Reminder Sent`, `Order Marked Ready`, and so on.

### Billing / paywall (A/B: `trial_2` \| `free_50`)

The variant is assigned by the backend (Firebase-weighted, stable per user) and
returned by `GET /billing/status`. The **client** fires the UI funnel below. The **backend** fires the
money-confirmed events from the Razorpay webhook (server-side CleverTap), so
revenue events can't be spoofed or lost if the app closes.

| Event | Source | Properties | Fired when |
| ----- | ------ | ---------- | ---------- |
| `Paywall Shown` | client | web: `default_plan`; Android: `from` (`free_orders`\|`plan_ended`\|`add_shop`\|`settings`\|`hard_trial`\|`first_order`), `default_plan`, `order_count` (orders not deleted), `amount` (default plan, paise), `trial` (₹2 trial offered), `video` (intro video on it) | paywall/trial screen shown with its plans (not the Settings status row) |
| `Paywall Closed` | client | `from`, `how` (`close_button`\|`back`), `seconds_on_screen` | Android: left a shown paywall without paying (✕ or Back), once per paywall |
| `Paywall Check Failed` | client | `from` (`hard_trial`) | Android: the billing check before the hard-trial paywall failed after retries (offline) — the owner sees "Try again" |
| `Paywall FAQ Opened` | client | — | Android: the paywall's FAQ opened |
| `Paywall Video Started` | client | `muted` | Android: the paywall video started playing (first time) |
| `Paywall Video Unmuted` / `Paywall Video Completed` | client | — | Android: sound turned on / the video played to the end once |
| `Plan Selected` | client | `variant`, `plan` | user taps a plan |
| `Checkout Started` | client | `variant`, `plan`, `amount`, `trial_amount` | UPI intent opened |
| `Checkout Succeeded` | client | `variant`, `plan` | returned from UPI app as success (optimistic) |
| `Checkout Unconfirmed` | client | `plan` | Razorpay said success but the server didn't confirm in time (Android) |
| `Checkout Failed` | client | `variant`, `plan`, `reason` | cancelled / failed |
| `Subscription Cancel Requested` | client | `plan` | user taps cancel |
| `Subscription Activated` | server | `plan`, `variant` | `subscription.authenticated`/`activated` webhook |
| `Subscription Charged` | server | `plan`, `amount`, `variant` | `subscription.charged` webhook (recurring success) |
| `Subscription Payment Failed` | server | `plan`, `variant` | `subscription.pending` (will retry) |
| `Subscription Halted` | server | `plan`, `variant` | `subscription.halted` (retries exhausted) |
| `Subscription Cancelled` | server | `plan`, `variant` | `subscription.cancelled` |

Android also keeps profile properties `orders_count`, `last_order_date` and
`first_order_date` (ISO dates) current on every new order. On login it sets
`MSG-whatsapp` = true (the login screen's terms line includes "and to get
updates on WhatsApp"), and on a new account's first login `signup_date` (a
CleverTap date) — both for the activation journeys.

**Default-plan A/B (web, since 2026-10-11).** The web paywall pre-selects
Yearly or Monthly 50/50, stable per shop (hash of the user id —
`web/src/data/paywallExperiment.ts`). `Paywall Shown`, `Checkout Started`,
`Checkout Succeeded` and `Checkout Failed` carry `default_plan`
(`annual`\|`monthly`); the profile carries `Default_Plan_Selected`, which also
splits the server-side `Subscription Activated`.

**Paywall test (Android 1.0.12+, from 2026-10-14).** Brand-new accounts only
(created after `LAUNDRY_PAYWALL_TEST_START` from app 1.0.12+; anyone who logged
in before never). Profile property **`Paywall_Variant`** = `hard_trial` (paywall
right after login, no close, 1-day ₹2 trial then ₹199/month) | `free1` (1 free
order, paywall after it + the guide). Both: guide on, Monthly-only ₹199, dark
video paywall. `Paywall_Variant` is set by the server (at login and every app
start) and by the app (from `/billing/status`), and every Android event of a
test account carries **`paywall_variant`** (`hard_trial` | `free1`), so event
exports split without a profile lookup. Test accounts get `Guide_Variant =
guide` (forced) — leave them out of the guide A/B. Funnels:
- hard_trial: `Logged In {is_new_user}` → `Paywall Shown {from: hard_trial, trial: true}`
  (`Paywall Check Failed` if offline) → `Checkout Started {trial_amount: 200}` →
  `Checkout Succeeded` / `Payment Retry Shown` → `Setup Completed` → `Order Saved`.
- free1: `Logged In` → `Setup Completed` → `Order Saved {orders_count: 1}` →
  `Tour Completed` / `Tour Skipped` → `Paywall Shown {from: first_order}` →
  `Checkout Started` or `Paywall Closed` → later `Free Orders Over Shown` →
  `Paywall Shown {from: free_orders}`.
Details: `docs/deploy/2026-10-14-paywall-test.md`.

**Free-orders A/B (Android, from 2026-10-14).** New Android shops (created
after the backend's `LAUNDRY_FREE_ORDERS_TEST_START`) get 3 or 5 free orders,
50/50, stable per shop (hash of the user id, `free_orders_v1`). The server
sets profile property **`Free_Orders_Variant`** = `free_3` | `free_5` at
sign-up and on every app start; shops outside the test don't have it. Only
1.0.10+ applies the number, so analyse shops first seen on 1.0.10+. The
paywall is `Free Orders Over Shown {orders}` / `Paywall Shown {from: free_orders, order_count}`.
Details and the analysis plan: `docs/deploy/2026-10-14-free-orders-ab.md`.

Profile property `subscription_status` (+ `plan`) is kept current from the
webhook, for retention/win-back journeys.

## Where it lives

- **Web**: `web/src/analytics/` — `clevertap.ts` (SDK loader, no-op until
  `NEXT_PUBLIC_CLEVERTAP_ACCOUNT_ID` is set) and `events.ts` (typed `Analytics`
  wrapper). Business events fire from `data/repository.ts` + `data/auth.ts`;
  screen/UI events fire from the page components.
- **Android**: `app/.../analytics/Analytics.kt` (wraps `CleverTapAPI`, no-op
  until the manifest account id/token are set). Business events fire from
  `data/LaundryRepository.kt` + the auth layer; screen/UI events from the
  Compose screens.

Both no-op safely until credentials are configured, so nothing breaks before
the CleverTap account id (+ Android token) is provided.
