"use client";

import * as AppDate from "@/core/appdate";
import { rupees } from "@/core/money";
import { syncNow as clockNow } from "@/core/syncclock";
import {
  amtOf, balance, deliverAllocation, expressAuto, receiveAllocation,
withPctExtras, isOpen, } from "@/domain/laundryMath";
import type {
  BillDetails, LedgerEntry, LedgerKind, OnboardingStep, Order, OrderLine, OrderStatus, PayMethod, PayTag, Route, Service,
} from "@/domain/models";
import { nextTs } from "./ids";
import { deriveState, useAppStore, type Snapshot } from "./store";
import { requestSync } from "./sync";
import type { LedgerRow, OrderRow, Rows } from "./rows";
import { stamp } from "./rows";
import { Analytics } from "@/analytics/events";
import { DEFAULT_SHOP_NAME, isDefaultShopName } from "@/domain/seed";
import { emptyBillDetails } from "@/domain/billDetails";
import { gstDefaults, type GstFields } from "@/domain/gst";
import { orderNo } from "@/domain/selectors";
import { statusMessage, waLink } from "@/domain/statusMessage";

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
  /** A wa.me link with a status update for the customer — a dialog offers to send it. */
  wa?: string;
}

let undoSnapshot: Snapshot | null = null;

// ---------------- helpers ----------------

function rows(): Rows {
  return useAppStore.getState().rows;
}

function setRows(mut: (r: Rows) => Rows) {
  // Any new change ends the previous command's Undo: its snapshot no longer
  // matches, and restoring it would wipe this change. A command that offers
  // its own Undo sets a fresh snapshot right after (publish).
  invalidateUndo();
  useAppStore.getState().setRows(mut);
}

/** Drops the pending Undo (keeps the toast text, without its Undo button). */
export function invalidateUndo() {
  if (!undoSnapshot) return;
  undoSnapshot = null;
  const t = useAppStore.getState().toast;
  if (t?.hasUndo) useAppStore.getState().showToast(t.text, false);
}

/** The customer's status update for an order, as a wa.me link (its state right now). */
function statusWa(orderId: number): string | undefined {
  const o = orderOf(orderId);
  const c = rows().customers.find((x) => x.id === o.custId);
  return c ? waLink(c.phone, statusMessage(rows().shop?.name ?? "", c.name, o)) : undefined;
}

function firstName(name: string): string {
  return name.startsWith("+") ? name : name.split(" ")[0];
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
    id: `e${ts}`, custId: a.cust, date: AppDate.today(), time: AppDate.nowText(), ts,
    kind: a.kind, amt: a.amt, method: a.method ?? "NONE", tag: a.tag ?? "NONE",
    cover: a.cover ?? 0, toOld: a.toOld ?? 0, toAdv: a.toAdv ?? 0,
    ref: a.ref ?? null, note: a.note ?? "",
  };
  return { ...e, ...stamp(), deleted: false };
}

function updateOrder(id: number, patch: Partial<Order>) {
  setRows((r) => ({
    ...r,
    // A deleted order stays deleted: later writes never bring it back.
    orders: r.orders.map((o) => (o.id === id && !o.deleted ? { ...o, ...patch, ...stamp() } : o)),
  }));
}

function insertLedger(...entries: LedgerRow[]) {
  setRows((r) => ({ ...r, ledger: [...r.ledger, ...entries] }));
}

