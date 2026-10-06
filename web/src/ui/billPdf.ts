"use client";

import type { BillReceipt } from "@/domain/billReceipt";

/**
 * Renders a BillReceipt to a one-page PDF, fully synchronously (canvas →
 * JPEG → hand-written PDF), so callers can still hand the file to
 * navigator.share inside the tap's user gesture — iOS Safari rejects a share
 * that comes after an await. No PDF library: the page is one embedded image,
 * which also sidesteps the ₹ glyph missing from the standard PDF fonts.
 */

const W = 380; // receipt width in CSS px == PDF points
const PAD = 22;
const SCALE = 3; // pixels per point, for crisp text when zoomed/printed

const INK = "#16181f";
const MUTED = "#5b6168";
const RULE = "#9aa0a6";
const FONT = "-apple-system, 'Segoe UI', Roboto, 'Noto Sans', Arial, sans-serif";

// Table columns (right edges, except Qty which is centred).
const COL_QTY = 190;
const COL_RATE = 280;
const ITEM_W = 140;

function font(ctx: CanvasRenderingContext2D, px: number, weight = 400) {
  ctx.font = `${weight} ${px}px ${FONT}`;
}

/** Greedy word-wrap; a single over-long word is left on its own line. */
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

/** Lays the receipt out; draws only when `draw` is true. Returns the height. */
function layout(ctx: CanvasRenderingContext2D, r: BillReceipt, draw: boolean): number {
  const left = PAD;
  const right = W - PAD;
  const mid = W / 2;
  let y = PAD;

  const text = (s: string, x: number, yy: number, align: CanvasTextAlign, color = INK) => {
    if (!draw) return;
    ctx.fillStyle = color;
    ctx.textAlign = align;
    ctx.fillText(s, x, yy);
  };
  const rule = (dashed: boolean) => {
    if (draw) {
      ctx.strokeStyle = dashed ? RULE : INK;
      ctx.lineWidth = dashed ? 1 : 1.2;
      ctx.setLineDash(dashed ? [3, 3] : []);
      ctx.beginPath();
      ctx.moveTo(left, y);
      ctx.lineTo(right, y);
      ctx.stroke();
      ctx.setLineDash([]);
    }
  };
  const row = (label: string, value: string, px = 14, bold = true) => {
    y += px + 7;
    font(ctx, px, 400);
    text(label, left, y, "left");
    font(ctx, px, bold ? 700 : 400);
    text(value, right, y, "right");
  };

  ctx.textBaseline = "alphabetic";

  // header
  font(ctx, 22, 700);
  for (const l of wrap(ctx, r.shopName, right - left)) {
    y += 26;
    text(l, mid, y, "center");
  }
  font(ctx, 13, 400);
  if (r.shopPhone) {
    y += 19;
    text(r.shopPhone, mid, y, "center", MUTED);
  }
  y += 19;
  text("Bill Receipt", mid, y, "center", MUTED);
  y += 14;
  rule(true);
  y += 6;

  // order info
  for (const i of r.info) row(i.label, i.value);
  y += 14;
  rule(true);
  y += 6;

  // table header
  y += 20;
  font(ctx, 13, 700);
  text("Item", left, y, "left");
  text("Qty", COL_QTY, y, "center");
  text("Rate", COL_RATE, y, "right");
  text("Total", right, y, "right");
  y += 10;
  rule(true);

  // lines
  for (const l of r.lines) {
    y += 20;
    const top = y;
    font(ctx, 13, 400);
    text(l.qty, COL_QTY, top, "center");
    text(l.rate, COL_RATE, top, "right");
    text(l.total, right, top, "right");
    wrap(ctx, l.item, ITEM_W).forEach((s, i) => {
      if (i > 0) y += 16;
      text(s, left, y, "left");
    });
    if (l.sub) {
      font(ctx, 12, 400);
      for (const s of wrap(ctx, l.sub, ITEM_W)) {
        y += 15;
        text(s, left, y, "left", MUTED);
      }
    }
    y += 4;
  }
  y += 8;
  rule(true);
  y += 4;

  // totals
  row("Subtotal", r.subtotal);
  for (const e of r.extras) row(e.label, e.value);
  y += 12;
  rule(false);
  y += 32;
  font(ctx, 20, 700);
  text("Total", left, y, "left");
  font(ctx, 22, 800);
  text(r.total, right, y, "right");
  y += 16;
  rule(true);
  y += 26;
  font(ctx, 14, 700);
  text("Thank you!", mid, y, "center");
  return y + PAD;
}

