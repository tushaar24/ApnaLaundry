import type { Metadata } from "next";
import { COMPANY, H2, LegalShell, P, UL } from "@/ui/legal";

export const metadata: Metadata = { title: "Terms & Conditions — MyLaundry" };

export default function TermsPage() {
  return (
    <LegalShell title="Terms & Conditions">
      <P>
        These Terms & Conditions (&ldquo;Terms&rdquo;) govern your access to and use of {COMPANY.name}
        &nbsp;(the &ldquo;Service&rdquo;), a laundry-management application operated by {COMPANY.legalName}
        &nbsp;(&ldquo;we&rdquo;, &ldquo;us&rdquo;, &ldquo;our&rdquo;). By creating an account or using the Service, you
        agree to these Terms. If you do not agree, please do not use the Service.
      </P>

      <H2>1. The Service</H2>
      <P>
        {COMPANY.name} helps laundry businesses take orders, generate bills, track pickups and deliveries,
        and maintain customer credit (&ldquo;khata&rdquo;) records. Features may change, be added, or be removed
        over time. We may also set reasonable usage limits.
      </P>

      <H2>2. Eligibility & accounts</H2>
      <UL>
        <li>You must be at least 18 years old and able to form a binding contract.</li>
        <li>You sign in with your mobile number and a one-time password (OTP). You are responsible for keeping access to that number secure.</li>
        <li>You are responsible for all activity under your account and for the accuracy of the data you enter.</li>
      </UL>

      <H2>3. Your responsibilities</H2>
      <UL>
        <li>Use the Service only for lawful purposes and in compliance with applicable laws.</li>
        <li>Do not misuse, disrupt, reverse-engineer, or attempt to gain unauthorised access to the Service.</li>
        <li>You are responsible for the data about your own customers that you store, and for having any consent required to store it.</li>
      </UL>

      <H2>4. Fees & subscriptions</H2>
      <P>
        Some features may be offered free and others on a paid or subscription basis. Applicable charges, billing
        cycles, and taxes (as per Indian law) will be shown before you pay. Payments are processed by our third-party
        payment provider. Refunds, where applicable, are governed by our{" "}
        <a href="/refund" className="font-semibold text-blue">Cancellation &amp; Refund Policy</a>.
        <br />
        <span className="text-[13px] text-muted">[Fill in your exact pricing, billing cycle, and tax terms.]</span>
      </P>

      <H2>5. Intellectual property</H2>
      <P>
        The Service, including its software, design, and content, is owned by {COMPANY.legalName} and protected by
        applicable laws. We grant you a limited, non-exclusive, non-transferable right to use the Service. The business
        and customer data you enter remains yours.
      </P>

      <H2>6. Disclaimers</H2>
      <P>
        The Service is provided &ldquo;as is&rdquo; and &ldquo;as available&rdquo;. While we work to keep it accurate and
        available, we do not warrant that it will be uninterrupted, error-free, or fit for a particular purpose. You are
        responsible for verifying critical figures (bills, balances, totals) before relying on them.
      </P>

      <H2>7. Limitation of liability</H2>
      <P>
        To the maximum extent permitted by law, {COMPANY.legalName} will not be liable for any indirect, incidental, or
        consequential damages, or for loss of profits, data, or goodwill arising from your use of the Service.
      </P>

      <H2>8. Suspension & termination</H2>
      <P>
        You may stop using the Service at any time. We may suspend or terminate access if these Terms are violated or if
        required by law. On termination, your right to use the Service ends.
      </P>

      <H2>9. Changes to these Terms</H2>
      <P>
        We may update these Terms from time to time. Material changes will be notified through the Service or by other
        reasonable means. Continued use after changes take effect means you accept the revised Terms.
      </P>

      <H2>10. Governing law & jurisdiction</H2>
      <P>
        These Terms are governed by the laws of India. Any disputes are subject to the exclusive jurisdiction of the
        courts at {COMPANY.jurisdiction}.
      </P>

      <H2>11. Contact</H2>
      <P>
        Questions about these Terms? Email us at {COMPANY.email} or see our{" "}
        <a href="/contact" className="font-semibold text-blue">Contact</a> page.
      </P>
    </LegalShell>
  );
}
