"use client";

import { useEffect, useState } from "react";
import * as AppDate from "@/core/appdate";
import { rupees } from "@/core/money";
import { amtOf } from "@/domain/laundryMath";
import { billReceipt } from "@/domain/billReceipt";
import type { LaundryState, Order, OrderStatus, PayMethod } from "@/domain/models";
import * as Sel from "@/domain/selectors";
import * as Repo from "@/data/repository";
import { Analytics } from "@/analytics/events";
import { cls, DateTimeBox, Divider, FieldBox, PillChip, PrimaryButton } from "../basics";
import { billPreviewUrl } from "../billPdf";
import { prepareBillAssets } from "../billRender";
import { IcCalendar } from "../icons";
import { AppSheet } from "../sheet";
import { ClothesEditor, useClothesState } from "../clothes";
import type { useNav } from "../shell";
import type { ActiveSheet } from "./types";

/** Ports of ui/sheets/OrderSheets.kt. */

function MoneyRow({
  label, value, valueColor, bold,
}: { label: string; value: string; valueColor?: string; bold?: boolean }) {
  return (
    <div className="flex w-full justify-between py-1.5">
      <span className={cls(bold ? "text-[16px] font-bold text-ink" : "text-[15px] text-inksecondary")}>{label}</span>
      <span className={cls("font-bold", bold ? "text-[18px]" : "text-[15px]")} style={valueColor ? { color: valueColor } : undefined}>
        {value}
      </span>
    </div>
  );
}

export function CollectPaymentSheet({
  state, orderId, onDismiss,
}: { state: LaundryState; orderId: number; onDismiss: () => void }) {
  const o = Sel.order(state, orderId);
  const c = Sel.customer(state, o?.custId ?? "");
  const total = o ? amtOf(o) : 0;
  const pre = o?.pre ?? 0;
  const oldBal = o ? Sel.balance(state, o.custId, o.id) : 0;
  const billDue = total - pre;
  const due = billDue + oldBal;
  const [payAmt, setPayAmt] = useState(String(Math.max(0, due)));
  if (!o) { onDismiss(); return null; }
  const got = parseInt(payAmt, 10) || 0;
  const left = due - got;

  const preview =
    payAmt === ""
      ? { bg: "var(--color-bg)", fg: "var(--color-muted)", text: "Type the amount you got" }
      : left > 0
        ? { bg: "var(--color-orangelight)", fg: "var(--color-orangedeep)", text: `${rupees(left)} will stay in khata (baaki)` }
        : left < 0
          ? { bg: "var(--color-bluelight)", fg: "var(--color-bluetext)", text: `${rupees(left)} extra — kept as advance` }
          : { bg: "var(--color-neutralfill)", fg: "var(--color-ink)", text: "Full payment — all clear" };

  const doPay = (method: PayMethod, amount: number) => {
    Repo.deliver(orderId, amount, method);
    onDismiss();
  };

  return (
    <AppSheet title="Collect payment" subtitle={`From ${c.name} before handing over`} onDismiss={onDismiss}>
      <div className="flex flex-col gap-3.5">
        <div className="rounded-[14px] bg-card p-3.5">
          <MoneyRow
            label={pre > 0 ? (billDue < 0 ? `Paid extra before (bill ${rupees(total)})` : `This bill (after ${rupees(pre)} paid)`) : "This bill"}
            value={(billDue < 0 ? "− " : "") + rupees(billDue)}
          />
          {oldBal !== 0 ? (
            <MoneyRow
              label={oldBal > 0 ? "Old baaki" : "Advance already paid"}
              value={(oldBal > 0 ? "+ " : "− ") + rupees(oldBal)}
              valueColor={oldBal > 0 ? "var(--color-orangetext)" : "var(--color-bluetext)"}
            />
          ) : null}
          <Divider className="my-1.5" />
          <MoneyRow label="Total to collect" value={rupees(Math.max(0, due))} bold />
        </div>

        <FieldBox
          value={payAmt}
          onChange={(v) => setPayAmt(v.replace(/\D/g, "").slice(0, 6))}
          prefix="₹"
          h={56}
          inputMode="numeric"
          textClass="bric text-[22px]"
        />

        <div className="flex flex-wrap gap-1.5">
          <PillChip
            label={`Full ${rupees(Math.max(0, due))}`}
            selected={payAmt === String(Math.max(0, due))}
            onClick={() => setPayAmt(String(Math.max(0, due)))}
          />
          {oldBal > 0 && billDue > 0 ? (
            <PillChip
              label={`Only this bill ${rupees(billDue)}`}
              selected={payAmt === String(billDue)}
              onClick={() => setPayAmt(String(billDue))}
            />
          ) : null}
        </div>

        <div className="rounded-xl p-3.5" style={{ background: preview.bg }}>
          <span className="text-[14px] font-semibold" style={{ color: preview.fg }}>{preview.text}</span>
        </div>

        <div className="flex w-full gap-2">
          <PrimaryButton h={54} disabled={got <= 0} onClick={() => doPay("CASH", got)} className="flex-1">
            Got cash
          </PrimaryButton>
          <PrimaryButton h={54} disabled={got <= 0} onClick={() => doPay("UPI", got)} className="flex-1">
            Got UPI
          </PrimaryButton>
        </div>
        <button
          type="button"
          onClick={() => doPay("NONE", 0)}
          className="h-[50px] w-full rounded-[14px] bg-neutralfill text-[15px] font-bold text-inksecondary"
        >
          Nothing now · add all to khata
        </button>
      </div>
    </AppSheet>
  );
}

