"use client";

import { useEffect, useRef, useState } from "react";
import * as AppDate from "@/core/appdate";
import { rupees } from "@/core/money";
import { collectedOn } from "@/domain/earningsMath";
import type { LaundryState, Order, OrderStatus } from "@/domain/models";
import * as Sel from "@/domain/selectors";
import * as Repo from "@/data/repository";
import { useAppStore, useLaundryState } from "@/data/store";
import { Analytics } from "@/analytics/events";
import { useScreenView } from "@/analytics/useScreenView";
import { cls, PrimaryButton, SectionLabel } from "@/ui/basics";
import { IcAdd, IcArrowDown, IcArrowUp, IcCalendar, IcEye, IcEyeOff, IcSearch, IcShirt } from "@/ui/icons";
import { OrderCard } from "@/ui/orderCard";
import { SheetHost } from "@/ui/sheets/host";
import type { ActiveSheet } from "@/ui/sheets/types";
import { Shell, useNav } from "@/ui/shell";

/** Home — pickups/deliveries for a day. Port of ui/screens/home/HomeScreen.kt. */

export default function HomePage() {
  return (
    <Shell tab="orders" wide>
      <HomeScreen />
    </Shell>
  );
}

function HomeScreen() {
  const state = useLaundryState();
  const hidden = useAppStore((s) => s.hideAmounts);
  const toggleHide = useAppStore((s) => s.toggleHideAmounts);
  const nav = useNav();
  useScreenView("home");

  // No paywall here: the Gate hard-gates the app until there's an active
  // subscription, so home is only reachable once subscribed.
  const startNewOrder = (from: "home" | "empty_home") => nav.openNewOrder({ from });

  const [tabPickup, setTabPickup] = useState(true);
  const [selDate, setSelDate] = useState(AppDate.today());
  // Past midnight (the tab left open overnight): move "Today" to the new day.
  const todayRef = useRef(AppDate.today());
  useEffect(() => {
    const tick = () => {
      const now = AppDate.today();
      if (now === todayRef.current) return;
      setSelDate((d) => (d === todayRef.current ? now : d));
      todayRef.current = now;
    };
    const id = setInterval(tick, 60_000);
    document.addEventListener("visibilitychange", tick);
    return () => { clearInterval(id); document.removeEventListener("visibilitychange", tick); };
  }, []);
  const [filter, setFilter] = useState("all");
  // Search (all dates). Kept in the page address so Back from an order lands
  // on the same results; the bottom nav opens a plain "/" and starts empty.
  const [query, setQuery] = useState("");
  const searching = query.trim() !== "";
  const restored = useRef(false);
  useEffect(() => {
    const p = new URLSearchParams(window.location.search);
    const q = p.get("q") ?? "";
    if (q) { setQuery(q); setTabPickup(p.get("tab") !== "del"); }
    restored.current = true;
  }, []);
  useEffect(() => {
    if (!restored.current) return;
    const p = new URLSearchParams();
    if (searching) { p.set("q", query); if (!tabPickup) p.set("tab", "del"); }
    const url = p.size ? `/?${p}` : "/";
    if (window.location.pathname + window.location.search !== url) window.history.replaceState(window.history.state, "", url);
  }, [query, tabPickup, searching]);
  const typeQuery = (v: string) => {
    if (!searching && v.trim() !== "") Analytics.orderSearchOpened();
    setQuery(v);
  };
  const [active, setActive] = useState<ActiveSheet | null>(null);

  function act(o: Order) {
    switch (o.status) {
      case "CREATED":
        if (o.lines.length === 0) setActive({ kind: "count", orderId: o.id, next: "RECEIVED" });
        else Repo.markPickedUp(o.id);
        break;
      case "RECEIVED":
        if (o.lines.length === 0) setActive({ kind: "count", orderId: o.id, next: "READY" });
        else setActive({ kind: "ready", orderId: o.id }); // asks the delivery date
        break;
      case "READY":
        setActive({ kind: "pay", orderId: o.id });
        break;
    }
  }

  const noOrders = state.orders.length === 0;

  const card = (o: Order, late: boolean) => (
    <OrderCard
      key={o.id}
      state={state}
      order={o}
      pickupTab={tabPickup}
      late={late}
      showDate={searching}
      onOpen={() => nav.openOrder(o.id)}
      onAct={() => act(o)}
      onMore={() => setActive({ kind: "menu", orderId: o.id })}
    />
  );

  const pending =
    selDate === AppDate.today()
      ? pendingOf(state, tabPickup).filter((o) => pass(o, tabPickup, filter)).sort(byTodo)
      : [];
  const list = dayOrders(state, selDate, tabPickup).filter((o) => pass(o, tabPickup, filter)).sort(byTodo);
  const results = searching ? searchResults(state, query, tabPickup) : [];

  return (
    <div className="relative flex min-h-dvh flex-col">
      {/* Header */}
      <div className="flex items-center gap-2 px-4 pb-2 pt-3.5">
        <h1 className="bric min-w-0 flex-1 truncate text-[22px]">{state.shop.name}</h1>
        <button
          type="button"
          onClick={() => nav.openRates("home")}
          className="h-11 rounded-full border border-cardborder bg-card px-3.5 text-[14px] font-bold"
        >
          ₹ Rates
        </button>
      </div>

      {!noOrders ? (
        <>
          {!searching ? (
            <DashboardStrip
              state={state}
              selDate={selDate}
              hidden={hidden}
              onEye={toggleHide}
              onOpen={() => nav.openEarnings()}
            />
          ) : null}
          <TabRow state={state} pickup={tabPickup} selDate={selDate} onSelect={(pk) => { setTabPickup(pk); setFilter("all"); }} />
          {!searching ? (
            <>
              <DateStrip state={state} pickup={tabPickup} selDate={selDate} onPick={setSelDate} />
              <FilterTabs state={state} pickup={tabPickup} selDate={selDate} filter={filter} onSelect={setFilter} />
            </>
          ) : null}
        </>
      ) : null}

      <div className="relative flex-1">
        {noOrders ? (
          <EmptyHome onNewOrder={() => startNewOrder("empty_home")} />
        ) : (
          <div className="flex flex-col gap-2.5 px-4 pb-[170px]">
            <SearchBar value={query} onChange={typeQuery} />
            {searching ? (
              <>
                <div className="flex items-center gap-2">
                  <span className="min-w-0 flex-1 text-[14px] text-muted">
                    {results.length} {results.length === 1 ? "order" : "orders"} in {tabPickup ? "Pickups" : "Deliveries"} · all dates
                  </span>
                  <button type="button" onClick={() => setQuery("")} className="h-9 shrink-0 text-[14px] font-bold text-blue">
                    Clear search
                  </button>
                </div>
                {results.length === 0 ? (
                  <div className="flex w-full flex-col items-center gap-3 px-6 py-10 text-center">
                    <span className="text-[17px] font-bold">No order found for “{query.trim()}”</span>
                    <span className="text-[14px] text-muted">Check the spelling, or take a new order for them.</span>
                    <button
                      type="button"
                      onClick={() => nav.openNewOrder({ from: "home", q: query.trim() })}
                      className="mt-1 flex h-12 max-w-full items-center gap-2 rounded-xl bg-blue px-5 text-[15px] font-bold text-ondark"
                    >
                      <IcAdd size={20} />
                      <span className="truncate">New order for “{query.trim()}”</span>
                    </button>
                  </div>
                ) : (
                  <div className="grid grid-cols-1 gap-2.5 lg:grid-cols-2">{results.map((o) => card(o, false))}</div>
                )}
              </>
            ) : (
              <>
                {pending.length > 0 ? (
                  <>
                    <div className="mt-0.5 flex items-center gap-2">
                      <span className="size-[7px] rounded-full bg-orange" />
                      <SectionLabel
                        text={(tabPickup ? "Pending from earlier · " : "Late or no delivery date · ") + pending.length}
                        tone="var(--color-orangetext)"
                      />
                    </div>
                    <div className="grid grid-cols-1 gap-2.5 lg:grid-cols-2">{pending.map((o) => card(o, true))}</div>
                  </>
                ) : null}

                <SectionLabel text={AppDate.long(selDate)} className="mt-1.5" />
                {list.length === 0 ? (
                  <div className="flex w-full flex-col items-center gap-1.5 p-10">
                    <span className="text-[17px] font-bold">{tabPickup ? "No pickups" : "No deliveries"}</span>
                    <span className="text-[14px] text-muted">Nothing here for {AppDate.long(selDate)}.</span>
                  </div>
                ) : (
                  <div className="grid grid-cols-1 gap-2.5 lg:grid-cols-2">{list.map((o) => card(o, false))}</div>
                )}
              </>
            )}
          </div>
        )}

        {/* New order FAB */}
        {!noOrders ? (
          <button
            type="button"
            onClick={() => startNewOrder("home")}
            className="fixed bottom-[88px] right-4 z-30 flex h-[58px] items-center gap-2 rounded-full bg-blue pl-[18px] pr-[22px] text-ondark shadow-lg shadow-blue/25 lg:bottom-8 lg:right-8"
          >
            <IcAdd size={22} />
            <span className="text-[17px] font-bold">New order</span>
          </button>
        ) : null}
      </div>

      <SheetHost active={active} state={state} nav={nav} onOpen={setActive} onDismiss={() => setActive(null)} />
    </div>
  );
}

