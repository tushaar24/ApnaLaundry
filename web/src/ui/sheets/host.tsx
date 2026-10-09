"use client";

import type { LaundryState } from "@/domain/models";
import type { useNav } from "../shell";
import type { ActiveSheet } from "./types";
import {
  BillViewSheet, CancelSheet, CollectPaymentSheet, CountClothesSheet, DeleteOrderSheet, ReadySheet, RescheduleSheet,
} from "./orderSheets";
import { AddOldBaakiSheet, CustomerFormSheet, ReceivePaymentSheet } from "./customerSheets";
import { OrderMenuSheet, ShareSummarySheet } from "./menuShare";

/** Renders whichever sheet is active. Screens just set/clear `active`. Port of SheetHost. */
export function SheetHost({
  active, state, nav, onOpen, onDismiss,
}: {
  active: ActiveSheet | null;
  state: LaundryState;
  nav: ReturnType<typeof useNav>;
  onOpen: (s: ActiveSheet) => void;
  onDismiss: () => void;
}) {
  if (!active) return null;
  switch (active.kind) {
    case "menu":
      return <OrderMenuSheet state={state} orderId={active.orderId} nav={nav} onOpen={onOpen} onDismiss={onDismiss} />;
    case "pay":
      return <CollectPaymentSheet state={state} orderId={active.orderId} onDismiss={onDismiss} />;
    case "ready":
      return <ReadySheet state={state} orderId={active.orderId} onDismiss={onDismiss} />;
    case "count":
      return <CountClothesSheet state={state} orderId={active.orderId} next={active.next} onDismiss={onDismiss} />;
    case "reschedule":
      return <RescheduleSheet state={state} orderId={active.orderId} which={active.which} onDismiss={onDismiss} />;
    case "cancel":
      return <CancelSheet state={state} orderId={active.orderId} onDismiss={onDismiss} />;
    case "deleteOrder":
      return <DeleteOrderSheet state={state} orderId={active.orderId} nav={nav} onDismiss={onDismiss} />;
    case "customerForm":
      return <CustomerFormSheet state={state} form={active} onDismiss={onDismiss} />;
    case "receive":
      return <ReceivePaymentSheet state={state} custId={active.custId} onDismiss={onDismiss} />;
    case "addOld":
      return <AddOldBaakiSheet state={state} custId={active.custId} onDismiss={onDismiss} />;
    case "billView":
      return <BillViewSheet state={state} orderId={active.orderId} onDismiss={onDismiss} />;
    case "share":
      return <ShareSummarySheet text={active.text} onDismiss={onDismiss} />;
  }
}
