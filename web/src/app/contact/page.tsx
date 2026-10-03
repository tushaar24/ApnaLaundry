import type { Metadata } from "next";
import { COMPANY, H2, LegalShell, P } from "@/ui/legal";

export const metadata: Metadata = { title: "Contact Us — ApnaLaundry" };

export default function ContactPage() {
  return (
    <LegalShell title="Contact Us">
      <P>
        We&rsquo;re happy to help with any question about {COMPANY.name}. Reach us through the details below and
        we&rsquo;ll get back to you as soon as we can.
      </P>

      <H2>Get in touch</H2>
      <div className="mt-4 overflow-hidden rounded-2xl border border-cardborder bg-card">
        <Row label="Business name" value={COMPANY.legalName} />
        <Row label="Email" value={COMPANY.email} href={`mailto:${COMPANY.email}`} />
        <Row label="Phone" value={COMPANY.phone} href={`tel:${COMPANY.phone.replace(/\s/g, "")}`} />
        <Row label="Address" value={COMPANY.address} />
        <Row label="Website" value={COMPANY.website} href={COMPANY.website} last />
      </div>

      <H2>Support hours</H2>
      <P>[e.g. Monday&ndash;Saturday, 10:00 AM &ndash; 7:00 PM IST. Update to your actual hours.]</P>

      <p className="mt-6 text-[13px] text-muted">
        For billing questions, see our{" "}
        <a href="/refund" className="font-semibold text-blue">Cancellation &amp; Refund Policy</a>; for how we handle
        your data, see our <a href="/privacy" className="font-semibold text-blue">Privacy Policy</a>.
      </p>
    </LegalShell>
  );
}

function Row({ label, value, href, last }: { label: string; value: string; href?: string; last?: boolean }) {
  return (
    <div className={`flex flex-col gap-0.5 px-4 py-3.5 sm:flex-row sm:items-center sm:gap-4 ${last ? "" : "border-b border-divider"}`}>
      <span className="w-32 shrink-0 text-[13px] font-semibold text-muted">{label}</span>
      {href ? (
        <a href={href} className="break-words text-[15px] font-semibold text-blue">{value}</a>
      ) : (
        <span className="break-words text-[15px] font-semibold text-ink">{value}</span>
      )}
    </div>
  );
}
