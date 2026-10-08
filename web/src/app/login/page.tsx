"use client";

import Link from "next/link";
import { useEffect, useRef, useState, type ReactNode } from "react";
import { AuthError, requestOtp, verifyOtp, type Challenge } from "@/data/auth";
import { Analytics } from "@/analytics/events";
import { useScreenView } from "@/analytics/useScreenView";
import { cls } from "@/ui/basics";
import { Gate } from "@/ui/gate";
import { LegalFooter } from "@/ui/legal";

/**
 * Phone → OTP sign-in. Port of ui/screens/login (LoginScreen + AuthViewModel):
 * real Indian mobiles start 6-9; 10000000xx are the backend's app-review
 * numbers (deterministic OTP, no SMS). Ad end-card look: blue hero on top,
 * cream sheet with the form at the bottom.
 */

export default function LoginPage() {
  return (
    <Gate zone="login">
      <LoginScreen />
    </Gate>
  );
}

const OTP_LEN = 6;
const WRONG_OTP = "Wrong OTP. Check your WhatsApp / SMS again.";

function LoginScreen() {
  const [phone, setPhone] = useState("");
  const [otp, setOtp] = useState("");
  const [step, setStep] = useState<"phone" | "otp">("phone");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [resendInSecs, setResendInSecs] = useState(0);
  const [attemptsRemaining, setAttemptsRemaining] = useState<number | null>(null);
  const [resent, setResent] = useState(false);
  const challengeRef = useRef<Challenge | null>(null);
  const countdownRef = useRef<ReturnType<typeof setInterval> | null>(null);

  useScreenView("login");

  const phoneValid = phone.length === 10 && ((phone[0] >= "6" && phone[0] <= "9") || phone.startsWith("10000000"));
  const otpValid = otp.length === OTP_LEN;

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
      setResent(step === "otp");
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
      const attempts = e instanceof AuthError ? e.attemptsRemaining : undefined;
      if (attempts != null) setAttemptsRemaining(attempts);
      const msg = e instanceof Error ? e.message : "Verification failed — try again";
      Analytics.otpVerificationFailed(msg, attempts);
      // A rejected code (attempts still left) keeps the digits, shown red, until
      // edited; anything else (expired, exhausted, offline) shows the server text.
      const wrongCode = attempts != null && attempts > 0;
      if (!wrongCode) setOtp("");
      setError(wrongCode ? WRONG_OTP : msg);
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
    setResent(false);
  }

  return (
    <div className="min-h-dvh bg-blue text-ondark">
      <div className="mx-auto flex min-h-dvh w-full max-w-[520px] flex-col">
        {step === "phone" ? (
          <>
            <div className="flex flex-col gap-[18px] px-6 pb-6 pt-12">
              <BrandPill />
              <HeroArt />
              <h1 className="bric text-[34px] leading-[1.05]">Laundry Business Made Easy!</h1>
              <ul className="flex flex-col gap-2.5">
                <Perk>Bills on WhatsApp</Perk>
                <Perk>Pickup &amp; delivery tracking</Perk>
                <Perk>All your accounts in one place</Perk>
              </ul>
            </div>
            <div className="flex-1" />
            <Sheet>
              <div className="flex flex-col gap-2.5">
                <label htmlFor="phone" className="text-[14px] font-semibold">Mobile number</label>
                <div className="flex h-[60px] items-center gap-2.5 rounded-[14px] border-2 border-cardborder bg-card px-4 focus-within:border-blue">
                  <span className="text-[19px] font-semibold text-muted">+91</span>
                  <input
                    id="phone"
                    value={phone.length > 5 ? `${phone.slice(0, 5)} ${phone.slice(5)}` : phone}
                    onChange={(e) => { setPhone(e.target.value.replace(/\D/g, "").slice(0, 10)); setError(null); }}
                    onKeyDown={(e) => { if (e.key === "Enter") void sendOtp(); }}
                    type="tel"
                    inputMode="numeric"
                    autoComplete="tel-national"
                    placeholder="Mobile Number"
                    autoFocus
                    className="min-w-0 flex-1 bg-transparent text-[19px] font-semibold tracking-[0.02em] text-ink placeholder:text-placeholder"
                  />
                </div>
                {error ? (
                  <p className="text-[14px] font-semibold text-errorred">{error}</p>
                ) : (
                  <p className="text-[14px] leading-[1.4] text-muted">The OTP will arrive on your WhatsApp / SMS</p>
                )}
              </div>
              <CtaButton disabled={!phoneValid || loading} onClick={() => void sendOtp()}>
                {loading ? "Sending OTP…" : "Get started"}
              </CtaButton>
              <p className="text-center text-[13px] text-muted">
                By continuing, you agree to our{" "}
                <Link href="/terms" className="text-blue underline">Terms</Link> and{" "}
                <Link href="/privacy" className="text-blue underline">Privacy Policy</Link>
              </p>
              <LegalFooter />
            </Sheet>
          </>
        ) : (
          <>
            <div className="flex flex-col gap-[18px] px-6 pb-7 pt-12">
              <div className="flex items-center gap-3">
                <button
                  type="button"
                  onClick={backToPhone}
                  aria-label="Back"
                  className="flex size-11 shrink-0 items-center justify-center rounded-full bg-white/15 hover:bg-white/25"
                >
                  <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><path d="M15 18l-6-6 6-6" /></svg>
                </button>
                <BrandPill small />
              </div>
              <OtpArt />
              <div className="flex flex-col gap-2">
                <h1 className="bric text-[34px] leading-[1.05]">Enter OTP</h1>
                <p className="text-[16px] leading-[1.45] text-onbluemuted">
                  Sent on WhatsApp / SMS to{" "}
                  <strong className="whitespace-nowrap text-ondark">+91 {phone.slice(0, 5)} {phone.slice(5)}</strong>
                  {" · "}
                  <button type="button" onClick={backToPhone} className="font-semibold text-ondark underline">
                    Change number
                  </button>
                </p>
              </div>
            </div>
            <Sheet grow gap={18}>
              <div className="flex flex-col gap-3.5">
                <OtpBoxes
                  value={otp}
                  error={error != null}
                  onChange={(v) => { setOtp(v.replace(/\D/g, "").slice(0, OTP_LEN)); setError(null); }}
                  onEnter={() => void verify()}
                />
                <OtpStatus otp={otp} error={error} attemptsRemaining={attemptsRemaining} resent={resent} />
              </div>
              <div className="flex min-h-11 flex-col justify-center gap-2.5">
                {resendInSecs > 0 ? (
                  <span className="text-[14px] text-muted">
                    Didn&apos;t get the OTP? You can resend in {resendInSecs} sec
                  </span>
                ) : (
                  <>
                    <span className="text-[14px] font-semibold text-inksecondary">Didn&apos;t get the OTP?</span>
                    <button
                      type="button"
                      onClick={() => void sendOtp()}
                      disabled={loading}
                      className="flex h-12 items-center justify-center gap-2 rounded-xl border-[1.5px] border-blue bg-card text-[15px] font-bold text-blue hover:bg-bluelight"
                    >
                      <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z" /></svg>
                      {loading ? "Sending…" : "Resend OTP"}
                    </button>
                  </>
                )}
              </div>
              <div className="flex-1" />
              <CtaButton disabled={!otpValid || error != null || loading} onClick={() => void verify()}>
                {loading ? "Verifying…" : "Continue"}
              </CtaButton>
            </Sheet>
          </>
        )}
      </div>
    </div>
  );
}

