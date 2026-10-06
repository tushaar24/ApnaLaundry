import type { LedgerEntry, Order, Service } from "./models";

/**
 * Pure money maths. Port of domain/LaundryMath.kt (PRODUCT_SPEC §4, §8).
 * No storage or UI dependencies.
 */

export function clothesOf(o: Order): number {
  return o.lines.reduce((s, l) => s + l.amt, 0);
}

/** Order total = clothes + express + fee − discount. Never stored separately. */
export function amtOf(o: Order): number {
  return clothesOf(o) + (o.express ? o.exAmt : 0) + o.fee - o.discount;
}

/** Express amount = round(clothes × pct / 100). */
export function expressAuto(clothesTotal: number, pct: number): number {
  return Math.round((clothesTotal * pct) / 100);
}

export function serviceName(id: string, services: Service[], snapshot?: string | null): string {
  const s = services.find((sv) => sv.id === id);
  if (s) return s.name;
  if (snapshot && snapshot.trim()) return snapshot;
  switch (id) {
    case "wf": return "Wash & Fold";
    case "wi": return "Wash & Iron";
    case "io": return "Iron Only";
    case "dc": return "Dry Cleaning";
    case "quick": return "Not itemised";
    default: return "Other service";
  }
}

/** Not yet delivered or cancelled — its total is in the baaki but has no `BILL` entry yet. */
export function isOpen(o: Order): boolean {
  return o.status !== "DELIVERED" && o.status !== "CANCELLED";
}

/**
 * Khata balance for a customer.
 * balance = Σ bills + Σ old baaki + Σ bill changes + Σ open orders − Σ payments.
 * An open order counts at its current total from the moment it is created; on
 * delivery it is replaced by its `BILL` entry. `exceptOrder` leaves one open
 * order and its prepayments out (the "old" side of a delivery).
 */
export function balance(custId: string, ledger: LedgerEntry[], orders: Order[], exceptOrder?: number): number {
  let b = 0;
  for (const e of ledger) {
    if (e.custId !== custId) continue;
    if (e.kind === "BILL" || e.kind === "OLD" || e.kind === "ADJ") {
      b += e.amt;
    } else {
      // GOT
      if (e.tag === "PRE" && e.ref === exceptOrder) continue;
      b -= e.amt;
    }
  }
  for (const o of orders) {
    if (o.custId === custId && o.id !== exceptOrder && isOpen(o)) b += amtOf(o);
  }
  return b;
}

export interface Allocation {
  cover: number; // paid this bill
  toOld: number; // cleared old baaki
  toAdv: number; // kept as advance
  newBalance: number; // customer khata after this
  billDue: number; // bill remaining before this payment
  paidToward: number; // order.paid after this
}

/**
 * Allocation of `amountReceived` at delivery (PRODUCT_SPEC §8).
 *   billDue = total − prepaid
 *   cover   = min(received, billDue)
 *   rest    = received − cover
 *   toOld   = min(rest, oldBaaki if positive)
 *   toAdv   = rest − toOld
 */
export function deliverAllocation(
  total: number,
  prepaid: number,
  oldBalance: number,
  amountReceived: number,
): Allocation {
  const billDue = Math.max(0, total - prepaid);
  const cover = Math.min(amountReceived, billDue);
  const rest = amountReceived - cover;
  const toOld = Math.min(rest, Math.max(0, oldBalance));
  const toAdv = rest - toOld;
  const newBalance = oldBalance + total - prepaid - amountReceived;
  return { cover, toOld, toAdv, newBalance, billDue, paidToward: prepaid + cover };
}

/** Allocation when receiving a standalone khata payment (not tied to a delivery). */
export function receiveAllocation(oldBalance: number, amountReceived: number): [number, number] {
  const toOld = Math.min(amountReceived, Math.max(0, oldBalance));
  return [toOld, amountReceived - toOld]; // toOld, toAdv
}

/** Weight line amount = round(max(kg, minKg) × ratePerKg). */
export function weightAmount(kg: number, ratePerKg: number, minKg: number): number {
  return Math.round(Math.max(kg, minKg) * ratePerKg);
}

export function paymentLabel(paid: number, total: number): string {
  if (paid >= total && total > 0) return "Paid";
  if (paid > 0) return "Part paid";
  return "In khata";
}
