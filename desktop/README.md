# Smitch Local

Control a stock Smitch Wi-Fi bulb from a browser on your laptop. The app uses the bulb’s local TCP protocol and runs without an account or internet connection. It supports power, brightness, RGB color on color bulbs, and warm-to-cool white on bulbs with adjustable temperature.

## Start

1. Extract this ZIP.
2. Install Python 3.10 or newer if you don’t already have it. There are no Python packages to install.
3. Start the app:
   - **Windows:** double-click **Start-Smitch.cmd**.
   - **macOS:** double-click **Start-Smitch.command**. If macOS blocks it the first time, Control-click it, choose **Open**, then confirm **Open**.
   - **Linux:** open a terminal in the extracted folder and run `sh Start-Smitch.sh`.
4. The browser should open at [http://127.0.0.1:8765](http://127.0.0.1:8765). Keep the launcher window open while you use the controls.

If the port is busy, run `python smitch.py --port 8766` on Windows or `python3 smitch.py --port 8766` on macOS or Linux.

## Connect

- **Already on home Wi-Fi.** Click **Find bulbs on Wi-Fi** and select the bulb. If it doesn’t show up, check your router’s connected-devices list and use **Enter an IP address**. Start with port 80. The browser remembers the last successfully connected home-network address.
- **On its own Wi-Fi.** In your laptop’s network settings, join the bulb’s **Smitch** network and stay connected if the laptop says there’s no internet. Click **Connect to hotspot**. You can keep using the controller tab while connected to the hotspot. If the bulb’s network is hidden because it was already set up, try the home-network path first. Follow your model’s setup instructions to re-enable its hotspot if needed.
- **Move it to home Wi-Fi.** While connected to the bulb’s hotspot, open **Set up home Wi-Fi once** and enter your 2.4 GHz home Wi-Fi name and password. Wait about 30 seconds, rejoin home Wi-Fi on the laptop, then use discovery or the router’s device list. The controller sends the password to the bulb and clears the password field. It does not save the password.

If you can use the bulb on its hotspot but cannot control it on your home network, use **Connect to hotspot** as the fallback. Some firmware versions may differ and guest networks may isolate devices. If a previously working home-network address changes, find the bulb again.

## Compatibility

The direct controls and Wi-Fi setup sequence come from My Smitch Android 2.9.7. The APK also queries bulb status over its home-network TCP endpoint. This app tries the same light-control commands on that endpoint. Both paths were exercised with a simulated bulb. Physical bulb support and home-network control depend on the model and firmware, and have not been verified on hardware.

The web server listens only on the laptop that runs it. The original Android app and bulb firmware are not modified.
