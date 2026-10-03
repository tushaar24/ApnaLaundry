"use client";

/**
 * Base design-system components — port of ui/components/Foundation.kt,
 * Chrome.kt and Sheet.kt pieces (buttons, chips, fields, cards, toggle,
 * stepper, labels).
 */

import { type InputHTMLAttributes, type ReactNode, useRef } from "react";
import { IcBack } from "./icons";

export function cls(...parts: (string | false | null | undefined)[]): string {
  return parts.filter(Boolean).join(" ");
}

// ---------- labels ----------

export function SectionLabel({ text, className, tone }: { text: string; className?: string; tone?: string }) {
  return <div className={cls("section-label", className)} style={tone ? { color: tone } : undefined}>{text}</div>;
}

// ---------- buttons ----------

export function PrimaryButton({
  children, onClick, disabled, className, h = 56, bg,
}: {
  children: ReactNode; onClick?: () => void; disabled?: boolean; className?: string; h?: number; bg?: "blue" | "orange";
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      disabled={disabled}
      style={{ height: h }}
      className={cls(
        "w-full rounded-[14px] px-4 text-[17px] font-bold text-ondark transition-colors",
        bg === "orange" ? "bg-orange" : disabled ? "bg-bluedisabled" : "bg-blue hover:bg-blue/90 active:bg-blue/80",
        className,
      )}
    >
      {children}
    </button>
  );
}

export function OutlineButton({
  children, onClick, className, h = 56, border = "var(--color-blue)", fg = "var(--color-blue)",
}: {
  children: ReactNode; onClick?: () => void; className?: string; h?: number; border?: string; fg?: string;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      style={{ height: h, borderColor: border, color: fg }}
      className={cls(
        "rounded-[14px] border-[1.5px] bg-transparent px-4 text-[16px] font-bold transition-colors hover:bg-black/[0.03]",
        className,
      )}
    >
      {children}
    </button>
  );
}

// ---------- pill chip ----------

export function PillChip({
  label, selected, onClick, className,
}: { label: string; selected: boolean; onClick: () => void; className?: string }) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={cls(
        "h-11 whitespace-nowrap rounded-full border-[1.5px] px-3.5 text-[14px] font-bold transition-colors",
        selected ? "border-ink bg-ink text-ondark" : "border-cardborder bg-card text-ink hover:border-ink/40",
        className,
      )}
    >
      {label}
    </button>
  );
}

// ---------- segmented control ----------

export function Segmented({
  options, onSelect, className,
}: { options: [string, boolean][]; onSelect: (i: number) => void; className?: string }) {
  return (
    <div className={cls("flex w-full gap-1 rounded-xl bg-segtrack p-1", className)}>
      {options.map(([label, sel], i) => (
        <button
          key={label}
          type="button"
          onClick={() => onSelect(i)}
          className={cls(
            "h-12 flex-1 rounded-[9px] text-[15px] font-bold transition-colors",
            sel ? "bg-card text-ink" : "text-inksecondary",
          )}
        >
          {label}
        </button>
      ))}
    </div>
  );
}

// ---------- bordered text field ----------

export function FieldBox({
  value, onChange, placeholder, prefix, suffix, h = 52, borderColor, borderWidth = 1.5,
  inputMode, textClass = "text-[17px] font-semibold", center, leading, className, type = "text",
  autoFocus, inputProps,
}: {
  value: string;
  onChange: (v: string) => void;
  placeholder?: string;
  prefix?: string;
  suffix?: string;
  h?: number;
  borderColor?: string;
  borderWidth?: number;
  inputMode?: "numeric" | "decimal" | "tel" | "text";
  textClass?: string;
  center?: boolean;
  leading?: ReactNode;
  className?: string;
  type?: string;
  autoFocus?: boolean;
  inputProps?: InputHTMLAttributes<HTMLInputElement>;
}) {
  return (
    <div
      style={{ height: h, borderColor: borderColor ?? "var(--color-fieldborder)", borderWidth }}
      className={cls("flex items-center gap-2 rounded-xl border bg-card px-3.5", className)}
    >
      {leading}
      {prefix ? <span className="text-[17px] font-semibold text-muted">{prefix}</span> : null}
      <input
        type={type}
        value={value}
        onChange={(e) => onChange(e.target.value)}
        placeholder={placeholder}
        inputMode={inputMode}
        autoFocus={autoFocus}
        className={cls("min-w-0 flex-1 bg-transparent text-ink placeholder:text-muted", textClass, center && "text-center")}
        {...inputProps}
      />
      {suffix ? <span className="shrink-0 text-[15px] font-semibold text-muted">{suffix}</span> : null}
    </div>
  );
}

// ---------- cards ----------

export function AppCard({
  children, className, borderColor, bg, onClick,
}: { children: ReactNode; className?: string; borderColor?: string; bg?: string; onClick?: () => void }) {
  const style = {
    ...(borderColor ? { borderColor } : {}),
    ...(bg ? { background: bg } : {}),
  };
  const base = cls("rounded-2xl border border-cardborder bg-card", onClick && "cursor-pointer text-left w-full", className);
  if (onClick) {
    return (
      <button type="button" onClick={onClick} className={base} style={style}>
        {children}
      </button>
    );
  }
  return (
    <div className={base} style={style}>
      {children}
    </div>
  );
}

