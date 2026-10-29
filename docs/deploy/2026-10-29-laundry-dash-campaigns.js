// WhatsApp campaign readout (first: the Diwali ₹999/year push, 28 Oct 2026).
//   GET /campaigns?offer=diwali999            summary: funnel, by tier, failures (DB on every load, refreshes every minute)
//   GET /campaigns/shops?offer=diwali999      every shop messaged: WhatsApp status + the Owners page's columns, filters, CSV
//   GET /campaigns/shop/:id?offer=diwali999   one shop: campaign timeline + the owner detail (opens in a new tab from the list)
//   GET /api/campaigns/:offer                 the summary as JSON
//   GET /api/campaigns/:offer/shops           the shop list as JSON
// Reads laundry_offer_sends (one row per owner messaged, written by the send
// script), laundry_whatsapp_messages (delivered / read / failed from Meta's
// webhook), laundry_offer_events (the shop's app or the website loaded the offer
// price) and laundry_subscriptions on the offer's Razorpay plan (checkout opened,
// paid). Owner columns come from server.js's listUsers / userDetail (passed in).
// Mounted from server.js: require('./campaigns')(app, readOnly, { listUsers, userDetail }).
'use strict';

const TIER_NAMES = {
  0: 'On a ₹2 trial', 1: 'Lapsed, or 3+ orders never paid', 2: 'Opened Razorpay, never finished', 3: 'Never paid, 1–2 orders',
  4: 'Set up, no order, active last 7 days', 5: 'Set up, no order, inactive', 6: 'Never set up, active last 7 days', 7: 'Never set up, inactive',
};
const META_ERRORS = {
  '131049': 'Meta per-person marketing cap (too many promos from businesses)', '130429': 'Our number\'s daily sending limit', '131026': 'Not on WhatsApp / can\'t receive',
  '130472': 'Number in a Meta experiment', '131047': 'Outside the 24-hour window', '131056': 'Too many messages to this number', '131053': 'Media upload error',
};
const STAGES = { paying: 'Paying', trial: '₹2 trial', initiated: 'Initiated only', lapsed: 'Lapsed', none: 'No subscription' };

function offerConfig(name) {
  try {
    const o = JSON.parse(process.env.LAUNDRY_PAYWALL_OFFER || 'null');
    if (o && o.name === name) return { planId: o.planIds && o.planIds.annual, price: o.prices && o.prices.annual, from: o.from, until: o.until, audience: o.audience || 'all' };
  } catch { /* fall through */ }
  return { planId: process.env.CAMPAIGN_PLAN_ID || 'plan_TtQFD2nbdDhSHq', price: 999, from: null, until: null, audience: '?' };
}

// ───────── data ─────────