/**
 * "When will it be delivered?" — Today / Tomorrow / Day after or any date (not
 * before pickup). Asked when an order is marked ready, the way the payment is
 * asked when it's delivered.
 */
function DeliveryDateQuestion({ o, date, onDate }: { o: Order; date: string; onDate: (d: string) => void }) {
  const today = AppDate.today();
  const chips: [string, string][] = [
    ["Today", today],
    ["Tomorrow", AppDate.add(today, 1)],
    ["Day after", AppDate.add(today, 2)],
  ];
  return (
    <div className="flex flex-col gap-2.5">
      <span className="text-[15px] font-bold">When will it be delivered?</span>
      <div className="flex flex-wrap gap-1.5">
        {chips.map(([label, iso]) => (
          <PillChip key={label} label={label} selected={date === iso} onClick={() => onDate(iso)} />
        ))}
      </div>
      <DateTimeBox
        icon={<IcCalendar size={20} />}
        iconTint="var(--color-blue)"
        text={date ? `Delivery: ${AppDate.short(date)}` : "Pick a delivery date"}
        isSet={date !== ""}
        type="date"
        value={date || today}
        min={o.pickupDate}
        onPick={onDate}
      />
    </div>
  );
}

/** Mark ready: asks when it'll be delivered (starts on the order's date, if any). */
export function ReadySheet({
  state, orderId, onDismiss,
}: { state: LaundryState; orderId: number; onDismiss: () => void }) {
  const o = Sel.order(state, orderId);
  const c = Sel.customer(state, o?.custId ?? "");
  const [date, setDate] = useState(o?.deliveryDate ?? "");
  if (!o) { onDismiss(); return null; }
  return (
    <AppSheet title="Mark ready" subtitle={`${c.name} · #${Sel.orderNo(o)}`} onDismiss={onDismiss}>
      <div className="flex flex-col gap-4">
        <DeliveryDateQuestion o={o} date={date} onDate={setDate} />
        <PrimaryButton
          disabled={date === ""}
          onClick={() => {
            if (date === "") return;
            Repo.markReady(orderId, date);
            onDismiss();
          }}
        >
          {date === "" ? "Pick a delivery date" : "Mark ready"}
        </PrimaryButton>
      </div>
    </AppSheet>
  );
}

