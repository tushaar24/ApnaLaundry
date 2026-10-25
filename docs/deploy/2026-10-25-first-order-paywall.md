# The paywall right after the first order again (2026-10-25)

**Owner:**
> Show paywall as soon as user creates an order or until the demo is over … like how it used to be. Right now we show the paywall as soon as user clicks on creating a second order.

## Now (since 22 Oct)

Every new account gets `free1_monthly_499`:
- `gate: free_orders`, 1 free order, Monthly ₹499 paid upfront;
- the free_orders gate has no pop-up, so the paywall only appears on New order for a 2nd order.

## Change

A new variant **`first_order_monthly_499`** for accounts created from the moment the script runs. It is the same plan and price with **`gate: first_order`**:
- **When it shows:** the paywall appears once, right after the first order is saved **and** the guide is finished or skipped. If the guide is skipped first, it waits for the order.
- **Where it shows:** on a main screen (Home / Customers / Earnings / Settings), so it never interrupts sending the first bill.
- **Closing it:** it can be closed. After that, New order asks for the plan, as now.
- **No app release:** the server sends `paywallGroup: "free1"` for a `first_order` gate, and app 1.0.13+ draws it (`FirstOrderPaywall`).

Accounts already in `free1_monthly_499` keep the wall at the 2nd New order. To give them the pop-up as well, edit that variant's `gate` to `first_order` instead. That changes it for everyone already in it, and mixes its readout.

## Apply

The auto-mode classifier blocked the prod `.env` edit from the job; the dry run passed.

```sh
DRY_RUN=1 bash docs/deploy/2026-10-25-first-order-paywall-deploy.sh   # prints the new variant + split
bash docs/deploy/2026-10-25-first-order-paywall-deploy.sh             # applies; recreates backend + laundry-dash
```

- **Backup:** written next to `.env` (`.env.bak-first-order-<time>`).
- **Success check:** an empty `[paywall]` log after the restart means the setting was accepted.
- **Rollback:** restore the backup, then `docker compose -f docker-compose.prod.yml up -d --force-recreate backend` and do the same for `laundry-dash`. Accounts created in between fall back to `free1_monthly_499`.
