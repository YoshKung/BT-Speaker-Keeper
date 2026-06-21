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

If the target speaker is no longer bonded/paired and Auto Connect is on, the app can start a repair pairing attempt. On Google TV it first tries the accessory-pairing action `com.google.android.intent.action.CONNECT_INPUT`, then falls back to Bluetooth/device-picker/settings actions. It selects the configured speaker when visible by name or stored Bluetooth address, accepts the Bluetooth pair prompt, waits briefly, and checks A2DP state again.

The live monitor uses public A2DP state checks only. It can bypass the long user cooldown so repair starts quickly after a disconnect, but it still requires Auto Connect, skips while playback is active when that safety control is enabled, avoids concurrent sessions, and throttles Settings launches.

When the saved alias disappears, Google TV may show the same speaker under a factory discovery name. BT Speaker Keeper stores the public Bluetooth address from scans or the visible pairing row so later repair attempts can still find it. Manual **Repair Pair Now** may select the only focused/visible discovered device if no saved address is available; automatic repair does not use that broad fallback.

If repair pairing reaches a canceled/not-ready state such as `Pairing canceled` or `ถูกยกเลิก`, the app does not silently return Home. It opens the pairing assist prompt and retries only while that prompt is visible.

## Accessibility Automation

When the target speaker appears disconnected, the app opens Bluetooth Settings and the Accessibility Service:

- avoids resuming the last unrelated Settings page when direct Bluetooth Settings is unavailable;
- can open a fresh Settings home and navigate only through Bluetooth / Accessories / Remotes & Accessories labels;
- searches the active window tree for the configured speaker name;
- clicks the speaker row or clickable parent;
- searches for `Connect` or `เชื่อมต่อ`;
- clicks the Connect action when found;
- detects `Connected` or `เชื่อมต่อแล้ว`;
- retries up to the configured max retry count;
- returns to Home after success or failure.

If Google TV opens an unrelated Settings area such as Apps / Security / Play Protect / Developer options, the service does not click or scroll that page. It reopens a fresh Settings home once, then fails with a visible reason if the destination is still wrong.

The Accessibility Service also watches live windows for a Bluetooth pairing prompt. It will press `Pair`, `Pair device`, `จับคู่`, or `จับคู่อุปกรณ์` only when:

- Auto Connect is on;
- the prompt/window contains the configured target speaker name;
- the prompt/window comes from a Settings, Bluetooth, or system package;
- the prompt/window is not a Wi-Fi / Wireless debugging / ADB pairing screen;
- the live-pair cooldown has elapsed.

Explicit **Repair Pair Now** sessions use the same target-name and Wi-Fi/ADB guards, and may accept the target prompt as part of that user-started repair flow.

## Safety Controls

- **Auto Connect On/Off** gates automatic attempts.
- **Skip while playback is active** uses `AudioManager.isMusicActive()` as best-effort detection.
- **Max retry count** limits Accessibility retries.
- **Cooldown** prevents aggressive app-open, boot, screen-on, and periodic loops. The Accessibility live monitor uses a shorter internal throttle so repair can start quickly after a disconnect.
- **Periodic lightweight retry** is off by default and uses Android WorkManager minimum interval behavior.

## Known Limitations

- Google TV Settings layouts and labels vary by device, OEM, Android TV OS version, and language.
- Accessibility automation may need device-specific tuning if the Settings UI changes.
- Android may restrict background activity launches after boot on some builds.
- Playback detection is best-effort and may not identify every video or audio session.
- Repair pairing requires the speaker to be powered on, in range, and discoverable/pairing-ready. If Google TV cannot see the speaker in the pairing UI, the app cannot pair it.
- For repair testing, you may forget/unpair the target speaker from Google TV Bluetooth settings, put the speaker into pairing mode, and confirm BT Speaker Keeper starts repair pairing automatically.
- Even after the app selects the right discovered device, pairing can still be canceled by Google TV or the speaker if the speaker is not ready, rejects bonding, exits pairing mode, or has a stale failed bond state.
- Live Pair prompt handling is best-effort. It only accepts prompts that visibly include the configured speaker name; it intentionally ignores generic or Wi-Fi/ADB pairing prompts.
- Direct silent A2DP reconnection is not guaranteed for normal sideload apps because Android exposes reliable public APIs for state checks, but not a supported universal public API for silently connecting an arbitrary A2DP sink.

## Why Not Hidden `BluetoothA2dp.connect`

Some examples use reflection or hidden APIs to call `BluetoothA2dp.connect`. That is not used here because hidden APIs are unsupported, can fail across Android versions/OEM builds, may be blocked by platform restrictions, and are not appropriate as the primary implementation for a sideload app.

BT Speaker Keeper keeps the Bluetooth layer read-only and public, then delegates the actual connect action to the same Settings UI the user can operate manually.
