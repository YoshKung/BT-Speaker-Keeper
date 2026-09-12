# v0.17 validation

Version 0.17 (versionCode 17) adds guarded saved-speaker details navigation when the Settings preview has no Connect action. It also checks connected A2DP devices before the bonded list and reconciles stale Connected status without navigating user-owned Settings.

## Local checks

- Gradle `test`, `lint`, and `assembleDebug` passed: 160 tests, zero failures/errors, lint zero errors and 68 warnings.
- Fifteen new regressions cover one-shot details navigation, unsafe/ambiguous rows, exact target identity, connected-A2DP precedence, user-owned Settings, and active-session protection.
- Public source files match the validated build inputs. The release uses the same APK tested on the device.
- No permissions, dependencies, hidden Bluetooth APIs or automatic unpair/re-pair behavior were added. Playback/cooldown and user-control guards remain in place.

## Device checks

- 5/5 app-owned and 5/5 live-monitor reconnects passed. Each started with a saved, disconnected target and visible Connect action; the app performed Connect and target confirmation without connection assistance after the trigger.
- Every cycle independently confirmed target A2DP Connected, app Connected, inactive automation and logged Home return. Durations were 15.190–21.661 seconds, mean 17.138 seconds. One session used one navigation retry.
- One verified target-row tap opened details with Connect while A2DP remained Disconnected. Actual row metadata matched the guarded navigation policy. A supplemental live-monitor reconnect succeeded afterward.
- The user confirmed automatic connection and normal audio after a power cycle. Subsequent readback confirmed v0.17, matching installed APK hash, Accessibility enabled, target A2DP Connected/Active, no error/backoff or pending automation, and final Home foreground.

## Important limitation

The original intermittent startup preview without Connect did not recur on v0.17. Its fallback branch is covered by local tests and the row/navigation assumptions were verified on the TV, but the branch has not run against the original failure screen. Post-startup logs show an already-connected target when the monitor checked, not an app-owned Connect session. The power-cycle result does not prove the new fallback fixed that intermittent condition.

## APK

- File: `BT-Speaker-Keeper-v0.17-debug.apk`
- Package: `com.btspeakerkeeper.tv`
- Version: `0.17` / code `17`
- SHA-256: `723C62A57080487AC5BDC06CB4FD869EB53354F9CDB3C780684927C3B74D482F`
- APK Signature Scheme v2 verified; signer matches v0.16.

The APK is excluded from Git and distributed through the v0.17 GitHub Release with its checksum and this validation summary.
