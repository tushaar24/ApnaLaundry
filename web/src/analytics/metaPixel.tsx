"use client";

import Script from "next/script";
import { usePathname } from "next/navigation";
import { useEffect, useRef } from "react";

/**
 * Meta (Facebook) Pixel for mylaundry.work — custom events:
 *   login_success           browser only
 *   subscription_initiated  browser only
 *   subscription_activated  ALSO sent server-side via the Conversions API when
 *                           Razorpay confirms the payment (backend
 *                           services/laundry-meta.js); both use eventID
 *                           subact_<razorpaySubscriptionId> so Meta counts it once.
 */

export const META_PIXEL_ID = "1082693971198391";

type Fbq = (...args: unknown[]) => void;

function fbq(...args: unknown[]) {
  if (typeof window === "undefined") return;
  const f = (window as unknown as { fbq?: Fbq }).fbq;
  if (f) f(...args);
}

export const MetaPixel = {
  loginSuccess(isNewUser: boolean) {
    fbq("trackCustom", "login_success", { is_new_user: isNewUser });
  },
  /** Checkout opened for a subscription (value = what approval charges now, in ₹). */
  subscriptionInitiated(subscriptionId: string, plan: string, valueRupees: number) {
    fbq("trackCustom", "subscription_initiated",
      { plan, subscription_id: subscriptionId, value: valueRupees, currency: "INR" });
  },
  /** Mandate approved in Razorpay Checkout. */
  subscriptionActivated(subscriptionId: string, plan: string, valueRupees: number) {
    fbq("trackCustom", "subscription_activated",
      { plan, value: valueRupees, currency: "INR" },
      { eventID: `subact_${subscriptionId}` });
  },
};

/** Loads the pixel once and sends a PageView on every client-side route change. */
export function MetaPixelScript() {
  const pathname = usePathname();
  const first = useRef(true);
  useEffect(() => {
    // The init snippet already sends the first PageView.
    if (first.current) { first.current = false; return; }
    fbq("track", "PageView");
  }, [pathname]);

  return (
    <>
      <Script id="meta-pixel" strategy="afterInteractive">{`
!function(f,b,e,v,n,t,s)
{if(f.fbq)return;n=f.fbq=function(){n.callMethod?
n.callMethod.apply(n,arguments):n.queue.push(arguments)};
if(!f._fbq)f._fbq=n;n.push=n;n.loaded=!0;n.version='2.0';
n.queue=[];t=b.createElement(e);t.async=!0;
t.src=v;s=b.getElementsByTagName(e)[0];
s.parentNode.insertBefore(t,s)}(window, document,'script',
'https://connect.facebook.net/en_US/fbevents.js');
fbq('init', '${META_PIXEL_ID}');
fbq('track', 'PageView');
`}</Script>
      <noscript>
        {/* eslint-disable-next-line @next/next/no-img-element */}
        <img height="1" width="1" style={{ display: "none" }} alt=""
          src={`https://www.facebook.com/tr?id=${META_PIXEL_ID}&ev=PageView&noscript=1`} />
      </noscript>
    </>
  );
}
