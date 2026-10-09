"use client";

import { useRef, useState, type ReactNode } from "react";
import type { BillDetails } from "@/domain/models";
import {
  cleanAddress, cleanGstin, cleanPhone, cleanTermsCustom, cleanUpi, fmtBillPhone, gstinCheck,
  isGstinValid, isPhoneValid, isUpiValid, TERM_PRESETS, termLines, upiCheck, type Check,
} from "@/domain/billDetails";
import { uploadLogo } from "@/data/logoUpload";
import * as Repo from "@/data/repository";
import { AppSheet } from "../sheet";
import { cls, FieldBox, OutlineButton, PrimaryButton } from "../basics";
import { IcBadge, IcCall, IcCamera, IcCheck, IcChevronRight, IcImage, IcList, IcPin, IcQr } from "../icons";
import { logoUrl } from "@/domain/billDetails";

/**
 * "+ Add fields" (handoff §7.1/§7.2): a list of optional bill fields, each
 * opening its editor in the same sheet. Done / dismiss in an editor returns
 * to the list so several fields can be added in one go; Done on the list
 * closes the sheet. Every change goes straight to `onChange`, so the bill
 * behind the sheet updates live.
 */

type FieldKey = "upi" | "logo" | "address" | "terms" | "gstin" | "phone";

export function FieldsSheet({
  details, onChange, onClose,
}: {
  details: BillDetails;
  onChange: (patch: Partial<BillDetails>) => void;
  onClose: () => void;
}) {
  const [editing, setEditing] = useState<FieldKey | null>(null);
  const back = () => setEditing(null);

  if (editing === "upi") return <UpiEditor value={details.upiId} onSave={(v) => { onChange({ upiId: v }); back(); }} onBack={back} />;
  if (editing === "logo") return <LogoEditor logoId={details.logoId} onChange={(id) => onChange({ logoId: id })} onBack={back} />;
  if (editing === "address") return <AddressEditor value={details.address} onSave={(v) => { onChange({ address: v }); back(); }} onBack={back} />;
  if (editing === "terms") return <TermsEditor terms={details.terms} custom={details.termsCustom} onSave={(terms, termsCustom) => { onChange({ terms, termsCustom }); back(); }} onBack={back} />;
  if (editing === "gstin") return <GstinEditor value={details.gstin} onSave={(v) => { onChange({ gstin: v }); back(); }} onBack={back} />;
  if (editing === "phone") return <PhoneEditor value={details.billPhone} onSave={(v) => { onChange({ billPhone: v }); back(); }} onBack={back} />;

  const terms = termLines(details).length;
  const rows: { key: FieldKey; icon: ReactNode; title: string; empty: string; set: string }[] = [
    { key: "upi", icon: <IcQr size={22} />, title: "UPI QR on bill", empty: "Customers scan and pay you. Money goes to your bank.", set: isUpiValid(details.upiId) ? `UPI · ${details.upiId}` : "" },
    { key: "logo", icon: <IcImage size={22} />, title: "Shop logo", empty: "Your logo at the top of every bill", set: details.logoId ? "Logo added" : "" },
    { key: "address", icon: <IcPin size={22} />, title: "Shop address", empty: "Helps new customers find your shop", set: details.address },
    { key: "terms", icon: <IcList size={22} />, title: "Terms & conditions", empty: "e.g. not responsible for colour fading", set: terms ? `${terms} line${terms > 1 ? "s" : ""} at the bottom` : "" },
    { key: "gstin", icon: <IcBadge size={22} />, title: "GSTIN", empty: "Only for GST-registered shops", set: isGstinValid(details.gstin) ? details.gstin : "" },
    { key: "phone", icon: <IcCall size={22} />, title: "Phone number", empty: "Customers call or WhatsApp you on this", set: isPhoneValid(details.billPhone) ? fmtBillPhone(details.billPhone) : "" },
  ];

  return (
    <AppSheet title="Add fields to your bill" subtitle="All optional. Your bill already works without these." onDismiss={onClose}>
      <div className="flex flex-col divide-y divide-divider overflow-hidden rounded-2xl border border-cardborder bg-card">
        {rows.map((r) => (
          <button
            key={r.key}
            type="button"
            onClick={() => setEditing(r.key)}
            className="flex min-h-[64px] w-full items-center gap-3 px-4 py-3 text-left hover:bg-black/[0.02]"
          >
            <span className="flex size-10 shrink-0 items-center justify-center rounded-xl bg-bluelight text-blue">{r.icon}</span>
            <span className="flex min-w-0 flex-1 flex-col">
              <span className="text-[15px] font-bold">{r.title}</span>
              <span className={cls("truncate text-[13px]", r.set ? "font-semibold text-greentext" : "text-muted")}>{r.set || r.empty}</span>
            </span>
            <span className="flex shrink-0 items-center gap-0.5 text-[14px] font-bold text-blue">
              {r.set ? "Change" : "Add"}
              <IcChevronRight size={16} />
            </span>
          </button>
        ))}
      </div>
      <PrimaryButton className="mt-4" onClick={onClose}>Done</PrimaryButton>
    </AppSheet>
  );
}

