/**
 * Small app-level flags + auth credentials, kept in localStorage.
 * Port of data/Prefs.kt (DataStore). The web app is online-only, so no
 * domain data is persisted here — only flags and tokens.
 */

const K = {
  LOGGED_IN: "al_logged_in",
  SETUP_DONE: "al_setup_done",
  HIDE_AMOUNTS: "al_hide_amounts",
  ACCESS_TOKEN: "al_access_token",
  ACCESS_EXPIRES_AT: "al_access_expires_at",
  REFRESH_TOKEN: "al_refresh_token",
  USER_ID: "al_user_id",
  USER_PHONE: "al_user_phone",
  DEVICE_ID: "al_device_id", // stable installation id, survives logout
  SUB_ACTIVE: "al_sub_active", // {u: userId, at: epoch ms} of the last check that saw an active subscription
} as const;

/** How long a cached "subscription active" result lets the owner in while billing is unreachable. */
const SUB_CACHE_MS = 7 * 24 * 60 * 60 * 1000;

const mem = new Map<string, string>(); // SSR-safe fallback

function get(key: string): string | null {
  if (typeof window === "undefined") return mem.get(key) ?? null;
  return window.localStorage.getItem(key);
}

function set(key: string, value: string) {
  if (typeof window === "undefined") { mem.set(key, value); return; }
  window.localStorage.setItem(key, value);
}

function remove(key: string) {
  if (typeof window === "undefined") { mem.delete(key); return; }
  window.localStorage.removeItem(key);
}

export const prefs = {
  get loggedIn(): boolean { return get(K.LOGGED_IN) === "1"; },
  setLoggedIn(v: boolean) { set(K.LOGGED_IN, v ? "1" : "0"); },

  get setupDone(): boolean { return get(K.SETUP_DONE) === "1"; },
  setSetupDone(v: boolean) { set(K.SETUP_DONE, v ? "1" : "0"); },

  get hideAmounts(): boolean { return get(K.HIDE_AMOUNTS) === "1"; },
  setHideAmounts(v: boolean) { set(K.HIDE_AMOUNTS, v ? "1" : "0"); },

  get accessToken(): string | null { return get(K.ACCESS_TOKEN); },
  get accessExpiresAt(): number { return parseInt(get(K.ACCESS_EXPIRES_AT) ?? "0", 10) || 0; },
  get refreshToken(): string | null { return get(K.REFRESH_TOKEN); },
  get userPhone(): string | null { return get(K.USER_PHONE); },

  setCredentials(access: string, accessExpiresAt: number, refresh: string, userId: string, phone: string) {
    set(K.ACCESS_TOKEN, access);
    set(K.ACCESS_EXPIRES_AT, String(accessExpiresAt));
    set(K.REFRESH_TOKEN, refresh);
    set(K.USER_ID, userId);
    set(K.USER_PHONE, phone);
  },

  setAccessPair(access: string, accessExpiresAt: number, refresh: string) {
    set(K.ACCESS_TOKEN, access);
    set(K.ACCESS_EXPIRES_AT, String(accessExpiresAt));
    set(K.REFRESH_TOKEN, refresh);
  },

  /** Drops tokens (dead session) but keeps user id/phone. */
  clearCredentials() {
    remove(K.ACCESS_TOKEN);
    remove(K.ACCESS_EXPIRES_AT);
    remove(K.REFRESH_TOKEN);
  },

  /** Remember that the server just confirmed an active subscription for this user. */
  markSubActive() {
    const u = get(K.USER_ID);
    if (u) set(K.SUB_ACTIVE, JSON.stringify({ u, at: Date.now() }));
  },
  clearSubActive() { remove(K.SUB_ACTIVE); },
  /**
   * True when, within the last 7 days, the server confirmed an active
   * subscription for the logged-in user. Only used when billing can't be reached.
   */
  get subActiveCached(): boolean {
    try {
      const c = JSON.parse(get(K.SUB_ACTIVE) ?? "null") as { u?: string; at?: number } | null;
      const age = Date.now() - (c?.at ?? 0);
      return !!c && c.u === get(K.USER_ID) && age >= 0 && age < SUB_CACHE_MS;
    } catch {
      return false;
    }
  },

  /** Stable per-install device id. */
  deviceId(): string {
    let id = get(K.DEVICE_ID);
    if (!id) {
      id = typeof crypto !== "undefined" && "randomUUID" in crypto
        ? crypto.randomUUID()
        : `web-${Date.now()}-${Math.random().toString(36).slice(2)}`;
      set(K.DEVICE_ID, id);
    }
    return id;
  },

  logoutAndReset() {
    this.setLoggedIn(false);
    this.setSetupDone(false);
    this.setHideAmounts(false);
    remove(K.ACCESS_TOKEN);
    remove(K.ACCESS_EXPIRES_AT);
    remove(K.REFRESH_TOKEN);
    remove(K.USER_ID);
    remove(K.USER_PHONE);
    remove(K.SUB_ACTIVE);
    // DEVICE_ID survives — it identifies the installation, not the user.
  },
};
