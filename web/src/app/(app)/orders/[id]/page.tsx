"use client";

import { use, useState } from "react";
import * as AppDate from "@/core/appdate";
import { rupees } from "@/core/money";
import { amtOf } from "@/domain/laundryMath";
import type { Order } from "@/domain/models";
import * as Sel from "@/domain/selectors";
import * as Repo from "@/data/repository";
import { useLaundryState } from "@/data/store";
import { useScreenView } from "@/analytics/useScreenView";
import { AppCard, Avatar, cls, Divider, OutlineButton, PrimaryButton, TopBar } from "@/ui/basics";
import { downloadBill, sendBillOnWhatsApp, usePrepareBillSend } from "@/ui/billActions";
import { IcMore } from "@/ui/icons";
import { SheetHost } from "@/ui/sheets/host";
import type { ActiveSheet } from "@/ui/sheets/types";
import { Shell, useNav } from "@/ui/shell";

/** Order detail — port of ui/screens/order/OrderDetailScreen.kt. */

export default function OrderDetailPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = use(params);
  return (
    <Shell tab={null} showMobileNav={false}>
      <OrderDetailScreen orderId={parseInt(id, 10)} />
    </Shell>
  );
}

function paymentText(o: Order, amt: number): string {
  if (o.status === "DELIVERED") {
    const k = amt - o.paid;
    if (k <= 0) return `Paid ${rupees(amt)}`;
    if (o.paid > 0) return `Paid ${rupees(o.paid)} · ${rupees(k)} went to khata`;
    return `${rupees(amt)} went to khata`;
  }
  if (o.lines.length === 0) return "Bill after clothes are counted";
  if (o.pre >= amt) return "Paid in advance";
  return "To be paid at delivery";
}

