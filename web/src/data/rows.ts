import type {
  Customer, DayClose, LedgerEntry, Order, OrderLine, Service, ServiceItem, Shop,
} from "@/domain/models";
import { syncNow } from "@/core/syncclock";
import { billDetailsFrom, onboardingStepFrom } from "@/domain/billDetails";
import { DEFAULT_GST_PCT, gstModeFrom, gstPctFrom } from "@/domain/gst";

/**
 * In-memory rows = domain shape + sync metadata, mirroring the app's Room
 * entities. `updatedAt` is the last-write-wins key, `deleted` a tombstone,
 * `dirty` marks rows awaiting push. JSON columns (items/lines) stay parsed
 * here and are (de)serialized at the wire boundary in syncApi.
 */

export interface SyncMeta {
  updatedAt: number;
  deleted: boolean;
  dirty: boolean;
}

export type ServiceRow = Service & SyncMeta;
export type CustomerRow = Customer & SyncMeta;
export type OrderRow = Order & SyncMeta;
export type LedgerRow = LedgerEntry & SyncMeta;
export type DayCloseRow = DayClose & SyncMeta;
export type ShopRow = Shop & { nextOrder: number; nextCust: number } & Omit<SyncMeta, "deleted">;

export interface Rows {
  shop: ShopRow | null;
  services: ServiceRow[];
  customers: CustomerRow[];
  orders: OrderRow[];
  ledger: LedgerRow[];
  dayCloses: DayCloseRow[];
}

export const emptyRows: Rows = {
  shop: null,
  services: [],
  customers: [],
  orders: [],
  ledger: [],
  dayCloses: [],
};

/** Fresh dirty stamp for a local write. */
export function stamp(): Pick<SyncMeta, "updatedAt" | "dirty"> {
  return { updatedAt: syncNow(), dirty: true };
}

// ---- wire DTOs (field-for-field what routes/laundry.js accepts/returns) ----

export interface ShopDto {
  name: string; phone: string; expressPct: number;
  nextOrder: number; nextCust: number; updatedAt: number;
  // Bill details + onboarding — absent from servers before 2026-10-09.
  billPhone?: string; address?: string; gstin?: string; upiId?: string; logoId?: string;
  terms?: string[]; termsCustom?: string; billTemplate?: string; onboardingStep?: string;
  // Last-used GST setting — absent from servers before the GST release.
  gstOn?: boolean; gstPct?: number; gstMode?: string;
}
export interface ServiceDto {
  id: string; name: string; mode: string; ratePerKg: number | null;
  minKg: number | null; readyInDays: number | null; lockedToPiece: boolean;
  sortOrder: number; deleted: boolean; itemsJson: string; updatedAt: number;
}
export interface CustomerDto {
  id: string; name: string; phone: string; address: string;
  pastOrders: number; lastLabel: string; agoRank: number;
  deleted: boolean; updatedAt: number;
}
export interface OrderDto {
  id: number; custId: string; pickup: string; delivery: string;
  pickupDate: string; pickupTime: string; deliveryDate: string; deliveryTime: string;
  ddAuto: boolean; status: string; cancelReason: string; fee: number;
  express: boolean; exAmt: number; discount: number; pre: number; paid: number;
  doneAt: string; doneDate: string; createdOn: string; billSent: boolean;
  pieces: number; linesJson: string; serialNo?: string; exPct?: number; discPct?: number;
  // GST on the order — absent from servers / clients before the GST release.
  gstOn?: boolean; gstPct?: number; gstMode?: string;
  deleted: boolean; updatedAt: number;
}
export interface LedgerDto {
  id: string; custId: string; date: string; time: string; ts: number;
  kind: string; amt: number; method: string; tag: string;
  cover: number; toOld: number; toAdv: number; ref: number | null;
  note: string; deleted: boolean; updatedAt: number;
}
export interface DayCloseDto {
  date: string; closedAt: number; cashCounted: number | null;
  deleted: boolean; updatedAt: number;
}

