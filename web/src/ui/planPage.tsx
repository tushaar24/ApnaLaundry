"use client";

import { useCallback, useEffect, useState, type CSSProperties } from "react";
import { openSubscriptionCheckout, type SubscribeResult } from "@/data/billing";
import { PrimaryButton } from "@/ui/basics";

/**
 * The /plan/<plan>/<userId> page body: ₹799 a year or ₹99 a month, the same Razorpay
 * subscription the app's Plans screen starts (UPI approval once, then it renews).
 * Price is fixed by the link; the server refuses if this owner's plan is the other one.
 */

interface Info {
  plan: "annual" | "monthly";
  amount: number;
  shopName: string;
  phone: string;
  active: boolean;
  unavailable: string | null;
  subscription: { status: string; plan: string; amount: number; currentEnd?: string | null } | null;
}

type View =
  | { kind: "loading" }
  | { kind: "missing" }
  | { kind: "error"; message: string }
  | { kind: "ready"; info: Info }
  | { kind: "done"; info: Info };

const rupees = (paise: number) => "₹" + Math.round(paise / 100).toLocaleString("en-IN");

export function PlanPage({ id, plan }: { id: string; plan: "annual" | "monthly" }) {
  const [view, setView] = useState<View>({ kind: "loading" });
  const [busy, setBusy] = useState(false);
  const [note, setNote] = useState<string>("");

  const load = useCallback(async (): Promise<Info | null> => {
    const r = await fetch(`/api/laundry/public/plan/${plan}/${encodeURIComponent(id)}`, { cache: "no-store" });
    if (r.status === 404) { setView({ kind: "missing" }); return null; }
    const body = (await r.json().catch(() => ({}))) as Partial<Info> & { success?: boolean; message?: string };
    if (!r.ok || !body.success || !body.amount) { setView({ kind: "error", message: body.message || "Could not load" }); return null; }
    const info = body as Info;
    setView(info.active ? { kind: "done", info } : { kind: "ready", info });
    return info;
  }, [id, plan]);

  useEffect(() => {
    load();
    const trap = () => { try { window.history.pushState(null, "", window.location.href); } catch { /* ignore */ } };
    trap();
    window.addEventListener("popstate", trap);
    return () => window.removeEventListener("popstate", trap);
  }, [load]);

  useEffect(() => {
    const warn = (e: BeforeUnloadEvent) => { if (busy) { e.preventDefault(); e.returnValue = ""; } };
    window.addEventListener("beforeunload", warn);
    return () => window.removeEventListener("beforeunload", warn);
  }, [busy]);

  // After Razorpay says "authorized", the webhook activates the row; poll until it shows.
  async function waitForActive(): Promise<void> {
    for (let i = 0; i < 20; i++) {
      await new Promise((ok) => setTimeout(ok, 2500));
      const info = await load();
      if (info?.active) return;
    }
    setNote("Payment approved. Your plan will show as active in a minute — open the app.");
  }

  async function pay() {
    if (view.kind !== "ready" || busy) return;
    setBusy(true); setNote("");
    try {
      const r = await fetch(`/api/laundry/public/plan/${plan}/${encodeURIComponent(id)}`, { method: "POST", headers: { "content-type": "application/json" }, body: "{}" });
      const o = (await r.json().catch(() => ({}))) as Partial<SubscribeResult> & { success?: boolean; code?: string; message?: string };
      if (r.status === 409 && o.code === "ALREADY_SUBSCRIBED") { await load(); setBusy(false); return; }
      if (!r.ok || !o.success || !o.subscriptionId || !o.keyId) throw new Error(o.message || "Could not start the payment. Try again.");
      await openSubscriptionCheckout(o as SubscribeResult, {
        name: view.info.shopName || undefined,
        contact: view.info.phone ? undefined : undefined,
        onSuccess: async () => { setNote("Approved. Activating your plan…"); await waitForActive(); setBusy(false); },
        onDismiss: () => { setBusy(false); setNote("Payment window closed. Nothing was charged — tap the button to try again."); },
        onError: (m) => setNote(m),
      });
    } catch (e) {
      setBusy(false);
      setNote(e instanceof Error ? e.message : "Something went wrong. Try again.");
    }
  }

  const card: CSSProperties = { background: "var(--color-card, #fff)", border: "1px solid var(--color-cardborder, #e2ded6)", borderRadius: 16, padding: 20 };
  const wrap: CSSProperties = { minHeight: "100vh", background: "var(--color-bg, #f5f3ee)", color: "var(--color-ink, #16191d)", padding: "24px 16px 48px", fontFamily: "inherit" };
  const muted: CSSProperties = { color: "var(--color-muted, #5b6168)", fontSize: 14 };
  const yearly = plan === "annual";
  const untilText = (iso?: string | null) => (iso ? new Date(iso).toLocaleDateString("en-IN", { day: "numeric", month: "short", year: "numeric" }) : "");

  return (
    <main style={wrap}>
      <div style={{ maxWidth: 440, margin: "0 auto", display: "grid", gap: 14 }}>
        <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
          <span style={{ fontWeight: 800, fontSize: 20 }}>MyLaundry</span>
          <span style={muted}>· {yearly ? "Yearly plan" : "Monthly plan"}</span>
        </div>

        {view.kind === "loading" && <div style={card}>Loading…</div>}
        {view.kind === "missing" && <div style={card}>This link is not valid. Ask MyLaundry support for a fresh one.</div>}
        {view.kind === "error" && <div style={card}>{view.message}</div>}

        {view.kind === "done" && (
          <div style={{ ...card, borderColor: "#15803d" }}>
            <div style={{ fontSize: 22, fontWeight: 800, color: "#15803d" }}>Plan active ✓</div>
            <p style={{ margin: "8px 0 0" }}>
              {view.info.shopName ? `${view.info.shopName}'s` : "Your"} MyLaundry plan is active{view.info.subscription?.currentEnd ? ` till ${untilText(view.info.subscription.currentEnd)}` : ""}. Open the app and carry on.
            </p>
          </div>
        )}

        {view.kind === "ready" && (
          <>
            <div style={card}>
              <div style={{ fontSize: 13, ...muted, textTransform: "uppercase", letterSpacing: 0.6 }}>Shop</div>
              <div style={{ fontSize: 22, fontWeight: 800, marginTop: 2 }}>{view.info.shopName || "Your shop"}</div>
              {view.info.phone && <div style={muted}>+91 {view.info.phone}</div>}
            </div>

            {view.info.unavailable ? (
              <div style={card}>
                <div style={{ fontWeight: 700, fontSize: 17 }}>{view.info.unavailable}</div>
                <p style={{ ...muted, margin: "6px 0 0" }}>Open the MyLaundry app → Plans to see your plan, or message MyLaundry support on WhatsApp.</p>
              </div>
            ) : (
              <div style={card}>
                <div style={{ display: "flex", justifyContent: "space-between", alignItems: "baseline" }}>
                  <span style={{ fontWeight: 700, fontSize: 17 }}>{yearly ? "MyLaundry for 1 year" : "MyLaundry for 1 month"}</span>
                  <span style={{ fontWeight: 800, fontSize: 26 }}>{rupees(view.info.amount)}<span style={{ fontSize: 14, fontWeight: 600 }}>{yearly ? "/year" : "/month"}</span></span>
                </div>
                <p style={{ ...muted, margin: "6px 0 14px" }}>
                  {yearly
                    ? "Unlimited orders, bills on WhatsApp, khata and earnings for 12 months. Approve once in your UPI app; renews next year. Cancel any time from the app."
                    : "Unlimited orders, bills on WhatsApp, khata and earnings. Approve once in your UPI app; ₹99 renews every month. Cancel any time from the app."}
                </p>
                <PrimaryButton onClick={pay} disabled={busy}>{busy ? "Opening payment…" : `Pay ${rupees(view.info.amount)}`}</PrimaryButton>
                <p style={{ ...muted, margin: "10px 0 0" }}>Google Pay, PhonePe, Paytm, BHIM. Your plan activates the moment the approval goes through.</p>
                {note && <p style={{ margin: "10px 0 0", color: "#8a3a06", fontWeight: 600 }}>{note}</p>}
              </div>
            )}
          </>
        )}

        <p style={{ ...muted, textAlign: "center", fontSize: 12 }}>Payments are handled by Razorpay. Need help? Message MyLaundry support on WhatsApp.</p>
      </div>
    </main>
  );
}
