import { tokenManager } from "./tokenManager";
import type { SyncChanges } from "./rows";

/**
 * /api/laundry/sync wire calls with Bearer auth + one 401-refresh-retry.
 * Port of data/sync/SyncApi.kt.
 */

export class NotLoggedInError extends Error {
  constructor() {
    super("Not logged in");
    this.name = "NotLoggedInError";
  }
}

export interface PushResponse {
  success: boolean;
  applied?: number;
  skipped?: number;
  message?: string;
}

export interface PullResponse {
  success: boolean;
  checkpoint?: number;
  changes?: SyncChanges;
  message?: string;
}

async function authedFetch(path: string, init?: RequestInit): Promise<Response> {
  let token = await tokenManager.validAccessToken();
  if (!token) throw new NotLoggedInError();
  let res = await fetch(path, {
    ...init,
    headers: { ...(init?.headers ?? {}), Authorization: `Bearer ${token}` },
  });
  if (res.status === 401) {
    token = await tokenManager.forceRefresh();
    if (!token) throw new NotLoggedInError();
    res = await fetch(path, {
      ...init,
      headers: { ...(init?.headers ?? {}), Authorization: `Bearer ${token}` },
    });
    if (res.status === 401) throw new NotLoggedInError();
  }
  return res;
}

export const syncApi = {
  async pull(since: number): Promise<PullResponse> {
    const res = await authedFetch(`/api/laundry/sync/pull?since=${since}`);
    return (await res.json()) as PullResponse;
  },

  async push(changes: SyncChanges): Promise<PushResponse> {
    const res = await authedFetch(`/api/laundry/sync/push`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ changes }),
    });
    return (await res.json()) as PushResponse;
  },
};
