"use client";

import { useEffect } from "react";
import { usePathname, useRouter } from "next/navigation";
import { bootstrap, retryBoot } from "@/data/auth";
import { useAppStore } from "@/data/store";
import { initAnalytics } from "@/analytics/clevertap";
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
