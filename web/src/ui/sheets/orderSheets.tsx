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
import { cls, DateTimeBox, Divider, FieldBox, PillChip, PrimaryButton, Toggle } from "../basics";
import { billPreviewUrl } from "../billPdf";
import { prepareBillAssets } from "../billRender";
import { IcCalendar } from "../icons";
import { AppSheet } from "../sheet";
import { ClothesEditor, useClothesState } from "../clothes";
import type { useNav } from "../shell";

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

export function CountClothesSheet({
  state, orderId, next, onDismiss,
}: { state: LaundryState; orderId: number; next: OrderStatus; onDismiss: () => void }) {
  const o = Sel.order(state, orderId);
  const c = Sel.customer(state, o?.custId ?? "");
  const clothes = useClothesState(state.services);
  if (!o) { onDismiss(); return null; }
  const total = clothes.total(state.services);

  return (
    <AppSheet title="Count clothes" subtitle={`${c.name} · #${o.id}. The bill is made after this.`} onDismiss={onDismiss}>
      <div className="flex flex-col gap-4">
        <ClothesEditor services={state.services} api={clothes} editablePrice={false} />
        <div className="flex w-full items-center justify-between">
          <span className="text-[15px] font-semibold text-muted">Total</span>
          <span className="bric text-[26px]">{rupees(total)}</span>
        </div>
        <PrimaryButton
          disabled={total <= 0}
          onClick={() => {
            Repo.saveCount(orderId, next, clothes.lines(state.services));
            onDismiss();
          }}
        >
          {next === "READY" ? "Mark ready · make bill" : "Picked up · make bill"}
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
  const base = o ? (isPickup ? o.pickupDate : o.deliveryDate || AppDate.today()) : AppDate.today();
  const [date, setDate] = useState(base);
  const [time] = useState(o ? AppDate.to24h(isPickup ? o.pickupTime : o.deliveryTime) : "");
  const [notify, setNotify] = useState(true);
  if (!o) { onDismiss(); return null; }

  const title = isPickup ? "Reschedule pickup" : o.deliveryDate !== "" ? "Reschedule delivery" : "Set delivery date";
  const chips: [string, string][] = [
    ["Today", AppDate.today()],
    ["Tomorrow", AppDate.add(AppDate.today(), 1)],
    ["Day after", AppDate.add(AppDate.today(), 2)],
  ];

  return (
    <AppSheet title={title} subtitle={`${c.name} · #${o.id}`} onDismiss={onDismiss}>
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
          text={`Selected: ${AppDate.short(date)}`}
          isSet
          type="date"
          value={date}
          min={isPickup ? undefined : o.pickupDate}
          onPick={setDate}
        />
        {isPickup && o.deliveryDate !== "" && o.ddAuto ? (
          <span className="text-[13px] text-muted">
            Delivery moves too: {AppDate.short(AppDate.add(o.deliveryDate, AppDate.daysBetween(o.pickupDate, date)))}
          </span>
        ) : null}
        <div className="flex w-full items-center rounded-xl bg-card p-3.5">
          <span className="flex-1 text-[15px] font-semibold">Tell customer on WhatsApp</span>
          <Toggle on={notify} onToggle={() => setNotify((v) => !v)} />
        </div>
        <PrimaryButton
          onClick={() => {
            Repo.reschedule(orderId, which, date, time, notify);
            onDismiss();
          }}
        >
          Save
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

  return (
    <AppSheet title="Cancel pickup?" subtitle={`${c.name} · #${o.id}`} onDismiss={onDismiss}>
      <div className="flex flex-col gap-3.5">
        <div className="flex flex-wrap gap-1.5">
          {reasons.map((r) => (
            <PillChip key={r} label={r} selected={reason === r} onClick={() => setReason(reason === r ? "" : r)} />
          ))}
        </div>
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
            Cancel pickup
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
    <AppSheet title={`Delete ${what} #${o.id}?`} subtitle={`${c.name} · ${rupees(amtOf(o))}`} onDismiss={onDismiss}>
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
    <AppSheet title={`Bill #${o.id}`} subtitle={`${c.name} · this is what the customer sees`} onDismiss={onDismiss}>
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
    <img src={src} alt={`Bill #${order.id}`} className="w-full rounded-2xl border border-cardborder bg-white" />
  );
}
