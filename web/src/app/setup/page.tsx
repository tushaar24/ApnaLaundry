"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { useAppStore, useLaundryState } from "@/data/store";
import * as Repo from "@/data/repository";
import { cleanName } from "@/domain/billDetails";
import type { OnboardingStep } from "@/domain/models";
import { isDefaultShopName } from "@/domain/seed";
import { Analytics } from "@/analytics/events";
import { useScreenView } from "@/analytics/useScreenView";
import { Gate } from "@/ui/gate";
import { RatesView } from "@/ui/screens/rates";
import { ToastBar } from "@/ui/shell";
import { IntroStep, NameStep, StepBar } from "@/ui/onboarding/steps";
import { BillDesignScreen } from "@/ui/onboarding/billDesign";

/**
 * Onboarding, after login and the paywall (handoff §1):
 *   Intro → 1 · Laundry name → 2 · Services & rates → 3 · Your bill → Home.
 * The current step is saved on the shop after every move, so a reload, a
 * killed tab or another device resumes exactly there.
 */
export default function SetupPage() {
  return (
    <Gate zone="setup">
      <SetupScreen />
    </Gate>
  );
}

type Step = Exclude<OnboardingStep, "" | "done">;

function SetupScreen() {
  const router = useRouter();
  const setSetupDone = useAppStore((s) => s.setSetupDone);
  const state = useLaundryState();
  const saved = state.shop.onboardingStep;
  // Shops from before steps were tracked ("") start at the intro.
  const [step, setStep] = useState<Step>(saved === "" || saved === "done" ? "intro" : saved);
  useScreenView("setup");

  const goTo = (s: Step) => {
    setStep(s);
    Repo.setOnboardingStep(s);
    window.scrollTo(0, 0);
  };

  const finish = () => {
    Repo.setOnboardingStep("done");
    Analytics.setupCompleted(state.services.length, state.shop.expressPct);
    setSetupDone(true);
    router.replace("/");
    Repo.showInfo("All set! Take your first order.");
  };

  // Finished on another device while this one sat on setup.
  useEffect(() => {
    if (saved === "done") {
      setSetupDone(true);
      router.replace("/");
    }
  }, [saved, setSetupDone, router]);

  return (
    <div className="mx-auto w-full max-w-[640px]">
      {step === "intro" ? (
        <IntroStep onStart={() => goTo("name")} />
      ) : step === "name" ? (
        <NameStep
          initial={isDefaultShopName(state.shop.name) ? "" : state.shop.name}
          onBack={() => goTo("intro")}
          onNext={(name) => {
            Repo.updateShopDetails({ name: cleanName(name) });
            goTo("services");
          }}
        />
      ) : step === "services" ? (
        <RatesView
          from="setup"
          header={<StepBar step={2} onBack={() => goTo("name")} />}
          onDone={() => goTo("bill")}
        />
      ) : (
        <BillDesignScreen mode="onboarding" onBack={() => goTo("services")} onDone={finish} />
      )}
      <ToastBar />
    </div>
  );
}
