"use client";

import { useCallback, useEffect, useRef, useState, type CSSProperties } from "react";
import { openSubscriptionCheckout, type SubscribeResult } from "@/data/billing";
import { PrimaryButton } from "@/ui/basics";

/** The /autopay/<userId> page body. */

interface Info {
  shopName: string;
  phone: string;
  plans: { plan: string; amount: number }[];
  defaultPlan: string;
  active: boolean;
  subscription: { status: string; plan: string; amount: number; subscriptionId: string | null; shortUrl: string | null } | null;
  keyId: string;
}

type View =
  | { kind: "loading" }
  | { kind: "missing" }
  | { kind: "error"; message: string }
  | { kind: "ready"; info: Info }
  | { kind: "done"; info: Info };

const rupees = (paise: number) => "₹" + Math.round(paise / 100).toLocaleString("en-IN");
const planName = (p: string) => (p === "annual" ? "Yearly" : "Monthly");
const per = (p: string) => (p === "annual" ? "year" : "month");

export function AutopayPage({ id }: { id: string }) {
  const [view, setView] = useState<View>({ kind: "loading" });
  const [plan, setPlan] = useState<string>("monthly");
  const [busy, setBusy] = useState(false);
  const [note, setNote] = useState<string>("");
  const [payUrl, setPayUrl] = useState<string>("");
  const polling = useRef<number | null>(null);

  const load = useCallback(async (): Promise<Info | null> => {
    const r = await fetch(`/api/laundry/public/autopay/${encodeURIComponent(id)}`, { cache: "no-store" });
    if (r.status === 404) { setView({ kind: "missing" }); return null; }
    const body = (await r.json().catch(() => ({}))) as Partial<Info> & { success?: boolean; message?: string };
    if (!r.ok || !body.success) { setView({ kind: "error", message: body.message || "Could not load" }); return null; }
    const info = body as Info;
    setView(info.active ? { kind: "done", info } : { kind: "ready", info });
    return info;
  }, [id]);

  useEffect(() => {
    load().then((info) => {
      if (!info) return;
      setPlan(info.plans.some((p) => p.plan === info.defaultPlan) ? info.defaultPlan : info.plans[0]?.plan ?? "monthly");
      // A checkout the owner already started keeps its hosted payment page (QR on a laptop).
      if (info.subscription?.status === "created" && info.subscription.shortUrl) setPayUrl(info.subscription.shortUrl);
    });
    // Back does nothing: the page re-enters itself so the browser never leaves it mid-mandate.
    const trap = () => { try { window.history.pushState(null, "", window.location.href); } catch { /* ignore */ } };
    trap();
    window.addEventListener("popstate", trap);
    return () => { window.removeEventListener("popstate", trap); if (polling.current) window.clearInterval(polling.current); };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [id]);

  // Leaving mid-payment gets the browser's "are you sure" (we can't block it outright).
  useEffect(() => {
    const warn = (e: BeforeUnloadEvent) => { if (busy) { e.preventDefault(); e.returnValue = ""; } };
    window.addEventListener("beforeunload", warn);
    return () => window.removeEventListener("beforeunload", warn);
  }, [busy]);

  function pollUntilActive() {
    if (polling.current) window.clearInterval(polling.current);
    let tries = 0;
    polling.current = window.setInterval(async () => {
      tries += 1;
      const info = await load().catch(() => null);
      if ((info && info.active) || tries > 40) { if (polling.current) window.clearInterval(polling.current); polling.current = null; setBusy(false); }
    }, 2000);
  }

  async function start() {
    if (view.kind !== "ready" || busy) return;
    setBusy(true); setNote("");
    try {
      const r = await fetch(`/api/laundry/public/autopay/${encodeURIComponent(id)}`, {
        method: "POST", headers: { "content-type": "application/json" }, body: JSON.stringify({ plan }),
      });
      const body = (await r.json().catch(() => ({}))) as Partial<SubscribeResult> & { success?: boolean; code?: string; message?: string };
      if (r.status === 409 || body.code === "ALREADY_SUBSCRIBED") { await load(); setBusy(false); return; }
      if (!r.ok || !body.success || !body.subscriptionId || !body.keyId) throw new Error(body.message || "Could not start AutoPay. Try again.");
      const result: SubscribeResult = { subscriptionId: body.subscriptionId, keyId: body.keyId, shortUrl: body.shortUrl ?? null, plan: body.plan || plan, amount: body.amount || 0, trialAmount: body.trialAmount || 0 };
      if (result.shortUrl) setPayUrl(result.shortUrl);
      await openSubscriptionCheckout(result, {
        name: view.info.shopName,
        // Razorpay's own UPI AutoPay QR (the mandate itself, not a link) shown first, then UPI apps / UPI id,
        // then card and net banking. Razorpay hides any flow it can't offer on this device or plan.
        config: {
          display: {
            blocks: {
              upi: { name: "UPI AutoPay — scan QR or choose your app", instruments: [{ method: "upi", flows: ["qr", "intent", "collect"] }] },
              other: { name: "Card / Net banking", instruments: [{ method: "card" }, { method: "netbanking" }] },
            },
            sequence: ["block.upi", "block.other"],
            preferences: { show_default_blocks: true },
          },
        },
        onSuccess: () => { setNote("Approved. Confirming with the bank…"); pollUntilActive(); },
        onDismiss: () => { setBusy(false); setNote("Checkout closed. Nothing was charged — tap the button to try again."); },
        onError: (m) => { setNote(m); },
      });
    } catch (e) {
      setBusy(false);
      setNote(e instanceof Error ? e.message : "Something went wrong. Try again.");
    }
  }

  const card: CSSProperties = { background: "var(--color-card, #fff)", border: "1px solid var(--color-cardborder, #e2ded6)", borderRadius: 16, padding: 20 };
  const wrap: CSSProperties = { minHeight: "100vh", background: "var(--color-bg, #f5f3ee)", color: "var(--color-ink, #16191d)", padding: "24px 16px 48px", fontFamily: "inherit" };
  const muted: CSSProperties = { color: "var(--color-muted, #5b6168)", fontSize: 14 };

  return (
    <main style={wrap}>
      <div style={{ maxWidth: 440, margin: "0 auto", display: "grid", gap: 14 }}>
        <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
          <span style={{ fontWeight: 800, fontSize: 20 }}>MyLaundry</span>
          <span style={{ ...muted }}>· AutoPay set-up</span>
        </div>

        {view.kind === "loading" && <div style={card}>Loading…</div>}
        {view.kind === "missing" && <div style={card}>This link is not valid. Ask MyLaundry support for a fresh one.</div>}
        {view.kind === "error" && <div style={card}>{view.message}</div>}

        {view.kind === "done" && (
          <div style={{ ...card, borderColor: "#15803d" }}>
            <div style={{ fontSize: 22, fontWeight: 800, color: "#15803d" }}>AutoPay is set up ✓</div>
            <p style={{ margin: "8px 0 0" }}>{view.info.shopName ? `${view.info.shopName}'s plan is active.` : "Your plan is active."} Open the MyLaundry app and carry on.</p>
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
              <div style={{ fontWeight: 700, marginBottom: 10 }}>Choose your plan</div>
              <div style={{ display: "grid", gap: 8 }}>
                {view.info.plans.map((p) => {
                  const on = p.plan === plan;
                  return (
                    <button key={p.plan} type="button" onClick={() => !busy && setPlan(p.plan)} disabled={busy}
                      style={{ textAlign: "left", padding: "12px 14px", borderRadius: 12, border: `1.5px solid ${on ? "#1d4ed8" : "var(--color-cardborder, #e2ded6)"}`, background: on ? "#e6ecfb" : "#fff", cursor: "pointer" }}>
                      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "baseline" }}>
                        <span style={{ fontWeight: 700 }}>{planName(p.plan)}</span>
                        <span style={{ fontWeight: 800, fontSize: 18 }}>{rupees(p.amount)}<span style={{ ...muted, fontWeight: 500 }}>/{per(p.plan)}</span></span>
                      </div>
                    </button>
                  );
                })}
              </div>
              <div style={{ marginTop: 14 }}>
                <PrimaryButton onClick={start} disabled={busy}>
                  {busy ? "Opening payment…" : `Set up AutoPay · ${rupees(view.info.plans.find((p) => p.plan === plan)?.amount ?? 0)}/${per(plan)}`}
                </PrimaryButton>
              </div>
              <p style={{ ...muted, margin: "10px 0 0" }}>
                Pay with UPI AutoPay (Google Pay, PhonePe, Paytm, BHIM), a card or net banking. Your UPI app will ask you to approve the AutoPay once; it then renews on its own, and you can cancel any time from the app's Settings.
              </p>
              {note && <p style={{ margin: "10px 0 0", color: "#8a3a06", fontWeight: 600 }}>{note}</p>}
            </div>

            <div style={card}>
              <div style={{ fontWeight: 700 }}>Want to scan a QR?</div>
              <p style={{ ...muted, margin: "4px 0 0" }}>
                Tap <b>Set up AutoPay</b> and choose <b>Scan QR</b> under UPI — Razorpay shows the AutoPay QR there, and any UPI app that supports AutoPay can scan it.
                {payUrl ? <> On a laptop, <a href={payUrl} target="_blank" rel="noopener noreferrer" style={{ color: "#1d4ed8" }}>open the payment page</a> and the QR is on screen.</> : null}
              </p>
            </div>
          </>
        )}

        <p style={{ ...muted, textAlign: "center", fontSize: 12 }}>Payments are handled by Razorpay. Need help? Call MyLaundry support.</p>
      </div>
    </main>
  );
}
