"use client";

import * as AppDate from "@/core/appdate";
import { rupees } from "@/core/money";
import { syncNow as clockNow } from "@/core/syncclock";
import {
  amtOf, balance, deliverAllocation, expressAuto, receiveAllocation,
} from "@/domain/laundryMath";
import type {
  LedgerEntry, LedgerKind, Order, OrderLine, OrderStatus, PayMethod, PayTag, Route, Service,
} from "@/domain/models";
import { nextTs } from "./ids";
import { deriveState, useAppStore, type Snapshot } from "./store";
import { requestSync } from "./sync";
import type { LedgerRow, OrderRow, Rows } from "./rows";
import { stamp } from "./rows";
import { Analytics } from "@/analytics/events";

/**
 * Commands over the in-memory rows — a 1:1 port of data/LaundryRepository.kt
 * plus the toast/Undo publishing of ShopViewModel. Every undoable command
 * captures a Snapshot first; `undo()` restores it (restored rows re-stamped
 * dirty, rows created after the snapshot turned into tombstones so the
 * rollback syncs instead of being resurrected by the next pull).
 */

export interface CmdResult {
  toast: string;
  undo?: Snapshot;
}

let undoSnapshot: Snapshot | null = null;

// ---------------- helpers ----------------

function rows(): Rows {
  return useAppStore.getState().rows;
}

function setRows(mut: (r: Rows) => Rows) {
  useAppStore.getState().setRows(mut);
}

function firstName(name: string): string {
  return name.startsWith("+") ? name : name.split(" ")[0];
}

function waReady(nm: string): string {
  return AppDate.isLateNight() ? `ready message to ${nm} goes at 9 AM` : `WhatsApp sent to ${nm}`;
}

function custName(custId: string): string {
  const c = rows().customers.find((c) => c.id === custId);
  return firstName(c?.name ?? "—");
}

function orderOf(id: number): OrderRow {
  const o = rows().orders.find((o) => o.id === id && !o.deleted);
  if (!o) throw new Error(`Order #${id} not found`);
  return o;
}

interface EntryArgs {
  cust: string;
  kind: LedgerKind;
  amt: number;
  method?: PayMethod;
  tag?: PayTag;
  cover?: number;
  toOld?: number;
  toAdv?: number;
  ref?: number | null;
  note?: string;
}

function mkEntry(a: EntryArgs): LedgerRow {
  const ts = nextTs();
  const e: LedgerEntry = {
    id: `e${ts}`, custId: a.cust, date: AppDate.today(), time: "Just now", ts,
    kind: a.kind, amt: a.amt, method: a.method ?? "NONE", tag: a.tag ?? "NONE",
    cover: a.cover ?? 0, toOld: a.toOld ?? 0, toAdv: a.toAdv ?? 0,
    ref: a.ref ?? null, note: a.note ?? "",
  };
  return { ...e, ...stamp(), deleted: false };
}

function updateOrder(id: number, patch: Partial<Order>) {
  setRows((r) => ({
    ...r,
    orders: r.orders.map((o) => (o.id === id ? { ...o, ...patch, ...stamp() } : o)),
  }));
}

function insertLedger(...entries: LedgerRow[]) {
  setRows((r) => ({ ...r, ledger: [...r.ledger, ...entries] }));
}

function publish(res: CmdResult) {
  undoSnapshot = res.undo ?? null;
  useAppStore.getState().showToast(res.toast, !!res.undo);
  requestSync();
}

export function showInfo(text: string) {
  undoSnapshot = null;
  useAppStore.getState().showToast(text, false);
}

// ---------------- snapshot / undo ----------------

export function snapshot(): Snapshot {
  const r = rows();
  return {
    orders: r.orders, ledger: r.ledger, customers: r.customers, services: r.services, shop: r.shop,
  };
}

export function undo() {
  const s = undoSnapshot;
  if (!s) return;
  const now = clockNow();
  setRows((cur) => {
    function restore<T extends { dirty: boolean; updatedAt: number; deleted: boolean }, K>(
      snap: T[], current: T[], key: (t: T) => K,
    ): T[] {
      const keep = new Set(snap.map(key));
      const tombs = current
        .filter((t) => !keep.has(key(t)))
        .map((t) => ({ ...t, deleted: true, dirty: true, updatedAt: now }));
      return [...snap.map((t) => ({ ...t, dirty: true, updatedAt: now })), ...tombs];
    }
    return {
      ...cur,
      orders: restore(s.orders, cur.orders, (o) => o.id),
      ledger: restore(s.ledger, cur.ledger, (l) => l.id),
      customers: restore(s.customers, cur.customers, (c) => c.id),
      services: restore(s.services, cur.services, (sv) => sv.id),
      shop: s.shop ? { ...s.shop, dirty: true, updatedAt: now } : cur.shop,
    };
  });
  undoSnapshot = null;
  useAppStore.getState().dismissToast();
  requestSync();
}

