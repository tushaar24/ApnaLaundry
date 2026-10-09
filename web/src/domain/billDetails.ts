import type { BillDetails, BillTemplate, OnboardingStep, Order, OrderLine, Service } from "./models";
import * as AppDate from "@/core/appdate";

/**
 * Optional bill details (phone on bill, address, GSTIN, UPI, logo, terms) and
 * the bill designs: defaults, validation, and the sample order the onboarding
 * "Your bill" step previews. Port twin: domain/BillDetails.kt.
 */

export const BILL_TEMPLATES: { id: BillTemplate; label: string }[] = [
  { id: "classic", label: "Classic" },
  { id: "bold", label: "Bold" },
  { id: "receipt", label: "Receipt" },
];

export const TERM_PRESETS: { id: string; text: string }[] = [
  { id: "check", text: "Please check your clothes at delivery. No claims after." },
  { id: "fade", text: "Not responsible for colour fading or shrinkage." },
  { id: "pocket", text: "Please empty pockets. Not responsible for things left inside." },
  { id: "days", text: "Clothes not collected within 30 days are at the owner's risk." },
];

export const ONBOARDING_STEPS: OnboardingStep[] = ["intro", "name", "services", "bill", "done"];

export function emptyBillDetails(billPhone = ""): BillDetails {
  return {
    billPhone, address: "", gstin: "", upiId: "", logoId: "",
    terms: [], termsCustom: "", billTemplate: "classic",
  };
}

/** Accepts anything from the wire (older servers send none of these). */
export function billDetailsFrom(d: Partial<Record<keyof BillDetails, unknown>>, fallbackPhone = ""): BillDetails {
  const str = (v: unknown) => (typeof v === "string" ? v : "");
  const tpl = str(d.billTemplate);
  return {
    billPhone: str(d.billPhone) || fallbackPhone,
    address: str(d.address),
    gstin: str(d.gstin),
    upiId: str(d.upiId),
    logoId: str(d.logoId),
    terms: Array.isArray(d.terms) ? d.terms.filter((t): t is string => typeof t === "string") : [],
    termsCustom: str(d.termsCustom),
    billTemplate: BILL_TEMPLATES.some((t) => t.id === tpl) ? (tpl as BillTemplate) : "classic",
  };
}

export function onboardingStepFrom(v: unknown): OnboardingStep {
  return typeof v === "string" && (ONBOARDING_STEPS as string[]).includes(v) ? (v as OnboardingStep) : "";
}

// ── input clean-up (what the editors store) ──

export const cleanName = (s: string) => s.trim().replace(/\s+/g, " ").slice(0, 40);
export const cleanPhone = (s: string) => s.replace(/\D/g, "").slice(-10);
export const cleanAddress = (s: string) => s.replace(/\s+/g, " ").trim().slice(0, 120);
export const cleanGstin = (s: string) => s.toUpperCase().replace(/[^0-9A-Z]/g, "").slice(0, 15);
export const cleanUpi = (s: string) => s.trim().replace(/\s+/g, "").slice(0, 60);
export const cleanTermsCustom = (s: string) => s.replace(/\s+/g, " ").trim().slice(0, 80);

// ── validation (§8) ──

export const NAME_MIN = 2;
export const isNameOk = (s: string) => s.trim().length >= NAME_MIN;

const GSTIN_RE = /^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z][0-9A-Z]Z[0-9A-Z]$/;
const UPI_RE = /^[A-Za-z0-9._-]{2,}@[A-Za-z]{2,}$/;

export const isGstinValid = (s: string) => GSTIN_RE.test(s);
export const isUpiValid = (s: string) => UPI_RE.test(s);
export const isPhoneValid = (s: string) => /^\d{10}$/.test(s);

export type Check = { ok: boolean; message: string } | null;

/** Live message under the GSTIN input; null while empty. Format-only check. */
export function gstinCheck(s: string): Check {
  if (s === "") return null;
  if (s.length !== 15) return { ok: false, message: `A GSTIN has 15 characters — you typed ${s.length}` };
  if (!isGstinValid(s)) return { ok: false, message: "This does not look like a GSTIN — please check it" };
  return { ok: true, message: "Looks right — prints under your laundry name" };
}

export function upiCheck(s: string): Check {
  if (s === "") return null;
  if (!isUpiValid(s)) return { ok: false, message: "A UPI ID looks like name@bank — please check it" };
  return { ok: true, message: "A pay QR will print on every bill" };
}

/** "9876543210" → "+91 98765 43210". */
export function fmtBillPhone(p: string): string {
  return p.length === 10 ? `+91 ${p.slice(0, 5)} ${p.slice(5)}` : p ? `+91 ${p}` : "";
}

