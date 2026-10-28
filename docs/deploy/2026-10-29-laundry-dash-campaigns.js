// WhatsApp campaign readout (first: the Diwali ₹999/year push, 28 Oct 2026).
//   GET /campaigns?offer=diwali999        the page (queries the DB on every load, refreshes itself every minute)
//   GET /api/campaigns/:offer             the same numbers as JSON
// Reads laundry_offer_sends (one row per owner messaged, written by the send
// script), laundry_whatsapp_messages (delivered / read / failed from Meta's
// webhook), laundry_offer_events (the shop's app or the website loaded the offer
// price) and laundry_subscriptions on the offer's Razorpay plan (checkout opened,
// paid). Mounted from server.js: require('./campaigns')(app, readOnly).
'use strict';

const TIER_NAMES = {
  0: 'On a ₹2 trial', 1: 'Lapsed, or 3+ orders never paid', 2: 'Opened Razorpay, never finished', 3: 'Never paid, 1–2 orders',
  4: 'Set up, no order, active last 7 days', 5: 'Set up, no order, inactive', 6: 'Never set up, active last 7 days', 7: 'Never set up, inactive',
};
const META_ERRORS = {
  '131049': 'Meta per-person marketing cap (too many promos from businesses)', '130429': 'Our number\'s daily sending limit', '131026': 'Not on WhatsApp / can\'t receive',
  '130472': 'Number in a Meta experiment', '131047': 'Outside the 24-hour window', '131056': 'Too many messages to this number', '131053': 'Media upload error',
};

function offerConfig(name) {
  try {
    const o = JSON.parse(process.env.LAUNDRY_PAYWALL_OFFER || 'null');
    if (o && o.name === name) return { planId: o.planIds && o.planIds.annual, price: o.prices && o.prices.annual, from: o.from, until: o.until };
  } catch { /* fall through */ }
  return { planId: process.env.CAMPAIGN_PLAN_ID || 'plan_TtQFD2nbdDhSHq', price: 999, from: null, until: null };
}

