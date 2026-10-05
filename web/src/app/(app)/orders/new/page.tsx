"use client";

import { Suspense, useEffect, useMemo, useState } from "react";
import { useSearchParams } from "next/navigation";
import * as AppDate from "@/core/appdate";
import { rupees } from "@/core/money";
import { expressAuto } from "@/domain/laundryMath";
import type { Route } from "@/domain/models";
import * as Sel from "@/domain/selectors";
import * as Repo from "@/data/repository";
import { useLaundryState } from "@/data/store";
import { paywallInfo, useBillingStore } from "@/data/billingStore";
import { Analytics } from "@/analytics/events";
import {
  AppCard, Avatar, cls, DateTimeBox, Divider, FieldBox, PrimaryButton, SectionLabel, Toggle, TopBar,
} from "@/ui/basics";
import { ClothesEditor, useClothesState } from "@/ui/clothes";
import { IcCalendar, IcClock } from "@/ui/icons";
import { SheetHost } from "@/ui/sheets/host";
import type { ActiveSheet } from "@/ui/sheets/types";
import { Shell, useNav } from "@/ui/shell";

/** New / edit order — port of ui/screens/neworder/NewOrderScreen.kt. */

export default function NewOrderPage() {
  return (
    <Shell tab={null} showMobileNav={false} wide>
      <Suspense>
        <NewOrderScreen />
      </Suspense>
    </Shell>
  );
}

function tatText(n: number): string {
  if (n === 0) return "same day";
  if (n === 1) return "in 1 day";
  return `in ${n} days`;
}

