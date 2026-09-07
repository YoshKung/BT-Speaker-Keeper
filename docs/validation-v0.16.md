# v0.16 validation

Version 0.16 (versionCode 16) fixes repeated Connect activation that could restart the wait before A2DP verification or recovery. It adds bounded connection phases, fresh visible-action gesture fallback, callback deadlines, two-pane Settings navigation, and exact-target system connection confirmation.

## Local checks

- Gradle `test`, `lint`, and `assembleDebug` passed.
- 145 unit tests passed with no failures or errors; lint reported 0 errors and 68 warnings.
- Regression coverage includes persistent Connect labels and duplicate events, ineffective clicks, cancelled gestures, missing/late/duplicate A2DP callbacks, session expiry, unsafe or ambiguous actions, and Settings navigation/confirmation guards.
- No Bluetooth permissions or dependencies were added. Public Bluetooth APIs, playback/cooldown guards, and user control of Settings are retained.

## Physical-device checks

Verified on Chromecast running Android 14/API 34 on 2026-09-07:

- 5/5 app-owned reconnects (2 app-open and 3 Connect Now triggers), plus 5/5 live-monitor reconnects after disconnection.
- Every baseline used the saved, paired target with a visible Connect action. Test setup deliberately disconnected that target; the app performed every subsequent Connect and exact-target confirmation action without connection assistance.
- Every cycle confirmed the configured target through A2DP, ended with app status Connected and no pending automation, and logged Home return after A2DP confirmation. Final Home foreground was independently checked.
- Session durations ranged from 13.407 to 19.290 seconds, excluding setup and pre-session detection. Four sessions used one navigation retry within the configured budget.
- Normal speaker audio was confirmed after the first successful reconnect; final A2DP state identified the configured target as connected and active.

Both app automation and a separate direct system-UI connection attempt had failed before the TV and speaker were restarted. The successful cycles were recorded after that restart. These results do not isolate the earlier physical/system cause or establish reliability across other devices.

## Validated APK

- File: `BT-Speaker-Keeper-v0.16-debug.apk`
- Package: `com.btspeakerkeeper.tv`
- SHA-256: `D65434622F0BB1ADE943A32529A3766BF5008294119E5A950652B86955B1E51C`
- APK Signature Scheme v2 verified, with the same signer as v0.15; the installed APK matched this hash.

The APK is retained as a local release artifact and is excluded from Git. This record does not imply a GitHub release has been published.