function publish(res: CmdResult) {
  undoSnapshot = res.undo ?? null;
  useAppStore.getState().showToast(res.toast, !!res.undo);
  // A status change: ask whether to tell the customer on WhatsApp.
  if (res.wa) useAppStore.getState().askWhatsApp(res.wa);
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
  useAppStore.getState().setRows((cur) => {
    // Only rows this command changed go back (re-stamped so the rollback
    // syncs). Untouched rows keep their own updatedAt — re-stamping them would
    // let a stale copy beat a newer change from another device (e.g. bring
    // back an order deleted on the phone).
    function same<T extends { dirty: boolean; updatedAt: number }>(a: T, b: T | undefined): boolean {
      if (!b) return false;
      return JSON.stringify({ ...a, dirty: false, updatedAt: 0 }) === JSON.stringify({ ...b, dirty: false, updatedAt: 0 });
    }
    function restore<T extends { dirty: boolean; updatedAt: number; deleted: boolean }, K>(
      snap: T[], current: T[], key: (t: T) => K,
    ): T[] {
      const keep = new Set(snap.map(key));
      const cur = new Map(current.map((t) => [key(t), t] as const));
      const tombs = current
        .filter((t) => !keep.has(key(t)) && !t.deleted)
        .map((t) => ({ ...t, deleted: true, dirty: true, updatedAt: now }));
      const untouched = current.filter((t) => !keep.has(key(t)) && t.deleted);
      const back = snap.map((t) => (same(t, cur.get(key(t))) ? cur.get(key(t))! : { ...t, dirty: true, updatedAt: now }));
      return [...back, ...tombs, ...untouched];
    }
    return {
      ...cur,
      orders: restore(s.orders, cur.orders, (o) => o.id),
      ledger: restore(s.ledger, cur.ledger, (l) => l.id),
      customers: restore(s.customers, cur.customers, (c) => c.id),
      services: restore(s.services, cur.services, (sv) => sv.id),
      shop: s.shop && !same(s.shop, cur.shop ?? undefined) ? { ...s.shop, dirty: true, updatedAt: now } : cur.shop,
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
      name: DEFAULT_SHOP_NAME, phone: shopPhone ?? "", expressPct: DEFAULT_EXPRESS_PCT,
      ...emptyBillDetails(shopPhone ?? ""), ...gstDefaults(), onboardingStep: "intro",
      nextOrder: 1001, nextCust: 1, ...stamp(),
    },
    services: seedServices.map((s) => ({ ...s, ...stamp(), deleted: false })),
  }));
}

export function hasShop(): boolean {
  return rows().shop != null;
}

/** The shop has real activity (a customer or an order). */
export function hasShopActivity(): boolean {
  const r = rows();
  return r.customers.some((c) => !c.deleted) || r.orders.some((o) => !o.deleted);
}

/**
 * Onboarding (shop name + rate list) is behind this account. The server keeps
 * no flag and the seed is pushed at first login, so "has a shop" isn't enough:
 * a shop still on a placeholder name with no activity hasn't onboarded.
 */
export function isOnboarded(): boolean {
  const shop = rows().shop;
  if (shop == null) return false;
  // Shops created since onboarding was tracked carry their step.
  if (shop.onboardingStep !== "") return shop.onboardingStep === "done";
  return !isDefaultShopName(shop.name) || hasShopActivity();
}

/**
 * Whether a freshly pulled shop says onboarding is finished: true/false when
 * it carries a step (so a step finished on another device counts), else only
 * activity proves it (null = no opinion, keep the local flag).
 */
export function pulledSetupDone(): boolean | null {
  const shop = rows().shop;
  if (shop == null) return null;
  if (shop.onboardingStep !== "") return shop.onboardingStep === "done";
  return hasShopActivity() ? true : null;
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
  publish({ toast: `Picked up · ${custName(o.custId)}`, undo: undoSnap, wa: statusWa(orderId) });
}

/** Ready: the owner also says when it'll be delivered (like payment on delivery). */
export function markReady(orderId: number, deliveryDate: string) {
  const undoSnap = snapshot();
  const o = orderOf(orderId);
  updateOrder(orderId, deliveryDate ? { status: "READY", deliveryDate, ddAuto: false } : { status: "READY" });
  Analytics.orderMarkedReady(orderId);
  publish({ toast: `Marked ready · ${custName(o.custId)}`, undo: undoSnap, wa: statusWa(orderId) });
}

/** Count-clothes sheet: attach lines and advance to `next` (received or ready). */
export function saveCount(orderId: number, next: OrderStatus, lines: OrderLine[], deliveryDate = "") {
  const undoSnap = snapshot();
  const o = orderOf(orderId);
  const nm = custName(o.custId);
  const st = deriveState(rows());
  const total = lines.reduce((s, l) => s + l.amt, 0);
  // A % express / discount set when the pickup was booked applies now the clothes are counted.
  const { exAmt, discount } = withPctExtras({ ...o, lines }, st.shop.expressPct);
  updateOrder(orderId, {
    status: next, lines, exAmt, discount, billSent: false,
    // Counted straight to ready: the delivery date asked in the sheet.
    ...(next === "READY" && deliveryDate ? { deliveryDate, ddAuto: false } : {}),
  });
  Analytics.clothesCounted(orderId, next, total);
  const head = next === "READY" ? `Marked ready · ${nm}` : "Picked up";
  publish({ toast: `${head} · bill of ${rupees(total)} made — send it from the order`, undo: undoSnap, wa: statusWa(orderId) });
}

