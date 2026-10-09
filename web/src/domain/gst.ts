import { rupeesPaise } from "@/core/money";

/**
 * GST on an order — exclusive (added on top of the price) or inclusive
 * (already in the price). `gstCalc` is the ONE place the tax maths lives:
 * `amtOf` in laundryMath is its total, and every screen, bill design, khata
 * entry and payment goes through that. Port twin: domain/Gst.kt.
 *
 * Tax figures are kept in paise (integers) so web and Android agree to the
 * paisa; the total a customer pays stays in whole rupees.
 */

export type GstMode = "excl" | "incl";

/** The GST fields on an order, and the shop's last-used defaults. */
export interface GstFields {
  gstOn: boolean;
  gstPct: number; // 18, 5, 12 … may be fractional
  gstMode: GstMode; // "excl" = added on top of the price, "incl" = already in the price
}

export const DEFAULT_GST_PCT = 18;
export const GST_RATE_CHIPS = [5, 12, 18];

/** A new shop: GST off, 18% exclusive ready for the first time it is switched on. */
export function gstDefaults(): GstFields {
  return { gstOn: false, gstPct: DEFAULT_GST_PCT, gstMode: "excl" };
}

export function gstModeFrom(v: unknown): GstMode {
  return v === "incl" ? "incl" : "excl";
}

/** A rate from the wire or a text box: 0–100 (two decimals), else `fallback`. */
export function gstPctFrom(v: unknown, fallback = 0): number {
  const n = typeof v === "number" ? v : typeof v === "string" ? parseFloat(v) : NaN;
  return Number.isFinite(n) && n >= 0 ? Math.min(100, Math.round(n * 100) / 100) : fallback;
}

export interface GstBreakdown {
  on: boolean; // false = no GST on this amount (switched off, 0%, or nothing to tax)
  pct: number;
  mode: GstMode;
  base: number; // ₹ clothes + express + fee − discount
  taxablePaise: number;
  taxPaise: number;
  cgstPaise: number;
  sgstPaise: number;
  roundOffPaise: number; // exclusive only: whole-rupee total − exact amount (−20 = −₹0.20)
  total: number; // ₹ the customer pays, whole rupees
}

/**
 * The maths (HANDOFF-gst §3), on `base` = clothes + express + fee − discount:
 *   exclusive: tax = round2(base × pct/100); total = round(base + tax); roundOff = total − (base + tax)
 *   inclusive: total = base; taxable = round2(base × 100/(100+pct)); tax = base − taxable
 *   CGST = half the tax (rounded down to the paisa), SGST = the rest.
 */
export function gstCalc(base: number, g: GstFields): GstBreakdown {
  const pct = g.gstOn ? g.gstPct : 0;
  if (!(pct > 0) || base <= 0) {
    return {
      on: false, pct: 0, mode: g.gstMode, base, taxablePaise: base * 100, taxPaise: 0, cgstPaise: 0, sgstPaise: 0, roundOffPaise: 0, total: base,
    };
  }
  const basePaise = base * 100;
  let taxablePaise: number;
  let taxPaise: number;
  let total: number;
  let roundOffPaise: number;
  if (g.gstMode === "incl") {
    taxablePaise = Math.round((basePaise * 100) / (100 + pct));
    taxPaise = basePaise - taxablePaise;
    total = base;
    roundOffPaise = 0;
  } else {
    taxablePaise = basePaise;
    taxPaise = Math.round((basePaise * pct) / 100);
    total = Math.round((basePaise + taxPaise) / 100);
    roundOffPaise = total * 100 - (basePaise + taxPaise);
  }
  const cgstPaise = Math.floor(taxPaise / 2);
  return {
    on: true, pct, mode: g.gstMode, base, taxablePaise, taxPaise, cgstPaise, sgstPaise: taxPaise - cgstPaise, roundOffPaise, total,
  };
}

// ── labels shared by the screens and the bill ──

/** "18", "2.5" — a rate without trailing zeros. */
export function fmtPct(pct: number): string {
  return String(Math.round(pct * 100) / 100);
}

/** "9" for 18% — the CGST / SGST half. */
export const halfRate = (pct: number) => fmtPct(pct / 2);

/** App screens: the GST row above the total (exclusive). `markRounded` folds the round-off into the label. */
export function gstRowLabel(g: GstBreakdown, markRounded = false): string {
  return `GST ${fmtPct(g.pct)}% (CGST + SGST)` + (markRounded && g.roundOffPaise !== 0 ? " · rounded" : "");
}
export const gstRowValue = (g: GstBreakdown) => `+ ${rupeesPaise(g.taxPaise)}`;
export const roundOffValue = (g: GstBreakdown) => (g.roundOffPaise < 0 ? "− " : "+ ") + rupeesPaise(Math.abs(g.roundOffPaise));

/** App screens: the small line under the total (inclusive). */
export function gstInclusiveLine(g: GstBreakdown): string {
  return `Includes GST ${fmtPct(g.pct)}%: ${rupeesPaise(g.taxPaise)} · taxable ${rupeesPaise(g.taxablePaise)}`;
}

/** Printed bill: the note under the total (inclusive). */
export function gstBillNote(g: GstBreakdown): string {
  return `Price includes GST ${fmtPct(g.pct)}%: taxable ${rupeesPaise(g.taxablePaise)} + CGST ${rupeesPaise(g.cgstPaise)} + SGST ${rupeesPaise(g.sgstPaise)}`;
}

/** New-order card: what the switch does right now. */
export function gstHint(on: boolean, pct: number, mode: GstMode): string {
  if (!on) return "Add GST to this bill";
  return `${fmtPct(pct)}% · ${mode === "incl" ? "already in your price" : "added on top of the price"}`;
}
