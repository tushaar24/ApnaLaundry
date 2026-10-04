"use client";

import { useEffect, useMemo, useRef, useState } from "react";
import * as AppDate from "@/core/appdate";
import { rupees } from "@/core/money";
import { cancelSubscription, openSubscriptionCheckout, subscribe, type BillingStatus } from "@/data/billing";
import { paywallInfo, useBillingStore } from "@/data/billingStore";
import { Analytics } from "@/analytics/events";
import { cls, PrimaryButton, SectionLabel } from "@/ui/basics";
import { IcBook, IcChart, IcChat, IcCheck, IcReceipt } from "@/ui/icons";

/**
 * Paywall / plans screen for the A/B paywall flow, built from the handoff
 * (docs 08-subscription). Mobile is the full-screen layout from the design;
 * desktop is a centred modal card. "Pay" creates the Razorpay subscription and
 * opens Razorpay Checkout for the UPI AutoPay approval (Razorpay owns that step:
 * its own UPI app picker on mobile, QR on desktop); on success we poll status.
 */

type Plan = "annual" | "monthly";
type Stage = "plans" | "waiting" | "done";

// Launch "was" prices (struck-through) from the design — marketing only.
const WAS_MONTHLY = 799;
const WAS_ANNUAL = 8999;

const FEATURES = ["Unlimited orders", "Bills on WhatsApp", "Khata for every customer", "Daily earnings — cash and UPI"];

// backend TRIAL_DAYS (laundry-razorpay.js)
const TRIAL_DAYS = 7;

// The trial paywall lists features with a subtitle each (design 08b).
const TRIAL_FEATURES: { Icon: typeof IcReceipt; title: string; sub: string }[] = [
  { Icon: IcReceipt, title: "Unlimited orders", sub: "Walk-in, home pickup and delivery" },
  { Icon: IcChat, title: "Bills on WhatsApp", sub: "Make and send a bill in one tap" },
  { Icon: IcBook, title: "Khata for every customer", sub: "Always know who owes you money" },
  { Icon: IcCheck, title: "“Clothes ready” message", sub: "Customers get a WhatsApp automatically" },
  { Icon: IcChart, title: "Daily earnings", sub: "Cash and UPI added up for you" },
];

