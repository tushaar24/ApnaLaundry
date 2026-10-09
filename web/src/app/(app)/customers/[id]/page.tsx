"use client";

import { use, useState } from "react";
import * as AppDate from "@/core/appdate";
import { rupees } from "@/core/money";
import { amtOf, isOpen } from "@/domain/laundryMath";
import type { LaundryState, Order, PayMethod } from "@/domain/models";
import * as Sel from "@/domain/selectors";
import { useLaundryState } from "@/data/store";
import { sendReminderOnWhatsApp } from "@/ui/billActions";
import { Analytics } from "@/analytics/events";
import { useScreenView } from "@/analytics/useScreenView";
import {
  AppCard, Avatar, cls, OutlineButton, PrimaryButton, Segmented, StatusPill, TopBar,
} from "@/ui/basics";
import { SheetHost } from "@/ui/sheets/host";
import type { ActiveSheet } from "@/ui/sheets/types";
import { Shell, useNav } from "@/ui/shell";

/** Customer khata page — port of ui/screens/customer/CustomerScreen.kt. */

export default function CustomerPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = use(params);
  return (
    <Shell tab={null} showMobileNav={false}>
      <CustomerScreen custId={id} />
    </Shell>
  );
}

interface KhataRow {
  key: string;
  title: string;
  sub: string;
  amount: string;
  amtColor: string;
  after: string;
}

function methodName(m: PayMethod): string {
  return m === "CASH" ? "Cash" : m === "UPI" ? "UPI" : "";
}

function balWord(b: number): string {
  return b > 0 ? `Baaki ${rupees(b)}` : b < 0 ? `Advance ${rupees(b)}` : "All clear";
}

function khataRows(state: LaundryState, custId: string): KhataRow[] {
  const led = state.ledger
    .filter((e) => e.custId === custId)
    .slice()
    .sort((a, b) => (a.date < b.date ? -1 : a.date > b.date ? 1 : a.ts - b.ts));
  let run = 0;
  const out = led.map((e): KhataRow => {
    const before = run;
    const whenStr = AppDate.plain(e.date).split(", ")[1] + (e.time ? ` · ${e.time}` : "");
    let isAdd = false;
    let title = "";
    let sub = "";
    switch (e.kind) {
      case "BILL": {
        isAdd = true;
        run += e.amt;
        const o = e.ref != null ? Sel.order(state, e.ref) : undefined;
        title = `Bill · Order #${e.ref}`;
        const desc = e.note !== "" ? e.note : o ? `${Sel.itemsLabel(o)} · ${Sel.svcLabel(o)}` : "";
        sub =
          whenStr +
          (desc !== "" ? ` · ${desc}` : "") +
          (before < 0 ? ` · ${rupees(Math.min(-before, e.amt))} advance used` : "");
        break;
      }
      case "OLD":
        isAdd = true;
        run += e.amt;
        title = "Old baaki";
        sub = `${whenStr} · from notebook`;
        break;
      case "ADJ":
        isAdd = e.amt > 0;
        run += e.amt;
        title = `Bill changed · Order #${e.ref}`;
        sub = `${whenStr} · order edited after delivery`;
        break;
      case "GOT": {
        run -= e.amt;
        title = e.tag === "PRE" ? `Paid for order #${e.ref} · ${methodName(e.method)}` : `Got ${rupees(e.amt)} · ${methodName(e.method)}`;
        sub = whenStr + (e.toAdv > 0 ? ` · ${rupees(e.toAdv)} kept as advance` : "");
        break;
      }
    }
    const fg = isAdd ? "var(--color-orangetext)" : "var(--color-bluetext)";
    return {
      key: e.id,
      title,
      sub,
      amount: (isAdd ? "+ " : "− ") + rupees(e.amt),
      amtColor: fg,
      after: balWord(run),
    };
  });
  // Open orders are already in the baaki at their current total (newest last).
  const open = state.orders
    .filter((o) => o.custId === custId && isOpen(o))
    .sort((a, b) => a.id - b.id);
  for (const o of open) {
    const amt = amtOf(o);
    run += amt;
    const desc = o.lines.length > 0 ? `${Sel.itemsLabel(o)} · ${Sel.svcLabel(o)}` : "clothes not counted yet";
    out.push({
      key: `open-${o.id}`,
      title: `Order #${Sel.orderNo(o)} · in progress`,
      sub: `${AppDate.plain(o.createdOn || o.pickupDate).split(", ")[1]} · ${desc}`,
      amount: "+ " + rupees(amt),
      amtColor: "var(--color-orangetext)",
      after: balWord(run),
    });
  }
  return out.reverse();
}

