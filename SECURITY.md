# Security

## Reporting a problem

Please avoid posting Wi-Fi passwords, private IP layouts, signing keys, or other credentials in a public issue. For ordinary bugs, open an issue with the app version, platform, bulb model, and the error message after removing private details.

## Local-network design

- The desktop server binds to `127.0.0.1` only.
- Bulb commands are restricted to private IPv4 addresses.
- Wi-Fi passwords are sent directly to the bulb during setup and are not saved by the controllers.
- The Android app contains no login, analytics, advertising, or cloud SDK.
- Android signing keys are intentionally excluded from this repository.
