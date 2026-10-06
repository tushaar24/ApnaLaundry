"use client";

import { useState } from "react";
import type { PricingMode, Service, ServiceItem } from "@/domain/models";
import { useLaundryState } from "@/data/store";
import * as Repo from "@/data/repository";
import { Analytics } from "@/analytics/events";
import {
  cls, FieldBox, OutlineButton, PillChip, PrimaryButton, Segmented, TopBar,
} from "../basics";
import { IcAdd, IcCheck, IcDelete, IcEdit, IcInfo, IcLock } from "../icons";

/**
 * Rate card editor — port of ui/screens/rates/RatesScreen.kt. Serves both
 * the first-run setup (from="setup": shop-name field + "Start taking
 * orders") and later edits from Home/Settings.
 */

type RatePage = { kind: "list" } | { kind: "edit"; serviceId: string } | { kind: "add" };

function modeLabel(s: Service): string {
  return s.mode === "WEIGHT" ? "By weight" : "Per piece";
}

function summaryLine(s: Service): string {
  if (s.mode === "WEIGHT") {
    const rate = s.ratePerKg != null ? `₹${s.ratePerKg} per kg` : "Rate not set";
    const min = s.minKg != null && s.minKg > 0 ? ` · minimum ${Math.trunc(s.minKg)} kg` : "";
    return rate + min;
  }
  const priced = s.items.filter((it) => it.price != null);
  if (priced.length === 0) return "No prices set yet — tap Edit";
  return (
    priced.slice(0, 3).map((it) => `${it.name} ₹${it.price}`).join(" · ") +
    (priced.length > 3 ? ` · +${priced.length - 3} more` : "")
  );
}

export function RatesView({ from, onDone, onBack }: { from: string; onDone: () => void; onBack: () => void }) {
  const state = useLaundryState();
  const setup = from === "setup";
  const [page, setPage] = useState<RatePage>({ kind: "list" });

  if (page.kind === "edit") {
    const svc = state.services.find((s) => s.id === page.serviceId);
    if (!svc) {
      setPage({ kind: "list" });
      return null;
    }
    return (
      <EditServicePage
        original={svc}
        canDelete={state.services.length > 1}
        onSave={(s) => { Repo.upsertService(s); Analytics.serviceEdited(s.id); setPage({ kind: "list" }); }}
        onDelete={() => { Repo.deleteService(svc.id); setPage({ kind: "list" }); }}
        onBack={() => setPage({ kind: "list" })}
      />
    );
  }
  if (page.kind === "add") {
    return (
      <AddServicePage
        existing={state.services}
        onAdd={(s) => { Repo.upsertService(s); Analytics.serviceAdded(s.mode); setPage({ kind: "list" }); }}
        onBack={() => setPage({ kind: "list" })}
      />
    );
  }
  return (
    <RateListPage
      setup={setup}
      onEdit={(id) => setPage({ kind: "edit", serviceId: id })}
      onAdd={() => setPage({ kind: "add" })}
      onDone={onDone}
      onBack={onBack}
    />
  );
}

// ───────────────────────── list ─────────────────────────

