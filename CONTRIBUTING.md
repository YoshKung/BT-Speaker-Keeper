# Contributing

BT Speaker Keeper is intentionally conservative around Bluetooth control.

## Development rules

- Use public Android Bluetooth APIs only for Bluetooth state and paired-device checks.
- Do not add hidden/private `BluetoothA2dp.connect` reflection as the primary reconnect path.
- Keep Accessibility automation guarded by the selected target name or stored Bluetooth address.
- Keep Wi-Fi, Wireless debugging, and ADB pairing prompts excluded from automatic Pair clicks.
- Keep the Android TV UI usable with a D-pad remote; focus should land on real buttons/actions.

## Validation

Before opening a pull request, run:

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

If you change Settings automation text matching, add or update unit tests for English and Thai labels.
