"use client";

import { useMemo, useState } from "react";
import { rupees } from "@/core/money";
import type { OrderLine, Service } from "@/domain/models";
import { trimKg } from "@/domain/selectors";
import { cls, FieldBox, Stepper } from "./basics";

/**
 * Clothes counting state + editor — port of ui/components/Clothes.kt
 * (ClothesState + ClothesEditor), shared by New order and Count clothes.
 */

export interface ClothesState {
  qty: Record<string, Record<string, number>>; // svcId -> itemName -> qty
  weight: Record<string, string>; // svcId -> weight text
  pcs: Record<string, string>; // svcId -> optional cloth count for a by-weight service
  price: Record<string, Record<string, string>>; // svcId -> itemName -> override text
  selected: string;
}

export interface ClothesApi {
  state: ClothesState;
  qtyOf(svc: string, item: string): number;
  bump(svc: string, item: string, d: number): void;
  setWeight(svc: string, v: string): void;
  setPcs(svc: string, v: string): void;
  priceText(svc: string, item: string): string | undefined;
  setPrice(svc: string, item: string, v: string): void;
  priceFor(svc: Service, item: string, base: number): number;
  select(svcId: string): void;
  lines(services: Service[]): OrderLine[];
  total(services: Service[]): number;
}

export function useClothesState(services: Service[]): ClothesApi {
  const initialSelected =
    (services.find((s) => s.mode === "PIECE") ?? services[0])?.id ?? "";
  const [state, setState] = useState<ClothesState>({
    qty: {}, weight: {}, pcs: {}, price: {}, selected: initialSelected,
  });

  // Keep selection valid if the service list changes under us.
  const selected = services.some((s) => s.id === state.selected) ? state.selected : initialSelected;

  return useMemo<ClothesApi>(() => {
    const api: ClothesApi = {
      state: { ...state, selected },
      qtyOf: (svc, item) => state.qty[svc]?.[item] ?? 0,
      bump: (svc, item, d) =>
        setState((s) => ({
          ...s,
          qty: {
            ...s.qty,
            [svc]: { ...(s.qty[svc] ?? {}), [item]: Math.max(0, (s.qty[svc]?.[item] ?? 0) + d) },
          },
        })),
      setWeight: (svc, v) => setState((s) => ({ ...s, weight: { ...s.weight, [svc]: v } })),
      setPcs: (svc, v) => setState((s) => ({ ...s, pcs: { ...s.pcs, [svc]: v } })),
      priceText: (svc, item) => state.price[svc]?.[item],
      setPrice: (svc, item, v) =>
        setState((s) => ({
          ...s,
          price: { ...s.price, [svc]: { ...(s.price[svc] ?? {}), [item]: v } },
        })),
      priceFor: (svc, item, base) => {
        const ov = state.price[svc.id]?.[item];
        if (ov != null && ov !== "") {
          const n = parseInt(ov, 10);
          return Number.isNaN(n) ? 0 : n;
        }
        return base;
      },
      select: (svcId) => setState((s) => ({ ...s, selected: svcId })),
      lines: (svcs) => {
        const out: OrderLine[] = [];
        for (const s of svcs) {
          if (s.mode === "WEIGHT") {
            const kg = parseFloat(state.weight[s.id] ?? "");
            if (kg > 0) {
              const rate = s.ratePerKg ?? 0;
              const min = s.minKg ?? 0;
              out.push({
                serviceId: s.id, serviceName: s.name, itemName: "By weight",
                // qty on a weight line = clothes in the bag (0 = not counted)
                qty: parseInt(state.pcs[s.id] ?? "", 10) || 0, price: rate, base: rate, kg, amt: Math.round(Math.max(kg, min) * rate),
              });
            }
          } else {
            const q = state.qty[s.id] ?? {};
            for (const it of s.items) {
              const base = it.price ?? 0;
              const p = api.priceFor(s, it.name, base);
              const c = q[it.name] ?? 0;
              if (c > 0 && p > 0) {
                out.push({
                  serviceId: s.id, serviceName: s.name, itemName: it.name,
                  qty: c, price: p, base, kg: 0, amt: c * p,
                });
              }
            }
          }
        }
        return out;
      },
      total: (svcs) => api.lines(svcs).reduce((sum, l) => sum + l.amt, 0),
    };
    return api;
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [state, selected]);
}

function serviceStat(api: ClothesApi, s: Service): { n: number; kg: number; amt: number } {
  if (s.mode === "WEIGHT") {
    const kg = parseFloat(api.state.weight[s.id] ?? "") || 0;
    const amt = kg > 0 ? Math.round(Math.max(kg, s.minKg ?? 0) * (s.ratePerKg ?? 0)) : 0;
    return { n: 0, kg, amt };
  }
  const q = api.state.qty[s.id] ?? {};
  let n = 0;
  let a = 0;
  for (const it of s.items) {
    const base = it.price ?? 0;
    const p = api.priceFor(s, it.name, base);
    const c = q[it.name] ?? 0;
    if (p > 0) { n += c; a += c * p; }
  }
  return { n, kg: 0, amt: a };
}

export function ClothesEditor({
  services, api, editablePrice, onAddItem,
}: {
  services: Service[];
  api: ClothesApi;
  editablePrice: boolean;
  /** Per-piece services: add a cloth + price right here (saved to the rate list). */
  onAddItem?: (svc: Service, name: string, price: number) => void;
}) {
  const cur = services.find((s) => s.id === api.state.selected);

  return (
    <div className="flex w-full flex-col gap-3">
      {/* service tiles (2 columns) */}
      <div className="grid grid-cols-2 gap-2">
        {services.map((s) => {
          const { n, kg, amt } = serviceStat(api, s);
          const has = amt > 0;
          const sel = api.state.selected === s.id;
          const sub = has && s.mode === "WEIGHT"
            ? `${trimKg(kg)} kg · ${rupees(amt)}`
            : has
              ? `${n} ${n === 1 ? "item" : "items"} · ${rupees(amt)}`
              : s.mode === "WEIGHT"
                ? "By weight"
                : "Tap to add";
          return (
            <button
              key={s.id}
              type="button"
              onClick={() => api.select(s.id)}
              className={cls(
                "flex h-16 flex-col justify-center rounded-[14px] border-2 px-3.5 text-left",
                sel ? "border-ink bg-ink" : has ? "border-blue bg-bluelight" : "border-cardborder bg-card",
              )}
            >
              <span className={cls("truncate text-[15px] font-bold", sel ? "text-ondark" : "text-ink")}>{s.name}</span>
              <span
                className={cls(
                  "truncate text-[12px] font-semibold",
                  sel ? "text-ondarkmuted" : has ? "text-bluetext" : "text-muted",
                )}
              >
                {sub}
              </span>
            </button>
          );
        })}
      </div>

      {cur ? (
        cur.mode === "PIECE" ? (
          <div>
            {cur.items
              .filter((it) => (it.price ?? 0) > 0)
              .map((item) => {
                const base = item.price ?? 0;
                const c = api.qtyOf(cur.id, item.name);
                const priceText = api.priceText(cur.id, item.name) ?? String(base);
                const changed = editablePrice && priceText !== "" && parseInt(priceText, 10) !== base;
                return (
                  <div key={item.name} className="flex h-14 items-center gap-2 py-0.5">
                    <div className="min-w-0 flex-1">
                      <div className="truncate text-[16px] font-semibold">{item.name}</div>
                      {changed ? <div className="text-[12px] font-semibold text-orangetext">rate ₹{base}</div> : null}
                    </div>
                    {editablePrice ? (
                      <FieldBox
                        value={priceText}
                        onChange={(v) => api.setPrice(cur.id, item.name, v.replace(/\D/g, "").slice(0, 5))}
                        prefix="₹"
                        h={42}
                        className="w-[88px]"
                        inputMode="numeric"
                        textClass="text-[16px] font-bold"
                      />
                    ) : (
                      <span className="text-[15px] font-semibold text-muted">₹{base}</span>
                    )}
                    <Stepper
                      qty={c}
                      onDec={() => api.bump(cur.id, item.name, -1)}
                      onInc={() => api.bump(cur.id, item.name, 1)}
                    />
                  </div>
                );
              })}
            {onAddItem ? <AddItemRow svc={cur} onAdd={(name, price) => onAddItem(cur, name, price)} /> : null}
          </div>
        ) : (
          <WeightEditor cur={cur} api={api} />
        )
      ) : null}
    </div>
  );
}

/** "Add a cloth" under a per-piece service: name + price, saved to the rate list. */
function AddItemRow({ svc, onAdd }: { svc: Service; onAdd: (name: string, price: number) => void }) {
  const [name, setName] = useState("");
  const [price, setPrice] = useState("");
  const clean = name.trim().replace(/\s+/g, " ");
  const p = parseInt(price, 10) || 0;
  const existing = svc.items.find((it) => it.name.toLowerCase() === clean.toLowerCase());
  const priced = existing != null && (existing.price ?? 0) > 0;
  const ok = clean !== "" && (priced || p > 0);
  const add = () => {
    if (!ok) return;
    onAdd(existing?.name ?? clean, priced ? existing!.price ?? 0 : p);
    setName("");
    setPrice("");
  };
  return (
    <div className="mt-2 flex flex-col gap-2 rounded-[14px] border-[1.5px] border-dashed border-blueborder bg-bluelight/50 p-3">
      <span className="text-[13px] font-bold text-bluetext">Add a cloth to {svc.name}</span>
      <div className="flex w-full items-center gap-2">
        <FieldBox
          value={name}
          onChange={(v) => setName(v.slice(0, 30))}
          placeholder="e.g. Blazer"
          h={44}
          className="min-w-0 flex-1"
          inputProps={{ "aria-label": "Cloth name", onKeyDown: (e) => { if (e.key === "Enter") add(); } }}
        />
        <FieldBox
          value={priced ? String(existing!.price) : price}
          onChange={(v) => setPrice(v.replace(/\D/g, "").slice(0, 5))}
          prefix="₹"
          placeholder="Price"
          h={44}
          className="w-[96px]"
          inputMode="numeric"
          inputProps={{ "aria-label": "Price", readOnly: priced, onKeyDown: (e) => { if (e.key === "Enter") add(); } }}
        />
        <button
          type="button"
          onClick={add}
          disabled={!ok}
          className={cls("h-11 shrink-0 rounded-[10px] px-3.5 text-[14px] font-bold", ok ? "bg-blue text-ondark" : "bg-neutralfill text-muted")}
        >
          Add
        </button>
      </div>
      <span className="text-[12px] text-bluetext/80">
        {priced ? "Already on your rate list — Add puts 1 in this order." : "Saved to your rate list and added to this order."}
      </span>
    </div>
  );
}

function WeightEditor({ cur, api }: { cur: Service; api: ClothesApi }) {
  const rate = cur.ratePerKg ?? 0;
  const min = cur.minKg ?? 0;
  const w = api.state.weight[cur.id] ?? "";
  const pcs = api.state.pcs[cur.id] ?? "";
  const kg = parseFloat(w) || 0;
  const amt = kg > 0 ? Math.round(Math.max(kg, min) * rate) : 0;
  return (
    <div className="flex flex-col gap-2">
      <div className="flex w-full items-center gap-3">
        <FieldBox
          value={w}
          onChange={(v) => api.setWeight(cur.id, v.replace(/[^\d.]/g, ""))}
          placeholder="0"
          suffix="kg"
          h={56}
          inputMode="decimal"
          className="flex-1"
        />
        <span className="bric text-[22px]">{rupees(amt)}</span>
      </div>
      <FieldBox
        value={pcs}
        onChange={(v) => api.setPcs(cur.id, v.replace(/\D/g, "").slice(0, 4))}
        placeholder="Number of clothes (optional)"
        suffix={pcs !== "" ? "clothes" : undefined}
        h={48}
        inputMode="numeric"
      />
      <p className="text-[13px] text-muted">
        ₹{rate} per kg. Bags under {trimKg(min)} kg are charged for {trimKg(min)} kg.
      </p>
    </div>
  );
}
