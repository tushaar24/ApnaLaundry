"use client";

import { useEffect, useMemo, useRef, useState } from "react";
import * as AppDate from "@/core/appdate";
import { rupees } from "@/core/money";
import { cancelSubscription, openSubscriptionCheckout, preloadCheckout, subscribe } from "@/data/billing";
import { paywallInfo, useBillingStore } from "@/data/billingStore";
import { Analytics } from "@/analytics/events";
import { MetaPixel } from "@/analytics/metaPixel";
import { cls, PrimaryButton } from "@/ui/basics";
import { IcBook, IcChart, IcChat, IcCheck, IcPlay, IcReceipt, IcVolume, IcVolumeOff, IcWarning } from "@/ui/icons";
import { AppSheet } from "@/ui/sheet";

/**
 * Paywall / plans screen for the ₹2 trial, built from the handoff (docs
 * 08-subscription, design 08b). It is the Gate's non-cancellable hard paywall:
 * mobile is the full-screen layout from the design; desktop is a centred card.
 * "Start trial" creates the Razorpay subscription and opens Razorpay Checkout
 * for the UPI AutoPay approval (Razorpay owns that step: its own UPI app picker
 * on mobile, QR on desktop); on success we poll status.
 *
 * Also exports ManagePlanScreen — the already-subscribed plan summary + cancel.
 */

type Plan = "annual" | "monthly";
type Stage = "plans" | "waiting";

// Launch "was" prices (struck-through) from the design — marketing only.
const WAS_MONTHLY = 799;
const WAS_ANNUAL = 8999;

// backend TRIAL_DAYS (laundry-razorpay.js)
const TRIAL_DAYS = 7;

// Paywall intro video — 720p faststart MP4 (~3.7 MB) served from public/.
const PAYWALL_VIDEO_URL = "/paywall-intro.mp4";

// Feature row under the video — four icons, two-line labels.
const TRIAL_FEATURES: { Icon: typeof IcReceipt; label: string }[] = [
  { Icon: IcChat, label: "Bills on WhatsApp" },
  { Icon: IcBook, label: "Khata for customers" },
  { Icon: IcReceipt, label: "Unlimited orders" },
  { Icon: IcChart, label: "Daily earnings" },
];

// UPI apps that support AutoPay mandates (text chips — no third-party logos).
const UPI_APPS = ["GPay", "PhonePe", "Paytm", "BHIM"];

// Dark paywall palette — navy with the brand blue/orange as accents.
const PW = {
  bg: "bg-[linear-gradient(180deg,#10255C_0%,#0B1838_42%,#08112A_100%)]",
  bar: "bg-[#08112A]/95",
  text: "text-[#B4C3E9]",
  accent: "text-[#8FB0FF]",
  eyebrow: "text-[#FDBA74]",
  surface: "bg-white/[0.06]",
  cta: "bg-[linear-gradient(90deg,#4876FF_0%,#1D4ED8_100%)] shadow-[0_8px_24px_rgba(29,78,216,0.45)]",
};

