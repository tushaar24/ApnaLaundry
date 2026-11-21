import * as AppDate from "@/core/appdate";
import { rupees, rupeesPaise } from "@/core/money";
import { clothesOf, gstOf } from "@/domain/laundryMath";
import { gstBillNote, gstDefaults, halfRate, roundOffValue } from "@/domain/gst";
import type { BillDetails, BillTemplate, LaundryState, Order, OrderLine, Service, Shop } from "@/domain/models";
import {
  billDetailsFrom, fmtBillPhone, isEmailValid, isGstinValid, isUpiValid, SAMPLE_CUSTOMER_ADDRESS, sampleOrder, termLines, upiPayload,
} from "@/domain/billDetails";
import * as Sel from "@/domain/selectors";

/**
 * Everything printed on a customer's bill, already formatted — one model
 * drives every design (Classic / Bold / Receipt / Invoice), the PDF, the shared image,
 * the "View bill" sheet, the public /b/ page and the onboarding preview, so
 * they can never drift apart. Port twin: domain/BillReceipt.kt.
 */

export interface ReceiptLine {
  item: string; // "Shirt × 3" / "Wash & Fold · 4 kg"
  sub: string; // service name under the item ("" = none)
  amount: string;
  rate?: string; // price per item: "₹20 each" / "₹60/kg" ("" / absent = none)
  // The Invoice design's columns: "Shirt", "3", "₹80.00", "₹240.00" ("" where a lump bill has none).
  name?: string;
  qty?: string;
  unit?: string;
  totalExact?: string;
}

export interface ReceiptRow {
  label: string;
  value: string;
  discount?: boolean; // printed green
  exact?: string; // the value with paise (Invoice design)
}

export interface ReceiptUpi {
  id: string;
  payload: string; // upi://pay?… the QR encodes
  amount: string;
}

/**
 * Under a real bill, when the customer owes from earlier (old baaki, other
 * orders) or has advance with the shop (owner, 21 Nov 2026: "old baaki &
 * advance payment is not added in the bill"): Total − Paid ± previous =
 * Total to pay. `previous` is the khata without this bill — everything the
 * customer owes now minus what's still due on this bill — so a payment made
 * at this delivery that cleared old baaki is already in it. Port twin: BillReceipt.Khata.
 */
export interface ReceiptKhata {
  rows: ReceiptRow[]; // "Paid" "− ₹100" (when something is paid on this bill), then "Previous due (old baaki)" "+ ₹200" / "Advance with shop" "− ₹500"
  toPay: ReceiptRow; // "Total to pay" "₹400" — never below ₹0
  advanceLeft: string; // "Advance left ₹200" when the advance is more than everything due; ""
  previous: number; // + old baaki / − advance
  toPayAmount: number;
}

export interface BillReceipt {
  template: BillTemplate;
  shop: {
    name: string;
    phone: string; // "+91 98765 43210" or ""
    address: string;
    gstin: string; // only when valid
    email: string; // only when valid
    logoId: string;
  };
  taxInvoice: boolean; // "TAX INVOICE" over the bill number: GST on the order + a valid GSTIN
  billNo: string; // "Bill #1001"
  date: string; // "Fri, 9 Oct"
  customer: { name: string; phone: string; address?: string }; // address only when the shop prints it and the customer has one
  readyBy: string; // "Ready by Sat, 10 Oct · 6 PM" / "Delivered Sat, 10 Oct" / ""
  lines: ReceiptLine[];
  subtotal: string;
  extras: ReceiptRow[]; // express, pickup/delivery, discount, then exclusive GST (taxable, CGST, SGST, round off)
  total: string;
  gstNote: string; // inclusive GST: "Price includes GST 18%: …" under the total ("" = none)
  payStatus: string; // real bills: "To pay ₹X" / "Paid ₹Y · to pay ₹Z" / "Paid in full"; "" on the preview. Paid = toward this bill (before delivery: paid in advance)
  upi: ReceiptUpi | null; // what's left to pay (with old baaki / advance: khata.toPayAmount); null when nothing is (or no valid UPI id)
  terms: string[];
  // The Invoice design: the order's details as label → value, money with paise.
  orderNo?: string; // "1005" / the owner's serial
  customerMobile?: string; // "9455838720" (no +91)
  createdAt?: string; // "03/10/2026"
  dateLabel?: string; // "Delivered Date" / "Delivery Date" / ""
  dateValue?: string;
  subtotalExact?: string; // "₹840.00"
  totalExact?: string;
  // In-app bills of a customer with old baaki or advance in the khata (else absent): drawn instead of payStatus.
  // Not on the public /b/ page — its server copy of the order has no khata.
  khata?: ReceiptKhata | null;
}

/** The order fields a bill shows — also what the public bill endpoint returns. */
export type BillOrder = Pick<
  Order,
  "id" | "status" | "createdOn" | "deliveryDate" | "doneDate" | "express" | "exAmt" | "fee" | "discount" | "lines"
> & Partial<Pick<Order, "paid" | "pre" | "deliveryTime" | "serialNo" | "gstOn" | "gstPct" | "gstMode">>;

