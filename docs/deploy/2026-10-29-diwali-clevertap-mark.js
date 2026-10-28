// Mark every owner we messaged on their CleverTap profile + one event, so a segment can be built there.
//   profile: Diwali_999_Msg (sent | queued | refused), Diwali_999_WA (sent | delivered | read | failed | none),
//            Diwali_999_Tier, Diwali_999_Batch, Diwali_999_Sent_At ($D_ date)
//   event:   "Diwali Offer Sent" {offer, tier, batch, wa_status} at the send time (accepted sends only)
// Re-runnable: profile props are overwritten with the latest status; the event is re-sent with the same ts
// (CleverTap keeps duplicates, so re-run only when the recipient list grew — or set EVENTS=0).
const { PrismaClient } = require("@prisma/client"); const p = new PrismaClient();
const acct = process.env.LAUNDRY_CLEVERTAP_ACCOUNT_ID, pass = process.env.LAUNDRY_CLEVERTAP_PASSCODE, region = process.env.LAUNDRY_CLEVERTAP_REGION;
const url = (!region || region === "global") ? "https://api.clevertap.com/1/upload" : `https://${region}.api.clevertap.com/1/upload`;
const H = { "X-CleverTap-Account-Id": acct, "X-CleverTap-Passcode": pass, "Content-Type": "application/json" };
const sendEvents = process.env.EVENTS !== "0";
(async () => {
  const rows = await p.$queryRawUnsafe(`select s.phone, s.user_id, s.tier, s.batch, s.status, s.wa_message_id, s.sent_at, m.status as wa
    from laundry_offer_sends s left join laundry_whatsapp_messages m on m.wa_message_id = s.wa_message_id order by s.sent_at`);
  const recs = [];
  for (const r of rows) {
    if (!r.user_id) continue;
    const msg = r.wa_message_id ? "sent" : r.status === "retry" ? "queued" : "refused";
    const wa = r.wa_message_id ? (r.wa || "sent") : "none";
    const ts = Math.floor(new Date(r.sent_at).getTime() / 1000);
    recs.push({ type: "profile", identity: r.user_id, profileData: { Diwali_999_Msg: msg, Diwali_999_WA: wa, Diwali_999_Tier: Number(r.tier), Diwali_999_Batch: r.batch, Diwali_999_Sent_At: "$D_" + ts } });
    if (sendEvents && r.wa_message_id) recs.push({ type: "event", identity: r.user_id, evtName: "Diwali Offer Sent", ts, evtData: { offer: "diwali999", tier: Number(r.tier), batch: r.batch, wa_status: wa, platform: "backend" } });
  }
  let ok = 0, bad = 0;
  for (let i = 0; i < recs.length; i += 500) {
    const batch = recs.slice(i, i + 500);
    const res = await fetch(url, { method: "POST", headers: H, body: JSON.stringify({ d: batch }) });
    const j = await res.json().catch(() => ({}));
    ok += j.processed || 0; bad += (j.unprocessed || []).length;
    if (!res.ok || (j.unprocessed || []).length) console.log("batch", i / 500, res.status, JSON.stringify(j).slice(0, 300));
  }
  console.log(`clevertap: ${rows.length} rows, ${recs.length} records uploaded → processed ${ok}, unprocessed ${bad}; rows without identity ${rows.filter((r) => !r.user_id).length}`);
  await p.$disconnect();
})().catch((e) => { console.error(e.message); process.exit(1); });
