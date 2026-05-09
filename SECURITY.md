# Security

## Accessibility Service scope

BT Speaker Keeper uses an Accessibility Service to automate the same Google TV Bluetooth Settings screens a user can operate manually.

The service is intended to:

- read visible Settings window text;
- find the configured speaker by name or stored Bluetooth address;
- click Bluetooth `Connect` / `Pair` actions only for the configured target;
- reject Wi-Fi, Wireless debugging, and ADB pairing prompts.

The app does not use Accessibility for account, password, payment, browser, or unrelated app automation.

## Reporting issues

When reporting a bug, redact personal information from screenshots and logs, including:

- Bluetooth MAC addresses;
- home network IP addresses;
- Wireless debugging pairing codes;
- account names or profile photos.

For public issues, describe the Google TV model, Android TV OS version, app version, speaker model, and language setting when relevant.
