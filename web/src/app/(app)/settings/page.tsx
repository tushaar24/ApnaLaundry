"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import * as Repo from "@/data/repository";
import { logout } from "@/data/auth";
import { useLaundryState } from "@/data/store";
import { useScreenView } from "@/analytics/useScreenView";
import { AppCard, FieldBox, SectionLabel } from "@/ui/basics";
import { Shell, useNav } from "@/ui/shell";

/** Settings — port of ui/screens/settings/SettingsScreen.kt. */

export default function SettingsPage() {
  return (
    <Shell tab="settings">
      <SettingsScreen />
    </Shell>
  );
}

function SettingsScreen() {
  const state = useLaundryState();
  const nav = useNav();
  const router = useRouter();
  const shop = state.shop;
  const [name, setName] = useState(shop.name);
  const [loggingOut, setLoggingOut] = useState(false);
  useScreenView("settings");

  async function doLogout() {
    if (loggingOut) return;
    setLoggingOut(true);
    try {
      await logout();
      router.replace("/login");
    } catch (e) {
      Repo.showInfo(e instanceof Error ? e.message : "Couldn't log out — try again");
      setLoggingOut(false);
    }
  }

  return (
    <div className="flex min-h-dvh flex-col gap-4 p-4">
      <h1 className="bric text-[28px]">Settings</h1>

      {/* Shop name */}
      <div className="flex flex-col gap-2">
        <SectionLabel text="Shop name" />
        <FieldBox
          value={name}
          onChange={(v) => {
            setName(v);
            Repo.updateShop(v, shop.expressPct);
          }}
        />
      </div>

      {/* Rate card */}
      <AppCard onClick={() => nav.openRates("settings")}>
        <div className="flex w-full items-center justify-between p-4">
          <span className="flex flex-col">
            <span className="text-[16px] font-bold">Rate card</span>
            <span className="text-[13px] text-muted">Services and prices</span>
          </span>
          <span className="text-[15px] font-bold text-blue">Edit</span>
        </div>
      </AppCard>

      {/* Phone */}
      <AppCard>
        <div className="flex w-full justify-between p-4">
          <span className="text-[15px] font-semibold text-muted">Phone</span>
          <span className="text-[15px] font-bold">+91 {shop.phone}</span>
        </div>
      </AppCard>

      {/* Coming later */}
      <AppCard bg="var(--color-neutralfill)" borderColor="var(--color-neutralfill)">
        <div className="flex flex-col gap-1 p-4">
          <span className="text-[13px] font-bold text-muted">Coming later</span>
          <span className="text-[14px] text-inksecondary">GST on bills · shop logo · staff logins · Hindi</span>
        </div>
      </AppCard>

      <button type="button" onClick={() => void doLogout()} className="flex h-[52px] w-full items-center justify-center">
        <span className="text-[15px] font-bold text-orangetext">
          {loggingOut ? "Logging out…" : "Log out / restart"}
        </span>
      </button>
    </div>
  );
}
