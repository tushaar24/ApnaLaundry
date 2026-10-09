"use client";

import { useCallback, useEffect, useLayoutEffect, useMemo, useRef, useState } from "react";
import type { BillDetails, BillTemplate } from "@/domain/models";
import {
  addedFieldsCount, BILL_TEMPLATES, cleanName, isNameOk, sampleOrder,
} from "@/domain/billDetails";
import { sampleReceipt } from "@/domain/billReceipt";
import { useLaundryState } from "@/data/store";
import * as Repo from "@/data/repository";
import { prefs } from "@/data/prefs";
import { billPreviewUrl } from "../billPdf";
import { billHeight, prepareBillAssets } from "../billRender";
import { sendTestBill } from "../billActions";
import { prefetchSampleLink } from "../billLinks";
import { cls, FieldBox, OutlineButton, PrimaryButton, TopBar } from "../basics";
import { IcAdd, IcChevronLeft, IcChevronRight, IcWhatsApp } from "../icons";
import { BottomBar, StepBar } from "./steps";
import { FieldsSheet } from "./billFields";

/**
 * Onboarding step 3 · "Your bill" (handoff §7) and, with mode="settings",
 * Settings → Bill design & details (§7 edit mode). Shows the owner's real
 * bill — their name, rates, phone — in the three designs on a swipe strip.
 *
 * Onboarding saves every change straight to the shop (so a killed app
 * resumes with it); Settings keeps a draft until Save.
 */

const SLIDE_W = 322;
const GAP = 12;

