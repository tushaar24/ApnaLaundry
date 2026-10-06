"use client";

import { create } from "zustand";
import { getBillingStatus, type BillingStatus } from "./billing";

/**
 * Billing/paywall state. Holds the latest BillingStatus from the backend
 * (paywall due, active subscription) and refreshes it on load and on the
 * screens that show plan state.
 */

interface BillingStore {
  status: BillingStatus | null;
  loaded: boolean;
  refresh(): Promise<void>;
}

export const useBillingStore = create<BillingStore>((set) => ({
  status: null,
  loaded: false,
  async refresh() {
    try {
      const s = await getBillingStatus();
      set({ status: s, loaded: true });
    } catch {
      set({ loaded: true });
    }
  },
}));

/** Derived paywall helpers for the UI. */
export interface PaywallInfo {
  ready: boolean; // billing reachable and configured
  hasActive: boolean;
  blocked: boolean; // no active subscription (and not in grace) → hard paywall
}

export function paywallInfo(status: BillingStatus | null): PaywallInfo {
  // Billing unreachable or unconfigured fails open: never gate.
  if (!status || !status.configured) return { ready: false, hasActive: false, blocked: false };
  const hasActive = status.hasActiveSubscription;
  return { ready: true, hasActive, blocked: !hasActive && status.paywallDue };
}