// ---------------- seeding / lifecycle ----------------

/** First-login bootstrap for a NEW account: shop defaults + default rate card only. */
export async function ensureSeeded(shopPhone: string | null) {
  if (rows().shop) return;
  const { seedServices, DEFAULT_EXPRESS_PCT } = await import("@/domain/seed");
  setRows((r) => ({
    ...r,
    shop: {
      name: "MyLaundry", phone: shopPhone ?? "", expressPct: DEFAULT_EXPRESS_PCT,
      nextOrder: 1001, nextCust: 1, ...stamp(),
    },
    services: seedServices.map((s) => ({ ...s, ...stamp(), deleted: false })),
  }));
}

export function hasShop(): boolean {
  return rows().shop != null;
}

/** Wipe everything (logout). The next login pulls or reseeds. */
export function clearAll() {
  setRows(() => ({ shop: null, services: [], customers: [], orders: [], ledger: [], dayCloses: [] }));
  undoSnapshot = null;
}

// ---------------- order status ----------------

export function markPickedUp(orderId: number) {
  const undoSnap = snapshot();
  const o = orderOf(orderId);
  updateOrder(orderId, { status: "RECEIVED" });
  Analytics.orderPickedUp(orderId);
  publish({ toast: `Picked up · ${custName(o.custId)}`, undo: undoSnap });
}

export function markReady(orderId: number) {
  const undoSnap = snapshot();
  const o = orderOf(orderId);
  updateOrder(orderId, { status: "READY" });
  Analytics.orderMarkedReady(orderId);
  publish({ toast: `Marked ready · ${waReady(custName(o.custId))}`, undo: undoSnap });
}

/** Count-clothes sheet: attach lines and advance to `next` (received or ready). */
export function saveCount(orderId: number, next: OrderStatus, lines: OrderLine[]) {
  const undoSnap = snapshot();
  const o = orderOf(orderId);
  const nm = custName(o.custId);
  const st = deriveState(rows());
  const total = lines.reduce((s, l) => s + l.amt, 0);
  const exAmt = o.express ? expressAuto(total, st.shop.expressPct) : o.exAmt;
  updateOrder(orderId, { status: next, lines, exAmt, billSent: false });
  Analytics.clothesCounted(orderId, next, total);
  const head = next === "READY" ? `Marked ready · ${waReady(nm)}` : "Picked up";
  publish({ toast: `${head} · bill of ${rupees(total)} made — send it from the order`, undo: undoSnap });
}

export function deliver(orderId: number, amountReceived: number, method: PayMethod) {
  const undoSnap = snapshot();
  const o = orderOf(orderId);
  const st = deriveState(rows());
  const total = amtOf(o);
  const pre = o.pre;
  const oldBal = balance(o.custId, st.ledger, st.orders, o.id);
  const alloc = deliverAllocation(total, pre, oldBal, amountReceived);
  const entries: LedgerRow[] = [mkEntry({ cust: o.custId, kind: "BILL", amt: total, ref: o.id })];
  if (amountReceived > 0) {
    entries.push(mkEntry({
      cust: o.custId, kind: "GOT", amt: amountReceived, method, tag: "DELIVER",
      cover: alloc.cover, toOld: alloc.toOld, toAdv: alloc.toAdv, ref: o.id,
    }));
  }
  insertLedger(...entries);
  updateOrder(orderId, { status: "DELIVERED", doneAt: "Just now", doneDate: AppDate.today(), paid: alloc.paidToward });
  Analytics.orderDelivered({
    orderId, total, amountReceived, method,
    toKhata: Math.max(0, total - alloc.paidToward), fromAdvance: pre,
    items: o.lines.map((l) => ({ service: l.serviceName, item: l.itemName, qty: l.qty, amount: l.amt })),
  });
  if (amountReceived > 0) {
    Analytics.paymentReceived({ amount: amountReceived, method, type: "delivery", customerId: o.custId, orderId });
  }
  const paidPart = amountReceived > 0 ? ` · ${rupees(amountReceived)} ${method === "UPI" ? "UPI" : "cash"}` : "";
  const balPart = alloc.newBalance > 0
    ? ` · ${rupees(alloc.newBalance)} baaki`
    : alloc.newBalance < 0
      ? ` · ${rupees(alloc.newBalance)} advance`
      : " · all clear";
  publish({ toast: `Delivered${paidPart}${balPart}`, undo: undoSnap });
}

