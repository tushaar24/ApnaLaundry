"use client";

import { useEffect, useMemo, useRef, useState } from "react";
import * as AppDate from "@/core/appdate";
import { rupees } from "@/core/money";
import { cancelSubscription, openSubscriptionCheckout, preloadCheckout, subscribe } from "@/data/billing";
import { paywallInfo, useBillingStore } from "@/data/billingStore";
import { Analytics } from "@/analytics/events";
import { MetaPixel } from "@/analytics/metaPixel";
import { cls, PrimaryButton, SectionLabel } from "@/ui/basics";
import { IcBook, IcChart, IcChat, IcCheck, IcReceipt, IcWarning } from "@/ui/icons";
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
const TRIAL_DAYS = 1;
const TRIAL_PERIOD = TRIAL_DAYS === 1 ? "1 day" : `${TRIAL_DAYS} days`;

// The trial paywall lists features with a subtitle each (design 08b).
const TRIAL_FEATURES: { Icon: typeof IcReceipt; title: string; sub: string }[] = [
  { Icon: IcReceipt, title: "Unlimited orders", sub: "Walk-in, home pickup and delivery" },
  { Icon: IcChat, title: "Bills on WhatsApp", sub: "Make and send a bill in one tap" },
  { Icon: IcBook, title: "Khata for every customer", sub: "Always know who owes you money" },
  { Icon: IcCheck, title: "“Clothes ready” message", sub: "Customers get a WhatsApp automatically" },
  { Icon: IcChart, title: "Daily earnings", sub: "Cash and UPI added up for you" },
];

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
  // Razorpay Checkout closed without paying (failed, cancelled or Back) —
  // the "try again" sheet over the plans.
  const [retrySheet, setRetrySheet] = useState(false);
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

  const trialStart = useMemo(() => fmtTill(TRIAL_DAYS), []);

  async function pay() {
    if (busy) return;
    setBusy(true);
    setError(null);
    setRetrySheet(false);
    Analytics.planSelected(plan);
    // If the mount-time warm-up failed or hasn't finished, this restarts the
    // checkout.js load in parallel with the subscribe call instead of after it.
    preloadCheckout();
    try {
      // The server decides the real plan/amount (never trust the client for money).
      const res = await subscribe(plan);
      Analytics.checkoutStarted(res.plan, res.amount, res.trialAmount || 0);
      // Value in rupees: what this approval charges now (the ₹2 trial).
      const chargeRupees = (res.trialAmount || res.amount) / 100;
      MetaPixel.subscriptionInitiated(res.subscriptionId, res.plan, chargeRupees);
      // Razorpay Checkout owns the UPI AutoPay approval (its own app picker on
      // mobile, QR on desktop). On success we wait for the webhook to confirm.
      await openSubscriptionCheckout(res, {
        onSuccess: () => {
          MetaPixel.subscriptionActivated(res.subscriptionId, res.plan, chargeRupees);
          setRetrySheet(false);
          setStage("waiting");
          startPolling(res.plan);
        },
        onDismiss: () => {
          setBusy(false);
          setRetrySheet(true);
        },
        // Checkout stays open on a failed payment (Razorpay offers its own
        // retry); the sheet sits behind it and is there once it's closed.
        onError: (msg) => {
          Analytics.checkoutFailed(res.plan, msg);
          setBusy(false);
          setRetrySheet(true);
        },
      });
    } catch (e) {
      setError(e instanceof Error ? e.message : "Could not start — try again");
      Analytics.checkoutFailed(plan, e instanceof Error ? e.message : "error");
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
      await refresh();
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
        <div className="relative flex flex-1 flex-col justify-center px-5 py-6">
          {/* ₹2 trial hero */}
          <div className="rounded-2xl bg-blue px-4 py-3.5 text-ondark">
            <div className="text-[11px] font-bold tracking-[0.08em] text-bluebar">{TRIAL_DAYS}-DAY FULL TRIAL</div>
            <div className="mt-1 flex items-center gap-4">
              <span className="bric text-[40px] leading-none">{rupees(trialR)}</span>
              <div className="min-w-0 flex-1">
                <div className="text-[16px] font-bold leading-snug">That&apos;s all you pay today</div>
                <div className="text-[13px] text-bluebar">Every feature unlocked for {TRIAL_PERIOD}</div>
              </div>
            </div>
          </div>

          <SectionLabel text="Everything in the app" className="mt-4" />
          <div className="mt-2 flex flex-col gap-1.5">
            {TRIAL_FEATURES.map((f) => (
              <div key={f.title} className="flex items-center gap-2.5">
                <span className="flex size-9 shrink-0 items-center justify-center rounded-[10px] bg-bluelight text-blue">
                  <f.Icon size={18} />
                </span>
                <div className="min-w-0">
                  <div className="text-[15px] font-bold leading-tight">{f.title}</div>
                  <div className="text-[12px] text-muted leading-tight">{f.sub}</div>
                </div>
              </div>
            ))}
          </div>

          <SectionLabel text={`Your plan after ${TRIAL_PERIOD}`} className="mt-4" />
          <div className="mt-2 flex flex-col gap-2">
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

          {error ? <p className="mt-2 text-[13px] font-semibold text-orangetext">{error}</p> : null}

          <PrimaryButton className="mt-5" onClick={pay} disabled={busy}>
            {busy ? "Starting…" : `Start trial for ${rupees(trialR)}`}
          </PrimaryButton>
          <p className="mt-2 text-center text-[12px] text-muted">
            {`Then ${rupees(plan === "annual" ? annualR : monthlyR)}/${plan === "annual" ? "year" : "month"} from ${trialStart} by UPI AutoPay. Cancel anytime before.`}
          </p>
        </div>
      )}
    </div>
  );

  return (
    <div className="flex min-h-dvh justify-center bg-bg">
      {card}
      {retrySheet && stage === "plans" ? (
        <AppSheet
          title={`Your ${TRIAL_DAYS} free days are waiting`}
          noSidebar
          onDismiss={() => setRetrySheet(false)}
          leading={(
            <span className="flex size-14 shrink-0 items-center justify-center rounded-full bg-orangelight text-orange">
              <IcWarning size={28} />
            </span>
          )}
        >
          <p className="text-[15px] text-muted">
            The {rupees(trialR)} didn&apos;t go through. Nothing was charged — try once more and the full app opens right away.
          </p>
          <PrimaryButton className="mt-5" onClick={pay} disabled={busy}>
            {`Try again · Pay ${rupees(trialR)}`}
          </PrimaryButton>
        </AppSheet>
      ) : null}
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
        "relative w-full rounded-2xl border-2 bg-card px-4 py-2.5 text-left",
        selected ? "border-blue" : "border-cardborder",
      )}
    >
      {badge ? (
        <span className="absolute -top-2.5 left-4 rounded-full bg-orange px-2 py-0.5 text-[10px] font-bold tracking-wide text-ondark">
          {badge}
        </span>
      ) : null}
      <div className="flex items-center gap-3">
        <span
          className={cls(
            "flex size-5 shrink-0 items-center justify-center rounded-full border-2",
            selected ? "border-blue" : "border-cardborder",
          )}
        >
          {selected ? <span className="size-2.5 rounded-full bg-blue" /> : null}
        </span>
        <div className="min-w-0 flex-1">
          <div className="text-[16px] font-bold">{name}</div>
          <div className="text-[13px] text-muted">{note}</div>
        </div>
        <div className="text-right">
          <div className="bric text-[20px]">{price}</div>
          <div className="text-[12px] text-muted">
            <span className="line-through">{was}</span> {per}
          </div>
        </div>
      </div>
    </button>
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

// "25 Sep 2027" from today + offsetDays.
function fmtTill(offsetDays: number): string {
  const [y, m, d] = AppDate.add(AppDate.today(), offsetDays).split("-");
  return `${parseInt(d, 10)} ${MONTHS[parseInt(m, 10) - 1]} ${y}`;
}

// "25 Sep 2027" from an ISO timestamp.
function fmtDate(iso: string): string {
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return "";
  return `${d.getDate()} ${MONTHS[d.getMonth()]} ${d.getFullYear()}`;
}
