"use client";

import { Suspense, use, useState } from "react";
import { useSearchParams } from "next/navigation";
import * as AppDate from "@/core/appdate";
import { rupees } from "@/core/money";
import { amtOf, isOpen } from "@/domain/laundryMath";
import type { Route } from "@/domain/models";
import * as Sel from "@/domain/selectors";
import * as Repo from "@/data/repository";
import { useLaundryState } from "@/data/store";
import { useScreenView } from "@/analytics/useScreenView";
import { AppCard, cls, Divider, OutlineButton, PrimaryButton, TopBar } from "@/ui/basics";
import { downloadBill, sendBillOnWhatsApp, usePrepareBillSend } from "@/ui/billActions";
import { SheetHost } from "@/ui/sheets/host";
import type { ActiveSheet } from "@/ui/sheets/types";
import { Shell, useNav } from "@/ui/shell";

/** Bill screen (after save / from order) — port of ui/screens/bill/BillScreen.kt. */

export default function BillPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = use(params);
  return (
    <Shell tab={null} showMobileNav={false}>
      <Suspense>
        <BillScreen orderId={parseInt(id, 10)} />
      </Suspense>
    </Shell>
  );
}

function routeLine(pickup: Route, delivery: Route): string {
  return (
    (pickup === "HOME" ? "Picked up from home" : "Received at shop") +
    " → " +
    (delivery === "HOME" ? "deliver to home" : "customer collects at shop")
  );
}