export function CountClothesSheet({
  state, orderId, next, onDismiss, onSaved,
}: { state: LaundryState; orderId: number; next: OrderStatus; onDismiss: () => void; onSaved?: () => void }) {
  const o = Sel.order(state, orderId);
  const c = Sel.customer(state, o?.custId ?? "");
  const clothes = useClothesState(state.services);
  const [date, setDate] = useState(o?.deliveryDate ?? "");
  if (!o) { onDismiss(); return null; }
  const total = clothes.total(state.services);
  const askDate = next === "READY";

  return (
    <AppSheet title="Count clothes" subtitle={`${c.name} · #${Sel.orderNo(o)}. The bill is made after this.`} onDismiss={onDismiss}>
      <div className="flex flex-col gap-4">
        <ClothesEditor services={state.services} api={clothes} editablePrice={false} />
        <div className="flex w-full items-center justify-between">
          <span className="text-[15px] font-semibold text-muted">Total</span>
          <span className="bric text-[26px]">{rupees(total)}</span>
        </div>
        {askDate ? <DeliveryDateQuestion o={o} date={date} onDate={setDate} /> : null}
        <PrimaryButton
          disabled={total <= 0 || (askDate && date === "")}
          onClick={() => {
            Repo.saveCount(orderId, next, clothes.lines(state.services), askDate ? date : "");
            (onSaved ?? onDismiss)();
          }}
        >
          {askDate && date === "" ? "Pick a delivery date" : next === "READY" ? "Mark ready · make bill" : "Picked up · make bill"}
        </PrimaryButton>
      </div>
    </AppSheet>
  );
}

export function RescheduleSheet({
  state, orderId, which, onDismiss,
}: { state: LaundryState; orderId: number; which: string; onDismiss: () => void }) {
  const o = Sel.order(state, orderId);
  const c = Sel.customer(state, o?.custId ?? "");
  const isPickup = which === "pickup";
  // No delivery date yet: nothing is pre-selected — the owner picks one (an
  // order only takes today's date by itself when it's marked delivered).
  const base = o ? (isPickup ? o.pickupDate : o.deliveryDate) : "";
  const [date, setDate] = useState(base);
  const [time] = useState(o ? AppDate.to24h(isPickup ? o.pickupTime : o.deliveryTime) : "");
  if (!o) { onDismiss(); return null; }

  const title = isPickup ? "Reschedule pickup" : o.deliveryDate !== "" ? "Reschedule delivery" : "Set delivery date";
  const chips: [string, string][] = [
    ["Today", AppDate.today()],
    ["Tomorrow", AppDate.add(AppDate.today(), 1)],
    ["Day after", AppDate.add(AppDate.today(), 2)],
  ];

  return (
    <AppSheet title={title} subtitle={`${c.name} · #${Sel.orderNo(o)}`} onDismiss={onDismiss}>
      <div className="flex flex-col gap-3.5">
        <div className="flex flex-wrap gap-1.5">
          {chips.map(([label, iso]) => (
            <PillChip key={label} label={label} selected={date === iso} onClick={() => setDate(iso)} />
          ))}
        </div>
        {/* Any date, past ones included — e.g. entering an order after the fact. */}
        <DateTimeBox
          icon={<IcCalendar size={20} />}
          iconTint="var(--color-blue)"
          text={date ? `Selected: ${AppDate.short(date)}` : "Pick a date"}
          isSet={date !== ""}
          type="date"
          value={date || AppDate.today()}
          min={isPickup ? undefined : o.pickupDate}
          onPick={setDate}
        />
        {isPickup && date && o.deliveryDate !== "" && o.ddAuto ? (
          <span className="text-[13px] text-muted">
            Delivery moves too: {AppDate.short(AppDate.add(o.deliveryDate, AppDate.daysBetween(o.pickupDate, date)))}
          </span>
        ) : null}
        <PrimaryButton
          disabled={date === ""}
          onClick={() => {
            if (date === "") return;
            Repo.reschedule(orderId, which, date, time, false);
            onDismiss();
          }}
        >
          {date === "" ? "Pick a date to save" : "Save"}
        </PrimaryButton>
      </div>
    </AppSheet>
  );
}

export function CancelSheet({
  state, orderId, onDismiss,
}: { state: LaundryState; orderId: number; onDismiss: () => void }) {
  const o = Sel.order(state, orderId);
  const c = Sel.customer(state, o?.custId ?? "");
  const [reason, setReason] = useState("");
  if (!o) { onDismiss(); return null; }
  const reasons = ["Customer not home", "Customer cancelled", "Wrong address", "Other"];
  const word = o.status === "CREATED" ? "pickup" : "order";

  return (
    <AppSheet title={`Cancel ${word}?`} subtitle={`${c.name} · #${Sel.orderNo(o)}`} onDismiss={onDismiss}>
      <div className="flex flex-col gap-3.5">
        <div className="flex flex-wrap gap-1.5">
          {reasons.map((r) => (
            <PillChip key={r} label={r} selected={reason === r} onClick={() => setReason(reason === r ? "" : r)} />
          ))}
        </div>
        {o.status === "DELIVERED" ? (
          <span className="text-[13px] text-muted">
            The bill comes off the khata. Money already taken stays as {Sel.firstName(c.name)}&apos;s advance.
          </span>
        ) : null}
        <div className="flex w-full gap-2">
          <button
            type="button"
            onClick={onDismiss}
            className="h-[54px] flex-1 rounded-[14px] bg-neutralfill text-[16px] font-bold text-inksecondary"
          >
            Keep order
          </button>
          <PrimaryButton
            h={54}
            bg="orange"
            className="flex-1"
            onClick={() => {
              Repo.cancelOrder(orderId, reason);
              onDismiss();
            }}
          >
            Cancel {word}
          </PrimaryButton>
        </div>
      </div>
    </AppSheet>
  );
}

