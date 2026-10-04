import type { Metadata, Viewport } from "next";
import { Bricolage_Grotesque, Figtree } from "next/font/google";
import "./globals.css";
import Script from "next/script";
import { MetaPixelScript } from "@/analytics/metaPixel";

const figtree = Figtree({
  subsets: ["latin"],
  variable: "--font-figtree",
  display: "swap",
});

const bricolage = Bricolage_Grotesque({
  subsets: ["latin"],
  variable: "--font-bricolage",
  display: "swap",
});

export const metadata: Metadata = {
  title: "ApnaLaundry",
  description: "Orders and bills, in two taps.",
  applicationName: "ApnaLaundry",
};

export const viewport: Viewport = {
  width: "device-width",
  initialScale: 1,
  themeColor: "#f5f3ee",
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en" className={`${figtree.variable} ${bricolage.variable}`}>
      <body>
        {/* Ad clicks land with ?fbclid=; store it as Meta's _fbc cookie before any
            client redirect (e.g. / -> /login) can drop the query string. The
            backend sends it to the Conversions API for event matching. */}
        <Script id="meta-fbclid" strategy="beforeInteractive">{`
try {
  var id = new URLSearchParams(location.search).get('fbclid');
  var m = document.cookie.match(/(?:^|; )_fbc=([^;]*)/);
  if (id && !(m && m[1].slice(-id.length - 1) === '.' + id)) {
    document.cookie = '_fbc=fb.1.' + Date.now() + '.' + id + '; path=/; max-age=7776000; SameSite=Lax';
  }
} catch (e) {}
`}</Script>
        {children}
        <MetaPixelScript />
      </body>
    </html>
  );
}
