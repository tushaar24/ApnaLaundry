import type { OrderStatus } from "@/domain/models";

/** Which bottom sheet (if any) is currently open, plus its target. Port of ActiveSheet.kt. */
export type ActiveSheet =
  | { kind: "menu"; orderId: number }
  | { kind: "pay"; orderId: number }
  | { kind: "count"; orderId: number; next: OrderStatus }
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
      onSaved?: (id: string) => void;
    }
  | { kind: "receive"; custId: string }
  | { kind: "addOld"; custId: string }
  | { kind: "billView"; orderId: number }
  | { kind: "share"; text: string };
