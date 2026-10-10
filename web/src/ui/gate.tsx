"use client";

import { useEffect, useState } from "react";
import { usePathname, useRouter } from "next/navigation";
import { bootstrap, retryBoot } from "@/data/auth";
import { useAppStore } from "@/data/store";
import { prefs } from "@/data/prefs";
import { paywallInfo, useBillingStore } from "@/data/billingStore";
import { initAnalytics } from "@/analytics/clevertap";
import { PaywallScreen } from "@/ui/screens/paywall";
import { PrimaryButton } from "./basics";
import { IcLaundry } from "./icons";

/**
 * Client-side auth/billing/setup gate (the web port of AppNavGraph's start-
 * destination logic). Resolves, in order:
 *   1. login        — !authed → /login
 *   2. subscription — an authed user without an active subscription (and not
 *      in grace) hits the non-cancellable ₹2-trial paywall BEFORE setup (both
 *      the setup and app zones pass through here). ONLY an active subscription (or a
 *      cancelled one still in the period it paid for)
 *      gets in. A failed status check is retried; if it still fails, a recent
 *      cached "active" result (prefs.subActiveCached, 7 days) lets the owner
 *      in, otherwise a "try again" screen shows. Never fails open.
 *   3. setup        — authed && !setupDone → /setup
 *   4. app          — otherwise
 * Also bootstraps the online store (initial pull) on load.
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

function BootErrorScreen({ message, onRetry }: { message: string; onRetry?: () => void }) {
  return (
    <div className="flex min-h-dvh flex-col items-center justify-center gap-4 bg-bg px-8 text-center">
      <div className="bric text-[24px]">Can&apos;t reach the server</div>
      <p className="max-w-sm text-[14px] text-muted">
        The website needs internet to load your shop. Check your connection and try again.
        <span className="mt-1 block text-[12px] text-faint">{message}</span>
      </p>
      <PrimaryButton className="max-w-[240px]" onClick={onRetry ?? (() => void retryBoot())}>
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

  // Subscription state. For authed users (the setup and app zones) the
  // subscription is resolved before setup, so the hard gate sits between
  // login and setup. The login zone never checks billing.
  const billingLoaded = useBillingStore((s) => s.loaded);
  const billingStatus = useBillingStore((s) => s.status);
  const billingFailed = useBillingStore((s) => s.failed);
  const refreshBilling = useBillingStore((s) => s.refresh);
  const [gated, setGated] = useState<boolean | null>(null);

  useEffect(() => {
    initAnalytics();
    void bootstrap();
  }, []);

  useEffect(() => {
    if (authed && zone !== "login") void refreshBilling();
  }, [authed, zone, refreshBilling]);

  // Latch the gating decision once billing first loads, so the success → "Done"
  // screen isn't skipped the instant the subscription goes active.
  useEffect(() => {
    if (zone === "login" || !authed || !billingLoaded || gated !== null) return;
    if (billingStatus) {
      setGated(paywallInfo(billingStatus).blocked);
      return;
    }
    // Billing unreachable: only a recently confirmed active subscription gets in.
    if (billingFailed && prefs.subActiveCached) setGated(false);
  }, [zone, authed, billingLoaded, billingStatus, billingFailed, gated]);

  useEffect(() => {
    if (!hydrated) return;
    if (!authed) {
      // Carry the deep link along so login can land back on it.
      if (zone !== "login") {
        const here = window.location.pathname + window.location.search;
        router.replace(here !== "/" ? `/login?returnTo=${encodeURIComponent(here)}` : "/login");
      }
      return;
    }
    // Authed on the login page → back to the deep link that brought us here
    // (internal paths only — no "//host"), or the app; billing/setup resolve there.
    if (zone === "login") {
      const raw = new URLSearchParams(window.location.search).get("returnTo") ?? "";
      router.replace(raw.startsWith("/") && !raw.startsWith("//") ? raw : "/");
      return;
    }
    // Subscription before setup: wait for the billing decision, and while the
    // hard gate is up don't route anywhere (the paywall is shown below).
    if (gated === null || gated) return;
    if (!setupDone) {
      if (zone !== "setup") router.replace("/setup");
      return;
    }
    if (zone !== "app") router.replace("/");
  }, [hydrated, authed, setupDone, zone, gated, router, pathname]);

  if (!hydrated) return <Splash />;
  if (!authed) return zone === "login" ? <>{children}</> : <Splash />;
  // Authed on the login page: redirecting to the app.
  if (zone === "login") return <Splash />;

  // Authed in the setup/app zones: a boot/sync failure, then the subscription
  // gate, then setup/app.
  if (bootError) return <BootErrorScreen message={bootError} />;
  if (gated === null && !billingStatus && billingFailed) {
    return <BootErrorScreen message="Couldn't check your subscription" onRetry={() => void refreshBilling()} />;
  }
  if (!billingLoaded || gated === null) return <Splash />;
  if (gated) {
    return (
      // Once active, the routing effect above sends a brand-new shop to /setup.
      <PaywallScreen onDone={() => setGated(false)} />
    );
  }
  if (!setupDone) return zone === "setup" ? <>{children}</> : <Splash />;
  return zone === "app" ? <>{children}</> : <Splash />;
}