function RateListPage({
  setup, onEdit, onAdd, onDone, onBack,
}: {
  setup: boolean;
  onEdit: (id: string) => void;
  onAdd: () => void;
  onDone: () => void;
  onBack: () => void;
}) {
  const state = useLaundryState();
  const services = state.services;
  const [shopName, setShopName] = useState(state.shop.name);
  const [expressPct, setExpressPct] = useState(String(state.shop.expressPct));

  function commitAndDone() {
    Repo.updateShop(
      setup ? (shopName.trim() || "My Shop") : state.shop.name,
      parseInt(expressPct, 10) || 50,
    );
    onDone();
  }

  return (
    <div className="flex min-h-dvh flex-col">
      {!setup ? <TopBar title="Rate list" onBack={onBack} /> : null}
      <div className="flex flex-1 flex-col gap-4 px-4 py-4">
        {setup ? (
          <>
            <div className="flex items-center gap-2">
              <span className="text-blue"><IcCheck size={18} /></span>
              <span className="text-[13px] font-semibold text-muted">Number verified · Last step</span>
            </div>
            <div className="flex flex-col gap-2">
              <span className="text-[13px] font-bold text-inksecondary">SHOP NAME</span>
              <FieldBox value={shopName} onChange={setShopName} h={56} borderColor="var(--color-blue)" borderWidth={2} />
            </div>
            <h1 className="bric text-[28px]">Your rate list</h1>
            <InfoBox
              title="Already filled for you"
              body={`We added ${services.length} common services with usual prices. You can start taking orders now and change anything later from ₹ Rates on the home screen.`}
            />
          </>
        ) : (
          <InfoBox title="Change prices anytime" body="Tap Edit on a service to change its prices. New prices apply to new orders only." />
        )}

        <span className="text-[13px] font-bold text-inksecondary">YOUR SERVICES ({services.length})</span>

        <button
          type="button"
          onClick={onAdd}
          className="dash-border flex w-full items-center gap-3 rounded-2xl p-4 text-left"
        >
          <span className="flex size-10 items-center justify-center rounded-full bg-bluelight text-blue">
            <IcAdd size={22} />
          </span>
          <span className="flex min-w-0 flex-col">
            <span className="text-[16px] font-bold text-blue">Add new service</span>
            <span className="text-[13px] text-muted">Only if you do something extra, like Steam Press</span>
          </span>
        </button>

        {services.map((s) => (
          <ServiceCard key={s.id} s={s} onEdit={() => onEdit(s.id)} onDelete={() => Repo.deleteService(s.id)} />
        ))}

        <span className="text-[13px] font-bold text-inksecondary">OTHER SETTINGS · OPTIONAL</span>
        <div className="flex w-full items-center rounded-2xl border border-cardborder bg-card p-4">
          <div className="min-w-0 flex-1">
            <div className="text-[16px] font-bold">Express charge</div>
            <div className="text-[13px] text-muted">Extra you take for urgent orders</div>
          </div>
          <FieldBox
            value={expressPct}
            onChange={(v) => setExpressPct(v.replace(/\D/g, "").slice(0, 3))}
            prefix="+"
            suffix="%"
            h={48}
            className="w-[104px]"
            inputMode="numeric"
            textClass="text-[18px] font-bold"
          />
        </div>
      </div>

      <div className="sticky bottom-0 w-full border-t border-divider bg-card">
        <p className="px-4 pt-3 text-[13px] text-muted">Nothing else is needed. You can change prices anytime.</p>
        <div className="p-4">
          <PrimaryButton onClick={commitAndDone}>{setup ? "Start taking orders" : "Done"}</PrimaryButton>
        </div>
      </div>
    </div>
  );
}

function InfoBox({ title, body }: { title: string; body: string }) {
  return (
    <div className="flex w-full gap-2.5 rounded-[14px] bg-bluelight p-4">
      <span className="shrink-0 text-blue"><IcInfo size={20} /></span>
      <div className="flex flex-col gap-1">
        <span className="text-[15px] font-bold text-bluetext">{title}</span>
        <span className="text-[14px] text-bluetext">{body}</span>
      </div>
    </div>
  );
}

function ServiceCard({ s, onEdit, onDelete }: { s: Service; onEdit: () => void; onDelete: () => void }) {
  return (
    <div className="flex w-full flex-col gap-3 rounded-2xl border border-cardborder bg-card p-4">
      <div className="flex items-center gap-2.5">
        <div className="min-w-0 flex-1">
          <div className="bric truncate text-[20px]">{s.name}</div>
          <div className="text-[13px] text-muted">{modeLabel(s)}</div>
        </div>
        <button
          type="button"
          onClick={onEdit}
          className="flex h-11 items-center gap-1.5 rounded-[10px] bg-bluelight px-3.5 text-[15px] font-bold text-blue"
        >
          <IcEdit size={18} />
          Edit
        </button>
        <button
          type="button"
          onClick={onDelete}
          aria-label="Delete"
          className="flex size-11 items-center justify-center rounded-[10px] border border-orangeborder text-deletered"
        >
          <IcDelete size={20} />
        </button>
      </div>
      <div className="w-full truncate rounded-[10px] bg-neutralfill px-3 py-2.5 text-[14px] text-inksecondary">
        {summaryLine(s)}
      </div>
    </div>
  );
}

// ───────────────────────── edit / add ─────────────────────────

function SectionCard({
  title, subtitle, children,
}: { title: string; subtitle?: string; children: React.ReactNode }) {
  return (
    <div className="flex w-full flex-col rounded-2xl border border-cardborder bg-card p-4">
      <div className="text-[16px] font-bold">{title}</div>
      {subtitle ? <div className="mt-0.5 text-[13px] text-muted">{subtitle}</div> : null}
      <div className={cls("flex flex-col", subtitle ? "mt-3" : "mt-2.5")}>{children}</div>
    </div>
  );
}