function BillScreen({ orderId }: { orderId: number }) {
  const from = useSearchParams().get("from") ?? "home";
  const state = useLaundryState();
  const nav = useNav();
  const [active, setActive] = useState<ActiveSheet | null>(null);
  useScreenView("bill");
  usePrepareBillSend(orderId);

  const o = Sel.order(state, orderId);
  if (!o) {
    return <div className="p-8 text-center text-[14px] text-muted">Order not found.</div>;
  }
  const c = Sel.customer(state, o.custId);
  const amt = amtOf(o);
  const fullyPaid = o.pre >= amt && amt > 0;
  const due = amt - o.pre; // still to collect before delivery

  const done = () => (from === "new" ? nav.openHome() : nav.back());

  return (
    <div className="flex min-h-dvh flex-col">
      <TopBar title="Bill" onBack={done} />

      <div className="flex flex-col gap-4 p-4">
        {/* banner */}
        <div className="w-full rounded-2xl bg-ink p-4">
          <div className="bric text-[20px] text-ondark">
            {from === "new" ? `Order #${Sel.orderNo(o)} saved` : `Bill for order #${Sel.orderNo(o)}`}
          </div>
          <div className="text-[13px] font-semibold text-ondarkfaint">
            {(o.lines.length > 0 ? `${Sel.itemsLabel(o)} · ` : "") + `Total ${rupees(amt)}`}
          </div>
        </div>

        {/* summary */}
        <AppCard>
          <div className="flex flex-col gap-1.5 p-4">
            <span className="text-[17px] font-bold">{c.name}</span>
            <span className="text-[13px] text-muted">+91 {Sel.fmtPhone(c.phone)}</span>
            <span className="text-[13px] text-inksecondary">{routeLine(o.pickup, o.delivery)}</span>
            <Divider className="my-1" />
            {o.lines.map((l, i) => (
              <BillRow
                key={i}
                label={l.kg > 0 ? Sel.weightLabel(l) : `${l.itemName} × ${l.qty}`}
                value={rupees(l.amt)}
              />
            ))}
            {o.express && o.exAmt > 0 ? <BillRow label="Express" value={`+ ${rupees(o.exAmt)}`} /> : null}
            {o.fee > 0 ? <BillRow label="Pickup / delivery" value={`+ ${rupees(o.fee)}`} /> : null}
            {o.discount > 0 ? <BillRow label="Discount" value={`− ${rupees(o.discount)}`} tone="var(--color-orangetext)" /> : null}
            <Divider className="my-1" />
            <BillRow label="Total" value={rupees(amt)} bold />
            <span className="text-[13px] text-muted">
              {o.deliveryDate !== ""
                ? (o.delivery === "HOME" ? "Delivery " : "Ready by ") +
                  AppDate.short(o.deliveryDate) +
                  (o.deliveryTime ? ` · ${o.deliveryTime}` : "")
                : "Delivery date not set yet"}
            </span>
          </div>
        </AppCard>

        {/* send section */}
        <div className="flex flex-col gap-2">
          <span className="text-[13px] font-bold text-muted">Bill</span>
          <div className="flex w-full gap-2">
            <OutlineButton
              h={50}
              className="flex-1"
              border="var(--color-cardborder)"
              fg="var(--color-ink)"
              onClick={() => setActive({ kind: "billView", orderId: o.id })}
            >
              View bill
            </OutlineButton>
            <OutlineButton
              h={50}
              className="flex-1"
              border="var(--color-cardborder)"
              fg="var(--color-ink)"
              onClick={() => downloadBill(state, o)}
            >
              Download
            </OutlineButton>
          </div>
          <PrimaryButton h={54} onClick={() => sendBillOnWhatsApp(state, o)}>
            {o.billSent ? "Send again" : "Send on WhatsApp"}
          </PrimaryButton>
          <span className={cls("text-[13px] font-bold", o.billSent ? "text-bluetext" : "text-orangetext")}>
            {o.billSent ? `Sent to ${Sel.firstName(c.name)} ✓` : "Not sent yet"}
          </span>
        </div>

        {/* paying now */}
        {isOpen(o) && due > 0 ? (
          <AppCard>
            <div className="flex flex-col gap-2.5 p-4">
              <span className="text-[15px] font-bold">Paying now?</span>
              <div className="flex w-full gap-2">
                <OutlineButton h={50} className="flex-1" onClick={() => Repo.prepay(o.id, "CASH")}>
                  Got cash {rupees(due)}
                </OutlineButton>
                <OutlineButton h={50} className="flex-1" onClick={() => Repo.prepay(o.id, "UPI")}>
                  Got UPI {rupees(due)}
                </OutlineButton>
              </div>
            </div>
          </AppCard>
        ) : isOpen(o) && fullyPaid ? (
          <div className="w-full rounded-xl bg-bluelight p-3.5">
            <span className="text-[14px] font-semibold text-bluetext">Fully paid — nothing to collect at delivery</span>
          </div>
        ) : null}

        <div className="mt-1 flex w-full gap-2">
          <OutlineButton
            h={52}
            className="flex-1"
            border="var(--color-cardborder)"
            fg="var(--color-ink)"
            onClick={() => nav.openNewOrder({ editId: o.id, from: "bill" })}
          >
            Edit bill
          </OutlineButton>
          <PrimaryButton h={52} className="flex-1" onClick={done}>
            Done
          </PrimaryButton>
        </div>
        <button
          type="button"
          onClick={() => setActive({ kind: "deleteOrder", orderId: o.id })}
          className="mx-auto mb-6 h-11 px-4 text-[14px] font-bold text-orangetext"
        >
          Delete bill
        </button>
      </div>

      <SheetHost active={active} state={state} nav={nav} onOpen={setActive} onDismiss={() => setActive(null)} />
    </div>
  );
}

function BillRow({
  label, value, tone, bold,
}: { label: string; value: string; tone?: string; bold?: boolean }) {
  return (
    <div className="flex w-full justify-between py-[3px]">
      <span className={bold ? "text-[16px] font-bold" : "text-[15px] text-inksecondary"}>{label}</span>
      <span className={cls("font-bold", bold ? "text-[18px]" : "text-[15px]")} style={tone ? { color: tone } : undefined}>
        {value}
      </span>
    </div>
  );
}
