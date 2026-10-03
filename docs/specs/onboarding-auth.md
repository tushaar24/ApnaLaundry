# Onboarding — demo login & rate-card setup

Status: ✅ Built

## Problem statement

The owner is not a tech person. Getting into the app must be near-instant (no password, no long
forms), and the very first setup must capture just enough (shop name + rates) to start taking
orders — nothing more.

## Solution

A phone → OTP demo sign-in (OTP auto-reads itself), followed on first launch by a single
rate-card setup screen. On later launches the owner lands straight on Home.

## User stories

1. As a shop owner, I want to sign in with just my mobile number, so that I don't deal with passwords.
2. As a shop owner, I want the number pre-filled in the demo, so that I can try the app in one tap.
3. As a shop owner, I want the OTP read automatically, so that I never type a code.
4. As a shop owner, I want a clear demo hint (number 9876543210, OTP 123456), so that I know what to enter.
5. As a shop owner, I want wrong numbers/OTPs rejected with a plain message, so that I trust the flow.
6. As a shop owner, I want a "Change number" link on the OTP step, so that I can fix a typo.
7. As a shop owner setting up, I want to type my shop name, so that it appears on Home and bills.
8. As a shop owner, I want default services and prices already filled, so that I only tweak exceptions.
9. As a shop owner, I want to set my closing time during setup, so that the day-update reminder is right.
10. As a shop owner, I want to set the express charge %, so that rush orders price themselves.
11. As a shop owner, I want one button "Save and start taking orders", so that setup is obviously done.
12. As a returning owner, I want to skip login and setup, so that I go straight to my orders.
13. As a shop owner, I want to log out / restart from Settings, so that I can reset the demo.

## Implementation decisions

- **Modules:** `ui/screens/login/` (`LoginScreen`, `AuthViewModel`), `data/remote/AuthApi` (Ktor),
  `data/AuthRepository`, `data/Prefs` (DataStore), plus the rate-card screen (see
  [rate-card-settings.md](rate-card-settings.md)).
- **Fixed demo credentials:** phone `9876543210`, OTP `123456`. Anything else is rejected. This
  was the user's chosen login mode.
- **Auth over Ktor, offline:** `AuthApi.requestOtp` / `verifyOtp` POST JSON to
  `https://demo.apnalaundry.local/...`; a `MockEngine` validates the demo credentials and returns
  a token. Real Ktor + ContentNegotiation, no server, ~500ms simulated latency.
- **OTP auto-fill:** on reaching the OTP step the screen waits ~900ms then fills `123456`,
  simulating SMS auto-read. The owner still taps "Verify & continue".
- **Routing:** start destination is chosen from DataStore flags — `!loggedIn → login`,
  `loggedIn && !setupDone → rates/setup`, else `home`. `verifyOtp` sets `loggedIn`; saving the
  setup rate card sets `setupDone`.
- **Setup vs edit rate card:** the same screen serves both, keyed by a `from` argument
  (`setup` | `home` | `settings`); setup mode shows the "Number verified · Last step" header and
  the shop-name field.

## Testing decisions

- Good test = external behaviour: given a phone/OTP, the auth result and the resulting nav flag.
- **Seam:** `AuthRepository` over `AuthApi` (the `MockEngine` makes this deterministic and offline).
  A unit test can assert `verifyOtp("9876543210","123456")` succeeds and sets `loggedIn`, and that
  wrong inputs fail. Not yet written (see [pending.md](pending.md)).
- Prior art: none yet for auth; follow the pure-input/assert-output style of `AcceptanceTest`.

## Out of scope

> **Superseded:** the demo flow described here was replaced by real OTP auth
> against the shared backend — see [sync-and-auth.md](sync-and-auth.md). The
> MockEngine, pre-filled number, and auto-filled OTP are gone.

Multiple users/roles and password reset remain out of scope.

## Further notes

Verified on emulator: pre-filled number → Continue → OTP auto-fills → Verify → rate card → Home.
