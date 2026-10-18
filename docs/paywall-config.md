# Paywall variants: change them from the backend, no app release

From **app 1.0.13**, the paywall a new owner sees is set by **one setting** in `backend/.env` on the server: `LAUNDRY_PAYWALL_CONFIG`. It covers:
- when the paywall shows;
- which plans it shows, and which is pre-selected;
- a ₹2 trial or paying upfront;
- free orders;
- prices.

The app draws whatever the server sends. Older apps and accounts created before the first split keep what they had.

## The setting

One line of JSON:

```json
{
  "splits": [
    { "from": "2026-10-18T07:58:42Z", "split": { "hard_trial_yearly_plus_monthly_4999_499": 100 } }
  ],
  "variants": {
    "hard_trial_yearly_plus_monthly_4999_499": {
      "gate": "login", "plans": ["annual", "monthly"], "default": "annual",
      "trialDays": 1, "freeOrders": 0, "prices": { "annual": 4999, "monthly": 499 }
    }
  },
  "force": { "1000000029": "hard_trial_yearly_plus_monthly_4999_499" }
}
```

### Variant fields

| Field | Values | Meaning |
|---|---|---|
| name (the key) | any text | Your name for the combination. It shows up as `Paywall_Variant` in CleverTap and on admin.mylaundry.work |
| `gate` | `login` | Paywall right after login, before setup, with no close button (hard trial) |
| | `first_order` | Paywall once after the first order and the guide; closable. Then New order asks for a plan |
| | `free_orders` | No extra paywall. New order asks for a plan once `freeOrders` are used |
| `plans` | `["annual"]`, `["monthly"]`, `["annual","monthly"]` | Plans on the paywall. One plan means no choice |
| `default` | `annual` / `monthly` | Pre-selected when both are shown |
| `trialDays` | `0`–`30` | `0` = paid in full when the owner approves AutoPay. `1`+ = ₹2 today (refunded), the plan after that many days |
| `freeOrders` | `0`, `1`, `3`, … | Orders allowed before New order asks for a plan |
| `prices` | rupees, e.g. `{"annual": 4999, "monthly": 499}` | Shown and charged. **₹4,999/yr, ₹499/month and ₹199/month already have Razorpay plans.** For any other price, create the plan in Razorpay first and add `"planIds": {"annual": "plan_…"}` |

### Splits: who gets which variant

- Every account created **at or after** a split's `from` gets one of its variants, picked by a stable hash of the account id. The percentages should add up to 100.
- **To change the split, add a new entry** with a new `from` time. Only accounts created after it are affected; everyone earlier keeps their variant.
- **Editing a variant changes it for everyone already in it.** That's how you change trial days or the default plan for that group.
- Accounts created before the first split keep the original 50/50 test: `hard_trial` / `free1` at ₹199.

### `force`: test phones
Pins a phone number to a variant whatever its sign-up date. Use a review number (`10000000xx`) to try a variant on 1.0.13.

## Examples

- **Yearly only, 1-day trial:** `"plans": ["annual"], "default": "annual", "trialDays": 1`
- **free1-style, both plans, paid upfront:** `"gate": "first_order", "plans": ["annual","monthly"], "default": "annual", "trialDays": 0, "freeOrders": 1, "prices": {"annual": 4999, "monthly": 499}`
- **3 free orders, then Monthly ₹199:** `"gate": "free_orders", "plans": ["monthly"], "trialDays": 0, "freeOrders": 3, "prices": {"monthly": 199}`
- **50/50 between two new variants from Monday:** add `{ "from": "2026-10-20T00:00:00Z", "split": { "variant_a": 50, "variant_b": 50 } }`.

## Apply a change

On the server, edit the single `LAUNDRY_PAYWALL_CONFIG=` line in `~/shweta-makeup-monorepo/backend/.env`, then run:

```sh
cd ~/shweta-makeup-monorepo && docker compose -f docker-compose.prod.yml up -d --force-recreate backend
docker logs --since 1m shweta-makeup-monorepo-backend-1 2>&1 | grep "\[paywall\]"   # empty = accepted
```

If the JSON is broken, or a variant is incomplete (no gate, plans or price, or an unknown price without a plan id), the backend logs `[paywall] … ignored` and falls back safely. Owners don't see a broken paywall.

## Rules the backend keeps for you

- **App 1.0.13+** draws the variant exactly as set.
- **Owners on an older app:**
  - If the variant shows the paywall at login, they get today's hard trial at ₹199 with a 1-day trial.
  - Otherwise they get free1.
  - If they open a 1.0.13 account on an older app, they see both standard plans paid upfront, because the old screen can't word a trial. The charge always matches the screen.
- **One ₹2 trial per shop.** A shop that already had one is charged in full, and the screen says so.

Backend: `services/laundry-paywall-test.js`, `routes/laundry-billing.js`. App: `PaywallConfig` in `BillingModels.kt`, `VideoOffer.kt`.
