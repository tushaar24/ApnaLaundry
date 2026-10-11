import { prefs } from "./prefs";

/**
 * A/B test (2026-10-11): which plan the paywall pre-selects — Yearly (the old
 * default) or Monthly. 50/50, stable per shop: the bucket is a hash of the
 * user id, so the same shop sees the same default on every visit and device.
 * Only the pre-selected card changes; card order, copy and prices don't.
 *
 * Analyse in CleverTap by the `default_plan` event property (Paywall Shown,
 * Checkout Started / Succeeded / Failed) or the `Default_Plan_Selected` profile
 * property (also splits the server-side Subscription Activated).
 */

export type DefaultPlan = "annual" | "monthly";

// Changing this re-buckets everyone — bump it only to start a fresh test.
const EXPERIMENT = "default_plan_v1";

/** FNV-1a 32-bit — tiny, deterministic, evenly spread over ids. */
function fnv1a(s: string): number {
  let h = 0x811c9dc5;
  for (let i = 0; i < s.length; i++) {
    h ^= s.charCodeAt(i);
    h = Math.imul(h, 0x01000193);
  }
  return h >>> 0;
}

export function defaultPlanVariant(): DefaultPlan {
  // The paywall only shows to logged-in users, so the user id is there; the
  // device id is just a stable fallback.
  const id = prefs.userId ?? prefs.deviceId();
  return fnv1a(`${EXPERIMENT}:${id}`) % 2 === 0 ? "annual" : "monthly";
}