export function BillDesignScreen({
  mode, onBack, onDone,
}: {
  mode: "onboarding" | "settings";
  onBack: () => void;
  onDone: () => void;
}) {
  const state = useLaundryState();
  const shop = state.shop;
  const loginPhone = prefs.userPhone ?? shop.phone;
  const editing = mode === "settings";

  const [name, setName] = useState(shop.name);
  const [draft, setDraft] = useState<BillDetails>(() => ({
    billPhone: shop.billPhone || loginPhone,
    address: shop.address, gstin: shop.gstin, email: shop.email, upiId: shop.upiId, logoId: shop.logoId,
    terms: shop.terms, termsCustom: shop.termsCustom, billTemplate: shop.billTemplate,
  }));
  const [sheet, setSheet] = useState(false);
  const [tested, setTested] = useState(false);

  const change = useCallback((patch: Partial<BillDetails>) => {
    setDraft((d) => ({ ...d, ...patch }));
    if (!editing) Repo.updateShopDetails(patch);
  }, [editing]);

  // ── the three bills ──
  const order = useMemo(() => sampleOrder(state.services, shop.expressPct, 1001), [state.services, shop.expressPct]);
  // "Test on WhatsApp" sends the sample-bill link; fetch it before the tap.
  useEffect(() => {
    void prefetchSampleLink();
  }, []);
  const receipts = useMemo(
    () => BILL_TEMPLATES.map((t) =>
      sampleReceipt({ ...shop, ...draft, name: editing ? cleanName(name) || shop.name : shop.name }, order, t.id)),
    [shop, draft, order, editing, name],
  );
  const [images, setImages] = useState<string[]>([]);
  useEffect(() => {
    let live = true;
    const t = setTimeout(() => {
      void prepareBillAssets(receipts[0]).then(() => {
        if (!live) return;
        // Equal heights: every slide stretches to the tallest bill.
        const h = Math.max(...receipts.map(billHeight));
        setImages(receipts.map((r) => billPreviewUrl(r, h)));
      });
    }, 120);
    return () => {
      live = false;
      clearTimeout(t);
    };
  }, [receipts]);

  // ── strip <-> selected design ──
  const strip = useRef<HTMLDivElement>(null);
  const index = Math.max(0, BILL_TEMPLATES.findIndex((t) => t.id === draft.billTemplate));
  const placed = useRef(false);
  useLayoutEffect(() => {
    // Start on the saved design without animating, once the slides exist.
    if (placed.current || !strip.current || images.length === 0) return;
    strip.current.scrollLeft = index * (SLIDE_W + GAP);
    placed.current = true;
  }, [images.length, index]);

  const select = useCallback((t: BillTemplate) => {
    setDraft((d) => (d.billTemplate === t ? d : { ...d, billTemplate: t }));
    if (!editing) Repo.updateShopDetails({ billTemplate: t });
  }, [editing]);

  const scrollEnd = useRef<ReturnType<typeof setTimeout> | null>(null);
  const onScroll = () => {
    if (scrollEnd.current) clearTimeout(scrollEnd.current);
    scrollEnd.current = setTimeout(() => {
      const el = strip.current;
      if (!el) return;
      const i = Math.min(BILL_TEMPLATES.length - 1, Math.max(0, Math.round(el.scrollLeft / (SLIDE_W + GAP))));
      select(BILL_TEMPLATES[i].id);
    }, 90);
  };
  const go = (i: number) => {
    const el = strip.current;
    if (!el) return;
    el.scrollTo({ left: i * (SLIDE_W + GAP), behavior: "smooth" });
    select(BILL_TEMPLATES[i].id);
  };

  const added = addedFieldsCount(draft, loginPhone);
  const testOnWhatsApp = () => {
    sendTestBill(receipts[index]);
    setTested(true);
    Repo.showInfo("Opening WhatsApp · pick who gets the test bill");
  };

  const save = () => {
    if (!isNameOk(name)) return;
    Repo.updateShopDetails({ ...draft, name: cleanName(name) });
    Repo.showInfo("Bill saved");
    onDone();
  };

  return (
    <div className="flex min-h-dvh flex-col">
      {editing ? <TopBar title="Bill design & details" onBack={onBack} /> : null}
      <div className="flex flex-1 flex-col gap-4 px-4 py-4">
        {editing ? (
          <div className="flex flex-col gap-1.5">
            <label htmlFor="bill-shop-name" className="text-[13px] font-bold text-inksecondary">Laundry name</label>
            <FieldBox
              value={name}
              onChange={(v) => setName(v.slice(0, 40))}
              h={56}
              inputProps={{ id: "bill-shop-name", autoCapitalize: "words", maxLength: 40 }}
            />
          </div>
        ) : (
          <>
            <StepBar step={3} onBack={onBack} />
            <h1 className="bric mt-1 text-[28px]">Your bill is ready</h1>
            <p className="-mt-2 text-[14px] text-muted">Your name, rates and phone number are already on it.</p>
          </>
        )}

        {/* design switcher */}
        <div className="flex items-center gap-2 rounded-2xl border border-cardborder bg-card p-1.5">
          <button
            type="button"
            aria-label="Previous design"
            disabled={index === 0}
            onClick={() => go(index - 1)}
            className="flex size-11 items-center justify-center rounded-xl text-ink disabled:text-[#C9C3B8]"
          >
            <IcChevronLeft size={22} />
          </button>
          <div className="flex flex-1 flex-col items-center" aria-live="polite">
            <span className="text-[14px]">
              <span className="text-[11px] font-bold tracking-wider text-faint">DESIGN </span>
              <span className="font-bold">{BILL_TEMPLATES[index].label}</span>
            </span>
            <span className="text-[12px] text-muted">{index + 1} of {BILL_TEMPLATES.length} · swipe the bill to change</span>
          </div>
          <button
            type="button"
            aria-label="Next design"
            disabled={index === BILL_TEMPLATES.length - 1}
            onClick={() => go(index + 1)}
            className="flex size-11 items-center justify-center rounded-xl text-ink disabled:text-[#C9C3B8]"
          >
            <IcChevronRight size={22} />
          </button>
        </div>

        <div className="grid grid-cols-2 gap-3">
          <button
            type="button"
            onClick={() => setSheet(true)}
            className="flex h-12 items-center justify-center gap-1.5 rounded-[14px] bg-bluelight text-[15px] font-bold text-blue"
          >
            <IcAdd size={18} /> Add fields
            {added > 0 ? (
              <span className="ml-0.5 flex h-5 min-w-5 items-center justify-center rounded-full bg-blue px-1.5 text-[12px] text-ondark">{added}</span>
            ) : null}
          </button>
          <OutlineButton h={48} onClick={testOnWhatsApp} border="var(--color-fieldborder)" fg="var(--color-ink)" className="flex items-center justify-center gap-1.5 text-[15px]">
            <IcWhatsApp size={18} className="text-[#25D366]" />
            {tested ? "Sent · again" : "Test on WhatsApp"}
          </OutlineButton>
        </div>

        {/* the bills: swipe strip, one slide per design */}
        <div
          ref={strip}
          onScroll={onScroll}
          className="-mx-4 flex snap-x snap-mandatory overflow-x-auto px-4 pb-1 [scrollbar-width:none] [&::-webkit-scrollbar]:hidden"
          style={{ gap: GAP, scrollPaddingInline: 16 }}
          aria-label="Bill designs"
        >
          {BILL_TEMPLATES.map((t, i) => (
            <div key={t.id} className="shrink-0 snap-start" style={{ width: SLIDE_W }}>
              {images[i] ? (
                // eslint-disable-next-line @next/next/no-img-element -- a data: URL of the rendered bill
                <img
                  src={images[i]}
                  alt={`${t.label} bill design`}
                  className={cls("w-full rounded-2xl border bg-white shadow-sm", i === index ? "border-blue" : "border-cardborder")}
                />
              ) : (
                <div className="h-[520px] w-full rounded-2xl border border-cardborder bg-white" />
              )}
            </div>
          ))}
        </div>

        <div className="rounded-[14px] bg-[#FBEEDC] p-4 text-[13.5px] text-[#6B3A10]">
          <span className="font-bold">Good to know: </span>
          Express charge and discount are sample lines. They show on a bill only when you add them to an order — as a % or a fixed amount.
        </div>
      </div>

      {editing ? (
        <BottomBar>
          <PrimaryButton onClick={save} disabled={!isNameOk(name)}>Save</PrimaryButton>
        </BottomBar>
      ) : (
        <BottomBar note="You can change the design and details anytime in Settings.">
          <PrimaryButton onClick={onDone}>Start taking orders</PrimaryButton>
        </BottomBar>
      )}

      {sheet ? <FieldsSheet details={draft} onChange={change} onClose={() => setSheet(false)} /> : null}
    </div>
  );
}
