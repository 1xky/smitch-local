# Smitch Local

Control compatible stock Smitch Wi-Fi bulbs locally, without logging in or relying on the discontinued cloud service.

Smitch Local talks directly to the bulb over your Wi-Fi network. The desktop controller runs in a local browser tab on Windows, macOS, or Linux, while the Android app is a standalone controller.

## Downloads

| Platform | Download | Requirements |
| --- | --- | --- |
| Android | [Smitch Local APK](downloads/Smitch-Local-Android.apk) | Android 6.0 or newer |
| Windows | [Windows ZIP](downloads/Smitch-Local-Windows.zip) | Python 3.10+ |
| macOS | [macOS ZIP](downloads/Smitch-Local-macOS.zip) | Python 3.10+ |
| Linux | [Linux ZIP](downloads/Smitch-Local-Linux.zip) | Python 3.10+ |

You can also use the [project website](https://1xky.github.io/smitch-local/) for a simpler download and setup guide.

## Quick start

### Android

Download the APK, allow installation from the browser or file manager when Android asks, then open **Smitch Local**. The app requests only network access and network-state permissions.

### Windows

Extract the desktop ZIP and double-click `Start-Smitch.cmd`.

### macOS

Extract the ZIP and double-click `Start-Smitch.command`. If macOS blocks it the first time, Control-click the file, choose **Open**, and confirm.

### Linux

Extract the ZIP, open a terminal inside the folder, and run:

```sh
sh Start-Smitch.sh
```

The desktop controller opens at `http://127.0.0.1:8765`. It listens only on the computer running it.

## Connecting a bulb

The recommended setup is to put the bulb and your phone/computer on the same 2.4 GHz home Wi-Fi network. Use **Find bulbs on Wi-Fi**, or enter the bulb's local IP address from your router's connected-device list.

You can also connect directly to the bulb's `Smitch` hotspot. While connected to that hotspot, the controller can send your home Wi-Fi details to the bulb once. Firmware behavior varies, so the hotspot remains the fallback when home-network control is unavailable.

## Repository layout

- `desktop/` — dependency-free Python web controller and launchers
- `android/` — Android app source and tests
- `downloads/` - ready-to-use APK and separate Windows, macOS, and Linux ZIPs
- `docs/` — GitHub Pages download and setup site

## Development

The desktop version uses only Python's standard library:

```sh
python3 desktop/smitch.py --no-browser
```

Android Studio can build the Android project with JDK 17+ and Android SDK 35. The included offline build script documents the toolchain used for the supplied APK. See [android/README.md](android/README.md) for details.

## Compatibility and safety

The local command set was derived from My Smitch Android 2.9.7 and checked against simulated bulb responses. Physical-bulb behavior depends on the model and firmware. Use the project only with bulbs and Wi-Fi networks you own or are allowed to manage.

This is an independent community project. It is not affiliated with or endorsed by Smitch.
