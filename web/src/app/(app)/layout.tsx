"use client";

import { BillingGate, Gate } from "@/ui/gate";

/**
 * Authenticated app zone: requires login + setup done (Gate), then an active
 * subscription for the trial variant (BillingGate — a hard gate), otherwise the
 * free-orders variant uses a soft paywall inside the app.
 */
export default function AppLayout({ children }: { children: React.ReactNode }) {
  return (
    <Gate zone="app">
      <BillingGate>{children}</BillingGate>
    </Gate>
  );
}
