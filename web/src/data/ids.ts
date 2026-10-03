import type { LedgerRow } from "./rows";

/**
 * Ledger ts/id minting (port of LaundryRepository's tsCounter). Values are
 * minted locally; pulled rows may carry higher ones, so the counter is
 * refreshed after every pull.
 */

let tsCounter = 100_000;

export function refreshTsCounter(ledger: LedgerRow[]) {
  const maxTs = ledger.reduce((m, r) => Math.max(m, r.ts), 0);
  tsCounter = Math.max(tsCounter, maxTs + 1);
}

export function nextTs(): number {
  return ++tsCounter;
}
