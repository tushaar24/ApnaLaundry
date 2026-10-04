"use client";

import * as AppDate from "@/core/appdate";
import { rupees } from "@/core/money";
import { amtOf } from "@/domain/laundryMath";
import type { LaundryState, Order } from "@/domain/models";
import * as Sel from "@/domain/selectors";
import * as Repo from "@/data/repository";

/**
 * Real "Download" and "Send on WhatsApp" for a bill — the previous handlers
 * only showed a toast. Works on web across iOS, Android and desktop:
 *  - WhatsApp: opens wa.me with the customer's number + the bill as text.
 *  - Download: renders the bill to a PNG, then shares it via the native share
 *    sheet where supported (iOS Safari / Android Chrome) or downloads the file
 *    (desktop).
 */

interface Row {
  label: string;
  value: string;
}

function billRows(o: Order): { lines: Row[]; total: string } {
  const lines: Row[] = o.lines.map((l) => ({
    label: l.kg > 0 ? `${l.serviceName} · ${Sel.trimKg(l.kg)} kg` : `${l.itemName} × ${l.qty}`,
    value: rupees(l.amt),
  }));
  if (o.express && o.exAmt > 0) lines.push({ label: "Express", value: `+ ${rupees(o.exAmt)}` });
  if (o.fee > 0) lines.push({ label: "Pickup / delivery", value: `+ ${rupees(o.fee)}` });
  if (o.discount > 0) lines.push({ label: "Discount", value: `− ${rupees(o.discount)}` });
  return { lines, total: rupees(amtOf(o)) };
}

/** Plain-text bill for WhatsApp (asterisks render as bold in WhatsApp). */
function billText(state: LaundryState, o: Order): string {
  const c = Sel.customer(state, o.custId);
  const { lines, total } = billRows(o);
  const body = lines.map((r) => `${r.label}  —  ${r.value}`).join("\n");
  return [
    `*${state.shop.name}*`,
    `+91 ${Sel.fmtPhone(state.shop.phone)}`,
    "",
    `Bill #${o.id} · ${AppDate.plain(o.createdOn)}`,
    `To: ${c.name}`,
    "",
    body,
    "————————",
    `*Total: ${total}*`,
    "",
    "Thank you!",
  ].join("\n");
}

/** Opens WhatsApp to the customer with the bill text, and marks the bill sent. */
export function sendBillOnWhatsApp(state: LaundryState, o: Order): void {
  const c = Sel.customer(state, o.custId);
  const digits = (c.phone || "").replace(/\D/g, "").slice(-10);
  const text = encodeURIComponent(billText(state, o));
  // With a number → chat opens straight to the customer; without → WhatsApp
  // asks the owner to pick a contact.
  const url = digits.length === 10 ? `https://wa.me/91${digits}?text=${text}` : `https://wa.me/?text=${text}`;
  // Must open synchronously in the click handler so it isn't popup-blocked.
  window.open(url, "_blank", "noopener,noreferrer");
  Repo.sendBill(o.id); // marks billSent + analytics + toast
}

/** Opens WhatsApp to the customer with a khata balance reminder. */
export function sendReminderOnWhatsApp(state: LaundryState, custId: string, bal: number): void {
  const c = Sel.customer(state, custId);
  const digits = (c.phone || "").replace(/\D/g, "").slice(-10);
  const text = encodeURIComponent([
    `*${state.shop.name}*`,
    `+91 ${Sel.fmtPhone(state.shop.phone)}`,
    "",
    `${c.name}, a gentle reminder — your laundry balance is *${rupees(bal)}*.`,
    "Please clear it on your next visit. Thank you!",
  ].join("\n"));
  const url = digits.length === 10 ? `https://wa.me/91${digits}?text=${text}` : `https://wa.me/?text=${text}`;
  // Must open synchronously in the click handler so it isn't popup-blocked.
  window.open(url, "_blank", "noopener,noreferrer");
  Repo.showInfo(`Opening WhatsApp · reminder to ${Sel.firstName(c.name)} for ${rupees(bal)}`);
}

// ---------------- PNG rendering + download/share ----------------

