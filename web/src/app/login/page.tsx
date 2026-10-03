"use client";

import { useEffect, useRef, useState } from "react";
import { AuthError, requestOtp, verifyOtp, type Challenge } from "@/data/auth";
import { Analytics } from "@/analytics/events";
import { useScreenView } from "@/analytics/useScreenView";
import { cls, FieldBox, PrimaryButton } from "@/ui/basics";
import { Gate } from "@/ui/gate";
import { LegalFooter } from "@/ui/legal";
import { IcLaundry } from "@/ui/icons";

/**
 * Phone → OTP sign-in. Port of ui/screens/login (LoginScreen + AuthViewModel):
 * real Indian mobiles start 6-9; 10000000xx are the backend's app-review
 * numbers (deterministic OTP, no SMS).
 */

export default function LoginPage() {
  return (
    <Gate zone="login">
      <LoginScreen />
    </Gate>
  );
}

function LoginScreen() {
  const [phone, setPhone] = useState("");
  const [otp, setOtp] = useState("");
  const [step, setStep] = useState<"phone" | "otp">("phone");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [resendInSecs, setResendInSecs] = useState(0);
  const [attemptsRemaining, setAttemptsRemaining] = useState<number | null>(null);
  const challengeRef = useRef<Challenge | null>(null);
  const countdownRef = useRef<ReturnType<typeof setInterval> | null>(null);

  useScreenView("login");

  const phoneValid = phone.length === 10 && ((phone[0] >= "6" && phone[0] <= "9") || phone.startsWith("10000000"));
  const otpValid = otp.length === 6;

  useEffect(() => () => {
    if (countdownRef.current) clearInterval(countdownRef.current);
  }, []);

  function startResendCountdown(nextSendAtMs: number) {
    if (countdownRef.current) clearInterval(countdownRef.current);
    const tick = () => {
      const left = Math.ceil((nextSendAtMs - Date.now()) / 1000);
      setResendInSecs(Math.max(0, left));
      if (left <= 0 && countdownRef.current) clearInterval(countdownRef.current);
    };
    tick();
    countdownRef.current = setInterval(tick, 1000);
  }

  async function sendOtp() {
    if (!phoneValid || loading || resendInSecs > 0) return;
    setLoading(true);
    setError(null);
    Analytics.otpRequested(phone.startsWith("10000000") ? "review" : "real", step === "otp");
    try {
      const ch = await requestOtp(phone);
      challengeRef.current = ch;
      setStep("otp");
      setOtp("");
      setAttemptsRemaining(ch.attemptsRemaining);
      startResendCountdown(ch.nextSendAtMs);
    } catch (e) {
      const msg = e instanceof Error ? e.message : "Couldn't send OTP — try again";
      Analytics.otpRequestFailed(msg);
      setError(msg);
    } finally {
      setLoading(false);
    }
  }

  async function verify() {
    const ch = challengeRef.current;
    if (!otpValid || loading) return;
    if (!ch) {
      setError("Request a new OTP first");
      return;
    }
    setLoading(true);
    setError(null);
    Analytics.otpSubmitted();
    try {
      await verifyOtp(ch, otp);
      // The Gate reacts to authed/setupDone and routes to /setup or /.
    } catch (e) {
      setOtp("");
      const attempts = e instanceof AuthError ? e.attemptsRemaining : undefined;
      if (attempts != null) setAttemptsRemaining(attempts);
      const msg = e instanceof Error ? e.message : "Verification failed — try again";
      Analytics.otpVerificationFailed(msg, attempts);
      setError(msg);
      setLoading(false);
    }
  }

  function backToPhone() {
    if (countdownRef.current) clearInterval(countdownRef.current);
    challengeRef.current = null;
    setStep("phone");
    setOtp("");
    setError(null);
    setResendInSecs(0);
    setAttemptsRemaining(null);
  }

  return (
    <div className="mx-auto flex min-h-dvh w-full max-w-[520px] flex-col gap-9 px-6 pb-6 pt-10">
      <div className="flex size-12 items-center justify-center rounded-[14px] bg-blue text-ondark">
        <IcLaundry size={26} />
      </div>

      {step === "phone" ? (
        <>
          <div className="flex flex-col gap-3">
            <h1 className="bric text-[38px] leading-[40px]">
              Orders and bills,
              <br />
              in two taps.
            </h1>
            <p className="text-[17px] text-muted">Enter your mobile number to start. No password, no long forms.</p>
          </div>
          <div className="flex flex-col gap-2.5">
            <span className="text-[14px] font-semibold">Mobile number</span>
            <FieldBox
              value={phone}
              onChange={(v) => { setPhone(v.replace(/\D/g, "").slice(0, 10)); setError(null); }}
              prefix="+91"
              h={60}
              borderColor="var(--color-blue)"
              borderWidth={2}
              inputMode="tel"
              placeholder="98765 43210"
              textClass="text-[19px] font-semibold tracking-[0.02em]"
              autoFocus
              inputProps={{
                onKeyDown: (e) => { if (e.key === "Enter") void sendOtp(); },
              }}
            />
            <p className="text-[14px] text-muted">We&apos;ll send a one-time password to this number by SMS.</p>
            {error ? <p className="text-[14px] font-semibold text-orangetext">{error}</p> : null}
          </div>
          <div className="flex-1" />
          <PrimaryButton h={58} disabled={!phoneValid || loading} onClick={() => void sendOtp()}>
            {loading ? "Sending…" : "Continue"}
          </PrimaryButton>
        </>
      ) : (
        <>
          <div className="flex flex-col gap-3">
            <h1 className="bric text-[38px] leading-[40px]">Enter the code</h1>
            <div className="flex gap-2">
              <span className="text-[17px] text-muted">Sent to +91 {phone}</span>
              <button type="button" onClick={backToPhone} className="text-[17px] font-bold text-blue">
                Change
              </button>
            </div>
          </div>
          <div className="flex flex-col gap-2.5">
            <span className="text-[14px] font-semibold">6-digit OTP</span>
            <FieldBox
              value={otp}
              onChange={(v) => { setOtp(v.replace(/\D/g, "").slice(0, 6)); setError(null); }}
              h={60}
              borderColor="var(--color-blue)"
              borderWidth={2}
              inputMode="numeric"
              placeholder="••••••"
              center
              textClass="text-[24px] font-bold tracking-[0.4em]"
              autoFocus
              inputProps={{
                onKeyDown: (e) => { if (e.key === "Enter") void verify(); },
                autoComplete: "one-time-code",
              }}
            />
            {error ? (
              <p className="text-[14px] font-semibold text-orangetext">{error}</p>
            ) : attemptsRemaining != null && attemptsRemaining < 5 ? (
              <p className="text-[14px] text-muted">{attemptsRemaining} attempts left</p>
            ) : null}
            {resendInSecs > 0 ? (
              <p className="text-[14px] text-muted">Resend code in {resendInSecs}s</p>
            ) : (
              <button type="button" onClick={() => void sendOtp()} className={cls("w-fit text-[14px] font-bold text-blue")}>
                Resend code
              </button>
            )}
          </div>
          <div className="flex-1" />
          <PrimaryButton h={58} disabled={!otpValid || loading} onClick={() => void verify()}>
            {loading ? "Verifying…" : "Verify & continue"}
          </PrimaryButton>
        </>
      )}
      <LegalFooter />
    </div>
  );
}
