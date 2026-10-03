"use client";

import { useEffect, useMemo, useRef, useState } from "react";
import * as AppDate from "@/core/appdate";
import { rupees } from "@/core/money";
import { cancelSubscription, openSubscriptionCheckout, subscribe } from "@/data/billing";
import { paywallInfo, useBillingStore } from "@/data/billingStore";
import { Analytics } from "@/analytics/events";
import { cls, PrimaryButton } from "@/ui/basics";
import { IcCheck } from "@/ui/icons";

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

export function PaywallScreen({
  reason, onClose, onDone,
}: {
  reason: "limit" | "trial" | "upsell";
  onClose: () => void;
  onDone: (continuing: boolean) => void;
}) {
  const status = useBillingStore((s) => s.status);
  const refresh = useBillingStore((s) => s.refresh);
  const info = paywallInfo(status);

  const [plan, setPlan] = useState<Plan>("annual");
  const [stage, setStage] = useState<Stage>("plans");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const pollRef = useRef<ReturnType<typeof setInterval> | null>(null);

  const annual = status?.plans.annual.amount ?? 499900;
  const monthly = status?.plans.monthly.amount ?? 49900;
  const annualR = Math.round(annual / 100);
  const monthlyR = Math.round(monthly / 100);
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

  async function pay() {
    if (busy) return;
    setBusy(true);
    setError(null);
    Analytics.planSelected(info.variant, plan);
    try {
      const res = await subscribe(plan);
      Analytics.checkoutStarted(info.variant, plan, plan === "annual" ? annual : monthly, res.trialAmount || 0);
      // Razorpay Checkout owns the UPI AutoPay approval (its own app picker on
      // mobile, QR on desktop). On success we wait for the webhook to confirm.
      await openSubscriptionCheckout(res, {
        onSuccess: () => { setStage("waiting"); startPolling(); },
        onDismiss: () => { setBusy(false); },
        onError: (msg) => {
          setError(msg);
          Analytics.checkoutFailed(info.variant, plan, msg);
          setBusy(false);
        },
      });
    } catch (e) {
      setError(e instanceof Error ? e.message : "Could not start — try again");
      Analytics.checkoutFailed(info.variant, plan, e instanceof Error ? e.message : "error");
      setBusy(false);
    }
  }

  function startPolling() {
    if (pollRef.current) clearInterval(pollRef.current);
    pollRef.current = setInterval(async () => {
      await refresh();
      const s = useBillingStore.getState().status;
      if (s?.hasActiveSubscription) {
        if (pollRef.current) clearInterval(pollRef.current);
        Analytics.checkoutSucceeded(info.variant, plan);
        setStage("done");
        setBusy(false);
      }
    }, 3000);
  }

  // ---- render ----

  const card = (
    <div className="flex max-h-[92dvh] w-full flex-col overflow-hidden bg-bg lg:max-h-[88dvh] lg:w-[460px] lg:rounded-3xl lg:border lg:border-cardborder lg:shadow-2xl">
      {stage === "done" ? (
        <DoneView
          plan={plan}
          paid={plan === "annual" ? annualR : monthlyR}
          till={plan === "annual" ? tillAnnual : renewMonthly}
          renewLabel={plan === "annual" ? "Valid till" : "Renews on"}
          blocked={blocked}
          onDone={onDone}
        />
      ) : stage === "waiting" ? (
        <WaitingView onBack={() => { setStage("plans"); setBusy(false); }} />
      ) : (
        <>
          <div className="flex items-center justify-between px-5 pt-4">
            <button type="button" onClick={onClose} aria-label="Close" className="flex size-9 items-center justify-center rounded-full text-[22px] leading-none text-muted hover:bg-neutralfill">
              ✕
            </button>
          </div>
          <div className="flex-1 overflow-y-auto px-5 pb-4">
            {!info.isTrial ? (
              <div className="text-[13px] font-bold text-orangetext">
                {info.orderCount} of {info.freeThreshold} free orders used
              </div>
            ) : null}
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
            {error ? <p className="mt-2 text-[13px] font-semibold text-orangetext">{error}</p> : null}
          </div>

          <div className="border-t border-divider bg-card px-5 py-4">
            <PrimaryButton onClick={pay} disabled={busy}>
              {busy
                ? "Starting…"
                : info.isTrial
                  ? "Start trial for ₹2"
                  : plan === "annual"
                    ? `Pay ${rupees(annualR)} for 1 year`
                    : `Pay ${rupees(monthlyR)} for 1 month`}
            </PrimaryButton>
            <p className="mt-2 text-center text-[12px] text-muted">
              {info.isTrial
                ? `Then ${rupees(plan === "annual" ? annualR : monthlyR)} by UPI AutoPay · cancel anytime`
                : plan === "annual"
                  ? `Valid till ${tillAnnual} · pay by UPI`
                  : `Renews on ${renewMonthly} · pay by UPI`}
            </p>
          </div>
        </>
      )}
    </div>
  );

  return (
    <div className="fixed inset-0 z-50 flex items-stretch justify-center lg:items-center lg:bg-[rgba(22,25,33,0.45)] lg:p-4 lg:pl-[240px]">
      {card}
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
        "relative w-full rounded-2xl border-2 bg-card px-4 py-3.5 text-left",
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
  plan, paid, till, renewLabel, blocked, onDone,
}: {
  plan: Plan;
  paid: number;
  till: string;
  renewLabel: string;
  blocked: boolean;
  onDone: (continuing: boolean) => void;
}) {
  return (
    <div className="flex flex-1 flex-col items-center gap-4 overflow-y-auto p-6 text-center">
      <div className="mt-4 flex size-16 items-center justify-center rounded-full bg-blue text-ondark">
        <IcCheck size={34} />
      </div>
      <h2 className="bric text-[24px]">{plan === "annual" ? "Yearly plan is active" : "Monthly plan is active"}</h2>
      <p className="text-[14px] text-muted">Take as many orders as you want. No more limits.</p>
      <div className="w-full rounded-2xl border border-cardborder bg-card">
        <SummaryRow k="Plan" v={plan === "annual" ? "Yearly" : "Monthly"} />
        <SummaryRow k="Paid" v={rupees(paid)} />
        <SummaryRow k={renewLabel} v={till} />
        <SummaryRow k="Orders" v="Unlimited" last />
      </div>
      <p className="text-[12px] text-muted">The receipt is also sent to you on WhatsApp.</p>
      <div className="mt-2 w-full">
        <PrimaryButton onClick={() => onDone(blocked)}>
          {blocked ? "+ Continue with new order" : "Go to my orders"}
        </PrimaryButton>
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

export { cancelSubscription };