export function deliver(orderId: number, amountReceived: number, method: PayMethod) {
  deliverSplit(orderId, amountReceived > 0 ? [[method, amountReceived]] : []);
}

/**
 * Delivers with the money received in one or more parts (e.g. part cash, part
 * UPI). Each part is its own GOT entry, so Earnings' cash / UPI split stays
 * right; the allocation (this bill → old baaki → advance) is worked out on the
 * total and filled in part by part.
 */
export function deliverSplit(orderId: number, payments: [PayMethod, number][]) {
  const parts = payments.filter(([, amt]) => amt > 0);
  const amountReceived = parts.reduce((s, [, amt]) => s + amt, 0);
  const method: PayMethod = parts.slice().sort((a, b) => b[1] - a[1])[0]?.[0] ?? "NONE";
  const undoSnap = snapshot();
  const o = orderOf(orderId);
  // Already delivered: never bill twice. A cancelled order may be delivered —
  // that revives it (it has no BILL entry).
  if (o.status === "DELIVERED") return;
  const st = deriveState(rows());
  const total = amtOf(o);
  const pre = o.pre;
  const oldBal = balance(o.custId, st.ledger, st.orders, o.id);
  const alloc = deliverAllocation(total, pre, oldBal, amountReceived);
  const entries: LedgerRow[] = [mkEntry({ cust: o.custId, kind: "BILL", amt: total, ref: o.id })];
  let coverLeft = alloc.cover;
  let oldLeft = alloc.toOld;
  for (const [m, amt] of parts) {
    const cover = Math.min(amt, coverLeft); coverLeft -= cover;
    const toOld = Math.min(amt - cover, oldLeft); oldLeft -= toOld;
    entries.push(mkEntry({
      cust: o.custId, kind: "GOT", amt, method: m, tag: "DELIVER",
      cover, toOld, toAdv: amt - cover - toOld, ref: o.id,
    }));
  }
  insertLedger(...entries);
  // Delivered today: the delivery date / time become now, so it shows under
  // today's Deliveries whatever was planned (or if no date was set).
  const now = AppDate.nowText();
  updateOrder(orderId, {
    status: "DELIVERED", cancelReason: "", doneAt: now, doneDate: AppDate.today(),
    deliveryDate: AppDate.today(), deliveryTime: now, ddAuto: false, paid: alloc.paidToward,
  });
  Analytics.orderDelivered({
    orderId, total, amountReceived, method,
    toKhata: Math.max(0, total - alloc.paidToward), fromAdvance: pre,
    items: o.lines.map((l) => ({ service: l.serviceName, item: l.itemName, qty: l.qty, amount: l.amt })),
  });
  for (const [m, amt] of parts) {
    Analytics.paymentReceived({ amount: amt, method: m, type: "delivery", customerId: o.custId, orderId });
  }
  const paidPart = parts.length === 0
    ? ""
    : " · " + parts.map(([m, amt]) => `${rupees(amt)} ${m === "UPI" ? "UPI" : "cash"}`).join(" + ");
  const balPart = alloc.newBalance > 0
    ? ` · ${rupees(alloc.newBalance)} baaki`
    : alloc.newBalance < 0
      ? ` · ${rupees(alloc.newBalance)} advance`
      : " · all clear";
  publish({ toast: `Delivered${paidPart}${balPart}`, undo: undoSnap, wa: statusWa(orderId) });
}