async function renderBillPng(state: LaundryState, o: Order): Promise<Blob> {
  const c = Sel.customer(state, o.custId);
  const { lines, total } = billRows(o);

  const S = 2; // device-pixel scale for crisp text
  const W = 620;
  const pad = 36;
  const lineH = 34;
  const headerH = 150;
  const footerH = 120;
  const H = headerH + lines.length * lineH + footerH;

  const canvas = document.createElement("canvas");
  canvas.width = W * S;
  canvas.height = H * S;
  const ctx = canvas.getContext("2d");
  if (!ctx) throw new Error("Canvas unsupported");
  ctx.scale(S, S);

  // background
  ctx.fillStyle = "#ffffff";
  ctx.fillRect(0, 0, W, H);

  const left = pad;
  const right = W - pad;
  let y = pad + 8;

  ctx.textBaseline = "alphabetic";
  ctx.fillStyle = "#16181f";
  ctx.font = "700 30px -apple-system, Segoe UI, Roboto, sans-serif";
  ctx.textAlign = "left";
  ctx.fillText(state.shop.name, left, y);
  y += 26;
  ctx.font = "400 15px -apple-system, Segoe UI, Roboto, sans-serif";
  ctx.fillStyle = "#5b6168";
  ctx.fillText(`+91 ${Sel.fmtPhone(state.shop.phone)}`, left, y);
  y += 24;

  const rule = (yy: number) => {
    ctx.strokeStyle = "#e7e3da";
    ctx.lineWidth = 1;
    ctx.beginPath();
    ctx.moveTo(left, yy);
    ctx.lineTo(right, yy);
    ctx.stroke();
  };
  rule(y);
  y += 26;

  ctx.fillStyle = "#5b6168";
  ctx.font = "600 14px -apple-system, Segoe UI, Roboto, sans-serif";
  ctx.fillText(`Bill #${o.id} · ${AppDate.plain(o.createdOn)}`, left, y);
  y += 24;
  ctx.fillStyle = "#16181f";
  ctx.font = "600 16px -apple-system, Segoe UI, Roboto, sans-serif";
  ctx.fillText(`To: ${c.name}`, left, y);
  y += 30;

  for (const r of lines) {
    ctx.fillStyle = "#16181f";
    ctx.font = "400 16px -apple-system, Segoe UI, Roboto, sans-serif";
    ctx.textAlign = "left";
    ctx.fillText(r.label, left, y);
    ctx.font = "600 16px -apple-system, Segoe UI, Roboto, sans-serif";
    ctx.textAlign = "right";
    ctx.fillText(r.value, right, y);
    y += lineH;
  }

  y += 2;
  rule(y);
  y += 30;
  ctx.textAlign = "left";
  ctx.fillStyle = "#16181f";
  ctx.font = "700 20px -apple-system, Segoe UI, Roboto, sans-serif";
  ctx.fillText("Total", left, y);
  ctx.textAlign = "right";
  ctx.fillText(total, right, y);
  y += 38;
  ctx.textAlign = "left";
  ctx.fillStyle = "#1d4ed8";
  ctx.font = "700 16px -apple-system, Segoe UI, Roboto, sans-serif";
  ctx.fillText("Thank you!", left, y);

  return await new Promise<Blob>((resolve, reject) => {
    canvas.toBlob((b) => (b ? resolve(b) : reject(new Error("toBlob failed"))), "image/png");
  });
}

/** True on phones/tablets, where the native share sheet is the better "save". */
function isMobileDevice(): boolean {
  // iPadOS 13+ reports itself as "Macintosh" — the touch check catches it.
  return (
    /Android|iPhone|iPad|iPod/i.test(navigator.userAgent) ||
    (/Mac/.test(navigator.userAgent) && navigator.maxTouchPoints > 1)
  );
}

/** Share the bill image via the native sheet (mobile) or download it (desktop). */
export async function downloadBill(state: LaundryState, o: Order): Promise<void> {
  let blob: Blob;
  try {
    blob = await renderBillPng(state, o);
  } catch {
    Repo.showInfo("Couldn't make the bill image — try again");
    return;
  }
  const file = new File([blob], `Bill-${o.id}.png`, { type: "image/png" });

  // Mobile (iOS Safari 15+/Android Chrome): native share sheet → save or send.
  // Desktop Chrome also exposes navigator.share (macOS/Windows), but there
  // "Download" must stay a real download — so the share path is mobile-only,
  // and a failed share (not a user dismissal) falls through to the download.
  const nav = navigator as Navigator & { canShare?: (d: ShareData) => boolean };
  if (isMobileDevice() && typeof nav.share === "function" && nav.canShare?.({ files: [file] })) {
    try {
      await nav.share({ files: [file], title: `Bill #${o.id}` });
      return;
    } catch (e) {
      if (e instanceof DOMException && e.name === "AbortError") return; // user dismissed
      /* share unavailable after all — fall through to the download */
    }
  }

  // Desktop: download the file.
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = `Bill-${o.id}.png`;
  document.body.appendChild(a);
  a.click();
  a.remove();
  setTimeout(() => URL.revokeObjectURL(url), 2000);
  Repo.showInfo(`Bill-${o.id}.png downloaded`);
}
