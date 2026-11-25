# Smitch Local for Android

Install `Smitch-Local-Android.apk` on your Android phone (Android 6.0 or newer, with an up-to-date Android System WebView). If Android asks, allow installation from the app you used to open the APK. Open **Smitch Local**; there is no account, login, subscription, or PC server to run.

This is a separate controller using the stock Smitch local TCP commands. It installs alongside the original app and the earlier modified app. It does not modify the bulb firmware.

## Connect

**Recommended: home Wi-Fi.** If the bulb is already on your router, connect your phone to that router and tap **Find bulbs on this Wi-Fi**. Select a result. You can also find the bulb in your router's connected-device list, then enter its IP address and port (usually 80). The app remembers the last successfully connected home-network IP and port.

**Direct hotspot.** Open Wi-Fi settings from the app, join the bulb's Smitch network, and choose to stay connected if Android reports no internet. Return to the app and tap **Connect to bulb hotspot**. It uses `192.168.4.1:80`. Local sockets explicitly use Wi-Fi so mobile data does not take priority over the bulb connection.

**Set up home Wi-Fi once.** While on the bulb hotspot, open **Set up home Wi-Fi once**, enter your 2.4 GHz Wi-Fi name and password, and tap **Send Wi-Fi settings**. Wait about 30 seconds, reconnect your phone to home Wi-Fi, then find the bulb. The password is sent directly over the bulb's local connection and is not saved by this app. It is cleared from the form before sending and when the app is hidden.

Home-network control depends on the model/firmware accepting local commands after joining the router. It has not been confirmed on a physical bulb. If it fails, use the direct hotspot path. The app cannot control a disconnected bulb over mobile data or the internet.

## Controls and discovery

- Power, brightness, RGB hue/saturation, and white temperature when the bulb reports support.
- Controls send on slider release. “Command sent” means a local socket write completed; **Refresh status** reads the bulb's report. It does not claim that the hardware acknowledged an unconfirmed change.
- Discovery checks TCP port 80 on the phone's current private IPv4 subnet, capped at its /24 segment (at most 254 addresses). It validates the Smitch model and MAC response before showing a result. A saved address on the current subnet is also checked, including its saved port.
- Other local subnets and ports can be entered manually. Guest Wi-Fi isolation, VPN rules, a changed DHCP address, or firmware differences can prevent access. Only use the app with your own bulb/network.
- No Wi-Fi scan/location, microphone, camera, storage, or account permission. The only permissions are Internet (needed for local sockets) and network state. There are no cloud SDKs or remote web assets.

## Source and builds

The public source does not include the private application signing key used by the downloadable APK. Keep your own key private when building updates. A locally generated key or Android Studio's debug key is fine for personal test builds, but builds signed with a different key cannot update the downloadable APK in place.

**Android Studio:** Open this folder as a Gradle project, use JDK 17+ and Android SDK 35, sync, and build a debug APK for local testing. For a signed release, set `SMITCH_KEYSTORE`, `SMITCH_STORE_PASSWORD`, `SMITCH_KEY_ALIAS`, and `SMITCH_KEY_PASSWORD` in the build environment before running `assembleRelease`. The build bundles the local HTML/CSS/JS. Gradle dependencies are required for this route.

**Offline build used for the supplied APK:** Run `python build.py` (or `Build-Windows.cmd`). It uses the JDK, apktool, DX embedded in the JADX bundle, apksig, and framework resources already present on the current PC. On another PC, set `SMITCH_JAVA_HOME`, `SMITCH_BUILD_TOOLS`, and `SMITCH_FRAMEWORK` to the relevant local paths. No Android API stubs are included in the resulting APK; the declarations in `build.py` are used only during compilation. The APK is signed and verified with v1, v2, and v3 signatures.

## Validation and limits

`python test.py` checks command bytes, model capabilities, input bounds, Wi-Fi provisioning order, CR framing, fragmented status reads, non-bulb replies, and timeouts using simulated socket streams. `node tests/ui-test.cjs` checks bridge routing, discovery selection, controls, error handling, capability visibility, and password clearing.

The supplied APK compiled successfully, passed those tests, and had its manifest and signatures verified. Installation, Android WebView execution, Wi-Fi routing, and real bulb behavior have not been tested on an Android device. The original My Smitch 2.9.7 APK and the existing PC controller were used as protocol references.
