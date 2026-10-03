"use client";

import { Suspense, useEffect } from "react";
import { useSearchParams } from "next/navigation";
import { Analytics } from "@/analytics/events";
import { useScreenView } from "@/analytics/useScreenView";
import { RatesView } from "@/ui/screens/rates";
import { Shell, useNav } from "@/ui/shell";

/** Rate card editor opened from Home / Settings (rates/{from} in the app). */
export default function RatesPage() {
  return (
    <Shell tab={null} showMobileNav={false}>
      <Suspense>
        <RatesScreen />
      </Suspense>
    </Shell>
  );
}

function RatesScreen() {
  const nav = useNav();
  const from = useSearchParams().get("from") ?? "home";
  useScreenView("rates");
  useEffect(() => {
    Analytics.ratesOpened(from);
  }, [from]);
  return <RatesView from="edit" onDone={nav.back} onBack={nav.back} />;
}