// ───────────────────────── editors ─────────────────────────

function EditorFooter({ canRemove, onRemove, onDone, doneLabel = "Done" }: { canRemove: boolean; onRemove: () => void; onDone: () => void; doneLabel?: string }) {
  return (
    <div className="mt-5 flex gap-3">
      {canRemove ? (
        <OutlineButton className="w-[38%] shrink-0" onClick={onRemove} border="var(--color-orange)" fg="var(--color-deletered)">
          Remove
        </OutlineButton>
      ) : null}
      <PrimaryButton className="flex-1" onClick={onDone}>{doneLabel}</PrimaryButton>
    </div>
  );
}

function CheckLine({ check }: { check: Check }) {
  if (!check) return null;
  return (
    <p className={cls("mt-2 flex items-center gap-1.5 text-[13px] font-semibold", check.ok ? "text-greentext" : "text-orangetext")} aria-live="polite">
      {check.ok ? <IcCheck size={15} strokeWidth={3} /> : null}
      {check.message}
    </p>
  );
}

function UpiEditor({ value, onSave, onBack }: { value: string; onSave: (v: string) => void; onBack: () => void }) {
  const [v, setV] = useState(value);
  const check = upiCheck(v);
  return (
    <AppSheet title="UPI for payments" subtitle="A pay QR prints on every bill, so customers pay you straight to your bank." onDismiss={onBack}>
      <label htmlFor="upi" className="text-[13px] font-bold text-inksecondary">UPI ID</label>
      <FieldBox
        value={v}
        onChange={(x) => setV(cleanUpi(x))}
        placeholder="e.g. sharmalaundry@okaxis"
        h={56}
        className="mt-1.5"
        borderColor={check && !check.ok ? "var(--color-orange)" : undefined}
        autoFocus
        inputProps={{ id: "upi", autoCapitalize: "none", autoCorrect: "off", spellCheck: false, enterKeyHint: "done" }}
      />
      <CheckLine check={check} />
      <p className="mt-2 text-[13px] text-muted">Find it in GPay, PhonePe or Paytm — tap your photo at the top.</p>
      <EditorFooter canRemove={value !== ""} onRemove={() => onSave("")} onDone={() => onSave(v)} />
    </AppSheet>
  );
}

function AddressEditor({ value, onSave, onBack }: { value: string; onSave: (v: string) => void; onBack: () => void }) {
  const [v, setV] = useState(value);
  return (
    <AppSheet title="Shop address" subtitle="Prints under your laundry name, so new customers can find you." onDismiss={onBack}>
      <label htmlFor="address" className="sr-only">Shop address</label>
      <textarea
        id="address"
        value={v}
        onChange={(e) => setV(e.target.value.slice(0, 120))}
        placeholder="Shop no., road, area, city"
        rows={3}
        autoFocus
        className="w-full resize-none rounded-xl border-[1.5px] border-fieldborder bg-card px-3.5 py-3 text-[17px] font-semibold text-ink placeholder:text-muted"
      />
      <p className="mt-1 text-right text-[12px] text-faint">{v.length}/120</p>
      <EditorFooter canRemove={value !== ""} onRemove={() => onSave("")} onDone={() => onSave(cleanAddress(v))} />
    </AppSheet>
  );
}

function GstinEditor({ value, onSave, onBack }: { value: string; onSave: (v: string) => void; onBack: () => void }) {
  const [v, setV] = useState(value);
  const check = gstinCheck(v);
  return (
    <AppSheet title="GSTIN" subtitle="15 letters and numbers. Skip this if your shop is not GST registered." onDismiss={onBack}>
      <label htmlFor="gstin" className="sr-only">GSTIN</label>
      <FieldBox
        value={v}
        onChange={(x) => setV(cleanGstin(x))}
        placeholder="e.g. 27ABCDE1234F1Z5"
        h={56}
        borderColor={check && !check.ok ? "var(--color-orange)" : undefined}
        textClass="text-[18px] font-bold tracking-wider"
        autoFocus
        inputProps={{ id: "gstin", autoCapitalize: "characters", autoCorrect: "off", spellCheck: false, maxLength: 15 }}
      />
      <CheckLine check={check} />
      <EditorFooter canRemove={value !== ""} onRemove={() => onSave("")} onDone={() => onSave(v)} />
    </AppSheet>
  );
}

function PhoneEditor({ value, onSave, onBack }: { value: string; onSave: (v: string) => void; onBack: () => void }) {
  const [v, setV] = useState(value);
  return (
    <AppSheet title="Phone number on bill" subtitle="Customers call or WhatsApp you on this number." onDismiss={onBack}>
      <label htmlFor="bill-phone" className="sr-only">Phone number</label>
      <FieldBox
        value={v}
        onChange={(x) => setV(x.replace(/\D/g, "").slice(0, 10))}
        prefix="+91"
        placeholder="98765 43210"
        h={56}
        inputMode="numeric"
        autoFocus
        inputProps={{ id: "bill-phone", autoComplete: "tel-national" }}
      />
      <EditorFooter canRemove={value !== ""} onRemove={() => onSave("")} onDone={() => onSave(cleanPhone(v))} />
    </AppSheet>
  );
}

