"use client";

import { Gate } from "@/ui/gate";

/**
 * Authenticated app zone. The Gate resolves login → subscription → setup → app:
 * a trial user without an active subscription is hard-gated (before setup too),
 * while the free-orders variant uses a soft paywall inside the app.
 */
export default function AppLayout({ children }: { children: React.ReactNode }) {
  return <Gate zone="app">{children}</Gate>;
}