async function campaignData(q, offer) {
  const cfg = offerConfig(offer);
  const sends = await q(
    `select s.phone, s.user_id, s.tier, s.name, s.batch, s.wa_message_id, s.status as send_status, s.error, s.sent_at,
            m.status as wa_status, m.error_code, m.error_title, m.wa_timestamp
       from laundry_offer_sends s
       left join laundry_whatsapp_messages m on m.wa_message_id = s.wa_message_id
      order by s.sent_at`);
  const userIds = [...new Set(sends.map((s) => s.user_id).filter(Boolean))];
  const views = userIds.length ? await q(
    `select e.user_id, min(e.at) as first_at, count(*)::int as n
       from laundry_offer_events e join laundry_offer_sends s on s.user_id = e.user_id
      where e.offer = $1 and e.at >= s.sent_at group by e.user_id`, [offer]) : [];
  const subs = await q(
    `select sub.user_id, sub.status, sub.paid_count, sub.amount, sub.created_at, sub.updated_at, u.phone, sh.name as shop
       from laundry_subscriptions sub
       join laundry_users u on u.id = sub.user_id
       left join laundry_shops sh on sh.user_id = sub.user_id and (sh.shop_id = '' or sh.shop_id is null)
      where sub.plan_id = $1 order by sub.created_at`, [cfg.planId]);
  const V = new Map(views.map((v) => [v.user_id, v]));
  const S = new Map();
  subs.forEach((s) => { if (!S.has(s.user_id)) S.set(s.user_id, []); S.get(s.user_id).push(s); });
  const paidRow = (s) => s.status === 'active' || Number(s.paid_count || 0) > 0;
  const rows = sends.map((s) => {
    const ss = S.get(s.user_id) || [];
    return {
      phone: s.phone, name: s.name, tier: Number(s.tier), batch: s.batch, sent_at: s.sent_at,
      accepted: Boolean(s.wa_message_id), retry: s.send_status === 'retry', send_error: s.error,
      wa: s.wa_status || (s.wa_message_id ? 'sent' : null), wa_error: s.error_code ? `${s.error_code} ${s.error_title || ''}`.trim() : null, wa_error_code: s.error_code,
      viewed: V.has(s.user_id), viewed_at: V.get(s.user_id)?.first_at || null,
      checkout: ss.length > 0, paid: ss.some(paidRow), paid_at: (ss.find(paidRow) || {}).updated_at || null,
    };
  });
  const agg = (g) => ({
    n: g.length, accepted: g.filter((r) => r.accepted).length, retry: g.filter((r) => r.retry).length, refused: g.filter((r) => !r.accepted && !r.retry).length,
    delivered: g.filter((r) => ['delivered', 'read'].includes(r.wa)).length, read: g.filter((r) => r.wa === 'read').length, pending: g.filter((r) => r.wa === 'sent').length,
    undelivered: g.filter((r) => r.wa === 'failed').length, viewed: g.filter((r) => r.viewed).length, checkout: g.filter((r) => r.checkout).length, paid: g.filter((r) => r.paid).length,
  });
  const byTier = {};
  rows.forEach((r) => { (byTier[r.tier] ??= []).push(r); });
  const byHour = {};
  rows.filter((r) => r.accepted).forEach((r) => { const k = istHour(r.sent_at); byHour[k] = (byHour[k] || 0) + 1; });
  const waErrors = {};
  rows.filter((r) => r.wa === 'failed').forEach((r) => { const k = r.wa_error_code || '?'; waErrors[k] = (waErrors[k] || 0) + 1; });
  const sendErrors = {};
  rows.filter((r) => !r.accepted).forEach((r) => { const k = (r.send_error || '(none)').slice(0, 90); sendErrors[k] = (sendErrors[k] || 0) + 1; });
  const messagedUsers = new Set(sends.map((s) => s.user_id));
  return {
    offer, generated_at: new Date().toISOString(), config: cfg,
    first_send: sends.length ? sends[0].sent_at : null, last_send: sends.length ? sends[sends.length - 1].sent_at : null,
    total: agg(rows), by_tier: Object.fromEntries(Object.entries(byTier).map(([t, g]) => [t, agg(g)])),
    by_hour: byHour, wa_errors: waErrors, send_errors: sendErrors,
    offer_subscriptions: {
      rows: subs.length, paid: subs.filter(paidRow).length, by_status: subs.reduce((a, s) => (a[s.status] = (a[s.status] || 0) + 1, a), {}),
      not_messaged: subs.filter((s) => !messagedUsers.has(s.user_id)).length,
      list: subs.map((s) => ({ phone: String(s.phone || '').slice(-10), shop: s.shop || '', status: s.status, paid: paidRow(s), tier: (sends.find((x) => x.user_id === s.user_id) || {}).tier ?? null, at: s.created_at })),
    },
    converted: rows.filter((r) => r.paid || r.checkout).map((r) => ({ phone: r.phone, name: r.name, tier: r.tier, viewed_at: r.viewed_at, checkout: r.checkout, paid: r.paid, paid_at: r.paid_at })),
  };
}

