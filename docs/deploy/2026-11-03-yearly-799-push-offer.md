# ₹799/year push offer — CleverTap journey webhook (3 Nov 2026)

**What:** owners in the ₹99 first-order arm who saw the pop-up get a push after 30 min; the journey's
webhook step first "grants" them, so the Plans screen / pop-up / wall show **Yearly ₹799 (pre-selected)
+ their Monthly ₹99** for 72 h. Razorpay plan `plan_Tvh4X3mtHLFg9t`.

**Webhook (journey step, CleverTap payload, no body/headers):**
`POST https://shwetamakeover.online/api/laundry/offers/grant?offer=yearly799&key=<LAUNDRY_OFFER_WEBHOOK_KEY>`
Allowed sources only: `LAUNDRY_OFFER_WEBHOOK_IPS` (CleverTap's published IPs). Response `{granted, rejected, missing}`.

**Rules enforced server-side (services/laundry-paywall-offer.js, routes/laundry-offers.js):**
- `audience: tagged` — only owners with a `laundry_offer_sends` row template `push:yearly799` and a wa_message_id.
- `variants: ["first_order_monthly_99"]` — anyone outside that paywall-test group is **rejected** by the webhook
  (logged as a `status='rejected'` row, wa_message_id NULL, error `not in variant: <group>`; the campaign page
  shows them as refused) and never gets the offer even if a row exists.
- `grantHours: 72` from the latest grant; re-granting restarts it. Active/authenticated subscribers are skipped.

**Incident log, 3 Nov IST**
- 17:27 offer + webhook deployed (first config rejected: a listed plan without a price; fixed 17:27, validator relaxed).
- 17:32 IP allow-list deployed; a call from our own server now gets 403.
- 17:51–17:58 the owner's journey went live with a segment of *anyone who saw a paywall*; CleverTap called the
  webhook in batches and ~820 owners were granted — only ~50 in the ₹99 arm; 258 pre-test accounts, 142 hard-paywall
  cohort, 59 in the live ₹199/₹499 arms, rest other. 0 payers among them.
- 17:56 first revocation pass: 674 non-₹99 rows deleted (grants kept arriving).
- 17:57 `variants` lock deployed; webhook now rejects non-₹99 owners.
- 17:59 second pass: 146 more deleted. Final state: 52 ₹99-arm owners granted, all resolving to Yearly ₹799 + Monthly ₹99.
- 18:05 rejection logging written (backend 642cfab); deploy pending the owner's permission.
Owner still to fix the CleverTap segment: event Paywall Shown, last 7 days, **property paywall_variant = first_order_monthly_99**,
exclude subscription_status = active — only 34 of the 198 ₹99 viewers were in the first batch.

**Readout:** admin.mylaundry.work/campaigns?offer=yearly799&template=push:yearly799 (granted / refused / viewed / checkout / paid);
CleverTap events for granted owners carry `paywall_variant = offer:yearly799`.
