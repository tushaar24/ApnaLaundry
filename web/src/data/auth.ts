"use client";

import { authApi, parseIsoMs } from "./authApi";
import { prefs } from "./prefs";
import { useAppStore } from "./store";
import { clearAll, ensureSeeded, hasShop } from "./repository";
import { hasPendingChanges, resetCheckpoint, startAutoSync, syncNow } from "./sync";
import { setSessionDeadListener } from "./tokenManager";
import { Analytics } from "@/analytics/events";
import { deriveState } from "./store";

/**
 * Real OTP auth + the login/logout data lifecycle. Port of
 * data/AuthRepository.kt for the online-only web client:
 *  - verify success -> store credentials -> initial sync. If the server
 *    already holds this account's shop, setup is skipped; otherwise defaults
 *    are seeded and the setup flow runs.
 *  - logout refuses to discard unsynced work: it pushes first and fails
 *    (keeping the session) if that isn't possible.
 */

export interface Challenge {
  id: string;
  token: string;
  phone: string;
  nextSendAtMs: number;
  attemptsRemaining: number;
}

export class AuthError extends Error {
  attemptsRemaining?: number;
  constructor(message: string, attemptsRemaining?: number) {
    super(message);
    this.name = "AuthError";
    this.attemptsRemaining = attemptsRemaining;
  }
}

const OFFLINE_MSG = "No internet — check your connection and try again";

export async function requestOtp(phone: string): Promise<Challenge> {
  let reply;
  try {
    reply = await authApi.requestOtp(phone);
  } catch {
    throw new AuthError(OFFLINE_MSG);
  }
  const body = reply.body;
  if (!body?.success || !body.challengeId || !body.challengeToken) {
    throw new AuthError(body?.message ?? "Couldn't send OTP — try again");
  }
  return {
    id: body.challengeId,
    token: body.challengeToken,
    phone,
    nextSendAtMs: parseIsoMs(body.nextSendAt),
    attemptsRemaining: body.attemptsRemaining ?? 5,
  };
}

export async function verifyOtp(challenge: Challenge, otp: string): Promise<void> {
  const deviceId = prefs.deviceId();
  let reply;
  try {
    reply = await authApi.verifyOtp(challenge.id, challenge.token, otp, deviceId);
  } catch {
    throw new AuthError(OFFLINE_MSG);
  }
  const body = reply.body;
  if (!body?.success) {
    throw new AuthError(body?.message ?? "Verification failed — try again", body?.attemptsRemaining);
  }
  const user = body.user;
  if (!user || !body.accessToken || !body.refreshToken) {
    throw new AuthError("Verification failed — try again");
  }

  // The web store is in-memory per page load, so there is never stale data
  // from a different account — but the flags must reset when the phone changes.
  const prevPhone = prefs.userPhone;
  if (prevPhone != null && prevPhone !== user.phone) {
    clearAll();
    prefs.setSetupDone(false);
  }

  prefs.setCredentials(body.accessToken, parseIsoMs(body.accessExpiresAt), body.refreshToken, user.id, user.phone);
  const store = useAppStore.getState();
  store.setAuthed(true);
  resetCheckpoint();

  // Initial sync: pull this account's data if it exists.
  await syncNow();
  const existingAccount = hasShop();
  if (existingAccount) {
    // Existing account restored from the server — skip the setup flow.
    store.setSetupDone(true);
  } else {
    store.setSetupDone(false);
    await ensureSeeded(user.phone);
    await syncNow();
  }

  // Identify the owner so every event attributes to this shop, then the
  // funnel event. is_new_user / needs_setup = this account had no shop yet.
  const shopName = deriveState(useAppStore.getState().rows).shop.name;
  Analytics.identify({ identity: user.id, phone: user.phone, name: shopName });
  Analytics.loggedIn(!existingAccount, !existingAccount);
}

/**
 * Push-then-logout. Throws (session kept) if unsynced work can't be pushed
 * for a transient reason — but a dead session never blocks logout.
 */
export async function logout(): Promise<void> {
  if (hasPendingChanges()) {
    const r = await syncNow();
    const authDead = r.kind === "error" && r.authDead;
    if (!authDead && hasPendingChanges()) {
      throw new AuthError("Couldn't sync your latest changes — check internet and try again");
    }
  }
  const token = prefs.accessToken;
  if (token) {
    try {
      await authApi.logout(token); // best-effort server-side revoke
    } catch {
      /* ignore */
    }
  }
  Analytics.loggedOut();
  prefs.logoutAndReset();
  clearAll();
  resetCheckpoint();
  const store = useAppStore.getState();
  store.setAuthed(false);
  store.setSetupDone(false);
}

/**
 * Page-load bootstrap for the online-only client: if logged in, pull the
 * full state (checkpoint starts at 0 each load). A dead session drops back
 * to login; a network failure surfaces a retry screen.
 */
let booted = false;

export async function bootstrap(): Promise<void> {
  if (booted) return;
  booted = true;

  const store = useAppStore.getState();
  store.setAuthed(prefs.loggedIn);
  store.setSetupDone(prefs.setupDone);
  useAppStore.setState({ hideAmounts: prefs.hideAmounts });

  setSessionDeadListener(() => {
    const s = useAppStore.getState();
    s.setAuthed(false);
  });

  if (!prefs.loggedIn) {
    store.setHydrated(true);
    return;
  }

  resetCheckpoint();
  const result = await syncNow();
  if (result.kind === "error") {
    if (result.authDead) {
      useAppStore.getState().setAuthed(false);
    } else {
      useAppStore.getState().setBootError(result.message);
    }
  } else {
    // An account whose shop exists server-side never re-runs setup.
    if (hasShop()) useAppStore.getState().setSetupDone(true);
  }
  useAppStore.getState().setHydrated(true);
  startAutoSync();
}

/** Retry after a failed boot pull. */
export async function retryBoot(): Promise<void> {
  const store = useAppStore.getState();
  store.setBootError(null);
  store.setHydrated(false);
  const result = await syncNow();
  if (result.kind === "error" && !result.authDead) {
    useAppStore.getState().setBootError(result.message);
  } else if (result.kind === "error" && result.authDead) {
    useAppStore.getState().setAuthed(false);
  } else if (hasShop()) {
    useAppStore.getState().setSetupDone(true);
  }
  useAppStore.getState().setHydrated(true);
  startAutoSync();
}
