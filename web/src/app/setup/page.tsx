"use client";

import { useRouter } from "next/navigation";
import { useAppStore, useLaundryState } from "@/data/store";
import { Analytics } from "@/analytics/events";
import { useScreenView } from "@/analytics/useScreenView";
import { Gate } from "@/ui/gate";
import { RatesView } from "@/ui/screens/rates";
import { ToastBar } from "@/ui/shell";

/** First-run rate-card setup (rates/setup in the app's nav graph). */
export default function SetupPage() {
  return (
    <Gate zone="setup">
      <SetupScreen />
    </Gate>
  );
}

function SetupScreen() {
  const router = useRouter();
  const setSetupDone = useAppStore((s) => s.setSetupDone);
  const state = useLaundryState();
  useScreenView("setup");
  return (
    <div className="mx-auto w-full max-w-[640px]">
      <RatesView
        from="setup"
        onDone={() => {
          Analytics.setupCompleted(state.services.length, state.shop.expressPct);
          setSetupDone(true);
          router.replace("/");
        }}
        onBack={() => undefined}
      />
      <ToastBar />
    </div>
  );
}
