// Restore shop names that the Android name-step race overwrote with the
// "MyLaundry" placeholder (fixed in app commit 0a59fa7). The typed name was
// sent to the owner's CleverTap profile (Name) before it was lost, so we read
// it back from there.
//
// Runs inside the backend container (Prisma + LAUNDRY_CLEVERTAP_* env).
//   node restore-shop-names.js          → dry run: prints the plan, writes nothing
//   APPLY=1 node restore-shop-names.js  → applies the plan
//
// Safe to re-run. Each update is compare-and-set (same name + same updated_at
// as when read) and bumps updated_at by exactly 1, so:
//  - a phone with no unsynced shop change pulls the restored name;
//  - a phone with an unsynced shop change (newer updated_at) keeps its own row
//    (and its order counter) — re-run later to catch those.
const { PrismaClient } = require('@prisma/client');
const prisma = new PrismaClient();

const APPLY = process.env.APPLY === '1';
// Same set as the app's SeedData.UNNAMED_SHOP_NAMES.
const PLACEHOLDERS = new Set(['', 'mylaundry', 'apna laundry', 'apnalaundry', 'my shop', 'shine laundry']);
const isPlaceholder = (n) => PLACEHOLDERS.has(String(n ?? '').trim().toLowerCase());
const clean = (n) => String(n).trim().replace(/\s+/g, ' ').slice(0, 40); // BillDetails.cleanName

const REGION = (process.env.LAUNDRY_CLEVERTAP_REGION || 'global').trim();
const CT_HOST = REGION === 'global' || REGION === '' ? 'api.clevertap.com' : `${REGION}.api.clevertap.com`;
const CT_HEADERS = {
  'X-CleverTap-Account-Id': process.env.LAUNDRY_CLEVERTAP_ACCOUNT_ID,
  'X-CleverTap-Passcode': process.env.LAUNDRY_CLEVERTAP_PASSCODE,
};
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

async function ctName(identity) {
  for (let attempt = 0; attempt < 3; attempt++) {
    const res = await fetch(`https://${CT_HOST}/1/profile.json?identity=${encodeURIComponent(identity)}`, { headers: CT_HEADERS });
    if (res.status === 429) { await sleep(2000 * (attempt + 1)); continue; }
    const j = await res.json().catch(() => ({}));
    if (j.status !== 'success') return { name: null, err: j.error || `http ${res.status}` };
    const rec = j.record || {};
    return { name: rec.name ?? rec.profileData?.Name ?? rec.profileData?.name ?? null };
  }
  return { name: null, err: 'rate limited' };
}

(async () => {
  if (typeof fetch !== 'function') throw new Error('Needs Node 18+ (global fetch)');
  if (!CT_HEADERS['X-CleverTap-Account-Id'] || !CT_HEADERS['X-CleverTap-Passcode']) {
    throw new Error('LAUNDRY_CLEVERTAP_ACCOUNT_ID / _PASSCODE not set');
  }

  const shops = (await prisma.laundryShop.findMany({
    select: { userId: true, name: true, updatedAt: true, user: { select: { phone: true, signupPlatform: true } } },
  })).filter((s) => isPlaceholder(s.name) && !String(s.user?.phone || '').startsWith('1000000')); // skip review/demo numbers

  console.log(`${shops.length} shops still on a placeholder name; asking CleverTap for each…`);
  const plan = [];
  let noName = 0;
  let errors = 0;
  for (const s of shops) {
    const { name, err } = await ctName(s.userId);
    if (err) errors += 1;
    if (name && !isPlaceholder(name) && clean(name).length >= 2) {
      plan.push({ userId: s.userId, phone: s.user?.phone, platform: s.user?.signupPlatform, from: s.name, to: clean(name), updatedAt: s.updatedAt });
    } else noName += 1;
    await sleep(150); // stay well under CleverTap's API rate limit
  }

  console.table(plan.map(({ phone, platform, from, to }) => ({ phone, platform, from, to })));
  console.log(`restorable: ${plan.length} · no usable CleverTap name: ${noName} · CleverTap errors: ${errors}`);

  if (!APPLY) { console.log('\nDry run — nothing written. Re-run with APPLY=1 to apply.'); return; }

  let done = 0;
  let changed = 0;
  for (const p of plan) {
    const r = await prisma.laundryShop.updateMany({
      where: { userId: p.userId, name: p.from, updatedAt: p.updatedAt }, // compare-and-set
      data: { name: p.to, updatedAt: p.updatedAt + 1n, serverUpdatedAt: new Date() },
    });
    if (r.count === 1) done += 1; else changed += 1;
  }
  console.log(`\nApplied: ${done} restored · ${changed} skipped (shop changed since it was read — re-run to retry).`);
})()
  .catch((e) => { console.error(e); process.exitCode = 1; })
  .finally(() => prisma.$disconnect());