export function prepay(orderId: number, method: PayMethod) {
  const undoSnap = snapshot();
  const o = orderOf(orderId);
  const total = amtOf(o);
  insertLedger(mkEntry({ cust: o.custId, kind: "GOT", amt: total, method, tag: "PRE", cover: total, ref: o.id }));
  updateOrder(orderId, { pre: total });
  Analytics.paymentReceived({ amount: total, method, type: "prepay", customerId: o.custId, orderId });
  publish({ toast: `Got ${rupees(total)} ${method === "UPI" ? "UPI" : "cash"} · order fully paid`, undo: undoSnap });
}

export function cancelOrder(orderId: number, reason: string) {
  const undoSnap = snapshot();
  const o = orderOf(orderId);
  updateOrder(orderId, { status: "CANCELLED", cancelReason: reason });
  Analytics.orderCancelled(orderId, reason);
  publish({ toast: `Pickup cancelled · ${custName(o.custId)}`, undo: undoSnap });
}

/** kind = "pickup" or "drop". */
export function reschedule(orderId: number, kind: string, dateIso: string, time24: string, notify: boolean) {
  const undoSnap = snapshot();
  const o = orderOf(orderId);
  const time12 = AppDate.to12h(time24);
  let msg: string;
  if (kind === "pickup") {
    const patch: Partial<Order> = { pickupDate: dateIso, pickupTime: time12 };
    if (o.deliveryDate !== "" && o.ddAuto) {
      const shift = AppDate.daysBetween(o.pickupDate, dateIso);
      patch.deliveryDate = AppDate.add(o.deliveryDate, shift);
    }
    updateOrder(orderId, patch);
    msg = `Pickup moved to ${AppDate.short(dateIso)}` + (time12 ? ` · ${time12}` : "");
  } else {
    updateOrder(orderId, { deliveryDate: dateIso, deliveryTime: time12, ddAuto: false });
    const prefix = o.deliveryDate !== "" ? "Delivery moved to " : "Delivery date set: ";
    msg = prefix + AppDate.short(dateIso) + (time12 ? ` · ${time12}` : "");
  }
  Analytics.orderRescheduled(orderId, kind === "pickup" ? "pickup" : "delivery", notify);
  publish({ toast: msg + (notify ? " · WhatsApp sent" : ""), undo: undoSnap });
}

export function sendBill(orderId: number) {
  const o = orderOf(orderId);
  updateOrder(orderId, { billSent: true });
  Analytics.billSent(orderId);
  publish({ toast: `Opening WhatsApp · bill #${orderId} to ${custName(o.custId)}` });
}

// ---------------- khata ----------------

export function receivePayment(custId: string, amount: number, method: PayMethod) {
  const undoSnap = snapshot();
  const st = deriveState(rows());
  const oldBal = balance(custId, st.ledger, st.orders);
  const [toOld, toAdv] = receiveAllocation(oldBal, amount);
  insertLedger(mkEntry({ cust: custId, kind: "GOT", amt: amount, method, tag: "RECEIVE", toOld, toAdv }));
  Analytics.paymentReceived({ amount, method, type: "khata", customerId: custId });
  const after = oldBal - amount;
  const tail = after > 0 ? `${rupees(after)} still baaki` : after < 0 ? `${rupees(after)} advance` : "all clear";
  publish({ toast: `Got ${rupees(amount)} ${method === "UPI" ? "UPI" : "cash"} · ${tail}`, undo: undoSnap });
}

export function addOldBaaki(custId: string, amount: number) {
  const undoSnap = snapshot();
  insertLedger(mkEntry({ cust: custId, kind: "OLD", amt: amount }));
  Analytics.oldBaakiAdded(amount, custId);
  publish({ toast: `Added ${rupees(amount)} old baaki`, undo: undoSnap });
}

// ---------------- customers ----------------

