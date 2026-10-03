"use client";

import * as AppDate from "@/core/appdate";
import { rupees } from "@/core/money";
import { amtOf } from "@/domain/laundryMath";
import type { LaundryState, Order } from "@/domain/models";
import * as Sel from "@/domain/selectors";
import { cls, ExpressTag } from "./basics";
import { IcCall, IcHome, IcMore, IcStore } from "./icons";

/** Port of ui/components/OrderCard.kt — the home-list card with next action. */
export function OrderCard({
  state, order, pickupTab, late = false, onOpen, onAct, onMore,
}: {
  state: LaundryState;
  order: Order;
  pickupTab: boolean;
  late?: boolean;
  onOpen: () => void;
  onAct: () => void;
  onMore: () => void;
}) {
  const o = order;
  const c = Sel.customer(state, o.custId);
  const counted = o.lines.length > 0;
  const home = pickupTab ? o.pickup === "HOME" : o.delivery === "HOME";
  const amt = amtOf(o);
  const addr = c.address || "address not saved";

  const where = pickupTab
    ? home
      ? `Pick up from ${addr}` + (o.pickupTime ? ` · ${o.pickupTime}` : "")
      : "Walk-in at shop"
    : home
      ? `Deliver to ${addr}` + (o.deliveryTime ? ` · ${o.deliveryTime}` : "")
      : "Customer collects at shop" + (o.deliveryTime ? ` · by ${o.deliveryTime}` : "");
  const showWhere = home || (!pickupTab && o.deliveryTime !== "");

  const inWork = o.status === "CREATED" || o.status === "RECEIVED" || o.status === "READY";
  const warn =
    !pickupTab && o.deliveryDate === "" && inWork
      ? "No delivery date · tap ⋯ to set"
      : late
        ? `Late · was due ${AppDate.plain(pickupTab ? o.pickupDate : o.deliveryDate)}`
        : !pickupTab && o.status === "CREATED"
          ? "Not picked up yet"
          : !pickupTab && o.status === "RECEIVED"
            ? "Not ready yet"
            : "";

  // payment label
  let payLabel: string;
  let payTone: string;
  if (o.status === "DELIVERED") {
    if (o.paid >= amt) { payLabel = "Paid"; payTone = "text-bluetext"; }
    else if (o.paid > 0) { payLabel = "Part paid"; payTone = "text-orangetext"; }
    else { payLabel = "In khata"; payTone = "text-orangetext"; }
  } else if (o.pre >= amt && amt > 0) { payLabel = "Paid"; payTone = "text-bluetext"; }
  else if (o.pre > 0) { payLabel = `${rupees(amt - o.pre)} more to collect`; payTone = "text-orangetext"; }
  else { payLabel = "Unpaid"; payTone = "text-orangetext"; }

  const actLabel =
    o.status === "CREATED" ? "Mark picked up"
      : o.status === "RECEIVED" ? "Mark ready"
        : o.status === "READY" ? "Mark delivered"
          : "";
  const canAct = actLabel !== "";
  const doneText =
    o.status === "DELIVERED"
      ? "Delivered" + (o.doneAt ? ` · ${o.doneAt}` : "")
      : o.status === "CANCELLED"
        ? "Cancelled" + (o.cancelReason ? ` · ${o.cancelReason}` : "")
        : "";

  const meta =
    `#${o.id} · ` +
    (counted
      ? `${Sel.itemsLabel(o)} · ${Sel.svcLabel(o)}`
      : o.pickup === "HOME" && o.status === "CREATED"
        ? "Clothes will be counted at pickup"
        : (o.pieces > 0 ? `${o.pieces} pieces · ` : "") + "Bill not made yet");

  return (
    <div
      className={cls(
        "rounded-2xl border bg-card",
        late ? "border-orangeborder" : "border-cardborder",
      )}
    >
      <div className="flex flex-col gap-[5px] px-3.5 pb-3 pt-3.5">
        <button type="button" onClick={onOpen} className="flex w-full items-center text-left">
          <span className="flex min-w-0 flex-1 items-center gap-2">
            <span className="truncate text-[17px] font-bold">{c.name}</span>
            {o.express ? <ExpressTag /> : null}
          </span>
          {counted ? <span className="text-[17px] font-bold">{rupees(amt)}</span> : null}
        </button>
        <div className="flex w-full items-start justify-between gap-2">
          <span className="flex-1 text-[13px] text-muted">{meta}</span>
          {counted && o.status !== "CANCELLED" ? (
            <span className={cls("text-[13px] font-bold", payTone)}>{payLabel}</span>
          ) : null}
        </div>
        {showWhere ? (
          <div className="flex items-start gap-1.5 text-inksecondary">
            <span className="mt-0.5 shrink-0">{home ? <IcHome size={15} /> : <IcStore size={15} />}</span>
            <span className="text-[14px]">{where}</span>
          </div>
        ) : null}
        {warn ? (
          <div className="flex items-center gap-1.5">
            <span className="size-[7px] rounded-full bg-orange" />
            <span className="text-[13px] font-bold text-orangetext">{warn}</span>
          </div>
        ) : null}
        <div className="mt-1.5 flex w-full items-center gap-2">
          {canAct ? (
            <button
              type="button"
              onClick={onAct}
              className="h-[46px] flex-1 rounded-xl bg-bluelight text-[15px] font-bold text-blue transition-colors hover:bg-blueborder/60"
            >
              {actLabel}
            </button>
          ) : (
            <div className="flex h-[46px] flex-1 items-center rounded-xl bg-bg px-3 text-[14px] font-semibold text-muted">
              {doneText}
            </div>
          )}
          {home && canAct ? (
            <a
              href={`tel:+91${c.phone}`}
              aria-label="Call"
              className="flex size-[46px] items-center justify-center rounded-xl border border-cardborder bg-card text-inksecondary"
            >
              <IcCall size={20} />
            </a>
          ) : null}
          <button
            type="button"
            onClick={onMore}
            aria-label="More"
            className="flex size-[46px] items-center justify-center rounded-xl border border-cardborder bg-card text-inksecondary"
          >
            <IcMore size={20} />
          </button>
        </div>
      </div>
    </div>
  );
}
