"use client";

import { useState } from "react";
import * as AppDate from "@/core/appdate";
import { isNameOk } from "@/domain/billDetails";
import { cls, FieldBox, PrimaryButton } from "../basics";
import { IcBack, IcCheck } from "../icons";

/**
 * Onboarding building blocks (handoff §3–§5): the step bar on steps 1–3, the
 * intro screen, and step 1 (laundry name with a live "on your bill" card).
 */

export const STEP_LABELS = ["Laundry name", "Services", "Your bill"] as const;

/** "Step N of 3" with a back arrow, then three labelled segments. */
export function StepBar({ step, onBack }: { step: 1 | 2 | 3; onBack: () => void }) {
  return (
    <div className="flex flex-col gap-2.5">
      <div className="-ml-2.5 flex items-center gap-1">
        <button
          type="button"
          onClick={onBack}
          aria-label="Back"
          className="flex size-11 items-center justify-center rounded-full text-ink hover:bg-black/[0.04]"
        >
          <IcBack size={24} />
        </button>
        <span className="text-[14px] font-semibold text-muted">Step {step} of 3</span>
      </div>
      <ol className="grid grid-cols-3 gap-2" aria-label="Setup steps">
        {STEP_LABELS.map((label, i) => {
          const n = i + 1;
          const done = n < step;
          const current = n === step;
          return (
            <li key={label} className="flex flex-col gap-1.5" aria-current={current ? "step" : undefined}>
              <span className={cls("h-1.5 rounded-full", done || current ? "bg-blue" : "bg-[#DAD5CB]")} />
              <span
                className={cls(
                  "flex items-center gap-1 text-[12px]",
                  done && "font-semibold text-greentext",
                  current && "font-bold text-blue",
                  !done && !current && "font-medium text-faint",
                )}
              >
                {done ? <IcCheck size={13} strokeWidth={3} /> : null}
                {label}
              </span>
            </li>
          );
        })}
      </ol>
    </div>
  );
}

/** Pinned bottom bar with the step's primary button. */
export function BottomBar({ children, note }: { children: React.ReactNode; note?: string }) {
  return (
    <div className="sticky bottom-0 w-full border-t border-divider bg-card">
      {note ? <p className="px-4 pt-3 text-[13px] text-muted">{note}</p> : null}
      <div className="p-4">{children}</div>
    </div>
  );
}

const INTRO_STEPS = [
  { title: "Laundry name", body: "Printed at the top of every bill" },
  { title: "Services & rates", body: "Common prices are already filled in — just check them" },
  { title: "Your bill", body: "Pick a bill design. Add phone, email, address and GSTIN if you want" },
];

export function IntroStep({ onStart }: { onStart: () => void }) {
  return (
    <div className="flex min-h-dvh flex-col">
      <div className="flex flex-1 flex-col gap-4 px-4 py-6">
        <span className="inline-flex w-fit items-center gap-1.5 rounded-full bg-[#E3F4E8] px-3 py-1.5 text-[13px] font-bold text-greentext">
          <IcCheck size={15} strokeWidth={3} /> Number verified
        </span>
        <h1 className="bric text-[30px] leading-tight">Let&apos;s set up your shop</h1>
        <p className="-mt-1 text-[15px] text-muted">3 quick steps. Then you can send your first bill on WhatsApp.</p>
        <ol className="mt-2 flex flex-col">
          {INTRO_STEPS.map((s, i) => (
            <li key={s.title} className="flex gap-3.5">
              <div className="flex flex-col items-center">
                <span
                  className={cls(
                    "flex size-9 shrink-0 items-center justify-center rounded-full text-[15px] font-bold",
                    i === 0 ? "bg-blue text-ondark" : "border-2 border-blue bg-card text-blue",
                  )}
                >
                  {i + 1}
                </span>
                {i < INTRO_STEPS.length - 1 ? <span className="my-1 w-0.5 flex-1 bg-blueborder" /> : null}
              </div>
              <div className={cls("flex flex-col pt-1.5", i < INTRO_STEPS.length - 1 && "pb-6")}>
                <span className="text-[17px] font-bold">{s.title}</span>
                <span className="text-[14px] text-muted">{s.body}</span>
              </div>
            </li>
          ))}
        </ol>
      </div>
      <BottomBar>
        <PrimaryButton onClick={onStart}>Start with step 1</PrimaryButton>
      </BottomBar>
    </div>
  );
}

export function NameStep({
  initial, onBack, onNext,
}: {
  initial: string;
  onBack: () => void;
  onNext: (name: string) => void;
}) {
  const [name, setName] = useState(initial);
  const ok = isNameOk(name);
  const next = () => {
    if (ok) onNext(name.trim());
  };
  return (
    <div className="flex min-h-dvh flex-col">
      <form className="flex flex-1 flex-col gap-4 px-4 py-4" onSubmit={(e) => { e.preventDefault(); next(); }}>
        <StepBar step={1} onBack={onBack} />
        <h1 className="bric mt-1 text-[28px]">What is your laundry called?</h1>
        <p className="-mt-2 text-[14px] text-muted">This name goes on every bill you send to customers.</p>
        <label className="sr-only" htmlFor="shop-name">Laundry name</label>
        <FieldBox
          value={name}
          onChange={(v) => setName(v.slice(0, 40))}
          placeholder="e.g. Sharma Laundry"
          h={56}
          borderColor="var(--color-blue)"
          borderWidth={2}
          autoFocus
          inputProps={{ id: "shop-name", autoCapitalize: "words", enterKeyHint: "next", maxLength: 40 }}
        />
        <MiniBill name={name.trim()} />
      </form>
      <BottomBar>
        <PrimaryButton onClick={next} disabled={!ok}>Next: Services</PrimaryButton>
      </BottomBar>
    </div>
  );
}

/** The small "On your bill" card under the name input. */
function MiniBill({ name }: { name: string }) {
  return (
    <div className="mt-1 flex flex-col gap-2">
      <span className="text-[12px] font-bold uppercase tracking-wide text-faint">On your bill</span>
      <div className="flex flex-col gap-3 rounded-2xl border border-cardborder bg-card p-4" aria-live="polite">
        <div className={cls("bric truncate text-[22px]", !name && "text-placeholder")}>{name || "Your laundry name"}</div>
        <div className="-mt-2 text-[13px] text-muted">Bill #1001 · {AppDate.plain(AppDate.today())}</div>
        {[78, 62, 70].map((w) => (
          <div key={w} className="flex items-center justify-between gap-6" aria-hidden="true">
            <span className="h-2.5 rounded-full bg-neutralfill" style={{ width: `${w}%` }} />
            <span className="h-2.5 w-10 rounded-full bg-neutralfill" />
          </div>
        ))}
      </div>
    </div>
  );
}
