"use client";

import { tokenManager } from "./tokenManager";

/**
 * Billing API client (web) for the A/B paywall + Razorpay UPI AutoPay
 * subscription flow. Talks to /api/laundry/billing/* (proxied to the backend).
 * The paywall UI calls getStatus() to decide what to show, then subscribe()
 * + openSubscriptionCheckout() to take the UPI AutoPay mandate.
 */

// "trial_2" or "free_<N>" (N = free orders allowed, an A/B lever).
export type PaywallVariant = string;

export interface BillingStatus {
  configured: boolean;
  variant: PaywallVariant;
  orderCount: number;
  freeOrderThreshold: number;
  paywallDue: boolean;
  hasActiveSubscription: boolean;
  plans: {
    monthly: { amount: number };
    annual: { amount: number };
    trial: { amount: number };
  };
  subscription: {
    id: string;
    plan: string;
    variant: string | null;
    status: string;
    amount: number;
    trialAmount: number;
    paidCount: number;
    currentEnd: string | null;
    chargeAt: string | null;
  } | null;
}

export interface SubscribeResult {
  subscriptionId: string;
  /**
   * UPI AutoPay authorization link from the backend. Open it on the device to
   * pick a UPI app, or render it as a QR code to scan.
   */
  intentUrl: string;
  plan: string;
  variant: PaywallVariant;
  amount: number;
  trialAmount: number;
  reused?: boolean;
}

async function authedFetch(path: string, init?: RequestInit): Promise<Response> {
  let token = await tokenManager.validAccessToken();
  if (!token) throw new Error("Not logged in");
  let res = await fetch(path, { ...init, headers: { ...(init?.headers ?? {}), Authorization: `Bearer ${token}` } });
  if (res.status === 401) {
    token = await tokenManager.forceRefresh();
    if (!token) throw new Error("Not logged in");
    res = await fetch(path, { ...init, headers: { ...(init?.headers ?? {}), Authorization: `Bearer ${token}` } });
  }
  return res;
}

export async function getBillingStatus(): Promise<BillingStatus> {
  const res = await authedFetch("/api/laundry/billing/status");
  return (await res.json()) as BillingStatus;
}

export async function subscribe(plan: "monthly" | "annual"): Promise<SubscribeResult> {
  const res = await authedFetch("/api/laundry/billing/subscribe", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ plan }),
  });
  const body = await res.json();
  if (!res.ok || !body.success) throw new Error(body.message || "Could not start subscription");
  return body as SubscribeResult;
}

export async function cancelSubscription(): Promise<void> {
  const res = await authedFetch("/api/laundry/billing/cancel", { method: "POST" });
  const body = await res.json();
  if (!res.ok || !body.success) throw new Error(body.message || "Could not cancel");
}

/**
 * Open the backend-provided UPI AutoPay intent to approve the mandate — no
 * Razorpay SDK. On desktop, render `intentUrl` as a QR code instead (scan with
 * a phone's UPI app). The authoritative confirmation comes from the server
 * webhook, so after the user returns, poll getBillingStatus() for
 * `hasActiveSubscription`.
 */
export function openIntent(intentUrl: string): void {
  if (typeof window === "undefined" || !intentUrl) return;
  // upi:// intents must navigate the current tab (a UPI app handles them);
  // https authorization pages open in a new tab.
  if (intentUrl.startsWith("upi:")) {
    window.location.href = intentUrl;
  } else {
    window.open(intentUrl, "_blank", "noopener");
  }
}
