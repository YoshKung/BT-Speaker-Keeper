# BT Speaker Keeper

BT Speaker Keeper is a sideloadable Android TV / Google TV utility app that helps reconnect one selected paired Bluetooth speaker.

The app uses public Bluetooth APIs only to check paired devices and A2DP connection state. It does not call hidden or private `BluetoothA2dp.connect`. When the target speaker is disconnected, it uses an Accessibility Service to automate the existing Google TV Settings flow.

## Build

Requirements:

- JDK 17
- Android SDK with API 36
- Android build tools available to Gradle

Build commands:

```powershell
$env:JAVA_HOME = (Resolve-Path ".tools\jdk17\jdk-*").Path
$env:ANDROID_HOME = (Resolve-Path ".tools\android-sdk").Path
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
$env:PATH = "$env:JAVA_HOME\bin;$env:ANDROID_HOME\platform-tools;$env:PATH"

.\gradlew.bat test
.\gradlew.bat lint
.\gradlew.bat assembleDebug
```

The debug APK will be created under:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Sideload to Google TV

1. Enable Developer options on Google TV.
2. Enable USB debugging or Network debugging.
3. Connect from your computer:

```powershell
adb connect <GOOGLE_TV_IP_ADDRESS>
adb devices
```

4. Install the APK:

