"use client";

import { identify, rawCharged, rawTrack, updateProfile } from "./clevertap";

/**
 * Typed CleverTap event wrapper. One method per event in the shared tracking
 * plan (docs/analytics-events.md) — identical names + property keys to the
 * Android `Analytics` object, so funnels line up across platforms.
 */

export type ScreenName =
  | "login" | "setup" | "home" | "customers" | "earnings" | "settings"
  | "new_order" | "order_detail" | "customer_khata" | "bill" | "rates";

export type PayMethodProp = "cash" | "upi" | "none";

function method(m: string): PayMethodProp {
  return m === "CASH" ? "cash" : m === "UPI" ? "upi" : "none";
}

export const Analytics = {
  identify,
  updateProfile,

  screen(screen: ScreenName) {
    rawTrack("Screen Viewed", { screen });
  },

  // ---- auth ----
  otpRequested(phoneType: "real" | "review", isResend: boolean) {
    rawTrack("OTP Requested", { phone_type: phoneType, is_resend: isResend });
  },
  otpRequestFailed(reason: string) {
    rawTrack("OTP Request Failed", { reason });
  },
  otpSubmitted() {
    rawTrack("OTP Submitted");
  },
  otpVerificationFailed(reason: string, attemptsRemaining?: number) {
    rawTrack("OTP Verification Failed", {
      reason,
      ...(attemptsRemaining != null ? { attempts_remaining: attemptsRemaining } : {}),
    });
  },
  loggedIn(isNewUser: boolean, needsSetup: boolean) {
    rawTrack("Logged In", { is_new_user: isNewUser, needs_setup: needsSetup });
  },
  setupCompleted(servicesCount: number, expressPct: number) {
    rawTrack("Setup Completed", { services_count: servicesCount, express_pct: expressPct });
  },
  setupSkipped(servicesCount: number) {
    rawTrack("Setup Skipped", { services_count: servicesCount });
  },
  loggedOut() {
    rawTrack("Logged Out");
  },

  // ---- order creation & lifecycle ----
  newOrderStarted(source: "home" | "empty_home" | "customer", isEdit: boolean) {
    rawTrack("New Order Started", { source, is_edit: isEdit });
  },
  orderSaved(p: {
    orderId: number; isEdit: boolean; hasBill: boolean; pickup: string; delivery: string;
    express: boolean; discount: number; fee: number; pieces: number; kg: number;
    servicesCount: number; total: number; quickBill: boolean;
  }) {
    rawTrack("Order Saved", {
      order_id: p.orderId, is_edit: p.isEdit, has_bill: p.hasBill, pickup: p.pickup,
      delivery: p.delivery, express: p.express, discount: p.discount, fee: p.fee,
      pieces: p.pieces, kg: p.kg, services_count: p.servicesCount, total: p.total,
      quick_bill: p.quickBill,
    });
  },
  orderPickedUp(orderId: number) {
    rawTrack("Order Picked Up", { order_id: orderId });
  },
  clothesCounted(orderId: number, nextStatus: string, total: number) {
    rawTrack("Clothes Counted", { order_id: orderId, next_status: nextStatus, total });
  },
  orderMarkedReady(orderId: number) {
    rawTrack("Order Marked Ready", { order_id: orderId });
  },
  orderDelivered(p: {
    orderId: number; total: number; amountReceived: number; method: string;
    toKhata: number; fromAdvance: number; items?: Record<string, unknown>[];
  }) {
    rawTrack("Order Delivered", {
      order_id: p.orderId, total: p.total, amount_received: p.amountReceived,
      method: method(p.method), to_khata: p.toKhata, from_advance: p.fromAdvance,
    });
    // Revenue event — the bill total is the "work done" value.
    rawCharged(p.total, { payment_method: method(p.method), order_id: p.orderId }, p.items);
  },
  orderCancelled(orderId: number, reason: string) {
    rawTrack("Order Cancelled", { order_id: orderId, reason });
  },
  orderRescheduled(orderId: number, kind: "pickup" | "delivery", notify: boolean) {
    rawTrack("Order Rescheduled", { order_id: orderId, kind, notify });
  },

  // ---- payments & khata ----
  paymentReceived(p: {
    amount: number; method: string; type: "delivery" | "prepay" | "khata";
    customerId: string; orderId?: number;
  }) {
    rawTrack("Payment Received", {
      amount: p.amount, method: method(p.method), type: p.type,
      customer_id: p.customerId, ...(p.orderId != null ? { order_id: p.orderId } : {}),
    });
  },
  oldBaakiAdded(amount: number, customerId: string) {
    rawTrack("Old Baaki Added", { amount, customer_id: customerId });
  },

  // ---- customers, bills, rates, engagement ----
  customerAdded(source: "order" | "list", withOldBaaki: boolean) {
    rawTrack("Customer Added", { source, with_old_baaki: withOldBaaki });
  },
  reminderSent(customerId: string, amount: number) {
    rawTrack("Reminder Sent", { customer_id: customerId, amount });
  },
  billSent(orderId: number) {
    rawTrack("Bill Sent", { order_id: orderId, channel: "whatsapp" });
  },
  billViewed(orderId: number) {
    rawTrack("Bill Viewed", { order_id: orderId });
  },
  summaryShared(period: string) {
    rawTrack("Summary Shared", { period });
  },
  ratesOpened(from: string) {
    rawTrack("Rates Opened", { from });
  },
  serviceAdded(mode: string) {
    rawTrack("Service Added", { mode });
  },
  serviceEdited(serviceId: string) {
    rawTrack("Service Edited", { service_id: serviceId });
  },
  serviceDeleted(serviceId: string) {
    rawTrack("Service Deleted", { service_id: serviceId });
  },
  earningsPeriodChanged(period: string) {
    rawTrack("Earnings Period Changed", { period });
  },
  orderSearchOpened() {
    rawTrack("Order Search Opened");
  },

  // ---- billing / paywall funnel (₹2 trial → monthly | annual) ----
  // The client fires the UI funnel below; the backend fires the money-confirmed
  // events (Subscription Activated / Charged / Payment Failed / Halted /
  // Cancelled) from the Razorpay webhook.
  paywallShown() {
    rawTrack("Paywall Shown");
  },
  planSelected(plan: string) {
    rawTrack("Plan Selected", { plan });
  },
  checkoutStarted(plan: string, amount: number, trialAmount: number) {
    rawTrack("Checkout Started", { plan, amount, trial_amount: trialAmount });
  },
  checkoutSucceeded(plan: string) {
    rawTrack("Checkout Succeeded", { plan });
  },
  checkoutFailed(plan: string, reason: string) {
    rawTrack("Checkout Failed", { plan, reason });
  },
  // The "try again" sheet shown when Razorpay Checkout closes unpaid.
  paymentRetryShown(plan: string, reason: "failed" | "cancelled") {
    rawTrack("Payment Retry Shown", { plan, reason });
  },
  paymentRetryTapped(plan: string) {
    rawTrack("Payment Retry Tapped", { plan });
  },
  paymentRetryDismissed(plan: string) {
    rawTrack("Payment Retry Dismissed", { plan });
  },
  subscriptionCancelRequested(plan: string) {
    rawTrack("Subscription Cancel Requested", { plan });
  },
};
