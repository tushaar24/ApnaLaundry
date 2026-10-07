# Analytics — CleverTap event tracking plan

One shared taxonomy for **both** clients (the Next.js website in `web/` and the
Android app in `app/`). Same event names and property keys on both, so funnels
in CleverTap are directly comparable across platforms.

Platform is distinguished by two event properties stamped automatically on
every client event (incl. `Charged`) — never add them by hand, and never use
either key as an event-specific property:

- `platform` = `web` | `android`
- `source` = `website` | `android`

Where in the app an action started is `entry_point` (see `New Order Started`,
`Customer Added`).

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
   `OTP Submitted` → `Logged In` → `Setup Completed` → `New Order Started` →
   `Order Saved` → `Order Delivered`.
2. **Order creation** — `New Order Started` → `Order Saved {has_bill}` →
   `Bill Sent` → `Order Delivered`.
3. **Order fulfilment** — `Order Picked Up` → `Clothes Counted` →
   `Order Marked Ready` → `Order Delivered`.
4. **Cash collection** — `Order Delivered` → `Payment Received` /
   `Old Baaki Added` (chase baaki) → `Reminder Sent`.
5. **Revenue** — `Charged` (CleverTap's reserved revenue event) on every
   delivery, for LTV / revenue reports.

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
| `Logged In` | `is_new_user` (server just created the account), `needs_setup` (no shop yet) | verify-otp succeeded (also fires `onUserLogin`) |
| `Setup Completed` | `services_count`, `express_pct` | first-run rate card saved ("Start taking orders") |
| `Logged Out` | — | user logs out from Settings |

### Order creation & lifecycle

| Event | Properties | Fired when |
| ----- | ---------- | ---------- |
| `New Order Started` | `entry_point` (`home`\|`empty_home`\|`customer`), `is_edit` | New/Edit order screen opened (`empty_home` = the CTA on an empty Home) |
| `Order Saved` | `order_id`, `is_edit`, `has_bill`, `pickup`, `delivery`, `express`, `discount`, `fee`, `pieces`, `kg`, `services_count`, `total`, `quick_bill` | order created or edited |
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
| `Customer Added` | `entry_point` (`order`\|`list`), `with_old_baaki` | new customer saved |
| `Reminder Sent` | `customer_id`, `amount` | WhatsApp baaki reminder |
| `Bill Sent` | `order_id`, `channel` (`whatsapp`) | send bill |
| `Bill Viewed` | `order_id` | customer-facing bill preview opened |
| `Summary Shared` | `period` | earnings day/week/month summary shared |
| `Rates Opened` | `from` (`home`\|`settings`) | rate card opened |
| `Service Added` | `mode` (`PIECE`\|`WEIGHT`) | new service saved |
| `Service Edited` | `service_id` | service edited |
| `Service Deleted` | `service_id` | service deleted |
| `Screen Viewed` | `screen` | each main screen is shown (`login`, `setup`, `home`, `customers`, `earnings`, `settings`, `new_order`, `order_detail`, `customer_khata`, `bill`, `rates`) |
| `Earnings Period Changed` | `period` (`today`\|`week`\|`month`) | period toggle on Earnings |
| `Order Search Opened` | — | search opened on Home |

### Billing / paywall (₹2 trial → monthly \| annual)

Single hard gate — the A/B (`trial_2` / `free_50`) was removed 2026-10-06, so
no event carries a `variant`. The **client** fires the UI funnel below. The
**backend** (`courses/backend/routes/laundry-billing.js`) fires the
money-confirmed events server-side (stamped `platform: "backend"`), so revenue
events can't be spoofed or lost if the app closes. The backend no-ops until
`LAUNDRY_CLEVERTAP_ACCOUNT_ID` + `LAUNDRY_CLEVERTAP_PASSCODE` (+
`LAUNDRY_CLEVERTAP_REGION`) are set on the server.

| Event | Source | Properties | Fired when |
| ----- | ------ | ---------- | ---------- |
| `Paywall Shown` | client | — | paywall/trial screen shown |
| `Plan Selected` | client | `plan` | user taps a plan / Pay |
| `Checkout Started` | client | `plan`, `amount`, `trial_amount` | subscription created, Razorpay checkout opened |
| `Checkout Succeeded` | client | `plan` | status poll sees the subscription active (optimistic) |
| `Checkout Failed` | client | `plan`, `reason` | checkout dismissed / errored |
| `Subscription Cancel Requested` | client | `plan` | user taps cancel |
| `Subscription Activated` | server | `plan`, `trial` | `subscription.authenticated` webhook |
| `Subscription Charged` | server | `plan`, `amount` | `subscription.charged` webhook (recurring success) |
| `Subscription Payment Failed` | server | `plan` | `subscription.pending` webhook (will retry) |
| `Subscription Halted` | server | `plan` | `subscription.halted` webhook (retries exhausted) |
| `Subscription Cancelled` | server | `plan` | user cancel via `POST /billing/cancel` |

Profile property `subscription_status` (`authenticated` → `active` →
`halted` \| `cancelled`) + `plan` is kept current from the webhook, for
retention/win-back journeys.

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
