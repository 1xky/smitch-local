Smitch Local for macOS
======================

What you need
-------------
- A recent version of macOS
- Python 3.10 or newer from https://www.python.org/downloads/macos/
- A compatible Smitch Wi-Fi bulb

Start the controller
--------------------
1. Extract this ZIP to a normal folder.
2. Double-click Start-Smitch.command.
3. Your browser should open http://127.0.0.1:8765 automatically.
4. Keep the Terminal window open while using the controller.

If macOS blocks it, Control-click Start-Smitch.command, choose Open, then
confirm Open.

Recommended: use the bulb on home Wi-Fi
----------------------------------------
Your Mac and bulb must be on the same home network. The bulb normally needs a
2.4 GHz network; your Mac can use another band on the same router.

1. Open Smitch Local and select Find bulbs on Wi-Fi.
2. Select your bulb from the results.
3. If it is not found, find its local IP in your router's connected-device list,
   then use Enter an IP address in Smitch Local. Leave port 80.
4. Use the controls shown for your bulb.

Direct connection to the bulb hotspot
-------------------------------------
1. Open Control Centre, select Wi-Fi, and join the network named Smitch.
2. Stay connected even if macOS says the network has no internet.
3. Return to Smitch Local and select Connect to hotspot.
4. Control the bulb. Reconnect to normal Wi-Fi when finished.

Set up the bulb on home Wi-Fi
-----------------------------
1. Connect the Mac to the bulb's Smitch Wi-Fi hotspot.
2. In Smitch Local, select Connect to hotspot.
3. Open Set up home Wi-Fi once.
4. Enter your 2.4 GHz home Wi-Fi name and password, then send the settings.
5. Wait about 30 seconds for the bulb to restart and join your router.
6. Reconnect the Mac to your normal home Wi-Fi.
7. Select Find bulbs on Wi-Fi, or enter the bulb's IP from your router.

If setup fails, reconnect to the bulb hotspot and retry. Some bulb models or
firmware versions may support only direct hotspot control.

Troubleshooting
---------------
- If permission is denied, run: chmod +x Start-Smitch.command
- If port 8765 is busy, run: python3 smitch.py --port 8766
- Guest Wi-Fi may isolate devices; use the main home network.
- Never share your Wi-Fi password in a public issue or screenshot.
