import type { Customer, LaundryState, Order, OrderLine } from "./models";
import { balance as khataBalance } from "./laundryMath";

/** Small derived-data helpers shared by screens. Port of ui/Selectors.kt. */

const unknownCustomer: Customer = {
  id: "", name: "—", phone: "0000000000", address: "", pastOrders: 0, lastLabel: "", agoRank: 9,
};

export function customer(state: LaundryState, id: string): Customer {
  return state.customers.find((c) => c.id === id) ?? unknownCustomer;
}

export function order(state: LaundryState, id: number): Order | undefined {
  return state.orders.find((o) => o.id === id);
}

export function balance(state: LaundryState, custId: string, exceptOrder?: number): number {
  return khataBalance(custId, state.ledger, state.orders, exceptOrder);
}

export function firstName(name: string): string {
  return name.startsWith("+") ? name : name.split(" ")[0];
}

export function fmtPhone(d: string): string {
  return d.length === 10 ? d.slice(0, 5) + " " + d.slice(5) : d;
}

export function initials(name: string): string {
  return name
    .split(" ")
    .filter((p) => p.trim())
    .slice(0, 2)
    .map((p) => p[0].toUpperCase())
    .join("");
}

/** The number shown as "#…" on an order and its bill: the owner's serial, else the order id. */
export function orderNo(o: { id: number; serialNo?: string }): string {
  return o.serialNo?.trim() || String(o.id);
}

/** "3 items", "2 kg", "2 kg (12 clothes)", "3 items + 2 kg", "Not itemised". */
export function itemsLabel(o: Order): string {
  if (o.lines.length === 0) return o.pieces > 0 ? `${o.pieces} pieces` : "";
  if (o.lines.every((l) => l.isQuick && l.qty === 0)) return "Not itemised";
  let pieces = 0;
  let kg = 0;
  let kgClothes = 0; // optional count of clothes in the by-weight bags
  for (const l of o.lines) {
    if (l.kg > 0) { kg += l.kg; kgClothes += l.qty; }
    else pieces += l.qty;
  }
  const parts: string[] = [];
  if (pieces > 0) parts.push(`${pieces} ${pieces === 1 ? "item" : "items"}`);
  if (kg > 0) parts.push(`${trimKg(kg)} kg` + (kgClothes > 0 ? ` (${kgClothes} clothes)` : ""));
  return parts.join(" + ");
}

/** "Wash & Fold · 4 kg" or, with the clothes counted, "Wash & Fold · 4 kg · 12 clothes". */
export function weightLabel(l: OrderLine): string {
  return `${l.serviceName} · ${trimKg(l.kg)} kg` + (l.qty > 0 ? ` · ${l.qty} ${l.qty === 1 ? "cloth" : "clothes"}` : "");
}

export function svcLabel(o: Order): string {
  return [...new Set(o.lines.map((l) => l.serviceName))].join(" + ");
}

export function trimKg(kg: number): string {
  return kg % 1 === 0 ? String(Math.trunc(kg)) : String(kg);
}

/** Order count shown for a customer (notebook history + app orders, minus cancelled). */
export function orderCount(state: LaundryState, c: Customer): number {
  return c.pastOrders + state.orders.filter((o) => o.custId === c.id && o.status !== "CANCELLED").length;
}

/** "1 order" / "3 orders" — counted label with the right plural. */
export function countNoun(n: number, noun: string): string {
  return `${n} ${noun}${n === 1 ? "" : "s"}`;
}
