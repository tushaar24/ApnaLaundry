import type { NextRequest } from "next/server";

/**
 * Server-side proxy to the shared Express backend's /api/laundry/* routes.
 *
 * A plain Next rewrite forwards the browser's Origin header, which the
 * backend's CORS allowlist rejects. This handler forwards only what the
 * backend needs (method, path, query, body, Authorization) and no Origin —
 * the backend treats it as server-to-server, so its CORS config never needs
 * to know about the website's domain.
 */

const BACKEND = process.env.BACKEND_URL ?? "http://localhost:5001";

export const dynamic = "force-dynamic";

async function proxy(req: NextRequest, pathParts: string[]): Promise<Response> {
  const url = `${BACKEND}/api/laundry/${pathParts.join("/")}${req.nextUrl.search}`;

  const headers: Record<string, string> = {};
  const ct = req.headers.get("content-type");
  if (ct) headers["content-type"] = ct;
  const auth = req.headers.get("authorization");
  if (auth) headers["authorization"] = auth;

  let upstream: Response;
  try {
    upstream = await fetch(url, {
      method: req.method,
      headers,
      body: req.method === "GET" || req.method === "HEAD" ? undefined : await req.arrayBuffer(),
      cache: "no-store",
      redirect: "manual",
    });
  } catch {
    return Response.json(
      { success: false, message: "Backend unreachable" },
      { status: 502 },
    );
  }

  return new Response(await upstream.arrayBuffer(), {
    status: upstream.status,
    headers: {
      "content-type": upstream.headers.get("content-type") ?? "application/json",
      "cache-control": "no-store",
    },
  });
}

type Ctx = { params: Promise<{ path: string[] }> };

export async function GET(req: NextRequest, ctx: Ctx) {
  const { path } = await ctx.params;
  return proxy(req, path);
}

export async function POST(req: NextRequest, ctx: Ctx) {
  const { path } = await ctx.params;
  return proxy(req, path);
}
