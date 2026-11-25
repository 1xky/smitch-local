Smitch Local for Linux
======================

What you need
-------------
- A desktop Linux distribution
- Python 3.10 or newer
- A compatible Smitch Wi-Fi bulb

Start the controller
--------------------
1. Extract this ZIP to a normal folder.
2. Open a terminal inside the extracted Smitch-Local-Linux folder.
3. Run: sh Start-Smitch.sh
4. Your browser should open http://127.0.0.1:8765 automatically.
5. Keep the terminal open while using the controller.

No pip packages are needed.

Recommended: use the bulb on home Wi-Fi
----------------------------------------
Your Linux computer and bulb must be on the same home network. The bulb normally
needs a 2.4 GHz Wi-Fi network.

1. Open Smitch Local and select Find bulbs on Wi-Fi.
2. Select your bulb from the results.
3. If it is not found, find its local IP in your router's connected-device list,
   then use Enter an IP address in Smitch Local. Leave port 80.
4. Use the controls shown for your bulb.

Direct connection to the bulb hotspot
-------------------------------------
1. Open your desktop's Wi-Fi settings and join the network named Smitch.
2. Stay connected even if Linux says the network has no internet.
3. Return to Smitch Local and select Connect to hotspot.
4. Control the bulb. Reconnect to normal Wi-Fi when finished.

Set up the bulb on home Wi-Fi
-----------------------------
1. Connect Linux to the bulb's Smitch Wi-Fi hotspot.
2. In Smitch Local, select Connect to hotspot.
3. Open Set up home Wi-Fi once.
4. Enter your 2.4 GHz home Wi-Fi name and password, then send the settings.
5. Wait about 30 seconds for the bulb to restart and join your router.
6. Reconnect Linux to your normal home Wi-Fi.
7. Select Find bulbs on Wi-Fi, or enter the bulb's IP from your router.

If setup fails, reconnect to the bulb hotspot and retry. Some bulb models or
firmware versions may support only direct hotspot control.

Troubleshooting
---------------
- Install the python3 package if Python is missing.
- If port 8765 is busy, run: python3 smitch.py --port 8766
- Guest Wi-Fi may isolate devices; use the main home network.
- Never share your Wi-Fi password in a public issue or screenshot.
