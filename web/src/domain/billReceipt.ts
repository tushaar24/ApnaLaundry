import * as AppDate from "@/core/appdate";
import { rupees } from "@/core/money";
import { amtOf, clothesOf } from "@/domain/laundryMath";
import type { BillDetails, BillTemplate, LaundryState, Order, OrderLine, Shop } from "@/domain/models";
import {
  billDetailsFrom, fmtBillPhone, isGstinValid, isUpiValid, termLines, upiPayload,
} from "@/domain/billDetails";
import * as Sel from "@/domain/selectors";

/**
 * Everything printed on a customer's bill, already formatted — one model
 * drives every design (Classic / Bold / Receipt), the PDF, the shared image,
 * the "View bill" sheet, the public /b/ page and the onboarding preview, so
 * they can never drift apart. Port twin: domain/BillReceipt.kt.
 */

export interface ReceiptLine {
  item: string; // "Shirt × 3" / "Wash & Fold · 4 kg"
  sub: string; // service name under the item ("" = none)
  amount: string;
}

export interface ReceiptRow {
  label: string;
  value: string;
  discount?: boolean; // printed green
}

export interface ReceiptUpi {
  id: string;
  payload: string; // upi://pay?… the QR encodes
  amount: string;
}

export interface BillReceipt {
  template: BillTemplate;
  shop: {
    name: string;
    phone: string; // "+91 98765 43210" or ""
    address: string;
    gstin: string; // only when valid
    logoId: string;
  };
  billNo: string; // "Bill #1001"
  date: string; // "Fri, 9 Oct"
  customer: { name: string; phone: string };
  readyBy: string; // "Ready by Sat, 10 Oct · 6 PM" / "Delivered Sat, 10 Oct" / ""
  lines: ReceiptLine[];
  subtotal: string;
  extras: ReceiptRow[]; // express, pickup/delivery, discount
  total: string;
  payStatus: string; // real bills: "To pay ₹X" / "Paid ₹Y · to pay ₹Z" / "Paid in full"; "" on the preview
  upi: ReceiptUpi | null;
  terms: string[];
}

/** The order fields a bill shows — also what the public bill endpoint returns. */
export type BillOrder = Pick<
  Order,
  "id" | "status" | "createdOn" | "deliveryDate" | "doneDate" | "express" | "exAmt" | "fee" | "discount" | "lines"
> & Partial<Pick<Order, "paid" | "deliveryTime">>;

/** The shop fields a bill shows (older servers send only name + phone). */
export type BillShop = { name: string; phone: string } & Partial<BillDetails>;

/** Everything needed to print one bill (the public /b/<token> page's payload). */
export interface BillData {
  shop: BillShop;
  customer: { name: string; phone: string };
  order: BillOrder;
}

export interface ReceiptOptions {
  /** Preview only: show "Express (urgent, +N%)". */
  expressPct?: number;
  /** Preview: no payment line. */
  sample?: boolean;
  /** Override the shop's saved design (the design switcher). */
  template?: BillTemplate;
}

function line(l: OrderLine): ReceiptLine {
  if (l.kg > 0) return { item: `${l.serviceName} · ${Sel.trimKg(l.kg)} kg`, sub: "", amount: rupees(l.amt) };
  if (l.isQuick) return { item: l.qty > 0 ? `${l.itemName} × ${l.qty}` : l.itemName, sub: "", amount: rupees(l.amt) };
  return { item: `${l.itemName} × ${l.qty}`, sub: l.serviceName, amount: rupees(l.amt) };
}

function readyByOf(o: BillOrder): string {
  if (o.status === "DELIVERED" && o.doneDate) return `Delivered ${AppDate.plain(o.doneDate)}`;
  if (!o.deliveryDate) return "";
  const time = o.deliveryTime ? ` · ${o.deliveryTime}` : "";
  return `Ready by ${AppDate.plain(o.deliveryDate)}${time}`;
}

function payStatusOf(paid: number, total: number): string {
  if (paid <= 0) return `To pay ${rupees(total)}`;
  if (paid >= total) return "Paid in full";
  return `Paid ${rupees(paid)} · to pay ${rupees(total - paid)}`;
}

export function billReceipt(state: LaundryState, o: Order, opts: ReceiptOptions = {}): BillReceipt {
  const c = Sel.customer(state, o.custId);
  return receiptFrom({ shop: state.shop, customer: c, order: o }, opts);
}

/** The onboarding preview: a sample order built from the owner's rates. */
export function sampleReceipt(shop: Shop, order: Order, template: BillTemplate): BillReceipt {
  return receiptFrom(
    { shop, customer: { name: "Sample customer", phone: "" }, order },
    { expressPct: shop.expressPct, sample: true, template },
  );
}

export function receiptFrom({ shop, customer: c, order: o }: BillData, opts: ReceiptOptions = {}): BillReceipt {
  const d = billDetailsFrom(shop, shop.phone);
  const total = amtOf(o);
  const phone = (c.phone || "").replace(/\D/g, "").slice(-10);

  const extras: ReceiptRow[] = [];
  if (o.express && o.exAmt > 0) {
    const label = opts.expressPct != null ? `Express (urgent, +${opts.expressPct}%)` : "Express (urgent)";
    extras.push({ label, value: `+ ${rupees(o.exAmt)}` });
  }
  if (o.fee > 0) extras.push({ label: "Pickup / delivery", value: `+ ${rupees(o.fee)}` });
  if (o.discount > 0) extras.push({ label: "Discount", value: `− ${rupees(o.discount)}`, discount: true });

  return {
    template: opts.template ?? d.billTemplate,
    shop: {
      name: shop.name,
      phone: fmtBillPhone(d.billPhone),
      address: d.address,
      gstin: isGstinValid(d.gstin) ? d.gstin : "",
      logoId: d.logoId,
    },
    billNo: `Bill #${o.id}`,
    date: AppDate.plain(o.createdOn),
    customer: { name: c.name, phone: phone ? `+91 ${Sel.fmtPhone(phone)}` : "" },
    readyBy: readyByOf(o),
    lines: o.lines.map(line),
    subtotal: rupees(clothesOf(o)),
    extras,
    total: rupees(total),
    payStatus: opts.sample || o.paid == null ? "" : payStatusOf(o.paid, total),
    upi: isUpiValid(d.upiId)
      ? { id: d.upiId, payload: upiPayload(d.upiId, shop.name, total, o.id), amount: rupees(total) }
      : null,
    terms: termLines(d),
  };
}
