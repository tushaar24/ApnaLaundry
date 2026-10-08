import { rupees } from "@/core/money";
import { amtOf, clothesOf } from "@/domain/laundryMath";
import type { LaundryState, Order, OrderLine } from "@/domain/models";
import * as Sel from "@/domain/selectors";

/**
 * Everything printed on the customer's bill receipt, already formatted —
 * one model drives the PDF, the shared image and the "View bill" sheet so
 * they can never drift apart. Port twin: ui/screens/bill/BillReceipt.kt.
 */

export interface ReceiptLine {
  item: string;
  sub: string; // service name under the item ("" = none)
  qty: string;
  rate: string;
  total: string;
}

export interface ReceiptRow {
  label: string;
  value: string;
}

export interface BillReceipt {
  shopName: string;
  shopPhone: string; // "+91 98765 43210", or "" if the shop has no phone
  info: ReceiptRow[]; // Order / Customer / Mobile / Created At / Delivery Date
  lines: ReceiptLine[];
  subtotal: string;
  extras: ReceiptRow[]; // express, pickup/delivery, discount
  total: string;
}

/** ₹1,250.00 — receipts show paise like a printed bill. */
export function receiptMoney(v: number): string {
  return `${rupees(v)}.00`;
}

/** "2026-10-04" → "04/10/2026". */
export function receiptDate(iso: string): string {
  const [y, m, d] = iso.split("-");
  return d && m && y ? `${d}/${m}/${y}` : iso;
}

function line(l: OrderLine): ReceiptLine {
  if (l.kg > 0) {
    return {
      item: l.serviceName,
      sub: "By weight",
      qty: `${Sel.trimKg(l.kg)} kg`,
      rate: l.price > 0 ? `${receiptMoney(l.price)}/kg` : "—",
      total: receiptMoney(l.amt),
    };
  }
  if (l.isQuick) {
    return { item: l.itemName, sub: "", qty: l.qty > 0 ? String(l.qty) : "—", rate: "—", total: receiptMoney(l.amt) };
  }
  return { item: l.itemName, sub: l.serviceName, qty: String(l.qty), rate: receiptMoney(l.price), total: receiptMoney(l.amt) };
}

/** The order fields a bill shows — also what the public bill endpoint returns. */
export type BillOrder = Pick<
  Order,
  "id" | "status" | "createdOn" | "deliveryDate" | "doneDate" | "express" | "exAmt" | "fee" | "discount" | "lines"
>;

/** Everything needed to print one bill (the public /b/<token> page's payload). */
export interface BillData {
  shop: { name: string; phone: string };
  customer: { name: string; phone: string };
  order: BillOrder;
}

export function billReceipt(state: LaundryState, o: Order): BillReceipt {
  const c = Sel.customer(state, o.custId);
  return receiptFrom({ shop: state.shop, customer: c, order: o });
}

export function receiptFrom({ shop, customer: c, order: o }: BillData): BillReceipt {
  const phone = (c.phone || "").replace(/\D/g, "").slice(-10);

  const info: ReceiptRow[] = [
    { label: "Order", value: String(o.id).padStart(6, "0") },
    { label: "Customer", value: c.name.toUpperCase() },
  ];
  if (phone) info.push({ label: "Mobile", value: phone });
  info.push({ label: "Created At", value: receiptDate(o.createdOn) });
  if (o.status === "DELIVERED" && o.doneDate) info.push({ label: "Delivered Date", value: receiptDate(o.doneDate) });
  else if (o.deliveryDate) info.push({ label: "Delivery Date", value: receiptDate(o.deliveryDate) });

  const extras: ReceiptRow[] = [];
  if (o.express && o.exAmt > 0) extras.push({ label: "Express", value: `+ ${receiptMoney(o.exAmt)}` });
  if (o.fee > 0) extras.push({ label: "Pickup / delivery", value: `+ ${receiptMoney(o.fee)}` });
  if (o.discount > 0) extras.push({ label: "Discount", value: `− ${receiptMoney(o.discount)}` });

  return {
    shopName: shop.name,
    shopPhone: shop.phone ? `+91 ${Sel.fmtPhone(shop.phone)}` : "",
    info,
    lines: o.lines.map(line),
    subtotal: receiptMoney(clothesOf(o)),
    extras,
    total: receiptMoney(amtOf(o)),
  };
}
