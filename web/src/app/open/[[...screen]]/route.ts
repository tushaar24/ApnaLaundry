import type { NextRequest } from "next/server";
import { LANGS, openAppResponse } from "@/core/openApp";

/**
 * https links into the Android app: /open/<screen> opens mylaundry://<screen>.
 *
 *   /open/new-order · /open/home · /open/customers · /open/earnings · /open/subscription
 *   (anything else, or plain /open, opens Home)
 *
 * Language links: /open/<lang> and /open/<lang>/<screen> (or ?lang=<lang> on any of
 * them) open the app in that language — en hi mr gu bn ta te kn ml pa or.
 *
 * Page and app hand-off: core/openApp.ts.
 */

export const dynamic = "force-dynamic";

export async function GET(req: NextRequest, { params }: { params: Promise<{ screen?: string[] }> }) {
  const { screen } = await params;
  const parts = (screen ?? []).map((p) => p.toLowerCase());
  const pathLang = LANGS.includes(parts[0] ?? "") ? parts.shift()! : "";
  const lang = pathLang || (req.nextUrl.searchParams.get("lang") ?? "").toLowerCase();
  return openAppResponse(req.headers.get("user-agent") ?? "", parts[0] ?? "", "open", "", lang);
}
