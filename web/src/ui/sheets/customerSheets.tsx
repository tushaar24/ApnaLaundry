"use client";

import { useState } from "react";
import { rupees } from "@/core/money";
import type { LaundryState, PayMethod } from "@/domain/models";
import * as Sel from "@/domain/selectors";
import * as Repo from "@/data/repository";
import { FieldBox, PillChip, PrimaryButton } from "../basics";
import { AppSheet } from "../sheet";
import type { ActiveSheet } from "./types";

/** Ports of ui/sheets/CustomerSheets.kt. */

function Field({
  label, value, onChange, placeholder, prefix, inputMode,
}: {
  label: string;
  value: string;
  onChange: (v: string) => void;
  placeholder: string;
  prefix?: string;
  inputMode?: "numeric" | "tel" | "text";
}) {
  return (
    <div>
      <div className="mb-1.5 text-[14px] font-semibold">{label}</div>
      <FieldBox value={value} onChange={onChange} placeholder={placeholder} prefix={prefix} inputMode={inputMode} />
    </div>
  );
}

export function CustomerFormSheet({
  state, form, onDismiss,
}: {
  state: LaundryState;
  form: Extract<ActiveSheet, { kind: "customerForm" }>;
  onDismiss: () => void;
}) {
  const editing = form.editId != null;
  const existing = editing ? state.customers.find((c) => c.id === form.editId) : undefined;
  const [name, setName] = useState(existing?.name ?? form.prefillName ?? "");
  const [phone, setPhone] = useState(existing?.phone ?? form.prefillPhone ?? "");
  const [address, setAddress] = useState(existing?.address ?? "");
  const [oldBaaki, setOldBaaki] = useState("");

  const dup =
    phone.length === 10
      ? state.customers.find((c) => c.phone === phone && !(editing && c.id === form.editId))
      : undefined;
  const addrNeeded = !!form.needAddress && form.ctx === "order";
  const valid = name.trim() !== "" && phone.length === 10 && !dup && (!addrNeeded || address.trim() !== "");

  return (
    <AppSheet title={editing ? "Edit customer" : "New customer"} onDismiss={onDismiss}>
      <div className="flex flex-col gap-3">
        <Field label="Name" value={name} onChange={setName} placeholder="Customer name" />
        <Field
          label="Phone"
          value={phone}
          onChange={(v) => setPhone(v.replace(/\D/g, "").slice(0, 10))}
          placeholder="10-digit number"
          prefix="+91"
          inputMode="tel"
        />
        {dup ? (
          <div className="flex w-full items-center rounded-xl bg-orangelight p-3">
            <span className="flex-1 text-[14px] font-semibold text-orangedeep">Already saved as {dup.name}</span>
            <button
              type="button"
              className="text-[14px] font-bold text-blue"
              onClick={() => {
                form.onSaved?.(dup.id);
                onDismiss();
              }}
            >
              Use this
            </button>
          </div>
        ) : null}
        <div>
          <div className="mb-1.5 flex w-full justify-between">
            <span className="text-[14px] font-semibold">Address</span>
            <span className={addrNeeded ? "text-[12px] text-orangetext" : "text-[12px] text-muted"}>
              {addrNeeded ? "Needed for home pickup / delivery" : "Optional"}
            </span>
          </div>
          <FieldBox
            value={address}
            onChange={setAddress}
            placeholder="House, area, landmark"
            borderColor={addrNeeded && address.trim() === "" ? "var(--color-orangeborder)" : undefined}
          />
        </div>
        {form.ctx === "list" && !editing ? (
          <Field
            label="Old baaki from your notebook"
            value={oldBaaki}
            onChange={(v) => setOldBaaki(v.replace(/\D/g, "").slice(0, 6))}
            placeholder="0"
            prefix="₹"
            inputMode="numeric"
          />
        ) : null}
        <PrimaryButton
          disabled={!valid}
          onClick={() => {
            const id = Repo.saveCustomer(
              form.editId ?? null,
              name.trim(),
              phone,
              address.trim(),
              parseInt(oldBaaki, 10) || 0,
              form.ctx === "list",
            );
            form.onSaved?.(id);
            onDismiss();
          }}
        >
          {editing ? "Save changes" : "Save customer"}
        </PrimaryButton>
      </div>
    </AppSheet>
  );
}

