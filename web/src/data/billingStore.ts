"use client";

import { create } from "zustand";
import { getBillingStatus, type BillingStatus } from "./billing";
import { prefs } from "./prefs";

/**
 * Billing/paywall state. Holds the latest BillingStatus from the backend and
 * refreshes it on load and on the screens that show plan state. Every good
 * status also updates the local "subscription active" cache (prefs), which is
 * the only thing that can let the owner in while billing is unreachable.
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
        if (s.hasActiveSubscription) prefs.markSubActive();
        else prefs.clearSubActive();
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
  blocked: boolean; // status known and no active subscription → hard paywall
}

export function paywallInfo(status: BillingStatus | null): PaywallInfo {
  // No status: undecided here. The Gate never lets a failed check through
  // (only a recent cached "active" result can, see prefs.subActiveCached).
  if (!status) return { ready: false, hasActive: false, blocked: false };
  const hasActive = status.hasActiveSubscription;
  // Only an active subscription gets in: not "unconfigured", not "not due".
  return { ready: status.configured, hasActive, blocked: !hasActive };
}
