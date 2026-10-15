# Deploy: QA phone 1000000028 → paywall test hard_trial (2026-10-15)

Owner's ask: the review/QA number **1000000028** always gets the paywall test's
**hard_trial** group, so the hard paywall can be tested on demand.

`services/laundry-paywall-test.js` (`2026-10-15-backend-paywall-qa-phone.patch`):
a `FORCED_GROUP` map checked first in `groupFor`, so the phone gets hard_trial
whatever its account age, platform or app version. It still needs the test to be
on (`LAUNDRY_PAYWALL_TEST_START` and the ₹199 plan id set). Everyone else gets the
same group as before (checked locally on 5,000 synthetic accounts against the live
file).

What that number then gets, from the existing code: 0 free orders, the hard
paywall right after login on app 1.0.12+ (no close), the ₹2 / 1-day trial, then
₹199/month, the guide on, CleverTap `Paywall_Variant = hard_trial`. Review
numbers 1000000xxx are left out of all readouts.

## Apply

From the app repo root:

```sh
sh docs/deploy/2026-10-15-paywall-qa-phone-deploy.sh
```

Backup → patch (dry-run first) → `node --check` → server commit → `up -d --build
backend` → prints the group the running code gives 1000000028 (`hard_trial`) and
an old account (`null`).

## Test

Log in on app 1.0.12 with 1000000028, OTP **100008** (review numbers: `10000` +
the last digit). The account doesn't exist yet, so the first login creates it.
The ₹2 trial is a real UPI AutoPay charge; cancel AutoPay in the UPI app after
the test.

## Roll back

Restore `services/laundry-paywall-test.js` from the backup folder the script
prints, then `docker compose -f docker-compose.prod.yml up -d --build backend`.

## Done

Deployed 2026-10-15 10:04 IST: server commit 8527b33, backup
`~/backend-filebak/20261015-043431-paywall-qa-phone`. The running backend gives
1000000028 → `hard_trial` and an old account → `null`; container healthy.
