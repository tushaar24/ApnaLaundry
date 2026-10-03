"use client";

import { useEffect, useState } from "react";
import { usePathname, useRouter } from "next/navigation";
import { bootstrap, retryBoot } from "@/data/auth";
import { useAppStore } from "@/data/store";
import { paywallInfo, useBillingStore } from "@/data/billingStore";
import { initAnalytics } from "@/analytics/clevertap";
import { PaywallScreen } from "@/ui/screens/paywall";
import { PrimaryButton } from "./basics";
import { IcLaundry } from "./icons";

/**
 * Client-side auth/setup gate (the web port of AppNavGraph's start-
 * destination logic): !loggedIn → /login, loggedIn && !setupDone → /setup,
 * else the app. Also bootstraps the online store (initial pull) on load.
 */

function Splash() {
  return (
    <div className="flex min-h-dvh flex-col items-center justify-center gap-4 bg-bg">
      <div className="flex size-14 items-center justify-center rounded-2xl bg-blue text-ondark">
        <IcLaundry size={30} />
      </div>
      <div className="text-[14px] font-semibold text-muted">Loading your shop…</div>
    </div>
  );
}

function BootErrorScreen({ message }: { message: string }) {
  return (
    <div className="flex min-h-dvh flex-col items-center justify-center gap-4 bg-bg px-8 text-center">
      <div className="bric text-[24px]">Can&apos;t reach the server</div>
      <p className="max-w-sm text-[14px] text-muted">
        The website needs internet to load your shop. Check your connection and try again.
        <span className="mt-1 block text-[12px] text-faint">{message}</span>
      </p>
      <PrimaryButton className="max-w-[240px]" onClick={() => void retryBoot()}>
        Try again
      </PrimaryButton>
    </div>
  );
}

export function Gate({ children, zone }: { children: React.ReactNode; zone: "app" | "login" | "setup" }) {
  const router = useRouter();
  const pathname = usePathname();
  const hydrated = useAppStore((s) => s.hydrated);
  const authed = useAppStore((s) => s.authed);
  const setupDone = useAppStore((s) => s.setupDone);
  const bootError = useAppStore((s) => s.bootError);

  useEffect(() => {
    initAnalytics();
    void bootstrap();
  }, []);

  useEffect(() => {
    if (!hydrated) return;
    if (!authed) {
      if (zone !== "login") router.replace("/login");
      return;
    }
    if (!setupDone) {
      if (zone !== "setup") router.replace("/setup");
      return;
    }
    if (zone !== "app") router.replace("/");
  }, [hydrated, authed, setupDone, zone, router, pathname]);

  if (!hydrated) return <Splash />;
  if (bootError && zone === "app") return <BootErrorScreen message={bootError} />;
  if (!authed && zone !== "login") return <Splash />;
  if (authed && !setupDone && zone === "app") return <Splash />;
  if (authed && setupDone && zone !== "app") return <Splash />;

  return <>{children}</>;
}

/**
 * Subscription gate inside the authed app. Flow (both platforms): logged in →
 * check subscription → route; show a loader until the check resolves.
 *
 *  - trial_2 variant with no active subscription → a NON-cancellable paywall;
 *    the app isn't reachable until there's an active subscription.
 *  - free_<N> variant (or active sub, or billing unconfigured/unreachable) →
 *    the app renders; the free-orders paywall is soft (banner + new-order block).
 *
 * The decision is latched once billing first loads so the success → "Done"
 * screen isn't skipped the instant the subscription goes active.
 */
export function BillingGate({ children }: { children: React.ReactNode }) {
  const router = useRouter();
  const status = useBillingStore((s) => s.status);
  const loaded = useBillingStore((s) => s.loaded);
  const refresh = useBillingStore((s) => s.refresh);
  const [gated, setGated] = useState<boolean | null>(null);

  useEffect(() => { void refresh(); }, [refresh]);

  useEffect(() => {
    if (!loaded || gated !== null) return;
    const info = paywallInfo(status);
    setGated(info.isTrial && info.blocked && !info.hasActive);
  }, [loaded, status, gated]);

  if (!loaded || gated === null) return <Splash />;
  if (gated) {
    return (
      <PaywallScreen
        reason="trial"
        hardGate
        onClose={() => undefined}
        onDone={(continuing) => {
          setGated(false);
          // The Done button promised "+ Continue with new order" — honor it.
          if (continuing) router.push("/orders/new?from=home");
        }}
      />
    );
  }
  return <>{children}</>;
}