async function sendRows(q, offer, cfg, template) {
  const sends = await q(
    `select s.phone, s.user_id, s.tier, s.name, s.batch, s.wa_message_id, s.status as send_status, s.error, s.sent_at, s.template,
            m.status as wa_status, m.error_code, m.error_title, m.wa_timestamp
       from laundry_offer_sends s
       left join laundry_whatsapp_messages m on m.wa_message_id = s.wa_message_id
      where ($1 = '' or s.template = $1)
      order by s.sent_at`, [template || '']);
  const userIds = [...new Set(sends.map((s) => s.user_id).filter(Boolean))];
  const views = userIds.length ? await q(
    `select e.user_id, min(e.at) as first_at, max(e.at) as last_at, count(*)::int as n
       from laundry_offer_events e join laundry_offer_sends s on s.user_id = e.user_id
      where e.offer = $1 and e.at >= s.sent_at group by e.user_id`, [offer]) : [];
  const subs = await q(
    `select sub.user_id, sub.status, sub.paid_count, sub.amount, sub.created_at, sub.updated_at, sub.razorpay_subscription_id, u.phone, sh.name as shop
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
    const wa = s.wa_status || (s.wa_message_id ? 'sent' : null);
    return {
      phone: s.phone, userId: s.user_id, name: s.name, tier: Number(s.tier), batch: s.batch, template: s.template, sentAt: s.sent_at,
      accepted: Boolean(s.wa_message_id), retry: s.send_status === 'retry', sendError: s.error,
      wa, waAt: s.wa_timestamp, waErrorCode: s.error_code, waError: s.error_code ? `${s.error_code} ${META_ERRORS[s.error_code] || s.error_title || ''}`.trim() : null,
      waLabel: s.retry ? 'waiting for daily limit' : !s.wa_message_id ? 'refused' : wa === 'failed' ? 'not delivered' : wa === 'sent' ? 'sent, not delivered yet' : wa,
      viewed: V.has(s.user_id), viewedAt: V.get(s.user_id)?.first_at || null, viewedLast: V.get(s.user_id)?.last_at || null, views: V.get(s.user_id)?.n || 0,
      checkout: ss.length > 0, checkoutAt: ss[0]?.created_at || null, paid: ss.some(paidRow), paidAt: (ss.find(paidRow) || {}).updated_at || null,
    };
  });
  return { sends, rows, subs, paidRow };
}

async function campaignData(q, offer, template) {
  const cfg = offerConfig(offer);
  const { sends, rows, subs, paidRow } = await sendRows(q, offer, cfg, template);
  const templates = (await q('select template, count(*)::int as n from laundry_offer_sends group by template order by min(sent_at)')).map((t) => ({ name: t.template, n: t.n }));
  const agg = (g) => ({
    n: g.length, accepted: g.filter((r) => r.accepted).length, retry: g.filter((r) => r.retry).length, refused: g.filter((r) => !r.accepted && !r.retry).length,
    delivered: g.filter((r) => ['delivered', 'read'].includes(r.wa)).length, read: g.filter((r) => r.wa === 'read').length, pending: g.filter((r) => r.wa === 'sent').length,
    undelivered: g.filter((r) => r.wa === 'failed').length, viewed: g.filter((r) => r.viewed).length, checkout: g.filter((r) => r.checkout).length, paid: g.filter((r) => r.paid).length,
  });
  const byTier = {};
  rows.forEach((r) => { (byTier[r.tier] ??= []).push(r); });
  const byHour = {};
  rows.filter((r) => r.accepted).forEach((r) => { const k = istHour(r.sentAt); byHour[k] = (byHour[k] || 0) + 1; });
  const waErrors = {};
  rows.filter((r) => r.wa === 'failed').forEach((r) => { const k = r.waErrorCode || '?'; waErrors[k] = (waErrors[k] || 0) + 1; });
  const sendErrors = {};
  rows.filter((r) => !r.accepted).forEach((r) => { const k = (r.sendError || '(none)').slice(0, 90); sendErrors[k] = (sendErrors[k] || 0) + 1; });
  const messagedUsers = new Set(sends.map((s) => s.user_id));
  const firstSend = sends.length ? sends[0].sent_at : new Date();
  const inbound = await q(
    `select m.phone, m.type, m.body, coalesce(m.wa_timestamp, m.created_at) as at, s.user_id, s.tier, s.sent_at, s.name as greeting
       from laundry_whatsapp_messages m join laundry_offer_sends s on s.phone = m.phone
      where m.direction = 'inbound' and coalesce(m.wa_timestamp, m.created_at) >= $1 order by 4 desc limit 300`, [firstSend]);
  const replies = inbound.map((r) => ({ phone: r.phone, userId: r.user_id, tier: Number(r.tier), name: r.greeting, at: r.at, type: r.type, body: (r.body || '').replace(/\s+/g, ' ').trim(),
    auto: looksAutomatic(r.body, new Date(r.at) - new Date(r.sent_at)) }));
  return {
    offer, template: template || '', templates, generated_at: new Date().toISOString(), config: cfg, replies,
    reply_summary: { total: replies.length, owners: new Set(replies.map((r) => r.phone)).size, likely_human: replies.filter((r) => !r.auto).length },
    first_send: sends.length ? sends[0].sent_at : null, last_send: sends.length ? sends[sends.length - 1].sent_at : null,
    total: agg(rows), by_tier: Object.fromEntries(Object.entries(byTier).map(([t, g]) => [t, agg(g)])),
    by_hour: byHour, wa_errors: waErrors, send_errors: sendErrors,
    offer_subscriptions: {
      rows: subs.length, paid: subs.filter(paidRow).length, by_status: subs.reduce((a, s) => (a[s.status] = (a[s.status] || 0) + 1, a), {}),
      not_messaged: subs.filter((s) => !messagedUsers.has(s.user_id)).length,
      list: subs.map((s) => ({ userId: s.user_id, phone: String(s.phone || '').slice(-10), shop: s.shop || '', status: s.status, paid: paidRow(s), tier: (sends.find((x) => x.user_id === s.user_id) || {}).tier ?? null, at: s.created_at })),
    },
    converted: rows.filter((r) => r.paid || r.checkout).map((r) => ({ userId: r.userId, phone: r.phone, name: r.name, tier: r.tier, viewedAt: r.viewedAt, checkout: r.checkout, paid: r.paid, paidAt: r.paidAt })),
  };
}

// Every messaged shop with the Owners page's columns attached.
async function shopsData(q, offer, listUsers, template) {
  const cfg = offerConfig(offer);
  const { rows } = await sendRows(q, offer, cfg, template);
  const owners = await listUsers(q);
  const O = new Map(owners.map((o) => [o.id, o]));
  const byPhone = new Map(owners.map((o) => [String(o.phone || '').slice(-10), o]));
  return {
    offer, config: cfg, generated_at: new Date().toISOString(),
    shops: rows.map((r) => {
      const o = (r.userId && O.get(r.userId)) || byPhone.get(r.phone) || {};
      return {
        ...r, userId: r.userId || o.id || null,
        owner: o.name || null, shopName: o.shopName || null, createdAt: o.createdAt || null, lastLoginAt: o.lastLoginAt || null, isTest: Boolean(o.isTest),
        stage: o.stage || null, paywallVariant: o.paywallVariant || null, plan: o.plan || null, status: o.status || null, initiatedPlans: o.initiatedPlans || [],
        subCreatedAt: o.subCreatedAt || null, accessUntil: o.accessUntil || null, subsCount: o.subsCount || 0, retentionMonths: o.retentionMonths || 0,
        revenuePaise: o.revenuePaise || 0, failedPayments: o.failedPayments || 0, services: o.services || 0, customers: o.customers || 0,
        orders: o.orders || 0, ordersTotal: o.ordersTotal || 0, ordersDeleted: o.ordersDeleted || 0, lastOrderSync: o.lastOrderSync || null,
        callCount: o.callCount || 0, lastCallAt: o.lastCallAt || null, lastOutcome: o.lastOutcome || null, lastNote: o.lastNote || '', callTagNames: o.callTagNames || [],
      };
    }),
  };
}

// One shop: the campaign trail + the owner detail.
async function shopData(q, offer, id, userDetail) {
  const cfg = offerConfig(offer);
  const detail = await userDetail(q, id);
  const inbound = await q(`select m.type, m.body, coalesce(m.wa_timestamp, m.created_at) as at from laundry_whatsapp_messages m join laundry_offer_sends s on s.phone = m.phone where s.user_id = $1 and m.direction = 'inbound' and coalesce(m.wa_timestamp, m.created_at) >= s.sent_at order by 3`, [id]);
  const sends = await q(`select s.*, m.status as wa_status, m.error_code, m.error_title, m.wa_timestamp, m.created_at as wa_created
         from laundry_offer_sends s left join laundry_whatsapp_messages m on m.wa_message_id = s.wa_message_id where s.user_id = $1 order by s.sent_at`, [id]);
  const views = await q('select kind, at from laundry_offer_events where offer = $1 and user_id = $2 order by at', [offer, id]);
  const subs = await q('select id, status, paid_count, amount, created_at, updated_at, razorpay_subscription_id from laundry_subscriptions where plan_id = $1 and user_id = $2 order by created_at', [cfg.planId, id]);
  if (!detail) return null;
  return { offer, config: cfg, detail, sends, views, offerSubs: subs, inbound };
}

// A WhatsApp Business greeting / away message: boilerplate text arriving right after our message.
const AUTO_RE = /thank(s| you) for (contacting|reaching|your message|messaging|connecting|visit)|welcome to|we.?re (unavailable|not available|done for the day|closed)|not available right now|will (respond|reply|get back)|reply with|option number|our timings|working hours|abhi hum available nahi|aapka message mil gaya|swagat|स्वागत|आपका संदेश|धन्यवाद/i;
function looksAutomatic(body, msSinceSend) {
  const b = String(body || '');
  if (AUTO_RE.test(b)) return true;
  return msSinceSend >= 0 && msSinceSend < 3 * 60e3 && b.length > 40; // long text within 3 minutes of delivery = a bot
}

// ───────── rendering ─────────

const IST = { timeZone: 'Asia/Kolkata' };
const istHour = (d) => new Date(d).toLocaleString('en-IN', { ...IST, day: '2-digit', month: 'short', hour: '2-digit', hour12: false }).replace(',', '') + ':00';
const ist = (d) => (d ? new Date(d).toLocaleString('en-IN', { ...IST, day: '2-digit', month: 'short', hour: '2-digit', minute: '2-digit', hour12: false }) : '–');
const istD = (d) => (d ? new Date(d).toLocaleString('en-IN', { ...IST, day: '2-digit', month: 'short', year: '2-digit' }) : '–');
const pct = (a, b) => (b ? `${Math.round((100 * a) / b)}%` : '–');
const esc = (s) => String(s ?? '').replace(/[&<>"]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]));
const num = (n) => Number(n || 0).toLocaleString('en-IN');
const inr = (p) => '₹' + Math.round(Number(p || 0) / 100).toLocaleString('en-IN');

const CSS = `
:root{color-scheme:light;--bg:#f6f6f4;--card:#fcfcfb;--ink:#0b0b0b;--ink2:#52514e;--muted:#7a7873;--line:#e2e1dc;--soft:#edebe6;--bar:#2a78d6;--good:#1a7f37;--bad:#b3261e;--warn:#9a6700;--link:#1c5cab}
@media (prefers-color-scheme:dark){:root{color-scheme:dark;--bg:#121211;--card:#1a1a19;--ink:#fff;--ink2:#c3c2b7;--muted:#8f8d86;--line:#33332f;--soft:#262623;--bar:#3987e5;--good:#4fbf6b;--bad:#ff8a80;--warn:#f0c674;--link:#6da7ec}}
*{box-sizing:border-box}body{margin:0;background:var(--bg);color:var(--ink);font:14.5px/1.45 -apple-system,BlinkMacSystemFont,"Segoe UI",Inter,sans-serif;padding:14px 16px 40px}
header{display:flex;align-items:baseline;gap:14px;flex-wrap:wrap;margin:4px 0 6px}h1{font-size:21px;margin:0}h2{font-size:16px;margin:26px 0 10px}
.meta{color:var(--muted);font-size:13px}a{color:var(--link);text-decoration:none;font-weight:600}a.sub{font-weight:400;color:var(--muted)}
.tiles{display:grid;grid-template-columns:repeat(auto-fit,minmax(140px,1fr));gap:10px;margin-top:12px}.tile{background:var(--card);border:1px solid var(--line);border-radius:10px;padding:12px 14px}.tile .v{font-size:26px;font-weight:700;line-height:1;font-variant-numeric:tabular-nums}.tile .l{color:var(--ink2);font-size:12px;margin-top:6px}.tile.good .v{color:var(--good)}
.funnel{background:var(--card);border:1px solid var(--line);border-radius:10px;padding:12px 16px}.frow{display:grid;grid-template-columns:260px 1fr 70px 170px;gap:12px;align-items:center;padding:8px 0;border-bottom:1px solid var(--line)}.frow:last-child{border-bottom:none}.flabel{font-weight:600}.flabel small{display:block;font-weight:400;color:var(--muted);font-size:12px}.fbar{height:16px;background:var(--soft);border-radius:4px;overflow:hidden}.ffill{height:100%;background:var(--bar)}.fnum{text-align:right;font-weight:700;font-variant-numeric:tabular-nums}.fpct{color:var(--ink2);font-size:13px}.fprev{color:var(--muted)}
@media (max-width:640px){.frow{grid-template-columns:1fr 60px}.fbar,.fpct{grid-column:1/-1}}
.tw{overflow-x:auto;border:1px solid var(--line);border-radius:10px;background:var(--card)}table{border-collapse:collapse;width:100%;font-size:13px;min-width:560px}th,td{padding:6px 9px;text-align:left;border-bottom:1px solid var(--line);vertical-align:top;white-space:nowrap}th{font-size:11px;letter-spacing:.04em;text-transform:uppercase;color:var(--muted);background:var(--soft);position:sticky;top:0;cursor:pointer;user-select:none}td.n,th.n{text-align:right;font-variant-numeric:tabular-nums}tr:last-child td{border-bottom:none}small,.muted{color:var(--muted)}td.wrap{white-space:normal;min-width:180px}
.note{background:var(--soft);border-radius:10px;padding:10px 14px;font-size:13px;color:var(--ink2);margin-top:10px}
.pill{display:inline-block;padding:1px 8px;border-radius:999px;font-size:12px;font-weight:600;background:var(--soft);color:var(--ink2)}.pill.good{background:rgba(26,127,55,.12);color:var(--good)}.pill.bad{background:rgba(179,38,30,.12);color:var(--bad)}.pill.warn{background:rgba(154,103,0,.12);color:var(--warn)}.pill.info{background:rgba(42,120,214,.12);color:var(--link)}
.filters{display:flex;gap:8px;flex-wrap:wrap;align-items:center;margin:12px 0}.filters select,.filters input{font:inherit;color:var(--ink);background:var(--card);border:1px solid var(--line);border-radius:8px;padding:6px 8px}.filters button{font:inherit;font-weight:600;border:1px solid var(--line);border-radius:8px;padding:6px 10px;background:var(--card);color:var(--ink);cursor:pointer}
tbody tr.row{cursor:pointer}tbody tr.row:hover td{background:var(--soft)}
.grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(300px,1fr));gap:12px}.card{background:var(--card);border:1px solid var(--line);border-radius:10px;padding:12px 14px}.card h3{margin:0 0 8px;font-size:14px}dl{display:grid;grid-template-columns:max-content 1fr;gap:4px 12px;margin:0;font-size:13.5px}dt{color:var(--muted)}dd{margin:0}
.tl{list-style:none;padding:0;margin:0}.tl li{display:grid;grid-template-columns:130px 1fr;gap:10px;padding:5px 0;border-bottom:1px solid var(--line);font-size:13.5px}.tl li:last-child{border-bottom:none}.tl time{color:var(--muted);font-variant-numeric:tabular-nums}
`;
const head = (title, extra = '') => `<!doctype html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">${extra}<title>${esc(title)}</title><style>${CSS}</style></head><body>`;
const nav = (offer, here, d) => `<header><h1>🪔 ${esc(offer)}</h1><span class="meta">${here}</span>${d && d.templates && d.templates.length > 1 ? `<span class="meta">template: ${d.templates.map((t) => `<a href="?offer=${esc(offer)}&template=${esc(t.name)}" ${d.template === t.name ? 'style="text-decoration:underline"' : ''}>${esc(t.name)}</a> (${t.n})`).join(' · ')} · <a href="?offer=${esc(offer)}&template=" ${!d.template ? 'style="text-decoration:underline"' : ''}>all</a></span>` : ''}<a href="/campaigns?offer=${esc(offer)}" style="margin-left:auto">Summary</a><a href="/campaigns/shops?offer=${esc(offer)}">Shops messaged</a><a href="/" class="sub">Owners</a><a href="/calls" class="sub">Calls</a><a href="/logout" class="sub">Sign out</a></header>`;

function summaryPage(d) {
  const T = d.total;
  const steps = [
    ['Messages accepted by Meta', T.accepted, 'sent from +91 98867 88977'], ['Delivered', T.delivered, 'Meta status webhook'], ['Read', T.read, 'blue ticks'],
    ['Opened the Plans screen', T.viewed, 'their app / the website loaded the ₹' + d.config.price + ' price after the message'],
    ['Opened Razorpay checkout', T.checkout, 'a subscription on the offer plan was created'], ['Paid ₹' + d.config.price, T.paid, '₹' + num(T.paid * d.config.price) + ' collected'],
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
  const link = (r) => r.userId ? `<a href="/campaigns/shop/${esc(r.userId)}?offer=${esc(d.offer)}" target="_blank" rel="noopener">${esc(r.phone)}</a>` : esc(r.phone);
  const conv = d.converted.sort((a, b) => Number(b.paid) - Number(a.paid)).map((r) => `<tr><td>${link(r)}</td><td>${esc(r.name)}</td><td class=n>${r.tier}</td><td>${ist(r.viewedAt)}</td><td>${r.checkout ? 'yes' : ''}</td><td>${r.paid ? `<b>paid</b> ${ist(r.paidAt)}` : 'not yet'}</td></tr>`).join('') || '<tr><td colspan=6>No one has reached checkout yet</td></tr>';
  const os = d.offer_subscriptions;
  const notMsg = os.list.filter((s) => s.tier == null).map((s) => `<tr><td>${link(s)}</td><td>${esc(s.shop)}</td><td>${esc(s.status)}</td><td>${s.paid ? '<b>paid</b>' : ''}</td><td>${ist(s.at)}</td></tr>`).join('');
  return `${head('Campaign · ' + d.offer, '<meta http-equiv="refresh" content="60">')}
${nav(d.offer, 'live from the database · refreshes every minute · ' + ist(d.generated_at) + ' IST', d)}
<div class="meta">WhatsApp template diwali_999_yearly from +91 98867 88977 · first send ${ist(d.first_send)} · last send ${ist(d.last_send)} · offer: yearly ₹${num(d.config.price)} on ${esc(d.config.planId)} until ${d.config.until ? ist(d.config.until) : '–'} IST · shown to: ${d.config.audience === 'messaged' ? 'messaged owners only' : 'every shop without a plan'}</div>
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
<div class="note">Delivered and read arrive from Meta's status webhook, usually within minutes. "Opened the Plans screen" means that owner's app or the website loaded the offer price after their message was sent; an app that is merely opened also loads it, so treat it as "came back to the app". The full list of shops with every status is on <a href="/campaigns/shops?offer=${esc(d.offer)}">Shops messaged</a>.</div>
<h2>By tier</h2><div class="tw"><table><thead><tr><th>Tier</th><th>Who</th><th class=n>In list</th><th class=n>Accepted</th><th class=n>Delivered</th><th class=n>Read</th><th class=n>Opened Plans</th><th class=n>Checkout</th><th class=n>Paid</th><th class=n>Awaiting limit</th></tr></thead><tbody>${tiers}</tbody></table></div>
<h2>Owners who reached checkout</h2><div class="tw"><table><thead><tr><th>Phone</th><th>Shop</th><th class=n>Tier</th><th>Opened Plans</th><th>Checkout</th><th>Paid</th></tr></thead><tbody>${conv}</tbody></table></div>
<h2>Why messages did not arrive</h2><div class="tw"><table><thead><tr><th>Meta code</th><th>Meaning</th><th class=n>Owners</th></tr></thead><tbody>${waErr}</tbody></table></div>
<div class="tw" style="margin-top:10px"><table><thead><tr><th>Refused at send time</th><th class=n>Owners</th></tr></thead><tbody>${sendErr}</tbody></table></div>
<h2>Replies on WhatsApp <small>${num(d.reply_summary.total)} messages from ${num(d.reply_summary.owners)} owners · ${num(d.reply_summary.likely_human)} look like a person</small></h2>
<div class="tw"><table><thead><tr><th>When</th><th>Phone</th><th>Shop</th><th class=n>Tier</th><th>Kind</th><th>Message</th></tr></thead><tbody>${d.replies.map((r) => `<tr><td>${ist(r.at)}</td><td>${r.userId ? `<a href="/campaigns/shop/${esc(r.userId)}?offer=${esc(d.offer)}" target="_blank" rel="noopener">${esc(r.phone)}</a>` : esc(r.phone)}</td><td>${esc(r.name)}</td><td class=n>${r.tier}</td><td>${r.auto ? '<span class="pill">auto-reply</span>' : '<span class="pill good">person</span>'}</td><td class=wrap style="min-width:320px">${r.type === 'text' ? esc(r.body.slice(0, 240)) : '<i>' + esc(r.type) + '</i> ' + esc(r.body.slice(0, 120))}</td></tr>`).join('') || '<tr><td colspan=6 class="muted">No replies yet</td></tr>'}</tbody></table></div>
<div class="note">Replies land on the My Laundry App number (+91 98867 88977), which nobody answers from the app. "auto-reply" = a WhatsApp Business greeting or away message from the shop's own account; "person" = anything else. Messages are shown newest first, up to 300.</div>
<h2>Sends by hour</h2><div class="tw"><table><thead><tr><th>Hour</th><th class=n>Accepted</th></tr></thead><tbody>${hours}</tbody></table></div>
<h2>₹${num(d.config.price)} plan on Razorpay, everyone</h2>
<div class="note">${num(os.rows)} subscriptions created on the offer plan, ${num(os.paid)} paid; by status: ${esc(Object.entries(os.by_status).map(([k, v]) => `${k} ${v}`).join(', ') || 'none')}. ${num(os.not_messaged)} of them are owners who were not messaged.</div>
${notMsg ? `<div class="tw" style="margin-top:10px"><table><thead><tr><th>Not messaged, phone</th><th>Shop</th><th>Status</th><th></th><th>When</th></tr></thead><tbody>${notMsg}</tbody></table></div>` : ''}
<p class="meta" style="margin-top:28px">JSON: <a href="/api/campaigns/${esc(d.offer)}">summary</a> · <a href="/api/campaigns/${esc(d.offer)}/shops">shops</a></p>
</body></html>`;
}

function shopsPage(d) {
  const json = JSON.stringify(d.shops).replace(/</g, '\\u003c');
  return `${head('Shops messaged · ' + d.offer)}
${nav(d.offer, `${num(d.shops.length)} shops · live · ${ist(d.generated_at)} IST`)}
<div class="filters">
  <input id="q" placeholder="Search phone / owner / shop" size="26">
  <select id="fwa"><option value="">WhatsApp: all</option><option value="read">read</option><option value="delivered">delivered (incl. read)</option><option value="sent">sent, not delivered yet</option><option value="failed">not delivered</option><option value="retry">waiting for daily limit</option><option value="refused">refused</option></select>
  <select id="ftier"><option value="">Tier: all</option>${Object.keys(TIER_NAMES).map((t) => `<option value="${t}">${t} · ${esc(TIER_NAMES[t])}</option>`).join('')}</select>
  <select id="fbatch"><option value="">Batch: all</option>${[...new Set(d.shops.map((s) => s.batch))].sort().map((b) => `<option>${esc(b)}</option>`).join('')}</select>
  <select id="fstage"><option value="">Stage: all</option>${Object.entries(STAGES).map(([k, v]) => `<option value="${k}">${esc(v)}</option>`).join('')}</select>
  <select id="fact"><option value="">Response: all</option><option value="viewed">opened Plans</option><option value="checkout">opened Razorpay</option><option value="paid">paid</option><option value="none">no response yet</option></select>
  <select id="forders"><option value="">Orders: all</option><option value="0">0</option><option value="1-2">1–2</option><option value="3+">3+</option></select>
  <label><input type="checkbox" id="ftest"> hide test phones</label>
  <button id="csv">Download CSV</button>
  <span class="meta" id="count"></span>
</div>
<div class="tw"><table id="t"><thead></thead><tbody></tbody></table></div>
<div class="note">Click a row to open that shop in a new tab: the message trail, the ₹${num(d.config.price)} attempts and the same detail the Owners page shows. Columns sort on click.</div>
<script>
const OFFER=${JSON.stringify(d.offer)}, ROWS=${json}, STAGES=${JSON.stringify(STAGES)}, TIERS=${JSON.stringify(TIER_NAMES)};
const IST={timeZone:'Asia/Kolkata'};
const ist=(v)=>v?new Date(v).toLocaleString('en-IN',{...IST,day:'2-digit',month:'short',hour:'2-digit',minute:'2-digit',hour12:false}):'–';
const istD=(v)=>v?new Date(v).toLocaleString('en-IN',{...IST,day:'2-digit',month:'short',year:'2-digit'}):'–';
const esc=(s)=>String(s??'').replace(/[&<>"]/g,(c)=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;'}[c]));
const inr=(p)=>'₹'+Math.round((p||0)/100).toLocaleString('en-IN');
const waPill=(r)=>r.retry?'<span class="pill warn">waiting for limit</span>':!r.accepted?'<span class="pill bad">refused</span>':r.wa==='read'?'<span class="pill good">read</span>':r.wa==='delivered'?'<span class="pill info">delivered</span>':r.wa==='failed'?'<span class="pill bad">not delivered</span><div class="muted" style="white-space:normal;max-width:220px">'+esc(r.waError||'')+'</div>':'<span class="pill">sent</span>';
const COLS=[
 ['phone','Phone',(r)=>esc(r.phone)+(r.isTest?' <small>test</small>':'')],
 ['owner','Owner / shop',(r)=>esc(r.owner||'—')+'<div class="muted">'+esc(r.shopName||'no shop set up')+'</div>'],
 ['tier','Tier',(r)=>r.tier+' <small>'+esc(TIERS[r.tier]||'')+'</small>'],
 ['batch','Batch',(r)=>esc(r.batch)],
 ['template','Template',(r)=>esc(r.template||'')],
 ['sentAt','Sent',(r)=>ist(r.sentAt)],
 ['waLabel','WhatsApp',(r)=>waPill(r)],
 ['waAt','Status at',(r)=>ist(r.waAt)],
 ['viewedAt','Opened Plans',(r)=>r.viewed?ist(r.viewedAt)+(r.views>1?' <small>×'+r.views+'</small>':''):'<span class="muted">—</span>'],
 ['checkoutAt','Razorpay',(r)=>r.checkout?ist(r.checkoutAt):'<span class="muted">—</span>'],
 ['paid','Paid ₹${num(d.config.price)}',(r)=>r.paid?'<span class="pill good">paid '+ist(r.paidAt)+'</span>':'<span class="muted">—</span>'],
 ['createdAt','Joined',(r)=>istD(r.createdAt)],
 ['stage','Stage',(r)=>r.stage?'<span class="pill">'+esc(STAGES[r.stage]||r.stage)+'</span>':'—'],
 ['paywallVariant','Paywall test',(r)=>esc(r.paywallVariant||'—')],
 ['plan','Plan',(r)=>esc(r.plan||'—')],
 ['status','Razorpay status',(r)=>esc(r.status||'—')],
 ['initiatedPlans','Plans tried',(r)=>esc((r.initiatedPlans||[]).join(', ')||'—')],
 ['subCreatedAt','Sub started',(r)=>istD(r.subCreatedAt)],
 ['accessUntil','Access until',(r)=>istD(r.accessUntil)],
 ['retentionMonths','Paid months',(r)=>r.retentionMonths,1],
 ['revenuePaise','Paid to us',(r)=>r.revenuePaise?inr(r.revenuePaise):'—',1],
 ['failedPayments','Failed pays',(r)=>r.failedPayments||'',1],
 ['services','Services',(r)=>r.services,1],
 ['customers','Customers',(r)=>r.customers,1],
 ['ordersTotal','Orders (total)',(r)=>r.ordersTotal+(r.ordersDeleted?'<div class="muted">'+r.ordersDeleted+' deleted</div>':''),1],
 ['lastLoginAt','Last login',(r)=>ist(r.lastLoginAt)],
 ['callCount','Calls',(r)=>r.callCount||'',1],
 ['lastOutcome','Last call',(r)=>r.lastCallAt?ist(r.lastCallAt)+' <small>'+esc(r.lastOutcome||'')+'</small>':'<span class="muted">—</span>'],
 ['callTagNames','Call tags',(r)=>esc((r.callTagNames||[]).join(', '))],
 ['lastNote','Last note',(r)=>esc((r.lastNote||'').slice(0,90))],
];
let sort={k:'sentAt',dir:-1};
const $=(s)=>document.querySelector(s);
function filtered(){
  const q=$('#q').value.trim().toLowerCase(), wa=$('#fwa').value, tier=$('#ftier').value, batch=$('#fbatch').value, stage=$('#fstage').value, act=$('#fact').value, ord=$('#forders').value, hideTest=$('#ftest').checked;
  return ROWS.filter((r)=>{
    if(q && !(r.phone+' '+(r.owner||'')+' '+(r.shopName||'')+' '+(r.name||'')).toLowerCase().includes(q)) return false;
    if(wa==='read'&&r.wa!=='read') return false; if(wa==='delivered'&&!['delivered','read'].includes(r.wa)) return false; if(wa==='sent'&&!(r.accepted&&r.wa==='sent')) return false;
    if(wa==='failed'&&r.wa!=='failed') return false; if(wa==='retry'&&!r.retry) return false; if(wa==='refused'&&(r.accepted||r.retry)) return false;
    if(tier!==''&&String(r.tier)!==tier) return false; if(batch&&r.batch!==batch) return false; if(stage&&r.stage!==stage) return false;
    if(act==='viewed'&&!r.viewed) return false; if(act==='checkout'&&!r.checkout) return false; if(act==='paid'&&!r.paid) return false; if(act==='none'&&(r.viewed||r.checkout||r.paid)) return false;
    if(ord==='0'&&r.ordersTotal!==0) return false; if(ord==='1-2'&&!(r.ordersTotal>=1&&r.ordersTotal<=2)) return false; if(ord==='3+'&&r.ordersTotal<3) return false;
    if(hideTest&&r.isTest) return false;
    return true;
  });
}
function val(r,k){const v=r[k]; return Array.isArray(v)?v.join(','):(v??'');}
function render(){
  const rows=filtered().sort((a,b)=>{const x=val(a,sort.k),y=val(b,sort.k); return (x>y?1:x<y?-1:0)*sort.dir;});
  $('#t thead').innerHTML='<tr>'+COLS.map(([k,t,,n])=>'<th data-k="'+k+'" class="'+(n?'n':'')+'">'+t+(sort.k===k?(sort.dir>0?' ▲':' ▼'):'')+'</th>').join('')+'</tr>';
  $('#t tbody').innerHTML=rows.map((r)=>'<tr class="row" data-id="'+esc(r.userId||'')+'">'+COLS.map(([k,,f,n])=>'<td class="'+(n?'n':'')+'">'+f(r)+'</td>').join('')+'</tr>').join('');
  $('#count').textContent=rows.length+' of '+ROWS.length+' shops';
  document.querySelectorAll('#t th').forEach((th)=>th.onclick=()=>{const k=th.dataset.k; sort=sort.k===k?{k,dir:-sort.dir}:{k,dir:-1}; render();});
  document.querySelectorAll('#t tbody tr.row').forEach((tr)=>tr.onclick=()=>{ if(tr.dataset.id) window.open('/campaigns/shop/'+tr.dataset.id+'?offer='+encodeURIComponent(OFFER),'_blank','noopener'); });
}
['#q','#fwa','#ftier','#fbatch','#fstage','#fact','#forders','#ftest'].forEach((s)=>$(s).addEventListener('input',render));
$('#csv').onclick=()=>{
  const rows=filtered(); const keys=['phone','owner','shopName','tier','batch','sentAt','waLabel','waError','waAt','viewedAt','views','checkoutAt','paid','paidAt','createdAt','stage','paywallVariant','plan','status','subCreatedAt','accessUntil','retentionMonths','revenuePaise','failedPayments','services','customers','orders','ordersTotal','lastLoginAt','callCount','lastCallAt','lastOutcome','lastNote'];
  const csv=[keys.join(',')].concat(rows.map((r)=>keys.map((k)=>{const v=r[k]; const s=v==null?'':Array.isArray(v)?v.join('|'):String(v); return '"'+s.replace(/"/g,'""')+'"';}).join(','))).join('\\n');
  const a=document.createElement('a'); a.href=URL.createObjectURL(new Blob([csv],{type:'text/csv'})); a.download=OFFER+'-shops.csv'; a.click();
};
render();
</script></body></html>`;
}

function shopPage(d) {
  const u = d.detail.user, shop = d.detail.shop || {};
  const trail = [];
  for (const s of d.sends) {
    trail.push([s.sent_at, s.wa_message_id ? `Message sent: ${esc(s.template || '')} (${esc(s.batch)}, tier ${s.tier}, greeting "${esc(s.name)}")` : s.status === 'retry' ? 'Send attempted, rejected by the daily limit, queued for retry' : `Send refused: ${esc(s.error || '')}`]);
    if (s.wa_status && s.wa_status !== 'sent') trail.push([s.wa_timestamp || s.wa_created, s.wa_status === 'failed' ? `Not delivered: ${esc(s.error_code)} ${esc(META_ERRORS[s.error_code] || s.error_title || '')}` : `WhatsApp: ${esc(s.wa_status)}`]);
  }
  for (const v of d.views) trail.push([v.at, 'Opened the app / Plans: the ₹' + num(d.config.price) + ' price was loaded']);
  for (const s of d.offerSubs) {
    trail.push([s.created_at, `Razorpay checkout opened for ₹${num(d.config.price)} (${esc(s.razorpay_subscription_id || s.id)})`]);
    if (s.status === 'active' || Number(s.paid_count || 0) > 0) trail.push([s.updated_at, `<b>Paid ₹${num(d.config.price)}</b>`]);
    else if (s.status !== 'created') trail.push([s.updated_at, `Checkout ended: ${esc(s.status)}`]);
  }
  for (const m of d.inbound) trail.push([m.at, `Replied on WhatsApp${looksAutomatic(m.body, 1) ? ' <span class="pill">auto-reply</span>' : ' <span class="pill good">person</span>'}: ${m.type === 'text' ? esc((m.body || '').replace(/\s+/g, ' ').slice(0, 300)) : '<i>' + esc(m.type) + '</i>'}`]);
  trail.sort((a, b) => new Date(a[0]) - new Date(b[0]));
  const tl = trail.map(([t, what]) => `<li><time>${ist(t)}</time><span>${what}</span></li>`).join('') || '<li><span class="muted">No campaign events for this shop</span></li>';
  const subs = d.detail.subscriptions.map((s) => `<tr><td>${esc(s.plan)}</td><td>${esc(s.status)} <small>${esc(STAGES[s.stage] || '')}</small></td><td class=n>${inr(s.amount)}</td><td class=n>${s.paid_count || 0}</td><td>${istD(s.created_at)}</td><td>${istD(s.accessUntil)}</td><td class=wrap>${s.payments.map((p) => `${esc(p.status)} ${inr(p.amount)} ${esc(p.method || '')} ${p.error_description ? '<small>' + esc(p.error_description) + '</small>' : ''}`).join('<br>') || '<span class="muted">no payment attempts</span>'}</td></tr>`).join('') || '<tr><td colspan=7 class="muted">Never opened a checkout</td></tr>';
  const orders = d.detail.orders.slice(0, 25).map((o) => `<tr><td>#${o.id}</td><td>${esc(o.customer)}</td><td>${esc(o.status)}</td><td>${istD(o.createdOn)}</td><td class=n>₹${num(o.amount)}</td><td class=n>₹${num(o.paid)}</td><td>${o.billSent ? 'yes' : ''}</td><td class=wrap>${esc(o.services)}</td></tr>`).join('') || '<tr><td colspan=8 class="muted">No orders</td></tr>';
  const custs = d.detail.customers.slice(0, 15).map((c) => `<tr><td>${esc(c.name)}</td><td>${esc(c.phone)}</td><td class=n>${c.orders}</td><td class=n>₹${num(c.billed)}</td><td class=n>₹${num(c.balance)}</td><td>${istD(c.lastOrder)}</td></tr>`).join('') || '<tr><td colspan=6 class="muted">No customers</td></tr>';
  const calls = d.detail.calls.map((c) => `<tr><td>${ist(c.startedAt)}</td><td>${esc(c.agent || '')}</td><td>${esc(c.outcome || '')}</td><td class=n>${c.durationSec ? Math.round(c.durationSec / 60) + ' min' : ''}</td><td class=wrap>${esc(c.note || '')}</td><td>${(c.tags || []).map((t) => esc(t.name)).join(', ')}</td></tr>`).join('') || '<tr><td colspan=6 class="muted">Never called</td></tr>';
  const e = d.detail.earnings || {};
  const earn = Object.values(e).map((r) => `<dt>${esc(r.label)}</dt><dd>₹${num(r.sales ?? r.billed ?? r.total ?? 0)} <small>${r.orders != null ? r.orders + ' orders' : ''}</small></dd>`).join('');
  const sc = Object.entries(d.detail.statusCounts || {}).map(([k, v]) => `${esc(k)} ${v}`).join(' · ');
  return `${head((shop.name || u.name || u.phone) + ' · ' + d.offer)}
${nav(d.offer, 'one shop · ' + ist(new Date()) + ' IST')}
<h2 style="margin-top:8px">${esc(shop.name || 'No shop name')} <span class="meta">${esc(u.phone)}${u.isTest ? ' · test phone' : ''} · owner ${esc(u.name || '—')} · joined ${istD(u.created_at)} · last login ${ist(u.last_login_at)}</span></h2>
<div class="grid">
<div class="card"><h3>Campaign trail</h3><ul class="tl">${tl}</ul></div>
<div class="card"><h3>Shop</h3><dl><dt>Shop phone</dt><dd>${esc(shop.phone || '—')}</dd><dt>Services</dt><dd>${d.detail.services.length}: ${esc(d.detail.services.map((s) => s.name).join(', '))}</dd><dt>Customers</dt><dd>${d.detail.customers.length}</dd><dt>Orders</dt><dd>${d.detail.orders.length} <small>${esc(sc)}</small></dd><dt>Khata due (market)</dt><dd>₹${num(d.detail.baakiMarket)} from ${d.detail.baakiCustomers} customers</dd><dt>Day closes</dt><dd>${d.detail.dayCloses}</dd>${earn}</dl></div>
</div>
<h2>Subscriptions and payments</h2><div class="tw"><table><thead><tr><th>Plan</th><th>Status</th><th class=n>Amount</th><th class=n>Paid charges</th><th>Started</th><th>Access until</th><th>Payment attempts</th></tr></thead><tbody>${subs}</tbody></table></div>
<h2>Orders <small>latest 25 of ${d.detail.orders.length}</small></h2><div class="tw"><table><thead><tr><th>#</th><th>Customer</th><th>Status</th><th>Created</th><th class=n>Amount</th><th class=n>Paid</th><th>Bill sent</th><th>Services</th></tr></thead><tbody>${orders}</tbody></table></div>
<h2>Customers <small>top 15 by billing</small></h2><div class="tw"><table><thead><tr><th>Name</th><th>Phone</th><th class=n>Orders</th><th class=n>Billed</th><th class=n>Due</th><th>Last order</th></tr></thead><tbody>${custs}</tbody></table></div>
<h2>Support calls</h2><div class="tw"><table><thead><tr><th>When</th><th>Agent</th><th>Outcome</th><th class=n>Length</th><th>Note</th><th>Tags</th></tr></thead><tbody>${calls}</tbody></table></div>
</body></html>`;
}

module.exports = function mount(app, readOnly, deps = {}) {
  const { listUsers, userDetail } = deps;
  const offerOf = (req) => String(req.query.offer || req.params.offer || 'diwali999').replace(/[^a-z0-9_-]/gi, '').slice(0, 40) || 'diwali999';
  const templateOf = (req) => (req.query.template === undefined ? 'diwali_999_yearly' : String(req.query.template).replace(/[^a-z0-9_-]/gi, '').slice(0, 60));
  app.get('/campaigns', async (req, res, next) => {
    try { res.type('html').send(summaryPage(await readOnly((q) => campaignData(q, offerOf(req), templateOf(req))))); } catch (e) { next(e); }
  });
  app.get('/campaigns/shops', async (req, res, next) => {
    try { res.type('html').send(shopsPage(await readOnly((q) => shopsData(q, offerOf(req), listUsers, templateOf(req))))); } catch (e) { next(e); }
  });
  app.get('/campaigns/shop/:id', async (req, res, next) => {
    try {
      const d = await readOnly((q) => shopData(q, offerOf(req), req.params.id, userDetail));
      if (!d) return res.status(404).type('html').send(head('Not found') + '<p>No such owner.</p></body></html>');
      res.type('html').send(shopPage(d));
    } catch (e) { next(e); }
  });
  app.get('/api/campaigns/:offer', async (req, res, next) => {
    try { res.json(await readOnly((q) => campaignData(q, offerOf(req), templateOf(req)))); } catch (e) { next(e); }
  });
  app.get('/api/campaigns/:offer/shops', async (req, res, next) => {
    try { res.json(await readOnly((q) => shopsData(q, offerOf(req), listUsers, templateOf(req)))); } catch (e) { next(e); }
  });
};
module.exports.campaignData = campaignData;
module.exports.shopsData = shopsData;
module.exports.shopData = shopData;
