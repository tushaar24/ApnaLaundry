import type { Metadata } from "next";
import { PublicBill } from "@/ui/publicBill";

/**
 * Customer-facing bill page — the link a shop sends on WhatsApp. No login:
 * the unguessable token is the access. Lives outside the (app) gate.
 */

export const metadata: Metadata = {
  title: "Your bill",
  robots: { index: false, follow: false },
};

export default async function BillLinkPage({ params }: { params: Promise<{ token: string }> }) {
  const { token } = await params;
  return <PublicBill token={token} />;
}
