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
        if (hasAccess(s)) prefs.markSubActive();
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
  // Only an active subscription, or a cancelled one still in the period it
  // paid for, gets in: not "unconfigured".
  return { ready: status.configured, hasActive, blocked: !hasAccess(status) };
}

/** A cancelled plan still inside the period it already paid for. */
export function inCancelGrace(status: BillingStatus | null): boolean {
  return !!status && status.subscription?.status === "cancelled" && !status.paywallDue;
}

/**
 * The day a cancelled plan stops working (ISO): a ₹2 trial cancelled before its
 * first real charge runs to that charge date; a paid plan to its period end.
 */
export function accessUntilIso(status: BillingStatus | null): string | null {
  const sub = status?.subscription;
  if (!sub) return null;
  const trialOnly = (sub.trialAmount || 0) > 0 && (sub.paidCount || 0) === 0;
  return (trialOnly ? sub.chargeAt ?? sub.currentEnd : sub.currentEnd ?? sub.chargeAt) ?? null;
}

/**
 * May use the app: an active plan, or a cancelled one the server says is still
 * inside the period it already paid for (paywallDue false). Only a cancelled
 * plan gets the grace, so "billing unconfigured" never lets anyone in.
 */
export function hasAccess(status: BillingStatus): boolean {
  if (status.hasActiveSubscription) return true;
  return status.subscription?.status === "cancelled" && !status.paywallDue;
}
