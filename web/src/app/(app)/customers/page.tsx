"use client";

import { Suspense, useState } from "react";
import { useSearchParams } from "next/navigation";
import { rupees } from "@/core/money";
import type { Customer, LaundryState } from "@/domain/models";
import * as Sel from "@/domain/selectors";
import { useLaundryState } from "@/data/store";
import { useScreenView } from "@/analytics/useScreenView";
import { AppCard, Avatar, cls, FieldBox } from "@/ui/basics";
import { IcAdd, IcSearch } from "@/ui/icons";
import { SheetHost } from "@/ui/sheets/host";
import type { ActiveSheet } from "@/ui/sheets/types";
import { Shell, useNav } from "@/ui/shell";

/** Customers list + baaki/advance tiles — port of ui/screens/customers/CustomersScreen.kt. */

export default function CustomersPage() {
  return (
    <Shell tab="customers" wide>
      <Suspense>
        <CustomersScreen />
      </Suspense>
    </Shell>
  );
}

function CustomersScreen() {
  const initialFilter = useSearchParams().get("filter") || "all";
  const state = useLaundryState();
  const nav = useNav();
  const [filter, setFilter] = useState(initialFilter);
  const [query, setQuery] = useState("");
  const [active, setActive] = useState<ActiveSheet | null>(null);
  useScreenView("customers");

  const withBal = state.customers.map((c) => [c, Sel.balance(state, c.id)] as const);
  const owe = withBal.filter(([, b]) => b > 0);
  const adv = withBal.filter(([, b]) => b < 0);
  const sumOwe = owe.reduce((s, [, b]) => s + b, 0);
  const sumAdv = adv.reduce((s, [, b]) => s + b, 0);

  const q = query.trim();
  const qd = q.replace(/\D/g, "");
  let list = withBal.filter(([, b]) => (filter === "baaki" ? b > 0 : filter === "advance" ? b < 0 : true));
  if (q !== "") {
    list = list.filter(
      ([c]) => c.name.toLowerCase().includes(q.toLowerCase()) || (qd.length >= 3 && c.phone.includes(qd)),
    );
  }
  list =
    filter === "baaki"
      ? list.slice().sort((a, b) => b[1] - a[1])
      : filter === "advance"
        ? list.slice().sort((a, b) => a[1] - b[1])
        : list.slice().sort((a, b) => a[0].agoRank - b[0].agoRank);

  return (
    <div className="relative flex min-h-dvh flex-col">
      <div className="flex flex-col gap-2.5 px-4 pt-3">
        <div className="flex items-end gap-2">
          <h1 className="bric text-[24px]">Customers</h1>
          <span className="pb-[3px] text-[13px] text-muted">{state.customers.length} customers</span>
        </div>
        <FieldBox
          value={query}
          onChange={setQuery}
          placeholder="Search name or phone"
          h={48}
          leading={<span className="text-muted"><IcSearch size={20} /></span>}
        />
        <div className="flex w-full gap-2">
          <FilterTile
            label="All"
            value={String(state.customers.length)}
            sub="customers"
            tone="var(--color-ink)"
            selected={filter === "all"}
            onClick={() => setFilter("all")}
          />
          <FilterTile
            label="Baaki"
            value={rupees(sumOwe)}
            sub={`${owe.length} to collect`}
            tone="var(--color-orangetext)"
            selected={filter === "baaki"}
            onClick={() => setFilter(filter === "baaki" ? "all" : "baaki")}
          />
          <FilterTile
            label="Advance"
            value={rupees(sumAdv)}
            sub={`${adv.length} paid extra`}
            tone="var(--color-bluetext)"
            selected={filter === "advance"}
            onClick={() => setFilter(filter === "advance" ? "all" : "advance")}
          />
        </div>
      </div>

      <div className="flex flex-1 flex-col gap-2 px-4 pb-24 pt-3">
        <div className="grid grid-cols-1 gap-2 lg:grid-cols-2">
          {list.map(([c, bal]) => (
            <CustomerRow key={c.id} state={state} c={c} bal={bal} onClick={() => nav.openCustomer(c.id)} />
          ))}
        </div>
        {list.length === 0 ? (
          <div className="flex w-full flex-col items-center gap-2.5 pt-10">
            <span className="text-[16px] font-semibold">
              {q !== "" ? `No customer found for “${q}”` : "No customers here"}
            </span>
            <button
              type="button"
              onClick={() =>
                setActive({
                  kind: "customerForm",
                  editId: null,
                  ctx: "list",
                  prefillName: qd.length >= 3 ? "" : q.charAt(0).toUpperCase() + q.slice(1),
                  prefillPhone: qd.length >= 3 ? qd.slice(-10) : "",
                })
              }
              className="rounded-xl bg-blue px-5 py-3 text-[15px] font-bold text-ondark"
            >
              {q !== ""
                ? qd.length >= 3
                  ? "Add customer with this number"
                  : `Add “${q.charAt(0).toUpperCase() + q.slice(1)}” as customer`
                : "Add customer"}
            </button>
          </div>
        ) : null}
      </div>

      <button
        type="button"
        onClick={() => setActive({ kind: "customerForm", editId: null, ctx: "list" })}
        className="fixed bottom-[88px] right-4 z-30 flex h-[52px] items-center gap-1.5 rounded-full bg-blue px-5 text-ondark shadow-lg shadow-blue/25 lg:bottom-8 lg:right-8"
      >
        <IcAdd size={22} />
        <span className="text-[16px] font-bold">Add customer</span>
      </button>

      <SheetHost active={active} state={state} nav={nav} onOpen={setActive} onDismiss={() => setActive(null)} />
    </div>
  );
}

