"use client";

import { rupees } from "@/core/money";
import { amtOf } from "@/domain/laundryMath";
import { billReceipt } from "@/domain/billReceipt";
import type { LaundryState, Order } from "@/domain/models";
import * as Sel from "@/domain/selectors";
import * as Repo from "@/data/repository";
import { billPdfFile } from "@/ui/billPdf";

/**
 * "Download" and "Send on WhatsApp" for a bill — both hand over the bill as a
 * PDF receipt (ui/billPdf.ts). A wa.me link can only carry text, so:
 *  - Phones (iOS Safari / Android Chrome): the native share sheet with the PDF
 *    attached → the owner taps WhatsApp and picks the customer.
 *  - Desktop: the PDF downloads and WhatsApp opens on the customer's chat with
 *    a short note, ready for the owner to attach the file.
 * The PDF is built synchronously so the share stays inside the tap's user
 * gesture (iOS Safari refuses a share after an await).
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

/** Short message that travels with the PDF. */
function billCaption(state: LaundryState, o: Order): string {
  const c = Sel.customer(state, o.custId);
  return `Hi ${Sel.firstName(c.name)}, here is your bill for order #${o.id} from ${state.shop.name} — Total ${rupees(amtOf(o))}. Thank you!`;
}

/** True on phones/tablets, where the native share sheet is the better "save". */
function isMobileDevice(): boolean {
  // iPadOS 13+ reports itself as "Macintosh" — the touch check catches it.
  return (
    /Android|iPhone|iPad|iPod/i.test(navigator.userAgent) ||
    (/Mac/.test(navigator.userAgent) && navigator.maxTouchPoints > 1)
  );
}

function makePdf(state: LaundryState, o: Order): File | null {
  try {
    return billPdfFile(billReceipt(state, o), o.id);
  } catch {
    Repo.showInfo("Couldn't make the bill PDF — try again");
    return null;
  }
}

/**
 * Mobile only: desktop Chrome also exposes navigator.share (macOS/Windows),
 * but there "Download" must stay a real download.
 */
function canShareFile(file: File): boolean {
  const nav = navigator as Navigator & { canShare?: (d: ShareData) => boolean };
  return isMobileDevice() && typeof nav.share === "function" && !!nav.canShare?.({ files: [file] });
}

function saveFile(file: File): void {
  const url = URL.createObjectURL(file);
  const a = document.createElement("a");
  a.href = url;
  a.download = file.name;
  document.body.appendChild(a);
  a.click();
  a.remove();
  setTimeout(() => URL.revokeObjectURL(url), 2000);
}

function isDismissal(e: unknown): boolean {
  return e instanceof DOMException && e.name === "AbortError";
}

/** Sends the bill PDF to the customer on WhatsApp, and marks the bill sent. */
export function sendBillOnWhatsApp(state: LaundryState, o: Order): void {
  const file = makePdf(state, o);
  if (!file) return;
  const caption = billCaption(state, o);

  if (canShareFile(file)) {
    navigator.share({ files: [file], text: caption, title: `Bill #${o.id}` }).then(
      () => Repo.sendBill(o.id), // marks billSent + analytics + toast
      (e) => {
        if (isDismissal(e)) return;
        saveFile(file);
        Repo.showInfo(`${file.name} saved — attach it in WhatsApp`);
      },
    );
    return;
  }

  // Desktop: open the customer's chat (synchronously, so it isn't
  // popup-blocked) and download the PDF to attach there.
  window.open(waUrl(customerDigits(state, o.custId), caption), "_blank", "noopener,noreferrer");
  saveFile(file);
  Repo.sendBill(o.id);
  Repo.showInfo(`${file.name} downloaded — attach it in the WhatsApp chat`);
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
  if (canShareFile(file)) {
    navigator.share({ files: [file], title: `Bill #${o.id}` }).catch((e) => {
      if (isDismissal(e)) return;
      saveFile(file); // share unavailable after all — fall back to the download
      Repo.showInfo(`${file.name} downloaded`);
    });
    return;
  }
  saveFile(file);
  Repo.showInfo(`${file.name} downloaded`);
}
