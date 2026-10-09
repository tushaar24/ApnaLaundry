"use client";

import type { ReactNode } from "react";
import type { LaundryState } from "@/domain/models";
import * as Sel from "@/domain/selectors";
import * as Repo from "@/data/repository";
import { cls, PrimaryButton } from "../basics";
import { AppSheet } from "../sheet";
import {
  IcCalendar, IcCall, IcCancel, IcCheck, IcDelete, IcEdit, IcEye, IcPerson, IcReceipt, IcTruck,
} from "../icons";
import type { ActiveSheet } from "./types";
import type { useNav } from "../shell";

/** Ports of ui/sheets/MenuAndShare.kt. */

function MenuRow({
  icon, label, danger, onClick,
}: { icon: ReactNode; label: string; danger?: boolean; onClick: () => void }) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={cls(
        "flex h-[52px] w-full items-center gap-3.5 text-left",
        danger ? "text-orangetext" : "text-ink",
      )}
    >
      <span className={danger ? "text-orangetext" : "text-inksecondary"}>{icon}</span>
      <span className="text-[16px] font-semibold">{label}</span>
    </button>
  );
}

export function OrderMenuSheet({
  state, orderId, nav, onOpen, onDismiss,
}: {
  state: LaundryState;
  orderId: number;
  nav: ReturnType<typeof useNav>;
  onOpen: (s: ActiveSheet) => void;
  onDismiss: () => void;
}) {
  const o = Sel.order(state, orderId);
  const c = Sel.customer(state, o?.custId ?? "");
  if (!o) { onDismiss(); return null; }

  return (
    <AppSheet title={c.name} subtitle={`#${o.id} · +91 ${Sel.fmtPhone(c.phone)}`} onDismiss={onDismiss}>
      <div className="flex flex-col">
        <MenuRow icon={<IcEye size={22} />} label="View order details" onClick={() => { onDismiss(); nav.openOrder(o.id); }} />
        {o.lines.length > 0 ? (
          <MenuRow icon={<IcReceipt size={22} />} label="View / send bill" onClick={() => { onDismiss(); nav.openBill(o.id); }} />
        ) : null}
        <MenuRow icon={<IcEdit size={22} />} label="Edit order / bill" onClick={() => { onDismiss(); nav.openNewOrder({ editId: o.id, from: "home" }); }} />
        {o.status === "CREATED" || o.status === "RECEIVED" ? (
          <MenuRow icon={<IcCheck size={22} />} label="Mark delivered now" onClick={() => onOpen({ kind: "pay", orderId: o.id })} />
        ) : null}
        {o.status === "CREATED" ? (
          <MenuRow icon={<IcCalendar size={22} />} label="Reschedule pickup" onClick={() => onOpen({ kind: "reschedule", orderId: o.id, which: "pickup" })} />
        ) : null}
        {o.status === "CREATED" || o.status === "RECEIVED" || o.status === "READY" ? (
          <MenuRow
            icon={<IcTruck size={22} />}
            label={o.deliveryDate !== "" ? "Reschedule delivery" : "Set delivery date"}
            onClick={() => onOpen({ kind: "reschedule", orderId: o.id, which: "drop" })}
          />
        ) : null}
        <MenuRow icon={<IcPerson size={22} />} label="Open customer khata" onClick={() => { onDismiss(); nav.openCustomer(o.custId); }} />
        <a href={`tel:+91${c.phone}`} onClick={onDismiss} className="flex h-[52px] w-full items-center gap-3.5 text-ink">
          <span className="text-inksecondary"><IcCall size={22} /></span>
          <span className="text-[16px] font-semibold">Call {Sel.firstName(c.name)}</span>
        </a>
        {o.status === "CREATED" ? (
          <MenuRow icon={<IcCancel size={22} />} label="Cancel this pickup" danger onClick={() => onOpen({ kind: "cancel", orderId: o.id })} />
        ) : null}
        <MenuRow
          icon={<IcDelete size={22} />}
          label={o.lines.length > 0 ? "Delete bill" : "Delete order"}
          danger
          onClick={() => onOpen({ kind: "deleteOrder", orderId: o.id })}
        />
      </div>
    </AppSheet>
  );
}

export function ShareSummarySheet({ text, onDismiss }: { text: string; onDismiss: () => void }) {
  return (
    <AppSheet title="Share summary" onDismiss={onDismiss}>
      <div className="flex flex-col gap-3.5">
        <div className="whitespace-pre-wrap rounded-[14px] bg-card p-3.5 text-[14px] font-semibold text-inksecondary">
          {text}
        </div>
        <PrimaryButton
          h={54}
          onClick={() => {
            Repo.showInfo("Opening WhatsApp with this message…");
            window.open(`https://wa.me/?text=${encodeURIComponent(text)}`, "_blank", "noopener");
            onDismiss();
          }}
        >
          Send on WhatsApp
        </PrimaryButton>
      </div>
    </AppSheet>
  );
}
