"use client";

import { create } from "zustand";
import type { LaundryState, Shop } from "@/domain/models";
import { emptyBillDetails } from "@/domain/billDetails";
import { gstDefaults } from "@/domain/gst";
import { emptyRows, type Rows } from "./rows";
import { prefs } from "./prefs";

/**
 * The single source of shared app state (web port of ShopViewModel +
 * the Room-backed reactive LaundryState): in-memory rows mirrored against
 * the backend by the sync engine, a global toast with Undo, and flags.
 */

export interface ToastState {
  text: string;
  hasUndo: boolean;
  /** A wa.me link — the toast shows a WhatsApp button that opens it. */
  wa?: string;
}

export interface Snapshot {
  orders: Rows["orders"];
  ledger: Rows["ledger"];
  customers: Rows["customers"];
  services: Rows["services"];
  shop: Rows["shop"];
}

interface AppStore {
  rows: Rows;
  /** Bootstrap finished (initial pull done or failed). */
  hydrated: boolean;
  authed: boolean;
  setupDone: boolean;
  hideAmounts: boolean;
  /** Online-only: the initial pull failed and there is no data to show. */
  bootError: string | null;
  syncing: boolean;
  toast: ToastState | null;

  setRows(mut: (r: Rows) => Rows): void;
  setHydrated(v: boolean): void;
  setAuthed(v: boolean): void;
  setSetupDone(v: boolean): void;
  setBootError(v: string | null): void;
  setSyncing(v: boolean): void;
  toggleHideAmounts(): void;
  showToast(text: string, hasUndo: boolean, wa?: string): void;
  dismissToast(): void;
}

let toastTimer: ReturnType<typeof setTimeout> | null = null;

export const useAppStore = create<AppStore>((set, get) => ({
  rows: emptyRows,
  hydrated: false,
  authed: false,
  setupDone: false,
  hideAmounts: false,
  bootError: null,
  syncing: false,
  toast: null,

  setRows: (mut) => set((s) => ({ rows: mut(s.rows) })),
  setHydrated: (v) => set({ hydrated: v }),
  setAuthed: (v) => {
    prefs.setLoggedIn(v);
    set({ authed: v });
  },
  setSetupDone: (v) => {
    prefs.setSetupDone(v);
    set({ setupDone: v });
  },
  setBootError: (v) => set({ bootError: v }),
  setSyncing: (v) => set({ syncing: v }),
  toggleHideAmounts: () => {
    const next = !get().hideAmounts;
    prefs.setHideAmounts(next);
    set({ hideAmounts: next });
  },
  showToast: (text, hasUndo, wa) => {
    if (toastTimer) clearTimeout(toastTimer);
    set({ toast: { text, hasUndo, wa } });
    // Toasts with Undo / WhatsApp stay long enough to act on; plain notices are brief.
    toastTimer = setTimeout(() => set({ toast: null }), hasUndo || wa ? 7000 : 2500);
  },
  dismissToast: () => {
    if (toastTimer) clearTimeout(toastTimer);
    set({ toast: null });
  },
}));

// ---------------- derived LaundryState ----------------

const FALLBACK_SHOP: Shop = { name: "MyLaundry", phone: "", expressPct: 50, onboardingStep: "", ...emptyBillDetails(), ...gstDefaults() };

let lastRows: Rows | null = null;
let lastDerived: LaundryState | null = null;

function shopOf(r: NonNullable<Rows["shop"]>): Shop {
  return {
    name: r.name, phone: r.phone, expressPct: r.expressPct, onboardingStep: r.onboardingStep,
    billPhone: r.billPhone, address: r.address, gstin: r.gstin, email: r.email, upiId: r.upiId, logoId: r.logoId,
    terms: r.terms, termsCustom: r.termsCustom, billTemplate: r.billTemplate,
    gstOn: r.gstOn, gstPct: r.gstPct, gstMode: r.gstMode,
  };
}

/** Rows -> domain LaundryState (tombstones dropped, services sorted). Memoized on rows identity. */
export function deriveState(rows: Rows): LaundryState {
  if (rows === lastRows && lastDerived) return lastDerived;
  const state: LaundryState = {
    shop: rows.shop
      ? shopOf(rows.shop)
      : FALLBACK_SHOP,
    services: rows.services.filter((s) => !s.deleted).slice().sort((a, b) => a.sortOrder - b.sortOrder),
    customers: rows.customers.filter((c) => !c.deleted),
    orders: rows.orders.filter((o) => !o.deleted),
    ledger: rows.ledger.filter((l) => !l.deleted).slice().sort((a, b) => a.ts - b.ts),
    closedDays: new Set(rows.dayCloses.filter((d) => !d.deleted).map((d) => d.date)),
  };
  lastRows = rows;
  lastDerived = state;
  return state;
}

/** Hook: the derived domain state every screen reads. */
export function useLaundryState(): LaundryState {
  const rows = useAppStore((s) => s.rows);
  return deriveState(rows);
}