/** Returns the customer id (new or existing on edit). */
export function saveCustomer(
  editId: string | null, name: string, phone: string, address: string, oldBaaki: number, fromList: boolean,
): string {
  const undoSnap = snapshot();
  if (editId != null) {
    setRows((r) => ({
      ...r,
      customers: r.customers.map((c) => (c.id === editId ? { ...c, name, phone, address, ...stamp() } : c)),
    }));
    requestSync();
    return editId;
  }
  const shop = rows().shop;
  if (!shop) throw new Error("No shop");
  const id = `c${shop.nextCust}`;
  setRows((r) => ({
    ...r,
    customers: [
      ...r.customers,
      { id, name, phone, address, pastOrders: 0, lastLabel: "—", agoRank: 9, ...stamp(), deleted: false },
    ],
    shop: r.shop ? { ...r.shop, nextCust: r.shop.nextCust + 1, ...stamp() } : r.shop,
  }));
  if (oldBaaki > 0) insertLedger(mkEntry({ cust: id, kind: "OLD", amt: oldBaaki }));
  Analytics.customerAdded(fromList ? "list" : "order", oldBaaki > 0);
  if (fromList) {
    publish({ toast: `${name} added` + (oldBaaki > 0 ? ` with ${rupees(oldBaaki)} baaki` : ""), undo: undoSnap });
  } else {
    requestSync();
  }
  return id;
}

// ---------------- rate card ----------------

export function upsertService(service: Service) {
  setRows((r) => {
    const exists = r.services.some((s) => s.id === service.id);
    const row = { ...service, ...stamp(), deleted: false };
    return {
      ...r,
      services: exists ? r.services.map((s) => (s.id === service.id ? { ...s, ...row } : s)) : [...r.services, row],
    };
  });
  requestSync();
}

export function deleteService(id: string) {
  const undoSnap = snapshot();
  const services = rows().services.filter((s) => !s.deleted);
  if (services.length <= 1) {
    publish({ toast: "Keep at least one service" });
    return;
  }
  const sv = services.find((s) => s.id === id);
  if (!sv) return;
  setRows((r) => ({
    ...r,
    services: r.services.map((s) => (s.id === id ? { ...s, deleted: true, ...stamp() } : s)),
  }));
  Analytics.serviceDeleted(id);
  publish({ toast: `${sv.name} deleted · old orders keep it`, undo: undoSnap });
}

export function updateShop(name: string, expressPct: number) {
  setRows((r) => ({
    ...r,
    shop: r.shop ? { ...r.shop, name, expressPct, ...stamp() } : r.shop,
  }));
  // Keep the CleverTap profile's Name current with the shop name.
  if (name.trim() !== "") Analytics.updateProfile({ Name: name.trim() });
  requestSync();
}

/** Line-count helpers for the Order Saved event. */
function orderMetrics(o: Order): { pieces: number; kg: number; servicesCount: number } {
  let pieces = 0;
  let kg = 0;
  for (const l of o.lines) {
    if (l.kg > 0) kg += l.kg;
    else pieces += l.qty;
  }
  const servicesCount = new Set(o.lines.map((l) => l.serviceId)).size;
  return { pieces, kg, servicesCount };
}

// ---------------- new / edit order ----------------

export interface SaveOrderResult {
  orderId: number;
  goToBill: boolean;
  edited: boolean;
}

export interface SaveOrderArgs {
  editId: number | null;
  custId: string;
  pickup: Route;
  delivery: Route;
  pickupDate: string;
  pickupTime24: string;
  deliveryDate: string;
  deliveryTime24: string;
  ddAuto: boolean;
  fee: number;
  express: boolean;
  exAmt: number;
  discount: number;
  lines: OrderLine[];
  quickAmount: number;
  quickPieces: number;
}

function quickLine(amount: number, pieces: number): OrderLine {
  return {
    serviceId: "quick", serviceName: "Not itemised", itemName: "Clothes (not itemised)",
    qty: pieces, price: 0, base: 0, kg: 0, amt: amount, isQuick: true,
  };
}

function touchCustomer(custId: string) {
  setRows((r) => ({
    ...r,
    customers: r.customers.map((c) => (c.id === custId ? { ...c, lastLabel: "Today", agoRank: 0, ...stamp() } : c)),
  }));
}

