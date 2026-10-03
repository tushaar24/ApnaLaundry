import type { Metadata } from "next";
import { COMPANY, H2, LegalShell, P } from "@/ui/legal";

export const metadata: Metadata = { title: "Shipping & Exchange — ApnaLaundry" };

export default function ShippingPage() {
  return (
    <LegalShell title="Shipping & Exchange Policy">
      <P>
        {COMPANY.name}, operated by {COMPANY.legalName}, is a digital software service. We do not sell or ship any
        physical goods, so no physical shipping is involved.
      </P>

      <H2>1. Digital delivery</H2>
      <P>
        Access to the Service is delivered electronically. Once you sign up (and, where applicable, complete payment for
        a paid plan), the relevant features are activated on your account immediately or within a short time. You use the
        Service over the internet via the website or app — nothing is shipped to a physical address.
      </P>

      <H2>2. No physical exchange</H2>
      <P>
        As there are no physical products, there is no shipping, delivery, return, or exchange of goods. Any questions
        about paid plans are handled under our{" "}
        <a href="/refund" className="font-semibold text-blue">Cancellation &amp; Refund Policy</a>.
      </P>

      <H2>3. Service availability</H2>
      <P>
        If you are unable to access the Service after signing up or paying, please contact us so we can resolve it
        promptly.
      </P>

      <H2>4. Contact</H2>
      <P>For any access or activation issue, email {COMPANY.email} or call {COMPANY.phone}.</P>
    </LegalShell>
  );
}