function NewOrderScreen() {
  const params = useSearchParams();
  const editId = (() => {
    const v = parseInt(params.get("edit") ?? "", 10);
    return Number.isNaN(v) || v <= 0 ? null : v;
  })();
  const presetCustId = params.get("cust") || null;

  const state = useLaundryState();
  const nav = useNav();
  const services = state.services;
  const clothes = useClothesState(services);
  const editingOrder = editId != null ? Sel.order(state, editId) : undefined;

  // Catch-all paywall guard: creating a new order (not editing) is blocked once
  // the free orders run out, from any entry point. Editing stays allowed.
  // replace, not push — pushing would leave this page under the paywall and
  // Back would re-trigger the guard in a loop.
  const billing = useBillingStore((s) => s.status);
  useEffect(() => {
    if (editId == null && paywallInfo(billing).blocked) nav.openPaywall("limit", { replace: true });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [billing, editId]);

  const [custId, setCustId] = useState<string | null>(presetCustId);
  const [query, setQuery] = useState("");
  const [pickup, setPickup] = useState<Route>("SHOP");
  const [delivery, setDelivery] = useState<Route>("SHOP");
  const [pickupDate, setPickupDate] = useState(AppDate.today());
  const [pickupTime, setPickupTime] = useState("");
  const [nDD, setNDD] = useState(""); // "" = auto, "none" = cleared, else manual iso
  const [deliveryTime, setDeliveryTime] = useState("");
  const [feeText, setFeeText] = useState("");
  const [express, setExpress] = useState(false);
  // null = follow the automatic amount (pct of clothes); a string = the owner
  // typed their own, which may be "" mid-edit (must NOT snap back to auto).
  const [exOverride, setExOverride] = useState<string | null>(null);
  const [discountText, setDiscountText] = useState("");
  const [quickAmt, setQuickAmt] = useState("");
  const [quickPcs, setQuickPcs] = useState("");
  const [showQuickBox, setShowQuickBox] = useState(false);
  const [active, setActive] = useState<ActiveSheet | null>(null);
  const [loaded, setLoaded] = useState(false);

  // New Order Started — once per screen open, tagged new vs edit + entry point.
  useEffect(() => {
    Analytics.screen("new_order");
    Analytics.newOrderStarted(presetCustId ? "customer" : "home", editId != null);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // Prefill for edit mode.
  useEffect(() => {
    const o = editingOrder;
    if (!o || loaded || services.length === 0) return;
    setCustId(o.custId);
    setPickup(o.pickup);
    setDelivery(o.delivery);
    setPickupDate(o.pickupDate);
    setPickupTime(AppDate.to24h(o.pickupTime));
    setDeliveryTime(AppDate.to24h(o.deliveryTime));
    setNDD(o.deliveryDate === "" ? "none" : o.ddAuto ? "" : o.deliveryDate);
    setFeeText(o.fee > 0 ? String(o.fee) : "");
    setExpress(o.express);
    setDiscountText(o.discount > 0 ? String(o.discount) : "");
    // Same base as the live auto amount below: every line, quick amount included.
    const clothesTotalForEx = o.lines.reduce((s, l) => s + l.amt, 0);
    setExOverride(
      o.express && o.exAmt !== expressAuto(clothesTotalForEx, state.shop.expressPct) ? String(o.exAmt) : null,
    );
    for (const l of o.lines) {
      if (l.isQuick) {
        setQuickAmt(String(l.amt));
        setQuickPcs(l.qty > 0 ? String(l.qty) : "");
        setShowQuickBox(true);
      } else if (l.kg > 0) {
        clothes.setWeight(l.serviceId, Sel.trimKg(l.kg));
      } else {
        clothes.bump(l.serviceId, l.itemName, l.qty);
        const base =
          services.find((s) => s.id === l.serviceId)?.items.find((it) => it.name === l.itemName)?.price ?? l.base;
        if (l.price !== base) clothes.setPrice(l.serviceId, l.itemName, String(l.price));
      }
    }
    const firstReal = o.lines.find((l) => !l.isQuick);
    if (firstReal) clothes.select(firstReal.serviceId);
    setLoaded(true);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [editingOrder?.id, services.length, loaded]);

  const cust = custId != null ? Sel.customer(state, custId) : null;
  const anyHome = pickup === "HOME" || delivery === "HOME";
  const pct = state.shop.expressPct;

  const quickAmount = showQuickBox ? parseInt(quickAmt, 10) || 0 : 0;
  const clothesTotal = clothes.total(services) + quickAmount;
  const exAuto = expressAuto(clothesTotal, pct);
  const exAmt = express ? (exOverride === null ? exAuto : parseInt(exOverride, 10) || 0) : 0;
  const fee = anyHome ? parseInt(feeText, 10) || 0 : 0;
  const discount = parseInt(discountText, 10) || 0;
  const grand = Math.max(0, clothesTotal + exAmt + fee - discount);
  const empty = clothesTotal === 0;

  // delivery date resolution
  const usedWithTat = services.filter(
    (s) =>
      s.readyInDays != null &&
      ((s.mode === "WEIGHT" && (parseFloat(clothes.state.weight[s.id] ?? "") || 0) > 0) ||
        (s.mode === "PIECE" && Object.values(clothes.state.qty[s.id] ?? {}).some((q) => q > 0))),
  );
  const basis = usedWithTat.length
    ? usedWithTat.reduce((a, b) => ((a.readyInDays ?? 0) >= (b.readyInDays ?? 0) ? a : b))
    : null;
  const autoDrop = basis ? AppDate.add(pickupDate, basis.readyInDays ?? 0) : "";
  const cleared = nDD === "none";
  const manual = !cleared && nDD !== "" && nDD >= pickupDate;
  const dropIso = cleared ? "" : manual ? nDD : autoDrop;
  const ddAuto = !cleared && !manual && autoDrop !== "";

  const ready = cust != null && (!anyHome || cust.address !== "");
  const ctaLabel =
    cust == null
      ? "Choose a customer"
      : anyHome && cust.address === ""
        ? "Add address first"
        : editId != null
          ? "Save changes"
          : empty && pickup === "HOME"
            ? "Schedule pickup"
            : empty
              ? "Save order"
              : "Save & make bill";

  const hint = cleared
    ? "No delivery date. You can set it later from the ⋯ menu."
    : manual
      ? "You set this date."
      : basis
        ? `Set automatically: ${basis.name} is ready ${tatText(basis.readyInDays ?? 0)}.`
        : "Optional — leave empty if you don't know yet.";

  function save() {
    if (!ready || !cust) return;
    const result = Repo.saveOrder({
      editId,
      custId: cust.id,
      pickup,
      delivery,
      pickupDate,
      pickupTime24: pickupTime,
      deliveryDate: dropIso,
      deliveryTime24: deliveryTime,
      ddAuto,
      fee,
      express,
      exAmt,
      discount,
      lines: clothes.lines(services),
      quickAmount: showQuickBox ? parseInt(quickAmt, 10) || 0 : 0,
      quickPieces: showQuickBox ? parseInt(quickPcs, 10) || 0 : 0,
    });
    // The saved form must not stay in history (the app's nav graph pops it) —
    // otherwise browser Back lands on a blank "New order" after saving.
    if (editId != null) nav.back();
    else if (result.goToBill) nav.openBill(result.orderId, "new", { replace: true });
    else nav.openHome({ replace: true });
  }

  // ---- live bill card (shared mobile/desktop) ----
  const billCard =
    clothesTotal > 0 ? (
      <AppCard>
        <div className="flex flex-col gap-1.5 p-3.5">
          <BillLine label="Clothes" value={rupees(clothesTotal)} />
          {exAmt > 0 ? <BillLine label="Express" value={`+ ${rupees(exAmt)}`} /> : null}
          {fee > 0 ? <BillLine label="Pickup / delivery" value={`+ ${rupees(fee)}`} /> : null}
          {discount > 0 ? <BillLine label="Discount" value={`− ${rupees(discount)}`} tone="var(--color-orangetext)" /> : null}
          <Divider />
          <div className="flex w-full justify-between">
            <span className="text-[16px] font-bold">Total</span>
            <span className="bric text-[22px]">{rupees(grand)}</span>
          </div>
        </div>
      </AppCard>
    ) : null;

  return (
    <div className="flex min-h-dvh flex-col">
      <TopBar
        title={editId != null ? "Edit order" : "New order"}
        onBack={nav.back}
        trailing={editId != null ? <span className="text-[14px] text-muted">#{editId}</span> : undefined}
      />
      <div className="flex-1 lg:grid lg:grid-cols-[1fr_320px] lg:items-start lg:gap-6">
        <div className="flex flex-col gap-[22px] px-4 pb-6 pt-0.5">
          {/* ---- customer ---- */}
          <div className="flex flex-col gap-2">
            <SectionLabel text="Customer" />
            {cust ? (
              <div className="flex w-full items-center gap-2.5 rounded-[14px] border-[1.5px] border-blue bg-bluelight p-2.5">
                <Avatar text={Sel.initials(cust.name)} />
                <div className="min-w-0 flex-1">
                  <div className="text-[16px] font-bold">{cust.name}</div>
                  <div className="text-[13px] text-inksecondary">
                    {Sel.fmtPhone(cust.phone)}
                    {" · "}
                    {Sel.orderCount(state, cust) > 0 ? Sel.countNoun(Sel.orderCount(state, cust), "order") : "New customer"}
                    {Sel.balance(state, cust.id) > 0 ? ` · ${rupees(Sel.balance(state, cust.id))} baaki` : ""}
                  </div>
                </div>
                {editId == null ? (
                  <button type="button" onClick={() => { setCustId(null); setQuery(""); }} className="text-[14px] font-bold text-blue">
                    Change
                  </button>
                ) : null}
              </div>
            ) : (
              <CustomerPicker
                query={query}
                setQuery={setQuery}
                onPick={(id) => { setCustId(id); setQuery(""); }}
                onAddNew={(prefillName, prefillPhone) =>
                  setActive({
                    kind: "customerForm",
                    editId: null,
                    ctx: "order",
                    prefillName,
                    prefillPhone,
                    needAddress: anyHome,
                    onSaved: (id) => { setCustId(id); setQuery(""); },
                  })
                }
              />
            )}
          </div>

          {/* ---- pickup / delivery ---- */}
          <div className="flex flex-col gap-3.5">
            <RouteChoice label="Pickup" value={pickup} shopLabel="At shop" homeLabel="From home" onChange={setPickup} />
            <RouteChoice label="Delivery" value={delivery} shopLabel="At shop" homeLabel="To home" onChange={setDelivery} />
            {anyHome ? (
              <AppCard>
                <div className="flex flex-col gap-2.5 p-3.5">
                  <div className="flex w-full items-center">
                    <div className="min-w-0 flex-1">
                      <div className="text-[13px] font-bold text-inksecondary">Address</div>
                      <div
                        className={cls(
                          "text-[14px]",
                          cust?.address ? "text-inksecondary" : "text-orangetext",
                        )}
                      >
                        {cust ? cust.address || "No address saved yet" : "Choose a customer first"}
                      </div>
                    </div>
                    {cust ? (
                      <button
                        type="button"
                        className="text-[14px] font-bold text-blue"
                        onClick={() =>
                          setActive({
                            kind: "customerForm",
                            editId: cust.id,
                            ctx: "order",
                            needAddress: true,
                            onSaved: (id) => setCustId(id),
                          })
                        }
                      >
                        {cust.address ? "Change" : "Add"}
                      </button>
                    ) : null}
                  </div>
                  <FieldBox
                    value={feeText}
                    onChange={(v) => setFeeText(v.replace(/\D/g, "").slice(0, 4))}
                    prefix="₹"
                    placeholder="Pickup / delivery charge (optional)"
                    h={48}
                    inputMode="numeric"
                  />
                </div>
              </AppCard>
            ) : null}
          </div>

          {/* ---- dates ---- */}
          <div className="flex flex-col gap-4">
            <DateRow
              label="Pickup"
              dateText={AppDate.short(pickupDate)}
              dateIsSet
              dateValue={pickupDate}
              minIso={AppDate.today()}
              time24={pickupTime}
              onPickDate={setPickupDate}
              onPickTime={setPickupTime}
            />
            <div className="flex flex-col gap-2">
              <DateRow
                label="Delivery"
                dateText={dropIso !== "" ? AppDate.short(dropIso) : "Add date (optional)"}
                dateIsSet={dropIso !== ""}
                dateValue={dropIso !== "" ? dropIso : AppDate.add(pickupDate, 1)}
                minIso={pickupDate}
                time24={deliveryTime}
                onPickDate={setNDD}
                onPickTime={setDeliveryTime}
              />
              <div className="flex items-center gap-2">
                <span className="flex-1 text-[13px] text-muted">{hint}</span>
                {dropIso !== "" ? (
                  <button type="button" onClick={() => setNDD("none")} className="shrink-0 text-[13px] font-bold text-blue">
                    Remove date
                  </button>
                ) : cleared ? (
                  <button type="button" onClick={() => setNDD("")} className="shrink-0 text-[13px] font-bold text-blue">
                    Use automatic
                  </button>
                ) : null}
              </div>
            </div>
          </div>

          {/* ---- clothes ---- */}
          <div className="flex flex-col gap-2.5">
            <SectionLabel text="Clothes" />
            <ClothesEditor services={services} api={clothes} editablePrice />
            {showQuickBox ? (
              <AppCard>
                <div className="flex flex-col gap-2 p-3.5">
                  <div className="flex w-full items-center">
                    <span className="flex-1 text-[15px] font-bold">Quick bill</span>
                    <button
                      type="button"
                      onClick={() => { setShowQuickBox(false); setQuickAmt(""); setQuickPcs(""); }}
                      className="text-[13px] font-bold text-blue"
                    >
                      Remove
                    </button>
                  </div>
                  <div className="flex gap-2">
                    <FieldBox
                      value={quickAmt}
                      onChange={(v) => setQuickAmt(v.replace(/\D/g, "").slice(0, 6))}
                      prefix="₹"
                      placeholder="Amount"
                      h={48}
                      className="flex-1"
                      inputMode="numeric"
                    />
                    <FieldBox
                      value={quickPcs}
                      onChange={(v) => setQuickPcs(v.replace(/\D/g, "").slice(0, 3))}
                      placeholder="Pieces"
                      h={48}
                      className="flex-1"
                      inputMode="numeric"
                    />
                  </div>
                </div>
              </AppCard>
            ) : null}
          </div>

          {/* ---- extras ---- */}
          <div className="flex flex-col gap-3">
            <SectionLabel text="Extras" />
            <AppCard>
              <div className="flex w-full items-center p-3.5">
                <div className="min-w-0 flex-1">
                  <div className="text-[15px] font-bold text-orangetext">Express order</div>
                  <div className="text-[13px] text-muted">
                    +{pct}% on clothes{express && clothesTotal > 0 ? ` = ${rupees(exAuto)}` : ""} · washed first
                  </div>
                </div>
                <Toggle on={express} onColor="var(--color-orange)" onToggle={() => { setExpress((v) => !v); setExOverride(null); }} />
              </div>
            </AppCard>
            {express ? (
              <FieldBox
                value={exOverride ?? String(exAuto)}
                onChange={(v) => setExOverride(v.replace(/\D/g, "").slice(0, 5))}
                prefix="₹"
                suffix="express"
                h={48}
                inputMode="numeric"
              />
            ) : null}
            <FieldBox
              value={discountText}
              onChange={(v) => setDiscountText(v.replace(/\D/g, "").slice(0, 5))}
              prefix="₹"
              suffix="discount (optional)"
              h={48}
              inputMode="numeric"
            />
          </div>

          {/* ---- live bill (mobile, inline) ---- */}
          <div className="lg:hidden">{billCard}</div>
        </div>

        {/* ---- desktop: sticky live bill ---- */}
        <div className="hidden lg:block lg:sticky lg:top-6 lg:pb-6">
          {billCard ?? (
            <AppCard>
              <div className="p-4 text-[14px] text-muted">Add clothes to see the live bill.</div>
            </AppCard>
          )}
          <div className="mt-4">
            <PrimaryButton disabled={!ready} onClick={save}>
              {ctaLabel}
            </PrimaryButton>
          </div>
        </div>
      </div>

      {/* ---- sticky bottom bar (mobile) ---- */}
      <div className="sticky bottom-0 w-full border-t border-divider bg-card p-4 lg:hidden">
        <PrimaryButton disabled={!ready} onClick={save}>
          {ctaLabel}
        </PrimaryButton>
      </div>

      <SheetHost active={active} state={state} nav={nav} onOpen={setActive} onDismiss={() => setActive(null)} />
    </div>
  );
}

function CustomerPicker({
  query, setQuery, onPick, onAddNew,
}: {
  query: string;
  setQuery: (v: string) => void;
  onPick: (id: string) => void;
  onAddNew: (prefillName: string, prefillPhone: string) => void;
}) {
  const state = useLaundryState();
  const q = query.trim();
  const qd = q.replace(/\D/g, "");
  const results = useMemo(() => {
    if (q === "") return state.customers.slice().sort((a, b) => a.agoRank - b.agoRank).slice(0, 3);
    return state.customers
      .filter((c) => c.name.toLowerCase().includes(q.toLowerCase()) || (qd.length >= 3 && c.phone.includes(qd)))
      .slice(0, 4);
  }, [state.customers, q, qd]);
  const isPhone = q !== "" && /^[\d\s+]+$/.test(q);

  return (
    <div className="w-full rounded-[14px] border-2 border-blue bg-card">
      <FieldBox
        value={query}
        onChange={setQuery}
        placeholder="Search name or phone number"
        h={54}
        borderColor="transparent"
        borderWidth={0}
      />
      {q === "" ? <SectionLabel text="Recent customers" className="px-3.5 pt-1.5" /> : null}
      {results.map((r) => (
        <button
          key={r.id}
          type="button"
          onClick={() => onPick(r.id)}
          className="flex w-full items-center gap-2.5 px-3.5 py-2 text-left hover:bg-bg"
        >
          <Avatar text={Sel.initials(r.name)} size={34} bg="var(--color-neutralfill)" fg="var(--color-inksecondary)" />
          <span className="flex min-w-0 flex-1 flex-col">
            <span className="truncate text-[15px] font-bold">{r.name}</span>
            <span className="text-[13px] text-muted">{Sel.fmtPhone(r.phone)}</span>
          </span>
          <span className="text-[12px] text-muted">
            {Sel.orderCount(state, r) > 0 ? Sel.countNoun(Sel.orderCount(state, r), "order") : "New"}
          </span>
        </button>
      ))}
      <div className="p-2.5">
        <button
          type="button"
          onClick={() => onAddNew(isPhone ? "" : q, isPhone ? qd.slice(-10) : "")}
          className="h-[50px] w-full rounded-xl border-[1.5px] border-blue text-[15px] font-bold text-blue"
        >
          {q === "" ? "Add new customer" : `Add “${q}” as new customer`}
        </button>
      </div>
    </div>
  );
}

function RouteChoice({
  label, value, shopLabel, homeLabel, onChange,
}: {
  label: string;
  value: Route;
  shopLabel: string;
  homeLabel: string;
  onChange: (r: Route) => void;
}) {
  return (
    <div className="flex flex-col gap-1.5">
      <span className="text-[14px] font-semibold">{label}</span>
      <div className="flex w-full gap-2">
        {([["SHOP", shopLabel], ["HOME", homeLabel]] as [Route, string][]).map(([r, lb]) => {
          const selected = value === r;
          return (
            <button
              key={r}
              type="button"
              onClick={() => onChange(r)}
              className={cls(
                "h-[54px] flex-1 rounded-[14px] text-[15px] font-bold",
                selected ? "border-[1.5px] border-blue bg-bluelight text-blue" : "border border-cardborder bg-card text-ink",
              )}
            >
              {lb}
            </button>
          );
        })}
      </div>
    </div>
  );
}

function DateRow({
  label, dateText, dateIsSet, dateValue, minIso, time24, onPickDate, onPickTime,
}: {
  label: string;
  dateText: string;
  dateIsSet: boolean;
  dateValue: string;
  minIso: string;
  time24: string;
  onPickDate: (iso: string) => void;
  onPickTime: (t: string) => void;
}) {
  return (
    <div className="flex flex-col gap-1.5">
      <span className="text-[15px] font-bold">{label}</span>
      <div className="flex w-full gap-2">
        <DateTimeBox
          className="flex-[1.45]"
          icon={<IcCalendar size={20} />}
          iconTint="var(--color-blue)"
          text={dateText}
          isSet={dateIsSet}
          type="date"
          value={dateValue}
          min={minIso}
          onPick={onPickDate}
        />
        <DateTimeBox
          className="flex-1"
          icon={<IcClock size={20} />}
          iconTint={time24 !== "" ? "var(--color-inksecondary)" : "var(--color-muted)"}
          text={time24 !== "" ? AppDate.to12h(time24) : "Time (optional)"}
          isSet={time24 !== ""}
          type="time"
          value={time24}
          onPick={onPickTime}
        />
      </div>
    </div>
  );
}

function BillLine({ label, value, tone }: { label: string; value: string; tone?: string }) {
  return (
    <div className="flex w-full justify-between">
      <span className="text-[15px] text-inksecondary">{label}</span>
      <span className="text-[15px] font-semibold" style={tone ? { color: tone } : undefined}>{value}</span>
    </div>
  );
}
