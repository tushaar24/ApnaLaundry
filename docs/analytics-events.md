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
| `Payment Received` | `amount`, `method` (`cash`\|`upi`), `type` (`delivery`\|`prepay`\|`khata`), `customer_id`, `order_id?` | any money-in |
| `Old Baaki Added` | `amount`, `customer_id` | pre-app debt recorded |
| `Charged` | `Amount` (bill total), `payment_method`, `order_id`, `Items` (per-service) | on every `Order Delivered` — revenue event |

### Customers, bills, rates, engagement

| Event | Properties | Fired when |
| ----- | ---------- | ---------- |
| `Customer Added` | `source` (`order`\|`list`), `with_old_baaki` | new customer saved |
| `Reminder Sent` | `customer_id`, `amount` | WhatsApp baaki reminder |
| `Bill Sent` | `order_id`, `channel` (`whatsapp`) | send bill |
| `Bill Viewed` | `order_id` | customer-facing bill preview opened |
| `Bill Downloaded` | `order_id`, `from` (`bill`\|`order_detail`) | bill PDF opened in the share sheet (Android) |
| `Tags Printed` | `order_id`, `tags`, `mode` (`piece`\|`order`), `paper` (`58mm`\|`80mm`), `from` (`bill`\|`order_detail`\|`menu`) | laundry tags printed on a Bluetooth printer (Android) |
| `Tags Print Failed` | `order_id`, `reason` (`connect`\|`write`\|`bluetooth_off`\|`permission`\|`no_bluetooth`), `from` | tag printing failed (Android) |
| `Combined Bill Sent` | `customer_id`, `orders`, `total`, `to_pay`, `channel` (`whatsapp`) | many orders of one customer sent as one bill |
| `Summary Shared` | `period` | earnings day/week/month summary shared |
| `Rates Opened` | `from` (`home`\|`settings`) | rate card opened |
| `Service Added` | `mode` (`PIECE`\|`WEIGHT`) | new service saved |
| `Service Edited` | `service_id` | service edited |
| `Service Deleted` | `service_id` | service deleted |
| `Screen Viewed` | `screen` | each main screen is shown (`login`, `setup`, `home`, `customers`, `earnings`, `settings`, `new_order`, `order_detail`, `customer_khata`, `combined_bill`, `bill`, `rates`) |
| `Earnings Period Changed` | `period` (`today`\|`week`\|`month`) | period toggle on Earnings |
| `Order Search Opened` | — | search opened on Home |

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
| `Customer Sheet Opened` | `ctx` (`order`\|`list`), `mode` (`new`\|`edit`), `trigger` (`auto`\|`add_button`\|`add_with_query`\|`address_card`\|`list`\|`list_with_query`\|`customer_page`\|`other`), `address_shown`, `address_needed`, `prefilled` (`none`\|`name`\|`phone`), `customers` (saved so far), `guide_on` | the customer sheet appears (all versions of the flow) |
| `Customer Sheet Field Edited` | `field` (`name`\|`phone`\|`address`\|`old_khata`), `ctx`, `seconds_since_open` | first typing in that field (once per field per sheet) |
| `Customer Phone Trimmed` | `digits`, `lead` (`91`\|`0`\|`other`) | more than 10 digits typed/pasted in Phone (it keeps the first 10 — "+91 98…" saves wrong) |
| `Customer Contacts Tapped` | `permission` (`granted`\|`asking`) | "Choose from contacts" |
| `Contacts Permission Answered` | `granted` | the contacts permission dialog answered (no → Android's own picker) |
| `Customer Contact Picked` | `source` (`in_app`\|`system`), `ok` (had a 10-digit mobile), `already_saved` | a contact tapped |
| `Customer Contacts Closed` | `source`, `contacts` (listed, in_app), `searched` | the contact list left without picking |
| `Customer Duplicate Shown` | `ctx` | "Already saved as …" (the number is a customer already) |
| `Customer Save Blocked` | `reason` (`name`\|`phone_empty`\|`phone_short`\|`duplicate`\|`address`), `ctx` (`order`\|`list`\|`address_sheet`), `name_filled`, `phone_digits`, `address_filled` | the greyed "Save customer" / "Save address" tapped |
| `Customer Sheet Closed` | `ctx`, `mode`, `trigger`, `how` (`saved`\|`used_existing`\|`dismissed`), `seconds_on_screen`, `name_filled`, `phone_digits`, `address_shown`, `address_filled`, `contacts_tapped`, `contact_picked`, `blocked_taps`, `missing` (what still blocked Save on a dismiss, else `none`), `method` (`typed`\|`contacts`, on save) | the sheet goes away — saved, "Use this" on a duplicate, or back / swipe / tap outside |
| `Address Sheet Opened` | `trigger` (`route`\|`customer_saved`\|`customer_picked`\|`address_card`\|`save_button`), `had_address`, `guide_on` | v2's address sheet appears |
| `Address Sheet Closed` | `trigger`, `how` (`saved`\|`dismissed`), `seconds_on_screen`, `edited`, `filled`, `blocked_taps` | v2's address sheet goes away |
| `Customer Selected` | `from` (`recent`\|`search`), `query_length` | an existing customer picked on New order |
| `Customer Search Used` | — | New order's customer search typed in (once per screen) |
| `Order Route Changed` | `leg` (`pickup`\|`delivery`), `to` (`shop`\|`home`), `customer_set`, `has_address` | At shop ↔ home switched on New order |
| `Order Save Blocked` | `reason` (`customer`\|`address`\|`clothes`), `is_edit` | the greyed bottom button ("Choose a customer" / "Add address first" / "Add clothes first") tapped |

`New Order Abandoned` also carries `customer_sheet_opens`, `save_blocked_taps`,
`address_needed`, `address_sheet_opened` (1.0.15+). The guide adds the step
`order_address_form` (v2's address sheet tip); its `order_route` and
`order_customer_form` cards word themselves for the variant.

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
| `Assistant Opened` | `from` (`home`\|`push`) | the Ask screen opens (Home button, or `mylaundry://assistant`) |
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
| `Language Changed` | `from`, `to` (codes), `where` (`login_first` = the picker on the first open \| `login` = the chip at the top left \| `settings`) | a language was picked (also the first pick) |

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