/** Terms printed at the bottom of a bill, in preset order, then the custom line. */
export function termLines(d: Pick<BillDetails, "terms" | "termsCustom">): string[] {
  const out = TERM_PRESETS.filter((t) => d.terms.includes(t.id)).map((t) => t.text);
  if (d.termsCustom.trim()) out.push(d.termsCustom.trim());
  return out;
}

/** How many optional fields the owner has filled (the "+ Add fields" badge). */
export function addedFieldsCount(d: BillDetails, loginPhone: string): number {
  let n = 0;
  if (isUpiValid(d.upiId)) n++;
  if (d.logoId) n++;
  if (d.address) n++;
  if (termLines(d).length) n++;
  if (isGstinValid(d.gstin)) n++;
  if (d.billPhone && d.billPhone !== loginPhone) n++;
  return n;
}

/** upi://pay link the bill's QR encodes. */
export function upiPayload(upiId: string, shopName: string, amount: number, billNo: number): string {
  const q = [
    `pa=${encodeURIComponent(upiId)}`,
    `pn=${encodeURIComponent(shopName)}`,
    `am=${amount}`,
    "cu=INR",
    `tn=${encodeURIComponent(`Bill ${billNo}`)}`,
  ];
  return `upi://pay?${q.join("&")}`;
}

/** Public URL of an uploaded logo (served through the site's /api proxy). */
export function logoUrl(logoId: string): string {
  return `/api/laundry/public/logos/${encodeURIComponent(logoId)}`;
}

// ── the sample order for the "Your bill" preview (§9) ──

function priced(s: Service) {
  return s.items.filter((it) => it.price != null && it.price > 0);
}

function pieceLine(s: Service, name: string, qty: number): OrderLine | null {
  const it = priced(s).find((i) => i.name.toLowerCase() === name.toLowerCase());
  if (!it || it.price == null) return null;
  return { serviceId: s.id, serviceName: s.name, itemName: it.name, qty, kg: 0, price: it.price, base: it.price, amt: it.price * qty };
}

/** A believable order built from the owner's own rates. */
export function sampleOrder(services: Service[], expressPct: number, nextOrderNo: number): Order {
  const svcs = services.filter((s) => s.mode === "PIECE" && priced(s).length > 0);
  const wi = svcs.find((s) => /wash\s*&?\s*iron/i.test(s.name)) ?? svcs[0];
  const dc = svcs.find((s) => /dry\s*clean/i.test(s.name)) ?? svcs.find((s) => s !== wi);
  const lines: OrderLine[] = [];
  if (wi) {
    const shirt = pieceLine(wi, "Shirt", 3);
    const pant = pieceLine(wi, "Pant", 2);
    if (shirt) lines.push(shirt);
    if (pant) lines.push(pant);
    // Fall back to the first priced items when Shirt/Pant aren't on the list.
    if (lines.length === 0) {
      for (const [i, it] of priced(wi).slice(0, 2).entries()) {
        const l = pieceLine(wi, it.name, i === 0 ? 3 : 2);
        if (l) lines.push(l);
      }
    }
  }
  if (dc) {
    const saree = pieceLine(dc, "Saree", 1) ?? (priced(dc)[0] ? pieceLine(dc, priced(dc)[0].name, 1) : null);
    if (saree) lines.push(saree);
  }
  if (lines.length < 2) {
    const kgSvc = services.find((s) => s.mode === "WEIGHT" && (s.ratePerKg ?? 0) > 0);
    if (kgSvc && kgSvc.ratePerKg != null) {
      const rate = kgSvc.ratePerKg;
      lines.push({ serviceId: kgSvc.id, serviceName: kgSvc.name, itemName: "By weight", qty: 0, kg: 4, price: rate, base: rate, amt: rate * 4 });
    }
  }
  const subtotal = lines.reduce((a, l) => a + l.amt, 0);
  const exAmt = Math.round((subtotal * expressPct) / 100);
  const today = AppDate.today();
  return {
    id: nextOrderNo,
    custId: "",
    pickup: "SHOP", delivery: "SHOP",
    pickupDate: today, pickupTime: "", deliveryDate: AppDate.add(today, 1), deliveryTime: "6 PM", ddAuto: false,
    status: "RECEIVED", cancelReason: "", fee: 0,
    express: exAmt > 0, exAmt,
    discount: subtotal >= 100 ? 20 : 0,
    pre: 0, paid: 0, doneAt: "", doneDate: "", createdOn: today, billSent: false, serialNo: "", exPct: expressPct, discPct: 0, pieces: 0,
    lines,
  };
}
