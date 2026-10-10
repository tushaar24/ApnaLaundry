"use client";

import { usePathname, useRouter } from "next/navigation";
import type { ReactNode } from "react";
import { useAppStore } from "@/data/store";
import { undo } from "@/data/repository";
import { cls } from "./basics";
import { IcChart, IcLaundry, IcPeople, IcReceipt, IcSettings } from "./icons";

/**
 * Responsive chrome. Mobile (<lg) = the app's UI: content + fixed bottom
 * nav (Orders / Customers / Earnings / Settings). Desktop (≥lg) = left
 * sidebar with the same tabs, content centred.
 */

export type NavTabKey = "orders" | "customers" | "earnings" | "settings";

const TABS: { key: NavTabKey; label: string; href: string; icon: typeof IcReceipt }[] = [
  { key: "orders", label: "Orders", href: "/", icon: IcReceipt },
  { key: "customers", label: "Customers", href: "/customers", icon: IcPeople },
  { key: "earnings", label: "Earnings", href: "/earnings", icon: IcChart },
  { key: "settings", label: "Settings", href: "/settings", icon: IcSettings },
];

export function ToastBar() {
  const toast = useAppStore((s) => s.toast);
  if (!toast) return null;
  return (
    // bottom-[156px] clears the New-order FAB (bottom 88px + 58px tall) — a
    // toast sitting on the FAB turns a late "Undo" tap into a new order.
    <div className="animate-toast pointer-events-none fixed inset-x-0 bottom-[156px] z-50 flex justify-center px-4 lg:bottom-6 lg:pl-[240px]">
      <div className="pointer-events-auto flex w-full max-w-[560px] items-center gap-3 rounded-[14px] bg-ink px-4 py-3.5">
        <span className="flex-1 text-[14px] font-semibold text-ondark">{toast.text}</span>
        {toast.wa ? (
          // Tell the customer: opens their chat with the update typed in.
          <button
            type="button"
            onClick={() => { window.open(toast.wa, "_blank", "noopener,noreferrer"); useAppStore.getState().dismissToast(); }}
            className="shrink-0 rounded-full bg-[#25D366] px-3 py-1.5 text-[13px] font-bold text-white"
          >
            WhatsApp
          </button>
        ) : null}
        {toast.hasUndo ? (
          <button type="button" onClick={undo} className="text-[15px] font-bold text-bluebar">
            Undo
          </button>
        ) : null}
      </div>
    </div>
  );
}

function BottomNav({ current }: { current: NavTabKey | null }) {
  const router = useRouter();
  return (
    <nav className="fixed inset-x-0 bottom-0 z-40 flex h-[72px] items-center border-t border-divider bg-card px-1 lg:hidden">
      {TABS.map((t) => {
        const selected = t.key === current;
        const Icon = t.icon;
        return (
          <button
            key={t.key}
            type="button"
            onClick={() => router.push(t.href)}
            className={cls(
              "flex flex-1 flex-col items-center gap-[3px] py-2",
              selected ? "text-blue" : "text-muted",
            )}
          >
            <Icon size={24} />
            <span className={cls("text-[11px]", selected ? "font-bold" : "font-semibold")}>{t.label}</span>
          </button>
        );
      })}
    </nav>
  );
}

function Sidebar({ current }: { current: NavTabKey | null }) {
  const router = useRouter();
  const shopName = useAppStore((s) => s.rows.shop?.name ?? "MyLaundry");
  const syncing = useAppStore((s) => s.syncing);
  return (
    <aside className="fixed inset-y-0 left-0 z-40 hidden w-[240px] flex-col border-r border-cardborder bg-card lg:flex">
      <div className="flex items-center gap-3 px-5 pb-4 pt-6">
        <div className="flex size-10 items-center justify-center rounded-xl bg-blue text-ondark">
          <IcLaundry size={22} />
        </div>
        <div className="min-w-0">
          <div className="bric truncate text-[17px]">{shopName}</div>
          <div className="text-[11px] font-semibold text-muted">{syncing ? "Syncing…" : "MyLaundry"}</div>
        </div>
      </div>
      <div className="flex flex-col gap-1 px-3">
        {TABS.map((t) => {
          const selected = t.key === current;
          const Icon = t.icon;
          return (
            <button
              key={t.key}
              type="button"
              onClick={() => router.push(t.href)}
              className={cls(
                "flex h-11 items-center gap-3 rounded-xl px-3 text-[15px] transition-colors",
                selected ? "bg-bluelight font-bold text-blue" : "font-semibold text-inksecondary hover:bg-neutralfill",
              )}
            >
              <Icon size={20} />
              {t.label}
            </button>
          );
        })}
      </div>
    </aside>
  );
}

/**
 * Page wrapper. `tab` highlights a nav tab (null = detail screens, nav still
 * shown on desktop for orientation, hidden-on-mobile handled by `showMobileNav`).
 */
export function Shell({
  tab, children, showMobileNav = true, wide,
}: {
  tab: NavTabKey | null;
  children: ReactNode;
  showMobileNav?: boolean;
  wide?: boolean;
}) {
  return (
    <div className="min-h-dvh">
      <Sidebar current={tab} />
      <main
        className={cls(
          "mx-auto min-h-dvh lg:pl-[240px]",
          showMobileNav && "pb-[72px] lg:pb-0",
        )}
      >
        <div className={cls("mx-auto min-h-dvh w-full", wide ? "max-w-[1060px]" : "max-w-[640px]", "lg:px-6")}>
          {children}
        </div>
      </main>
      {showMobileNav ? <BottomNav current={tab} /> : null}
      <ToastBar />
    </div>
  );
}

/** Route helpers mirroring the app's AppNavigator. */
export function useNav() {
  const router = useRouter();
  const pathname = usePathname();
  return {
    back: () => {
      if (window.history.length > 1) router.back();
      else router.push("/");
    },
    // replace: true swaps the current entry — used when leaving a completed
    // form (new order → bill/home), so Back doesn't resurrect the stale form.
    openHome: (opts?: { replace?: boolean }) =>
      (opts?.replace ? router.replace : router.push)("/"),
    openRates: (from: string) => router.push(`/rates?from=${from}`),
    // q: the customer search pre-filled (Home "New order for …").
    openNewOrder: (opts?: { editId?: number; custId?: string; from?: string; q?: string }) => {
      const p = new URLSearchParams();
      if (opts?.q) p.set("q", opts.q);
      if (opts?.editId) p.set("edit", String(opts.editId));
      if (opts?.custId) p.set("cust", opts.custId);
      if (opts?.from) p.set("from", opts.from);
      router.push(`/orders/new${p.size ? `?${p}` : ""}`);
    },
    openBill: (orderId: number, from = "home", opts?: { replace?: boolean }) =>
      (opts?.replace ? router.replace : router.push)(`/orders/${orderId}/bill?from=${from}`),
    openOrder: (orderId: number) => router.push(`/orders/${orderId}`),
    openCustomer: (custId: string) => router.push(`/customers/${custId}`),
    openCustomers: (filter = "all") => router.push(filter === "all" ? "/customers" : `/customers?filter=${filter}`),
    openEarnings: () => router.push("/earnings"),
    openSubscription: () => router.push("/subscription"),
    openBillDesign: () => router.push("/settings/bill"),
    pathname,
  };
}
