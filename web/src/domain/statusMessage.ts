import * as AppDate from "@/core/appdate";
import { rupees } from "@/core/money";
import { amtOf } from "./laundryMath";
import type { Order } from "./models";
import { orderNo } from "./selectors";

/**
 * The WhatsApp update a customer gets when their order changes status
 * (asterisks render bold). The owner sends it from the toast after the
 * change — nothing is sent by itself. Port of StatusMessage.kt.
 */
export function statusMessage(shopName: string, customerName: string, o: Order): string {
  const first = customerName.startsWith("+") ? customerName : customerName.split(" ")[0];
  const hi = `Hi ${first}, `;
  const no = `#${orderNo(o)}`;
  const shop = `*${shopName}*`;
  switch (o.status) {
    case "CREATED":
      return hi + `we'll pick up your clothes for order ${no}` + (o.pickupDate ? ` on ${AppDate.plain(o.pickupDate)}` : "") + `. — ${shop}`;
    case "RECEIVED":
      return (
        hi +
        (o.pickup === "HOME" ? `we've picked up your clothes for order ${no} — ${shop}.` : `we've received your clothes at ${shop} (order ${no}).`) +
        (o.deliveryDate ? ` They'll be ready by ${AppDate.plain(o.deliveryDate)}.` : " We'll message you when they're ready.")
      );
    case "READY":
      return (
        hi +
        `your clothes are ready at ${shop} (order ${no}).` +
        (o.lines.length > 0 ? ` Total *${rupees(amtOf(o))}*.` : "") +
        (o.delivery === "HOME"
          ? o.deliveryDate ? ` We'll deliver them on ${AppDate.plain(o.deliveryDate)}.` : " We'll deliver them soon."
          : " You can collect them anytime.")
      );
    case "DELIVERED":
      return hi + `your order ${no} has been delivered. Thank you for choosing ${shop}!`;
    case "CANCELLED":
      return hi + `your order ${no} at ${shop} has been cancelled.`;
  }
}

/** wa.me link that opens the customer's chat with [text] typed in. */
export function waLink(phone: string, text: string): string {
  const digits = phone.replace(/\D/g, "").slice(-10);
  const t = encodeURIComponent(text);
  return digits.length === 10 ? `https://wa.me/91${digits}?text=${t}` : `https://wa.me/?text=${t}`;
}
