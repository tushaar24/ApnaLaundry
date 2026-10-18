# Deploy: every new account → hard_trial; Yearly ₹4,999 only on app 1.0.13+ (2026-10-18)

The owner's call after the test readouts: Yearly on the website brought in about ₹463 per ₹2 trial, against about ₹28 for Monthly. So:

- **hard_trial at 100% for new accounts.** Accounts created at or after `LAUNDRY_PAYWALL_HARD_ALL_FROM` are hard_trial: paywall right after login, no close.
  - Accounts created before keep their 50/50 group and offer. That's 315 free1 and 323 hard_trial, checked after the deploy.
  - A plain `LAUNDRY_PAYWALL_TEST_PCT=100` would have moved every free1 owner (payers included) onto the hard paywall, so it wasn't used.
- **Yearly only on app 1.0.13+.** Those new accounts get one plan: ₹2 today (refunded), then **₹4,999/yr** on `LAUNDRY_RAZORPAY_PLAN_ANNUAL`, after `LAUNDRY_PAYWALL_YEARLY_TRIAL_DAYS` (7).
  - `/status` sends `yearlyOnly: true`, a field older apps don't know.
  - `/subscribe` puts them on the annual plan.
  - The 1.0.12 video paywall can only show Monthly, so 1.0.12 sign-ups after the switch get hard_trial at ₹199/month, 1-day trial.
- **CleverTap:** `Paywall_Offer` = `yearly_4999` | `monthly_199` next to `Paywall_Variant`.

**Backend:** `2026-10-18-backend-hard-trial-all-yearly.patch` (`services/laundry-paywall-test.js`, `routes/laundry-billing.js`).
- Checked locally on 4,000 synthetic accounts.
- No switch set → identical to before.
- With the switch → older accounts unchanged, newer ones hard_trial, Yearly only on 1.0.13+.

**App 1.0.13:**
- `BillingStatus.yearlyOnly`, plus `singlePlan`/`effectivePlan` in `PaywallUiState`.
- `VideoOffer` shows Yearly: "₹4,999 a year", a Yearly card with "just ₹417 a month", and yearly FAQ and bottom-line copy.
- Same release: App Links for `mylaundry.work/open|pay` and the per-shop phone number.

## Applied (18 Oct 2026, 13:28 IST)

`sh docs/deploy/2026-10-18-hard-yearly-deploy.sh` →
- server commit **b4bc3f7**
- backup `~/backend-filebak/20261018-075841-hard-yearly`
- `.env`: `LAUNDRY_PAYWALL_HARD_ALL_FROM=2026-10-18T07:58:42Z`, `LAUNDRY_PAYWALL_YEARLY_TRIAL_DAYS=7`

The running backend gives:
- new account on 1.0.13 → hard_trial · annual ₹4,999 · 7-day trial;
- new account on 1.0.12 → hard_trial · monthly ₹199 · 1-day trial;
- a 15 Oct account → unchanged (free1).

## Change or roll back

- **Trial length:** edit `LAUNDRY_PAYWALL_YEARLY_TRIAL_DAYS` in `backend/.env`, then `docker compose -f docker-compose.prod.yml up -d --force-recreate backend`.
- **Back to 50/50:** remove `LAUNDRY_PAYWALL_HARD_ALL_FROM` and recreate the backend. Accounts made in between go back to their hash group.
- **Full rollback:** restore the two files from the backup folder and rebuild.

## Superseded the same day: server-set variants (LAUNDRY_PAYWALL_CONFIG)

`sh docs/deploy/2026-10-18-paywall-config-deploy.sh`:
- server commit **64d441d**, patch `2026-10-18-backend-paywall-config.patch`;
- `LAUNDRY_PAYWALL_HARD_ALL_FROM` and `_YEARLY_TRIAL_DAYS` are removed, replaced by `LAUNDRY_PAYWALL_CONFIG`.

The current variant is **hard_trial_yearly_plus_monthly_4999_499**: login gate, Yearly ₹4,999 (pre-selected) and Monthly ₹499, 1-day ₹2 trial. It applies from the same switch time, on app 1.0.13+.

The app's `yearlyOnly` was replaced by `BillingStatus.paywall` (PaywallConfig). How to edit variants: `docs/paywall-config.md`.

Checked after the deploy: free1 315 and hard_trial 323 unchanged; the 1 sign-up since the switch was on 1.0.12 and got hard_trial at ₹199.
