"use client";

import { useState } from "react";
import * as AppDate from "@/core/appdate";
import { grouping, rupees } from "@/core/money";
import { computeEarnings } from "@/domain/earningsMath";
import { amtOf } from "@/domain/laundryMath";
import type { LaundryState, LedgerEntry, Order, OrderStatus } from "@/domain/models";
import * as Sel from "@/domain/selectors";
import { useAppStore, useLaundryState } from "@/data/store";
import { Analytics } from "@/analytics/events";
import { useScreenView } from "@/analytics/useScreenView";
import { AppCard, cls, DateTimeBox, Divider, PillChip } from "@/ui/basics";
import { IcCalendar, IcChart, IcChevronRight, IcEye, IcEyeOff, IcShare } from "@/ui/icons";
import { SheetHost } from "@/ui/sheets/host";
import type { ActiveSheet } from "@/ui/sheets/types";
import { Shell, useNav } from "@/ui/shell";

/** Earnings — port of ui/screens/earnings/EarningsScreen.kt. */

export default function EarningsPage() {
  return (
    <Shell tab="earnings">
      <EarningsScreen />
    </Shell>
  );
}

function startOfWeekIso(todayIso: string): string {
  // Monday-start week, like the app's "This week".
  const dow = (() => {
    const [y, m, d] = todayIso.split("-").map((v) => parseInt(v, 10));
    return new Date(Date.UTC(y, m - 1, d, 12)).getUTCDay(); // Sun=0
  })();
  const back = dow === 0 ? 6 : dow - 1;
  return AppDate.add(todayIso, -back);
}

