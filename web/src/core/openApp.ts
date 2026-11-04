/**
 * A tiny HTML page that opens the Android app on one screen — mylaundry://<screen>
 * (push/DeepLinks.kt, app 1.0.8+) — for https links in WhatsApp buttons and SMS,
 * which can't carry custom schemes. Used by /open/<screen> and /pay/<code>.
 *
 * Android: an intent:// link — Chrome opens the app if it's installed, else the
 * Play Store (also for builds older than 1.0.8, which can't take the link, so the
 * owner lands on Update). It fires on load, with a button for in-app browsers
 * that need a tap. Other devices get the Play Store and website links.
 *
 * Language links (app 1.0.16+): a `lang` (en hi mr gu bn ta te kn ml pa or) opens
 * the app in that language — mylaundry://<screen>?lang=hi — and the Play Store
 * link carries referrer lang=hi, which a fresh install reads on its first open
 * (core/InstallLanguage.kt). Older apps ignore it.
 *
 * Ad links (app 1.0.18+): a Meta ad whose destination is this page can carry Meta's
 * dynamic URL parameters — ?ad_id={{ad.id}}&ad_name={{ad.name}}&adset_id={{adset.id}}
 * &adset_name={{adset.name}}&campaign_id={{campaign.id}}&campaign_name={{campaign.name}}
 * &placement={{placement}}&site_source_name={{site_source_name}} — which go into the
 * Play Store link's referrer as plain pairs (utm_source=meta&utm_medium=link&ad_id=…).
 * A fresh install reads them once and reports them to the server
 * (data/attribution/InstallAttribution.kt → laundry_install_attributions + CleverTap).
 *
 * Every hit is logged as one JSON line (`open_link`) so link taps can be counted
 * from the container logs.
 */

export const SCREENS = [
  "new-order", "home", "customers", "earnings", "subscription", "assistant",
  // app 1.0.18: every screen; <id> screens take a second path segment (older apps open Home for these)
  "customer", "combined-bill", "order", "bill", "rates", "services", "add-service", "settings", "bill-design", "bill-details", "add-shop", "demo",
];
/** Screens that take an id: /open/order/1012 → mylaundry://order/1012. */
export const WITH_ID = ["customer", "combined-bill", "order", "bill"];
/** Query keys passed through to the app (?plan=annual on subscription, ?filter= on customers, ?cust= on new-order). */
export const QUERY_KEYS = ["plan", "filter", "cust", "period"];
/** The app's languages (core/I18n.kt AppLanguage). */
export const LANGS = ["en", "hi", "mr", "gu", "bn", "ta", "te", "kn", "ml", "pa", "or"];
/** Ad keys passed into the Play Store referrer (Meta dynamic URL parameters; see the header). */
export const AD_KEYS = ["ad_id", "ad_name", "adset_id", "adset_name", "campaign_id", "campaign_name", "placement", "site_source_name"];
const PACKAGE = "com.dailyworks.apnalaundry";
const PLAY_URL = `https://play.google.com/store/apps/details?id=${PACKAGE}`;

export function openAppResponse(userAgent: string, screen: string, via: string, code = "", lang = "", id = "", query: Record<string, string> = {}, ad: Record<string, string> = {}): Response {
  const base = SCREENS.includes(screen) ? screen : "home";
  const safeId = WITH_ID.includes(base) && /^[a-z0-9_-]{1,64}$/i.test(id) ? id : "";
  const qs = Object.entries(query).filter(([k, v]) => QUERY_KEYS.includes(k) && /^[a-z0-9_-]{1,64}$/i.test(v)).map(([k, v]) => `${k}=${v}`);
  const target = (safeId ? `${base}/${safeId}` : base);
  const language = LANGS.includes(lang) ? lang : "";
  const android = /android/i.test(userAgent);
  // Ad parameters: Meta fills {{ad.id}} etc. in; an unfilled one arrives literally as "{{ad.id}}" and is dropped.
  const adPairs = Object.entries(ad)
    .filter(([k, v]) => AD_KEYS.includes(k) && v && v.length <= 200 && !/[{}]/.test(v))
    .map(([k, v]) => `${k}=${encodeURIComponent(v)}`);
  console.log(JSON.stringify({ evt: "open_link", via, target, lang: language, code: code.slice(0, 64), android, ad: adPairs.length ? Object.fromEntries(adPairs.map((p) => p.split("=") as [string, string])) : undefined, at: new Date().toISOString() }));
  // The referrer: lang=hi for the language (core/InstallLanguage.kt) and the ad pairs
  // (data/attribution/InstallAttribution.kt) — each a plain k=v, the whole encoded once for Play.
  const referrerPairs = [
    ...(adPairs.length ? ["utm_source=meta", "utm_medium=link", ...adPairs] : []),
    ...(language ? [`lang=${language}`] : []),
  ];
  const playUrl = referrerPairs.length ? `${PLAY_URL}&referrer=${encodeURIComponent(referrerPairs.join("&"))}` : PLAY_URL;
  const q = [...qs, ...(language ? [`lang=${language}`] : [])];
  const intentUrl = `intent://${target}${q.length ? `?${q.join("&")}` : ""}#Intent;scheme=mylaundry;package=${PACKAGE};S.browser_fallback_url=${encodeURIComponent(playUrl)};end`;

  const body = android
    ? `<h1>Opening MyLaundry…</h1>
       <p>App khul raha hai. Agar nahi khula, neeche dabaiye.</p>
       <a class="btn" href="${intentUrl}">Open MyLaundry app</a>
       <a class="link" href="${playUrl}">Get the app on Play Store</a>
       <script>location.replace(${JSON.stringify(intentUrl)});</script>`
    : `<h1>Open this link on your Android phone</h1>
       <p>MyLaundry ka app Android pe hai. Phone pe ye link kholiye, ya Play Store se app lijiye.</p>
       <a class="btn" href="${playUrl}">Get the app on Play Store</a>
       <a class="link" href="/">Use MyLaundry on the website</a>`;

  const html = `<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<meta name="robots" content="noindex">
<title>MyLaundry</title>
<style>
  body { margin: 0; min-height: 100vh; display: flex; align-items: center; justify-content: center;
         font-family: system-ui, -apple-system, "Segoe UI", Roboto, sans-serif; background: #f6f7f9; color: #14181f; }
  main { max-width: 360px; padding: 32px 20px; text-align: center; }
  h1 { font-size: 22px; margin: 0 0 8px; }
  p { font-size: 15px; color: #4b5563; margin: 0 0 24px; line-height: 1.45; }
  .btn { display: block; background: #1d4ed8; color: #fff; text-decoration: none; font-weight: 700;
         font-size: 16px; padding: 15px; border-radius: 14px; margin-bottom: 14px; }
  .link { display: block; color: #1d4ed8; font-size: 15px; font-weight: 600; text-decoration: none; padding: 8px; }
</style>
</head>
<body><main>${body}</main></body>
</html>`;

  return new Response(html, {
    headers: { "content-type": "text/html; charset=utf-8", "cache-control": "no-store" },
  });
}