export function ReceivePaymentSheet({
  state, custId, onDismiss,
}: { state: LaundryState; custId: string; onDismiss: () => void }) {
  const c = Sel.customer(state, custId);
  const bal = Sel.balance(state, custId);
  const [amt, setAmt] = useState(bal > 0 ? String(bal) : "");
  const got = parseInt(amt, 10) || 0;
  const left = bal - got;

  const subtitle =
    bal > 0
      ? `${Sel.firstName(c.name)} has to pay ${rupees(bal)}`
      : bal < 0
        ? `${Sel.firstName(c.name)} already has ${rupees(bal)} advance`
        : "Nothing due. Anything you take becomes advance.";

  const preview =
    amt === ""
      ? { bg: "var(--color-bg)", fg: "var(--color-muted)", text: "Type the amount you got" }
      : left > 0
        ? { bg: "var(--color-orangelight)", fg: "var(--color-orangedeep)", text: `${rupees(left)} will still be baaki` }
        : left < 0
          ? { bg: "var(--color-bluelight)", fg: "var(--color-bluetext)", text: `${rupees(left)} extra — kept as advance for next bill` }
          : { bg: "var(--color-neutralfill)", fg: "var(--color-ink)", text: "Full payment — all clear" };

  const pay = (method: PayMethod) => {
    Repo.receivePayment(custId, got, method);
    onDismiss();
  };

  return (
    <AppSheet title="Receive payment" subtitle={subtitle} onDismiss={onDismiss}>
      <div className="flex flex-col gap-3.5">
        <FieldBox
          value={amt}
          onChange={(v) => setAmt(v.replace(/\D/g, "").slice(0, 6))}
          prefix="₹"
          h={56}
          inputMode="numeric"
          textClass="bric text-[22px]"
        />
        <div className="flex flex-wrap gap-1.5">
          {bal > 0 ? <PillChip label={`Full ${rupees(bal)}`} selected={amt === String(bal)} onClick={() => setAmt(String(bal))} /> : null}
          {[100, 200, 500]
            .filter((v) => v !== bal)
            .map((v) => (
              <PillChip key={v} label={rupees(v)} selected={amt === String(v)} onClick={() => setAmt(String(v))} />
            ))}
        </div>
        <div className="rounded-xl p-3.5" style={{ background: preview.bg }}>
          <span className="text-[14px] font-semibold" style={{ color: preview.fg }}>{preview.text}</span>
        </div>
        <div className="flex w-full gap-2">
          <PrimaryButton h={54} className="flex-1" disabled={got <= 0} onClick={() => pay("CASH")}>
            Got cash
          </PrimaryButton>
          <PrimaryButton h={54} className="flex-1" disabled={got <= 0} onClick={() => pay("UPI")}>
            Got UPI
          </PrimaryButton>
        </div>
      </div>
    </AppSheet>
  );
}

export function AddOldBaakiSheet({
  state, custId, onDismiss,
}: { state: LaundryState; custId: string; onDismiss: () => void }) {
  const c = Sel.customer(state, custId);
  const bal = Math.max(0, Sel.balance(state, custId));
  const [amt, setAmt] = useState("");
  const got = parseInt(amt, 10) || 0;

  return (
    <AppSheet
      title="Add old baaki"
      subtitle={`Money ${Sel.firstName(c.name)} owed you before you started using the app`}
      onDismiss={onDismiss}
    >
      <div className="flex flex-col gap-3.5">
        <FieldBox
          value={amt}
          onChange={(v) => setAmt(v.replace(/\D/g, "").slice(0, 6))}
          prefix="₹"
          h={56}
          inputMode="numeric"
          textClass="bric text-[22px]"
        />
        <div className="rounded-xl bg-bg p-3.5">
          <span className="text-[14px] font-semibold text-inksecondary">
            {amt === "" ? `Baaki now: ${rupees(bal)}` : `Baaki will become ${rupees(bal + got)}`}
          </span>
        </div>
        <PrimaryButton
          h={54}
          disabled={got <= 0}
          onClick={() => {
            Repo.addOldBaaki(custId, got);
            onDismiss();
          }}
        >
          Add old baaki
        </PrimaryButton>
      </div>
    </AppSheet>
  );
}