function EarningsScreen() {
  const state = useLaundryState();
  const hidden = useAppStore((s) => s.hideAmounts);
  const toggleHide = useAppStore((s) => s.toggleHideAmounts);
  const nav = useNav();
  const [period, setPeriod] = useState<"today" | "week" | "month" | "all" | "custom">("today");
  // Custom range: last 7 days until the owner picks their own.
  const [customFrom, setCustomFrom] = useState(() => AppDate.add(AppDate.today(), -6));
  const [customTo, setCustomTo] = useState(() => AppDate.today());
  const [payFilter, setPayFilter] = useState<"all" | "cash" | "upi">("all");
  const [showAllOrders, setShowAllOrders] = useState(false);
  const [active, setActive] = useState<ActiveSheet | null>(null);
  useScreenView("earnings");

  const today = AppDate.today();
  const weekStart = startOfWeekIso(today);
  const monthStart = today.slice(0, 8) + "01";
  const inRange = (iso: string): boolean =>
    period === "all" ? true
      : period === "custom" ? iso >= customFrom && iso <= customTo
      : period === "week" ? iso >= weekStart && iso <= today
        : period === "month" ? iso >= monthStart && iso <= today
          : iso === today;

  const e = computeEarnings(state, inRange);
  const m = (n: number): string => (hidden ? "₹ ••••" : rupees(n));

  const periodLabel =
    period === "week"
      ? `${AppDate.plain(weekStart)} – ${AppDate.plain(today)}`
      : period === "month"
        ? `1 – ${AppDate.dayOfMonth(today)} ${AppDate.plain(today).split(" ").pop()}`
        : period === "all" ? "All time"
          : period === "custom"
            ? customFrom === customTo ? AppDate.plain(customFrom) : `${AppDate.plain(customFrom)} – ${AppDate.plain(customTo)}`
            : AppDate.plain(today);
  const periodTitle =
    period === "week" ? "This week"
      : period === "month" ? "This month"
        : period === "all" ? "All time"
          : period === "custom" ? periodLabel : "Today";

  // Orders in the period by their own date (same as Home's Orders / Sales
  // tiles); live orders only — a cancelled one is listed but never counted.
  const periodOrders = state.orders
    .filter((o) => inRange(o.pickupDate))
    .sort((a, b) => (a.pickupDate < b.pickupDate ? 1 : a.pickupDate > b.pickupDate ? -1 : b.id - a.id));
  const counted = periodOrders.filter((o) => o.status !== "CANCELLED");
  const sales = counted.reduce((s, o) => s + amtOf(o), 0);
  const listed = showAllOrders ? periodOrders : periodOrders.slice(0, 5);

  const shareText =
    `${state.shop.name} — ${periodLabel}\n` +
    `Money received: ₹${grouping(e.received)}\n` +
    `  Cash ₹${grouping(e.cash)} · UPI ₹${grouping(e.upi)}\n` +
    `Orders delivered: ${e.ordersDelivered} (work ₹${grouping(e.work)})\n` +
    `Baaki added: ₹${grouping(e.baakiAdded)} · Old baaki collected: ₹${grouping(e.oldIn)}\n` +
    `Total baaki in market: ₹${grouping(e.baakiMarket)}`;

  const pays = state.ledger
    .filter((en) => en.kind === "GOT" && inRange(en.date))
    .sort((a, b) => (a.date < b.date ? 1 : a.date > b.date ? -1 : b.ts - a.ts));
  const shown = pays.filter((en) => payFilter === "all" || en.method.toLowerCase() === payFilter);

  const emptyAll = state.orders.length === 0 && state.ledger.length === 0;

  return (
    <div className="flex min-h-dvh flex-col">
      {/* header */}
      <div className="flex items-center px-4 pb-2 pt-3.5">
        <h1 className="bric flex-1 text-[22px]">Earnings</h1>
        <button type="button" aria-label="Toggle amounts" onClick={toggleHide} className="flex size-11 items-center justify-center text-inksecondary">
          {hidden ? <IcEyeOff size={22} /> : <IcEye size={22} />}
        </button>
        <button
          type="button"
          aria-label="Share"
          onClick={() => { Analytics.summaryShared(period); setActive({ kind: "share", text: shareText }); }}
          className="flex size-11 items-center justify-center text-inksecondary"
        >
          <IcShare size={22} />
        </button>
      </div>

      <div className="flex flex-1 flex-col gap-3.5 px-4 pb-5">
        {emptyAll ? (
          <div className="flex w-full flex-col items-center gap-2 pt-14 text-center">
            <div className="flex size-[84px] items-center justify-center rounded-full bg-neutralfill text-inksecondary">
              <IcChart size={40} />
            </div>
            <h2 className="bric text-[22px]">No earnings yet</h2>
            <p className="text-[14px] text-muted">
              Take and deliver your first order — the money you receive shows up here.
            </p>
          </div>
        ) : (
          <>
            <div className="no-scrollbar flex gap-1.5 overflow-x-auto">
              {(
                [
                  ["today", "Today"],
                  ["week", "This week"],
                  ["month", "This month"],
                  ["all", "All time"],
                  ["custom", "Custom"],
                ] as const
              ).map(([v, label]) => (
                <PillChip
                  key={v}
                  label={label}
                  selected={period === v}
                  onClick={() => { setPeriod(v); setPayFilter("all"); setShowAllOrders(false); Analytics.earningsPeriodChanged(v); }}
                />
              ))}
            </div>
            {period === "custom" ? (
              // From / To: any dates; To never before From.
              <div className="flex gap-2">
                <DateTimeBox
                  icon={<IcCalendar size={20} />}
                  iconTint="var(--color-blue)"
                  text={`From ${AppDate.plain(customFrom)}`}
                  isSet
                  type="date"
                  value={customFrom}
                  onPick={(d) => { if (!d) return; setCustomFrom(d); if (customTo < d) setCustomTo(d); setShowAllOrders(false); }}
                  className="min-w-0 flex-1"
                />
                <DateTimeBox
                  icon={<IcCalendar size={20} />}
                  iconTint="var(--color-blue)"
                  text={`To ${AppDate.plain(customTo)}`}
                  isSet
                  type="date"
                  value={customTo}
                  min={customFrom}
                  onPick={(d) => { if (!d) return; setCustomTo(d < customFrom ? customFrom : d); setShowAllOrders(false); }}
                  className="min-w-0 flex-1"
                />
              </div>
            ) : null}

            {/* Sales — same numbers as Home's tiles */}
            <div className="flex w-full flex-col gap-1 rounded-[18px] bg-blue p-4 text-ondark shadow-md shadow-blue/20">
              <span className="text-[13px] font-semibold text-ondark/80">Sales · {periodTitle}</span>
              <span className="bric text-[34px]">{m(sales)}</span>
              <span className="text-[13px] font-semibold text-ondark/80">
                {counted.length} {counted.length === 1 ? "order" : "orders"} · total of their bills
              </span>
            </div>

            {/* Every order in the period */}
            <span className="text-[15px] font-bold">Orders · {periodOrders.length}</span>
            <AppCard>
              <div className="flex w-full flex-col px-3.5">
                {periodOrders.length === 0 ? <span className="py-4 text-[14px] text-muted">No orders in this period.</span> : null}
                {listed.map((o, i) => (
                  <div key={o.id}>
                    {i > 0 ? <Divider /> : null}
                    <OrderRow state={state} o={o} hidden={hidden} onClick={() => nav.openOrder(o.id)} />
                  </div>
                ))}
                {periodOrders.length > 5 ? (
                  <>
                    <Divider />
                    <button
                      type="button"
                      onClick={() => setShowAllOrders((v) => !v)}
                      className="w-full py-3.5 text-center text-[14px] font-bold text-blue"
                    >
                      {showAllOrders ? "Show less" : `Show all ${periodOrders.length} orders`}
                    </button>
                  </>
                ) : null}
              </div>
            </AppCard>

            <div className="grid grid-cols-1 gap-3.5 lg:grid-cols-2 lg:items-start">
              {/* Money received */}
              <AppCard bg="var(--color-ink)" borderColor="var(--color-ink)" className="lg:col-span-2">
                <div className="flex w-full flex-col gap-1 p-4">
                  <span className="text-[13px] font-semibold text-ondarkmuted">Money received · {periodTitle}</span>
                  <span className="bric text-[34px] text-ondark">{m(e.received)}</span>
                  <div className="mt-1.5 flex w-full gap-3">
                    <MoneySplit label="Cash" value={m(e.cash)} note="Should be in your drawer" />
                    <MoneySplit label="UPI" value={m(e.upi)} note="In your bank account" />
                  </div>
                </div>
              </AppCard>

              {/* How money came in */}
              <AppCard>
                <div className="flex w-full flex-col gap-0.5 p-4">
                  <span className="mb-1 text-[15px] font-bold">How the money came in</span>
                  <Line label="Work done" value={m(e.work)} sign={null} />
                  <Line label="Went to khata" value={m(e.baakiAdded)} sign="−" />
                  <Line label="Old baaki collected" value={m(e.oldIn)} sign="+" />
                  <Line label="Advance taken" value={m(e.advIn)} sign="+" />
                  <Line
                    label={e.prepaid >= 0 ? "Paid before delivery" : "Paid on earlier days for these orders"}
                    value={m(e.prepaid)}
                    sign={e.prepaid >= 0 ? "+" : "−"}
                  />
                  <Divider className="my-1.5" />
                  <Line label="Money received" value={m(e.received)} sign={null} bold />
                </div>
              </AppCard>

              <div className="flex flex-col gap-3.5">
                {/* Work done by service */}
                {e.byService.length > 0 ? (
                  <AppCard>
                    <div className="flex w-full flex-col gap-2.5 p-4">
                      <span className="text-[15px] font-bold">Work done by service</span>
                      {(() => {
                        const maxAmt = Math.max(1, ...e.byService.map((s) => s.amount));
                        return e.byService.map((s) => (
                          <div key={s.label} className="flex flex-col gap-1">
                            <div className="flex w-full justify-between gap-2">
                              <span className="min-w-0 flex-1 text-[14px] font-semibold">{s.label}</span>
                              <span className="text-[13px] font-semibold text-muted">
                                {m(s.amount)}
                                {!hidden ? ` · ${Math.trunc((s.amount * 100) / Math.max(1, e.work))}%` : ""}
                              </span>
                            </div>
                            <div className="h-2 w-full rounded-full bg-divider">
                              <div
                                className="h-2 rounded-full"
                                style={{
                                  width: `${Math.min(100, Math.max(3, (s.amount / maxAmt) * 100))}%`,
                                  background: s.isExtra ? "var(--color-servicebargrey)" : "var(--color-blue)",
                                }}
                              />
                            </div>
                          </div>
                        ));
                      })()}
                      {e.discounts > 0 ? (
                        <span className="text-[13px] font-semibold text-orangetext">− {m(e.discounts)} discounts given</span>
                      ) : null}
                    </div>
                  </AppCard>
                ) : null}

                {/* stats */}
                <AppCard>
                  <div className="flex w-full p-4">
                    <Stat label="Orders" value={String(e.ordersDelivered)} />
                    <Stat label="Pieces" value={String(e.pieces)} />
                    <Stat label="Weight" value={`${Sel.trimKg(e.kg)} kg`} />
                  </div>
                </AppCard>

                {/* baaki in market */}
                <AppCard borderColor="var(--color-orangeborder)" bg="var(--color-orangelight)" onClick={() => nav.openCustomers("baaki")}>
                  <div className="flex w-full items-center p-4">
                    <div className="min-w-0 flex-1">
                      <div className="text-[16px] font-bold text-orangedeep">Baaki in market {m(e.baakiMarket)}</div>
                      <div className="text-[13px] text-orangetext">
                        {e.baakiCustomers} {e.baakiCustomers === 1 ? "customer still has to pay" : "customers still have to pay"}
                      </div>
                    </div>
                    <span className="text-orangetext"><IcChevronRight size={22} /></span>
                  </div>
                </AppCard>
              </div>
            </div>

            {/* payments received */}
            <span className="mt-1 text-[15px] font-bold">Payments received</span>
            <div className="flex gap-1.5">
              {(
                [
                  ["all", "All"],
                  ["cash", "Cash"],
                  ["upi", "UPI"],
                ] as const
              ).map(([v, label]) => {
                const count = v === "all" ? pays.length : pays.filter((en) => en.method.toLowerCase() === v).length;
                return <PillChip key={v} label={`${label} ${count}`} selected={payFilter === v} onClick={() => setPayFilter(v)} />;
              })}
            </div>
            <AppCard>
              <div className="flex w-full flex-col px-3.5">
                {shown.length === 0 ? (
                  <span className="py-4 text-[14px] text-muted">No payments in this period.</span>
                ) : null}
                {shown.map((entry, i) => (
                  <div key={entry.id}>
                    {i > 0 ? <Divider /> : null}
                    <PaymentRow state={state} e={entry} hidden={hidden} onClick={() => nav.openCustomer(entry.custId)} />
                  </div>
                ))}
              </div>
            </AppCard>
          </>
        )}
      </div>

      <SheetHost active={active} state={state} nav={nav} onOpen={setActive} onDismiss={() => setActive(null)} />
    </div>
  );
}

