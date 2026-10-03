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
} as const;

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
    // DEVICE_ID survives — it identifies the installation, not the user.
  },
};
