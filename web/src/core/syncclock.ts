/**
 * Wall-clock source for the sync layer's `updatedAt` stamps (last-write-wins
 * key on the server). Monotonic within the page so two writes in the same
 * millisecond still order deterministically. Port of core/SyncClock.kt.
 */
let last = 0;

export function syncNow(): number {
  const t = Math.max(Date.now(), last + 1);
  last = t;
  return t;
}
