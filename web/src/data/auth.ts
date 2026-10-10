"use client";

import type { ConfirmationResult } from "firebase/auth";
import { authApi, parseIsoMs } from "./authApi";
import { confirmFirebaseOtp, firebaseErrorCode, sendFirebaseOtp } from "./firebase";
import { prefs } from "./prefs";
import { useAppStore } from "./store";
import { clearAll, ensureSeeded, hasShop, isOnboarded, pulledSetupDone } from "./repository";
import { hasPendingChanges, pushNow, resetCheckpoint, startAutoSync, syncNow } from "./sync";
import { setSessionDeadListener } from "./tokenManager";
import { Analytics } from "@/analytics/events";
import { MetaPixel } from "@/analytics/metaPixel";
import { deriveState } from "./store";

/**
 * OTP login (Firebase phone auth; backend OTP for app-review numbers) + the
 * login/logout data lifecycle. Port of
 * data/AuthRepository.kt for the online-only web client:
 *  - verify success -> store credentials -> initial sync. If the server
 *    already holds this account's shop, setup is skipped; otherwise defaults
 *    are seeded and the setup flow runs.
 *  - logout refuses to discard unsynced work: it pushes first and fails
 *    (keeping the session) if that isn't possible.
 */

/**
 * In-flight OTP. Every real number goes through Firebase phone auth; the
 * app-review numbers 10000000xx keep the backend's own deterministic OTP.
 */
export type Challenge =
  | {
      kind: "backend";
      id: string;
      token: string;
      phone: string;
      nextSendAtMs: number;
      attemptsRemaining: number;
    }
  | {
      kind: "firebase";
      confirmation: ConfirmationResult;
      phone: string;
      nextSendAtMs: number;
    };

export class AuthError extends Error {
  attemptsRemaining?: number;
  /** The code was wrong but the same OTP can be retried. */
  wrongCode: boolean;
  constructor(message: string, attemptsRemaining?: number, wrongCode = false) {
    super(message);
    this.name = "AuthError";
    this.attemptsRemaining = attemptsRemaining;
    this.wrongCode = wrongCode;
  }
}

const OFFLINE_MSG = "No internet — check your connection and try again";
const RESEND_COOLDOWN_MS = 30_000;

const isReviewNumber = (phone: string) => phone.startsWith("10000000");

export async function requestOtp(phone: string): Promise<Challenge> {
  if (isReviewNumber(phone)) return requestBackendOtp(phone);
  let confirmation: ConfirmationResult;
  try {
    confirmation = await sendFirebaseOtp(phone);
  } catch (e) {
    const code = firebaseErrorCode(e);
    throw new AuthError(
      code === "auth/network-request-failed" ? OFFLINE_MSG
        : code === "auth/too-many-requests" || code === "auth/quota-exceeded" ? "Too many OTP requests — try again later"
        : code === "auth/invalid-phone-number" ? "Enter a valid 10-digit mobile number"
        : "Couldn't send OTP — try again",
    );
  }
  return { kind: "firebase", confirmation, phone, nextSendAtMs: Date.now() + RESEND_COOLDOWN_MS };
}

async function requestBackendOtp(phone: string): Promise<Challenge> {
  let reply;
  try {
    reply = await authApi.requestOtp(phone);
  } catch {
    throw new AuthError(OFFLINE_MSG);
  }
  const body = reply.body;
  if (!body?.success || !body.challengeId || !body.challengeToken) {
    let msg = body?.message ?? "Couldn't send OTP — try again";
    // Short cooldowns come with retryAt — show the wait so it's actionable.
    const retrySecs = Math.ceil((parseIsoMs(body?.retryAt) - Date.now()) / 1000);
    if (retrySecs > 0 && retrySecs <= 120) msg = `${msg} (${retrySecs}s)`;
    throw new AuthError(msg);
  }
  return {
    kind: "backend",
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
  if (challenge.kind === "firebase") {
    let idToken: string;
    try {
      idToken = await confirmFirebaseOtp(challenge.confirmation, otp);
    } catch (e) {
      const code = firebaseErrorCode(e);
      if (code === "auth/invalid-verification-code") throw new AuthError("Wrong OTP", undefined, true);
      throw new AuthError(
        code === "auth/code-expired" ? "OTP expired — request a new one"
          : code === "auth/network-request-failed" ? OFFLINE_MSG
          : code === "auth/too-many-requests" ? "Too many attempts — try again later"
          : "Verification failed — try again",
      );
    }
    try {
      reply = await authApi.firebaseLogin(idToken, deviceId);
    } catch {
      throw new AuthError(OFFLINE_MSG);
    }
  } else {
    try {
      reply = await authApi.verifyOtp(challenge.id, challenge.token, otp, deviceId);
    } catch {
      throw new AuthError(OFFLINE_MSG);
    }
  }
  const body = reply.body;
  if (!body?.success) {
    const attempts = body?.attemptsRemaining;
    throw new AuthError(body?.message ?? "Verification failed — try again", attempts, attempts != null && attempts > 0);
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
  resetCheckpoint();

  // Mark the session logged in BEFORE syncing: syncNow() no-ops while
  // !prefs.loggedIn, so without this the initial pull and the seed-push below
  // are both skipped and a brand-new shop's seeded rate card is never persisted
  // (lost if the user leaves before a later sync). This sets prefs.loggedIn
  // only — `authed` (which drives Gate routing) still flips once, at the end.
  prefs.setLoggedIn(true);

  // Initial sync: pull this account's data if it exists. We resolve setup
  // status BEFORE flipping `authed`, so the Gate routes exactly once — flipping
  // authed first would let it transiently route to /setup (a rate-screen flash)
  // during the pull, before setupDone is known. A just-created account has
  // nothing to pull, so it skips straight to seeding (saves a round trip).
  if (!body.isNewUser) await syncNow();
  const existingAccount = hasShop();
  if (!existingAccount) {
    await ensureSeeded(user.phone);
    // Just pulled (or nothing to pull) — only the seed needs to go up.
    await pushNow();
  }
  // An existing shop isn't proof of setup (the seed went up at first login):
  // an account that never onboarded still gets name → rate list after paying.
  const onboarded = isOnboarded();
  store.setSetupDone(onboarded);
  // Setup status is now known — reveal the authed app in a single transition.
  store.setAuthed(true);

  // Identify the owner so every event attributes to this shop, then the
  // funnel event. is_new_user = the server just created this account;
  // needs_setup = not onboarded yet (also true for an old account that never set up).
  const shopName = deriveState(useAppStore.getState().rows).shop.name;
  Analytics.identify({ identity: user.id, phone: user.phone, name: shopName });
  Analytics.loggedIn(body.isNewUser === true, !onboarded);
  MetaPixel.loginSuccess(!existingAccount);
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
    // The shop's onboarding step decides (so finishing on another device
    // counts); older shops count as set up only once they have activity.
    const done = pulledSetupDone();
    if (done != null) useAppStore.getState().setSetupDone(done);
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
  } else {
    const done = pulledSetupDone();
    if (done != null) useAppStore.getState().setSetupDone(done);
  }
  useAppStore.getState().setHydrated(true);
  startAutoSync();
}
