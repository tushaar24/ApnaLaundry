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
`Name` is refreshed when the shop name is set or edited.

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

An owner can run several shops (plan needed to add one). Each shop has its own
customers, khata, orders, bills and services; the app shows one at a time.
Profile property **`Shop_Count`** = how many shops the owner has.

| Event | Properties | Fired when |
| ----- | ---------- | ---------- |
| `Add Shop Tapped` | `from` (`home`\|`settings`), `has_plan` | Home ＋ / switch sheet / Settings "Add another shop" (no plan → paywall `from: add_shop`) |
| `Shop Added` | `shop_count` | a shop was created (server-checked plan) |
| `Shop Switched` | `shop_count` | the owner moved to another shop |
| `Earnings Shops Changed` | `selection` (`this`\|`all`\|`some`), `shops` | Earnings shop chips |

### Billing / paywall (A/B: `trial_2` \| `free_50`)

The variant is assigned by the backend (Firebase-weighted, stable per user) and
returned by `GET /billing/status`. The **client** fires the UI funnel below and
sets the `paywall_variant` profile property. The **backend** fires the
money-confirmed events from the Razorpay webhook (server-side CleverTap), so
revenue events can't be spoofed or lost if the app closes.

| Event | Source | Properties | Fired when |
| ----- | ------ | ---------- | ---------- |
| `Paywall Shown` | client | web: `default_plan`; Android: `from` (`free_orders`\|`settings`), `default_plan`, `order_count` (orders not deleted) | paywall/trial screen shown with its plans (not the Settings status row) |
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

Profile property `subscription_status` (+ `plan`, `paywall_variant`) is kept
current from the webhook, for retention/win-back journeys.

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
