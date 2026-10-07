# ApnaLaundry — Billing & Paywall

Subscription billing + the ₹2-trial paywall, across backend, website, and Android app.
Uses a **separate Razorpay account** from the course sales (`LAUNDRY_RAZORPAY_*`).

## 1. Architecture — Razorpay Subscriptions (not S2S)

The backend creates a **Razorpay subscription** server-side and the client authorizes
the UPI AutoPay mandate via **Razorpay Standard Checkout** (which owns the UPI
app-picker on mobile / QR on desktop). Razorpay then auto-charges the plan and
handles pre-debit notifications + retries. `subscription.*` webhooks keep our
tables authoritative. **No S2S, no raw `payments/create/*`, no self-run charge cron.**

**Why this design:** the Razorpay account does **not** have S2S (raw API) enabled —
`payments/create/upi` returns `BAD_REQUEST_ERROR: "The requested URL was not found"`.
Razorpay also has **no own-UI (no-Razorpay-screen) path for a *subscription*** mandate;
the only own-UI recurring path is the token/Recurring-Payments product, which itself
needs Razorpay to enable "UPI Intent on Recurring". Given that, we ship with Razorpay's
Standard Checkout screen (no enablement request, works today). The trade-off is that the
final mandate-approval step is Razorpay's screen; our paywall/plans UI is our own.

## 2. The offer (no A/B)

One offer for everyone: **₹2 trial, then the chosen ₹499/mo or ₹4999/yr plan**. Shown
**right after login** whenever there's no active subscription (or it expired, past the
cancellation grace). **Hard gate** — the app is unreachable until there's an active
subscription. (The earlier `trial_2` / `free_<N>` A/B experiment and its Firebase Remote
Config / `LAUNDRY_PAYWALL_VARIANTS` switch were removed on 2026-10-06.)

**Gate flow (both platforms):** logged in? → subscription active? → route; **a loader
shows until that check resolves** (no flash of the app or wrong screen).

- no active sub (`paywallDue`) → non-cancellable paywall (BillingGate). No close/back.
  Razorpay Checkout closing unpaid (failed, cancelled, Back) opens a "Your 7 free days are
  waiting" sheet whose CTA reopens Checkout; subscribe-API errors stay inline.
- otherwise (active sub, or billing unconfigured/unreachable) → the app renders.
  Billing being unreachable **fails open** (never locks the owner out of their shop).

## 3. Backend (`courses` repo, `backend/`)

- `routes/laundry-billing.js` — `GET /status`, `POST /subscribe`, `POST /cancel`,
  `POST /webhook` (signature-verified). `/subscribe` creates the customer + subscription
  and returns `{ subscriptionId, keyId, shortUrl, plan, amount, trialAmount }`.
- `services/laundry-razorpay.js` — Subscriptions API via Basic-auth fetch
  (`createSubscription`, `fetchSubscription`, `cancelSubscription`, webhook HMAC). Every
  new subscription uses `start_at = +7d` + a ₹2 **addon**.
- `services/laundry-clevertap.js` — server-side CleverTap for money-confirmed events.
- Models (`prisma/schema.prisma`): `LaundrySubscription` (razorpaySubscriptionId, plan,
  planId, status, amount, trialAmount, shortUrl, currentEnd, chargeAt),
  `LaundryPayment`, `LaundryWebhookEvent` (audit).
  Migration `20261003140000_laundry_billing_subscriptions`; A/B table + `variant` column
  dropped in `20261006120000_laundry_drop_paywall_ab`.
- Webhook → status map: `authenticated` / `activated` / `charged` (→ active, record payment,
  CleverTap) / `pending` (retrying) / `halted` / `cancelled` / `completed`.
- Env (server `backend/.env`): `LAUNDRY_RAZORPAY_KEY_ID/_KEY_SECRET/_WEBHOOK_SECRET`,
  `LAUNDRY_RAZORPAY_PLAN_MONTHLY=plan_TjPqwdhxQR8qaP` (₹499),
  `LAUNDRY_RAZORPAY_PLAN_ANNUAL=plan_TjPrToEWhVmszL` (₹4999),
  `LAUNDRY_TRIAL_AMOUNT_PAISE=200`,
  `LAUNDRY_CLEVERTAP_*`.

## 4. Website (`web/`, Next.js)

- `src/data/billing.ts` — `getBillingStatus`, `subscribe`, `cancelSubscription`,
  `openSubscriptionCheckout` (loads `checkout.razorpay.com/v1/checkout.js`, opens Standard
  Checkout with `subscription_id`; success → poll status).
- `src/ui/screens/paywall.tsx` — plans UI (`hardGate` hides the close button); on success
  shows the "confirming…" then "done" states.
- `src/ui/gate.tsx` — `BillingGate` (loader → trial hard gate or app). Wired in
  `src/app/(app)/layout.tsx`.
- Login-flash fix (`src/data/auth.ts`): `authed` is set **after** the initial pull resolves
  setup status, so the Gate routes once instead of flashing `/setup`.

## 5. Android app (`app/`, Compose)

- `data/billing/` — `BillingApi`, `BillingModels` (`SubscribeResponse` carries `keyId` /
  `shortUrl`), `BillingRepository`, `RazorpayCheckout` (+ `CheckoutBridge`,
  `CheckoutResult`).
- `MainActivity` implements Razorpay's `PaymentResultWithDataListener` →
  forwards to `CheckoutBridge`; `Checkout.preload` on create.
- `ui/screens/paywall/` — `PaywallViewModel`, `PaywallScreen` (`hardGate` +
  `BackHandler` block), `BillingGate` (loader → hard gate or app).
- Deps: `com.razorpay:checkout` (Standard Checkout) + `com.android.billingclient:billing-ktx`
  (kept for Play Store acceptance). ProGuard keep-rules for `com.razorpay.**`.
- `AndroidManifest.xml` has a `<queries>` block for `scheme="upi"` so UPI apps are visible
  on Android 11+ (targetSdk 36) — **required** or Checkout hides UPI even when a UPI app
  is installed.
- **Build:** Gradle needs Android Studio's bundled JDK 21, e.g.
  `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:assembleDebug`.

## 6. Status (as of 2026-10-03)

- **Backend** — deployed; Subscriptions + webhooks verified on prod (a real `trial_2`
  mandate `sub_TjTbjnOMJtXvOD` reached `authenticated` end-to-end).
- **Web** — deployed on mylaundry.work (Standard Checkout, hard/soft gates, login-flash fix).
- **Android** — builds + installs; paywall + Standard Checkout + gates in place.

## 7. Known follow-ups

- **₹2 trial timing:** implemented as a ₹2 subscription addon + `start_at +7d`. Verify on the
  Razorpay dashboard whether ₹2 is charged upfront vs on the first invoice (day 7); if not
  upfront, charge ₹2 as a separate one-time payment at signup.
- **Payment ledger:** the webhook records `subscription.charged` payments; the first/auth
  charge arrives as `payment.captured` and is not yet recorded in `laundry_payments`.
- **Razorpay "min version" dialog** on Android before Checkout: if it persists, bump the
  `com.razorpay:checkout` version.
- **Test device needs a UPI app installed** for the UPI option to appear in Checkout.

## 8. Deploy / release

- **Web:** `rsync web/ → courses-aws:/home/ubuntu/apnalaundry-web/` then
  `docker compose -f docker-compose.prod.yml up -d --build laundry`.
- **Backend:** push → on server `git pull` → `run --rm --build migrate` (if schema changed)
  → `up -d --build backend`.
