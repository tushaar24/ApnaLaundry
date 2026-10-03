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
    return {
      ready: false, hasActive: false, variant: status?.variant ?? "free_50", isTrial: false,
      orderCount: status?.orderCount ?? 0, freeThreshold: status?.freeOrderThreshold ?? 0,
      freeLeft: 0, blocked: false, showBanner: false,
    };
  }
  const isTrial = status.variant === "trial_2";
  const freeLeft = Math.max(0, status.freeOrderThreshold - status.orderCount);
  const hasActive = status.hasActiveSubscription;
  const blocked = !hasActive && status.paywallDue;
  const showBanner = !hasActive && (isTrial ? !hasActive : freeLeft <= 10);
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
