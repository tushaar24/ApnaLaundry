"use client";

import { getApp, getApps, initializeApp } from "firebase/app";
import {
  getAuth,
  RecaptchaVerifier,
  signInWithPhoneNumber,
  signOut,
  type ConfirmationResult,
} from "firebase/auth";

/**
 * Firebase phone auth (project shweta-makeover, the mylaundry.work web app):
 * Firebase sends the OTP SMS and checks the code behind an invisible
 * reCAPTCHA. We only keep the resulting ID token, which the backend trades
 * for our own session — the Firebase sign-in itself is dropped straight away.
 */

const firebaseConfig = {
  apiKey: "AIzaSyD5nbQKzKksks345VwK1hf823gVTlJ-08A",
  authDomain: "shweta-makeover.firebaseapp.com",
  projectId: "shweta-makeover",
  storageBucket: "shweta-makeover.firebasestorage.app",
  messagingSenderId: "2721051979",
  appId: "1:2721051979:web:2fe032124dd8d602aa24ce",
};

/** Element id the invisible reCAPTCHA renders into (the login page keeps one mounted). */
export const RECAPTCHA_CONTAINER_ID = "recaptcha-container";

function auth() {
  return getAuth(getApps().length ? getApp() : initializeApp(firebaseConfig));
}

let verifier: RecaptchaVerifier | null = null;

/** Sends the OTP SMS to a 10-digit Indian number. */
export async function sendFirebaseOtp(phone: string): Promise<ConfirmationResult> {
  // A fresh reCAPTCHA per send: a used or failed one can't be reused.
  verifier?.clear();
  const host = document.getElementById(RECAPTCHA_CONTAINER_ID);
  if (!host) throw new Error("reCAPTCHA container missing");
  const el = document.createElement("div");
  host.replaceChildren(el);
  verifier = new RecaptchaVerifier(auth(), el, { size: "invisible" });
  return signInWithPhoneNumber(auth(), `+91${phone}`, verifier);
}

/** Checks [code] and returns a fresh Firebase ID token (throws Firebase errors, e.g. a wrong code). */
export async function confirmFirebaseOtp(confirmation: ConfirmationResult, code: string): Promise<string> {
  const { user } = await confirmation.confirm(code);
  const token = await user.getIdToken(true);
  await signOut(auth());
  return token;
}

/** Firebase error code ("auth/invalid-verification-code"), or undefined. */
export function firebaseErrorCode(e: unknown): string | undefined {
  const code = (e as { code?: unknown } | null)?.code;
  return typeof code === "string" ? code : undefined;
}