/** The cream bottom sheet holding the form. */
function Sheet({ children, grow, gap = 16 }: { children: ReactNode; grow?: boolean; gap?: number }) {
  return (
    <div style={{ gap }} className={cls("flex flex-col rounded-t-[28px] bg-bg px-6 pb-8 pt-6 text-ink", grow && "flex-1")}>
      {children}
    </div>
  );
}

/** Full-width 58px CTA; disabled is the design's grey, not the app's pale blue. */
function CtaButton({ children, disabled, onClick }: { children: ReactNode; disabled?: boolean; onClick: () => void }) {
  return (
    <button
      type="button"
      onClick={onClick}
      disabled={disabled}
      className={cls(
        "h-[58px] w-full rounded-[14px] text-[17px] font-bold transition-colors",
        disabled ? "bg-cardborder text-disabledfg" : "bg-blue text-ondark hover:bg-blue/90 active:bg-blue/80",
      )}
    >
      {children}
    </button>
  );
}

function Droplet({ size }: { size: number }) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d="M12 2.7l5.7 5.7a8 8 0 1 1-11.3 0z" />
    </svg>
  );
}

function BrandPill({ small }: { small?: boolean }) {
  return (
    <div className={cls("flex items-center gap-2 self-start bg-card", small ? "rounded-[14px] py-1.5 pl-1.5 pr-3" : "rounded-2xl py-2 pl-2 pr-3.5")}>
      <div className={cls("flex items-center justify-center bg-blue text-ondark", small ? "size-[30px] rounded-[9px]" : "size-[34px] rounded-[10px]")}>
        <Droplet size={small ? 17 : 19} />
      </div>
      <span className={cls("bric text-ink", small ? "text-[20px]" : "text-[22px]")}>
        My<span className="text-blue">Laundry</span>
      </span>
    </div>
  );
}

