"use client";

import { useEffect } from "react";
import { useRouter } from "next/navigation";
import { hasAccess, useBillingStore } from "@/data/billingStore";
import { ManagePlanScreen } from "@/ui/screens/paywall";
import { IcLaundry } from "@/ui/icons";

/**
 * Manage plan (subscribed users, opened from Settings): plan summary + cancel.
 * Unsubscribed users never get here — the Gate hard-gates them on the ₹2-trial
 * paywall — so with no active subscription this just goes home.
 */
export default function SubscriptionPage() {
  const router = useRouter();
  const refresh = useBillingStore((s) => s.refresh);
  const loaded = useBillingStore((s) => s.loaded);
  const status = useBillingStore((s) => s.status);
  // Active, or cancelled but still inside the paid period (shows when it ends).
  const canManage = !!status && hasAccess(status);

  useEffect(() => { void refresh(); }, [refresh]);
  useEffect(() => { if (loaded && !canManage) router.replace("/"); }, [loaded, canManage, router]);

  if (!loaded || !canManage) return <Splash />;

  return (
    <ManagePlanScreen
      onClose={() => {
        if (window.history.length > 1) router.back();
        else router.push("/settings");
      }}
    />
  );
}

function Splash() {
  return (
    <div className="flex min-h-dvh items-center justify-center bg-bg">
      <div className="flex size-12 items-center justify-center rounded-2xl bg-blue text-ondark">
        <IcLaundry size={26} />
      </div>
    </div>
  );
}
