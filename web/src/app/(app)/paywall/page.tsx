"use client";

import { Suspense, useEffect } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { useBillingStore } from "@/data/billingStore";
import { PaywallScreen } from "@/ui/screens/paywall";
import { IcLaundry } from "@/ui/icons";

/** Full-screen (mobile) / centred-modal (desktop) paywall route. */
export default function PaywallPage() {
  return (
    <Suspense fallback={<Splash />}>
      <PaywallRoute />
    </Suspense>
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

function PaywallRoute() {
  const router = useRouter();
  const raw = useSearchParams().get("reason") || "upsell";
  const reason = (["limit", "trial", "upsell"].includes(raw) ? raw : "upsell") as "limit" | "trial" | "upsell";
  const refresh = useBillingStore((s) => s.refresh);
  const loaded = useBillingStore((s) => s.loaded);

  useEffect(() => { void refresh(); }, [refresh]);

  if (!loaded) return <Splash />;

  return (
    <div className="min-h-dvh bg-bg">
      <PaywallScreen
        reason={reason}
        onClose={() => router.push("/")}
        onDone={(continuing) => router.push(continuing ? "/orders/new?from=home" : "/")}
      />
    </div>
  );
}