function renderCanvas(r: BillReceipt): HTMLCanvasElement {
  const canvas = document.createElement("canvas");
  const measure = canvas.getContext("2d");
  if (!measure) throw new Error("Canvas unsupported");
  const H = Math.ceil(layout(measure, r, false));

  canvas.width = W * SCALE; // resizing resets the context state
  canvas.height = H * SCALE;
  const ctx = canvas.getContext("2d")!;
  ctx.scale(SCALE, SCALE);
  ctx.fillStyle = "#ffffff";
  ctx.fillRect(0, 0, W, H);
  layout(ctx, r, true);
  return canvas;
}

function dataUrlBytes(url: string): Uint8Array {
  const bin = atob(url.slice(url.indexOf(",") + 1));
  const out = new Uint8Array(bin.length);
  for (let i = 0; i < bin.length; i++) out[i] = bin.charCodeAt(i);
  return out;
}

/** Minimal PDF 1.4: one page of `wPt`×`hPt` points showing one JPEG. */
function jpegPdf(jpeg: Uint8Array, wPx: number, hPx: number, wPt: number, hPt: number): Blob {
  const parts: (string | Uint8Array)[] = [];
  const offsets: number[] = [];
  let len = 0;
  const push = (p: string | Uint8Array) => {
    parts.push(p);
    len += typeof p === "string" ? p.length : p.byteLength; // strings are ASCII-only
  };
  const obj = (n: number, body: string | (string | Uint8Array)[]) => {
    offsets[n] = len;
    push(`${n} 0 obj\n`);
    (Array.isArray(body) ? body : [body]).forEach(push);
    push("\nendobj\n");
  };

  const content = `q ${wPt} 0 0 ${hPt} 0 0 cm /Im0 Do Q`;
  push("%PDF-1.4\n");
  obj(1, "<< /Type /Catalog /Pages 2 0 R >>");
  obj(2, "<< /Type /Pages /Kids [3 0 R] /Count 1 >>");
  obj(3, `<< /Type /Page /Parent 2 0 R /MediaBox [0 0 ${wPt} ${hPt}] /Resources << /XObject << /Im0 5 0 R >> >> /Contents 4 0 R >>`);
  obj(4, `<< /Length ${content.length} >>\nstream\n${content}\nendstream`);
  obj(5, [
    `<< /Type /XObject /Subtype /Image /Width ${wPx} /Height ${hPx} /ColorSpace /DeviceRGB /BitsPerComponent 8 /Filter /DCTDecode /Length ${jpeg.byteLength} >>\nstream\n`,
    jpeg,
    "\nendstream",
  ]);
  const xref = len;
  push(`xref\n0 6\n0000000000 65535 f \n`);
  for (let n = 1; n <= 5; n++) push(`${String(offsets[n]).padStart(10, "0")} 00000 n \n`);
  push(`trailer\n<< /Size 6 /Root 1 0 R >>\nstartxref\n${xref}\n%%EOF\n`);
  return new Blob(parts as BlobPart[], { type: "application/pdf" });
}

/** The receipt as a PNG data URL, for the in-app "View bill" preview. */
export function billPreviewUrl(r: BillReceipt): string {
  return renderCanvas(r).toDataURL("image/png");
}

/** The receipt as a PDF file named `Bill-<order>.pdf`. Synchronous. */
export function billPdfFile(r: BillReceipt, orderId: number): File {
  const canvas = renderCanvas(r);
  const jpeg = dataUrlBytes(canvas.toDataURL("image/jpeg", 0.92));
  const blob = jpegPdf(jpeg, canvas.width, canvas.height, W, canvas.height / SCALE);
  return new File([blob], `Bill-${orderId}.pdf`, { type: "application/pdf" });
}