function Perk({ children }: { children: ReactNode }) {
  return (
    <li className="flex items-center gap-2.5 text-[16px] font-semibold">
      <span className="flex size-6 shrink-0 items-center justify-center rounded-full bg-green">
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="3.2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><path d="M20 6L9 17l-5-5" /></svg>
      </span>
      {children}
    </li>
  );
}

/**
 * Six digit boxes drawn over one real input, so paste, keyboard OTP
 * suggestions and autocomplete="one-time-code" all keep working.
 */
function OtpBoxes({ value, error, onChange, onEnter }: {
  value: string; error: boolean; onChange: (v: string) => void; onEnter: () => void;
}) {
  const [focused, setFocused] = useState(true);
  return (
    <div className="relative flex gap-2">
      {Array.from({ length: OTP_LEN }, (_, i) => {
        const ch = value[i] ?? "";
        const active = focused && !error && i === Math.min(value.length, OTP_LEN - 1);
        return (
          <div
            key={i}
            className={cls(
              "bric flex h-14 min-w-0 flex-1 items-center justify-center rounded-xl border-2 bg-card text-[24px] text-ink",
              error ? "border-errorred" : ch || active ? "border-blue" : "border-cardborder",
            )}
          >
            {ch}
          </div>
        );
      })}
      <input
        value={value}
        onChange={(e) => onChange(e.target.value)}
        onKeyDown={(e) => { if (e.key === "Enter") onEnter(); }}
        onFocus={() => setFocused(true)}
        onBlur={() => setFocused(false)}
        inputMode="numeric"
        autoComplete="one-time-code"
        maxLength={OTP_LEN}
        autoFocus
        aria-label="6-digit OTP"
        className="absolute inset-0 h-full w-full bg-transparent text-transparent caret-transparent outline-none selection:bg-transparent"
      />
    </div>
  );
}

function OtpStatus({ otp, error, attemptsRemaining, resent }: {
  otp: string; error: string | null; attemptsRemaining: number | null; resent: boolean;
}) {
  if (error) {
    return (
      <div role="alert" className="flex items-start gap-2 text-[14px] font-semibold text-errorred">
        <svg className="mt-px shrink-0" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><circle cx="12" cy="12" r="9" /><path d="M12 7.5v5.5M12 16.5v.01" /></svg>
        <span>
          {error}
          {attemptsRemaining != null && attemptsRemaining < 5 ? ` · ${attemptsRemaining} attempts left` : ""}
        </span>
      </div>
    );
  }
  if (otp.length === OTP_LEN) {
    return (
      <div className="flex items-center gap-2 text-[14px] font-semibold text-greentext">
        <span className="flex size-5 items-center justify-center rounded-full bg-green text-ondark">
          <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="3.5" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><path d="M20 6L9 17l-5-5" /></svg>
        </span>
        OTP filled in
      </div>
    );
  }
  return (
    <div className="flex items-center gap-2 text-[14px] text-inksecondary">
      <svg className="animate-spin" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="var(--color-blue)" strokeWidth="2.5" strokeLinecap="round" aria-hidden="true"><path d="M12 3a9 9 0 1 0 9 9" /></svg>
      {resent ? "OTP sent again — check your WhatsApp / SMS" : "Waiting for the OTP on WhatsApp / SMS"}
    </div>
  );
}

