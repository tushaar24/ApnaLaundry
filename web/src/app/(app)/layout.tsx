"use client";

import { Gate } from "@/ui/gate";

/**
 * Authenticated app zone. The Gate resolves login → subscription → setup → app:
 * a user without an active subscription is hard-gated on the ₹2-trial paywall
 * (before setup too).
 */
export default function AppLayout({ children }: { children: React.ReactNode }) {
  return <Gate zone="app">{children}</Gate>;
}