function OrderDetailScreen({ orderId }: { orderId: number }) {
  const state = useLaundryState();
  const nav = useNav();
  const [active, setActive] = useState<ActiveSheet | null>(null);
  useScreenView("order_detail");
  usePrepareBillSend(orderId);

  const o = Sel.order(state, orderId);
  if (!o) {
    return <div className="p-8 text-center text-[14px] text-muted">Order not found.</div>;
  }
  const c = Sel.customer(state, o.custId);
  const amt = amtOf(o);

  const cancelled = o.status === "CANCELLED";
  const labels = o.pickup === "HOME" ? ["To pick up", "Received", "Ready", "Delivered"] : ["Received", "Ready", "Delivered"];
  const keys: readonly Order["status"][] = o.pickup === "HOME"
    ? ["CREATED", "RECEIVED", "READY", "DELIVERED"]
    : ["RECEIVED", "READY", "DELIVERED"];
  const idx = cancelled ? -1 : keys.indexOf(o.status);

  const actLabel =
    o.status === "CREATED" ? "Mark picked up"
      : o.status === "RECEIVED" ? "Mark ready"
        : o.status === "READY" ? "Mark delivered"
          : "";

  function onAct() {
    if (!o) return;
    switch (o.status) {
      case "CREATED":
        if (o.lines.length === 0) setActive({ kind: "count", orderId: o.id, next: "RECEIVED" });
        else Repo.markPickedUp(o.id);
        break;
      case "RECEIVED":
        if (o.lines.length === 0) setActive({ kind: "count", orderId: o.id, next: "READY" });
        else Repo.markReady(o.id);
        break;
      case "READY":
        setActive({ kind: "pay", orderId: o.id });
        break;
    }
  }

  return (
    <div className="flex min-h-dvh flex-col">
      <TopBar
        title={`#${Sel.orderNo(o)}`}
        onBack={nav.back}
        trailing={
          <button
            type="button"
            aria-label="More"
            onClick={() => setActive({ kind: "menu", orderId: o.id })}
            className="flex size-11 items-center justify-center text-ink"
          >
            <IcMore size={22} />
          </button>
        }
      />

      <div className="flex flex-col gap-4 p-4">
        {/* customer header */}
        <button type="button" onClick={() => nav.openCustomer(o.custId)} className="flex w-full items-center gap-3 text-left">
          <Avatar text={Sel.initials(c.name)} size={44} bg="var(--color-bluelight)" fg="var(--color-bluetext)" />
          <span className="flex min-w-0 flex-1 flex-col">
            <span className="text-[18px] font-bold">{c.name}</span>
            <span className="text-[13px] text-muted">+91 {Sel.fmtPhone(c.phone)}</span>
          </span>
        </button>

        {/* stepper */}
        {!cancelled ? (
          <AppCard>
            <div className="flex w-full items-start p-4">
              {labels.map((label, i) => (
                <div key={label} className="flex flex-1 flex-col items-center gap-1.5">
                  <span
                    className={cls(
                      "size-[18px] rounded-full",
                      i <= idx ? "bg-blue" : "border-[1.5px] border-dashborder bg-card",
                    )}
                  />
                  <span className={cls("text-center text-[11px] font-semibold", i <= idx ? "text-ink" : "text-muteddot")}>
                    {label}
                  </span>
                </div>
              ))}
            </div>
          </AppCard>
        ) : (
          <div className="w-full rounded-xl bg-neutralfill p-3.5">
            <span className="text-[15px] font-semibold text-inksecondary">
              Cancelled{o.cancelReason ? ` · ${o.cancelReason}` : ""}
            </span>
          </div>
        )}

        {/* action */}
        {actLabel !== "" ? (
          <PrimaryButton h={54} onClick={onAct}>{actLabel}</PrimaryButton>
        ) : o.status === "DELIVERED" ? (
          <div className="w-full rounded-xl bg-bluelight p-3.5">
            <span className="text-[14px] font-semibold text-bluetext">
              Delivered{o.doneAt ? ` · ${o.doneAt}` : ""} · order closed
            </span>
          </div>
        ) : null}

        {/* items */}
        {o.lines.length > 0 ? (
          <>
            <AppCard>
              <div className="flex flex-col gap-1.5 p-4">
                {o.lines.map((l, i) => (
                  <div key={i} className="flex w-full justify-between gap-2">
                    <span className="flex min-w-0 flex-1 flex-col">
                      <span className="text-[15px] font-semibold">
                        {l.kg > 0 ? Sel.weightLabel(l) : `${l.itemName} × ${l.qty}`}
                      </span>
                      <span className="text-[12px] text-muted">
                        {l.serviceName}
                        {l.kg > 0 ? "" : ` · ₹${l.price} each`}
                      </span>
                    </span>
                    <span className="text-[15px] font-semibold">{rupees(l.amt)}</span>
                  </div>
                ))}
                {o.express && o.exAmt > 0 ? <DetailRow label="Express" value={`+ ${rupees(o.exAmt)}`} /> : null}
                {o.fee > 0 ? <DetailRow label="Pickup / delivery" value={`+ ${rupees(o.fee)}`} /> : null}
                {o.discount > 0 ? <DetailRow label="Discount" value={`− ${rupees(o.discount)}`} tone="var(--color-orangetext)" /> : null}
                <Divider className="my-1" />
                <DetailRow label="Total" value={rupees(amt)} bold />
                <span
                  className={cls(
                    "text-[13px] font-semibold",
                    o.status === "DELIVERED" && o.paid < amt ? "text-orangetext" : "text-bluetext",
                  )}
                >
                  {paymentText(o, amt)}
                </span>
              </div>
            </AppCard>
            <div className="flex w-full gap-2">
              <OutlineButton
                h={48}
                className="flex-1"
                border="var(--color-cardborder)"
                fg="var(--color-ink)"
                onClick={() => setActive({ kind: "billView", orderId: o.id })}
              >
                View bill
              </OutlineButton>
              <OutlineButton
                h={48}
                className="flex-1"
                border="var(--color-cardborder)"
                fg="var(--color-ink)"
                onClick={() => downloadBill(state, o)}
              >
                Download
              </OutlineButton>
              {o.billSent ? (
                <OutlineButton
                  h={48}
                  className="flex-1"
                  border="var(--color-blueborder)"
                  fg="var(--color-bluetext)"
                  onClick={() => sendBillOnWhatsApp(state, o)}
                >
                  Sent ✓
                </OutlineButton>
              ) : (
                <PrimaryButton h={48} className="flex-1" onClick={() => sendBillOnWhatsApp(state, o)}>
                  WhatsApp
                </PrimaryButton>
              )}
            </div>
          </>
        ) : (
          <div className="w-full rounded-xl bg-card p-3.5">
            <span className="text-[14px] font-semibold text-muted">Bill after clothes are counted</span>
          </div>
        )}

        {/* pickup / delivery info */}
        <AppCard>
          <div className="flex flex-col gap-2 p-4">
            <InfoRow
              label="Pickup"
              value={(o.pickup === "HOME" ? "From home · " : "At shop · ") + AppDate.short(o.pickupDate) + (o.pickupTime ? ` · ${o.pickupTime}` : "")}
            />
            <InfoRow
              label="Delivery"
              value={
                (o.delivery === "HOME" ? "To home · " : "At shop · ") +
                (o.deliveryDate !== ""
                  ? AppDate.short(o.deliveryDate) + (o.deliveryTime ? ` · ${o.deliveryTime}` : "")
                  : "date not set")
              }
            />
            {o.pickup === "HOME" || o.delivery === "HOME" ? (
              <InfoRow label="Address" value={c.address || "No address saved"} />
            ) : null}
          </div>
        </AppCard>

        {/* manage */}
        <div className="flex w-full gap-2 pb-6">
          <OutlineButton
            h={50}
            className="flex-1"
            border="var(--color-cardborder)"
            fg="var(--color-ink)"
            onClick={() => nav.openNewOrder({ editId: o.id, from: "order" })}
          >
            Edit
          </OutlineButton>
          <OutlineButton
            h={50}
            className="flex-1"
            border="var(--color-cardborder)"
            fg="var(--color-ink)"
            onClick={() => setActive({ kind: "menu", orderId: o.id })}
          >
            More
          </OutlineButton>
        </div>
      </div>

      <SheetHost active={active} state={state} nav={nav} onOpen={setActive} onDismiss={() => setActive(null)} />
    </div>
  );
}

function DetailRow({
  label, value, tone, bold,
}: { label: string; value: string; tone?: string; bold?: boolean }) {
  return (
    <div className="flex w-full justify-between py-0.5">
      <span className={bold ? "text-[16px] font-bold" : "text-[14px] text-inksecondary"}>{label}</span>
      <span className={cls("font-bold", bold ? "text-[18px]" : "text-[14px]")} style={tone ? { color: tone } : undefined}>
        {value}
      </span>
    </div>
  );
}

function InfoRow({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex w-full gap-3">
      <span className="w-[72px] shrink-0 text-[13px] font-semibold text-muted">{label}</span>
      <span className="flex-1 text-[14px] font-semibold">{value}</span>
    </div>
  );
}
