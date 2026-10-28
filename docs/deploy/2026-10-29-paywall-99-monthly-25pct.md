# Paywall test: ₹99/month for 25% of new accounts (29 Oct 2026 03:10 IST)

Owner ask: 25% of NEW users see ₹99 monthly; the other 75% keep today's ₹499 monthly. Nothing else changes.

- New variant **`first_order_monthly_99`** = a copy of `first_order_monthly_499` (pop-up after the first order, monthly only, no trial, 1 free order) with `prices.monthly = 99` and `planIds.monthly = plan_TtTopV0clhmnie` (₹99 monthly plan created on the laundry Razorpay account by the deploy script).
- New dated split in `LAUNDRY_PAYWALL_CONFIG`: `{"from":"2026-10-28T21:40:29Z","split":{"first_order_monthly_499":75,"first_order_monthly_99":25}}`. Accounts created before that keep their variant; the Diwali ₹999 offer is unaffected (it only applies to messaged, pre-campaign owners).
- Eligibility is the paywall test's own: Android sign-ups on 1.0.12+ (1.0.13+ draw the variant; a 1.0.12 account gets the legacy group). Web is not in the test.
- Verified in the container after the recreate: 4,000 synthetic new accounts → 1,025 / 2,975; a day-old account → `first_order_monthly_499`; the ₹99 variant resolves to gate first_order, monthly only, ₹99, the new plan id, no trial, 1 free order. Backend + laundry-dash recreated; backup `~/backend-filebak/20261028-214029-paywall-99/`.
- Read it: CleverTap profile `Paywall_Variant` / event prop `paywall_variant` = `first_order_monthly_99` vs `first_order_monthly_499`; admin.mylaundry.work "Paywall test" column. Watch `Paywall Shown` → `Checkout Started` → `Checkout Succeeded` and, for the ₹99 group, retention (`paid_count`) since the price is 5× lower.
- Change the share: run the script again with `PCT=…` (adds another dated split). Stop: add a split with `first_order_monthly_499: 100`.

Script: `docs/deploy/2026-10-29-paywall-99-monthly-25pct-deploy.sh` (`DRY_RUN=1` first).