// ---------- top bar ----------

export function TopBar({
  title, onBack, trailing,
}: { title: string; onBack?: () => void; trailing?: ReactNode }) {
  return (
    <div className="flex items-center gap-1 px-3 pb-1.5 pt-3">
      {onBack ? (
        <button type="button" onClick={onBack} aria-label="Back" className="flex size-11 items-center justify-center text-ink">
          <IcBack size={24} />
        </button>
      ) : null}
      <h1 className="bric flex-1 text-[22px]">{title}</h1>
      {trailing}
    </div>
  );
}

// ---------- toggle ----------

export function Toggle({ on, onToggle, onColor }: { on: boolean; onToggle: () => void; onColor?: string }) {
  return (
    <button
      type="button"
      role="switch"
      aria-checked={on}
      onClick={onToggle}
      style={{ background: on ? (onColor ?? "var(--color-blue)") : "var(--color-toggleoff)" }}
      className="flex h-7 w-[46px] shrink-0 items-center rounded-full p-0.5 transition-colors"
    >
      <span
        className="size-6 rounded-full bg-white transition-transform"
        style={{ transform: on ? "translateX(18px)" : "translateX(0)" }}
      />
    </button>
  );
}

// ---------- stepper ----------

export function Stepper({ qty, onDec, onInc }: { qty: number; onDec: () => void; onInc: () => void }) {
  return (
    <div className="flex items-center gap-2.5">
      <button
        type="button"
        aria-label="Decrease"
        onClick={qty > 0 ? onDec : undefined}
        className={cls(
          "flex size-[38px] items-center justify-center rounded-[10px] bg-neutralfill text-inksecondary",
          qty === 0 && "cursor-default opacity-60",
        )}
      >
        <span className="text-lg font-bold leading-none">−</span>
      </button>
      <span className="min-w-4 px-0.5 text-center text-[17px] font-bold">{qty}</span>
      <button
        type="button"
        aria-label="Increase"
        onClick={onInc}
        className={cls(
          "flex size-[38px] items-center justify-center rounded-[10px]",
          qty > 0 ? "bg-blue text-ondark" : "bg-bluelight text-blue",
        )}
      >
        <span className="text-lg font-bold leading-none">+</span>
      </button>
    </div>
  );
}

// ---------- avatar / tags ----------

export function Avatar({
  text, size = 38, bg = "var(--color-blue)", fg = "var(--color-ondark)",
}: { text: string; size?: number; bg?: string; fg?: string }) {
  return (
    <div
      style={{ width: size, height: size, background: bg, color: fg, fontSize: size < 38 ? 13 : 14 }}
      className="flex shrink-0 items-center justify-center rounded-full font-bold"
    >
      {text}
    </div>
  );
}

export function ExpressTag() {
  return (
    <span className="rounded-full bg-orangelight px-[7px] py-[3px] text-[11px] font-bold tracking-[0.04em] text-orangetext">
      EXPRESS
    </span>
  );
}

export function StatusPill({ label, bg, fg }: { label: string; bg: string; fg: string }) {
  return (
    <span style={{ background: bg, color: fg }} className="shrink-0 rounded-full px-2.5 py-1 text-[12px] font-bold">
      {label}
    </span>
  );
}

// ---------- divider ----------

export function Divider({ className }: { className?: string }) {
  return <div className={cls("h-px w-full bg-divider", className)} />;
}

// ---------- native date / time pickers behind styled boxes ----------

export function DateTimeBox({
  icon, text, isSet, type, value, min, onPick, className, iconTint,
}: {
  icon: ReactNode;
  text: string;
  isSet: boolean;
  type: "date" | "time";
  value: string; // iso date or HH:mm
  min?: string;
  onPick: (v: string) => void;
  className?: string;
  iconTint?: string;
}) {
  const ref = useRef<HTMLInputElement>(null);
  return (
    <button
      type="button"
      onClick={() => {
        const el = ref.current;
        if (!el) return;
        if ("showPicker" in el) {
          try { el.showPicker(); return; } catch { /* fall through */ }
        }
        el.focus();
        el.click();
      }}
      className={cls(
        "relative flex h-[52px] items-center gap-2 rounded-xl bg-card px-3",
        isSet ? "border-[1.5px] border-fieldborder" : "dash-border",
        className,
      )}
    >
      <span style={{ color: iconTint }} className="shrink-0">{icon}</span>
      <span className={cls("truncate text-[14px] font-bold", isSet ? "text-ink" : "text-muted")}>{text}</span>
      <input
        ref={ref}
        type={type}
        tabIndex={-1}
        value={value}
        min={min}
        onChange={(e) => {
          const v = e.target.value;
          if (type === "date") {
            if (v) onPick(min && v < min ? min : v);
          } else {
            onPick(v);
          }
        }}
        className="absolute inset-0 size-full opacity-0"
        style={{ pointerEvents: "none" }}
      />
    </button>
  );
}
