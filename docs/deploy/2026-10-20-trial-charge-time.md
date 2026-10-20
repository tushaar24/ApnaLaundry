# Trial charge time: debit notice at 14:00 IST, never on the day of the ₹2 (2026-10-20)

**Why.** Razorpay sends the UPI pre-debit notice exactly 24 h before a subscription's first charge (`start_at`). Our trials set `start_at` to "checkout + trial days", so the charge and its notice landed at the same clock time as the checkout, mostly 18:00–21:00. For 1-day trials that meant the notice arrived about 7 minutes after the ₹2.

The cancel readout (20 Oct, 70 trials that reached their notice) found:
- **Days into the trial:** 54% cancelled after a day-0 notice, against 21% after a day-6 notice.
- **Time of day:** evening notices (17–21) were the worst at 41%, and 70% on 1-day trials.
- **Prior use:** owners with 5+ orders before the notice cancelled 0 of 7 times.
- **Debit success:** debits at 13–17 succeeded most often.
- **Speed:** the median cancel came 24 minutes after the notice.

## The rule (all users, no variant)

`services/laundry-razorpay.js` → `trialStartAt(now, trialDays)`, used by `createSubscription` for every ₹2 trial. The first charge goes at the **first 14:00 IST** that is:
- at least the promised trial days after checkout (never earlier than promised), and
- at least **40 h** after checkout, so the notice (charge − 24 h) comes **≥ 16 h after the ₹2**.

| ₹2 paid | 1-day trial: notice → charge | 7-day trial: notice → charge |
|---|---|---|
| 09:00 | next day 14:00 → day after 14:00 | day 6 14:00 → day 7 14:00 |
| 19:00 | next day 14:00 → day after 14:00 | day 7 14:00 → day 8 14:00 |
| 22:30 | day 2 14:00 → day 3 14:00 | day 7 14:00 → day 8 14:00 |

- A 1-day trial is now charged 40–64 h after the ₹2.
- Not touched:
  - Trials already running: their `start_at` is already set in Razorpay.
  - Upfront plans: there's no trial, so no notice before the first charge.
  - Renewals: Razorpay schedules them at 00:00 IST.
  - Access: access follows the subscription status, which stays live until the charge, so nobody sees the paywall earlier.
  - Cancel grace: a cancelled trial keeps access until checkout + trial days, as before.

**Settings** (in `backend/.env`; recreate the backend after a change):
- `LAUNDRY_TRIAL_CHARGE_HOUR_IST`: default `14`. Set it to `off` to restore "checkout + trial days" (rollback).
- `LAUNDRY_TRIAL_MIN_NOTICE_HOURS`: default `16`.

**Known copy gap:** the 1.0.13/1.0.14 paywall computes its date on the phone as "today + trial days" ("₹4,999/year from 21 Oct", FAQ "On 21 Oct"), so it now shows a day or two earlier than the real charge.
- The real charge is always later than shown, never earlier.
- The Settings/plan screen (from Razorpay's `charge_at`) and the UPI app's notice show the real date.
- Fixing the paywall date needs an app update. The web paywall's "after <today + 7>" is still true.

## LIVE 2026-10-20 09:56 IST

- Server commit **9dc696b** contains `config/env.js` only. `services/laundry-razorpay.js` is patched but not committed, because it has older uncommitted live edits.
- Backup: `~/backend-filebak/20261020-042625-trial-charge-time`.
- Patch: `2026-10-20-backend-trial-charge-time.patch`. Deploy script: `2026-10-20-trial-charge-time-deploy.sh`.
- Container check: a 1-day trial started at 09:56 → notice 21 Oct 14:00, charge 22 Oct 14:00. A 7-day trial → notice 26 Oct 14:00, charge 27 Oct 14:00.