/** The shop fields a bill shows (older servers send only name + phone). */
export type BillShop = { name: string; phone: string } & Partial<BillDetails>;

/** Everything needed to print one bill (the public /b/<token> page's payload). */
export interface BillData {
  shop: BillShop;
  customer: { name: string; phone: string; address?: string };
  order: BillOrder;
}

/** The public page's payload for a shop's sample bill (no saved order). */
export interface SampleBillData {
  sample: true;
  shop: BillShop & { expressPct: number };
  services: { id: string; name: string; mode: string; ratePerKg: number | null; minKg: number | null; sortOrder: number; items: { name: string; price: number | null }[] }[];
}

/** The sample bill exactly as the onboarding preview drew it. */
export function receiptFromSample(d: SampleBillData): BillReceipt {
  const services: Service[] = d.services.map((s) => ({
    id: s.id, name: s.name, mode: s.mode === "WEIGHT" ? "WEIGHT" : "PIECE", ratePerKg: s.ratePerKg, minKg: s.minKg,
    readyInDays: null, lockedToPiece: false, sortOrder: s.sortOrder, items: s.items,
  }));
  const shop: Shop = {
    name: d.shop.name, phone: d.shop.phone, expressPct: d.shop.expressPct, onboardingStep: "",
    ...billDetailsFrom(d.shop, d.shop.phone), ...gstDefaults(),
  };
  return sampleReceipt(shop, sampleOrder(services, shop.expressPct, 1001), shop.billTemplate);
}

export interface ReceiptOptions {
  /** Preview only: show "Express (urgent, +N%)". */
  expressPct?: number;
  /** Preview: no payment line. */
  sample?: boolean;
  /** Override the shop's saved design (the design switcher). */
  template?: BillTemplate;
  /** Real in-app bills: the customer's whole khata now (+ owes / − advance), for the old baaki / advance lines. */
  balance?: number;
}

/** The price per item printed under it: "₹20 each", "₹60/kg"; "" for a lump (not itemised) bill. */
function rateOf(l: OrderLine): string {
  if (l.isQuick || l.price <= 0) return "";
  return l.kg > 0 ? `${rupees(l.price)}/kg` : `${rupees(l.price)} each`;
}

/** ₹ with paise: "₹840.00". */
const exact = (rupeesAmt: number) => rupeesPaise(rupeesAmt * 100);

function line(l: OrderLine): ReceiptLine {
  if (l.kg > 0) {
    return {
      item: Sel.weightLabel(l), sub: "", amount: rupees(l.amt), rate: rateOf(l),
      name: l.serviceName, qty: `${Sel.trimKg(l.kg)} kg`, unit: l.price > 0 ? `${exact(l.price)}/kg` : "", totalExact: exact(l.amt),
    };
  }
  if (l.isQuick) {
    return {
      item: l.qty > 0 ? `${l.itemName} × ${l.qty}` : l.itemName, sub: "", amount: rupees(l.amt),
      name: l.itemName, qty: l.qty > 0 ? String(l.qty) : "", unit: "", totalExact: exact(l.amt),
    };
  }
  return {
    item: `${l.itemName} × ${l.qty}`, sub: l.serviceName, amount: rupees(l.amt), rate: rateOf(l),
    name: l.itemName, qty: String(l.qty), unit: exact(l.price), totalExact: exact(l.amt),
  };
}

function readyByOf(o: BillOrder): string {
  if (o.status === "DELIVERED" && o.doneDate) return `Delivered ${AppDate.plain(o.doneDate)}`;
  if (!o.deliveryDate) return "";
  const time = o.deliveryTime ? ` · ${o.deliveryTime}` : "";
  return `Ready by ${AppDate.plain(o.deliveryDate)}${time}`;
}

/** Paid toward the bill: once delivered what was collected for it, before that what was paid in advance (the public page has no `pre`: its `paid`). */
export function paidOn(o: BillOrder, total: number): number {
  const p = o.status === "DELIVERED" ? (o.paid ?? 0) : (o.pre ?? o.paid ?? 0);
  return Math.min(Math.max(0, total), Math.max(0, p));
}

/** The old baaki / advance lines, for `previous` ≠ 0 (+ owes from before, − advance). */
function khataOf(total: number, paid: number, previous: number): ReceiptKhata {
  const sum = total - paid + previous;
  const toPay = Math.max(0, sum);
  const rows: ReceiptRow[] = [];
  if (paid > 0) rows.push({ label: "Paid", value: `− ${rupees(paid)}`, discount: true, exact: `− ${exact(paid)}` });
  if (previous > 0) rows.push({ label: "Previous due (old baaki)", value: `+ ${rupees(previous)}`, exact: `+ ${exact(previous)}` });
  else rows.push({ label: "Advance with shop", value: `− ${rupees(previous)}`, discount: true, exact: `− ${exact(-previous)}` });
  return {
    rows,
    toPay: { label: "Total to pay", value: rupees(toPay), exact: exact(toPay) },
    advanceLeft: sum < 0 ? `Advance left ${rupees(sum)}` : "",
    previous,
    toPayAmount: toPay,
  };
}

