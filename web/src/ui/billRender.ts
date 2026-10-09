"use client";

import QRCode from "qrcode";
import type { BillReceipt } from "@/domain/billReceipt";
import { logoUrl } from "@/domain/billDetails";

/**
 * Draws a BillReceipt in its design (Classic / Bold / Receipt) on a canvas.
 * The same drawing backs every bill surface — the onboarding preview, the
 * "View bill" sheet, the shared PDF and the public /b/ page — so what the
 * owner picks is exactly what customers get.
 *
 * Drawing is synchronous (PDF sharing must happen inside the tap on iOS), so
 * fonts and the logo are loaded ahead of time: call `prepareBillAssets` (or
 * `preloadLogo`) before rendering; until then the bill draws without the logo.
 */

export const BILL_W = 380; // CSS px == PDF points
const PAD = 22;
export const BILL_SCALE = 3; // pixels per point

const C = {
  ink: "#16191D",
  muted: "#5B6168",
  border: "#E2DED6",
  blue: "#1D4ED8",
  tint: "#E6ECFB",
  bandText: "#DCE5FB",
  green: "#15803D",
  paper: "#FFFFFF",
  slip: "#FFFEFA",
};

const MONO = "'Courier New', Courier, monospace";

// ── assets ──

type LogoEntry = { img: HTMLImageElement; gray: HTMLCanvasElement | null };
const logos = new Map<string, LogoEntry>();
const pending = new Map<string, Promise<void>>();

/** Loads (once) the shop logo so bills can draw it synchronously. */
export function preloadLogo(logoId: string): Promise<void> {
  if (!logoId || logos.has(logoId)) return Promise.resolve();
  const inflight = pending.get(logoId);
  if (inflight) return inflight;
  const p = new Promise<void>((resolve) => {
    const img = new Image();
    img.decoding = "async";
    img.onload = () => {
      logos.set(logoId, { img, gray: null });
      resolve();
    };
    img.onerror = () => resolve(); // a missing logo just isn't printed
    img.src = logoUrl(logoId);
  }).finally(() => pending.delete(logoId));
  pending.set(logoId, p);
  return p;
}

/** Registers a logo that's already in memory (just picked / uploaded). */
export function registerLogo(logoId: string, img: HTMLImageElement) {
  logos.set(logoId, { img, gray: null });
}

/** Thermal printers print only black: the Receipt design uses a B&W copy. */
function grayLogo(e: LogoEntry): CanvasImageSource {
  if (e.gray) return e.gray;
  const c = document.createElement("canvas");
  const side = 160;
  const k = Math.min(side / e.img.naturalWidth, side / e.img.naturalHeight, 1);
  c.width = Math.max(1, Math.round(e.img.naturalWidth * k));
  c.height = Math.max(1, Math.round(e.img.naturalHeight * k));
  const ctx = c.getContext("2d");
  if (!ctx) return e.img;
  ctx.drawImage(e.img, 0, 0, c.width, c.height);
  try {
    const px = ctx.getImageData(0, 0, c.width, c.height);
    const d = px.data;
    for (let i = 0; i < d.length; i += 4) {
      const lum = 0.299 * d[i] + 0.587 * d[i + 1] + 0.114 * d[i + 2];
      const v = Math.max(0, Math.min(255, (lum - 128) * 1.25 + 128)); // a little contrast
      d[i] = d[i + 1] = d[i + 2] = v;
    }
    ctx.putImageData(px, 0, 0);
  } catch {
    return e.img; // tainted (shouldn't happen: same-origin) — print as is
  }
  e.gray = c;
  return c;
}

/** Fonts the bill uses: the site's next/font families, resolved for canvas. */
function families(): { display: string; body: string } {
  const css = getComputedStyle(document.documentElement);
  const body = css.getPropertyValue("--font-figtree").trim();
  const display = css.getPropertyValue("--font-bricolage").trim();
  return {
    body: body ? `${body}, system-ui, sans-serif` : "system-ui, sans-serif",
    display: display ? `${display}, ${body || "system-ui"}, sans-serif` : "system-ui, sans-serif",
  };
}