export function prepay(orderId: number, method: PayMethod) {
  const undoSnap = snapshot();
  const o = orderOf(orderId);
  // Only what's still unpaid, and only before delivery (a delivered order's
  // payment is taken by deliver / the khata) — never charge it twice.
  const due = amtOf(o) - o.pre;
  if (!isOpen(o) || due <= 0) return;
  insertLedger(mkEntry({ cust: o.custId, kind: "GOT", amt: due, method, tag: "PRE", cover: due, ref: o.id }));
  updateOrder(orderId, { pre: o.pre + due });
  Analytics.paymentReceived({ amount: due, method, type: "prepay", customerId: o.custId, orderId });
  publish({ toast: `Got ${rupees(due)} ${method === "UPI" ? "UPI" : "cash"} · order fully paid`, undo: undoSnap });
}

export function cancelOrder(orderId: number, reason: string) {
  const undoSnap = snapshot();
  const o = orderOf(orderId);
  const back = o.status === "DELIVERED" ? reverseDelivery(o) : {};
  updateOrder(orderId, { ...back, status: "CANCELLED", cancelReason: reason });
  Analytics.orderCancelled(orderId, reason);
  publish({ toast: `${o.status === "CREATED" ? "Pickup" : "Order"} cancelled · ${custName(o.custId)}`, undo: undoSnap, wa: statusWa(orderId) });
}

const STATUS_WORD: Record<OrderStatus, string> = {
  CREATED: "To pick up", RECEIVED: "Received", READY: "Ready", DELIVERED: "Delivered", CANCELLED: "Cancelled",
};

/**
 * Change status: move an order to any state. Steps that need input (count
 * clothes, delivery date, payment, cancel reason) go through their own sheets
 * and commands; this does the direct writes, backward moves included.
 * Leaving DELIVERED first takes the bill off the khata. Port of setStatus.
 */
export function setStatus(orderId: number, target: OrderStatus) {
  const o = orderOf(orderId);
  if (o.status === target || target === "DELIVERED") return; // delivering = deliver()
  const undoSnap = snapshot();
  const unDelivered = o.status === "DELIVERED";
  const back = unDelivered ? reverseDelivery(o) : {};
  updateOrder(orderId, { ...back, status: target, cancelReason: "" });
  Analytics.orderStatusChanged(orderId, o.status, target);
  publish({
    toast: `Moved to ${STATUS_WORD[target]} · ${custName(o.custId)}` + (unDelivered ? " · bill taken off the khata" : ""),
    undo: undoSnap,
    wa: statusWa(orderId),
  });
}

/**
 * Un-deliver: the BILL (and any ADJ) khata entries become tombstones, so the
 * bill is gone; payments stay in the khata and count on the order as
 * paid-in-advance, so delivering again never bills twice. Returns the order
 * fields to write.
 */
function reverseDelivery(o: Order): Partial<Order> {
  const mine = rows().ledger.filter((e) => e.ref === o.id && !e.deleted);
  setRows((r) => ({
    ...r,
    ledger: r.ledger.map((e) =>
      e.ref === o.id && !e.deleted && (e.kind === "BILL" || e.kind === "ADJ") ? { ...e, deleted: true, ...stamp() } : e,
    ),
  }));
  const pre = mine.filter((e) => e.kind === "GOT").reduce((s, e) => s + e.cover, 0);
  return { pre, paid: 0, doneAt: "", doneDate: "" };
}

/**
 * Deletes an order and its bill for good: the order and every khata entry
 * made for it (bill, payments, edits) become tombstones, so the customer's
 * balance is as if it never existed. Undo restores it all.
 */
export function deleteOrder(orderId: number) {
  const undoSnap = snapshot();
  const o = orderOf(orderId);
  setRows((r) => ({
    ...r,
    orders: r.orders.map((x) => (x.id === orderId ? { ...x, deleted: true, ...stamp() } : x)),
    ledger: r.ledger.map((e) => (e.ref === orderId && !e.deleted ? { ...e, deleted: true, ...stamp() } : e)),
    // "Last order …" follows the newest order still there (or none).
    customers: r.customers.map((c) => {
      if (c.id !== o.custId) return c;
      const left = r.orders.filter((x) => !x.deleted && x.custId === c.id && x.id !== orderId);
      const latest = left.reduce<Order | null>((a, b) => (a == null || b.createdOn > a.createdOn ? b : a), null);
      const today = AppDate.today();
      const [lastLabel, agoRank] = latest == null
        ? ["—", 9]
        : latest.createdOn === today
          ? ["Today", 0]
          : [AppDate.plain(latest.createdOn), Math.min(8, Math.max(1, AppDate.daysBetween(latest.createdOn, today)))];
      return c.lastLabel === lastLabel && c.agoRank === agoRank ? c : { ...c, lastLabel, agoRank, ...stamp() };
    }),
  }));
  publish({ toast: `Bill #${orderNo(o)} deleted · ${custName(o.custId)}`, undo: undoSnap });
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
  publish({ toast: msg, undo: undoSnap });
}

