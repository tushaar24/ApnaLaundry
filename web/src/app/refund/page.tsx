import type { Metadata } from "next";
import { COMPANY, H2, LegalShell, P, UL } from "@/ui/legal";

export const metadata: Metadata = { title: "Cancellation & Refund — MyLaundry" };

export default function RefundPage() {
  return (
    <LegalShell title="Cancellation & Refund Policy">
      <P>
        This policy explains how cancellations and refunds work for paid plans or purchases on {COMPANY.name},
        operated by {COMPANY.legalName}. Please read it along with our{" "}
        <a href="/terms" className="font-semibold text-blue">Terms &amp; Conditions</a>.
      </P>

      <H2>1. Subscriptions & cancellation</H2>
      <UL>
        <li>You can cancel a paid plan at any time from your account or by contacting us at {COMPANY.email}.</li>
        <li>On cancellation, your plan remains active until the end of the current billing period; it will not renew after that.</li>
        <li>Cancelling stops future charges but does not, by itself, trigger a refund for the current period except as set out below.</li>
      </UL>

      <H2>2. Refunds</H2>
      <P>
        <span className="text-[13px] text-muted">
          [Set your actual refund terms here — e.g. a trial period, a money-back window, or a no-refund policy for
          digital subscriptions. The text below is a reasonable default; adjust it to match your business.]
        </span>
      </P>
      <UL>
        <li>If you were charged in error or experienced a failed/duplicate payment, contact us within [7] days and we will investigate and refund any amount wrongly charged.</li>
        <li>Refund requests for other reasons will be assessed on a case-by-case basis at our discretion.</li>
        <li>Fees already consumed for a period that has been used may be non-refundable.</li>
      </UL>

      <H2>3. How to request a refund</H2>
      <P>
        Email {COMPANY.email} with your registered mobile number, the payment date, and the reason. We may ask for the
        payment reference to locate the transaction.
      </P>

      <H2>4. Refund timeline & method</H2>
      <P>
        Approved refunds are made to the original payment method through our payment provider. Once approved, refunds are
        typically processed within [5&ndash;7] business days; the time for the amount to reflect in your account depends
        on your bank or card issuer.
      </P>

      <H2>5. Contact</H2>
      <P>For any cancellation or refund question, reach us at {COMPANY.email} or {COMPANY.phone}.</P>
    </LegalShell>
  );
}
