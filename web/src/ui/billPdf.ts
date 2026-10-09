"use client";

import type { BillReceipt } from "@/domain/billReceipt";
import { BILL_SCALE, BILL_W, renderBill } from "./billRender";

/**
 * Renders a BillReceipt to a one-page PDF, fully synchronously (canvas →
 * JPEG → hand-written PDF), so callers can still hand the file to
 * navigator.share inside the tap's user gesture — iOS Safari rejects a share
 * that comes after an await. No PDF library: the page is one embedded image,
 * which also sidesteps the ₹ glyph missing from the standard PDF fonts.
 */

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

/** True on phones/tablets, where the native share sheet is the better "save". */
function isMobileDevice(): boolean {
  // iPadOS 13+ reports itself as "Macintosh" — the touch check catches it.
  return (
    /Android|iPhone|iPad|iPod/i.test(navigator.userAgent) ||
    (/Mac/.test(navigator.userAgent) && navigator.maxTouchPoints > 1)
  );
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

/**
 * Phones: the native share sheet (save to Files / send). Desktop: a real
 * download — desktop Chrome also has navigator.share, but "Download" must
 * stay a download there. A failed share (not a dismissal) falls back to the
 * download. Call it synchronously inside the tap (iOS Safari requirement).
 */
export async function shareOrSaveFile(file: File, title: string): Promise<"shared" | "saved" | "dismissed"> {
  const nav = navigator as Navigator & { canShare?: (d: ShareData) => boolean };
  if (isMobileDevice() && typeof nav.share === "function" && nav.canShare?.({ files: [file] })) {
    try {
      await nav.share({ files: [file], title });
      return "shared";
    } catch (e) {
      if (e instanceof DOMException && e.name === "AbortError") return "dismissed";
    }
  }
  saveFile(file);
  return "saved";
}

/** The receipt as a PNG data URL, for the in-app "View bill" preview. */
export function billPreviewUrl(r: BillReceipt, minH = 0): string {
  return renderBill(r, minH).toDataURL("image/png");
}

/** The receipt as a PDF file named `Bill-<order>.pdf`. Synchronous. */
export function billPdfFile(r: BillReceipt, orderId: number): File {
  const canvas = renderBill(r);
  const jpeg = dataUrlBytes(canvas.toDataURL("image/jpeg", 0.92));
  const blob = jpegPdf(jpeg, canvas.width, canvas.height, BILL_W, canvas.height / BILL_SCALE);
  return new File([blob], `Bill-${orderId}.pdf`, { type: "application/pdf" });
}