export interface SyncChanges {
  shop?: ShopDto | null;
  services?: ServiceDto[];
  customers?: CustomerDto[];
  orders?: OrderDto[];
  ledger?: LedgerDto[];
  dayCloses?: DayCloseDto[];
}

// ---- row <-> DTO ----

export function shopToDto(r: ShopRow): ShopDto {
  const {
    name, phone, expressPct, nextOrder, nextCust, updatedAt,
    billPhone, address, gstin, upiId, logoId, terms, termsCustom, billTemplate, onboardingStep,
    gstOn, gstPct, gstMode,
  } = r;
  return {
    name, phone, expressPct, nextOrder, nextCust, updatedAt,
    billPhone, address, gstin, upiId, logoId, terms, termsCustom, billTemplate, onboardingStep,
    gstOn, gstPct, gstMode,
  };
}
export function shopFromDto(d: ShopDto): ShopRow {
  return {
    name: d.name, phone: d.phone, expressPct: d.expressPct,
    ...billDetailsFrom(d, d.phone),
    onboardingStep: onboardingStepFrom(d.onboardingStep),
    gstOn: !!d.gstOn, gstPct: gstPctFrom(d.gstPct, DEFAULT_GST_PCT), gstMode: gstModeFrom(d.gstMode),
    nextOrder: d.nextOrder, nextCust: d.nextCust, updatedAt: d.updatedAt, dirty: false,
  };
}

export function serviceToDto(r: ServiceRow): ServiceDto {
  return {
    id: r.id, name: r.name, mode: r.mode, ratePerKg: r.ratePerKg, minKg: r.minKg,
    readyInDays: r.readyInDays, lockedToPiece: r.lockedToPiece, sortOrder: r.sortOrder,
    deleted: r.deleted, itemsJson: JSON.stringify(r.items), updatedAt: r.updatedAt,
  };
}
export function serviceFromDto(d: ServiceDto): ServiceRow {
  let items: ServiceItem[] = [];
  try { items = JSON.parse(d.itemsJson || "[]"); } catch { items = []; }
  return {
    id: d.id, name: d.name, mode: d.mode === "WEIGHT" ? "WEIGHT" : "PIECE",
    ratePerKg: d.ratePerKg ?? null, minKg: d.minKg ?? null, readyInDays: d.readyInDays ?? null,
    lockedToPiece: !!d.lockedToPiece, items, sortOrder: d.sortOrder ?? 0,
    updatedAt: d.updatedAt, deleted: !!d.deleted, dirty: false,
  };
}

export function customerToDto(r: CustomerRow): CustomerDto {
  return {
    id: r.id, name: r.name, phone: r.phone, address: r.address, pastOrders: r.pastOrders,
    lastLabel: r.lastLabel, agoRank: r.agoRank, deleted: r.deleted, updatedAt: r.updatedAt,
  };
}
export function customerFromDto(d: CustomerDto): CustomerRow {
  return {
    id: d.id, name: d.name, phone: d.phone, address: d.address ?? "",
    pastOrders: d.pastOrders ?? 0, lastLabel: d.lastLabel ?? "", agoRank: d.agoRank ?? 0,
    updatedAt: d.updatedAt, deleted: !!d.deleted, dirty: false,
  };
}

