# BT Speaker Keeper

BT Speaker Keeper is a sideloadable Android TV / Google TV utility that helps keep one selected Bluetooth speaker connected as automatically as public Android APIs allow.

The app uses public Bluetooth APIs only to check paired devices and A2DP connection state. It does not call hidden or private `BluetoothA2dp.connect`. When the target speaker is disconnected, it uses an Accessibility Service to automate the existing Google TV Settings flow.

## What it does

- Scans connected and paired Bluetooth audio devices.
- Lets you choose one target speaker and toggle Auto Connect.
- Checks whether the speaker is already connected before launching Settings automation.
- Attempts reconnect after app open, boot delay, screen-on while the app process is alive, optional periodic retry, and a lightweight Accessibility live monitor.
- Can start a repair-pairing flow if the target speaker disappears from known devices.
- Rejects Wi-Fi / Wireless debugging / ADB pairing prompts.

## Install from GitHub Release

1. Download the latest APK from the GitHub Releases page.
2. Enable Developer options on Google TV.
3. Enable USB debugging or Network debugging.
4. Connect from your computer:

```bash
adb connect <GOOGLE_TV_IP_ADDRESS>
adb devices
```

5. Install the APK:

```bash
adb install -r BT-Speaker-Keeper-v0.1-debug.apk
```

6. Open **BT Speaker Keeper** from Apps.

The `v0.1` APK is debug-signed for sideload testing.

## One-time setup

1. Pair the Bluetooth speaker in Google TV Settings first.
2. Open **BT Speaker Keeper**.
3. Grant **Nearby Devices** / Bluetooth permission when prompted.
4. Connect the desired speaker manually once in Google TV Settings.
5. Press **Scan Known Devices** in BT Speaker Keeper.
6. Press the speaker row. Pressing the same row toggles Auto Connect on or off.
7. Open Accessibility settings from the app.
8. Enable **BT Speaker Keeper Automation** manually.
9. Return to the app and press **Connect Now** to test.

If the speaker later disappears from Google TV's known-device list, turn the speaker on and put it in pairing/discoverable mode, then press **Repair Pair Now**. If pairing cannot continue because the speaker is not visible or not ready, the app opens a full-screen pairing assist prompt and retries while that screen remains open.

## Runtime behavior

Before any UI automation runs, the app checks the target speaker with public Bluetooth A2DP state APIs:

- `BluetoothAdapter.bondedDevices`
- `BluetoothA2dp.getConnectedDevices()`
- `BluetoothA2dp.getConnectionState(device)`

If the speaker is already connected, the app records success and does not open Settings.

If disconnected, the app opens Bluetooth / Remotes & Accessories Settings and the Accessibility Service searches for:

- the configured speaker name or stored Bluetooth address;
- `Connect` / `เชื่อมต่อ`;
- `Connected` / `เชื่อมต่อแล้ว`;
- `Pair`, `Pair device`, `จับคู่`, or `จับคู่อุปกรณ์` only for the configured target.

The live monitor checks the configured target every 5 seconds while Auto Connect is on. It bypasses the long user cooldown so repair can start quickly after a disconnect, but still respects playback-skip safety, avoids concurrent sessions, and throttles Settings launches.

## Safety controls

- **Auto Connect On/Off** gates automatic attempts.
- **Skip while playback is active** uses `AudioManager.isMusicActive()` as best-effort detection.
- **Max retry count** limits Accessibility retries.
- **Cooldown** prevents aggressive app-open, boot, screen-on, and periodic loops.
- **Periodic lightweight retry** is off by default.

## Troubleshooting

### Speaker is not visible

Put the speaker into Bluetooth pairing mode and keep the pairing assist prompt open. The app can only select a speaker that Google TV exposes in the pairing UI.

### Bluetooth Pair prompt is ignored

The prompt must visibly include the configured target speaker name or a matched repair target. Generic Pair prompts are ignored by design.

### Wireless debugging prompt is ignored

This is expected. The app intentionally ignores Wi-Fi / Wireless debugging / ADB pairing screens even if they contain pairing text.

### No reconnect while video is playing

Turn off **Skip while playback is active** if you want automatic attempts even when Android reports active media playback.

## Build from source

Requirements:

- JDK 17
- Android SDK API 36
- Android SDK build tools

Build commands:

```bash
./gradlew test
./gradlew lint
./gradlew assembleDebug
```

On Windows PowerShell:

```powershell
.\gradlew.bat test
.\gradlew.bat lint
.\gradlew.bat assembleDebug
```

The debug APK is created under:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Known limitations

- Google TV Settings layouts and labels vary by device, OEM, Android TV OS version, and language.
- Accessibility automation may need device-specific tuning if the Settings UI changes.
- Android may restrict background activity launches after boot on some builds.
- Playback detection is best-effort and may not identify every video or audio session.
- Repair pairing requires the speaker to be powered on, in range, and discoverable/pairing-ready.
- Direct silent A2DP reconnection is not guaranteed for normal sideload apps because Android exposes reliable public APIs for state checks, but not a supported universal public API for silently connecting an arbitrary A2DP sink.

## License

MIT