export function PaywallScreen({
  reason, onClose, onDone, hardGate = false, setupPending = false,
}: {
  reason: "limit" | "trial" | "upsell";
  onClose: () => void;
  onDone: (continuing: boolean) => void;
  /** Non-cancellable gate (trial variant): no close button, app not reachable. */
  hardGate?: boolean;
  /** Brand-new shop hitting the gate before setup — the Done CTA leads to setup, not a new order. */
  setupPending?: boolean;
}) {
  const status = useBillingStore((s) => s.status);
  const refresh = useBillingStore((s) => s.refresh);
  const info = paywallInfo(status);

  const [plan, setPlan] = useState<Plan>("annual");
  const [stage, setStage] = useState<Stage>("plans");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  // The plan the SERVER put on the subscription (trial_2 is always monthly,
  // whatever was tapped) — Done/analytics must use this, not the local pick.
  const [purchased, setPurchased] = useState<Plan>("annual");
  const pollRef = useRef<ReturnType<typeof setInterval> | null>(null);

  const annual = status?.plans.annual.amount ?? 499900;
  const monthly = status?.plans.monthly.amount ?? 49900;
  const annualR = Math.round(annual / 100);
  const monthlyR = Math.round(monthly / 100);
  const trialR = Math.round((status?.plans.trial.amount ?? 200) / 100);
  const perMonth = Math.round(annual / 12 / 100);
  const saveVsMonthly = Math.round((monthly * 12 - annual) / 100);
  const yearIfMonthly = Math.round((monthly * 12) / 100);

  const blocked = reason === "limit" || info.freeLeft <= 0;
  const left = info.freeLeft;
  const headline = info.isTrial
    ? "Start taking orders"
    : blocked
      ? "Your free orders are finished"
      : left === 1
        ? "Only 1 free order left"
        : `Only ${left} free orders left`;
  const subline = info.isTrial
    ? "Try everything for ₹2. Your plan starts after the trial — cancel anytime."
    : blocked
      ? "Pick a plan to take new orders. Your old orders and khata are safe."
      : "Pick a plan and keep taking orders without a break.";

  // Fire Paywall Shown once.
  useEffect(() => {
    Analytics.paywallShown(info.variant, info.orderCount);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // Stop polling on unmount.
  useEffect(() => () => { if (pollRef.current) clearInterval(pollRef.current); }, []);

  const tillAnnual = useMemo(() => fmtTill(365), []);
  const renewMonthly = useMemo(() => fmtTill(30), []);
  const trialStart = useMemo(() => fmtTill(TRIAL_DAYS), []);

  async function pay() {
    if (busy) return;
    setBusy(true);
    setError(null);
    Analytics.planSelected(info.variant, plan);
    try {
      const res = await subscribe(plan);
      // The server decides the real plan/amount (never trust the client for money).
      const serverPlan: Plan = res.plan === "annual" ? "annual" : "monthly";
      setPurchased(serverPlan);
      Analytics.checkoutStarted(info.variant, res.plan, res.amount, res.trialAmount || 0);
      // Razorpay Checkout owns the UPI AutoPay approval (its own app picker on
      // mobile, QR on desktop). On success we wait for the webhook to confirm.
      await openSubscriptionCheckout(res, {
        onSuccess: () => { setStage("waiting"); startPolling(res.plan); },
        onDismiss: () => { setBusy(false); },
        onError: (msg) => {
          setError(msg);
          Analytics.checkoutFailed(info.variant, res.plan, msg);
          setBusy(false);
        },
      });
    } catch (e) {
      setError(e instanceof Error ? e.message : "Could not start — try again");
      Analytics.checkoutFailed(info.variant, plan, e instanceof Error ? e.message : "error");
      setBusy(false);
      // e.g. ALREADY_SUBSCRIBED from another device — refetch so the active
      // state renders instead of a broken Pay button.
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
        Analytics.checkoutSucceeded(info.variant, purchasedPlan);
        setStage("done");
        setBusy(false);
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
      {stage === "done" ? (
        <DoneView
          plan={purchased}
          paid={info.isTrial ? trialR : purchased === "annual" ? annualR : monthlyR}
          isTrial={info.isTrial}
          till={info.isTrial ? trialStart : purchased === "annual" ? tillAnnual : renewMonthly}
          renewLabel={info.isTrial ? "Plan starts" : purchased === "annual" ? "Valid till" : "Renews on"}
          blocked={blocked}
          setupPending={setupPending}
          onDone={onDone}
        />
      ) : stage === "waiting" ? (
        <WaitingView onBack={() => { setStage("plans"); setBusy(false); }} />
      ) : info.hasActive ? (
        // Already subscribed (e.g. opened from Settings): show the plan, not a
        // Pay button — Checkout can't re-authorize an active subscription.
        <ActiveView
          sub={status?.subscription ?? null}
          variant={info.variant}
          onClose={onClose}
          onCancelled={() => void refresh()}
        />
      ) : (
        <div className="relative flex flex-1 flex-col justify-center px-5 py-6">
          {!hardGate ? (
            <button type="button" onClick={onClose} aria-label="Close" className="absolute left-3 top-3 flex size-9 items-center justify-center rounded-full text-[22px] leading-none text-muted hover:bg-neutralfill">
              ✕
            </button>
          ) : null}

          {info.isTrial ? (
            <>
              {/* ₹2 trial hero */}
              <div className="rounded-2xl bg-blue px-4 py-3.5 text-ondark">
                <div className="text-[11px] font-bold tracking-[0.08em] text-bluebar">{TRIAL_DAYS}-DAY FULL TRIAL</div>
                <div className="mt-1 flex items-center gap-4">
                  <span className="bric text-[40px] leading-none">{rupees(trialR)}</span>
                  <div className="min-w-0 flex-1">
                    <div className="text-[16px] font-bold leading-snug">That&apos;s all you pay today</div>
                    <div className="text-[13px] text-bluebar">Every feature unlocked for {TRIAL_DAYS} days</div>
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

              <SectionLabel text={`Your plan after ${TRIAL_DAYS} days`} className="mt-4" />
              <div className="mt-2 flex flex-col gap-2">
                <PlanCard
                  dense
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
                  dense
                  selected={plan === "monthly"}
                  onSelect={() => setPlan("monthly")}
                  name="Monthly"
                  note="Pay every month"
                  price={rupees(monthlyR)}
                  per="/month"
                  was={rupees(WAS_MONTHLY)}
                />
              </div>
            </>
          ) : (
            <>
              <div className="text-[13px] font-bold text-orangetext">
                {info.orderCount} of {info.freeThreshold} free orders used
              </div>
              <h1 className="bric mt-1 text-[26px] leading-tight">{headline}</h1>
              <p className="mt-1.5 text-[15px] text-muted">{subline}</p>

              <ul className="mt-4 flex flex-col gap-2.5">
                {FEATURES.map((f) => (
                  <li key={f} className="flex items-center gap-2.5">
                    <span className="flex size-5 shrink-0 items-center justify-center rounded-full bg-bluelight text-blue">
                      <IcCheck size={14} />
                    </span>
                    <span className="text-[15px] font-semibold">{f}</span>
                  </li>
                ))}
              </ul>

              <div className="mt-5 flex flex-col gap-2.5">
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

              <p className={cls("mt-3 text-[13px] font-bold", plan === "annual" ? "text-blue" : "text-orangetext")}>
                {plan === "annual"
                  ? `You save ${rupees(saveVsMonthly)} vs paying monthly (${rupees(yearIfMonthly)} a year)`
                  : `Pick Yearly and save ${rupees(saveVsMonthly)}`}
              </p>
            </>
          )}

          {error ? <p className="mt-2 text-[13px] font-semibold text-orangetext">{error}</p> : null}

          <PrimaryButton className="mt-5" onClick={pay} disabled={busy}>
            {busy
              ? "Starting…"
              : info.isTrial
                ? `Start trial for ${rupees(trialR)}`
                : plan === "annual"
                  ? `Pay ${rupees(annualR)} for 1 year`
                  : `Pay ${rupees(monthlyR)} for 1 month`}
          </PrimaryButton>
          <p className="mt-2 text-center text-[12px] text-muted">
            {info.isTrial
              ? `Then ${rupees(plan === "annual" ? annualR : monthlyR)}/${plan === "annual" ? "year" : "month"} from ${trialStart} by UPI AutoPay. Cancel anytime before.`
              : plan === "annual"
                ? `Valid till ${tillAnnual} · pay by UPI`
                : `Renews on ${renewMonthly} · pay by UPI`}
          </p>
        </div>
      )}
    </div>
  );

  return (
    <div className="flex min-h-dvh justify-center bg-bg">
      {card}
    </div>
  );
}

function PlanCard({
  selected, onSelect, name, note, price, per, was, badge, dense,
}: {
  selected: boolean;
  onSelect: () => void;
  name: string;
  note: string;
  price: string;
  per: string;
  was: string;
  badge?: string;
  dense?: boolean;
}) {
  return (
    <button
      type="button"
      onClick={onSelect}
      className={cls(
        "relative w-full rounded-2xl border-2 bg-card px-4 text-left",
        dense ? "py-2.5" : "py-3.5",
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

function DoneView({
  plan, paid, isTrial, till, renewLabel, blocked, setupPending, onDone,
}: {
  plan: Plan;
  paid: number;
  isTrial: boolean;
  till: string;
  renewLabel: string;
  blocked: boolean;
  setupPending: boolean;
  onDone: (continuing: boolean) => void;
}) {
  const title = isTrial
    ? "Your trial has started"
    : plan === "annual" ? "Yearly plan is active" : "Monthly plan is active";
  return (
    <div className="flex flex-1 flex-col items-center gap-4 overflow-y-auto p-6 text-center">
      <div className="mt-4 flex size-16 items-center justify-center rounded-full bg-blue text-ondark">
        <IcCheck size={34} />
      </div>
      <h2 className="bric text-[24px]">{title}</h2>
      <p className="text-[14px] text-muted">Take as many orders as you want. No more limits.</p>
      <div className="w-full rounded-2xl border border-cardborder bg-card">
        <SummaryRow k="Plan" v={plan === "annual" ? "Yearly" : "Monthly"} />
        <SummaryRow k="Paid" v={isTrial ? `${rupees(paid)} (trial)` : rupees(paid)} />
        <SummaryRow k={renewLabel} v={till} />
        <SummaryRow k="Orders" v="Unlimited" last />
      </div>
      <p className="text-[12px] text-muted">The receipt is also sent to you on WhatsApp.</p>
      <div className="mt-2 w-full">
        <PrimaryButton onClick={() => onDone(blocked && !setupPending)}>
          {setupPending ? "Set up your shop" : blocked ? "+ Continue with new order" : "Go to my orders"}
        </PrimaryButton>
      </div>
    </div>
  );
}

/** Already-subscribed state: plan summary + cancel (inline confirm). */
function ActiveView({
  sub, variant, onClose, onCancelled,
}: {
  sub: BillingStatus["subscription"];
  variant: string;
  onClose: () => void;
  onCancelled: () => void;
}) {
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
      onCancelled();
      onClose();
    } catch (e) {
      setError(e instanceof Error ? e.message : "Could not cancel — try again");
      setBusy(false);
    }
  }

  return (
    <div className="flex flex-1 flex-col overflow-y-auto">
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
        {sub?.trialAmount && variant === "trial_2" ? (
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