function ChargeByCard({ draft, onChange }: { draft: Service; onChange: (s: Service) => void }) {
  return (
    <SectionCard title="How do you charge for this?">
      {draft.lockedToPiece ? (
        <div className="flex h-12 w-full items-center gap-2 rounded-[11px] bg-neutralfill px-3 text-inksecondary">
          <IcLock size={16} />
          <span className="text-[14px] font-semibold">Dry cleaning is always per piece</span>
        </div>
      ) : (
        <Segmented
          options={[["Per piece", draft.mode === "PIECE"], ["By weight (kg)", draft.mode === "WEIGHT"]]}
          onSelect={(i) => onChange({ ...draft, mode: (i === 0 ? "PIECE" : "WEIGHT") as PricingMode })}
        />
      )}
    </SectionCard>
  );
}

function PerPieceCard({
  draft, emptyHint, onChange,
}: { draft: Service; emptyHint?: boolean; onChange: (s: Service) => void }) {
  return (
    <SectionCard
      title="Price of each piece"
      subtitle={
        emptyHint
          ? "Fill only the clothes you take. Leave the rest empty."
          : "Leave it empty if you don't take that cloth. Empty ones won't show in orders."
      }
    >
      {draft.items.map((item, idx) => (
        <div key={item.name} className="flex h-14 w-full items-center">
          <span className={cls("flex-1 text-[16px] font-semibold", item.price == null && "text-muted")}>{item.name}</span>
          <FieldBox
            value={item.price != null ? String(item.price) : ""}
            onChange={(v) => {
              const digits = v.replace(/\D/g, "");
              const p = digits === "" ? null : parseInt(digits, 10);
              onChange({
                ...draft,
                items: draft.items.map((it, j) => (j === idx ? { ...it, price: p } : it)),
              });
            }}
            prefix="₹"
            placeholder="—"
            h={48}
            className="w-[110px]"
            inputMode="numeric"
            textClass="text-[17px] font-bold"
          />
        </div>
      ))}
    </SectionCard>
  );
}

function ByWeightCard({ draft, onChange }: { draft: Service; onChange: (s: Service) => void }) {
  return (
    <SectionCard title="Price by weight">
      <div className="mb-1 text-[14px] font-bold">Rate for 1 kg</div>
      <FieldBox
        value={draft.ratePerKg != null ? String(draft.ratePerKg) : ""}
        onChange={(v) => {
          const digits = v.replace(/\D/g, "");
          onChange({ ...draft, ratePerKg: digits === "" ? null : parseInt(digits, 10) });
        }}
        prefix="₹"
        suffix="per kg"
        h={64}
        borderColor="var(--color-blue)"
        borderWidth={2}
        inputMode="numeric"
        textClass="bric text-[28px]"
      />
      <div className="mt-2.5 flex w-full items-center">
        <div className="min-w-0 flex-1">
          <div className="text-[15px] font-bold">Minimum weight (optional)</div>
          <div className="text-[13px] text-muted">Small bags are charged for this much</div>
        </div>
        <FieldBox
          value={(() => { const v = draft.minKg ?? 0; return v % 1 === 0 ? String(Math.trunc(v)) : String(v); })()}
          onChange={(v) => {
            const t = v.replace(/[^\d.]/g, "");
            const n = parseFloat(t);
            onChange({ ...draft, minKg: Number.isNaN(n) ? null : n });
          }}
          suffix="kg"
          h={52}
          className="w-[100px]"
          inputMode="decimal"
          textClass="text-[17px] font-bold"
        />
      </div>
    </SectionCard>
  );
}

function ReadyInCard({ current, onPick }: { current: number | null; onPick: (v: number | null) => void }) {
  const [moreOpen, setMoreOpen] = useState(current != null && current >= 4);
  return (
    <SectionCard title="Ready in (optional)" subtitle="Fills the delivery date for you. Skip it if it changes every time.">
      <div className="flex flex-wrap gap-1.5">
        {([[0, "Same day"], [1, "1 day"], [2, "2 days"], [3, "3 days"]] as [number, string][]).map(([v, label]) => (
          <PillChip
            key={v}
            label={label}
            selected={current === v && !moreOpen}
            onClick={() => { onPick(current === v ? null : v); setMoreOpen(false); }}
          />
        ))}
        <PillChip
          label="More"
          selected={moreOpen || (current != null && current >= 4)}
          onClick={() => {
            const next = !moreOpen;
            setMoreOpen(next);
            if (!next) onPick(null);
          }}
        />
      </div>
      {moreOpen || (current != null && current >= 4) ? (
        <FieldBox
          value={current != null && current >= 4 ? String(current) : ""}
          onChange={(v) => {
            const t = v.replace(/\D/g, "").slice(0, 2);
            onPick(t === "" ? null : parseInt(t, 10));
          }}
          placeholder="4"
          suffix="days"
          h={48}
          className="mt-2.5 w-[120px]"
          inputMode="numeric"
        />
      ) : null}
    </SectionCard>
  );
}

