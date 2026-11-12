import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { PlanPage } from "@/ui/planPage";

/**
 * Fixed-price plan links — mylaundry.work/plan/annual/<userId> (₹799 a year) and
 * /plan/monthly/<userId> (₹99 a month): the app's own plans, paid in the browser
 * through Razorpay Checkout (UPI mandate), for owners reached on WhatsApp or by a
 * call. No login: the owner's user id is the access. Lives outside the (app) gate.
 */

export const metadata: Metadata = {
  title: "MyLaundry plan",
  robots: { index: false, follow: false },
};

export default async function PlanLinkPage({ params }: { params: Promise<{ plan: string; id: string }> }) {
  const { plan, id } = await params;
  if (plan !== "annual" && plan !== "monthly") notFound();
  return <PlanPage id={id} plan={plan as "annual" | "monthly"} />;
}
