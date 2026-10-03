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
  /** Razorpay subscription id (sub_…) — passed to Razorpay Checkout. */
  subscriptionId: string;
  /** Public Razorpay key id for Checkout. */
  keyId: string;
  /** Hosted authorization link (fallback; Checkout is the primary path). */
  shortUrl?: string | null;
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
  if (!res.ok) throw new Error(`Billing status failed (${res.status})`);
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

// ─────────────────────── Razorpay Standard Checkout ───────────────────────
// Razorpay owns the UPI AutoPay approval UI (its own app picker on mobile, QR on
// desktop). We create the subscription server-side, then open Checkout with the
// subscription id. The authoritative confirmation is the server webhook, so on
// success we poll getBillingStatus() for `hasActiveSubscription`.

const CHECKOUT_SRC = "https://checkout.razorpay.com/v1/checkout.js";
let checkoutLoading: Promise<void> | null = null;

function loadCheckout(): Promise<void> {
  if (typeof window === "undefined") return Promise.reject(new Error("No window"));
  if ((window as unknown as { Razorpay?: unknown }).Razorpay) return Promise.resolve();
  if (checkoutLoading) return checkoutLoading;
  checkoutLoading = new Promise((resolve, reject) => {
    const s = document.createElement("script");
    s.src = CHECKOUT_SRC;
    s.async = true;
    s.onload = () => resolve();
    s.onerror = () => { checkoutLoading = null; reject(new Error("Could not load Razorpay")); };
    document.head.appendChild(s);
  });
  return checkoutLoading;
}

export interface CheckoutCallbacks {
  name?: string;
  email?: string;
  contact?: string;
  onSuccess: () => void;
  onDismiss: () => void;
  onError?: (message: string) => void;
}

/**
 * Open Razorpay Checkout to authorize the subscription's UPI AutoPay mandate.
 * `onSuccess` fires when Razorpay confirms the authorization (then poll status);
 * `onDismiss` when the user closes without paying.
 */
export async function openSubscriptionCheckout(result: SubscribeResult, cb: CheckoutCallbacks): Promise<void> {
  await loadCheckout();
  const RazorpayCtor = (window as unknown as { Razorpay: new (opts: Record<string, unknown>) => { open: () => void; on: (e: string, h: (r: unknown) => void) => void } }).Razorpay;
  const description = result.trialAmount > 0
    ? "Start ₹2 trial · UPI AutoPay"
    : result.plan === "annual" ? "Yearly plan · UPI AutoPay" : "Monthly plan · UPI AutoPay";
  const rzp = new RazorpayCtor({
    key: result.keyId,
    subscription_id: result.subscriptionId,
    name: "ApnaLaundry",
    description,
    prefill: { name: cb.name, email: cb.email, contact: cb.contact },
    theme: { color: "#1d4ed8" },
    handler: () => cb.onSuccess(),
    modal: { ondismiss: () => cb.onDismiss() },
  });
  rzp.on("payment.failed", (resp: unknown) => {
    const err = (resp as { error?: { description?: string } })?.error;
    if (cb.onError) cb.onError(err?.description || "Payment failed — please try again");
  });
  rzp.open();
}
