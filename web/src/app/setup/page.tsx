"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { useAppStore, useLaundryState } from "@/data/store";
import * as Repo from "@/data/repository";
import { Analytics } from "@/analytics/events";
import { useScreenView } from "@/analytics/useScreenView";
import { Gate } from "@/ui/gate";
import { FieldBox, PrimaryButton } from "@/ui/basics";
import { IcCheck } from "@/ui/icons";
import { ToastBar } from "@/ui/shell";

/**
 * First-run setup, after the paywall: just the laundry's name. The rate card
 * is pre-seeded and edited later from ₹ Rates, so it isn't part of setup.
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
  const [shopName, setShopName] = useState("");
  const named = shopName.trim() !== "";
  useScreenView("setup");

  function done() {
    if (!named) return;
    Repo.updateShop(shopName.trim(), state.shop.expressPct);
    Analytics.setupCompleted(state.services.length, state.shop.expressPct);
    setSetupDone(true);
    router.replace("/");
  }

  return (
    <div className="mx-auto flex min-h-dvh w-full max-w-[640px] flex-col">
      <form
        className="flex flex-1 flex-col gap-4 px-4 py-6"
        onSubmit={(e) => { e.preventDefault(); done(); }}
      >
        <div className="flex items-center gap-2">
          <span className="text-blue"><IcCheck size={18} /></span>
          <span className="text-[13px] font-semibold text-muted">Trial started · Last step</span>
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
          inputProps={{ autoCapitalize: "words", enterKeyHint: "done" }}
        />
      </form>
      <div className="sticky bottom-0 w-full border-t border-divider bg-card p-4">
        <PrimaryButton onClick={done} disabled={!named}>Start taking orders</PrimaryButton>
      </div>
      <ToastBar />
    </div>
  );
}