/** Ironed shirts on a rail, folded clothes and a phone showing a sent bill. */
function HeroArt() {
  return (
    <svg className="block h-auto w-full max-w-[342px]" viewBox="0 0 342 260" role="img" aria-label="Ironed shirts on a rail, a stack of folded clothes and a phone showing a sent bill" style={{ maxHeight: 200 }}>
      <g fill="none" stroke="#16191D" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
        <path d="M40 18 V30 M232 18 V30" stroke="#FFFFFF" strokeOpacity="0.7" />
        <path d="M36 30 H236" stroke="#FFFFFF" strokeWidth="4" />
        <Shirt x={96} fill="#FFFFFF" pocket />
        <Shirt x={176} fill="#F4C870" />
        <rect x="24" y="218" width="128" height="22" rx="6" fill="#BFD0F8" />
        <rect x="30" y="198" width="116" height="22" rx="6" fill="#E8A33D" />
        <rect x="36" y="178" width="104" height="22" rx="6" fill="#FFFFFF" />
        <rect x="82" y="174" width="14" height="66" fill="#F5F3EE" />
        <rect x="230" y="92" width="94" height="156" rx="16" fill="#16191D" />
        <rect x="237" y="102" width="80" height="136" rx="9" fill="#FFFFFF" stroke="none" />
        <circle cx="277" cy="134" r="15" fill="#16A34A" stroke="none" />
        <path d="M270 134 L275 139 L284 129" stroke="#FFFFFF" strokeWidth="3" />
        <path d="M249 164 H305 M249 176 H293 M249 188 H299" stroke="#E2DED6" strokeWidth="5" />
        <path d="M249 204 H271 M287 204 H305" strokeWidth="5" />
        <rect x="247" y="216" width="60" height="12" rx="6" fill="#1D4ED8" stroke="none" />
        <path d="M12 249 H330" stroke="#FFFFFF" strokeOpacity="0.35" />
      </g>
    </svg>
  );
}

function Shirt({ x, fill, pocket }: { x: number; fill: string; pocket?: boolean }) {
  return (
    <g transform={`translate(${x} 0)`}>
      <path d="M0 30 V40 L-34 58 H34 L0 40" stroke="#FFFFFF" />
      <path d="M-14 58 L-38 70 L-46 104 L-32 108 L-28 88 V168 H28 V88 L32 108 L46 104 L38 70 L14 58 Q0 70 -14 58 Z" fill={fill} />
      <path d="M-14 58 L-6 72 L0 66 L6 72 L14 58" fill={fill} />
      <path d="M0 66 V168" />
      {pocket ? <path d="M10 92 H22 V104 H10 Z" /> : null}
      {[88, 110, 132, 154].map((cy) => <circle key={cy} cx="0" cy={cy} r="1.8" fill="#16191D" stroke="none" />)}
    </g>
  );
}

/** A phone receiving a masked OTP message, with a padlock badge. */
function OtpArt() {
  return (
    <svg className="block" width="200" height="140" viewBox="0 0 200 140" role="img" aria-label="An OTP arriving on a phone by WhatsApp or SMS">
      <g fill="none" stroke="#16191D" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
        <rect x="56" y="8" width="64" height="124" rx="13" fill="#16191D" />
        <rect x="62" y="17" width="52" height="106" rx="7" fill="#FFFFFF" stroke="none" />
        <path d="M70 34 H104 M70 46 H96" stroke="#E2DED6" strokeWidth="5" />
        <path d="M98 54 H178 A12 12 0 0 1 190 66 V86 A12 12 0 0 1 178 98 H120 L108 108 V98 H98 A12 12 0 0 1 86 86 V66 A12 12 0 0 1 98 54 Z" fill="#FFFFFF" />
        {[104, 118, 132, 146, 160, 174].map((cx) => <circle key={cx} cx={cx} cy="76" r="4.5" fill="#1D4ED8" stroke="none" />)}
        <circle cx="54" cy="106" r="18" fill="#16A34A" />
        <rect x="47" y="104" width="14" height="10" rx="2" fill="#FFFFFF" stroke="none" />
        <path d="M50 104 V100 A4 4 0 0 1 58 100 V104" stroke="#FFFFFF" strokeWidth="2.2" />
      </g>
    </svg>
  );
}