// ---------- empty state (brand-new shop) ----------

function EmptyHome({ onNewOrder }: { onNewOrder: () => void }) {
  return (
    <div className="flex flex-col gap-4 p-4">
      <div className="flex w-full flex-col items-center gap-4 rounded-2xl border border-cardborder bg-card p-6 text-center">
        <div className="flex size-[84px] items-center justify-center rounded-full bg-bluelight text-blue">
          <IcShirt size={40} />
        </div>
        <h2 className="bric text-[26px]">Your shop is ready!</h2>
        <p className="text-[15px] text-muted">No orders yet. Take your first one — it takes less than a minute.</p>
        <PrimaryButton onClick={onNewOrder}>+ Take your first order</PrimaryButton>
      </div>

      <div className="flex w-full flex-col gap-3.5 rounded-2xl border border-cardborder bg-card p-4">
        <SectionLabel text="How it works" />
        <HowStep n={1} title="Take the order" desc="Pick the customer and clothes. The bill is made for you." />
        <HowStep n={2} title="Mark it ready" desc="The customer gets a WhatsApp that clothes are ready." />
        <HowStep n={3} title="Hand over and collect money" desc="Cash, UPI or khata — it all adds up in Earnings." />
      </div>

      <p className="px-2 text-center text-[13px] text-muted">
        Your prices are already set. Change them anytime from ₹ Rates at the top.
      </p>
    </div>
  );
}