function MoneySplit({ label, value, note }: { label: string; value: string; note: string }) {
  return (
    <div className="flex-1">
      <div className="text-[12px] font-semibold text-ondarkmuted">{label}</div>
      <div className="text-[18px] font-bold text-ondark">{value}</div>
      <div className="text-[11px] text-ondarkfaint">{note}</div>
    </div>
  );
}

function Line({
  label, value, sign, bold,
}: { label: string; value: string; sign: string | null; bold?: boolean }) {
  return (
    <div className="flex w-full justify-between py-[5px]">
      <span className={bold ? "text-[16px] font-bold" : "text-[14px] text-inksecondary"}>
        {sign != null ? `${sign} ` : ""}
        {label}
      </span>
      <span
        className={cls("font-bold", bold ? "text-[17px]" : "text-[14px]")}
        style={{ color: sign === "−" ? "var(--color-orangetext)" : "var(--color-ink)" }}
      >
        {value}
      </span>
    </div>
  );
}

function Stat({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex flex-1 flex-col items-center">
      <span className="bric text-[22px]">{value}</span>
      <span className="text-[12px] text-muted">{label}</span>
    </div>
  );
}

function PaymentRow({
  state, e, hidden, onClick,
}: { state: LaundryState; e: LedgerEntry; hidden: boolean; onClick: () => void }) {
  const c = Sel.customer(state, e.custId);
  let what: string;
  switch (e.tag) {
    case "PRE":
      what = `Paid before delivery · #${e.ref}`;
      break;
    case "DELIVER": {
      const o = e.ref != null ? Sel.order(state, e.ref) : undefined;
      const khata = o ? amtOf(o) - o.paid : 0;
      what = `Order #${e.ref} delivered` + (khata > 0 ? ` · ${hidden ? "₹ ••••" : rupees(khata)} to khata` : "");
      break;
    }
    case "RECEIVE":
      what = e.toOld > 0 && e.toAdv > 0 ? "Old baaki + advance" : e.toOld > 0 ? "Old baaki" : "Advance";
      break;
    default:
      what = "Payment";
  }
  return (
    <button type="button" onClick={onClick} className="flex w-full items-center py-3 text-left">
      <span
        className="flex size-[34px] shrink-0 items-center justify-center rounded-full text-[14px] font-bold"
        style={{
          background: e.method === "CASH" ? "var(--color-neutralfill)" : "var(--color-bluelight)",
          color: e.method === "CASH" ? "var(--color-inksecondary)" : "var(--color-bluetext)",
        }}
      >
        {e.method === "CASH" ? "₹" : "U"}
      </span>
      <span className="ml-2.5 flex min-w-0 flex-1 flex-col">
        <span className="truncate text-[15px] font-bold">{c.name}</span>
        <span className="truncate text-[12px] text-muted">{what + (e.time ? ` · ${e.time}` : "")}</span>
      </span>
      <span className="text-[15px] font-bold">{hidden ? "₹ ••••" : rupees(e.amt)}</span>
    </button>
  );
}