function EditServicePage({
  original, canDelete, onSave, onDelete, onBack,
}: {
  original: Service;
  canDelete: boolean;
  onSave: (s: Service) => void;
  onDelete: () => void;
  onBack: () => void;
}) {
  const [draft, setDraft] = useState(original);

  return (
    <div className="flex min-h-dvh flex-col">
      <TopBar title="Edit service" onBack={onBack} />
      <div className="flex flex-1 flex-col gap-4 px-4 pb-4">
        <p className="text-[15px] text-muted">Change only what you need. Everything else can stay as it is.</p>
        <SectionCard title="Service name">
          <FieldBox value={draft.name} onChange={(v) => setDraft({ ...draft, name: v })} h={56} textClass="text-[18px] font-bold" />
        </SectionCard>
        <ChargeByCard draft={draft} onChange={setDraft} />
        {draft.mode === "PIECE" ? <PerPieceCard draft={draft} onChange={setDraft} /> : <ByWeightCard draft={draft} onChange={setDraft} />}
        <ReadyInCard current={draft.readyInDays} onPick={(v) => setDraft({ ...draft, readyInDays: v })} />
      </div>
      <div className="sticky bottom-0 flex w-full gap-3 border-t border-divider bg-card p-4">
        {canDelete ? (
          <OutlineButton border="var(--color-orangeborder)" fg="var(--color-deletered)" onClick={onDelete}>
            Delete
          </OutlineButton>
        ) : null}
        <PrimaryButton className="flex-1" onClick={() => onSave(draft)}>
          Save
        </PrimaryButton>
      </div>
    </div>
  );
}

function AddServicePage({
  existing, onAdd, onBack,
}: { existing: Service[]; onAdd: (s: Service) => void; onBack: () => void }) {
  const suggestions = ["Steam Press", "Shoe Cleaning", "Curtain Wash", "Carpet Cleaning", "Starch"];
  const baseItems: ServiceItem[] =
    existing.find((s) => s.mode === "PIECE")?.items.map((it) => ({ name: it.name, price: null })) ?? [];

  const [draft, setDraft] = useState<Service>({
    id: `s${Date.now()}`,
    name: "",
    mode: "PIECE",
    ratePerKg: null,
    minKg: null,
    readyInDays: null,
    lockedToPiece: false,
    items: baseItems,
    sortOrder: existing.length,
  });
  const named = draft.name.trim() !== "";

  return (
    <div className="flex min-h-dvh flex-col">
      <TopBar title="Add new service" onBack={onBack} />
      <div className="flex flex-1 flex-col gap-4 px-4 pb-4">
        <InfoBox title="Add a new service" body="Add only if you do a service that is not in your list. You can skip this and add it later too." />
        <SectionCard title="What is the service called?" subtitle="Type a name or tap one below.">
          <FieldBox value={draft.name} onChange={(v) => setDraft({ ...draft, name: v })} placeholder="e.g. Steam Press" h={56} />
          <div className="mt-2.5 flex flex-wrap gap-1.5">
            {suggestions.map((name) => (
              <PillChip key={name} label={name} selected={draft.name === name} onClick={() => setDraft({ ...draft, name })} />
            ))}
          </div>
        </SectionCard>
        <ChargeByCard draft={draft} onChange={setDraft} />
        {draft.mode === "PIECE" ? (
          <PerPieceCard draft={draft} emptyHint onChange={setDraft} />
        ) : (
          <ByWeightCard draft={draft} onChange={setDraft} />
        )}
        <ReadyInCard current={draft.readyInDays} onPick={(v) => setDraft({ ...draft, readyInDays: v })} />
      </div>
      <div className="sticky bottom-0 w-full border-t border-divider bg-card p-4">
        <PrimaryButton
          disabled={!named}
          onClick={() =>
            onAdd({
              ...draft,
              name: draft.name.trim(),
              ratePerKg: draft.mode === "WEIGHT" ? (draft.ratePerKg ?? 0) : null,
              minKg: draft.mode === "WEIGHT" ? (draft.minKg ?? 0) : null,
            })
          }
        >
          {named ? "Add service" : "Type a name to add"}
        </PrimaryButton>
      </div>
    </div>
  );
}
