# Language links

A link can open the Android app in one language, for ads, WhatsApp or SMS in that language (owner, 2026-10-25). It's the app's default from then on. The owner can still change it in the login dialog, with the chip at the top right of login, or in Settings.

| Language | Link | In-app (push, CleverTap) |
|---|---|---|
| English | https://mylaundry.work/open/en | `mylaundry://en` |
| हिन्दी Hindi | https://mylaundry.work/open/hi | `mylaundry://hi` |
| मराठी Marathi | https://mylaundry.work/open/mr | `mylaundry://mr` |
| ગુજરાતી Gujarati | https://mylaundry.work/open/gu | `mylaundry://gu` |
| বাংলা Bengali | https://mylaundry.work/open/bn | `mylaundry://bn` |
| தமிழ் Tamil | https://mylaundry.work/open/ta | `mylaundry://ta` |
| తెలుగు Telugu | https://mylaundry.work/open/te | `mylaundry://te` |
| ಕನ್ನಡ Kannada | https://mylaundry.work/open/kn | `mylaundry://kn` |
| മലയാളം Malayalam | https://mylaundry.work/open/ml | `mylaundry://ml` |
| ਪੰਜਾਬੀ Punjabi | https://mylaundry.work/open/pa | `mylaundry://pa` |
| ଓଡ଼ିଆ Odia | https://mylaundry.work/open/or | `mylaundry://or` |

## Language + screen

- **Path form:** `https://mylaundry.work/open/<lang>/<screen>`, e.g. https://mylaundry.work/open/hi/new-order. The in-app form is `mylaundry://hi/new-order`.
- **Query form:** `?lang=<lang>` on any link, e.g. https://mylaundry.work/open/new-order?lang=ta, `mylaundry://home?lang=hi`, https://mylaundry.work/pay/CODE?lang=hi.
- **Screens:** new-order, home, customers, earnings, subscription and assistant (the in-app links only). See `push/DeepLinks.kt`.

## What happens

- **App installed (1.0.16+):** the app opens in that language. Then:
  - not logged in yet: the login screen is in that language, and its language dialog has it selected;
  - logged in: it opens on Home, or on the link's screen.
  - The link wins over a language picked before, since the link is what the owner tapped.
- **App not installed:** the page sends them to the Play Store with `referrer=lang=hi`. On the first open after install, the app reads Play's install referrer once and switches to that language. This only applies if no language was picked yet.
  - This works only for installs from that Play Store link. An install found through search has no referrer.
- **Older apps (before 1.0.16):** `/open/hi` opens Home, and `?lang` is ignored. Nothing breaks.
- **Not Android:** the page shows the Play Store link (with the referrer) and the website link.

## Counting

- **Website:** every tap logs an `open_link` line with `lang` in the laundry container's logs.
- **App:** it sends `Language Changed` with `where` = `link` (link opened in the app) or `install_link` (first open after installing from a link).

## Code

- **App:**
  - `push/DeepLinks.kt` (`language()`);
  - `MainActivity.takeLinkLanguage`;
  - `core/InstallLanguage.kt` (the Play install referrer, `com.android.installreferrer`).
- **Website:**
  - `web/src/core/openApp.ts`;
  - `web/src/app/open/[[...screen]]/route.ts`;
  - `web/src/app/pay/[[...code]]/route.ts`.
- **Deploy:** `docs/deploy/2026-10-25-web-language-links-deploy.sh`.
