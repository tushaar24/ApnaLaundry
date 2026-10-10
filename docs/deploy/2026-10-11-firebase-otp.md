# Deploy: login OTP via Firebase phone auth (2026-10-11)

Every real number's login OTP is now sent and checked by **Firebase phone auth**
in the shweta Firebase project (`shweta-makeover`). The client does the OTP
with the Firebase SDK, then trades the Firebase ID token for our usual
permanent token at a new backend route. MiniMoth / 2Factor are no longer used
by current clients.

- **Android** (`com.dailyworks.apnalaundry`, Firebase app
  `1:2721051979:android:c02551946f5623faaa24ce`, `app/google-services.json`):
  `PhoneAuthProvider.verifyPhoneNumber` sends the SMS; Firebase auto-reads it
  (its SMS carries the app hash) and logs straight in, otherwise the code is
  typed. The old SMS Retriever autofill is gone.
- **Web** (mylaundry.work, Firebase web app `1:2721051979:web:2fe032124dd8d602aa24ce`):
  `signInWithPhoneNumber` behind an invisible reCAPTCHA.
- **Backend**: `POST /api/laundry/auth/firebase {idToken, deviceId}` verifies
  the ID token (Google's securetoken keys, `aud`/`iss` = `shweta-makeover`,
  `phone_number` must be +91) with node `crypto` — no firebase-admin, no
  service account, no new env vars (`FIREBASE_PROJECT_ID` already defaults to
  `shweta-makeover`). Same user / token / `isNewUser` as `verify-otp`.
- **App-review numbers** `10000000xx` still use `request-otp` / `verify-otp`
  (OTP `10000` + last digit), since Firebase can't text them.
- **Older app builds** still call `request-otp` / `verify-otp` for every number,
  so that path (MiniMoth) stays live until those builds are gone.

## Firebase console (project shweta-makeover)

- Authentication → Sign-in method → **Phone** enabled.
- Project settings → Android app `com.dailyworks.apnalaundry` → SHA fingerprints:
  - Play app signing SHA-1 `80:7D:C5:71:F7:B6:C6:5D:56:A6:9B:F7:7C:71:7F:26:8C:51:57:34`
  - Play app signing SHA-256 `4A:DD:39:31:4F:DA:CC:60:67:95:BB:4A:0F:C4:38:C2:6B:D6:A1:F6:79:B0:F2:42:44:73:1C:46:F3:2A:17:AC`
  - Debug SHA-1 `03:1B:50:B1:10:5B:44:BA:A8:50:0F:52:C4:80:D6:86:F8:52:A2:66`
  - (upload key `~/Documents/mylaundry.jks` too, for locally signed release builds)
  Without them Android sends fail with "This app is not authorized to use Firebase Authentication".
- Authentication → Settings → **Authorized domains**: `mylaundry.work`.
- If the web API key is referrer-restricted (Google Cloud → Credentials), allow `https://mylaundry.work/*`.
- Phone auth bills per SMS beyond the free tier — the project needs the Blaze plan,
  and Authentication → Settings → SMS region policy should allow India.

## Backend (courses-aws, `~/shweta-makeup-monorepo/backend`)

`2026-10-11-backend-firebase-otp.patch` was diffed against the live files
(server `main` 5f1ae04) and checked with `patch -p1 --dry-run` there.

```bash
scp docs/deploy/2026-10-11-backend-firebase-otp.patch courses-aws:/tmp/
ssh courses-aws
cd ~/shweta-makeup-monorepo
patch -p1 --dry-run < /tmp/2026-10-11-backend-firebase-otp.patch   # must apply cleanly
patch -p1 < /tmp/2026-10-11-backend-firebase-otp.patch
docker compose -f docker-compose.prod.yml up -d --build backend
git add backend/routes/laundry-auth.js backend/services/laundry-firebase-auth.js
git commit -m "feat(laundry): login with a Firebase phone-auth ID token"
```

Deploy the backend **before** the web site / app release — they call
`/auth/firebase` for every real number.

## Web

Rsync `web/` to the server as usual (see `laundry-web-prod-deploy`); the
Firebase SDK is a new npm dependency, so the image rebuild runs `npm ci`.

## Android

Version 1.0.5 (5) from `prod`; Play-signed builds pass Firebase's app check via
the Play signing SHA above.

Rollback: revert the client commits; the backend route is additive and can stay.