function FilterTile({
  label, value, sub, tone, selected, onClick,
}: {
  label: string;
  value: string;
  sub: string;
  tone: string;
  selected: boolean;
  onClick: () => void;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={cls(
        "flex h-[74px] flex-1 flex-col justify-center rounded-[14px] border px-3 py-2.5 text-left",
        selected ? "border-ink bg-ink" : "border-cardborder bg-card",
      )}
    >
      <span className="text-[12px] font-bold" style={{ color: selected ? "var(--color-ondarkmuted)" : tone }}>
        {label}
      </span>
      <span className="bric truncate text-[18px]" style={{ color: selected ? "var(--color-ondark)" : tone }}>
        {value}
      </span>
      <span className="truncate text-[11px]" style={{ color: selected ? "var(--color-ondarkmuted)" : "var(--color-muted)" }}>
        {sub}
      </span>
    </button>
  );
}

function CustomerRow({
  state, c, bal, onClick,
}: { state: LaundryState; c: Customer; bal: number; onClick: () => void }) {
  const avBg = bal > 0 ? "var(--color-orangelight)" : bal < 0 ? "var(--color-bluelight)" : "var(--color-neutralfill)";
  const avFg = bal > 0 ? "var(--color-orangetext)" : bal < 0 ? "var(--color-bluetext)" : "var(--color-inksecondary)";
  const count = Sel.orderCount(state, c);
  return (
    <AppCard onClick={onClick}>
      <div className="flex w-full items-center gap-3 p-3">
        <Avatar text={Sel.initials(c.name)} size={42} bg={avBg} fg={avFg} />
        <div className="min-w-0 flex-1">
          <div className="truncate text-[16px] font-bold">{c.name}</div>
          <div className="truncate text-[13px] text-muted">
            {(count > 0 ? `Last order ${c.lastLabel} · ${count} orders` : "New customer") + " · " + Sel.fmtPhone(c.phone)}
          </div>
        </div>
        <div className="flex shrink-0 flex-col items-end">
          {bal !== 0 ? (
            <span className="text-[16px] font-bold" style={{ color: avFg }}>{rupees(bal)}</span>
          ) : null}
          <span className="text-[12px]" style={{ color: avFg }}>
            {bal > 0 ? "baaki" : bal < 0 ? "advance" : "All clear"}
          </span>
        </div>
      </div>
    </AppCard>
  );
}
