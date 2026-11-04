"use client";

import { useCallback, useEffect, useState, type CSSProperties } from "react";
import { openOrderCheckout } from "@/data/billing";
import { PrimaryButton } from "@/ui/basics";

/**
 * The /autopay/<userId> page body: a YEAR of MyLaundry paid once at the owner's
 * yearly price — no AutoPay mandate, no monthly option (owner, 4 Nov 2026). For
 * owners whose bank or UPI app won't approve a mandate in the app.
 */

interface Info {
  shopName: string;
  phone: string;
  active: boolean;
  oneTime: { plan: string; amount: number };
  subscription: { status: string; plan: string; amount: number } | null;
}

type View =
  | { kind: "loading" }
  | { kind: "missing" }
  | { kind: "error"; message: string }
  | { kind: "ready"; info: Info }
  | { kind: "done"; info: Info; until?: string };

const rupees = (paise: number) => "₹" + Math.round(paise / 100).toLocaleString("en-IN");

export function AutopayPage({ id }: { id: string }) {
  const [view, setView] = useState<View>({ kind: "loading" });
  const [busy, setBusy] = useState(false);
  const [note, setNote] = useState<string>("");

  const load = useCallback(async (): Promise<Info | null> => {
    const r = await fetch(`/api/laundry/public/autopay/${encodeURIComponent(id)}`, { cache: "no-store" });
    if (r.status === 404) { setView({ kind: "missing" }); return null; }
    const body = (await r.json().catch(() => ({}))) as Partial<Info> & { success?: boolean; message?: string };
    if (!r.ok || !body.success || !body.oneTime) { setView({ kind: "error", message: body.message || "Could not load" }); return null; }
    const info = body as Info;
    setView(info.active ? { kind: "done", info } : { kind: "ready", info });
    return info;
  }, [id]);

  useEffect(() => {
    load();
    // Back does nothing: the page re-enters itself so the browser never leaves it mid-payment.
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

  async function pay() {
    if (view.kind !== "ready" || busy) return;
    setBusy(true); setNote("");
    try {
      const r = await fetch(`/api/laundry/public/autopay/${encodeURIComponent(id)}/order`, { method: "POST", headers: { "content-type": "application/json" }, body: "{}" });
      const o = (await r.json().catch(() => ({}))) as { success?: boolean; code?: string; message?: string; orderId?: string; keyId?: string; amount?: number; shopName?: string; phone?: string };
      if (r.status === 409 || o.code === "ALREADY_SUBSCRIBED") { await load(); setBusy(false); return; }
      if (!r.ok || !o.success || !o.orderId || !o.keyId || !o.amount) throw new Error(o.message || "Could not start the payment. Try again.");
      await openOrderCheckout(
        { orderId: o.orderId, keyId: o.keyId, amount: o.amount, description: `MyLaundry · 1 year · ${rupees(o.amount)}`, name: o.shopName || view.info.shopName, contact: o.phone ? `+91${o.phone}` : undefined },
        {
          onPaid: async (proof) => {
            setNote("Payment received. Activating your plan…");
            try {
              const c = await fetch(`/api/laundry/public/autopay/${encodeURIComponent(id)}/confirm`, {
                method: "POST", headers: { "content-type": "application/json" },
                body: JSON.stringify({ orderId: proof.razorpay_order_id, paymentId: proof.razorpay_payment_id, signature: proof.razorpay_signature }),
              });
              const cb = (await c.json().catch(() => ({}))) as { success?: boolean; message?: string; currentEnd?: string };
              if (!c.ok || !cb.success) throw new Error(cb.message || "Paid, but activation failed. Call MyLaundry support with payment id " + proof.razorpay_payment_id);
              const info = (await load()) ?? view.info;
              setView({ kind: "done", info, until: cb.currentEnd });
            } catch (e) {
              setNote(e instanceof Error ? e.message : "Paid, but activation failed. Call MyLaundry support.");
            } finally { setBusy(false); }
          },
          onDismiss: () => { setBusy(false); setNote("Payment window closed. Nothing was charged — tap the button to try again."); },
          onError: (m) => setNote(m),
        },
      );
    } catch (e) {
      setBusy(false);
      setNote(e instanceof Error ? e.message : "Something went wrong. Try again.");
    }
  }

  const card: CSSProperties = { background: "var(--color-card, #fff)", border: "1px solid var(--color-cardborder, #e2ded6)", borderRadius: 16, padding: 20 };
  const wrap: CSSProperties = { minHeight: "100vh", background: "var(--color-bg, #f5f3ee)", color: "var(--color-ink, #16191d)", padding: "24px 16px 48px", fontFamily: "inherit" };
  const muted: CSSProperties = { color: "var(--color-muted, #5b6168)", fontSize: 14 };
  const untilText = (iso?: string) => (iso ? new Date(iso).toLocaleDateString("en-IN", { day: "numeric", month: "short", year: "numeric" }) : "");

  return (
    <main style={wrap}>
      <div style={{ maxWidth: 440, margin: "0 auto", display: "grid", gap: 14 }}>
        <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
          <span style={{ fontWeight: 800, fontSize: 20 }}>MyLaundry</span>
          <span style={muted}>· Yearly plan</span>
        </div>

        {view.kind === "loading" && <div style={card}>Loading…</div>}
        {view.kind === "missing" && <div style={card}>This link is not valid. Ask MyLaundry support for a fresh one.</div>}
        {view.kind === "error" && <div style={card}>{view.message}</div>}

        {view.kind === "done" && (
          <div style={{ ...card, borderColor: "#15803d" }}>
            <div style={{ fontSize: 22, fontWeight: 800, color: "#15803d" }}>Plan active ✓</div>
            <p style={{ margin: "8px 0 0" }}>
              {view.info.shopName ? `${view.info.shopName}'s` : "Your"} MyLaundry plan is active{view.until ? ` till ${untilText(view.until)}` : ""}. Open the app and carry on.
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

            <div style={card}>
              <div style={{ display: "flex", justifyContent: "space-between", alignItems: "baseline" }}>
                <span style={{ fontWeight: 700, fontSize: 17 }}>1 year of MyLaundry</span>
                <span style={{ fontWeight: 800, fontSize: 26 }}>{rupees(view.info.oneTime.amount)}</span>
              </div>
              <p style={{ ...muted, margin: "6px 0 14px" }}>One payment. No AutoPay, no monthly charges. Unlimited orders, bills on WhatsApp, khata and earnings for 12 months.</p>
              <PrimaryButton onClick={pay} disabled={busy}>{busy ? "Opening payment…" : `Pay ${rupees(view.info.oneTime.amount)}`}</PrimaryButton>
              <p style={{ ...muted, margin: "10px 0 0" }}>UPI (Google Pay, PhonePe, Paytm, BHIM), cards and net banking. Your plan activates the moment the payment goes through.</p>
              {note && <p style={{ margin: "10px 0 0", color: "#8a3a06", fontWeight: 600 }}>{note}</p>}
            </div>
          </>
        )}

        <p style={{ ...muted, textAlign: "center", fontSize: 12 }}>Payments are handled by Razorpay. Need help? Call MyLaundry support.</p>
      </div>
    </main>
  );
}
