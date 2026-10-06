"use client";

import { useRouter } from "next/navigation";
import { useAppStore } from "@/data/store";
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
  useScreenView("setup");
  return (
    <div className="mx-auto w-full max-w-[640px]">
      <RatesView
        from="setup"
        onDone={() => {
          setSetupDone(true);
          router.replace("/");
        }}
        onBack={() => undefined}
      />
      <ToastBar />
    </div>
  );
}
