import type { GstMode } from "./gst";

/** Pure domain models. Port of domain/Models.kt — same enums and shapes. */

export type PricingMode = "PIECE" | "WEIGHT";

export type OrderStatus = "CREATED" | "RECEIVED" | "READY" | "DELIVERED" | "CANCELLED";

export type Route = "SHOP" | "HOME";

export type LedgerKind = "BILL" | "OLD" | "ADJ" | "GOT";

/** Payment allocation tag on a `GOT` ledger entry. */
export type PayTag = "PRE" | "DELIVER" | "RECEIVE" | "NONE";

export type PayMethod = "CASH" | "UPI" | "NONE";

export interface ServiceItem {
  name: string;
  price: number | null; // null / blank = not offered by this service
}

export interface Service {
  id: string;
  name: string;
  mode: PricingMode;
  ratePerKg: number | null; // for WEIGHT
  minKg: number | null; // for WEIGHT
  readyInDays: number | null; // null = no ready time
  lockedToPiece: boolean;
  items: ServiceItem[];
  sortOrder: number;
}

export interface Customer {
  id: string;
  name: string;
  phone: string;
  address: string;
  pastOrders: number; // orders from the notebook, before the app
  lastLabel: string; // "Today", "Yesterday", "23 Sep", "—"
  agoRank: number; // for sorting recents (0 = most recent)
}

export interface OrderLine {
  serviceId: string; // service id, or "quick" for a lump bill
  serviceName: string; // snapshot so deleting a service never rewrites history
  itemName: string; // item name, or "By weight" / "Clothes (not itemised)"
  qty: number;
  price: number; // per-piece price used on this order
  base: number; // rate-card price at the time (for the "rate ₹25" note)
  kg: number; // >0 for weight lines
  amt: number;
  isQuick?: boolean;
}

export interface Order {
  id: number;
  custId: string;
  pickup: Route;
  delivery: Route;
  pickupDate: string;
  pickupTime: string;
  deliveryDate: string; // "" = no date
  deliveryTime: string;
  ddAuto: boolean; // delivery date was auto-filled from ready time
  status: OrderStatus;
  cancelReason: string;
  fee: number; // pickup / delivery charge
  express: boolean;
  exAmt: number;
  discount: number;
  pre: number; // prepaid before delivery
  paid: number; // total paid toward this bill
  doneAt: string; // time string when delivered
  doneDate: string; // iso date delivered
  createdOn: string;
  billSent: boolean;
  serialNo: string; // owner-set bill / serial number ("" = use the order id)
  exPct: number; // express as % of the clothes (0 = exAmt is a fixed ₹ amount)
  discPct: number; // discount as % of the clothes (0 = discount is a fixed ₹ amount)
  pieces: number; // optional piece count for quick bills
  // GST on this order (see domain/gst). Orders from before GST have gstOn = false.
  gstOn: boolean;
  gstPct: number; // 18, 5, 12 … may be fractional
  gstMode: GstMode; // "excl" = added on top of the price, "incl" = already in the price
  note: string; // owner's free-text note ("starch the shirts"); "" = none
  lines: OrderLine[];
}

export interface LedgerEntry {
  id: string;
  custId: string;
  date: string;
  time: string;
  ts: number; // stable ordering within a day
  kind: LedgerKind;
  amt: number; // adj can be negative
  method: PayMethod;
  tag: PayTag;
  cover: number; // portion that paid this bill
  toOld: number; // portion that cleared old baaki
  toAdv: number; // portion kept as advance
  ref: number | null; // order id
  note: string;
}

export type BillTemplate = "classic" | "bold" | "receipt";

/** Onboarding progress ("" = a shop from before onboarding was tracked). */
export type OnboardingStep = "" | "intro" | "name" | "services" | "bill" | "done";

/** Optional details printed on bills, plus the chosen bill design. */
export interface BillDetails {
  billPhone: string; // 10 digits ("" = none)
  address: string;
  gstin: string; // printed only when valid
  email: string; // printed only when valid
  upiId: string; // QR printed only when valid
  logoId: string; // uploaded logo ("" = none)
  terms: string[]; // preset ids, see domain/billDetails TERM_PRESETS
  termsCustom: string;
  billTemplate: BillTemplate;
}

export interface Shop extends BillDetails {
  name: string;
  phone: string;
  expressPct: number;
  onboardingStep: OnboardingStep;
  // The last-used GST setting: pre-fills every new order (every change on a new order saves it back).
  gstOn: boolean;
  gstPct: number;
  gstMode: GstMode;
}

export interface DayClose {
  date: string;
  closedAt: number;
  cashCounted: number | null;
}

/** Everything the domain layer needs to compute derived values. */
export interface LaundryState {
  shop: Shop;
  services: Service[];
  customers: Customer[];
  orders: Order[];
  ledger: LedgerEntry[];
  closedDays: Set<string>;
}
