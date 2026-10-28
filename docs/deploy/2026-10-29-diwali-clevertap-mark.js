// Mark every owner we messaged on their CleverTap profile, so segments and campaigns can be built there.
//   Diwali_999_Msg     sent | queued | refused               (did Meta accept our send)
//   Diwali_999_WA      sent | delivered | read | failed | none
//   Diwali_999_Stage   one value per owner, the furthest point reached, in this order:
//                        paid        → paid ₹999
//                        checkout    → opened Razorpay for ₹999 (subscription created), not paid
//                        plans_seen  → the Plans screen with ₹999 was shown (CleverTap "Paywall Shown" with paywall_variant offer:diwali999,
//                                      apps 1.0.13+), no checkout
//                        app_opened  → the app / website loaded the ₹999 price after the message (any app version), Plans not confirmed
//                        read / delivered / sent / undelivered / queued / refused  → the WhatsApp side only
//   Diwali_999_Tier, Diwali_999_Batch, Diwali_999_Sent_At ($D_ date)
//   event "Diwali Offer Sent" {offer, tier, batch, wa_status} at the send time — only with EVENTS=1 (first run); re-sending duplicates.
// Re-run any time (e.g. every 30 min): profile values are overwritten with the latest state.
const { PrismaClient } = require("@prisma/client"); const p = new PrismaClient();
const acct = process.env.LAUNDRY_CLEVERTAP_ACCOUNT_ID, pass = process.env.LAUNDRY_CLEVERTAP_PASSCODE, region = process.env.LAUNDRY_CLEVERTAP_REGION;
const base = (!region || region === "global") ? "https://api.clevertap.com" : `https://${region}.api.clevertap.com`;
const H = { "X-CleverTap-Account-Id": acct, "X-CleverTap-Passcode": pass, "Content-Type": "application/json" };
const OFFER = process.env.OFFER || "diwali999", PLAN = process.env.OFFER_PLAN || "plan_TtQFD2nbdDhSHq", FROM = process.env.FROM || "20261028";
const sendEvents = process.env.EVENTS === "1";
const ymd = (d = new Date()) => d.toISOString().slice(0, 10).replace(/-/g, "");

// CleverTap: identities that saw a paywall carrying this offer (apps 1.0.13+ stamp paywall_variant on the event).
async function plansSeen() {
  const seen = new Set();
  let r = await fetch(`${base}/1/events.json?batch_size=5000`, { method: "POST", headers: H, body: JSON.stringify({ event_name: "Paywall Shown", from: +FROM, to: +ymd() }) });
  let j = await r.json(); let cursor = j.cursor;
  while (cursor) {
    const rr = await fetch(`${base}/1/events.json?cursor=${cursor}`, { headers: H }); const jj = await rr.json();
    if (jj.status !== "success") break;
    for (const rec of jj.records || []) if (rec.event_props && rec.event_props.paywall_variant === `offer:${OFFER}` && rec.profile && rec.profile.identity) seen.add(rec.profile.identity);
    cursor = jj.next_cursor;
  }
  return seen;
}

(async () => {
  const rows = await p.$queryRawUnsafe(`select s.phone, s.user_id, s.tier, s.batch, s.status, s.wa_message_id, s.sent_at, m.status as wa
    from laundry_offer_sends s left join laundry_whatsapp_messages m on m.wa_message_id = s.wa_message_id order by s.sent_at`);
  const ids = rows.map((r) => r.user_id).filter(Boolean);
  const viewed = new Set((await p.$queryRawUnsafe(`select distinct e.user_id from laundry_offer_events e join laundry_offer_sends s on s.user_id = e.user_id where e.offer = $1 and e.at >= s.sent_at`, OFFER)).map((r) => r.user_id));
  const idSet = new Set(ids);
  const subs = (await p.$queryRawUnsafe(`select user_id, status, paid_count from laundry_subscriptions where plan_id = $1`, PLAN)).filter((s) => idSet.has(s.user_id));
  const checkout = new Set(subs.map((s) => s.user_id)), paid = new Set(subs.filter((s) => s.status === "active" || Number(s.paid_count || 0) > 0).map((s) => s.user_id));
  let seen = new Set(); try { seen = await plansSeen(); } catch (e) { console.log("clevertap events read failed, plans_seen skipped:", e.message); }
  const recs = []; const stages = {};
  for (const r of rows) {
    if (!r.user_id) continue;
    const msg = r.wa_message_id ? "sent" : r.status === "retry" ? "queued" : "refused";
    const wa = r.wa_message_id ? (r.wa || "sent") : "none";
    const stage = paid.has(r.user_id) ? "paid" : checkout.has(r.user_id) ? "checkout" : seen.has(r.user_id) ? "plans_seen" : viewed.has(r.user_id) ? "app_opened"
      : msg !== "sent" ? msg : wa === "failed" ? "undelivered" : wa; // read | delivered | sent
    stages[stage] = (stages[stage] || 0) + 1;
    const ts = Math.floor(new Date(r.sent_at).getTime() / 1000);
    recs.push({ type: "profile", identity: r.user_id, profileData: { Diwali_999_Msg: msg, Diwali_999_WA: wa, Diwali_999_Stage: stage, Diwali_999_Tier: Number(r.tier), Diwali_999_Batch: r.batch, Diwali_999_Sent_At: "$D_" + ts } });
    if (sendEvents && r.wa_message_id) recs.push({ type: "event", identity: r.user_id, evtName: "Diwali Offer Sent", ts, evtData: { offer: OFFER, tier: Number(r.tier), batch: r.batch, wa_status: wa, platform: "backend" } });
  }
  let ok = 0, bad = 0;
  for (let i = 0; i < recs.length; i += 500) {
    const res = await fetch(`${base}/1/upload`, { method: "POST", headers: H, body: JSON.stringify({ d: recs.slice(i, i + 500) }) });
    const j = await res.json().catch(() => ({}));
    ok += j.processed || 0; bad += (j.unprocessed || []).length;
    if (!res.ok || (j.unprocessed || []).length) console.log("batch", i / 500, res.status, JSON.stringify(j).slice(0, 300));
  }
  console.log(`${new Date().toISOString()} clevertap: ${rows.length} owners → ${recs.length} records, processed ${ok}, unprocessed ${bad} | stages ${JSON.stringify(stages)} | plans_seen from CleverTap: ${seen.size}`);
  await p.$disconnect();
})().catch((e) => { console.error(e.message); process.exit(1); });