const STATUS_CHIP: Record<OrderStatus, [string, string, string]> = {
  CREATED: ["To pick up", "var(--color-orangelight)", "var(--color-orangetext)"],
  RECEIVED: ["Received", "var(--color-neutralfill)", "var(--color-inksecondary)"],
  READY: ["Ready", "var(--color-bluelight)", "var(--color-bluetext)"],
  DELIVERED: ["Delivered", "var(--color-neutralfill)", "var(--color-inksecondary)"],
  CANCELLED: ["Cancelled", "var(--color-neutralfill)", "var(--color-muted)"],
};

/** One order in the Earnings list: name, number · date, amount and status. */
function OrderRow({
  state, o, hidden, onClick,
}: { state: LaundryState; o: Order; hidden: boolean; onClick: () => void }) {
  const c = Sel.customer(state, o.custId);
  const [label, bg, fg] = STATUS_CHIP[o.status];
  const noBill = o.lines.length === 0;
  return (
    <button type="button" onClick={onClick} className="flex w-full items-center gap-2 py-3 text-left">
      <span className="flex min-w-0 flex-1 flex-col">
        <span className="truncate text-[15px] font-bold">{c.name}</span>
        <span className="truncate text-[12px] text-muted">#{Sel.orderNo(o)} · {AppDate.plain(o.pickupDate)}</span>
      </span>
      <span className="flex shrink-0 flex-col items-end gap-1">
        <span className={noBill ? "text-[12px] font-semibold text-muted" : "text-[15px] font-bold"}>
          {noBill ? "No bill yet" : hidden ? "₹ ••••" : rupees(amtOf(o))}
        </span>
        <span className="rounded-full px-2 py-0.5 text-[11px] font-bold" style={{ background: bg, color: fg }}>{label}</span>
      </span>
    </button>
  );
}
