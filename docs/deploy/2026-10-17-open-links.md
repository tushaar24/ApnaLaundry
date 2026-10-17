# Deploy: https links into the Android app — mylaundry.work/open/<screen> (2026-10-17)

WhatsApp template buttons and SMS only take https, but the app's deep links are
`mylaundry://<screen>` (push/DeepLinks.kt, app 1.0.8+). `web/src/app/open/[[...screen]]/route.ts`
serves a tiny HTML page (no site layout or scripts) that hands over to the app:

| Link | Opens |
|---|---|
| `https://mylaundry.work/open/new-order` | New order |
| `https://mylaundry.work/open/home` (or `/open`) | Orders (Home) |
| `https://mylaundry.work/open/customers` | Customers |
| `https://mylaundry.work/open/earnings` | Earnings |
| `https://mylaundry.work/open/subscription` | Plans / subscription |

Anything else opens Home.
- **Android:** an `intent://` link fires on load. If the app is installed, Chrome opens it on that screen. If not, it goes to the Play Store, which also covers builds older than 1.0.8 (the owner lands on Update). An "Open MyLaundry app" button is there for in-app browsers that need a tap.
- **Other devices:** the Play Store and website links.

## Applied on courses-aws (17 Oct 2026, ~13:25 IST)

Only this one file was added to the live copy (`~/apnalaundry-web`). The rest of the live site is unchanged; it still lacks prod's Firebase OTP commit, see the web deploy notes.

```sh
mkdir -p "$HOME/apnalaundry-web/src/app/open/[[...screen]]"
cat web/src/app/open/[[...screen]]/route.ts | ssh courses-aws 'cat > "$HOME/apnalaundry-web/src/app/open/[[...screen]]/route.ts"'
docker tag shweta-makeup-monorepo-laundry shweta-makeup-monorepo-laundry:rollback-20261017-075443
docker compose -f docker-compose.prod.yml build laundry      # next build typechecks
docker compose -f docker-compose.prod.yml up -d --no-deps laundry
```

Checked:
- An Android user agent gets `intent://<screen>#Intent;scheme=mylaundry;package=com.dailyworks.apnalaundry;…`.
- A desktop user agent gets the Play Store page.
- `/`, `/login` and `/privacy` return 200; `/api/laundry/*` still proxies.

## Roll back

```sh
docker tag shweta-makeup-monorepo-laundry:rollback-20261017-075443 shweta-makeup-monorepo-laundry
docker compose -f docker-compose.prod.yml up -d --no-deps laundry
rm -r "$HOME/apnalaundry-web/src/app/open"
```
