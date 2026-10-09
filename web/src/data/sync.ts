"use client";

import { invalidateUndo } from "./repository";

import { prefs } from "./prefs";
import { syncApi, NotLoggedInError } from "./syncApi";
import { useAppStore } from "./store";
import {
  customerFromDto, customerToDto, dayCloseFromDto, dayCloseToDto, ledgerFromDto, ledgerToDto,
  orderFromDto, orderToDto, serviceFromDto, serviceToDto, shopFromDto, shopToDto,
  type Rows, type SyncChanges,
} from "./rows";
import { refreshTsCounter } from "./ids";

/**
 * Online sync engine — port of data/sync/SyncManager.kt for a web client
 * whose source of truth is the in-memory store (re-pulled on every page
 * load). One sync = pull then push:
 *  - pull?since=<checkpoint>: apply each server row unless the local copy
 *    has a newer `updatedAt` (same LWW rule the server uses on push).
 *  - push: send every dirty row; on success clear the dirty flag, guarded
 *    by `updatedAt` so rows edited mid-flight stay dirty.
 */

export type SyncResult =
  | { kind: "success" }
  | { kind: "skipped" }
  | { kind: "error"; message: string; authDead: boolean };

let checkpoint = 0; // in-memory pull cursor; every page load starts from 0
let chain: Promise<unknown> = Promise.resolve(); // mutex

export function resetCheckpoint() {
  checkpoint = 0;
}

/** Set by applyPull: whether any pulled row was newer than ours (not just our own push echoed back). */
let pulledNewer = false;

function applyPull(rows: Rows, ch: SyncChanges): Rows {
  const next: Rows = { ...rows };

  if (ch.shop) {
    const dto = ch.shop;
    if (!next.shop || dto.updatedAt > next.shop.updatedAt) { next.shop = shopFromDto(dto); pulledNewer = true; }
  }

  function merge<R extends { updatedAt: number }, D extends { updatedAt: number }>(
    list: R[], dtos: D[] | undefined, key: (x: R | D) => string | number, from: (d: D) => R,
  ): R[] {
    if (!dtos || dtos.length === 0) return list;
    const out = list.slice();
    const index = new Map(out.map((r, i) => [key(r), i]));
    for (const d of dtos) {
      const i = index.get(key(d));
      if (i === undefined) {
        index.set(key(d), out.length);
        out.push(from(d));
        pulledNewer = true;
      } else if (d.updatedAt > out[i].updatedAt) {
        out[i] = from(d);
        pulledNewer = true;
      }
    }
    return out;
  }

  next.services = merge(next.services, ch.services, (x) => x.id, serviceFromDto);
  next.customers = merge(next.customers, ch.customers, (x) => x.id, customerFromDto);
  next.orders = merge(next.orders, ch.orders, (x) => x.id, orderFromDto);
  next.ledger = merge(next.ledger, ch.ledger, (x) => x.id, ledgerFromDto);
  next.dayCloses = merge(next.dayCloses, ch.dayCloses, (x) => x.date, dayCloseFromDto);
  return next;
}

async function pullOnce(): Promise<void> {
  const res = await syncApi.pull(checkpoint);
  if (!res.success) throw new Error(res.message ?? "Pull failed");
  const changes = res.changes ?? {};
  pulledNewer = false;
  useAppStore.getState().setRows((rows) => applyPull(rows, changes));
  // Another device's changes landed: restoring an older snapshot would delete
  // them. (Our own push echoed back isn't newer, so it keeps the Undo.)
  if (pulledNewer) invalidateUndo();
  checkpoint = res.checkpoint ?? checkpoint;
  refreshTsCounter(useAppStore.getState().rows.ledger);
}

