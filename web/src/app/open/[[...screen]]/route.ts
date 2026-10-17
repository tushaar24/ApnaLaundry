import type { NextRequest } from "next/server";

/**
 * https links into the Android app: /open/<screen> opens mylaundry://<screen>
 * (push/DeepLinks.kt, app 1.0.8+). WhatsApp template buttons and SMS only take
 * https, so this page hands over to the app.
 *
 *   /open/new-order · /open/home · /open/customers · /open/earnings · /open/subscription
 *   (anything else, or plain /open, opens Home)
 *
 * Android: an intent:// link — Chrome opens the app if it's installed, else the
 * Play Store (also for builds older than 1.0.8, which can't take the link, so
 * the owner lands on Update). It fires on load, with a button for in-app
 * browsers that need a tap. Other devices get the Play Store and website links.
 */

const SCREENS = ["new-order", "home", "customers", "earnings", "subscription"];
const PACKAGE = "com.dailyworks.apnalaundry";
const PLAY_URL = `https://play.google.com/store/apps/details?id=${PACKAGE}`;

export const dynamic = "force-dynamic";

export async function GET(req: NextRequest, { params }: { params: Promise<{ screen?: string[] }> }) {
  const { screen } = await params;
  const asked = (screen?.[0] ?? "").toLowerCase();
  const target = SCREENS.includes(asked) ? asked : "home";
  const android = /android/i.test(req.headers.get("user-agent") ?? "");
  const intentUrl = `intent://${target}#Intent;scheme=mylaundry;package=${PACKAGE};S.browser_fallback_url=${encodeURIComponent(PLAY_URL)};end`;

  const body = android
    ? `<h1>Opening MyLaundry…</h1>
       <p>App khul raha hai. Agar nahi khula, neeche dabaiye.</p>
       <a class="btn" href="${intentUrl}">Open MyLaundry app</a>
       <a class="link" href="${PLAY_URL}">Get the app on Play Store</a>
       <script>location.replace(${JSON.stringify(intentUrl)});</script>`
    : `<h1>Open this link on your Android phone</h1>
       <p>MyLaundry ka app Android pe hai. Phone pe ye link kholiye, ya Play Store se app lijiye.</p>
       <a class="btn" href="${PLAY_URL}">Get the app on Play Store</a>
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
