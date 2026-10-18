import type { NextRequest } from "next/server";
import { openAppResponse } from "@/core/openApp";

/**
 * https links into the Android app: /open/<screen> opens mylaundry://<screen>.
 *
 *   /open/new-order · /open/home · /open/customers · /open/earnings · /open/subscription
 *   (anything else, or plain /open, opens Home)
 *
 * Page and app hand-off: core/openApp.ts.
 */

export const dynamic = "force-dynamic";

export async function GET(req: NextRequest, { params }: { params: Promise<{ screen?: string[] }> }) {
  const { screen } = await params;
  return openAppResponse(req.headers.get("user-agent") ?? "", (screen?.[0] ?? "").toLowerCase(), "open");
}
