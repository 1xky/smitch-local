"use strict";

const byId = (id) => document.getElementById(id);
const hotspot = { ip: "192.168.4.1", port: 80 };
const storageKey = "smitch-local-bulb";
let profile;
let busy = false;
let controlBusy = false;
let lastSent = {};

async function request(route, data = {}) {
  const response = await fetch("/api/" + route, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    mode: "same-origin",
    body: JSON.stringify(data),
  });
  const result = await response.json();
  if (!response.ok) throw new Error(result.error || "The controller couldn't complete the request.");
  return result;
}

function feedback(id, message, error = false) {
  byId(id).textContent = message;
  byId(id).dataset.error = String(error);
}

function render() {
  const connected = Boolean(profile);
  byId("connection").textContent = connected ? "Connected" : "Disconnected";
  byId("connection").dataset.state = connected ? "connected" : "idle";
  byId("controls").disabled = !connected || busy;
  byId("device").textContent = connected
    ? profile.model + " · " + profile.ip + (profile.port === 80 ? "" : ":" + profile.port)
    : "Connect a Smitch bulb to use its controls.";

  const power = lastSent.power ?? (profile?.power != null ? profile.power === 1 : undefined);
  byId("on").setAttribute("aria-pressed", String(power === true));
  byId("off").setAttribute("aria-pressed", String(power === false));
  byId("brightness-out").value = profile && (lastSent.brightness ?? profile.brightness) !== null
    ? (lastSent.brightness ?? profile.brightness) + "%" : "—";
  byId("temperature-out").value = profile && (lastSent.temperature ?? profile.temperature) !== null
    ? (lastSent.temperature ?? profile.temperature) + " K" : "—";
  byId("temperature-row").hidden = !profile?.cct;
  byId("color-row").hidden = !profile?.rgb;
  const brightness = lastSent.brightness ?? profile?.brightness ?? 75;
  const temperature = lastSent.temperature ?? profile?.temperature ?? 4000;
  const color = lastSent.color ?? profile?.color ?? "#f5d5a3";
  const warm = (6500 - temperature) / 3800;
  const tint = `rgb(255, ${Math.round(252 - warm * 56)}, ${Math.round(245 - warm * 115)})`;
  const preview = byId("preview");
  preview.style.setProperty("--lamp", connected ? (lastSent.mode === "white" || !profile.rgb ? tint : color) : "#a09d8e");
  preview.style.setProperty("--level", connected && power !== false ? brightness / 100 : ".14");
}

function setProfile(device) {
  profile = device;
  lastSent = {};
  byId("brightness").value = Math.max(5, device?.brightness ?? 100);
  byId("temperature").value = device?.temperature ?? 4000;
  byId("color").value = device?.color ?? "#f5b95f";
  feedback("light-status", "");
  render();
}

function saveTarget(device) {
  if (device.ip === hotspot.ip) return;
  try {
    localStorage.setItem(storageKey, JSON.stringify({ ip: device.ip, port: device.port, mac: device.mac }));
  } catch {
    // Controls also work with browser storage disabled.
  }
}

async function networkAction(task, message) {
  if (busy || controlBusy) return;
  busy = true;
  document.querySelectorAll(".network button").forEach((button) => { button.disabled = true; });
  feedback("network-status", message);
  render();
  try {
    await task();
  } catch (error) {
    feedback("network-status", error.message, true);
  } finally {
    busy = false;
    document.querySelectorAll(".network button").forEach((button) => { button.disabled = false; });
    render();
  }
}

function connect(address, remembered = false) {
  return networkAction(async () => {
    const device = await request("connect", address);
    if (remembered && address.mac && device.mac !== address.mac) {
      throw new Error("The saved address now belongs to a different bulb. Find your bulb again on Wi-Fi.");
    }
    setProfile(device);
    saveTarget(profile);
    feedback("network-status", "Bulb ready.");
  }, "Checking the bulb…");
}

async function control(action, value) {
  if (!profile || busy || controlBusy) return;
  controlBusy = true;
  byId("controls").disabled = true;
  try {
    await request("light", { ip: profile.ip, port: profile.port, mac: profile.mac, action, value });
    lastSent[action] = value;
    if (action === "color" || action === "temperature") lastSent.mode = action === "color" ? "color" : "white";
    feedback("light-status", "Command sent.");
  } catch (error) {
    feedback("light-status", error.message, true);
  } finally {
    controlBusy = false;
    byId("brightness").value = Math.max(5, lastSent.brightness ?? profile.brightness ?? 100);
    byId("temperature").value = lastSent.temperature ?? profile.temperature ?? 4000;
    byId("color").value = lastSent.color ?? profile.color ?? "#f5b95f";
    render();
  }
}

byId("discover").addEventListener("click", () => networkAction(async () => {
  byId("results").replaceChildren();
  const { devices } = await request("discover");
  for (const device of devices) {
    const button = document.createElement("button");
    button.type = "button";
    button.textContent = `${device.model} · ${device.ip}`;
    button.addEventListener("click", () => connect(device));
    byId("results").append(button);
  }
  feedback("network-status", devices.length
    ? "Select your bulb."
    : "No bulbs found. Try its IP address from your router’s device list, or use the bulb hotspot.");
}, "Looking for Smitch bulbs on your Wi-Fi…"));

byId("manual").addEventListener("submit", (event) => {
  event.preventDefault();
  connect({ ip: byId("ip").value, port: byId("port").valueAsNumber });
});
byId("hotspot").addEventListener("click", () => connect(hotspot));
byId("setup").addEventListener("submit", (event) => {
  event.preventDefault();
  networkAction(async () => {
    await request("setup", { ssid: byId("ssid").value, password: byId("password").value });
    byId("password").value = "";
    if (profile?.ip === hotspot.ip) setProfile(null);
    feedback("network-status", "Wi-Fi settings sent. Wait about 30 seconds, rejoin your home Wi-Fi, then select Find bulbs on Wi-Fi.");
  }, "Sending Wi-Fi settings to the bulb…");
});
byId("off").addEventListener("click", () => control("power", false));
byId("on").addEventListener("click", () => control("power", true));
for (const [action, unit] of [["brightness", "%"], ["temperature", " K"]]) {
  byId(action).addEventListener("input", (event) => {
    byId(action + "-out").value = event.target.value + unit;
  });
  byId(action).addEventListener("change", (event) => control(action, event.target.valueAsNumber));
}
byId("color").addEventListener("change", (event) => control("color", event.target.value));
document.querySelectorAll("[data-color]").forEach((button) => {
  button.addEventListener("click", () => control("color", button.dataset.color));
});

render();
try {
  const saved = JSON.parse(localStorage.getItem(storageKey));
  if (saved) {
    byId("ip").value = saved.ip;
    byId("port").value = saved.port;
    connect(saved, true);
  }
} catch {
  // The Wi-Fi controls are usable without a remembered address.
}