export function orderToDto(r: OrderRow): OrderDto {
  return {
    id: r.id, custId: r.custId, pickup: r.pickup, delivery: r.delivery,
    pickupDate: r.pickupDate, pickupTime: r.pickupTime, deliveryDate: r.deliveryDate,
    deliveryTime: r.deliveryTime, ddAuto: r.ddAuto, status: r.status,
    cancelReason: r.cancelReason, fee: r.fee, express: r.express, exAmt: r.exAmt,
    discount: r.discount, pre: r.pre, paid: r.paid, doneAt: r.doneAt, doneDate: r.doneDate,
    createdOn: r.createdOn, billSent: r.billSent, pieces: r.pieces,
    linesJson: JSON.stringify(r.lines), serialNo: r.serialNo, exPct: r.exPct, discPct: r.discPct,
    gstOn: r.gstOn, gstPct: r.gstPct, gstMode: r.gstMode,
    deleted: r.deleted, updatedAt: r.updatedAt,
  };
}
export function orderFromDto(d: OrderDto): OrderRow {
  let lines: OrderLine[] = [];
  try { lines = JSON.parse(d.linesJson || "[]"); } catch { lines = []; }
  const status = ["CREATED", "RECEIVED", "READY", "DELIVERED", "CANCELLED"].includes(d.status)
    ? (d.status as OrderRow["status"])
    : "CREATED";
  return {
    id: d.id, custId: d.custId, pickup: d.pickup === "HOME" ? "HOME" : "SHOP",
    delivery: d.delivery === "HOME" ? "HOME" : "SHOP",
    pickupDate: d.pickupDate ?? "", pickupTime: d.pickupTime ?? "",
    deliveryDate: d.deliveryDate ?? "", deliveryTime: d.deliveryTime ?? "",
    ddAuto: !!d.ddAuto, status, cancelReason: d.cancelReason ?? "", fee: d.fee ?? 0,
    express: !!d.express, exAmt: d.exAmt ?? 0, discount: d.discount ?? 0,
    pre: d.pre ?? 0, paid: d.paid ?? 0, doneAt: d.doneAt ?? "", doneDate: d.doneDate ?? "",
    createdOn: d.createdOn ?? "", billSent: !!d.billSent, pieces: d.pieces ?? 0, lines,
    serialNo: d.serialNo ?? "", exPct: d.exPct ?? 0, discPct: d.discPct ?? 0,
    gstOn: !!d.gstOn, gstPct: gstPctFrom(d.gstPct), gstMode: gstModeFrom(d.gstMode),
    updatedAt: d.updatedAt, deleted: !!d.deleted, dirty: false,
  };
}

export function ledgerToDto(r: LedgerRow): LedgerDto {
  return {
    id: r.id, custId: r.custId, date: r.date, time: r.time, ts: r.ts, kind: r.kind,
    amt: r.amt, method: r.method, tag: r.tag, cover: r.cover, toOld: r.toOld,
    toAdv: r.toAdv, ref: r.ref, note: r.note, deleted: r.deleted, updatedAt: r.updatedAt,
  };
}
export function ledgerFromDto(d: LedgerDto): LedgerRow {
  const kind = ["BILL", "OLD", "ADJ", "GOT"].includes(d.kind) ? (d.kind as LedgerRow["kind"]) : "BILL";
  const method = ["CASH", "UPI", "NONE"].includes(d.method) ? (d.method as LedgerRow["method"]) : "NONE";
  const tag = ["PRE", "DELIVER", "RECEIVE", "NONE"].includes(d.tag) ? (d.tag as LedgerRow["tag"]) : "NONE";
  return {
    id: d.id, custId: d.custId, date: d.date ?? "", time: d.time ?? "", ts: d.ts ?? 0,
    kind, amt: d.amt ?? 0, method, tag, cover: d.cover ?? 0, toOld: d.toOld ?? 0,
    toAdv: d.toAdv ?? 0, ref: d.ref ?? null, note: d.note ?? "",
    updatedAt: d.updatedAt, deleted: !!d.deleted, dirty: false,
  };
}

export function dayCloseToDto(r: DayCloseRow): DayCloseDto {
  return { date: r.date, closedAt: r.closedAt, cashCounted: r.cashCounted, deleted: r.deleted, updatedAt: r.updatedAt };
}
export function dayCloseFromDto(d: DayCloseDto): DayCloseRow {
  return {
    date: d.date, closedAt: d.closedAt ?? 0, cashCounted: d.cashCounted ?? null,
    updatedAt: d.updatedAt, deleted: !!d.deleted, dirty: false,
  };
}
