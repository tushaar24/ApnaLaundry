import type { Metadata } from "next";
import { COMPANY, H2, LegalShell, P, UL } from "@/ui/legal";

export const metadata: Metadata = { title: "Privacy Policy — ApnaLaundry" };

export default function PrivacyPage() {
  return (
    <LegalShell title="Privacy Policy">
      <P>
        This Privacy Policy explains how {COMPANY.legalName} (&ldquo;we&rdquo;) collects, uses, and protects
        information when you use {COMPANY.name} (the &ldquo;Service&rdquo;). By using the Service, you agree to this policy.
      </P>

      <H2>1. Information we collect</H2>
      <UL>
        <li><strong>Account data:</strong> your mobile number, used to sign you in with a one-time password (OTP), and a device identifier.</li>
        <li><strong>Business data you enter:</strong> your shop details, services and prices, orders, bills, payments, and the customer records (name, phone, address, balances) you choose to store.</li>
        <li><strong>Usage & device data:</strong> app interactions, approximate device/browser information, and log data, used to operate and improve the Service.</li>
      </UL>

      <H2>2. How we use information</H2>
      <UL>
        <li>To provide, maintain, and sync the Service across your devices.</li>
        <li>To authenticate you (OTP) and keep your account secure.</li>
        <li>To understand usage and improve features and reliability.</li>
        <li>To communicate important notices about the Service.</li>
      </UL>

      <H2>3. Service providers we share with</H2>
      <P>We use trusted third parties only to run the Service. They process data on our behalf:</P>
      <UL>
        <li><strong>OTP / SMS provider</strong> — to deliver login codes to your mobile number.</li>
        <li><strong>Hosting & database</strong> — to store and sync your data securely.</li>
        <li><strong>Product analytics (CleverTap)</strong> — to understand feature usage and improve the Service. This may include an identifier and your interactions. [Confirm/adjust before publishing.]</li>
        <li><strong>Payment provider</strong> — if you make a payment, it is processed by our payment gateway; we do not store full card details.</li>
      </UL>
      <P>We do not sell your personal information.</P>

      <H2>4. The data of your own customers</H2>
      <P>
        Customer records you enter are stored so you can run your business (billing and khata). You are responsible for
        having any consent required to store this information, and for using it lawfully. We process it on your behalf.
      </P>

      <H2>5. Cookies & local storage</H2>
      <P>
        We use browser local storage and similar technologies to keep you signed in and to run core features. Analytics
        providers may set cookies to measure usage. You can control cookies through your browser settings, though some
        features may not work without them.
      </P>

      <H2>6. Data retention</H2>
      <P>
        We keep your information for as long as your account is active or as needed to provide the Service, and as
        required by law. You may request deletion of your account data as described below.
      </P>

      <H2>7. Security</H2>
      <P>
        We use reasonable technical and organisational measures to protect your data, including encrypted transport
        (HTTPS) and access controls. No method of transmission or storage is completely secure, so we cannot guarantee
        absolute security.
      </P>

      <H2>8. Your rights</H2>
      <P>
        Subject to applicable law, you may request access to, correction of, or deletion of your personal data, and you
        may withdraw consent where processing is based on it. To make a request, contact us at {COMPANY.email}.
      </P>

      <H2>9. Children</H2>
      <P>The Service is intended for business owners and is not directed at children under 18.</P>

      <H2>10. Changes to this policy</H2>
      <P>
        We may update this policy from time to time. We will post the updated version here and revise the
        &ldquo;Last updated&rdquo; date above.
      </P>

      <H2>11. Contact</H2>
      <P>
        For privacy questions or requests, email {COMPANY.email} or write to {COMPANY.address}.
      </P>
    </LegalShell>
  );
}