function HowStep({ n, title, desc }: { n: number; title: string; desc: string }) {
  return (
    <div className="flex gap-3 text-left">
      <span className="flex size-7 shrink-0 items-center justify-center rounded-full bg-ink text-[14px] font-bold text-ondark">
        {n}
      </span>
      <span className="flex min-w-0 flex-col">
        <span className="text-[16px] font-bold">{title}</span>
        <span className="text-[13px] text-muted">{desc}</span>
      </span>
    </div>
  );
}

// ---------- search ----------

/** Search bar above the order cards: sticky, so it stays while scrolling orders. */
function SearchBar({ value, onChange }: { value: string; onChange: (v: string) => void }) {
  const on = value.trim() !== "";
  return (
    <div className="sticky top-0 z-20 -mx-4 bg-bg px-4 pb-0.5 pt-1">
      <div
        className={cls(
          "flex h-12 items-center gap-2.5 rounded-[14px] border-[1.5px] bg-card pl-3.5 pr-1.5",
          on ? "border-blue" : "border-fieldborder",
        )}
      >
        <span className={on ? "text-blue" : "text-muted"}><IcSearch size={20} /></span>
        <input
          type="search"
          value={value}
          onChange={(e) => onChange(e.target.value)}
          placeholder="Search name, phone or order no."
          aria-label="Search orders"
          enterKeyHint="search"
          autoComplete="off"
          className="h-full min-w-0 flex-1 bg-transparent text-[16px] font-semibold outline-none placeholder:font-normal placeholder:text-faint [&::-webkit-search-cancel-button]:hidden"
        />
        {value !== "" ? (
          <button
            type="button"
            onClick={() => onChange("")}
            aria-label="Clear search"
            className="flex size-9 shrink-0 items-center justify-center"
          >
            <span className="flex size-6 items-center justify-center rounded-full bg-neutralfill text-[13px] font-bold text-inksecondary">✕</span>
          </button>
        ) : null}
      </div>
    </div>
  );
}