export function DeleteOrderSheet({
  state, orderId, nav, onDismiss,
}: { state: LaundryState; orderId: number; nav: ReturnType<typeof useNav>; onDismiss: () => void }) {
  const o = Sel.order(state, orderId);
  const c = Sel.customer(state, o?.custId ?? "");
  if (!o) { onDismiss(); return null; }
  const what = o.lines.length > 0 ? "bill" : "order";
  const paidAny = state.ledger.some((e) => e.ref === o.id && e.kind === "GOT");

  return (
    <AppSheet title={`Delete ${what} #${Sel.orderNo(o)}?`} subtitle={`${c.name} · ${rupees(amtOf(o))}`} onDismiss={onDismiss}>
      <div className="flex flex-col gap-3.5">
        <div className="rounded-xl bg-orangelight p-3.5">
          <span className="text-[14px] font-semibold text-orangedeep">
            The {what} is removed from orders, earnings and {Sel.firstName(c.name)}&apos;s khata
            {paidAny ? ", along with the payments recorded on it" : ""}. You can undo right after.
          </span>
        </div>
        <div className="flex w-full gap-2">
          <button
            type="button"
            onClick={onDismiss}
            className="h-[54px] flex-1 rounded-[14px] bg-neutralfill text-[16px] font-bold text-inksecondary"
          >
            Keep
          </button>
          <PrimaryButton
            h={54}
            bg="orange"
            className="flex-1"
            onClick={() => {
              onDismiss();
              Repo.deleteOrder(orderId);
              // The order's own pages would show "not found" — leave them.
              if (window.location.pathname.startsWith("/orders/")) nav.openHome({ replace: true });
            }}
          >
            Delete {what}
          </PrimaryButton>
        </div>
      </div>
    </AppSheet>
  );
}

export function BillViewSheet({
  state, orderId, onDismiss,
}: { state: LaundryState; orderId: number; onDismiss: () => void }) {
  const o = Sel.order(state, orderId);
  const c = Sel.customer(state, o?.custId ?? "");
  useEffect(() => {
    Analytics.billViewed(orderId);
  }, [orderId]);
  if (!o) { onDismiss(); return null; }

  return (
    <AppSheet title={`Bill #${Sel.orderNo(o)}`} subtitle={`${c.name} · this is what the customer sees`} onDismiss={onDismiss}>
      <ReceiptPreview state={state} order={o} />
    </AppSheet>
  );
}

/** The exact receipt image that goes into the PDF. */
function ReceiptPreview({ state, order }: { state: LaundryState; order: Order }) {
  const [src, setSrc] = useState("");
  useEffect(() => {
    let live = true;
    const r = billReceipt(state, order);
    prepareBillAssets(r)
      .then(() => {
        if (live) setSrc(billPreviewUrl(r));
      })
      .catch(() => {
        if (live) setSrc("");
      });
    return () => {
      live = false;
    };
  }, [state, order]);
  if (!src) return <div className="h-[420px] w-full rounded-2xl bg-white" />;
  return (
    // eslint-disable-next-line @next/next/no-img-element -- a data: URL, nothing to optimise
    <img src={src} alt={`Bill #${Sel.orderNo(order)}`} className="w-full rounded-2xl border border-cardborder bg-white" />
  );
}

/**
 * Change status: every state the order can be in, the next one pre-selected.
 * Forward steps that need input reuse the usual sheets (count clothes,
 * delivery date, collect payment, cancel reason); everything else is a direct
 * move, going backward included. Port of ChangeStatusSheet (Android).
 */
