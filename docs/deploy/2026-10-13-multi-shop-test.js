// Multi-shop backend test. `node test-multishop.js seed <base>` runs against the OLD
// (live) backend like an old app; `node test-multishop.js check <base>` runs against
// the NEW backend after the migration. Run from the matching backend dir (Prisma client).
const fs = require('fs');
const path = require('path');
const assert = require('assert');
const prisma = require(path.resolve('db'));

const [, , mode, base] = process.argv;
const STATE = path.join(__dirname, 'state.json');
const TOKEN = 'tok-owner-0001';
const PHONE = '9000000001';
const now = 1791900000000;

async function call(method, url, { body, shop, auth = true } = {}) {
  const headers = { 'Content-Type': 'application/json' };
  if (auth) headers.Authorization = `Bearer ${TOKEN}`;
  if (shop !== undefined) headers['X-Laundry-Shop'] = shop;
  const res = await fetch(base + url, { method, headers, body: body ? JSON.stringify(body) : undefined });
  const text = await res.text();
  let json = null;
  try { json = JSON.parse(text); } catch { json = text; }
  return { status: res.status, json };
}
const pull = (shop) => call('GET', '/api/laundry/sync/pull?since=0', { shop });
const push = (changes, shop) => call('POST', '/api/laundry/sync/push', { body: { changes }, shop });
const noCheckpoint = (r) => { const { checkpoint, ...rest } = r.json; return rest; };
// Stable order for comparison.
const sortChanges = (c) => {
  const out = { ...c };
  for (const k of Object.keys(out)) if (Array.isArray(out[k])) out[k] = [...out[k]].sort((a, b) => String(a.id ?? a.date).localeCompare(String(b.id ?? b.date)));
  return out;
};

function mainShopChanges(t) {
  return {
    shop: { name: 'Shop One', phone: PHONE, closeTime: '21:00', expressPct: 50, nextOrder: 2, nextCust: 2, billTemplate: 'bold', address: 'Main road', updatedAt: t },
    services: [{ id: 's1', name: 'Wash & Iron', mode: 'PIECE', itemsJson: '[{"name":"Shirt","price":20}]', sortOrder: 0, deleted: false, updatedAt: t }],
    customers: [{ id: 'c1', name: 'Ramesh', phone: '9876543210', address: '', pastOrders: 0, lastLabel: 'Today', agoRank: 0, deleted: false, updatedAt: t }],
    orders: [{
      id: 1, custId: 'c1', pickup: 'SHOP', delivery: 'SHOP', pickupDate: '2026-10-13', pickupTime: '', deliveryDate: '2026-10-14', deliveryTime: '',
      ddAuto: true, status: 'RECEIVED', cancelReason: '', fee: 0, express: false, exAmt: 0, discount: 0, pre: 0, paid: 0, doneAt: '', doneDate: '',
      createdOn: '2026-10-13', billSent: false, pieces: 3,
      linesJson: '[{"serviceId":"s1","serviceName":"Wash & Iron","itemName":"Shirt","qty":3,"price":20,"base":20,"kg":0.0,"amt":60}]',
      serialNo: '1', note: 'starch', deleted: false, updatedAt: t,
    }],
    ledger: [{ id: 'l1', custId: 'c1', date: '2026-10-13', time: '10:00', ts: t, kind: 'BILL', amt: 60, method: 'NONE', tag: 'NONE', cover: 0, toOld: 0, toAdv: 0, ref: 1, note: '', deleted: false, updatedAt: t }],
    dayCloses: [{ date: '2026-10-12', closedAt: t, cashCounted: 500, deleted: false, updatedAt: t }],
  };
}

const results = [];
async function check(name, fn) {
  try { await fn(); results.push(`PASS  ${name}`); } catch (e) { results.push(`FAIL  ${name}\n      ${e.message.split('\n').slice(0, 6).join('\n      ')}`); }
}

