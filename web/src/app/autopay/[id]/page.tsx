import type { Metadata } from "next";
import { AutopayPage } from "@/ui/autopayPage";

/**
 * Standalone AutoPay page — mylaundry.work/autopay/<userId>. For an owner who
 * can't finish the UPI AutoPay mandate inside the app: the same Razorpay
 * Checkout in the browser, plus the hosted link's QR to scan from any UPI app.
 * No login: the owner's user id (a UUID) is the access. Lives outside the (app) gate.
 */

export const metadata: Metadata = {
  title: "Set up AutoPay · MyLaundry",
  robots: { index: false, follow: false },
};

export default async function AutopayLinkPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  return <AutopayPage id={id} />;
}
