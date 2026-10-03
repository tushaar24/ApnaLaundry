import type { Customer, LaundryState, Order } from "./models";
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

export function balance(state: LaundryState, custId: string): number {
  return khataBalance(custId, state.ledger, state.orders);
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

/** "3 items", "2 kg", "3 items + 2 kg", "Not itemised". */
export function itemsLabel(o: Order): string {
  if (o.lines.length === 0) return o.pieces > 0 ? `${o.pieces} pieces` : "";
  if (o.lines.every((l) => l.isQuick && l.qty === 0)) return "Not itemised";
  let pieces = 0;
  let kg = 0;
  for (const l of o.lines) {
    if (l.kg > 0) kg += l.kg;
    else pieces += l.qty;
  }
  const parts: string[] = [];
  if (pieces > 0) parts.push(`${pieces} ${pieces === 1 ? "item" : "items"}`);
  if (kg > 0) parts.push(`${trimKg(kg)} kg`);
  return parts.join(" + ");
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