async function seed() {
  const user = await prisma.laundryUser.create({ data: { phone: PHONE, name: 'Owner', apiToken: TOKEN } });
  const p = await push(mainShopChanges(now));
  assert.strictEqual(p.status, 200, JSON.stringify(p.json));
  const pulled = await pull();
  const link = await call('POST', '/api/laundry/bills/1/link');
  const sample = await call('POST', '/api/laundry/bills/sample/link');
  const bill = await call('GET', `/api/laundry/public/bills/${link.json.token}`, { auth: false });
  const sampleBill = await call('GET', `/api/laundry/public/bills/${sample.json.token}`, { auth: false });
  fs.writeFileSync(STATE, JSON.stringify({
    userId: user.id, pushed: p.json, pull: noCheckpoint(pulled), token: link.json.token, sampleToken: sample.json.token,
    bill: bill.json, sampleBill: sampleBill.json,
  }, null, 2));
  console.log('seeded', user.id, 'push', JSON.stringify(p.json), 'bill', bill.status, 'sample', sampleBill.status);
}

async function verify() {
  const s = JSON.parse(fs.readFileSync(STATE, 'utf8'));
  const sameAsBefore = async (label) => {
    const r = await pull();
    assert.strictEqual(r.status, 200);
    assert.deepStrictEqual(sortChanges(noCheckpoint(r).changes), sortChanges(s.pull.changes), `${label}: pull differs`);
  };

  // ---- old clients (no header) see exactly what they did before ----
  await check('old app: pull is byte-for-byte the same after the migration', () => sameAsBefore('after migration'));
  await check('old app: "main" header = no header', async () => {
    const r = await pull('main');
    assert.deepStrictEqual(sortChanges(noCheckpoint(r).changes), sortChanges(s.pull.changes));
  });
  await check('old app: bill link token unchanged', async () => {
    const r = await call('POST', '/api/laundry/bills/1/link');
    assert.strictEqual(r.json.token, s.token);
  });
  await check('old app: public bill page unchanged', async () => {
    const r = await call('GET', `/api/laundry/public/bills/${s.token}`, { auth: false });
    assert.deepStrictEqual(r.json, s.bill);
  });
  await check('old app: sample bill link + page unchanged', async () => {
    const l = await call('POST', '/api/laundry/bills/sample/link');
    assert.strictEqual(l.json.token, s.sampleToken);
    const r = await call('GET', `/api/laundry/public/bills/${s.sampleToken}`, { auth: false });
    assert.deepStrictEqual(r.json, s.sampleBill);
  });
  await check('old app: re-pushing the same rows applies the same way', async () => {
    const r = await push(mainShopChanges(now));
    assert.deepStrictEqual(r.json, s.pushed);
  });

  // ---- shops API ----
  await check('GET /shops lists only the main shop', async () => {
    const r = await call('GET', '/api/laundry/shops');
    assert.strictEqual(r.status, 200);
    assert.deepStrictEqual(r.json.shops.map((x) => [x.id, x.name]), [['main', 'Shop One']]);
  });
  let shopId = null;
  await check('POST /shops: the 2nd shop is free (no plan)', async () => {
    const r = await call('POST', '/api/laundry/shops', { body: { name: '  Branch   Two ' } });
    assert.strictEqual(r.status, 200, JSON.stringify(r.json));
    assert.strictEqual(r.json.shop.name, 'Branch Two');
    shopId = r.json.shop.id;
    assert.ok(/^[0-9a-f-]{36}$/.test(shopId));
  });
  await check('POST /shops: a 3rd shop without a plan is refused (403 PLAN_REQUIRED)', async () => {
    const r = await call('POST', '/api/laundry/shops', { body: { name: 'Branch Three' } });
    assert.strictEqual(r.status, 403);
    assert.strictEqual(r.json.code, 'PLAN_REQUIRED');
    assert.strictEqual(await prisma.laundryShop.count({ where: { userId: s.userId } }), 2);
  });
  await prisma.laundrySubscription.create({ data: { userId: s.userId, plan: 'monthly', status: 'active', amount: 49900 } });
  let thirdId = null;
  await check('POST /shops: a 3rd shop with a plan is created', async () => {
    const r = await call('POST', '/api/laundry/shops', { body: { name: 'Branch Three' } });
    assert.strictEqual(r.status, 200, JSON.stringify(r.json));
    thirdId = r.json.shop.id;
  });

  // ---- the new shop: same client ids as the main shop, fully separate ----
  const t2 = now + 1000;
  const second = {
    shop: { name: 'Branch Two', phone: PHONE, closeTime: '20:00', expressPct: 30, nextOrder: 2, nextCust: 2, updatedAt: t2 },
    services: [{ id: 's1', name: 'Dry Clean', mode: 'PIECE', itemsJson: '[{"name":"Suit","price":200}]', sortOrder: 0, deleted: false, updatedAt: t2 }],
    customers: [{ id: 'c1', name: 'Suresh', phone: '9123456780', address: '', pastOrders: 0, lastLabel: 'Today', agoRank: 0, deleted: false, updatedAt: t2 }],
    orders: [{ ...mainShopChanges(t2).orders[0], linesJson: '[{"serviceId":"s1","serviceName":"Dry Clean","itemName":"Suit","qty":1,"price":200,"base":200,"kg":0.0,"amt":200}]', note: 'branch', updatedAt: t2 }],
    ledger: [{ ...mainShopChanges(t2).ledger[0], amt: 200, updatedAt: t2 }],
    dayCloses: [{ date: '2026-10-12', closedAt: t2, cashCounted: 900, deleted: false, updatedAt: t2 }],
  };
  await check('new shop: push with the header (same ids as main) is applied', async () => {
    const r = await push(second, shopId);
    assert.strictEqual(r.status, 200, JSON.stringify(r.json));
    assert.strictEqual(r.json.applied, 6);
  });
  await check('new shop: pull with the header returns only that shop', async () => {
    const r = await pull(shopId);
    const c = r.json.changes;
    assert.strictEqual(c.shop.name, 'Branch Two');
    assert.deepStrictEqual(c.customers.map((x) => x.name), ['Suresh']);
    assert.deepStrictEqual(c.orders.map((x) => x.note), ['branch']);
    assert.deepStrictEqual(c.dayCloses.map((x) => x.cashCounted), [900]);
    assert.ok(!('shopId' in c.shop) && !('createdAt' in c.shop), 'server-only fields leaked');
  });
  await check('old app after a second shop exists: pull still identical (no leak)', () => sameAsBefore('after second shop'));
  await check('unknown shop header -> 404 SHOP_NOT_FOUND (no fallback to main)', async () => {
    const r = await pull('00000000-0000-0000-0000-000000000000');
    assert.strictEqual(r.status, 404);
    assert.strictEqual(r.json.code, 'SHOP_NOT_FOUND');
    const w = await push(second, '00000000-0000-0000-0000-000000000000');
    assert.strictEqual(w.status, 404);
  });
  await check("another owner's shop id -> 404", async () => {
    await prisma.laundryUser.create({ data: { phone: '9000000002', name: 'Other', apiToken: 'tok-other-0002' } });
    const res = await fetch(`${base}/api/laundry/sync/pull?since=0`, { headers: { Authorization: 'Bearer tok-other-0002', 'X-Laundry-Shop': shopId } });
    assert.strictEqual(res.status, 404);
  });
  await check('new shop: its own bill link + public page', async () => {
    const l = await call('POST', '/api/laundry/bills/1/link', { shop: shopId });
    assert.notStrictEqual(l.json.token, s.token);
    const r = await call('GET', `/api/laundry/public/bills/${l.json.token}`, { auth: false });
    assert.strictEqual(r.json.bill.shop.name, 'Branch Two');
    assert.strictEqual(r.json.bill.customer.name, 'Suresh');
    assert.strictEqual(r.json.bill.order.lines[0].itemName, 'Suit');
    const old = await call('GET', `/api/laundry/public/bills/${s.token}`, { auth: false });
    assert.deepStrictEqual(old.json, s.bill);
  });
  await check('old app edits the main shop: lands in main only', async () => {
    const edit = { orders: [{ ...mainShopChanges(now).orders[0], note: 'edited by old app', updatedAt: now + 5000 }] };
    const r = await push(edit);
    assert.strictEqual(r.json.applied, 1);
    const mainOrders = (await pull()).json.changes.orders;
    assert.strictEqual(mainOrders.find((o) => o.id === 1).note, 'edited by old app');
    const branchOrders = (await pull(shopId)).json.changes.orders;
    assert.strictEqual(branchOrders.find((o) => o.id === 1).note, 'branch');
  });
  await check('GET /shops lists main first, then the new shop', async () => {
    const r = await call('GET', '/api/laundry/shops');
    assert.deepStrictEqual(r.json.shops.map((x) => [x.id, x.name]), [['main', 'Shop One'], [shopId, 'Branch Two'], [thirdId, 'Branch Three']]);
  });
  await check("DELETE the main shop -> 400 (can't)", async () => {
    const r = await call('DELETE', '/api/laundry/shops/main');
    assert.strictEqual(r.status, 400);
    assert.strictEqual(r.json.code, 'MAIN_SHOP');
  });
  await check("DELETE another owner's shop -> 404", async () => {
    const res = await fetch(`${base}/api/laundry/shops/${thirdId}`, { method: 'DELETE', headers: { Authorization: 'Bearer tok-other-0002' } });
    assert.strictEqual(res.status, 404);
  });
  await check('DELETE an extra shop: gone from the list, its sync refused', async () => {
    const r = await call('DELETE', `/api/laundry/shops/${thirdId}`);
    assert.strictEqual(r.status, 200, JSON.stringify(r.json));
    const list = await call('GET', '/api/laundry/shops');
    assert.deepStrictEqual(list.json.shops.map((x) => x.id), ['main', shopId]);
    assert.strictEqual((await pull(thirdId)).status, 404);
    assert.strictEqual((await call('DELETE', `/api/laundry/shops/${thirdId}`)).status, 404);
  });
  await check('after a delete: old app pull unchanged, the other shop intact', async () => {
    const mainOrders = (await pull()).json.changes.orders;
    assert.strictEqual(mainOrders.find((o) => o.id === 1).note, 'edited by old app');
    assert.ok(!('deletedAt' in (await pull()).json.changes.shop));
    assert.strictEqual((await pull(shopId)).json.changes.shop.name, 'Branch Two');
  });
  await check('support list: one row per owner, main shop name', async () => {
    const support = require(path.resolve('services/laundry-support-shops'));
    const rows = await support.loadSummaryRows(s.userId);
    assert.strictEqual(rows.length, 1);
    assert.strictEqual(rows[0].shopName, 'Shop One');
  });
  await check('logo upload is kept per shop', async () => {
    const png = Buffer.from('89504e470d0a1a0a', 'hex').toString('base64');
    for (let i = 0; i < 4; i++) await call('POST', '/api/laundry/shop/logo', { body: { contentType: 'image/png', dataBase64: png } });
    await call('POST', '/api/laundry/shop/logo', { body: { contentType: 'image/png', dataBase64: png }, shop: shopId });
    assert.strictEqual(await prisma.laundryShopLogo.count({ where: { userId: s.userId, shopId: '' } }), 3);
    assert.strictEqual(await prisma.laundryShopLogo.count({ where: { userId: s.userId, shopId } }), 1);
  });

  console.log(results.join('\n'));
  if (results.some((r) => r.startsWith('FAIL'))) process.exitCode = 1;
}

(mode === 'seed' ? seed() : verify())
  .catch((e) => { console.error(e); process.exitCode = 1; })
  .finally(() => prisma.$disconnect());
