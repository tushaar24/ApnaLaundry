// Diwali ₹999 WhatsApp push — runs inside the backend container.
//   docker exec -e LIST=/app/send.csv -e BATCH=batch1 -e LIMIT=1000 -e DRY_RUN=1 -w /app <container> node diwali-send.js
// Sends template diwali_999_yearly from the My Laundry App number to every row of LIST whose
// phone has no successful send yet (resume-safe), records each attempt in laundry_offer_sends,
// and writes an outbound row in laundry_whatsapp_messages so the webhook's delivered/read
// statuses attach to it. Stops at Meta's daily limit (error 130429 / 131056 / 80007) — rerun later.
const fs = require("fs");
const { PrismaClient } = require("@prisma/client");
const p = new PrismaClient();
const G = "https://graph.facebook.com/v21.0";
const PHONE_ID = process.env.WA_PHONE_ID || "1363210626875782";
const TOKEN = process.env.LAUNDRY_WHATSAPP_ACCESS_TOKEN;
const TEMPLATE = process.env.TEMPLATE || "diwali_999_yearly";
const LIMIT = Number(process.env.LIMIT || 1000), DRY = process.env.DRY_RUN === "1", BATCH = process.env.BATCH || "batch";
const rows = fs.readFileSync(process.env.LIST, "utf8").trim().split("\n").slice(1).map((l) => {
  const m = l.match(/^(\d{10}),(?:"((?:[^"]|"")*)"|([^,]*)),(\d+)/); if (!m) return null;
  return { phone: m[1], name: (m[2] != null ? m[2].replace(/""/g, '"') : m[3]) || "Sir", tier: Number(m[4]) };
}).filter(Boolean);
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
(async () => {
  const done = new Set((await p.$queryRawUnsafe("select phone from laundry_offer_sends where wa_message_id is not null")).map((r) => r.phone));
  const todo = rows.filter((r) => !done.has(r.phone)).slice(0, LIMIT);
  console.log(`list ${rows.length}, already sent ${rows.length - rows.filter((r) => !done.has(r.phone)).length}, sending now ${todo.length}${DRY ? " (DRY RUN)" : ""}`);
  let ok = 0, failed = 0, stopped = null; const sentNow = [];
  for (const r of todo) {
    if (DRY) { ok++; continue; }
    const user = await p.laundryUser.findFirst({ where: { phone: { endsWith: r.phone } }, select: { id: true } });
    const res = await fetch(`${G}/${PHONE_ID}/messages`, { method: "POST", headers: { Authorization: `Bearer ${TOKEN}`, "Content-Type": "application/json" },
      body: JSON.stringify({ messaging_product: "whatsapp", recipient_type: "individual", to: "91" + r.phone, type: "template",
        template: { name: TEMPLATE, language: { code: "en" }, components: [{ type: "body", parameters: [{ type: "text", text: r.name }] }] } }) });
    const j = await res.json().catch(() => ({}));
    const wamid = res.ok && j.messages && j.messages[0] && j.messages[0].id;
    const err = res.ok ? null : `${j.error && j.error.code}: ${(j.error && (j.error.error_data && j.error.error_data.details || j.error.message)) || res.status}`;
    await p.$executeRawUnsafe(
      "insert into laundry_offer_sends (phone, user_id, tier, name, batch, wa_message_id, status, error, sent_at) values ($1,$2,$3,$4,$5,$6,$7,$8,now()) on conflict (phone) do update set user_id=excluded.user_id, tier=excluded.tier, name=excluded.name, batch=excluded.batch, wa_message_id=excluded.wa_message_id, status=excluded.status, error=excluded.error, sent_at=now()",
      r.phone, user ? user.id : null, r.tier, r.name, BATCH, wamid || null, wamid ? "sent" : "failed", err);
    if (wamid) {
      ok++; sentNow.push({ userId: user ? user.id : null, tier: r.tier, ts: Math.floor(Date.now() / 1000) });
      await p.laundryWhatsappMessage.create({ data: { waMessageId: wamid, phoneNumberId: PHONE_ID, laundryUserId: user ? user.id : null, waId: "91" + r.phone, phone: r.phone, direction: "outbound", type: "template", body: TEMPLATE, status: "sent", waTimestamp: new Date(), raw: { campaign: BATCH, name: r.name } } }).catch(() => {});
    } else {
      failed++;
      const code = j.error && j.error.code;
      if ([130429, 131056, 80007, 131048].includes(code)) { stopped = err; break; }
    }
    if ((ok + failed) % 50 === 0) console.log(`… ${ok} sent, ${failed} failed`);
    await sleep(120);
  }
  console.log(`done: ${ok} sent, ${failed} failed${stopped ? " — STOPPED: " + stopped + " (rerun later; already-sent phones are skipped)" : ""}`);
  await p.$disconnect();
  // Stamp the CleverTap profiles right away (Diwali_999_Msg = sent etc.), so segments built on them see this batch immediately.
  if (!DRY) {
    // One "Diwali Offer Sent" event per owner messaged in THIS run (never re-sent), then the profile stamp for everyone.
    const ct = { acct: process.env.LAUNDRY_CLEVERTAP_ACCOUNT_ID, pass: process.env.LAUNDRY_CLEVERTAP_PASSCODE, region: process.env.LAUNDRY_CLEVERTAP_REGION };
    const ctUrl = (!ct.region || ct.region === "global") ? "https://api.clevertap.com/1/upload" : `https://${ct.region}.api.clevertap.com/1/upload`;
    const evs = sentNow.filter((x) => x.userId).map((x) => ({ type: "event", identity: x.userId, evtName: "Diwali Offer Sent", ts: x.ts, evtData: { offer: "diwali999", tier: x.tier, batch: BATCH, wa_status: "sent", platform: "backend" } }));
    for (let i = 0; i < evs.length; i += 500) {
      const res = await fetch(ctUrl, { method: "POST", headers: { "X-CleverTap-Account-Id": ct.acct, "X-CleverTap-Passcode": ct.pass, "Content-Type": "application/json" }, body: JSON.stringify({ d: evs.slice(i, i + 500) }) }).catch(() => null);
      if (!res || !res.ok) console.log("clevertap event upload failed for batch", i / 500);
    }
    console.log(`clevertap: ${evs.length} "Diwali Offer Sent" events for this run`);
    if (fs.existsSync("/app/ct-mark.js")) {
      const { execFileSync } = require("child_process");
      try { console.log(execFileSync("node", ["/app/ct-mark.js"], { env: { ...process.env, EVENTS: "0" }, encoding: "utf8" }).trim()); } catch (e) { console.log("clevertap stamping failed:", e.message); }
    }
  }
})().catch((e) => { console.error(e); process.exit(1); });