```powershell
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

5. Open **BT Speaker Keeper** from Apps.

## One-Time Setup

1. Pair the Bluetooth speaker in Google TV Settings first.
2. Open **BT Speaker Keeper**.
3. Grant **Nearby Devices** / Bluetooth permission when prompted.
4. Connect the desired Bluetooth speaker manually in Google TV Settings once.
5. Press **Scan Known Devices** in BT Speaker Keeper.
6. Press the connected speaker row. Pressing the same row toggles Auto Connect on or off for that speaker.
7. Open Accessibility settings from the app.
8. Enable **BT Speaker Keeper Automation** manually.
9. Return to the app and press **Connect Now** to test.

If the speaker later disappears from Google TV's known-device list, turn the speaker on and put it in pairing/discoverable mode, then press **Repair Pair Now**. The app will use guarded Accessibility automation to open Google TV pairing UI, choose the configured speaker by saved name or Bluetooth address, accept the Bluetooth pair prompt, and then recheck/reconnect.

If Google TV cancels pairing or the speaker is not visible because it is not in pairing mode, BT Speaker Keeper opens a **Speaker pairing needed** screen. Leave that screen open, press and hold the speaker Bluetooth/Pair button until the speaker enters pairing mode, and the app will retry the repair flow every 10 seconds. You can also press **Retry Pair Now** from that screen.

## Runtime Behavior

The app attempts reconnect:

- when the app is opened;
- when **Connect Now** is pressed;
- after device boot with a randomized 20-30 second delay;
- on screen-on while the app process is alive;
- through the Accessibility Service live monitor, which checks the configured target every 5 seconds while Auto Connect is on;
- optionally through a lightweight periodic WorkManager retry.

Before any UI automation runs, the app checks the target speaker with public Bluetooth A2DP state APIs:

- `BluetoothAdapter.bondedDevices`
- `BluetoothA2dp.getConnectedDevices()`
- `BluetoothA2dp.getConnectionState(device)`

If the speaker is already connected, the app logs that state and does not open Settings.

If the target speaker is no longer bonded/paired and Auto Connect is on, non-live automatic triggers and **Repair Pair Now** can start a repair pairing attempt. The live monitor does not start repair pairing by itself when the target is no longer paired; it records that repair is needed and waits for the configured backoff so it does not take over Settings while the user is fixing the device manually. On Google TV the repair flow first tries the accessory-pairing action `com.google.android.intent.action.CONNECT_INPUT`, then falls back to Bluetooth/device-picker/settings actions. It selects the configured speaker when visible by name or stored Bluetooth address, accepts the Bluetooth pair prompt, waits briefly, and checks A2DP state again.

The live monitor uses public A2DP state checks only. It can bypass the long user cooldown so reconnect can start quickly after a disconnect, but it still requires Auto Connect, skips while playback is active when that safety control is enabled, avoids concurrent sessions, pauses while Google TV Settings is already foreground without an app-owned automation session, and throttles Settings launches.

If a live-monitor reconnect attempt cannot find the configured speaker or cannot confirm connection after clicking the target row, the app backs off before trying again. The backoff uses the configured Cooldown value, with a 2 minute minimum if Cooldown is set to 0, but it allows a guarded live-monitor probe about every 30 seconds during that window so a speaker power-cycle can recover without waiting the full cooldown. Manual **Connect Now** and **Repair Pair Now** still run immediately.

When the saved alias disappears, Google TV may show the same speaker under a factory discovery name. BT Speaker Keeper stores the public Bluetooth address from scans or the visible pairing row so later repair attempts can still find it. Manual **Repair Pair Now** may select the only focused/visible discovered device if no saved address is available; automatic repair does not use that broad fallback.

If repair pairing reaches a canceled/not-ready state such as `Pairing canceled` or `ถูกยกเลิก`, the app does not silently return Home. It opens the pairing assist prompt and retries only while that prompt is visible.

## Accessibility Automation

When the target speaker appears disconnected, the app opens Bluetooth Settings and the Accessibility Service:

- avoids resuming the last unrelated Settings page when direct Bluetooth Settings is unavailable;
- can open a fresh Settings home, scroll the Settings navigation list, and click only exact Bluetooth / Accessories / Remotes & Accessories route labels;
- searches the active window tree for the configured speaker name;
- searches for `Connect` or `เชื่อมต่อ`;
- clicks the Connect action first when it is in the configured speaker context, including saved-device pages where the speaker name is text-only and the action is a sibling button;
- otherwise focuses the configured speaker row to expose its detail Connect action, then activates only a verified Connect action;
- if that saved-device progress state gets stuck, backs out once, resets the click state, and retries the target-context Connect search; if needed it then relaunches fresh Settings once before failing/backing off;
- detects `Connected` or `เชื่อมต่อแล้ว` only when it is not a disconnected label such as `เลิกเชื่อมต่อแล้ว`, then confirms the target is connected through public A2DP state before treating automation as successful;
- retries up to the configured max retry count;
- returns to Home after success or failure.

### Connect wait recovery (v0.16)

On two-pane Google TV Settings, the service includes otherwise omitted accessibility views so the saved-speaker Connect preview can be read. It scrolls the left vertical navigation list, ignores unrelated Apps/Security preview content when the left pane is verified as Settings home, and finishes opening the device-list route before selecting a speaker.

Some Chromecast builds show a second “Connect to <speaker>” / “เชื่อมต่อกับ <speaker>” Yes/No confirmation. During an app-owned Connect attempt, the service accepts only that exact target confirmation from the observed system confirmation package. It does not accept generic Yes dialogs. Live monitor also pauses while this confirmation belongs to the user.

After activating Connect, the service checks public A2DP state before considering another click. A Connect label that stays visible, or repeated Accessibility events, cannot restart the wait timer. The normal wait is 12 seconds, extended to 30 seconds only while A2DP reports Connecting.

If the target remains Disconnected after 12 seconds, the service may try one gesture per session at the center of a freshly read, visible, enabled Connect label in the target context. A rejected Accessibility click may use the same fallback immediately. Gestures use the actual display bounds, never a guessed right-pane position. Their completed/cancelled callbacks are observed, with a 4-second callback deadline. A gesture acknowledgement is not connection success: public A2DP must still confirm Connected.

Connect candidates representing the same action are deduplicated; multiple distinct eligible actions are rejected as ambiguous. Clickable ancestors containing Rename, Forget, or Disconnect actions are not activated. If Settings does not expose a safe Connect label, the service reports a bounded failure instead of guessing a location.

After the fallback, the existing one-Back/one-fresh-Settings recovery and configured retry budget apply. Each session has an independent 2-minute deadline, including when no useful window events arrive. Target A2DP checks time out after 4 seconds and deliver only one result; duplicate and late callbacks cannot overwrite a timeout or resume a finished session.

If Google TV opens an unrelated Settings area such as Apps / Security / Play Protect / Developer options, the service does not click or scroll that page. Wrong-destination labels take priority over generic Bluetooth/Accessories side-navigation labels, so a Developer options page is rejected even when a Settings side nav also contains Remotes & Accessories. It reopens a fresh Settings home once, then fails with a visible reason if the destination is still wrong.

The Accessibility Service also watches live windows for a Bluetooth pairing prompt. It will press `Pair`, `Pair device`, `จับคู่`, or `จับคู่อุปกรณ์` only when:

- Auto Connect is on;
- the prompt/window contains the configured target speaker name;
- the prompt/window comes from a Settings, Bluetooth, or system package;
- the prompt/window is not a Wi-Fi / Wireless debugging / ADB pairing screen;
- Thai Wireless debugging screens such as `การแก้ไขข้อบกพร่องผ่าน Wi-Fi` and pair-by-code / QR-code rows are also treated as unsafe;
- the live-pair cooldown has elapsed.

Explicit **Repair Pair Now** sessions use the same target-name and Wi-Fi/ADB guards, and may accept the target prompt as part of that user-started repair flow.

### Startup details and connection-state recovery (v0.17)

Some Settings previews omit Connect until the saved-speaker row is opened. After focusing a disconnected target and waiting for its preview, the service can open that exact row once per session. It requires one focused, visible, enabled, clickable row in the left pane, containing only the target identity and a disconnected status. Containers containing other devices or actions are rejected. Opening details remains navigation; the service must still find a verified Connect action and confirm the target through A2DP.

Target state checks now consult connected A2DP devices before the bonded-device list. A missing bond record cannot override a confirmed connection to the configured address. If only a name is configured, one exact match is required. The live monitor reconciles a manually established connection and clears stale errors/backoff without leaving user-owned Settings or interrupting an active automation session.

## Safety Controls

- **Auto Connect On/Off** gates automatic attempts.
- **Skip while playback is active** uses `AudioManager.isMusicActive()` as best-effort detection.
- **Max retry count** limits Accessibility retries.
- **Cooldown** prevents aggressive app-open, boot, screen-on, and periodic loops. The Accessibility live monitor uses a shorter internal throttle so reconnect can start quickly after a disconnect without starting repair pairing on its own.
- **Periodic lightweight retry** is off by default and uses Android WorkManager minimum interval behavior.

## Known Limitations

- Google TV Settings layouts and labels vary by device, OEM, Android TV OS version, and language.
- Accessibility automation may need device-specific tuning if the Settings UI changes.
- Android may restrict background activity launches after boot on some builds.
- Playback detection is best-effort and may not identify every video or audio session.
- Repair pairing requires the speaker to be powered on, in range, and discoverable/pairing-ready. If Google TV cannot see the speaker in the pairing UI, the app cannot pair it.
- For repair testing, you may forget/unpair the target speaker from Google TV Bluetooth settings, put the speaker into pairing mode, and confirm **Repair Pair Now** starts guarded repair pairing. Live monitor should report that repair is needed without opening the repair flow itself.
- Even after the app selects the right discovered device, pairing can still be canceled by Google TV or the speaker if the speaker is not ready, rejects bonding, exits pairing mode, or has a stale failed bond state.
- Live Pair prompt handling is best-effort. It only accepts prompts that visibly include the configured speaker name; it intentionally ignores generic or Wi-Fi/ADB pairing prompts.
- Direct silent A2DP reconnection is not guaranteed for normal sideload apps because Android exposes reliable public APIs for state checks, but not a supported universal public API for silently connecting an arbitrary A2DP sink.

## Why Not Hidden `BluetoothA2dp.connect`

Some examples use reflection or hidden APIs to call `BluetoothA2dp.connect`. That is not used here because hidden APIs are unsupported, can fail across Android versions/OEM builds, may be blocked by platform restrictions, and are not appropriate as the primary implementation for a sideload app.

BT Speaker Keeper keeps the Bluetooth layer read-only and public, then delegates the actual connect action to the same Settings UI the user can operate manually.