// ---------- header pieces ----------

function DashboardStrip({
  state, selDate, hidden, onEye, onOpen,
}: {
  state: LaundryState;
  selDate: string;
  hidden: boolean;
  onEye: () => void;
  onOpen: () => void;
}) {
  const newOrders = state.orders.filter((o) => o.createdOn === selDate && o.status !== "CANCELLED").length;
  const collected = collectedOn(selDate, state.ledger);
  const rel = AppDate.rel(selDate);
  const whenText = rel ? rel.toLowerCase() : `on ${AppDate.plain(selDate)}`;
  return (
    <div className="mx-4 mb-2.5 flex items-center rounded-[14px] border border-cardborder bg-card">
      <button type="button" onClick={onOpen} className="flex-1 px-3.5 py-2.5 text-left">
        <div className="text-[20px] font-bold">{newOrders}</div>
        <div className="text-[12px] text-muted">{(newOrders === 1 ? "New order " : "New orders ") + whenText}</div>
      </button>
      <button type="button" onClick={onOpen} className="flex-[1.25] px-3.5 py-2.5 text-left">
        <div className="text-[20px] font-bold">{hidden ? "₹ ••••" : rupees(collected)}</div>
        <div className="text-[12px] text-muted">Collected {whenText}</div>
      </button>
      <button type="button" onClick={onEye} aria-label="Toggle amounts" className="flex size-12 items-center justify-center text-muted">
        {hidden ? <IcEyeOff size={22} /> : <IcEye size={22} />}
      </button>
    </div>
  );
}

function TabRow({
  state, pickup, selDate, onSelect,
}: {
  state: LaundryState;
  pickup: boolean;
  selDate: string;
  onSelect: (pk: boolean) => void;
}) {
  return (
    <div className="flex gap-2 px-4 pb-2">
      {[true, false].map((pk) => {
        const on = pickup === pk;
        const n = tabTodo(state, pk, selDate);
        return (
          <button
            key={String(pk)}
            type="button"
            onClick={() => onSelect(pk)}
            className={cls(
              "flex h-[50px] flex-1 items-center justify-center gap-2 rounded-[14px] border-[1.5px]",
              on ? "border-ink bg-ink text-ondark" : "border-cardborder bg-card text-ink",
            )}
          >
            {pk ? <IcArrowUp size={20} /> : <IcArrowDown size={20} />}
            <span className="text-[16px] font-bold">{pk ? "Pickups" : "Deliveries"}</span>
            <span
              className={cls(
                "flex h-[22px] min-w-[22px] items-center justify-center rounded-full px-1.5 text-[12px] font-bold",
                n > 0 ? "bg-orange text-ondark" : on ? "bg-darkchiptrack text-ondark" : "bg-neutralfill text-inksecondary",
              )}
            >
              {n}
            </span>
          </button>
        );
      })}
    </div>
  );
}

/** Days the strip shows around today (orders can be dated in the past). */
const STRIP_BACK = 60;
const STRIP_AHEAD = 30;

