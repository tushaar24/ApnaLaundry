"use client";

import { useEffect } from "react";
import { useLaundryState } from "@/data/store";
import { Gate } from "@/ui/gate";
import { preloadLogo } from "@/ui/billRender";

/**
 * Authenticated app zone. The Gate resolves login → subscription → setup → app:
 * a user without an active subscription is hard-gated on the ₹2-trial paywall
 * (before setup too).
 */
export default function AppLayout({ children }: { children: React.ReactNode }) {
  // Bills are drawn synchronously when shared, so load the logo up front.
  const logoId = useLaundryState().shop.logoId;
  useEffect(() => {
    void preloadLogo(logoId);
  }, [logoId]);
  return <Gate zone="app">{children}</Gate>;
}