function payStatusOf(paid: number, total: number): string {
  if (paid <= 0) return `To pay ${rupees(total)}`;
  if (paid >= total) return "Paid in full";
  return `Paid ${rupees(paid)} · to pay ${rupees(total - paid)}`;
}

/** A real order's bill, with the customer's old baaki / advance from the khata (a cancelled order's bill: without). */
export function billReceipt(state: LaundryState, o: Order, opts: ReceiptOptions = {}): BillReceipt {
  const c = Sel.customer(state, o.custId);
  const balance = o.status === "CANCELLED" ? undefined : Sel.balance(state, o.custId);
  return receiptFrom({ shop: state.shop, customer: c, order: o }, { balance, ...opts });
}

/** The onboarding preview: a sample order built from the owner's rates. */
export function sampleReceipt(shop: Shop, order: Order, template: BillTemplate): BillReceipt {
  return receiptFrom(
    { shop, customer: { name: "Sample customer", phone: "", address: SAMPLE_CUSTOMER_ADDRESS }, order },
    { expressPct: shop.expressPct, sample: true, template },
  );
}

export function receiptFrom({ shop, customer: c, order: o }: BillData, opts: ReceiptOptions = {}): BillReceipt {
  const d = billDetailsFrom(shop, shop.phone);
  const gst = gstOf(o);
  const total = gst.total;
  const phone = (c.phone || "").replace(/\D/g, "").slice(-10);
  const paid = opts.sample ? 0 : paidOn(o, total);
  const previous = opts.balance != null ? opts.balance - (total - paid) : 0;
  const khata = !opts.sample && previous !== 0 ? khataOf(total, paid, previous) : null;
  // The pay QR asks for what's left: the whole total on the preview.
  const toPay = opts.sample ? total : khata ? khata.toPayAmount : total - paid;

  const extras: ReceiptRow[] = [];
  if (o.express && o.exAmt > 0) {
    const label = opts.expressPct != null ? `Express (urgent, +${opts.expressPct}%)` : "Express (urgent)";
    extras.push({ label, value: `+ ${rupees(o.exAmt)}`, exact: `+ ${exact(o.exAmt)}` });
  }
  if (o.fee > 0) extras.push({ label: "Pickup / delivery", value: `+ ${rupees(o.fee)}`, exact: `+ ${exact(o.fee)}` });
  if (o.discount > 0) extras.push({ label: "Discount", value: `− ${rupees(o.discount)}`, discount: true, exact: `− ${exact(o.discount)}` });
  if (gst.on && gst.mode === "excl") {
    extras.push({ label: "Taxable value", value: rupeesPaise(gst.taxablePaise) });
    extras.push({ label: `CGST @${halfRate(gst.pct)}%`, value: `+ ${rupeesPaise(gst.cgstPaise)}` });
    extras.push({ label: `SGST @${halfRate(gst.pct)}%`, value: `+ ${rupeesPaise(gst.sgstPaise)}` });
    if (gst.roundOffPaise !== 0) extras.push({ label: "Round off", value: roundOffValue(gst) });
  }
  const gstin = isGstinValid(d.gstin) ? d.gstin : "";

  return {
    template: opts.template ?? d.billTemplate,
    shop: {
      name: shop.name,
      phone: fmtBillPhone(d.billPhone),
      address: d.address,
      gstin,
      email: isEmailValid(d.email) ? d.email : "",
      logoId: d.logoId,
    },
    taxInvoice: gst.on && gstin !== "",
    billNo: `Bill #${Sel.orderNo(o)}`,
    date: AppDate.plain(o.createdOn),
    customer: {
      name: c.name,
      phone: phone ? `+91 ${Sel.fmtPhone(phone)}` : "",
      address: d.custAddressOnBill ? (c.address ?? "").replace(/\s+/g, " ").trim() : "",
    },
    readyBy: readyByOf(o),
    lines: o.lines.map(line),
    subtotal: rupees(clothesOf(o)),
    extras,
    total: rupees(total),
    gstNote: gst.on && gst.mode === "incl" ? gstBillNote(gst) : "",
    payStatus: opts.sample || o.paid == null ? "" : payStatusOf(paid, total),
    upi: toPay > 0 && isUpiValid(d.upiId)
      ? { id: d.upiId, payload: upiPayload(d.upiId, shop.name, toPay, o.id), amount: rupees(toPay) }
      : null,
    terms: termLines(d),
    orderNo: Sel.orderNo(o),
    customerMobile: phone,
    createdAt: AppDate.dmy(o.createdOn),
    dateLabel: o.status === "DELIVERED" && o.doneDate ? "Delivered Date" : o.deliveryDate ? "Delivery Date" : "",
    dateValue: o.status === "DELIVERED" && o.doneDate ? AppDate.dmy(o.doneDate) : AppDate.dmy(o.deliveryDate),
    subtotalExact: exact(clothesOf(o)),
    totalExact: exact(total),
    khata,
  };
}