function DateStrip({
  state, pickup, selDate, onPick,
}: {
  state: LaundryState;
  pickup: boolean;
  selDate: string;
  onPick: (iso: string) => void;
}) {
  const today = AppDate.today();
  // Stretch the range when the date picker jumps outside it.
  const back = Math.max(STRIP_BACK, -AppDate.daysBetween(today, selDate));
  const ahead = Math.max(STRIP_AHEAD, AppDate.daysBetween(today, selDate));
  const days: number[] = [];
  for (let i = -back; i <= ahead; i++) days.push(i);

  const scroller = useRef<HTMLDivElement>(null);
  const selRef = useRef<HTMLButtonElement>(null);
  const first = useRef(true);
  // Keep the selected day in view: centred on open, smoothly after a pick.
  useEffect(() => {
    const box = scroller.current;
    const btn = selRef.current;
    if (!box || !btn) return;
    const left = btn.offsetLeft - box.clientWidth / 2 + btn.clientWidth / 2;
    box.scrollTo({ left, behavior: first.current ? "auto" : "smooth" });
    first.current = false;
  }, [selDate]);
  // A mouse wheel scrolls vertically; turn it sideways so desktop can browse days.
  useEffect(() => {
    const box = scroller.current;
    if (!box) return;
    const onWheel = (e: WheelEvent) => {
      if (Math.abs(e.deltaY) <= Math.abs(e.deltaX)) return;
      const max = box.scrollWidth - box.clientWidth;
      if ((e.deltaY < 0 && box.scrollLeft <= 0) || (e.deltaY > 0 && box.scrollLeft >= max)) return;
      e.preventDefault();
      box.scrollLeft += e.deltaY;
    };
    box.addEventListener("wheel", onWheel, { passive: false });
    return () => box.removeEventListener("wheel", onWheel);
  }, []);

  return (
    <div className="flex items-center pb-1">
      <div ref={scroller} className="no-scrollbar relative flex min-w-0 flex-1 gap-0.5 overflow-x-auto px-3">
        {days.map((i) => {
          const iso = AppDate.add(today, i);
          const on = selDate === iso;
          const n = state.orders.filter((o) => onDate(o, iso, pickup) && o.status !== "CANCELLED").length;
          const dom = AppDate.dayOfMonth(iso);
          return (
            <button
              key={iso}
              ref={on ? selRef : undefined}
              type="button"
              onClick={() => onPick(iso)}
              className={cls(
                "flex h-[58px] w-[50px] shrink-0 flex-col items-center justify-center rounded-xl border-[1.5px]",
                on ? "border-ink bg-ink" : "border-transparent",
              )}
            >
              <span className={cls("text-[11px] font-bold", on ? "text-ondarkmuted" : i === 0 ? "text-blue" : "text-muted")}>
                {i === 0 ? "Today" : dom === 1 ? AppDate.plain(iso).split(" ")[2] : AppDate.dayName(iso)}
              </span>
              <span className={cls("text-[17px] font-bold", on ? "text-ondark" : "text-ink")}>{dom}</span>
              <span className={cls("h-4 text-[11px] font-bold", on ? "text-bluebar" : "text-blue")}>{n > 0 ? n : ""}</span>
            </button>
          );
        })}
      </div>
      {/* Jump to any date — the native picker sits invisibly over the button. */}
      <label className="relative mr-3 flex h-[58px] w-11 shrink-0 cursor-pointer items-center justify-center rounded-xl text-blue">
        <IcCalendar size={22} />
        <input
          type="date"
          value={selDate}
          aria-label="Pick a date"
          onClick={(e) => {
            const el = e.currentTarget;
            if ("showPicker" in el) {
              try { el.showPicker(); } catch { /* focus alone opens it on iOS */ }
            }
          }}
          onChange={(e) => { if (e.target.value) onPick(e.target.value); }}
          className="absolute inset-0 size-full cursor-pointer text-[16px] opacity-0"
          style={{ transform: "translateZ(0)" }}
        />
      </label>
    </div>
  );
}

function FilterTabs({
  state, pickup, selDate, filter, onSelect,
}: {
  state: LaundryState;
  pickup: boolean;
  selDate: string;
  filter: string;
  onSelect: (f: string) => void;
}) {
  const defs: [string, string][] = pickup
    ? [["all", "All"], ["created", "To pick up"], ["received", "Received"], ["cancelled", "Cancelled"]]
    : [["all", "All"], ["notready", "Not ready"], ["ready", "Ready"], ["delivered", "Delivered"]];
  const day = dayOrders(state, selDate, pickup);
  return (
    <div className="no-scrollbar flex gap-5 overflow-x-auto px-4 pb-2">
      {defs.map(([key, label]) => {
        const on = filter === key;
        const n = key === "all" ? day.filter((o) => o.status !== "CANCELLED").length : day.filter((o) => groupOf(o, pickup) === key).length;
        return (
          <button key={key} type="button" onClick={() => onSelect(key)} className="flex h-11 shrink-0 flex-col justify-center">
            <span className="flex items-center gap-[5px]">
              <span className={cls("text-[14px] font-bold", on ? "text-ink" : "text-muted")}>{label}</span>
              <span className="text-[14px] font-semibold text-faint">{n}</span>
            </span>
            <span className={cls("mt-1 h-[2.5px] bg-ink transition-all", on ? "w-10" : "w-0")} />
          </button>
        );
      })}
    </div>
  );
}