/** Loads fonts + the logo a bill needs; render after this for a complete bill. */
export async function prepareBillAssets(r: Pick<BillReceipt, "shop">): Promise<void> {
  const f = families();
  try {
    await Promise.all([
      document.fonts.load(`700 24px ${f.display}`),
      document.fonts.load(`400 14px ${f.body}`),
      document.fonts.load(`700 14px ${f.body}`),
    ]);
  } catch {
    /* fall back to system fonts */
  }
  await preloadLogo(r.shop.logoId);
}

// ── layout ──

interface Pen {
  ctx: CanvasRenderingContext2D;
  draw: boolean;
  y: number;
}

function wrap(ctx: CanvasRenderingContext2D, text: string, maxW: number): string[] {
  const out: string[] = [];
  let cur = "";
  for (const word of text.split(/\s+/).filter(Boolean)) {
    const next = cur ? `${cur} ${word}` : word;
    if (cur && ctx.measureText(next).width > maxW) {
      out.push(cur);
      cur = word;
    } else {
      cur = next;
    }
  }
  if (cur) out.push(cur);
  return out.length ? out : [""];
}

/**
 * Lays the bill out; draws only when `draw`. Returns the natural height.
 * With `minH` the "Thank you" line is pinned to the bottom of a taller card
 * (equal-height slides in the onboarding strip).
 */
