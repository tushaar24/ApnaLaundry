"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { useAppStore, useLaundryState } from "@/data/store";
import * as Repo from "@/data/repository";
import { isDefaultShopName } from "@/domain/seed";
import { useScreenView } from "@/analytics/useScreenView";
import { Gate } from "@/ui/gate";
import { FieldBox, PrimaryButton } from "@/ui/basics";
import { IcCheck } from "@/ui/icons";
import { RatesView } from "@/ui/screens/rates";
import { ToastBar } from "@/ui/shell";

/**
 * Onboarding, after login and the paywall: 1) the laundry's name — skipped
 * when the shop is already named — then 2) the rate list, then Home.
 */
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
  // Decided once on entry, so naming the shop doesn't drop the back button.
  const [askName] = useState(() => isDefaultShopName(state.shop.name));
  const [step, setStep] = useState<"name" | "rates">(askName ? "name" : "rates");
  useScreenView("setup");

  return (
    <div className="mx-auto w-full max-w-[640px]">
      {step === "name" ? (
        <NameStep
          initial={isDefaultShopName(state.shop.name) ? "" : state.shop.name}
          onNext={(name) => {
            Repo.updateShop(name, state.shop.expressPct);
            setStep("rates");
          }}
        />
      ) : (
        <RatesView
          from="setup"
          onDone={() => {
            setSetupDone(true);
            router.replace("/");
          }}
          onBack={askName ? () => setStep("name") : undefined}
        />
      )}
      <ToastBar />
    </div>
  );
}

function NameStep({ initial, onNext }: { initial: string; onNext: (name: string) => void }) {
  const [shopName, setShopName] = useState(initial);
  const named = shopName.trim() !== "";

  function next() {
    if (named) onNext(shopName.trim());
  }

  return (
    <div className="flex min-h-dvh flex-col">
      <form
        className="flex flex-1 flex-col gap-4 px-4 py-6"
        onSubmit={(e) => { e.preventDefault(); next(); }}
      >
        <div className="flex items-center gap-2">
          <span className="text-blue"><IcCheck size={18} /></span>
          <span className="text-[13px] font-semibold text-muted">Trial started · Step 1 of 2</span>
        </div>
        <h1 className="bric text-[28px]">What is your laundry called?</h1>
        <p className="text-[14px] text-muted">This name goes on every bill you send to customers.</p>
        <FieldBox
          value={shopName}
          onChange={setShopName}
          placeholder="e.g. Sharma Laundry"
          h={56}
          borderColor="var(--color-blue)"
          borderWidth={2}
          autoFocus
          inputProps={{ autoCapitalize: "words", enterKeyHint: "next" }}
        />
      </form>
      <div className="sticky bottom-0 w-full border-t border-divider bg-card p-4">
        <PrimaryButton onClick={next} disabled={!named}>Next</PrimaryButton>
      </div>
    </div>
  );
}
