# Leads — customers from Google ads, paid per lead

Status: 🚧 Building (owner's brief, 15 Nov 2026). Android 1.0.22 · backend branch `leads` · admin.mylaundry.work /leads.

## Problem

Owners want more customers, but they won't pay for marketing upfront (freemium-vs-hard-gate and
whole-base readouts: pay follows habit and value, not promises). MyLaundry can run Google ads on an
owner's own Maps listing, take the calls on its own number, qualify each caller, and hand the
owner a real customer. The owner should pay only for customers they actually get, on the spot or
from a small prepaid wallet.

## Solution

**MyLaundry's side (ops, not code):** the owner connects their Google Business Profile in the
app. We run Google Ads on that listing from an ad account only we control, with our own call
number on the ad (Vobiz +91 92409 54178, which already rings the support app). A caller is a
lead. The team qualifies it (what they need, where they are) and sends it to the owner from the
owner panel on admin.mylaundry.work, with what the lead cost us.

**The owner's side (the app):**

1. **Get customers** (Settings → Get customers, and Home when a lead is waiting): connect Google
   Business — Google sign-in with the business.manage scope, or paste the shop's Google Maps link
   when sign-in isn't set up yet.
2. **A lead arrives like a ride request**: a high-priority push ("New customer in Indiranagar ·
   Dry clean · ₹60") and a card on Home with a countdown. The area and service are shown; the
   number is hidden (******3210).
3. **Pay per lead**: the price is what the lead cost us + ₹10 (`LAUNDRY_LEAD_MARKUP_PAISE`). The
   owner pays from their **wallet** when it has enough, else **pays now** (Razorpay, UPI) or
   **adds money** (₹100 / ₹200 / ₹500 / ₹1,000) which also pays this lead.
4. **Paid → the number**: the customer's name and number with Call, WhatsApp and "Save as customer"
   so the first order is one tap away.
5. A lead the owner doesn't take in time **expires** (`LAUNDRY_LEAD_EXPIRY_MIN`, default 60) and the
   team can offer it to another shop. A wrong or unreachable number is **refunded to the wallet**
   by the team.

## Decisions (owner, 15 Nov 2026)

| # | Decision |
|---|---|
| 1 | Google Business Profile is connected from the app; the ad account is ours only. |
| 2 | The ad shows our number; leads come to us first and are qualified by hand. |
| 3 | Leads are pushed to the owner "like Uber shows a ride to a driver". |
| 4 | Price per lead = our cost + ₹10. |
| 5 | Pay on the spot, or from a wallet the owner tops up. |
| 6 | The number is revealed only after payment. |

Assumptions made while building (change them in `backend/.env`, no app release):
- A lead is offered to one shop (the shop whose listing the ad ran on); re-offering is a team action.
- Leads expire after 60 min; the team picks 15 / 30 / 60 / 120 per lead.
- Wallet top-ups: ₹50 minimum, ₹10,000 maximum; no withdrawals (refunds go to the wallet).
- Shown to owners the server lists in `LAUNDRY_LEADS_PHONES` (`all` = everyone), like Staff was.

## User stories

- As an owner, I open Get customers, tap Connect Google Business, pick my Google account, and see
  "Connected · Sharma Laundry, Indiranagar". If sign-in isn't available I paste my Maps link.
- As an owner, my phone rings with "New customer in Indiranagar · Dry clean · ₹60 · 58 min left".
  I tap it, see the area and service, and tap Pay ₹60. My wallet has ₹200, so I get the number at once.
- As an owner with an empty wallet, I tap Pay ₹60 now and pay by UPI; the number appears.
- As an owner, I add ₹500 once so the next leads cost one tap.
- As an owner, I tap Not now on a lead I can't serve; it goes back to the team.
- As the team, I pick the caller from today's incoming calls, type "Indiranagar", "Dry clean, 6
  kurtas", cost ₹50, choose the shop, and send. I see when the owner saw it, paid, or let it expire.
- As the team, I refund a lead whose number was wrong; the owner's wallet shows +₹60.

## Implementation decisions

### Server (courses/backend, mounted at `/api/laundry/leads`, `/api/laundry/devices`)

- **Tables** (`20261115120000_laundry_leads`): `laundry_leads`, `laundry_wallets`,
  `laundry_wallet_txns`, `laundry_wallet_orders` (Razorpay Orders for top-ups / pay-now),
  `laundry_gbp_connections` (Google account + encrypted refresh token + locations / Maps link),
  `laundry_devices` (the owner app's FCM tokens; 1.0.22+ registers at login and token refresh).
- **Owner API** (bearer token; staff tokens get 403 as for every owner-only route):
  `GET /` (wallet, Google connection, config, leads — one call for the screen), `GET /:id`,
  `POST /:id/seen`, `POST /:id/accept` (wallet debit → the number; 402 `INSUFFICIENT_BALANCE`
  otherwise), `POST /:id/decline`, `POST /:id/pay-order {amountPaise?}` (a Razorpay Order of at
  least the price; more = top-up that also pays this lead), `POST /wallet/topup {amountPaise}`,
  `POST /wallet/verify {orderId, paymentId, signature}` (signature + fetch the payment, credit,
  pay the attached lead), `GET /wallet` (balance + last 50 entries), `POST /gbp/connect
  {serverAuthCode}`, `POST /gbp/maps-link {url}`, `DELETE /gbp`.
- **Money rules.** Every wallet change is one transaction with the wallet row locked
  (`SELECT … FOR UPDATE`): a debit never overdraws, a Razorpay payment is credited once
  (`razorpay_payment_id` unique), paying a lead is credit (if a payment) then debit, so the
  wallet history shows every rupee. The webhook (`payment.captured` with `notes.kind =
  laundry_wallet`) and a 3-minute reconcile sweep (`created` orders → Razorpay `fetchOrder`)
  confirm payments the app never reported. Prices are set on the server from the team's cost;
  the app never sends an amount for a lead.
- **Lead states.** `offered` → `paid` | `declined` | `expired` (sweep every minute) | `cancelled`
  (team) → `refunded` (team, wallet credit). A `paid` lead returns the full number; every other
  state returns `******3210` (`services/laundry-phone-mask.js`).
- **Push.** `services/laundry-fcm.js` (pulled out of the support-app push) sends FCM HTTP v1 to
  every device of the owner: channel `leads`, high priority, TTL = time to expiry,
  `data.link = mylaundry://lead/<id>`. No device (an older app) → the lead row says `no_device`
  and the owner panel says "call the owner".
- **Google.** `LAUNDRY_GOOGLE_OAUTH_CLIENT_ID` / `_SECRET` (a Web client in the Firebase project
  shweta-makeover's Cloud project). The app gets the client id from `GET /` and shows the Google
  button only when it's set; the server auth code is exchanged here, the refresh token stored
  AES-256-GCM encrypted (`LAUNDRY_GBP_TOKEN_KEY`), and the Business Profile accounts + locations
  fetched (needs GBP API access approved for the project; until then the Google email is stored
  and `apiError` says why). Without any of this the Maps-link path works alone.
- **Admin API** (`X-Admin-Key` = `LAUNDRY_LEADS_ADMIN_KEY`, else `LAUNDRY_AI_CALL_KEY`):
  summary, list, one lead, create (by owner id or phone; caller by number or by a recent inbound
  support call id), cancel / refund / re-offer / push again, connections, one owner's wallet +
  leads, inbound calls to pick from, manual wallet adjust. Responses masked.
- **Experiments.** `GET /api/laundry/experiments` → `leads: true` for owners in
  `LAUNDRY_LEADS_PHONES` (sent only when on; older apps ignore it).
- **Analytics (server, CleverTap).** `Lead Offered`, `Lead Paid`, `Lead Declined`, `Lead Expired`,
  `Lead Refunded`, `Wallet Topped Up`, `GBP Connected`.

### Android (1.0.22)

- `data/leads/` — wire types, `LeadsApi`, `LeadsRepository` (one `LeadsState` flow the screens and
  the Home card read), `DeviceRegistrar` (FCM token → `/devices`, after login and on refresh;
  removed at logout). `Prefs.leadsEnabled` from experiments.
- `ui/screens/leads/` — `LeadsScreen` (wallet card, Google card, leads: waiting / taken / past),
  `LeadSheet` (pay from wallet / pay now / add money → the number, Call, WhatsApp, Save as
  customer), `WalletSheet` (amount chips), `LeadsHomeCard` (Home, while a lead waits),
  `LeadsViewModel` (Razorpay Checkout for Orders through the shared `CheckoutBridge`; only the
  instance that opened Checkout reacts).
- `RazorpayCheckout.launchOrder` (order_id) beside the subscription launch; `CheckoutResult.Success`
  carries orderId + signature (additive).
- Push channel `leads` (high importance, sound); `PushService` refreshes the leads flow on a
  `type = lead` data message so the Home card appears without reopening.
- Deep links `mylaundry://leads`, `mylaundry://lead/<id>` (+ the https /open twins); older apps
  open Home.
- Settings → **Get customers** card (owner only, when `leadsEnabled`).
- Google: Play services `AuthorizationClient` with scope business.manage + offline access for the
  server client id the server sent → server auth code → `POST /gbp/connect`.
- Analytics (client): `Leads Entry Tapped`, `Leads Opened`, `Lead Viewed`, `Lead Pay Tapped`,
  `Lead Paid`, `Lead Pay Failed`, `Lead Declined`, `Wallet Topup Started`, `Wallet Topped Up`,
  `Lead Call Tapped`, `Lead WhatsApp Tapped`, `Lead Saved As Customer`, `GBP Connect Tapped`,
  `GBP Connected`, `GBP Connect Failed`, `GBP Maps Link Saved`.

### admin.mylaundry.work

- `/leads`: tiles (waiting, paid, expired, revenue, cost, margin, wallet float), **Send a lead**
  form (caller from recent incoming calls or a typed number, name, area, service, note, cost ₹ →
  owner pays ₹, expiry, shop by number search), the leads table with cancel / refund / re-offer /
  push again; `/leads/connections` lists connected Google accounts and Maps links.
- Owner panel: wallet balance, Google connection, last leads, and a **Send a lead** button.

## Build order

1. [x] Server: tables, wallet + leads services, owner routes, admin routes, push, Google, webhook
       branch, sweeps, experiments key, unit tests. *(courses branch `leads`; 66 tests; owner + admin flow run end to end on a throwaway Postgres, 15 Nov)*
2. [x] Android: data layer + device registration, Leads screens, Home card, Settings card, nav +
       deep links, push channel, Razorpay orders, Google connect, analytics. *(1.0.22 on prod; translations pending — run tools/i18n/translate.py)*
3. [x] admin.mylaundry.work: leads page + owner panel; deploy script patches the live files. *(laundry-dashboard/leads.js)*
4. [ ] Deploy: `deploy/2026-11-15-leads.sh` (migrate, env, rebuild, dashboard patch, smoke) — written, NOT run.
       Google OAuth client + GBP API access are the owner's steps (`docs/deploy/2026-11-15-leads.md`).

## Testing decisions

- Server: unit tests for pricing, the lead state machine and the wallet rules (no overdraw,
  one credit per payment, pay-now = credit + debit), the Maps-link parser.
- Android: compile (`:app:assembleDebug`); the owner tries it on the phone with a review number
  (`LAUNDRY_LEADS_PHONES`) before `all`.
- Before release: an older app against the new server (the new keys are ignored; nothing else
  changed).

## Out of scope (v1)

Automatic lead intake from Vobiz calls (the team creates leads by hand), offering one lead to
several shops at once, website leads page, wallet withdrawals, Google Ads automation.
