import type { LaundryState, LedgerEntry } from "./models";
import { amtOf, balance, serviceName } from "./laundryMath";

/**
 * Earnings, computed live from the ledger and orders. Port of
 * domain/EarningsMath.kt. Because everything is derived, money reconciles:
 *   received = work − baakiAdded + oldIn + advIn + prepaid
 *   received = cash + upi
 */

export interface ServiceShare {
  label: string;
  amount: number;
  isExtra: boolean;
}

export interface Earnings {
  received: number;
  cash: number;
  upi: number;
  work: number;
  baakiAdded: number;
  oldIn: number;
  advIn: number;
  prepaid: number;
  ordersDelivered: number;
  pieces: number;
  kg: number;
  byService: ServiceShare[];
  discounts: number;
  baakiMarket: number;
  baakiCustomers: number;
}

export function computeEarnings(state: LaundryState, inRange: (iso: string) => boolean): Earnings {
  const gots = state.ledger.filter((e) => e.kind === "GOT" && inRange(e.date));
  let received = 0;
  let cash = 0;
  let oldIn = 0;
  let advIn = 0;
  for (const e of gots) {
    received += e.amt;
    if (e.method === "CASH") cash += e.amt;
    oldIn += e.toOld;
    advIn += e.toAdv;
  }

  const delivered = state.orders.filter((o) => o.status === "DELIVERED" && inRange(o.doneDate));
  let work = 0;
  let baakiAdded = 0;
  let pieces = 0;
  let kg = 0;
  let discounts = 0;
  const svc = new Map<string, number>();
  let express = 0;
  let fee = 0;
  for (const o of delivered) {
    const a = amtOf(o);
    work += a;
    baakiAdded += Math.max(0, a - o.paid);
    for (const l of o.lines) {
      svc.set(l.serviceId, (svc.get(l.serviceId) ?? 0) + l.amt);
      if (l.kg > 0) kg += l.kg;
      else pieces += l.qty;
    }
    fee += o.fee;
    express += o.express ? o.exAmt : 0;
    discounts += o.discount;
  }
  const prepaid = received - (work - baakiAdded + oldIn + advIn);

  const shares: ServiceShare[] = [];
  for (const [id, amt] of svc) {
    if (amt > 0) shares.push({ label: serviceName(id, state.services), amount: amt, isExtra: false });
  }
  if (express > 0) shares.push({ label: "Express charges", amount: express, isExtra: true });
  if (fee > 0) shares.push({ label: "Pickup / delivery charges", amount: fee, isExtra: true });
  shares.sort((a, b) => (Number(a.isExtra) - Number(b.isExtra)) || (b.amount - a.amount));

  const baakiCust = state.customers.filter((c) => balance(c.id, state.ledger, state.orders) > 0);
  const baakiMarket = baakiCust.reduce((s, c) => s + balance(c.id, state.ledger, state.orders), 0);

  return {
    received,
    cash,
    upi: received - cash,
    work,
    baakiAdded,
    oldIn,
    advIn,
    prepaid,
    ordersDelivered: delivered.length,
    pieces,
    kg,
    byService: shares,
    discounts,
    baakiMarket,
    baakiCustomers: baakiCust.length,
  };
}

/** Money received on a single day (any source) — used by the Home dashboard strip. */
export function collectedOn(iso: string, ledger: LedgerEntry[]): number {
  return ledger.filter((e) => e.kind === "GOT" && e.date === iso).reduce((s, e) => s + e.amt, 0);
}
