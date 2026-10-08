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
  /** The last refresh failed even after retrying (status keeps its last good value). */
  failed: boolean;
  /** Fetch status, retrying a failed call `retries` more times with backoff. */
  refresh(retries?: number): Promise<void>;
}

const RETRY_DELAYS_MS = [1000, 2000, 4000];
const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms));

export const useBillingStore = create<BillingStore>((set) => ({
  status: null,
  loaded: false,
  failed: false,
  async refresh(retries = 2) {
    // Before the first good status, go back to "not loaded" so the Gate waits
    // (shows its loader) instead of deciding while this attempt is in flight.
    set((st) => (st.status ? {} : { loaded: false, failed: false }));
    for (let attempt = 0; ; attempt++) {
      try {
        const s = await getBillingStatus();
        set({ status: s, loaded: true, failed: false });
        return;
      } catch {
        if (attempt >= retries) {
          set({ loaded: true, failed: true });
          return;
        }
        await sleep(RETRY_DELAYS_MS[Math.min(attempt, RETRY_DELAYS_MS.length - 1)]);
      }
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
  // No status, or billing unconfigured server-side: never gate here. The Gate
  // separately refuses to let a failed status check through (see gate.tsx).
  if (!status || !status.configured) return { ready: false, hasActive: false, blocked: false };
  const hasActive = status.hasActiveSubscription;
  return { ready: true, hasActive, blocked: !hasActive && status.paywallDue };
}
