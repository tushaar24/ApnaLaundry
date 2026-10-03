import { authApi, parseIsoMs } from "./authApi";
import { prefs } from "./prefs";

/**
 * Hands out a valid access token for API calls, refreshing (single-flight)
 * when the stored one is within 30s of expiry. Port of
 * data/remote/TokenManager.kt.
 *
 * Returns null when there is no usable token: transient network failure
 * (sync retries later) or a dead session (401 on refresh — credentials are
 * wiped and loggedIn flipped off so the auth gate shows login).
 */

let inflight: Promise<string | null> | null = null;

/** Called by the auth gate when the session dies mid-use. */
export type SessionDeadListener = () => void;
let onSessionDead: SessionDeadListener | null = null;
export function setSessionDeadListener(fn: SessionDeadListener) {
  onSessionDead = fn;
}

async function refreshLocked(): Promise<string | null> {
  const refresh = prefs.refreshToken;
  if (!refresh) return null;
  let reply;
  try {
    reply = await authApi.refresh(refresh);
  } catch {
    return null; // offline — try again on the next sync
  }
  if (reply.status === 401) {
    // Session revoked/expired on the server: this login is dead.
    prefs.clearCredentials();
    prefs.setLoggedIn(false);
    onSessionDead?.();
    return null;
  }
  const body = reply.body;
  if (!body?.success || !body.accessToken || !body.refreshToken) return null;
  prefs.setAccessPair(body.accessToken, parseIsoMs(body.accessExpiresAt), body.refreshToken);
  return body.accessToken;
}

function singleFlight(fn: () => Promise<string | null>): Promise<string | null> {
  if (inflight) return inflight;
  inflight = fn().finally(() => {
    inflight = null;
  });
  return inflight;
}

export const tokenManager = {
  async validAccessToken(): Promise<string | null> {
    const access = prefs.accessToken;
    if (access && Date.now() < prefs.accessExpiresAt - 30_000) return access;
    return singleFlight(refreshLocked);
  },

  /** Called after a request still got a 401 with a fresh-looking token. */
  forceRefresh(): Promise<string | null> {
    return singleFlight(refreshLocked);
  },
};
