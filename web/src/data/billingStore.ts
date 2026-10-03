"use client";

import { create } from "zustand";
import { getBillingStatus, type BillingStatus } from "./billing";
import { Analytics } from "@/analytics/events";

/**
 * Billing/paywall state for the A/B free-orders flow. Holds the latest
 * BillingStatus from the backend (variant, order count, paywall due, active
 * subscription) and refreshes it on load, after taking orders, and on focus.
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
      if (s.variant) Analytics.paywallVariant(s.variant);
    } catch {
      set({ loaded: true });
    }
  },
}));

/** Derived paywall helpers for the UI. */
export interface PaywallInfo {
  ready: boolean;
  hasActive: boolean;
  variant: string;
  isTrial: boolean; // trial_2
  orderCount: number;
  freeThreshold: number;
  freeLeft: number; // for free_<N>
  blocked: boolean; // free orders used up (or trial not taken) → new order opens paywall
  showBanner: boolean; // home banner (≤10 free orders left, or trial upsell)
}

export function paywallInfo(status: BillingStatus | null): PaywallInfo {
  if (!status || !status.configured) {
    // If the A/B variant can't be determined, default to the trial paywall.
    const variant = status?.variant || "trial_2";
    return {
      ready: false, hasActive: false, variant, isTrial: variant === "trial_2",
      orderCount: status?.orderCount ?? 0, freeThreshold: status?.freeOrderThreshold ?? 0,
      freeLeft: 0, blocked: false, showBanner: false,
    };
  }
  const isTrial = status.variant === "trial_2";
  const freeLeft = Math.max(0, status.freeOrderThreshold - status.orderCount);
  const hasActive = status.hasActiveSubscription;
  const blocked = !hasActive && status.paywallDue;
  // Banner is a free_<N>-only nudge (≤10 left / finished). The trial variant
  // never needs one — it's hard-gated before home is reachable.
  const showBanner = !hasActive && !isTrial && freeLeft <= 10;
  return {
    ready: true,
    hasActive,
    variant: status.variant,
    isTrial,
    orderCount: status.orderCount,
    freeThreshold: status.freeOrderThreshold,
    freeLeft,
    blocked,
    showBanner,
  };
}

/** The reason to open the paywall with, from the current paywall state. */
export function paywallReason(info: PaywallInfo): "limit" | "trial" | "upsell" {
  if (info.isTrial) return "trial";
  return info.blocked ? "limit" : "upsell";
}
