# Restore shop names lost to the "MyLaundry" race (2026-10-12)

**Bug:** on Android's onboarding name step, Next saved the name and the next
step as two concurrent read-change-write updates of the shop row. When both
read before either wrote, the step write put the `MyLaundry` placeholder back.
The server showed 16 Android shops with orders still named MyLaundry (they must
have typed a name to get past setup). Fixed in the app in `0a59fa7` (every shop
edit runs in one Room transaction).

**Data:** the typed name reached the owner's CleverTap profile (`Name`) before
it was overwritten, so `2026-10-12-restore-shop-names.js` reads it back from
there for every shop still on a placeholder name (review / demo numbers
`1000000xxx` skipped) and restores it.

## Run (from the repo root)

```sh
sh docs/deploy/2026-10-12-restore-shop-names.sh           # dry run: prints the plan, writes nothing
APPLY=1 sh docs/deploy/2026-10-12-restore-shop-names.sh   # apply
```

The runner checks the server still uses the "newer updated_at wins" shop
merge, copies the script into the backend container, runs it, and removes it.

## Why `updated_at + 1`

Each update is compare-and-set (same name and `updated_at` as when read) and
bumps `updated_at` by exactly 1 (plus `server_updated_at = now()` so pulls
pick it up):

- a phone whose shop row is in sync pulls the restored name (newer stamp);
- a phone with an unsynced shop change keeps its row and its order counter
  (its stamp is newer, so its push wins and the pull is ignored); its name
  stays MyLaundry until the next run. Stamping "now" instead could roll back an
  offline phone's `nextOrder` and reuse order numbers.

Re-run after the fixed app is out to catch those. Shops whose owner never typed
a name (no CleverTap Name) are left alone.
