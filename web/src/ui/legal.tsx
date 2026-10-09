import Link from "next/link";
import type { ReactNode } from "react";
import { IcLaundry } from "./icons";

/**
 * Shared chrome for the public legal / policy pages (Terms, Privacy, Refund,
 * Shipping, Contact). Static server components — no auth gate, so they are
 * reachable without logging in (payment gateways require public policy URLs).
 *
 * COMPANY holds the business-specific details. Fill in every [PLACEHOLDER]
 * and have the final text reviewed by legal counsel before relying on it.
 */
export const COMPANY = {
  name: "MyLaundry",
  legalName: "[Your registered business / proprietor name]",
  email: "[support@yourdomain.com]",
  phone: "[+91 XXXXXXXXXX]",
  address: "[Registered business address, City, State, PIN]",
  jurisdiction: "[City], [State], India",
  website: "https://mylaundry.work",
  lastUpdated: "3 October 2026",
};

export const LEGAL_LINKS: { href: string; label: string }[] = [
  { href: "/terms", label: "Terms & Conditions" },
  { href: "/privacy", label: "Privacy Policy" },
  { href: "/refund", label: "Cancellation & Refund" },
  { href: "/shipping", label: "Shipping & Exchange" },
  { href: "/contact", label: "Contact Us" },
];

export function LegalFooter() {
  return (
    <footer className="mt-10 border-t border-divider pt-6 text-center">
      <nav className="flex flex-wrap items-center justify-center gap-x-5 gap-y-2">
        {LEGAL_LINKS.map((l) => (
          <Link key={l.href} href={l.href} className="text-[13px] font-semibold text-muted hover:text-ink">
            {l.label}
          </Link>
        ))}
      </nav>
      <p className="mt-4 text-[12px] text-faint">
        © {COMPANY.lastUpdated.split(" ").pop()} {COMPANY.name}. All rights reserved.
      </p>
    </footer>
  );
}

export function LegalShell({ title, children }: { title: string; children: ReactNode }) {
  return (
    <div className="min-h-dvh bg-bg">
      <header className="border-b border-cardborder bg-card">
        <div className="mx-auto flex max-w-[820px] items-center gap-3 px-5 py-4">
          <Link href="/" className="flex items-center gap-2.5">
            <span className="flex size-9 items-center justify-center rounded-xl bg-blue text-ondark">
              <IcLaundry size={20} />
            </span>
            <span className="bric text-[18px]">{COMPANY.name}</span>
          </Link>
        </div>
      </header>

      <main className="mx-auto max-w-[820px] px-5 py-8">
        <h1 className="bric text-[30px]">{title}</h1>
        <p className="mt-1 text-[13px] text-muted">Last updated: {COMPANY.lastUpdated}</p>
        <article className="legal-prose mt-6">{children}</article>
        <LegalFooter />
      </main>
    </div>
  );
}

/** Section heading + paragraph helpers so the pages read consistently. */
export function H2({ children }: { children: ReactNode }) {
  return <h2 className="bric mt-8 text-[20px]">{children}</h2>;
}

export function P({ children }: { children: ReactNode }) {
  return <p className="mt-3 text-[15px] leading-relaxed text-inksecondary">{children}</p>;
}

export function UL({ children }: { children: ReactNode }) {
  return <ul className="mt-3 list-disc space-y-1.5 pl-5 text-[15px] leading-relaxed text-inksecondary">{children}</ul>;
}