async function pushOnce(): Promise<void> {
  const rows = useAppStore.getState().rows;
  const shop = rows.shop?.dirty ? rows.shop : null;
  const services = rows.services.filter((r) => r.dirty);
  const customers = rows.customers.filter((r) => r.dirty);
  const orders = rows.orders.filter((r) => r.dirty);
  const ledger = rows.ledger.filter((r) => r.dirty);
  const dayCloses = rows.dayCloses.filter((r) => r.dirty);

  const empty = !shop && !services.length && !customers.length && !orders.length && !ledger.length && !dayCloses.length;
  if (empty) return;

  const changes: SyncChanges = {
    shop: shop ? shopToDto(shop) : null,
    services: services.map(serviceToDto),
    customers: customers.map(customerToDto),
    orders: orders.map(orderToDto),
    ledger: ledger.map(ledgerToDto),
    dayCloses: dayCloses.map(dayCloseToDto),
  };

  const res = await syncApi.push(changes);
  if (!res.success) throw new Error(res.message ?? "Push failed");

  // Clear dirty flags, each guarded by updatedAt so anything edited while
  // the push was in flight stays dirty for the next round.
  const pushedShopAt = shop?.updatedAt;
  const at = new Map<string, number>();
  for (const r of services) at.set(`s${r.id}`, r.updatedAt);
  for (const r of customers) at.set(`c${r.id}`, r.updatedAt);
  for (const r of orders) at.set(`o${r.id}`, r.updatedAt);
  for (const r of ledger) at.set(`l${r.id}`, r.updatedAt);
  for (const r of dayCloses) at.set(`d${r.date}`, r.updatedAt);

  useAppStore.getState().setRows((cur) => ({
    shop: cur.shop && cur.shop.dirty && cur.shop.updatedAt === pushedShopAt
      ? { ...cur.shop, dirty: false }
      : cur.shop,
    services: cur.services.map((r) => (r.dirty && at.get(`s${r.id}`) === r.updatedAt ? { ...r, dirty: false } : r)),
    customers: cur.customers.map((r) => (r.dirty && at.get(`c${r.id}`) === r.updatedAt ? { ...r, dirty: false } : r)),
    orders: cur.orders.map((r) => (r.dirty && at.get(`o${r.id}`) === r.updatedAt ? { ...r, dirty: false } : r)),
    ledger: cur.ledger.map((r) => (r.dirty && at.get(`l${r.id}`) === r.updatedAt ? { ...r, dirty: false } : r)),
    dayCloses: cur.dayCloses.map((r) => (r.dirty && at.get(`d${r.date}`) === r.updatedAt ? { ...r, dirty: false } : r)),
  }));
}

export function hasPendingChanges(): boolean {
  const rows = useAppStore.getState().rows;
  return (
    rows.shop?.dirty === true ||
    rows.services.some((r) => r.dirty) ||
    rows.customers.some((r) => r.dirty) ||
    rows.orders.some((r) => r.dirty) ||
    rows.ledger.some((r) => r.dirty) ||
    rows.dayCloses.some((r) => r.dirty)
  );
}

/** One sync = pull then push, mutex-guarded. */
export function syncNow(): Promise<SyncResult> {
  return runLocked(async () => {
    await pullOnce();
    await pushOnce();
  });
}

/**
 * Push only, mutex-guarded — for a brand-new account right after seeding,
 * where a pull can't return anything and would only add a round trip.
 */
export function pushNow(): Promise<SyncResult> {
  return runLocked(pushOnce);
}

function runLocked(work: () => Promise<void>): Promise<SyncResult> {
  const run = chain.then(async (): Promise<SyncResult> => {
    if (!prefs.loggedIn) return { kind: "skipped" };
    const store = useAppStore.getState();
    store.setSyncing(true);
    try {
      await work();
      return { kind: "success" };
    } catch (e) {
      if (e instanceof NotLoggedInError) {
        return { kind: "error", message: "Not logged in", authDead: true };
      }
      return { kind: "error", message: e instanceof Error ? e.message : "Sync failed", authDead: false };
    } finally {
      useAppStore.getState().setSyncing(false);
    }
  });
  chain = run.catch(() => undefined);
  return run;
}

// ---------------- scheduler ----------------

let debounceTimer: ReturnType<typeof setTimeout> | null = null;

/** Debounced sync after every command (the app's SyncScheduler, tighter for an online-only client). */
export function requestSync() {
  if (debounceTimer) clearTimeout(debounceTimer);
  debounceTimer = setTimeout(() => {
    void syncNow();
  }, 400);
}

let autoStarted = false;

/** Periodic pull + sync-on-focus so another device's changes show up. */
export function startAutoSync() {
  if (autoStarted || typeof window === "undefined") return;
  autoStarted = true;
  setInterval(() => {
    if (document.visibilityState === "visible") void syncNow();
  }, 60_000);
  document.addEventListener("visibilitychange", () => {
    if (document.visibilityState === "visible") void syncNow();
  });
  window.addEventListener("beforeunload", (e) => {
    if (hasPendingChanges()) {
      e.preventDefault();
    }
  });
}
