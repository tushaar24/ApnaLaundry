import type { NextRequest } from "next/server";
import { openAppResponse } from "@/core/openApp";

/**
 * The "Plan shuru karein" WhatsApp button: /pay/<code> (the template's dynamic
 * URL suffix — a per-owner code). For now it opens the app's Plans screen
 * (mylaundry://subscription), the same as /open/subscription, and logs the code
 * so taps can be counted per owner. Page and app hand-off: core/openApp.ts.
 */

export const dynamic = "force-dynamic";

export async function GET(req: NextRequest, { params }: { params: Promise<{ code?: string[] }> }) {
  const { code } = await params;
  const lang = (req.nextUrl.searchParams.get("lang") ?? "").toLowerCase(); // optional, e.g. ?lang=hi
  return openAppResponse(req.headers.get("user-agent") ?? "", "subscription", "pay", code?.[0] ?? "", lang);
}