export function ChangeStatusSheet({
  state, orderId, onOpen, onDismiss,
}: {
  state: LaundryState;
  orderId: number;
  onOpen: (s: ActiveSheet) => void;
  onDismiss: () => void;
}) {
  const o = Sel.order(state, orderId);
  const c = Sel.customer(state, o?.custId ?? "");
  const [sel, setSel] = useState<OrderStatus>(() =>
    o?.status === "CREATED" ? "RECEIVED"
      : o?.status === "RECEIVED" ? "READY"
        : o?.status === "READY" ? "DELIVERED"
          : o?.status === "DELIVERED" ? "READY" // only way is back
            : "RECEIVED",
  );
  if (!o) { onDismiss(); return null; }

  const steps: [OrderStatus, string][] = [
    ...(o.pickup === "HOME" ? ([["CREATED", "To pick up"]] as [OrderStatus, string][]) : []),
    ["RECEIVED", "Received · clothes at shop"],
    ["READY", "Ready"],
    ["DELIVERED", "Delivered"],
    ["CANCELLED", "Cancelled"],
  ];

  function apply(target: OrderStatus) {
    if (!o) return;
    if (target === o.status) return onDismiss();
    if (target === "CANCELLED") return onOpen({ kind: "cancel", orderId: o.id });
    if (target === "DELIVERED") {
      // No bill yet: count the clothes first, then collect.
      return onOpen(o.lines.length === 0 ? { kind: "count", orderId: o.id, next: "READY", thenPay: true } : { kind: "pay", orderId: o.id });
    }
    if (target === "READY" && o.status !== "DELIVERED" && o.status !== "CANCELLED") {
      // Forward to ready asks the delivery date; back from delivered doesn't.
      return onOpen(o.lines.length === 0 ? { kind: "count", orderId: o.id, next: "READY" } : { kind: "ready", orderId: o.id });
    }
    if (target === "RECEIVED" && o.status === "CREATED" && o.lines.length === 0) {
      return onOpen({ kind: "count", orderId: o.id, next: "RECEIVED" });
    }
    Repo.setStatus(o.id, target);
    onDismiss();
  }

  const selLabel = steps.find(([k]) => k === sel)?.[1].split(" · ")[0] ?? "";
  return (
    <AppSheet title="Change status" subtitle={`${c.name} · #${Sel.orderNo(o)}`} onDismiss={onDismiss}>
      <div className="flex flex-col gap-2">
        {steps.map(([st, label]) => {
          const current = st === o.status;
          const on = sel === st && !current;
          const danger = st === "CANCELLED";
          return (
            <button
              key={st}
              type="button"
              disabled={current}
              onClick={() => setSel(st)}
              className={cls(
                "flex w-full items-center gap-2.5 rounded-[14px] border-[1.5px] px-3.5 py-[13px] text-left",
                on ? "border-blue bg-bluelight" : "border-cardborder bg-card",
              )}
            >
              <span
                className={cls(
                  "size-[18px] shrink-0 rounded-full border-[1.5px]",
                  on ? "border-blue bg-blue" : "border-fieldborder bg-card",
                )}
              />
              <span
                className={cls(
                  "flex-1 text-[15px]",
                  on ? "font-bold" : "font-semibold",
                  current ? "text-muted" : danger ? "text-orangetext" : "text-ink",
                )}
              >
                {label}
              </span>
              {current ? <span className="text-[12px] font-bold text-muted">Current</span> : null}
            </button>
          );
        })}
        {o.status === "DELIVERED" && sel !== "DELIVERED" ? (
          <span className="text-[13px] text-muted">
            Going back takes the bill off the khata. Money already taken stays with the order and counts when you deliver again.
          </span>
        ) : null}
        <PrimaryButton
          h={54}
          bg={sel === "CANCELLED" ? "orange" : "blue"}
          disabled={sel === o.status}
          onClick={() => apply(sel)}
          className="mt-1.5"
        >
          {sel === "DELIVERED"
            ? o.lines.length === 0 ? "Count clothes · deliver" : "Mark delivered"
            : sel === "CANCELLED" ? "Cancel order" : `Move to ${selLabel}`}
        </PrimaryButton>
      </div>
    </AppSheet>
  );
}