export function sendBill(orderId: number) {
  const o = orderOf(orderId);
  updateOrder(orderId, { billSent: true });
  Analytics.billSent(orderId);
  publish({ toast: `Opening WhatsApp · bill #${orderNo(orderOf(orderId))} to ${custName(o.custId)}` });
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

/** Saves bill details and/or the shop name (Settings / onboarding "Your bill"). */
export function updateShopDetails(patch: Partial<BillDetails> & { name?: string }) {
  setRows((r) => ({
    ...r,
    shop: r.shop ? { ...r.shop, ...patch, ...stamp() } : r.shop,
  }));
  if (patch.name && patch.name.trim() !== "") Analytics.updateProfile({ Name: patch.name.trim() });
  requestSync();
}

/**
 * The last-used GST setting. Every change on a NEW order saves it straight
 * away, so the next order opens the same way (edits of an old order don't).
 */
export function setGstDefaults(g: GstFields) {
  const cur = rows().shop;
  if (!cur || (cur.gstOn === g.gstOn && cur.gstPct === g.gstPct && cur.gstMode === g.gstMode)) return;
  setRows((r) => ({
    ...r,
    shop: r.shop ? { ...r.shop, gstOn: g.gstOn, gstPct: g.gstPct, gstMode: g.gstMode, ...stamp() } : r.shop,
  }));
  requestSync();
}

/** Records onboarding progress so a reload or another device resumes there. */
export function setOnboardingStep(step: OnboardingStep) {
  const cur = rows().shop;
  if (!cur || cur.onboardingStep === step) return;
  setRows((r) => ({ ...r, shop: r.shop ? { ...r.shop, onboardingStep: step, ...stamp() } : r.shop }));
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
  exPct: number; // 0 = exAmt is a fixed ₹ amount
  discount: number;
  discPct: number; // 0 = discount is a fixed ₹ amount
  gstOn: boolean;
  gstPct: number;
  gstMode: GstFields["gstMode"];
  lines: OrderLine[];
  quickAmount: number;
  quickPieces: number;
  serialNo: string; // "" = the order id
  note: string; // owner's note ("" = none)
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
    exPct: a.express ? a.exPct : 0, discount: a.discount, discPct: a.discPct,
    gstOn: a.gstOn && a.gstPct > 0, gstPct: a.gstPct, gstMode: a.gstMode,
    lines: a.lines, serialNo: a.serialNo.trim(), note: a.note.trim().slice(0, 500),
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
      exPct: fields.exPct, discount: fields.discount, discPct: fields.discPct,
      gstOn: fields.gstOn, gstPct: fields.gstPct, gstMode: fields.gstMode,
      lines: newLines, serialNo: fields.serialNo, note: fields.note,
    };
    updated = withPctExtras(updated, st.shop.expressPct);
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
    publish({ toast: `Order #${orderNo(updated)} updated${totalPart}${khataPart}`, undo: undoSnap });
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
    express: fields.express, exAmt: fields.exAmt, exPct: fields.exPct,
    discount: fields.discount, discPct: fields.discPct,
    gstOn: fields.gstOn, gstPct: fields.gstPct, gstMode: fields.gstMode,
    pre: 0, paid: 0, doneAt: "", doneDate: "", createdOn: AppDate.today(),
    billSent: false, pieces: 0, lines: fields.lines, serialNo: fields.serialNo, note: fields.note,
  };
  setRows((r) => ({
    ...r,
    orders: [...r.orders.filter((x) => x.id !== id), { ...order, ...stamp(), deleted: false }],
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
      : `Order #${orderNo(order)} saved for ${nm} · add clothes when ready`;
    publish({ toast, undo: undoSnap });
    return { orderId: id, goToBill: false, edited: false };
  }
  requestSync();
  return { orderId: id, goToBill: true, edited: false };
}