function CustomerScreen({ custId }: { custId: string }) {
  const state = useLaundryState();
  const nav = useNav();
  const [tab, setTab] = useState<"khata" | "orders">("khata");
  const [active, setActive] = useState<ActiveSheet | null>(null);
  useScreenView("customer_khata");

  const exists = state.customers.some((cu) => cu.id === custId);
  if (!exists) {
    return <div className="p-8 text-center text-[14px] text-muted">Customer not found.</div>;
  }
  const c = Sel.customer(state, custId);
  const nm = Sel.firstName(c.name);
  const bal = Sel.balance(state, custId);
  const mine = state.orders.filter((o) => o.custId === custId).sort((a, b) => b.id - a.id);
  const inProgress = mine.filter((o) => o.status === "CREATED" || o.status === "RECEIVED" || o.status === "READY");

  const balBg = bal > 0 ? "var(--color-orangelight)" : bal < 0 ? "var(--color-bluelight)" : "var(--color-card)";
  const balBd = bal > 0 ? "var(--color-orangeborder)" : bal < 0 ? "var(--color-blueborder)" : "var(--color-cardborder)";
  const balFg = bal > 0 ? "var(--color-orangedeep)" : bal < 0 ? "var(--color-bluetext)" : "var(--color-ink)";

  return (
    <div className="flex min-h-dvh flex-col">
      <TopBar title="" onBack={nav.back} />
      <div className="flex flex-col gap-3.5 px-4 pb-10">
        {/* header */}
        <div className="flex items-center gap-3">
          <Avatar text={Sel.initials(c.name)} size={52} bg="var(--color-neutralfill)" fg="var(--color-inksecondary)" />
          <div className="min-w-0 flex-1">
            <h1 className="bric text-[24px]">{c.name}</h1>
            <div className="text-[13px] text-muted">
              +91 {Sel.fmtPhone(c.phone)} · {Sel.countNoun(Sel.orderCount(state, c), "order")}
            </div>
            {c.address !== "" ? <div className="text-[13px] text-muted">{c.address}</div> : null}
          </div>
        </div>
        <div className="flex w-full gap-2">
          <OutlineButton h={48} className="flex-1" onClick={() => nav.openNewOrder({ custId, from: "customer" })}>
            New order
          </OutlineButton>
          <OutlineButton
            h={48}
            className="flex-1"
            border="var(--color-cardborder)"
            fg="var(--color-ink)"
            onClick={() => setActive({ kind: "customerForm", editId: custId, ctx: "list" })}
          >
            Edit
          </OutlineButton>
        </div>

        {/* balance card */}
        <div
          className="flex w-full flex-col gap-2 rounded-2xl border p-4"
          style={{ background: balBg, borderColor: balBd }}
        >
          <span className="text-[14px] font-semibold" style={{ color: balFg }}>
            {bal > 0 ? `${nm} has to pay you` : bal < 0 ? `${nm} has paid extra (advance)` : "All clear"}
          </span>
          <span className="bric text-[30px]" style={{ color: balFg }}>{rupees(bal)}</span>
          <span className="text-[12px]" style={{ color: balFg }}>
            {bal > 0
              ? "Includes orders in progress at their current total."
              : bal < 0
                ? "Used automatically on the next bill."
                : "Nothing to collect."}
          </span>
          {bal > 0 ? (
            <div className="flex w-full gap-2">
              <PrimaryButton h={46} className="flex-1" onClick={() => setActive({ kind: "receive", custId })}>
                Receive payment
              </PrimaryButton>
              <OutlineButton
                h={46}
                className="flex-1"
                border="var(--color-orangeborder)"
                fg="var(--color-orangetext)"
                onClick={() => {
                  Analytics.reminderSent(custId, bal);
                  sendReminderOnWhatsApp(state, custId, bal);
                }}
              >
                Remind
              </OutlineButton>
            </div>
          ) : null}
        </div>
        <button
          type="button"
          onClick={() => setActive({ kind: "addOld", custId })}
          className="w-fit text-[14px] font-bold text-blue"
        >
          + Add old baaki from notebook
        </button>

        {inProgress.length > 0 ? (
          <>
            <span className="text-[12px] font-bold text-muted">ORDERS IN PROGRESS</span>
            {inProgress.map((o) => (
              <OrderMiniRow key={o.id} o={o} onClick={() => nav.openOrder(o.id)} />
            ))}
          </>
        ) : null}

        <Segmented
          options={[["Khata", tab === "khata"], ["Orders", tab === "orders"]]}
          onSelect={(i) => setTab(i === 0 ? "khata" : "orders")}
        />

        {tab === "khata" ? (
          (() => {
            const rows = khataRows(state, custId);
            if (rows.length === 0) return <span className="text-[14px] text-muted">No khata entries yet</span>;
            return rows.map((r) => (
              <AppCard key={r.key}>
                <div className="flex w-full items-center gap-3 p-3">
                  <div className="min-w-0 flex-1">
                    <div className="text-[15px] font-bold">{r.title}</div>
                    <div className="text-[12px] text-muted">{r.sub}</div>
                    <div className="text-[12px] font-semibold text-muted">{r.after}</div>
                  </div>
                  <span className="text-[16px] font-bold" style={{ color: r.amtColor }}>{r.amount}</span>
                </div>
              </AppCard>
            ));
          })()
        ) : mine.length === 0 ? (
          <span className="text-[14px] text-muted">No orders yet</span>
        ) : (
          mine.map((o) => <OrderMiniRow key={o.id} o={o} onClick={() => nav.openOrder(o.id)} />)
        )}
      </div>

      <SheetHost active={active} state={state} nav={nav} onOpen={setActive} onDismiss={() => setActive(null)} />
    </div>
  );
}

