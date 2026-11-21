import type { OrderStatus } from "@/domain/models";

/** Which bottom sheet (if any) is currently open, plus its target. Port of ActiveSheet.kt. */
export type ActiveSheet =
  | { kind: "menu"; orderId: number }
  | { kind: "pay"; orderId: number }
  | { kind: "ready"; orderId: number }
  | { kind: "count"; orderId: number; next: OrderStatus; thenPay?: boolean } // thenPay: count, then collect
  | { kind: "changeStatus"; orderId: number }
  | { kind: "reschedule"; orderId: number; which: string } // "pickup" | "drop"
  | { kind: "cancel"; orderId: number }
  | { kind: "deleteOrder"; orderId: number }
  | {
      kind: "customerForm";
      editId: string | null;
      ctx: string; // "order" | "list"
      prefillName?: string;
      prefillPhone?: string;
      needAddress?: boolean;
      /** Where it was opened (analytics): customer_top_bar | customer_page | order_menu | bill | address_card | … */
      trigger?: string;
      onSaved?: (id: string) => void;
    }
  | { kind: "receive"; custId: string }
  | { kind: "addOld"; custId: string }
  | { kind: "billView"; orderId: number }
  | { kind: "share"; text: string };
