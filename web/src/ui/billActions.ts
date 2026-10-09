"use client";

import { useEffect } from "react";

import { rupees } from "@/core/money";
import { amtOf } from "@/domain/laundryMath";
import { billReceipt, type BillReceipt } from "@/domain/billReceipt";
import type { LaundryState, Order } from "@/domain/models";
import * as Sel from "@/domain/selectors";
import * as Repo from "@/data/repository";
import { billPdfFile, shareOrSaveFile } from "@/ui/billPdf";
import { cachedBillLink, cachedSampleLink, prefetchBillLink, prefetchSampleLink } from "@/ui/billLinks";

/**
 * "Send on WhatsApp" and "Download" for a bill.
 *  - WhatsApp: a browser can't hand a file to a specific WhatsApp chat (wa.me
 *    carries text only; the share sheet can't preselect a contact). So the tap
 *    opens the customer's chat with the message + a link to their bill page
 *    (/b/<token>, PDF download there), and the owner just hits send.
 *  - Download: the PDF receipt (ui/billPdf.ts) via the native share sheet on
 *    phones, a file download on desktop. Built synchronously so the share
 *    stays inside the tap's user gesture (iOS Safari refuses one after await).
 */

function customerDigits(state: LaundryState, custId: string): string {
  return (Sel.customer(state, custId).phone || "").replace(/\D/g, "").slice(-10);
}

function waUrl(digits: string, text: string): string {
  const t = encodeURIComponent(text);
  // With a number → chat opens straight to the customer; without → WhatsApp
  // asks the owner to pick a contact.
  return digits.length === 10 ? `https://wa.me/91${digits}?text=${t}` : `https://wa.me/?text=${t}`;
}

/** The WhatsApp message (asterisks render bold). Without a link it still carries the total. */
function billMessage(state: LaundryState, o: Order, link: string | null): string {
  const c = Sel.customer(state, o.custId);
  return [
    `Hi ${Sel.firstName(c.name)}, here is your bill for order #${o.id} from *${state.shop.name}* — Total *${rupees(amtOf(o))}*.`,
    link ? `View / download your bill:\n${link}` : "",
    "Thank you!",
  ].filter(Boolean).join("\n\n");
}

function makePdf(state: LaundryState, o: Order): File | null {
  try {
    return billPdfFile(billReceipt(state, o), o.id);
  } catch {
    Repo.showInfo("Couldn't make the bill PDF — try again");
    return null;
  }
}


/** Screens with a send button call this so the tap opens WhatsApp instantly. */
export function usePrepareBillSend(orderId: number): void {
  useEffect(() => {
    if (orderId > 0) void prefetchBillLink(orderId);
  }, [orderId]);
}

/**
 * Opens the customer's WhatsApp chat with the bill message + link, ready for
 * the owner to hit send, and marks the bill sent.
 */
export function sendBillOnWhatsApp(state: LaundryState, o: Order): void {
  const digits = customerDigits(state, o.custId);
  const link = cachedBillLink(o.id);
  if (link) {
    // Must open synchronously in the click handler so it isn't popup-blocked.
    window.open(waUrl(digits, billMessage(state, o, link)), "_blank", "noopener,noreferrer");
    Repo.sendBill(o.id); // marks billSent + analytics + toast
    return;
  }

  // Link not fetched yet (slow network): open the tab now, inside the tap, and
  // point it at WhatsApp once the link arrives — without it if that fails.
  const tab = window.open("about:blank", "_blank");
  void prefetchBillLink(o.id).then((l) => {
    const url = waUrl(digits, billMessage(state, o, l));
    if (tab) {
      tab.opener = null;
      tab.location.href = url;
    } else {
      window.location.href = url;
    }
    Repo.sendBill(o.id);
    if (!l) Repo.showInfo("Couldn't create the bill link — sent the total only");
  });
}

/** Opens WhatsApp to the customer with a khata balance reminder. */
export function sendReminderOnWhatsApp(state: LaundryState, custId: string, bal: number): void {
  const c = Sel.customer(state, custId);
  const text = [
    `*${state.shop.name}*`,
    `+91 ${Sel.fmtPhone(state.shop.phone)}`,
    "",
    `${c.name}, a gentle reminder — your laundry balance is *${rupees(bal)}*.`,
    "Please clear it on your next visit. Thank you!",
  ].join("\n");
  // Must open synchronously in the click handler so it isn't popup-blocked.
  window.open(waUrl(customerDigits(state, custId), text), "_blank", "noopener,noreferrer");
  Repo.showInfo(`Opening WhatsApp · reminder to ${Sel.firstName(c.name)} for ${rupees(bal)}`);
}

/** Share the bill PDF via the native sheet (mobile) or download it (desktop). */
export function downloadBill(state: LaundryState, o: Order): void {
  const file = makePdf(state, o);
  if (!file) return;
  void shareOrSaveFile(file, `Bill #${o.id}`).then((r) => {
    if (r === "saved") Repo.showInfo(`${file.name} downloaded`);
  });
}

/** The test bill message: like a real bill's, with the shop's sample-bill link. */
function testMessage(r: BillReceipt, link: string | null): string {
  return [
    `Here is a sample bill from *${r.shop.name}* — Total *${r.total}*.`,
    link ? `View / download the bill:\n${link}` : "",
    "Thank you!",
  ].filter(Boolean).join("\n\n");
}

/**
 * Onboarding "Test on WhatsApp": the same message + bill link real bills
 * send (the link opens the shop's sample bill), but with no number, so
 * WhatsApp lets the owner pick who gets it. Real bills open the customer's
 * chat directly.
 */
export function sendTestBill(r: BillReceipt): void {
  const link = cachedSampleLink();
  if (link) {
    window.open(waUrl("", testMessage(r, link)), "_blank", "noopener,noreferrer");
    return;
  }
  // Link not fetched yet: open the tab inside the tap, point it at WhatsApp
  // once the link arrives (without it if that fails).
  const tab = window.open("about:blank", "_blank");
  void prefetchSampleLink().then((l) => {
    const url = waUrl("", testMessage(r, l));
    if (tab) {
      tab.opener = null;
      tab.location.href = url;
    } else {
      window.location.href = url;
    }
  });
}
