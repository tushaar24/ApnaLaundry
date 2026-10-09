"use client";

import { prefs } from "@/data/prefs";
import { authedFetch } from "@/data/syncApi";

/**
 * Public bill links (mylaundry.work/b/<token>) for "Send on WhatsApp".
 * WhatsApp must open synchronously inside the tap (or it's popup-blocked), so
 * screens with a send button prefetch the order's link on mount and the tap
 * reads it from this cache. Keyed by account too — order ids repeat across
 * shops, and a browser can log into a different shop.
 */

const tokens = new Map<string, string>();
const inflight = new Map<string, Promise<string | null>>();

const key = (orderId: number | "sample") => `${prefs.userPhone ?? ""}:${orderId}`;

const urlFor = (token: string) => `${window.location.origin}/b/${token}`;

/** Fetches (once) and caches the order's bill link. Null when offline/failed. */
export function prefetchBillLink(orderId: number): Promise<string | null> {
  return fetchLink(key(orderId), `/api/laundry/bills/${orderId}/link`);
}

/** The shop's sample bill link (onboarding "Test on WhatsApp"). */
export function prefetchSampleLink(): Promise<string | null> {
  return fetchLink(key("sample"), "/api/laundry/bills/sample/link");
}

export function cachedSampleLink(): string | null {
  const t = tokens.get(key("sample"));
  return t ? urlFor(t) : null;
}

function fetchLink(k: string, path: string): Promise<string | null> {
  const have = tokens.get(k);
  if (have) return Promise.resolve(urlFor(have));
  const pending = inflight.get(k);
  if (pending) return pending;
  const p = authedFetch(path, { method: "POST" })
    .then((r) => r.json() as Promise<{ success?: boolean; token?: string }>)
    .then((b) => {
      if (!b.success || !b.token) return null;
      tokens.set(k, b.token);
      return urlFor(b.token);
    })
    .catch(() => null)
    .finally(() => inflight.delete(k));
  inflight.set(k, p);
  return p;
}

/** The cached link, if the prefetch has already landed. */
export function cachedBillLink(orderId: number): string | null {
  const t = tokens.get(key(orderId));
  return t ? urlFor(t) : null;
}
