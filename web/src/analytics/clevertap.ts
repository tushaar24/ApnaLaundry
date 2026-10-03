"use client";

/**
 * CleverTap Web SDK loader + thin core. Mirrors the courses frontend's setup
 * (queue object + region + CDN script), but loaded lazily from this module so
 * it is a NO-OP until NEXT_PUBLIC_CLEVERTAP_ACCOUNT_ID is set — nothing breaks
 * before the account id is provided.
 *
 * The typed event helpers live in events.ts; this file only does init,
 * identity (onUserLogin) and raw event/charged pushes.
 */

const ACCOUNT_ID = process.env.NEXT_PUBLIC_CLEVERTAP_ACCOUNT_ID ?? "";
const REGION = (process.env.NEXT_PUBLIC_CLEVERTAP_REGION ?? "").trim().toLowerCase();
// "global" (or unset) → no region prefix, default data centre.
const IS_GLOBAL = REGION === "" || REGION === "global";

interface CleverTapQueue {
  event: unknown[];
  profile: unknown[];
  account: unknown[];
  onUserLogin: unknown[];
  notifications: unknown[];
  privacy: unknown[];
  region?: string;
  [k: string]: unknown;
}

declare global {
  interface Window {
    clevertap?: CleverTapQueue;
  }
}

let initialized = false;

/** True when an account id is configured; otherwise every call below no-ops. */
export function analyticsEnabled(): boolean {
  return ACCOUNT_ID !== "";
}

/** Inject the CleverTap Web SDK once (client-side). Safe to call repeatedly. */
export function initAnalytics(): void {
  if (initialized || typeof window === "undefined" || !analyticsEnabled()) return;
  initialized = true;

  const q: CleverTapQueue = window.clevertap ?? {
    event: [], profile: [], account: [], onUserLogin: [], notifications: [], privacy: [],
  };
  q.account.push({ id: ACCOUNT_ID });
  if (!IS_GLOBAL) q.region = REGION;
  q.privacy.push({ optOut: false });
  q.privacy.push({ useIP: false });
  window.clevertap = q;

  const script = document.createElement("script");
  script.type = "text/javascript";
  script.async = true;
  // Region-specific CDN (eu1 → …bby2u); global/other → the default CDN.
  const host = REGION.startsWith("eu")
    ? "https://d2r1yp2w7bby2u.cloudfront.net"
    : "https://d2r1yp2w7bby2p.cloudfront.net";
  script.src = `${host}/js/clevertap.min.js`;
  document.head.appendChild(script);
}

function queue(): CleverTapQueue | null {
  if (typeof window === "undefined" || !analyticsEnabled()) return null;
  if (!initialized) initAnalytics();
  return window.clevertap ?? null;
}

/** Raw event push. Prefer the typed helpers in events.ts. */
export function rawTrack(name: string, props?: Record<string, unknown>): void {
  const q = queue();
  if (!q) return;
  // Stamp platform so web/android funnels stay separable without manual props.
  q.event.push(name, { platform: "web", ...(props ?? {}) });
}

/** Identify the shop owner so events attribute across sessions. */
export function identify(profile: { identity: string; phone?: string; name?: string }): void {
  const q = queue();
  if (!q) return;
  const site: Record<string, unknown> = { Identity: profile.identity };
  if (profile.phone) site.Phone = profile.phone.startsWith("+") ? profile.phone : `+91${profile.phone}`;
  if (profile.name) site.Name = profile.name;
  q.onUserLogin.push({ Site: site });
}

/** Update profile fields (e.g. shop name changed) without a fresh login. */
export function updateProfile(fields: Record<string, unknown>): void {
  const q = queue();
  if (!q) return;
  q.profile.push({ Site: fields });
}

/** CleverTap reserved revenue event. */
export function rawCharged(amount: number, props: Record<string, unknown>, items?: Record<string, unknown>[]): void {
  const q = queue();
  if (!q) return;
  q.event.push("Charged", {
    platform: "web",
    Amount: amount,
    ...props,
    ...(items ? { Items: items } : {}),
  });
}