function statusStyle(s: Order["status"]): [string, string, string] {
  switch (s) {
    case "CREATED": return ["To pick up", "var(--color-orangelight)", "var(--color-orangetext)"];
    case "RECEIVED": return ["Received", "var(--color-neutralfill)", "var(--color-inksecondary)"];
    case "READY": return ["Ready", "var(--color-bluelight)", "var(--color-bluetext)"];
    case "DELIVERED": return ["Delivered", "var(--color-neutralfill)", "var(--color-inksecondary)"];
    case "CANCELLED": return ["Cancelled", "var(--color-neutralfill)", "var(--color-inksecondary)"];
  }
}

function OrderMiniRow({ o, onClick }: { o: Order; onClick: () => void }) {
  const [label, bg, fg] = statusStyle(o.status);
  return (
    <AppCard onClick={onClick}>
      <div className="flex w-full items-center gap-2.5 p-3">
        <div className="min-w-0 flex-1">
          <div className="flex items-center gap-2">
            <span className="text-[15px] font-bold">#{Sel.orderNo(o)}</span>
            <span className={cls("text-[14px] font-semibold text-inksecondary")}>
              {o.lines.length > 0 ? rupees(amtOf(o)) : "Not counted"}
            </span>
          </div>
          <div className="truncate text-[12px] text-muted">
            {AppDate.plain(o.pickupDate).split(", ")[1]}
            {" · "}
            {o.lines.length > 0 ? `${Sel.itemsLabel(o)} · ${Sel.svcLabel(o)}` : "Clothes counted at pickup"}
          </div>
        </div>
        <StatusPill label={label} bg={bg} fg={fg} />
      </div>
    </AppCard>
  );
}
