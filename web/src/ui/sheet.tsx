"use client";

import { useEffect, type ReactNode } from "react";
import { cls } from "./basics";

/**
 * AppBottomSheet port: a modal bottom sheet on mobile (drag-handle, slide-up)
 * that becomes a centred dialog on desktop. Bricolage title + optional
 * subtitle, content below — same anatomy as the app's sheets.
 */
export function AppSheet({
  title, subtitle, leading, noSidebar, onDismiss, children,
}: {
  title: string;
  subtitle?: string;
  /** Optional badge left of the title (e.g. a warning icon). */
  leading?: ReactNode;
  /** Centre on the full viewport — for screens without the desktop sidebar (paywall). */
  noSidebar?: boolean;
  onDismiss: () => void;
  children: ReactNode;
}) {
  useEffect(() => {
    const prev = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape") onDismiss();
    };
    window.addEventListener("keydown", onKey);
    return () => {
      document.body.style.overflow = prev;
      window.removeEventListener("keydown", onKey);
    };
  }, [onDismiss]);

  return (
    <div className={cls("fixed inset-0 z-50 flex items-end justify-center lg:items-center", noSidebar ? "" : "lg:pl-[240px]")}>
      <button
        type="button"
        aria-label="Close"
        onClick={onDismiss}
        className="animate-scrim absolute inset-0 bg-[rgba(22,25,33,0.4)]"
      />
      <div
        role="dialog"
        aria-modal="true"
        className="animate-sheet relative flex max-h-[92dvh] w-full flex-col rounded-t-3xl bg-bg lg:max-h-[86dvh] lg:max-w-[560px] lg:rounded-3xl"
      >
        <div className="overflow-y-auto px-5 pb-7 pt-4 lg:px-6">
          <div className="mx-auto mb-3.5 h-1 w-10 rounded-full bg-cardborder lg:hidden" />
          {leading ? (
            <div className="flex items-center gap-4">
              {leading}
              <h2 className="bric min-w-0 flex-1 text-[24px]">{title}</h2>
            </div>
          ) : (
            <h2 className="bric text-[24px]">{title}</h2>
          )}
          {subtitle ? <p className="mt-1 text-[14px] text-muted">{subtitle}</p> : null}
          <div className="mt-4">{children}</div>
        </div>
      </div>
    </div>
  );
}
