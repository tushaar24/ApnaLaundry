# First-customer A/B — 3 ways to add a new shop's first customer (2026-10-24)

**Why:** in the free1_monthly_499 cohort (24 Oct), 55% of owners who started a New order
but never saved one got stuck adding the customer. See the readout in memory
(`first-order-blockers`).

**What the owner asked for:** three versions of the first-time "add customer" step, the
guide matching each one, and detailed events everywhere, including inside the sheets.

| Variant (`customer_flow`) | New order, for a shop with no customers yet |
|---|---|
| `v1_auto_sheet` | The New customer sheet (name, phone, optional address) opens by itself, guide or not. The guide's tip shows inside the sheet. |
| `v2_no_address` | The sheet opens by itself with only name + phone. Home pickup / delivery opens a separate "Customer's address" sheet when a customer is set while home is on, or when the route switches to home. "Add" next to Address and the greyed "Add address first" button open it too. |
| `v3_tap_to_add` | The sheet never opens by itself. The owner taps "Add new customer"; with the guide, its card points there. |
| not in the test | 1.0.14 exactly: the sheet opens by itself unless the guide is on. |

Guide wording per variant (`ui/tour/TourSteps.kt`):
- v1 / v3 sheet tip: "…Address is optional…".
- v2 "Shop or home?" card: "…then save their address." It also adds the `order_address_form` tip.
- The legacy wording is unchanged.

Events: `docs/analytics-events.md` → "First customer on New order".

## Who is in it (server)

`GET /api/laundry/experiments` sends `customerFlow` only for an account that meets all of these:
- `signup_platform = android` and `signup_app_version ≥ 15` (first login on 1.0.15+);
- created at or after `LAUNDRY_CUSTOMER_FLOW_START`.

The split is `LAUNDRY_CUSTOMER_FLOW_SPLIT` (v1,v2,v3; default `34,33,33`), on a stable hash
of the user id (`customer_flow_v1:<id>`).

`LAUNDRY_CUSTOMER_FLOW_FORCE='{"<10-digit phone>":"v3_tap_to_add"}'` pins QA phones to a
variant, ignoring the start and version rules. Use it to try each variant on a test number.

The server also saves CleverTap `Customer_Flow`.

**Backward compatibility:**
- Accounts outside the test get the exact same JSON as today (no new key).
- The app parses it with `ignoreUnknownKeys`.
- The app treats a missing key as the 1.0.14 flow.
- Older apps, older accounts and the website are unchanged.
- Stop the test by removing `LAUNDRY_CUSTOMER_FLOW_START` and recreating the backend. Apps then go back to the old flow at their next start.

## Deploy (backend first, then release app 1.0.15)

```sh
DRY_RUN=1 bash docs/deploy/2026-10-24-customer-flow-ab-deploy.sh   # patches + checks a copy, changes nothing
bash docs/deploy/2026-10-24-customer-flow-ab-deploy.sh             # applies
FORCE='{"1000000028":"v2_no_address"}' bash docs/deploy/2026-10-24-customer-flow-ab-deploy.sh   # same + QA phones
```

The script:
1. checks the patched route with the live container's node;
2. backs up `backend/.env` and the route to `~/backend-filebak/<ts>-customer-flow-ab/`;
3. applies `2026-10-24-backend-customer-flow-ab.patch`;
4. appends `LAUNDRY_CUSTOMER_FLOW_START=<now>` and `LAUNDRY_CUSTOMER_FLOW_SPLIT=34,33,33`;
5. rebuilds the backend and checks a new 1.0.15 account gets a variant (rolls back if not);
6. commits the route on the server.

Setting the start now is safe before the app ships: only 1.0.15 sign-ups qualify.

**Rollback:**
1. Copy the backup `.env` and `laundry-experiments.js` back.
2. `docker compose -f docker-compose.prod.yml up -d --build backend`.

## Reading it

Split any funnel by the `customer_flow` event property or the `Customer_Flow` profile property.

Main funnel: `New Order Started` → `Customer Sheet Opened` → `Customer Sheet Closed {how: saved}` → `Order Saved`.

The why:
- `Customer Save Blocked {reason}`
- `Customer Sheet Closed {missing, seconds_on_screen}`
- `Order Save Blocked {reason}`
- `Address Sheet Opened/Closed`

Each arm gets about a third of new sign-ups: about 45 a day at the 22–24 Oct rate of roughly 140 sign-ups a day.

Seeing a 40% → 55% lift in "saved an order" needs about 170 owners per arm (95% confidence, 80% power), so 4–5 days of sign-ups on 1.0.15.

## Deployed

- **Live since 2026-10-25T14:23:57Z (19:53 IST)**, as server commit `029bcbe`. The dry run passed first (new 1.0.15+ accounts split about 1035 / 1011 / 954).
- **Checked afterwards:** older accounts get the same `/experiments` as before.
- **App:** 1.0.15 was never released, so the variants reach owners with the 1.0.16 release.

## Split changed 2026-10-26 11:11 IST (owner): v1 10% / v2 45% / v3 45%, new sign-ups only

- **Why:** v1 converted worst. From the customer sheet to a first customer: v1 5/11, v2 10/11, v3 8/10. From customer to order: v1 1/5, v2 8/10, v3 8/9.
- **How:** the new `LAUNDRY_CUSTOMER_FLOW_SPLITS=[{"from":"2026-10-26T05:41:47Z","split":[10,45,45]}]` applies only to accounts created at or after `from`. Older accounts keep `LAUNDRY_CUSTOMER_FLOW_SPLIT=34,33,33`.
- **Code:** patch `2026-10-26-backend-customer-flow-dated-split.patch`, deployed with `2026-10-26-customer-flow-dated-split-deploy.sh` as server commit `6a6bb76`.
- **Checked before:** with real accounts, all 46 shops in the test kept their variant.
- **Checked after:** the shops in the test are unchanged (v1 13, v2 16, v3 17, test phones included); new sign-ups land about 10/46/44.
- **Backup:** `~/backend-filebak/<ts>-customer-flow-dated-split/`.
- **Rollback:** copy `.env` and `laundry-experiments.js` back, then `docker compose -f docker-compose.prod.yml up -d --build backend`.

## Split changed again 2026-10-26 23:04 IST (owner): v1 0% / v2 45% / v3 55%, new sign-ups only

- A second dated entry was appended to `LAUNDRY_CUSTOMER_FLOW_SPLITS` (`from` 2026-10-26T17:34:15Z) and the backend recreated (no code change).
- Checked after: existing accounts unchanged (v1 20, v2 44, v3 39 incl. test phones); a sign-up now lands about 45/55 on v2/v3, never v1.
- Backup: `backend/.env.bak-cf-0-45-55-20261026-173415`.