// ---------- pure home logic (ported from the app, itself from prototype vHome) ----------

function onDate(o: Order, iso: string, pickup: boolean): boolean {
  // A delivered order is done: it shows under Deliveries, not as a pickup.
  return pickup ? o.pickupDate === iso && o.status !== "DELIVERED" : o.deliveryDate === iso && o.status !== "CANCELLED";
}

function isTodo(o: Order, pickup: boolean): boolean {
  return pickup
    ? o.status === "CREATED"
    : o.status === "CREATED" || o.status === "RECEIVED" || o.status === "READY";
}

function groupOf(o: Order, pickup: boolean): string {
  if (pickup) {
    if (o.status === "CREATED") return "created";
    if (o.status === "CANCELLED") return "cancelled";
    return "received";
  }
  if (o.status === "CREATED" || o.status === "RECEIVED") return "notready";
  return o.status.toLowerCase();
}

const RANK: Record<OrderStatus, number> = { CREATED: 0, RECEIVED: 1, READY: 2, DELIVERED: 3, CANCELLED: 4 };

function byTodo(a: Order, b: Order): number {
  return RANK[a.status] - RANK[b.status] || Number(b.express) - Number(a.express) || a.id - b.id;
}

function pendingOf(state: LaundryState, pickup: boolean): Order[] {
  const today = AppDate.today();
  return state.orders.filter((o) =>
    pickup
      ? o.status === "CREATED" && o.pickupDate < today
      : (o.status === "CREATED" || o.status === "RECEIVED" || o.status === "READY") &&
        ((o.deliveryDate !== "" && o.deliveryDate < today) || o.deliveryDate === ""),
  );
}

function dayOrders(state: LaundryState, iso: string, pickup: boolean): Order[] {
  return state.orders.filter((o) => onDate(o, iso, pickup));
}

function pass(o: Order, pickup: boolean, filter: string): boolean {
  return filter === "all" ? o.status !== "CANCELLED" : groupOf(o, pickup) === filter;
}

function tabTodo(state: LaundryState, pickup: boolean, selDate: string): number {
  const base = state.orders.filter((o) => onDate(o, selDate, pickup) && isTodo(o, pickup)).length;
  return base + (selDate === AppDate.today() ? pendingOf(state, pickup).length : 0);
}

/** Deliveries: ready first, then not ready, not picked up, delivered. */
const DEL_RANK: Record<OrderStatus, number> = { READY: 0, RECEIVED: 1, CREATED: 2, DELIVERED: 3, CANCELLED: 4 };

/**
 * Search across all dates: name (any part), phone (3+ digits), order no.
 * (2+ digits, "#" optional). Pickups: to-do first, then newest pickup.
 * Deliveries: no cancelled; ready first, then earliest delivery date.
 */
function searchResults(state: LaundryState, query: string, pickup: boolean): Order[] {
  const qs = query.trim().toLowerCase();
  if (qs === "") return [];
  const qd = qs.replace(/\D/g, "");
  const qn = qs.replace(/^#\s*/, "");
  return state.orders
    .filter((o) => {
      if (!pickup && o.status === "CANCELLED") return false;
      const c = Sel.customer(state, o.custId);
      return (
        c.name.toLowerCase().includes(qs) ||
        (qd.length >= 3 && c.phone.includes(qd)) ||
        (qd.length >= 2 && (String(o.id).includes(qn) || Sel.orderNo(o).toLowerCase().includes(qn)))
      );
    })
    .sort((a, b) =>
      pickup
        ? RANK[a.status] - RANK[b.status] || b.pickupDate.localeCompare(a.pickupDate) || b.id - a.id
        : DEL_RANK[a.status] - DEL_RANK[b.status] ||
          (a.deliveryDate || "9999").localeCompare(b.deliveryDate || "9999") ||
          a.id - b.id,
    );
}