function layout(ctx: CanvasRenderingContext2D, r: BillReceipt, draw: boolean, minH = 0): number {
  const t = r.template;
  const receipt = t === "receipt";
  const f = families();
  const BODY = receipt ? MONO : f.body;
  const DISPLAY = receipt ? MONO : f.display;
  const left = PAD;
  const right = BILL_W - PAD;
  const mid = BILL_W / 2;
  const p: Pen = { ctx, draw, y: 0 };
  ctx.textBaseline = "alphabetic";

  const font = (px: number, weight = 400, fam = BODY) => {
    ctx.font = `${weight} ${px}px ${fam}`;
  };
  const text = (s: string, x: number, y: number, align: CanvasTextAlign, color = C.ink) => {
    if (!draw) return;
    ctx.fillStyle = color;
    ctx.textAlign = align;
    ctx.fillText(s, x, y);
  };
  const rule = (opts: { dashed?: boolean; width?: number; color?: string; x0?: number; x1?: number } = {}) => {
    if (!draw) return;
    ctx.strokeStyle = opts.color ?? (receipt ? C.ink : C.border);
    ctx.lineWidth = opts.width ?? 1;
    ctx.setLineDash(opts.dashed || receipt ? [4, 3] : []);
    ctx.beginPath();
    ctx.moveTo(opts.x0 ?? left, p.y);
    ctx.lineTo(opts.x1 ?? right, p.y);
    ctx.stroke();
    ctx.setLineDash([]);
  };
  const lines = (s: string, px: number, weight: number, fam: string, maxW: number, x: number, align: CanvasTextAlign, color: string, lh: number) => {
    font(px, weight, fam);
    for (const l of wrap(ctx, s, maxW)) {
      p.y += lh;
      text(l, x, p.y, align, color);
    }
  };
  const logo = (size: number, x: number, y: number, gray: boolean) => {
    const e = logos.get(r.shop.logoId);
    if (!e || !draw) return;
    const src = gray ? grayLogo(e) : e.img;
    const w = e.img.naturalWidth;
    const h = e.img.naturalHeight;
    const k = Math.min(size / w, size / h);
    const dw = w * k;
    const dh = h * k;
    ctx.drawImage(src, x + (size - dw) / 2, y + (size - dh) / 2, dw, dh);
  };
  const hasLogo = !!r.shop.logoId && logos.has(r.shop.logoId);
  const shopName = receipt ? r.shop.name.toUpperCase() : r.shop.name;
  const details = [r.shop.phone, r.shop.address, r.shop.gstin ? `GSTIN ${r.shop.gstin}` : ""].filter(Boolean);

  // 1. shop block
  if (t === "bold") {
    // Measure the band first, then paint it under the text.
    const textX = left + (hasLogo ? 56 : 0);
    const textW = right - textX;
    const measure = (): number => {
      let h = 0;
      font(22, 700, DISPLAY);
      h += wrap(ctx, shopName, textW).length * 26;
      font(12.5, 400, BODY);
      for (const d of details) h += wrap(ctx, d, textW).length * 17;
      return h;
    };
    const textH = measure();
    const bandH = Math.max(hasLogo ? 44 : 0, textH) + PAD * 2 - 4;
    if (draw) {
      ctx.fillStyle = C.blue;
      ctx.fillRect(0, 0, BILL_W, bandH);
    }
    if (hasLogo && draw) {
      const ty = PAD - 2 + (Math.max(44, textH) - 44) / 2;
      ctx.fillStyle = "#FFFFFF";
      ctx.beginPath();
      ctx.roundRect(left, ty, 44, 44, 10);
      ctx.fill();
      logo(36, left + 4, ty + 4, false);
    }
    p.y = PAD - 2 + Math.max(0, ((hasLogo ? 44 : 0) - textH) / 2) - 6;
    lines(shopName, 22, 700, DISPLAY, textW, textX, "left", "#FFFFFF", 26);
    for (const d of details) lines(d, 12.5, 400, BODY, textW, textX, "left", C.bandText, 17);
    p.y = bandH + 6;
  } else {
    p.y = PAD;
    const size = receipt ? 44 : 52;
    if (hasLogo) {
      logo(size, mid - size / 2, p.y, receipt);
      p.y += size + 4;
    }
    if (receipt) lines(shopName, 18, 700, DISPLAY, right - left, mid, "center", C.ink, 22);
    else lines(shopName, 24, 700, DISPLAY, right - left, mid, "center", C.ink, 28);
    for (const d of details) lines(d, receipt ? 12 : 13, 400, BODY, right - left, mid, "center", C.muted, 17);
    p.y += 14;
    rule();
  }

  // 2. bill number + date
  p.y += 22;
  font(14, 700);
  text(r.billNo, left, p.y, "left");
  font(13, 400);
  text(r.date, right, p.y, "right", C.muted);

  // 3. customer
  if (r.customer.name) lines(r.customer.name, 15, 700, BODY, right - left, left, "left", C.ink, 22);
  if (r.customer.phone) lines(r.customer.phone, 13, 400, BODY, right - left, left, "left", C.muted, 17);
  p.y += 12;
  rule();

  // 4. items — each numbered (serial no.) in a narrow left column
  const SN_W = 28;
  const itemX = left + SN_W;
  if (t === "bold") {
    p.y += 18;
    font(11, 700);
    text("NO.", left, p.y, "left", C.muted);
    text("ITEM", itemX, p.y, "left", C.muted);
    text("AMOUNT", right, p.y, "right", C.muted);
    p.y += 8;
    rule();
  }
  r.lines.forEach((l, n) => {
    p.y += 21;
    font(14, receipt ? 400 : 600);
    text(l.amount, right, p.y, "right");
    text(`${n + 1}.`, left, p.y, "left", C.muted);
    const itemW = right - itemX - 90;
    wrap(ctx, l.item, itemW).forEach((s, i) => {
      if (i > 0) p.y += 18;
      text(s, itemX, p.y, "left");
    });
    if (l.sub) lines(l.sub, 12, 400, BODY, itemW, itemX, "left", C.muted, 15);
  });
  p.y += 12;
  rule();

  // 5. totals
  const row = (label: string, value: string, color = C.ink) => {
    p.y += 20;
    font(13.5, 400);
    text(label, left, p.y, "left", color === C.ink ? C.muted : color);
    font(13.5, 600);
    text(value, right, p.y, "right", color);
  };
  row("Subtotal", r.subtotal);
  for (const e of r.extras) row(e.label, e.value, e.discount ? C.green : C.ink);
  p.y += 12;
  if (t === "bold") {
    const h = 44;
    if (draw) {
      ctx.fillStyle = C.tint;
      ctx.beginPath();
      ctx.roundRect(left - 8, p.y, right - left + 16, h, 999);
      ctx.fill();
    }
    p.y += 29;
    font(16, 700, DISPLAY);
    text("Total", left + 6, p.y, "left", C.blue);
    font(20, 700, DISPLAY);
    text(r.total, right - 6, p.y, "right", C.blue);
    p.y += h - 29;
  } else {
    rule({ width: receipt ? 1 : 1.5, color: C.ink, dashed: receipt });
    p.y += 30;
    font(receipt ? 17 : 18, 700, DISPLAY);
    text(receipt ? "TOTAL" : "Total", left, p.y, "left");
    font(receipt ? 19 : 22, 700, DISPLAY);
    text(r.total, right, p.y, "right");
    p.y += 8;
  }

  // 6. payment status (real bills)
  if (r.payStatus) {
    p.y += 22;
    font(13.5, 700);
    text(r.payStatus, mid, p.y, "center", r.payStatus === "Paid in full" ? C.green : C.ink);
  }

  // 7. UPI pay QR (valid UPI ids only) + ready-by
  if (r.upi) {
    p.y += 18;
    const qr = QRCode.create(r.upi.payload, { errorCorrectionLevel: "M" });
    const n = qr.modules.size;
    const box = 124;
    const cell = box / (n + 4); // 2-module quiet zone each side
    const x0 = mid - box / 2;
    if (draw) {
      ctx.fillStyle = "#FFFFFF";
      ctx.fillRect(x0, p.y, box, box);
      ctx.fillStyle = "#000000";
      for (let row2 = 0; row2 < n; row2++) {
        for (let col = 0; col < n; col++) {
          if (qr.modules.get(row2, col)) {
            ctx.fillRect(x0 + (col + 2) * cell, p.y + (row2 + 2) * cell, cell + 0.05, cell + 0.05);
          }
        }
      }
    }
    p.y += box;
    lines(`Scan to pay ${r.upi.amount}`, 14, 700, BODY, right - left, mid, "center", C.ink, 20);
    lines(`UPI · ${r.upi.id}`, 12, 400, BODY, right - left, mid, "center", C.muted, 16);
  } else {
    p.y += 4;
  }
  if (r.readyBy) lines(r.readyBy, 12.5, 400, BODY, right - left, mid, "center", C.muted, 18);

  // 8. terms
  if (r.terms.length) {
    p.y += 10;
    rule({ dashed: true });
    p.y += 2;
    lines(receipt ? "TERMS AND CONDITIONS" : "Terms and Conditions", 12.5, 700, BODY, right - left, left, "left", C.ink, 18);
    p.y += 2;
    r.terms.forEach((term, i) => lines(`${i + 1}. ${term}`, 11.5, 400, BODY, right - left, left, "left", C.muted, 15));
  }

  // 9. thank you — pinned to the bottom when the card is stretched
  const natural = p.y + 34 + PAD;
  if (minH > natural) p.y += minH - natural;
  p.y += 34;
  font(14, 700, receipt ? MONO : BODY);
  text(receipt ? "*** Thank you ***" : "Thank you!", mid, p.y, "center", receipt ? C.ink : C.muted);
  return Math.max(natural, minH);
}

/** The bill's natural height in points (for equal-height slides). */
export function billHeight(r: BillReceipt): number {
  const ctx = document.createElement("canvas").getContext("2d");
  if (!ctx) return 0;
  return Math.ceil(layout(ctx, r, false));
}

/** Renders the bill (optionally stretched to `minH` points) to a canvas. */
export function renderBill(r: BillReceipt, minH = 0): HTMLCanvasElement {
  const canvas = document.createElement("canvas");
  const measure = canvas.getContext("2d");
  if (!measure) throw new Error("Canvas unsupported");
  const H = Math.ceil(layout(measure, r, false, minH));
  canvas.width = BILL_W * BILL_SCALE; // resizing resets the context state
  canvas.height = H * BILL_SCALE;
  const ctx = canvas.getContext("2d")!;
  ctx.scale(BILL_SCALE, BILL_SCALE);
  ctx.fillStyle = r.template === "receipt" ? C.slip : C.paper;
  ctx.fillRect(0, 0, BILL_W, H);
  layout(ctx, r, true, minH);
  return canvas;
}
