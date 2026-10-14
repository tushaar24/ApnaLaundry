// Creates the ₹199 Monthly Razorpay plan (or finds the one already made) and
// prints its id, for LAUNDRY_RAZORPAY_PLAN_MONTHLY_199. Safe to re-run.
// Runs inside the backend container (config/env has the Razorpay keys):
//   docker exec -w /app <backend> node create-monthly-199-plan.js
const env = require('./config/env');

const AMOUNT = 19900; // paise
const NAME = 'MyLaundry Monthly ₹199';
const auth = 'Basic ' + Buffer.from(`${env.laundryRazorpayKeyId}:${env.laundryRazorpayKeySecret}`).toString('base64');

async function api(path, init = {}) {
  const res = await fetch(`https://api.razorpay.com/v1${path}`, {
    ...init,
    headers: { Authorization: auth, 'Content-Type': 'application/json', ...(init.headers || {}) },
  });
  const j = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(`${path} → ${res.status} ${JSON.stringify(j.error || j)}`);
  return j;
}

(async () => {
  if (!env.laundryRazorpayKeyId || !env.laundryRazorpayKeySecret) throw new Error('Razorpay keys not set');
  const { items = [] } = await api('/plans?count=100');
  const found = items.find((p) => p.period === 'monthly' && p.interval === 1 && p.item && p.item.amount === AMOUNT && p.item.currency === 'INR');
  if (found) {
    console.log(`existing plan: ${found.id} (${found.item.name}, ₹${found.item.amount / 100}/month)`);
    return;
  }
  const plan = await api('/plans', {
    method: 'POST',
    body: JSON.stringify({
      period: 'monthly',
      interval: 1,
      item: { name: NAME, amount: AMOUNT, currency: 'INR', description: 'Monthly plan for Android 1.0.12+' },
      notes: { app: 'mylaundry', since: '2026-10-14' },
    }),
  });
  console.log(`created plan: ${plan.id} (${plan.item.name}, ₹${plan.item.amount / 100}/month)`);
})().catch((e) => { console.error(`ERROR ${e.message}`); process.exit(1); });