function TermsEditor({ terms, custom, onSave, onBack }: { terms: string[]; custom: string; onSave: (t: string[], c: string) => void; onBack: () => void }) {
  const [picked, setPicked] = useState<string[]>(terms);
  const [own, setOwn] = useState(custom);
  const toggle = (id: string) => setPicked((p) => (p.includes(id) ? p.filter((x) => x !== id) : [...p, id]));
  return (
    <AppSheet title="Terms & conditions" subtitle="Small lines at the bottom of every bill. Tick the ones you want." onDismiss={onBack}>
      <div className="flex flex-col gap-2">
        {TERM_PRESETS.map((t) => {
          const on = picked.includes(t.id);
          return (
            <label key={t.id} className={cls("flex min-h-[56px] cursor-pointer items-start gap-3 rounded-xl border-[1.5px] bg-card px-3.5 py-3", on ? "border-blue" : "border-cardborder")}>
              <input type="checkbox" checked={on} onChange={() => toggle(t.id)} className="mt-0.5 size-5 shrink-0 accent-[var(--color-blue)]" />
              <span className="text-[14px] font-medium">{t.text}</span>
            </label>
          );
        })}
      </div>
      <label htmlFor="own-term" className="mt-4 block text-[13px] font-bold text-inksecondary">Your own line</label>
      <FieldBox
        value={own}
        onChange={(x) => setOwn(x.slice(0, 80))}
        placeholder="e.g. Sunday closed"
        h={52}
        className="mt-1.5"
        inputProps={{ id: "own-term", maxLength: 80 }}
      />
      <EditorFooter
        canRemove={terms.length > 0 || custom !== ""}
        onRemove={() => onSave([], "")}
        onDone={() => onSave(TERM_PRESETS.map((t) => t.id).filter((id) => picked.includes(id)), cleanTermsCustom(own))}
      />
    </AppSheet>
  );
}

function LogoEditor({ logoId, onChange, onBack }: { logoId: string; onChange: (id: string) => void; onBack: () => void }) {
  const gallery = useRef<HTMLInputElement>(null);
  const camera = useRef<HTMLInputElement>(null);
  const [busy, setBusy] = useState(false);

  const picked = async (files: FileList | null) => {
    const file = files?.[0];
    if (!file) return;
    setBusy(true);
    try {
      onChange(await uploadLogo(file));
    } catch (e) {
      Repo.showInfo(e instanceof Error ? e.message : "Couldn't add the logo — try again");
    } finally {
      setBusy(false);
      if (gallery.current) gallery.current.value = "";
      if (camera.current) camera.current.value = "";
    }
  };

  return (
    <AppSheet title="Shop logo" subtitle="Prints at the top of every bill, next to your laundry name." onDismiss={onBack}>
      <div className="flex items-center gap-4">
        <div
          className={cls(
            "flex size-[72px] shrink-0 items-center justify-center overflow-hidden rounded-2xl bg-card",
            logoId ? "border border-cardborder" : "border-2 border-dashed border-dashborder text-faint",
          )}
        >
          {busy ? (
            <span className="text-[12px] font-semibold text-muted">Adding…</span>
          ) : logoId ? (
            // eslint-disable-next-line @next/next/no-img-element -- small proxied asset
            <img src={logoUrl(logoId)} alt="Your shop logo" className="size-full object-contain" />
          ) : (
            <IcImage size={28} />
          )}
        </div>
        <p className="text-[14px] text-muted">
          {logoId
            ? "Looks good. It shows on all three designs. Tap below to change it."
            : "A square logo works best. A clear photo of your shop board also works."}
        </p>
      </div>
      <div className="mt-4 grid grid-cols-2 gap-3">
        <OutlineButton h={48} onClick={() => gallery.current?.click()} className="flex items-center justify-center gap-2">
          <IcImage size={18} /> From gallery
        </OutlineButton>
        <OutlineButton h={48} onClick={() => camera.current?.click()} className="flex items-center justify-center gap-2">
          <IcCamera size={18} /> Take photo
        </OutlineButton>
      </div>
      {/* The browser asks for camera / photo access only when a button is tapped. */}
      <input ref={gallery} type="file" accept="image/*" className="hidden" onChange={(e) => void picked(e.target.files)} />
      <input ref={camera} type="file" accept="image/*" capture="environment" className="hidden" onChange={(e) => void picked(e.target.files)} />
      <p className="mt-3 text-[13px] text-muted">No logo? Skip this — your laundry name already looks good on the bill.</p>
      <EditorFooter canRemove={logoId !== ""} onRemove={() => { onChange(""); onBack(); }} onDone={onBack} />
    </AppSheet>
  );
}