const IST = { timeZone: 'Asia/Kolkata' };
const istHour = (d) => new Date(d).toLocaleString('en-IN', { ...IST, day: '2-digit', month: 'short', hour: '2-digit', hour12: false }).replace(',', '') + ':00';
const ist = (d) => (d ? new Date(d).toLocaleString('en-IN', { ...IST, day: '2-digit', month: 'short', hour: '2-digit', minute: '2-digit', hour12: false }) : '–');
const pct = (a, b) => (b ? `${Math.round((100 * a) / b)}%` : '–');
const esc = (s) => String(s ?? '').replace(/[&<>"]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]));
const num = (n) => Number(n || 0).toLocaleString('en-IN');

function page(d) {
  const T = d.total;
  const steps = [
    ['Messages accepted by Meta', T.accepted, 'sent from +91 98867 88977'],
    ['Delivered', T.delivered, 'Meta status webhook'],
    ['Read', T.read, 'blue ticks'],
    ['Opened the Plans screen', T.viewed, 'their app / the website loaded the ₹' + d.config.price + ' price after the message'],
    ['Opened Razorpay checkout', T.checkout, 'a subscription on the offer plan was created'],
    ['Paid ₹' + d.config.price, T.paid, '₹' + num(T.paid * d.config.price) + ' collected'],
  ];
  let prev = T.accepted;
  const funnel = steps.map(([label, n, hint], i) => {
    const w = T.accepted ? Math.max(0.5, Math.round((100 * n) / T.accepted)) : 0;
    const row = `<div class="frow"><div class="flabel">${label}<small>${hint}</small></div><div class="fbar"><div class="ffill" style="width:${w}%"></div></div><div class="fnum">${num(n)}</div><div class="fpct">${pct(n, T.accepted)}${i ? `<span class="fprev"> · ${pct(n, prev)} of previous</span>` : ''}</div></div>`;
    if (n) prev = n;
    return row;
  }).join('');
  const tiers = Object.keys(d.by_tier).sort((a, b) => a - b).map((t) => {
    const g = d.by_tier[t];
    return `<tr><td>${t}</td><td>${esc(TIER_NAMES[t] || '')}</td><td class=n>${num(g.n)}</td><td class=n>${num(g.accepted)}</td><td class=n>${num(g.delivered)} <small>${pct(g.delivered, g.accepted)}</small></td><td class=n>${num(g.read)} <small>${pct(g.read, g.accepted)}</small></td><td class=n>${num(g.viewed)} <small>${pct(g.viewed, g.accepted)}</small></td><td class=n>${num(g.checkout)}</td><td class=n><b>${num(g.paid)}</b> <small>${pct(g.paid, g.accepted)}</small></td><td class=n>${num(g.retry)}</td></tr>`;
  }).join('');
  const hours = Object.entries(d.by_hour).sort().map(([h, n]) => `<tr><td>${esc(h)} IST</td><td class=n>${num(n)}</td></tr>`).join('') || '<tr><td colspan=2>Nothing sent yet</td></tr>';
  const waErr = Object.entries(d.wa_errors).sort((a, b) => b[1] - a[1]).map(([k, n]) => `<tr><td>${esc(k)}</td><td>${esc(META_ERRORS[k] || '')}</td><td class=n>${num(n)}</td></tr>`).join('') || '<tr><td colspan=3>None</td></tr>';
  const sendErr = Object.entries(d.send_errors).sort((a, b) => b[1] - a[1]).map(([k, n]) => `<tr><td>${esc(k)}</td><td class=n>${num(n)}</td></tr>`).join('') || '<tr><td colspan=2>None</td></tr>';
  const conv = d.converted.sort((a, b) => Number(b.paid) - Number(a.paid)).map((r) => `<tr><td>${esc(r.phone)}</td><td>${esc(r.name)}</td><td class=n>${r.tier}</td><td>${ist(r.viewed_at)}</td><td>${r.checkout ? 'yes' : ''}</td><td>${r.paid ? `<b>paid</b> ${ist(r.paid_at)}` : 'not yet'}</td></tr>`).join('') || '<tr><td colspan=6>No one has reached checkout yet</td></tr>';
  const os = d.offer_subscriptions;
  const notMsg = os.list.filter((s) => s.tier == null).map((s) => `<tr><td>${esc(s.phone)}</td><td>${esc(s.shop)}</td><td>${esc(s.status)}</td><td>${s.paid ? '<b>paid</b>' : ''}</td><td>${ist(s.at)}</td></tr>`).join('');
  const until = d.config.until ? ist(d.config.until) : '–';
  return `<!doctype html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
<meta http-equiv="refresh" content="60"><title>Campaign · ${esc(d.offer)}</title>
<style>
:root{color-scheme:light;--bg:#f6f6f4;--card:#fcfcfb;--ink:#0b0b0b;--ink2:#52514e;--muted:#7a7873;--line:#e2e1dc;--soft:#edebe6;--bar:#2a78d6;--good:#1a7f37;--link:#1c5cab}
@media (prefers-color-scheme:dark){:root{color-scheme:dark;--bg:#121211;--card:#1a1a19;--ink:#fff;--ink2:#c3c2b7;--muted:#8f8d86;--line:#33332f;--soft:#262623;--bar:#3987e5;--good:#4fbf6b;--link:#6da7ec}}
*{box-sizing:border-box}body{margin:0;background:var(--bg);color:var(--ink);font:15px/1.45 -apple-system,BlinkMacSystemFont,"Segoe UI",Inter,sans-serif;padding:16px}
.wrap{max-width:1000px;margin:0 auto}header{display:flex;align-items:baseline;gap:14px;flex-wrap:wrap;margin:8px 0 4px}h1{font-size:22px;margin:0}h2{font-size:17px;margin:28px 0 10px}
.meta{color:var(--muted);font-size:13px}a{color:var(--link);text-decoration:none;font-weight:600}
.tiles{display:grid;grid-template-columns:repeat(auto-fit,minmax(140px,1fr));gap:10px;margin-top:14px}.tile{background:var(--card);border:1px solid var(--line);border-radius:10px;padding:12px 14px}.tile .v{font-size:28px;font-weight:700;line-height:1;font-variant-numeric:tabular-nums}.tile .l{color:var(--ink2);font-size:12px;margin-top:6px}.tile.good .v{color:var(--good)}
.funnel{background:var(--card);border:1px solid var(--line);border-radius:10px;padding:12px 16px}.frow{display:grid;grid-template-columns:260px 1fr 70px 170px;gap:12px;align-items:center;padding:8px 0;border-bottom:1px solid var(--line)}.frow:last-child{border-bottom:none}.flabel{font-weight:600}.flabel small{display:block;font-weight:400;color:var(--muted);font-size:12px}.fbar{height:16px;background:var(--soft);border-radius:4px;overflow:hidden}.ffill{height:100%;background:var(--bar)}.fnum{text-align:right;font-weight:700;font-variant-numeric:tabular-nums}.fpct{color:var(--ink2);font-size:13px}.fprev{color:var(--muted)}
@media (max-width:640px){.frow{grid-template-columns:1fr 60px}.fbar,.fpct{grid-column:1/-1}}
.tw{overflow-x:auto;border:1px solid var(--line);border-radius:10px;background:var(--card)}table{border-collapse:collapse;width:100%;font-size:13.5px;min-width:560px}th,td{padding:7px 10px;text-align:left;border-bottom:1px solid var(--line);vertical-align:top}th{font-size:11px;letter-spacing:.05em;text-transform:uppercase;color:var(--muted);background:var(--soft)}td.n,th.n{text-align:right;font-variant-numeric:tabular-nums;white-space:nowrap}tr:last-child td{border-bottom:none}small{color:var(--muted)}
.note{background:var(--soft);border-radius:10px;padding:10px 14px;font-size:13.5px;color:var(--ink2);margin-top:10px}
</style></head><body><div class="wrap">
<header><h1>🪔 ${esc(d.offer)} campaign</h1><span class="meta">live from the database · refreshes every minute · ${ist(d.generated_at)} IST</span><a href="./" style="margin-left:auto">← Owners</a><a href="calls">Calls</a><a href="logout" class="meta">Sign out</a></header>
<div class="meta">WhatsApp template diwali_999_yearly from +91 98867 88977 · first send ${ist(d.first_send)} · last send ${ist(d.last_send)} · offer: yearly ₹${num(d.config.price)} on ${esc(d.config.planId)} until ${until} IST</div>
<div class="tiles">
<div class="tile"><div class="v">${num(T.n)}</div><div class="l">owners in the send list so far</div></div>
<div class="tile"><div class="v">${num(T.accepted)}</div><div class="l">accepted by Meta · ${num(T.retry)} waiting for the daily limit</div></div>
<div class="tile"><div class="v">${num(T.delivered)}</div><div class="l">delivered · ${pct(T.delivered, T.accepted)}</div></div>
<div class="tile"><div class="v">${num(T.read)}</div><div class="l">read · ${pct(T.read, T.accepted)}</div></div>
<div class="tile"><div class="v">${num(T.viewed)}</div><div class="l">opened Plans · ${pct(T.viewed, T.delivered)} of delivered</div></div>
<div class="tile"><div class="v">${num(T.checkout)}</div><div class="l">opened Razorpay</div></div>
<div class="tile good"><div class="v">${num(T.paid)}</div><div class="l">paid · ₹${num(T.paid * d.config.price)}</div></div>
</div>
<h2>Funnel</h2><div class="funnel">${funnel}</div>
<div class="note">Delivered and read arrive from Meta's status webhook, usually within minutes. "Opened the Plans screen" means that owner's app or the website loaded the offer price after their message was sent; an app that is merely opened also loads it, so treat it as "came back to the app". Taps on the WhatsApp button are not counted here (the link opens the app directly on Android).</div>
<h2>By tier</h2><div class="tw"><table><thead><tr><th>Tier</th><th>Who</th><th class=n>In list</th><th class=n>Accepted</th><th class=n>Delivered</th><th class=n>Read</th><th class=n>Opened Plans</th><th class=n>Checkout</th><th class=n>Paid</th><th class=n>Awaiting limit</th></tr></thead><tbody>${tiers}</tbody></table></div>
<h2>Owners who reached checkout</h2><div class="tw"><table><thead><tr><th>Phone</th><th>Shop</th><th class=n>Tier</th><th>Opened Plans</th><th>Checkout</th><th>Paid</th></tr></thead><tbody>${conv}</tbody></table></div>
<h2>Why messages did not arrive</h2><div class="tw"><table><thead><tr><th>Meta code</th><th>Meaning</th><th class=n>Owners</th></tr></thead><tbody>${waErr}</tbody></table></div>
<div class="tw" style="margin-top:10px"><table><thead><tr><th>Refused at send time</th><th class=n>Owners</th></tr></thead><tbody>${sendErr}</tbody></table></div>
<h2>Sends by hour</h2><div class="tw"><table><thead><tr><th>Hour</th><th class=n>Accepted</th></tr></thead><tbody>${hours}</tbody></table></div>
<h2>₹${num(d.config.price)} plan on Razorpay, everyone</h2>
<div class="note">${num(os.rows)} subscriptions created on the offer plan, ${num(os.paid)} paid; by status: ${esc(Object.entries(os.by_status).map(([k, v]) => `${k} ${v}`).join(', ') || 'none')}. ${num(os.not_messaged)} of them are owners who were not messaged (the offer shows to every shop without a plan while it runs).</div>
${notMsg ? `<div class="tw" style="margin-top:10px"><table><thead><tr><th>Not messaged, phone</th><th>Shop</th><th>Status</th><th></th><th>When</th></tr></thead><tbody>${notMsg}</tbody></table></div>` : ''}
<p class="meta" style="margin-top:28px">JSON: <a href="api/campaigns/${esc(d.offer)}">api/campaigns/${esc(d.offer)}</a></p>
</div></body></html>`;
}

module.exports = function mount(app, readOnly) {
  const offerOf = (req) => String(req.query.offer || req.params.offer || 'diwali999').replace(/[^a-z0-9_-]/gi, '').slice(0, 40) || 'diwali999';
  app.get('/campaigns', async (req, res, next) => {
    try { res.type('html').send(page(await readOnly((q) => campaignData(q, offerOf(req))))); } catch (e) { next(e); }
  });
  app.get('/api/campaigns/:offer', async (req, res, next) => {
    try { res.json(await readOnly((q) => campaignData(q, offerOf(req)))); } catch (e) { next(e); }
  });
};
module.exports.campaignData = campaignData;
