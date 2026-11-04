# Send-bill contacts ask — server switch (2026-11-04)

Owner: "keep a flag on backend to disable / enable this ask permission of contact if not given on
send bill on WhatsApp … it might drop my funnel of new order creation or impact existing users …
proper events so we can track this properly."

## What

Android 1.0.18 (commit 3773133) asks for READ_CONTACTS the first time a bill is sent on WhatsApp,
so a saved number gets the bill picture and an unsaved one gets a wa.me text + bill link (an
unsaved number otherwise lands on WhatsApp's "Send to" picker). That ask is now server-controlled:

- `GET /api/laundry/experiments` → `experiments.billContactsAsk`: `ask` | `no_ask`, every account.
- `LAUNDRY_BILL_CONTACTS_ASK_PCT` (backend `.env`, default 50) = share that gets `ask`, on a
  stable hash of the user id (`bill_contacts_ask_v1:<id>`). **0 = off for everyone, 100 = on for
  everyone.** Raising it only moves `no_ask` shops into `ask`.
- `LAUNDRY_BILL_CONTACTS_ASK_FORCE='{"<10-digit phone>":"ask"}'` pins QA phones.
- CleverTap profile `Bill_Contacts_Ask` (server + app).

App: the group is saved in Prefs at each app start (`ExperimentsRepository.refresh`) and read at
each send (`ui/screens/bill/ContactsGate.kt`). So a change applies from the shop's next app start.
`no_ask` and "never fetched" = no ask; a permission already given ("Choose from contacts") is
used in both groups. A "no" at a bill send is never asked again on that phone.

## Change it

```sh
ssh courses-aws
cd ~/shweta-makeup-monorepo
sed -i 's/^LAUNDRY_BILL_CONTACTS_ASK_PCT=.*/LAUNDRY_BILL_CONTACTS_ASK_PCT=0/' backend/.env   # 0 off · 50 split · 100 on
docker compose -f docker-compose.prod.yml up -d backend    # recreate so the new .env is read
```

## Backward compatibility

- Apps before 1.0.18 ignore the new key (`ignoreUnknownKeys`); the website never calls /experiments.
- The response keeps every earlier key unchanged; the deploy check compares `customerFlow` /
  `servicesSetup` for the last 500 accounts before and after (dry run 4 Nov: unchanged).
- 1.0.18 against a server without the key: no ask (the 1.0.17 behaviour).

## Events (docs/analytics-events.md → "Send-bill contacts ask")

`bill_contacts_ask` on every 1.0.18 event · `Contacts Permission Asked {from, screen}` ·
`Contacts Permission Answered {granted, from, screen, ms}` · `Bill Sent` / `Combined Bill Sent
{mode, contacts}` · profile `Contacts_Permission`.

## Deploy

`DRY_RUN=1 bash docs/deploy/2026-11-04-bill-contacts-ask-deploy.sh`, then without DRY_RUN.