export function PaywallScreen({ onDone }: {
  /** Subscription is active — the Gate lets the user through. */
  onDone: () => void;
}) {
  const status = useBillingStore((s) => s.status);
  const refresh = useBillingStore((s) => s.refresh);
  const info = paywallInfo(status);

  const [plan, setPlan] = useState<Plan>("annual");
  const [stage, setStage] = useState<Stage>("plans");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [faq, setFaq] = useState(false);
  // Razorpay Checkout closed without paying (failed, cancelled or Back) —
  // the "try again" sheet over the plans.
  const [retrySheet, setRetrySheet] = useState(false);
  // Mirrors retrySheet synchronously: a failed payment fires both onError and
  // onDismiss, and only the first should count as "shown".
  const retryShownRef = useRef(false);
  const pollRef = useRef<ReturnType<typeof setInterval> | null>(null);

  const annual = status?.plans.annual.amount ?? 499900;
  const monthly = status?.plans.monthly.amount ?? 49900;
  const annualR = Math.round(annual / 100);
  const monthlyR = Math.round(monthly / 100);
  const trialR = Math.round((status?.plans.trial.amount ?? 200) / 100);
  const perMonth = Math.round(annual / 12 / 100);
  const saveVsMonthly = Math.round((monthly * 12 - annual) / 100);

  // Fire Paywall Shown once, and warm up Razorpay Checkout (script +
  // connections) while the user reads the plans — so Pay opens it instantly.
  useEffect(() => {
    Analytics.paywallShown();
    if (!info.hasActive) preloadCheckout();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // Activated elsewhere (another device, or a refresh after ALREADY_SUBSCRIBED):
  // nothing to pay for — let the user through.
  useEffect(() => {
    if (info.hasActive && stage === "plans") onDone();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [info.hasActive, stage]);

  // Stop polling on unmount.
  useEffect(() => () => { if (pollRef.current) clearInterval(pollRef.current); }, []);

  const trialEnd = useMemo(() => fmtDayMonth(TRIAL_DAYS), []);

  function showRetry(purchasedPlan: string, reason: "failed" | "cancelled") {
    if (!retryShownRef.current) Analytics.paymentRetryShown(purchasedPlan, reason);
    retryShownRef.current = true;
    setRetrySheet(true);
  }

  function hideRetry() {
    retryShownRef.current = false;
    setRetrySheet(false);
  }

  // `chosen` lets the retry sheet switch a failed Yearly attempt to Monthly.
  async function pay(chosen: Plan = plan) {
    if (busy) return;
    setBusy(true);
    setError(null);
    hideRetry();
    Analytics.planSelected(chosen);
    // If the mount-time warm-up failed or hasn't finished, this restarts the
    // checkout.js load in parallel with the subscribe call instead of after it.
    preloadCheckout();
    try {
      // The server decides the real plan/amount (never trust the client for money).
      const res = await subscribe(chosen);
      Analytics.checkoutStarted(res.plan, res.amount, res.trialAmount || 0);
      // Value in rupees: what this approval charges now (the ₹2 trial).
      const chargeRupees = (res.trialAmount || res.amount) / 100;
      MetaPixel.subscriptionInitiated(res.subscriptionId, res.plan, chargeRupees);
      // Razorpay Checkout owns the UPI AutoPay approval (its own app picker on
      // mobile, QR on desktop). On success we wait for the webhook to confirm.
      await openSubscriptionCheckout(res, {
        onSuccess: () => {
          MetaPixel.subscriptionActivated(res.subscriptionId, res.plan, chargeRupees);
          hideRetry();
          setStage("waiting");
          startPolling(res.plan);
        },
        onDismiss: () => {
          setBusy(false);
          showRetry(res.plan, "cancelled");
        },
        // Checkout stays open on a failed payment (Razorpay offers its own
        // retry); the sheet sits behind it and is there once it's closed.
        onError: (msg) => {
          Analytics.checkoutFailed(res.plan, msg);
          setBusy(false);
          showRetry(res.plan, "failed");
        },
      });
    } catch (e) {
      setError(e instanceof Error ? e.message : "Could not start — try again");
      Analytics.checkoutFailed(chosen, e instanceof Error ? e.message : "error");
      setBusy(false);
      // e.g. ALREADY_SUBSCRIBED from another device — refetch so an active
      // subscription lets the user through instead of a broken Pay button.
      void refresh();
    }
  }

  // Poll for the webhook-confirmed activation — capped at ~90s (matches the
  // Android app) so a lost webhook shows a retriable message, not an eternal
  // spinner.
  const POLL_MS = 3000;
  const MAX_POLLS = 30;
  function startPolling(purchasedPlan: string) {
    if (pollRef.current) clearInterval(pollRef.current);
    let polls = 0;
    pollRef.current = setInterval(async () => {
      await refresh(0); // the poll itself is the retry
      const s = useBillingStore.getState().status;
      if (s?.hasActiveSubscription) {
        if (pollRef.current) clearInterval(pollRef.current);
        Analytics.checkoutSucceeded(purchasedPlan);
        // The trial has no success screen (handoff 08b) — the ₹2 only starts the
        // trial, the plan charges later, so proceed straight through.
        onDone();
      } else if (++polls >= MAX_POLLS) {
        if (pollRef.current) clearInterval(pollRef.current);
        setStage("plans");
        setBusy(false);
        setError("Payment is processing. If you approved it, this will activate in a moment — try refreshing.");
      }
    }, POLL_MS);
  }

  // ---- render ----

  const card = (
    <div className="flex w-full max-w-[480px] flex-col">
      {stage === "waiting" ? (
        <WaitingView onBack={() => { setStage("plans"); setBusy(false); }} />
      ) : (
        <div className="relative flex flex-1 flex-col px-5 pt-3 text-white">
          <div className="flex justify-end">
            <button
              type="button"
              onClick={() => { setFaq(true); Analytics.paywallFaqOpened(); }}
              className={cls("py-1 text-[14px] font-bold", PW.text)}
            >
              FAQs
            </button>
          </div>

          {/* Hero: 7 days FREE, then the ₹2 */}
          <div className={cls("text-center text-[12px] font-extrabold tracking-[0.16em]", PW.eyebrow)}>
            • YOUR LAUNDRY SHOP APP •
          </div>
          <h1 className="mt-3 text-center text-[19px] font-bold leading-snug">
            Start your {TRIAL_DAYS}-day{" "}
            <span className="rounded-md bg-orange px-1.5 py-0.5">FREE</span> trial for
          </h1>
          <div className={cls("bric mt-2 text-center text-[64px] leading-none", PW.accent)}>{rupees(trialR)}</div>
          <div className={cls("mt-2 text-center text-[11px] font-extrabold tracking-[0.12em]", PW.text)}>
            {rupees(trialR)} REFUNDED INSTANTLY
          </div>

          <div className="mt-5">
            <PaywallVideo paused={busy || faq} />
          </div>

          <div className="mt-6 grid grid-cols-4 gap-2">
            {TRIAL_FEATURES.map((f) => (
              <div key={f.label} className="flex flex-col items-center gap-2 text-center">
                <span className={cls("flex size-12 items-center justify-center rounded-full", PW.surface, PW.accent)}>
                  <f.Icon size={22} />
                </span>
                <span className="text-[12px] font-semibold leading-tight">{f.label}</span>
              </div>
            ))}
          </div>

          <div className="mt-7 text-center text-[16px] font-bold">Your plan after {TRIAL_DAYS} free days</div>
          <div className="mt-4 flex flex-col gap-3">
            <PlanCard
              selected={plan === "annual"}
              onSelect={() => setPlan("annual")}
              name="Yearly"
              note={`Only ${rupees(perMonth)} a month`}
              price={rupees(annualR)}
              per="/year"
              was={rupees(WAS_ANNUAL)}
              badge={`BEST VALUE · SAVE ${rupees(saveVsMonthly)}`}
            />
            <PlanCard
              selected={plan === "monthly"}
              onSelect={() => setPlan("monthly")}
              name="Monthly"
              note="Pay every month"
              price={rupees(monthlyR)}
              per="/month"
              was={rupees(WAS_MONTHLY)}
            />
          </div>

          <div className="mt-7 text-center text-[16px] font-bold">Works with your UPI app</div>
          <div className="mt-3 flex flex-wrap justify-center gap-2">
            {UPI_APPS.map((a) => (
              <span key={a} className="rounded-full bg-white px-3.5 py-1.5 text-[13px] font-bold text-ink">{a}</span>
            ))}
          </div>
          <div className={cls("mt-2 text-center text-[13px]", PW.text)}>and more</div>

          {error ? <p className="mt-3 text-center text-[13px] font-semibold text-[#FDBA74]">{error}</p> : null}

          {/* Sticky so the video above never pushes the CTA off-screen. */}
          <div className={cls("sticky bottom-0 -mx-5 mt-5 border-t border-white/10 px-5 pb-6 pt-3 backdrop-blur", PW.bar)}>
            <p className={cls("mb-2.5 text-center text-[12px]", PW.text)}>
              {`Autopays ${plan === "annual" ? `${rupees(annualR)}/year` : `${rupees(monthlyR)}/month`} after ${trialEnd} · cancel anytime before`}
            </p>
            <button
              type="button"
              onClick={() => void pay()}
              disabled={busy}
              className={cls(
                "h-14 w-full rounded-[14px] text-[16px] font-extrabold tracking-wide text-white transition-opacity disabled:opacity-60",
                PW.cta,
              )}
            >
              {busy ? "Starting…" : `START ${TRIAL_DAYS}-DAY FREE TRIAL`}
            </button>
          </div>
        </div>
      )}
    </div>
  );

  return (
    <div className={cls("flex min-h-dvh justify-center", stage === "plans" ? PW.bg : "bg-bg")}>
      {card}
      {faq ? (
        <AppSheet title="Questions" noSidebar onDismiss={() => setFaq(false)}>
          <div className="flex flex-col gap-4">
            <FaqItem q={`Why do I pay ${rupees(trialR)} today?`}>
              {`The ${rupees(trialR)} sets up UPI AutoPay for your plan, and it is refunded to you instantly. You get the full app free for ${TRIAL_DAYS} days.`}
            </FaqItem>
            <FaqItem q="When is my plan charged?">
              {`After ${trialEnd}, when your free days end: ${rupees(annualR)}/year or ${rupees(monthlyR)}/month, whichever you picked, by UPI AutoPay.`}
            </FaqItem>
            <FaqItem q="Can I cancel the trial?">
              Yes, you can cancel anytime during the trial. No charges will be applied if you cancel before the trial ends.
            </FaqItem>
            <FaqItem q="Which UPI apps work?">
              Any UPI app that supports AutoPay — GPay, PhonePe, Paytm, BHIM and more.
            </FaqItem>
          </div>
        </AppSheet>
      ) : null}
      {retrySheet && stage === "plans" ? (
        <AppSheet
          title={`Your ${TRIAL_DAYS} free days are waiting`}
          noSidebar
          onDismiss={() => {
            Analytics.paymentRetryDismissed(plan);
            hideRetry();
          }}
          leading={(
            <span className="flex size-14 shrink-0 items-center justify-center rounded-full bg-orangelight text-orange">
              <IcWarning size={28} />
            </span>
          )}
        >
          <p className="text-[15px] text-muted">
            The {rupees(trialR)} didn&apos;t go through, so nothing was charged. Try once more — the {rupees(trialR)} is refunded instantly and the full app opens right away.
          </p>
          {plan === "annual" ? (
            // A failed Yearly attempt: recommend the smaller Monthly plan.
            <>
              <div className="mt-4 rounded-2xl border-2 border-blue bg-bluelight px-4 py-3">
                <div className="text-[11px] font-bold tracking-[0.08em] text-blue">RECOMMENDED</div>
                <div className="mt-0.5 flex items-baseline justify-between gap-3">
                  <span className="text-[16px] font-bold text-bluetext">Try the Monthly plan</span>
                  <span className="bric text-[18px] text-bluetext">
                    {rupees(monthlyR)}<span className="text-[12px] font-normal">/month</span>
                  </span>
                </div>
                <div className="mt-0.5 text-[13px] text-bluetext">
                  {`Same ${TRIAL_DAYS} free days, ${rupees(trialR)} refunded instantly — then a smaller monthly payment.`}
                </div>
              </div>
              <PrimaryButton
                className="mt-4"
                onClick={() => {
                  Analytics.paymentRetryTapped("monthly", "annual");
                  setPlan("monthly");
                  void pay("monthly");
                }}
                disabled={busy}
              >
                Switch to Monthly
              </PrimaryButton>
              <button
                type="button"
                onClick={() => {
                  Analytics.paymentRetryTapped("annual");
                  void pay("annual");
                }}
                disabled={busy}
                className="mt-2 w-full py-3 text-[14px] font-bold text-muted"
              >
                Try Yearly again
              </button>
            </>
          ) : (
            <PrimaryButton
              className="mt-5"
              onClick={() => {
                Analytics.paymentRetryTapped(plan);
                void pay();
              }}
              disabled={busy}
            >
              Try again
            </PrimaryButton>
          )}
        </AppSheet>
      ) : null}
    </div>
  );
}

/**
 * Paywall intro video. Tries to autoplay WITH sound; browsers only allow that
 * after a tap on the page (Chrome carries the OTP "Verify" tap over, since the
 * Gate reaches the paywall by client-side navigation — a reload or iOS Safari
 * blocks it). If blocked it plays muted with a "Tap for sound" pill, which
 * unmutes and restarts from the beginning. Loops; paused while Checkout is open
 * or the tab is hidden; hidden entirely if the video fails to load.
 */
function PaywallVideo({ paused }: { paused: boolean }) {
  const ref = useRef<HTMLVideoElement>(null);
  const startedRef = useRef(false);
  const completedRef = useRef(false);
  const [muted, setMuted] = useState(false);
  const [playing, setPlaying] = useState(false);
  const [failed, setFailed] = useState(false);

  function started(wasMuted: boolean) {
    if (startedRef.current) return;
    startedRef.current = true;
    Analytics.paywallVideoStarted(wasMuted);
  }

  useEffect(() => {
    const v = ref.current;
    if (!v) return;
    let cancelled = false;
    v.muted = false;
    v.play().then(
      () => { if (!cancelled) started(false); },
      (e: unknown) => {
        // AbortError = paused/unmounted mid-start; only NotAllowedError means
        // "sound not allowed yet" → fall back to muted autoplay.
        if (cancelled || (e as Error)?.name !== "NotAllowedError") return;
        v.muted = true;
        setMuted(true);
        v.play().then(() => { if (!cancelled) started(true); }, () => {});
      },
    );
    const onVisibility = () => { if (document.hidden) v.pause(); };
    document.addEventListener("visibilitychange", onVisibility);
    return () => {
      cancelled = true;
      document.removeEventListener("visibilitychange", onVisibility);
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => { if (paused) ref.current?.pause(); }, [paused]);

  function play(fromStart: boolean) {
    const v = ref.current;
    if (!v) return;
    if (fromStart) v.currentTime = 0;
    void v.play().catch(() => {});
  }

  function setSound(on: boolean) {
    const v = ref.current;
    if (!v) return;
    v.muted = !on;
    setMuted(!on);
    if (on) Analytics.paywallVideoUnmuted();
  }

  // Tapping the video: first tap on a muted video turns sound on and restarts
  // it (they missed the audio); otherwise resume / pause.
  function onTap() {
    if (muted) { setSound(true); play(true); }
    else if (!playing) play(false);
    else ref.current?.pause();
  }

  if (failed) return null;

  return (
    <div className="relative aspect-video w-full overflow-hidden rounded-2xl bg-black">
      <video
        ref={ref}
        src={PAYWALL_VIDEO_URL}
        playsInline
        preload="auto"
        className="size-full object-cover"
        onClick={onTap}
        onPlay={() => setPlaying(true)}
        onPause={() => setPlaying(false)}
        loop
        // A looping video never fires "ended" — count the first time it reaches the end.
        onTimeUpdate={(e) => {
          const v = e.currentTarget;
          if (!completedRef.current && v.duration && v.currentTime >= v.duration - 0.5) {
            completedRef.current = true;
            Analytics.paywallVideoCompleted();
          }
        }}
        onError={() => setFailed(true)}
      />
      {muted ? (
        <button type="button" onClick={onTap} className="absolute left-2.5 top-2.5 flex items-center gap-1.5 rounded-full bg-black/70 px-3 py-1.5 text-[13px] font-bold text-white">
          <IcVolume size={16} />
          Tap for sound
        </button>
      ) : (
        <>
          {!playing ? (
            <button type="button" onClick={() => play(false)} aria-label="Play" className="absolute inset-0 flex items-center justify-center text-white">
              <span className="flex size-14 items-center justify-center rounded-full bg-black/60"><IcPlay size={26} /></span>
            </button>
          ) : null}
          <button type="button" onClick={() => setSound(false)} aria-label="Mute" className="absolute bottom-2 right-2 flex size-9 items-center justify-center rounded-full bg-black/60 text-white">
            <IcVolumeOff size={18} />
          </button>
        </>
      )}
    </div>
  );
}

function PlanCard({
  selected, onSelect, name, note, price, per, was, badge,
}: {
  selected: boolean;
  onSelect: () => void;
  name: string;
  note: string;
  price: string;
  per: string;
  was: string;
  badge?: string;
}) {
  return (
    <button
      type="button"
      onClick={onSelect}
      className={cls(
        "relative w-full rounded-2xl border-2 px-4 py-3 text-left text-white",
        selected ? "border-[#5B8CFF] bg-[#5B8CFF]/15" : "border-white/15 bg-white/[0.04]",
      )}
    >
      {badge ? (
        <span className="absolute -top-2.5 left-4 rounded-full bg-orange px-2 py-0.5 text-[10px] font-bold tracking-wide text-white">
          {badge}
        </span>
      ) : null}
      <div className="flex items-center gap-3">
        <span
          className={cls(
            "flex size-5 shrink-0 items-center justify-center rounded-full border-2",
            selected ? "border-[#8FB0FF]" : "border-white/30",
          )}
        >
          {selected ? <span className="size-2.5 rounded-full bg-[#8FB0FF]" /> : null}
        </span>
        <div className="min-w-0 flex-1">
          <div className="text-[16px] font-bold">{name}</div>
          <div className={cls("text-[13px]", PW.text)}>{note}</div>
        </div>
        <div className="text-right">
          <div className="bric text-[20px]">{price}</div>
          <div className={cls("text-[12px]", PW.text)}>
            <span className="line-through">{was}</span> {per}
          </div>
        </div>
      </div>
    </button>
  );
}

function FaqItem({ q, children }: { q: string; children: React.ReactNode }) {
  return (
    <div>
      <div className="text-[15px] font-bold">{q}</div>
      <p className="mt-1 text-[14px] text-muted">{children}</p>
    </div>
  );
}

function WaitingView({ onBack }: { onBack: () => void }) {
  return (
    <div className="flex flex-1 flex-col items-center justify-center gap-4 p-8 text-center">
      <div className="size-10 animate-spin rounded-full border-4 border-cardborder border-t-blue" />
      <h2 className="bric text-[22px]">Confirming your subscription…</h2>
      <p className="text-[14px] text-muted">We&apos;re confirming the UPI AutoPay approval. This updates automatically — it only takes a moment.</p>
      <p className="text-[13px] text-faint">Waiting for confirmation…</p>
      <button type="button" onClick={onBack} className="text-[14px] font-bold text-blue">
        Back to plans
      </button>
    </div>
  );
}

/** Already-subscribed state (opened from Settings): plan summary + cancel (inline confirm). */
export function ManagePlanScreen({ onClose }: { onClose: () => void }) {
  const sub = useBillingStore((s) => s.status?.subscription ?? null);
  const refresh = useBillingStore((s) => s.refresh);
  const [confirming, setConfirming] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const planName = sub?.plan === "annual" ? "Yearly" : "Monthly";
  const next = sub?.chargeAt ? fmtDate(sub.chargeAt) : null;

  async function cancel() {
    if (busy) return;
    setBusy(true);
    setError(null);
    try {
      Analytics.subscriptionCancelRequested(sub?.plan ?? "monthly");
      await cancelSubscription();
      void refresh();
      onClose();
    } catch (e) {
      setError(e instanceof Error ? e.message : "Could not cancel — try again");
      setBusy(false);
    }
  }

  return (
    <div className="flex min-h-dvh justify-center bg-bg">
      <div className="flex w-full max-w-[480px] flex-1 flex-col overflow-y-auto">
        <div className="flex items-center justify-between px-5 pt-4">
          <button type="button" onClick={onClose} aria-label="Close" className="flex size-9 items-center justify-center rounded-full text-[22px] leading-none text-muted hover:bg-neutralfill">
            ✕
          </button>
        </div>
        <div className="flex flex-1 flex-col items-center gap-4 p-6 text-center">
          <div className="mt-2 flex size-16 items-center justify-center rounded-full bg-blue text-ondark">
            <IcCheck size={34} />
          </div>
          <h2 className="bric text-[24px]">{planName} plan is active</h2>
          <p className="text-[14px] text-muted">Unlimited orders. Nothing to do here.</p>
          <div className="w-full rounded-2xl border border-cardborder bg-card">
            <SummaryRow k="Plan" v={planName} />
            <SummaryRow k="Amount" v={`${rupees(Math.round((sub?.amount ?? 0) / 100))}${sub?.plan === "annual" ? "/year" : "/month"}`} />
            {next ? <SummaryRow k="Next charge" v={next} /> : null}
            <SummaryRow k="Status" v={sub?.status === "pending" ? "Payment retrying" : "Active"} last />
          </div>
          {sub?.trialAmount ? (
            <p className="text-[12px] text-muted">Started with the ₹2 trial.</p>
          ) : null}
          {error ? <p className="text-[13px] font-semibold text-orangetext">{error}</p> : null}
          <div className="mt-auto w-full">
            {confirming ? (
              <div className="flex flex-col gap-2">
                <p className="text-[14px] font-semibold">Cancel the plan? New orders stop when it ends.</p>
                <div className="flex gap-2">
                  <button
                    type="button"
                    onClick={cancel}
                    disabled={busy}
                    className="h-[52px] flex-1 rounded-[14px] border-[1.5px] border-orange text-[15px] font-bold text-orangetext"
                  >
                    {busy ? "Cancelling…" : "Yes, cancel"}
                  </button>
                  <PrimaryButton className="flex-1" onClick={() => setConfirming(false)}>Keep plan</PrimaryButton>
                </div>
              </div>
            ) : (
              <button type="button" onClick={() => setConfirming(true)} className="w-full py-3 text-[14px] font-bold text-muted">
                Cancel subscription
              </button>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}

function SummaryRow({ k, v, last }: { k: string; v: string; last?: boolean }) {
  return (
    <div className={cls("flex items-center justify-between px-4 py-3", last ? "" : "border-b border-divider")}>
      <span className="text-[14px] text-muted">{k}</span>
      <span className="text-[15px] font-bold">{v}</span>
    </div>
  );
}

const MONTHS = ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"];

// "2 Oct" from today + offsetDays.
function fmtDayMonth(offsetDays: number): string {
  const [, m, d] = AppDate.add(AppDate.today(), offsetDays).split("-");
  return `${parseInt(d, 10)} ${MONTHS[parseInt(m, 10) - 1]}`;
}

// "25 Sep 2027" from an ISO timestamp.
function fmtDate(iso: string): string {
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return "";
  return `${d.getDate()} ${MONTHS[d.getMonth()]} ${d.getFullYear()}`;
}
