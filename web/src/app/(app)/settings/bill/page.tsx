"use client";

import { useScreenView } from "@/analytics/useScreenView";
import { Shell, useNav } from "@/ui/shell";
import { BillDesignScreen } from "@/ui/onboarding/billDesign";

/** Settings → Bill design & details: onboarding step 3 in edit mode. */
export default function BillDesignPage() {
  const nav = useNav();
  useScreenView("bill_design");
  return (
    <Shell tab={null} showMobileNav={false}>
      <BillDesignScreen mode="settings" onBack={nav.back} onDone={nav.back} />
    </Shell>
  );
}