export function saveOrder(a: SaveOrderArgs): SaveOrderResult {
  const undoSnap = snapshot();
  const st = deriveState(rows());
  const anyHome = a.pickup === "HOME" || a.delivery === "HOME";
  const fields = {
    custId: a.custId, pickup: a.pickup, delivery: a.delivery, pickupDate: a.pickupDate,
    pickupTime: AppDate.to12h(a.pickupTime24), deliveryDate: a.deliveryDate,
    deliveryTime: AppDate.to12h(a.deliveryTime24), ddAuto: a.ddAuto,
    fee: anyHome ? a.fee : 0, express: a.express, exAmt: a.express ? a.exAmt : 0,
    discount: a.discount, lines: a.lines,
  };
  const nm = custName(a.custId);

  if (a.editId != null) {
    const o = orderOf(a.editId);
    // keep lines of deleted services + any quick line
    const kept = o.lines.filter((l) => !l.isQuick && !st.services.some((s) => s.id === l.serviceId));
    if (a.quickAmount > 0) kept.push(quickLine(a.quickAmount, a.quickPieces));
    const newLines = [...kept, ...a.lines];
    let updated: Order = {
      ...o,
      custId: fields.custId, pickup: fields.pickup, delivery: fields.delivery,
      pickupDate: fields.pickupDate, pickupTime: fields.pickupTime,
      deliveryDate: fields.deliveryDate, deliveryTime: fields.deliveryTime,
      ddAuto: fields.ddAuto, fee: fields.fee, express: fields.express, exAmt: fields.exAmt,
      discount: fields.discount, lines: newLines,
    };
    if (updated.status === "CREATED" && newLines.length > 0 && a.pickup === "SHOP") {
      updated = { ...updated, status: "RECEIVED" };
    }
    const before = amtOf(o);
    const after = amtOf(updated);
    const diff = after - before;
    if (diff !== 0) updated = { ...updated, billSent: false };
    updateOrder(o.id, updated);
    if (o.status === "DELIVERED" && diff !== 0) {
      insertLedger(mkEntry({ cust: o.custId, kind: "ADJ", amt: diff, ref: o.id }));
    }
    const khataPart = o.status === "DELIVERED" && diff !== 0
      ? ` · khata ${diff > 0 ? "+" : "−"}${rupees(Math.abs(diff))}`
      : "";
    const totalPart = diff !== 0 ? ` · new total ${rupees(after)}` : "";
    {
      const met = orderMetrics(updated);
      Analytics.orderSaved({
        orderId: o.id, isEdit: true, hasBill: newLines.length > 0, pickup: updated.pickup,
        delivery: updated.delivery, express: updated.express, discount: updated.discount,
        fee: updated.fee, pieces: met.pieces, kg: met.kg, servicesCount: met.servicesCount,
        total: after, quickBill: newLines.some((l) => l.isQuick),
      });
    }
    publish({ toast: `Order #${o.id} updated${totalPart}${khataPart}`, undo: undoSnap });
    return { orderId: o.id, goToBill: false, edited: true };
  }

  // create
  const shop = rows().shop;
  if (!shop) throw new Error("No shop");
  const id = shop.nextOrder;
  const status: OrderStatus = a.pickup === "SHOP" ? "RECEIVED" : "CREATED";
  const order: Order = {
    id, custId: fields.custId, pickup: fields.pickup, delivery: fields.delivery,
    pickupDate: fields.pickupDate, pickupTime: fields.pickupTime,
    deliveryDate: fields.deliveryDate, deliveryTime: fields.deliveryTime,
    ddAuto: fields.ddAuto, status, cancelReason: "", fee: fields.fee,
    express: fields.express, exAmt: fields.exAmt, discount: fields.discount,
    pre: 0, paid: 0, doneAt: "", doneDate: "", createdOn: AppDate.today(),
    billSent: false, pieces: 0, lines: fields.lines,
  };
  setRows((r) => ({
    ...r,
    orders: [...r.orders, { ...order, ...stamp(), deleted: false }],
    shop: r.shop ? { ...r.shop, nextOrder: id + 1, ...stamp() } : r.shop,
  }));
  touchCustomer(a.custId);

  {
    const met = orderMetrics(order);
    Analytics.orderSaved({
      orderId: id, isEdit: false, hasBill: fields.lines.length > 0, pickup: order.pickup,
      delivery: order.delivery, express: order.express, discount: order.discount,
      fee: order.fee, pieces: met.pieces, kg: met.kg, servicesCount: met.servicesCount,
      total: amtOf(order), quickBill: false,
    });
  }

  if (fields.lines.length === 0) {
    const toast = a.pickup === "HOME"
      ? `Pickup scheduled for ${nm}` + (order.pickupTime ? ` · ${order.pickupTime}` : "")
      : `Order #${id} saved for ${nm} · add clothes when ready`;
    publish({ toast, undo: undoSnap });
    return { orderId: id, goToBill: false, edited: false };
  }
  requestSync();
  return { orderId: id, goToBill: true, edited: false };
}
