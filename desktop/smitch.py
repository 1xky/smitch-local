from __future__ import annotations

import argparse
import ipaddress
import json
import re
import socket
import struct
import threading
import time
import webbrowser
from concurrent.futures import ThreadPoolExecutor
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

HOTSPOT = ("192.168.4.1", 80)
NETWORKS = tuple(map(ipaddress.ip_network, (
    "10.0.0.0/8", "172.16.0.0/12", "192.168.0.0/16", "169.254.0.0/16",
)))
ASSETS = Path(__file__).resolve().parent / "web"
SERVICE = "_http._tcp.local"
GATE = threading.Lock()
VERIFIED = {}


class BulbError(Exception):
    pass


def private_ip(value):
    if not isinstance(value, str):
        raise ValueError("Enter the bulb's local IPv4 address.")
    try:
        ip = ipaddress.IPv4Address(value.strip())
    except ipaddress.AddressValueError as exc:
        raise ValueError("Enter the bulb's local IPv4 address.") from exc
    if not any(ip in network for network in NETWORKS):
        raise ValueError("Use an IPv4 address from your local network.")
    return str(ip)


def integer(value, minimum, maximum, label):
    if type(value) is not int or not minimum <= value <= maximum:
        raise ValueError(f"{label} must be between {minimum} and {maximum}.")
    return value


def target(data):
    return (private_ip(data.get("ip")), integer(data.get("port", 80), 1, 65535, "Port"))


def parse_report(raw):
    try:
        report = json.loads(raw.strip(b"\r\n\t \x00"))
    except (ValueError, UnicodeError):
        return None
    if not isinstance(report, dict):
        return None
    model = report.get("model")
    mac = report.get("mac_address")
    if not isinstance(model, str) or not isinstance(mac, str):
        return None
    if not re.fullmatch(r"[0-9a-fA-F]{12}", re.sub(r"[:-]", "", mac)):
        return None
    values = report.get("values")
    values = values[0] if isinstance(values, list) and values and isinstance(values[0], dict) else {}
    white = model.lower().startswith("white bulb")
    rgb = ("rgb" in model.lower() or "color bulb" in model.lower() or
           all(channel in values for channel in ("r", "g", "b"))) and not white
    if not (white or rgb or "bulb" in model.lower()):
        return None

    def number(keys, low, high):
        for key in keys:
            value = values.get(key)
            if type(value) is int and low <= value <= high:
                return value
            if isinstance(value, str) and len(value) < 6 and value.isdecimal():
                parsed = int(value)
                if low <= parsed <= high:
                    return parsed
        return None

    power_keys = ("powerState", "power_state", "power_status", "power")
    power = number(power_keys, 0, 1)
    if power is None:
        power = next((int(values[key]) for key in power_keys if isinstance(values.get(key), bool)), None)
    channels = [number((key,), 0, 255) for key in ("r", "g", "b")]
    temperature = number(("temp", "temperature"), 0, 6500)
    cct = "cct" in model.lower() or (white and " sc" not in model.lower()) or bool(temperature)
    return {
        "model": model[:80], "mac": mac.upper(), "rgb": rgb, "white": white, "cct": cct,
        "power": power, "brightness": number(("brightness",), 0, 100),
        "temperature": temperature if temperature and 2700 <= temperature <= 6500 else None,
        "color": "#" + "".join(f"{value:02x}" for value in channels)
        if all(value is not None for value in channels) else None,
    }


def send(address, command, *, query=False, timeout=2.8):
    with socket.create_connection(address, timeout=timeout) as connection:
        connection.sendall(command.encode("utf-8") + b"\r")
        if not query:
            return
        deadline = time.monotonic() + timeout
        raw = bytearray()
        while len(raw) < 16384 and (remaining := deadline - time.monotonic()) > 0:
            connection.settimeout(remaining)
            try:
                chunk = connection.recv(4096)
            except TimeoutError:
                break
            if not chunk:
                break
            raw.extend(chunk)
            for frame in (raw, *re.split(br"[\r\n\x00]+", raw)):
                report = parse_report(frame)
                if report is not None:
                    return report
        raise BulbError("The address answered, but it didn't return a recognized Smitch bulb status.")


def identify(address, timeout=2.8):
    profile = send(address, "0001xx03", query=True, timeout=timeout)
    profile.update(ip=address[0], port=address[1])
    with GATE:
        VERIFIED[address] = profile
    return profile


def light_command(profile, action, value):
    if action == "power":
        if type(value) is not bool:
            raise ValueError("Choose On or Off.")
        return f"0001xx09xxx{int(value)}"
    if action == "brightness":
        level = integer(value, 5, 100, "Brightness")
        opcode = "02" if profile["white"] else "07"
        return f"0001xx{opcode}{level:03d}"
    if action == "temperature":
        if not profile["cct"]:
            raise ValueError("This bulb did not report adjustable white temperature.")
        kelvin = integer(value, 2700, 6500, "Temperature")
        opcode = "01" if profile["white"] else "10"
        return f"0001xx{opcode}{kelvin}"
    if action == "color":
        if not profile["rgb"]:
            raise ValueError("This bulb did not report RGB support.")
        if not isinstance(value, str) or not re.fullmatch(r"#[0-9a-fA-F]{6}", value):
            raise ValueError("Choose a valid RGB color.")
        rgb = "".join(f"{int(value[index:index+2], 16):03d}" for index in (1, 3, 5))
        return "0001xx01" + rgb
    raise ValueError("Unknown light control.")


def provision(data):
    ssid, password = data.get("ssid"), data.get("password")
    if not isinstance(ssid, str) or not 1 <= len(ssid.encode()) <= 32 or any(ord(c) < 32 for c in ssid):
        raise ValueError("Enter a Wi-Fi name of 1–32 bytes without control characters.")
    if not isinstance(password, str) or any(ord(c) < 32 for c in password):
        raise ValueError("Enter a valid Wi-Fi password.")
    if not 8 <= len(password.encode()) <= 63:
        raise ValueError("Enter a Wi-Fi password of 8–63 bytes.")
    profile = identify(HOTSPOT)
    commands = ["0000xx01" + ssid, "0000xx02" + password, "0000xx05SMTH"]
    if profile["model"].lower() == "white bulb":
        commands.append("0000xx0S11")
    commands.append("0000xx03")
    for command in commands:
        send(HOTSPOT, command)
        time.sleep(0.15)
    with GATE:
        VERIFIED.pop(HOTSPOT, None)
    return {"sent": True}


def dns_name(packet, offset):
    labels = []
    end = None
    visited = set()
    while offset not in visited and offset < len(packet) and len(labels) < 63:
        visited.add(offset)
        length = packet[offset]
        offset += 1
        if length == 0:
            return ".".join(labels).lower(), end if end is not None else offset
        if length >= 192 and offset < len(packet):
            end = offset + 1 if end is None else end
            offset = ((length & 63) << 8) | packet[offset]
            continue
        if length > 63 or offset + length > len(packet):
            break
        labels.append(packet[offset:offset + length].decode("utf-8", "replace"))
        offset += length
    raise ValueError("Invalid DNS name.")


def dns_records(packet):
    _, flags, questions, answers, authority, extra = struct.unpack_from("!6H", packet)
    if not flags & 0x8000:
        return []
    offset = 12
    for _ in range(questions):
        _, offset = dns_name(packet, offset)
        offset += 4
    records = []
    for _ in range(answers + authority + extra):
        name, offset = dns_name(packet, offset)
        kind, _, ttl, length = struct.unpack_from("!HHIH", packet, offset)
        start = offset + 10
        offset = start + length
        if offset > len(packet) or not ttl:
            continue
        if kind == 12:
            records.append((name, kind, dns_name(packet, start)[0]))
        elif kind == 33 and length >= 7:
            port = struct.unpack_from("!H", packet, start + 4)[0]
            records.append((name, kind, (dns_name(packet, start + 6)[0], port)))
        elif kind == 1 and length == 4:
            records.append((name, kind, socket.inet_ntoa(packet[start:offset])))
    return records


def mdns_question(name, kind):
    encoded = (label.encode("utf-8") for label in name.split("."))
    labels = b"".join(bytes([len(label)]) + label for label in encoded)
    return struct.pack("!6H", 1, 0, 1, 0, 0, 0) + labels + b"\0" + struct.pack("!HH", kind, 1)


def mdns_candidates():
    multicast = ("224.0.0.251", 5353)
    instances, services, hosts = set(), {}, {}
    queried = set()
    deadline = time.monotonic() + 2.2
    mid = time.monotonic() + 0.85
    repeated = False
    with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as discovery:
        discovery.bind(("0.0.0.0", 0))
        discovery.setsockopt(socket.IPPROTO_IP, socket.IP_MULTICAST_TTL, 255)
        discovery.sendto(mdns_question(SERVICE, 12), multicast)
        while time.monotonic() < deadline:
            if time.monotonic() > mid and not repeated:
                discovery.sendto(mdns_question(SERVICE, 12), multicast)
                repeated = True
            discovery.settimeout(min(0.25, max(0.01, deadline - time.monotonic())))
            try:
                packet, _ = discovery.recvfrom(8192)
            except TimeoutError:
                continue
            try:
                records = dns_records(packet)
            except (ValueError, struct.error):
                continue
            for name, kind, value in records:
                if kind == 12 and name == SERVICE and value.endswith("." + SERVICE):
                    if re.search(r"smitch|smth|iotmonks", value):
                        instances.add(value)
                elif kind == 33:
                    services[name] = value
                elif kind == 1:
                    hosts.setdefault(name, set()).add(value)
            for instance in list(instances)[:16]:
                if instance not in services:
                    query = (instance, 33)
                elif services[instance][0] not in hosts:
                    query = (services[instance][0], 1)
                else:
                    continue
                if query not in queried:
                    try:
                        discovery.sendto(mdns_question(*query), multicast)
                    except (ValueError, OSError):
                        continue
                    queried.add(query)
    candidates = set()
    for instance in instances:
        if instance not in services:
            continue
        host, port = services[instance]
        for ip in hosts.get(host, ()):
            try:
                candidates.add((private_ip(ip), integer(port, 1, 65535, "Port")))
            except ValueError:
                pass
    return sorted(candidates)[:16]


def discover():
    def check(address):
        try:
            return identify(address, timeout=1.4)
        except (BulbError, OSError):
            return None

    with ThreadPoolExecutor(max_workers=8) as pool:
        return [profile for profile in pool.map(check, mdns_candidates()) if profile]


class Handler(BaseHTTPRequestHandler):
    def setup(self):
        super().setup()
        self.connection.settimeout(8)

    def log_message(self, *_):
        pass

    def respond(self, status, data, content_type="application/json; charset=utf-8"):
        body = json.dumps(data).encode() if isinstance(data, dict) else data
        self.send_response(status)
        self.send_header("Content-Type", content_type)
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Cache-Control", "no-store")
        self.send_header("X-Content-Type-Options", "nosniff")
        self.send_header("Referrer-Policy", "no-referrer")
        self.send_header("Content-Security-Policy", "default-src 'self'; style-src 'self' 'unsafe-inline'; base-uri 'none'; frame-ancestors 'none'; form-action 'none'")
        self.end_headers()
        self.wfile.write(body)

    def good_host(self):
        return self.headers.get("Host") in self.server.hosts

    def do_GET(self):
        if not self.good_host():
            self.respond(403, {"error": "Use the local controller address."})
            return
        files = {
            "/": ("index.html", "text/html"),
            "/app.css": ("app.css", "text/css"),
            "/app.js": ("app.js", "text/javascript"),
        }
        if self.path not in files:
            self.respond(404, {"error": "Page not found."})
            return
        name, content_type = files[self.path]
        self.respond(200, (ASSETS / name).read_bytes(), content_type + "; charset=utf-8")

    def do_POST(self):
        origin = "http://" + self.headers.get("Host", "")
        if not self.good_host() or self.headers.get("Origin") != origin:
            self.respond(403, {"error": "Open the controller from its local browser tab."})
            return
        if self.headers.get_content_type() != "application/json":
            self.respond(415, {"error": "Expected a JSON request."})
            return
        try:
            size = int(self.headers.get("Content-Length", "0"))
            if not 0 < size <= 4096:
                raise ValueError("Invalid request size.")
            data = json.loads(self.rfile.read(size))
            if not isinstance(data, dict):
                raise ValueError("Invalid request.")
            result = self.dispatch(data)
        except ValueError as exc:
            self.respond(400, {"error": str(exc)})
        except BulbError as exc:
            self.respond(502, {"error": str(exc)})
        except OSError:
            self.respond(503, {"error": "Couldn't reach the bulb. Check that it has power and that your laptop is on the right Wi-Fi."})
        else:
            self.respond(200, result)

    def dispatch(self, data):
        if self.path == "/api/connect":
            return identify(target(data))
        if self.path == "/api/discover":
            return {"devices": discover()}
        if self.path == "/api/setup":
            return provision(data)
        if self.path == "/api/light":
            address = target(data)
            with GATE:
                profile = VERIFIED.get(address)
            if profile is None or profile["mac"] != data.get("mac"):
                raise ValueError("Connect to the bulb before sending a command.")
            send(address, light_command(profile, data.get("action"), data.get("value")))
            return {"sent": True}
        raise ValueError("Unknown request.")


def main():
    parser = argparse.ArgumentParser(description="Run the Smitch Local browser controller.")
    parser.add_argument("--port", type=int, default=8765, help="Local web port (default 8765)")
    parser.add_argument("--no-browser", action="store_true", help="Print the URL without opening the browser")
    args = parser.parse_args()
    try:
        server = ThreadingHTTPServer(("127.0.0.1", args.port), Handler)
    except OSError as exc:
        parser.exit(1, f"Could not start the web server: {exc}\nTry --port 8766.\n")
    port = server.server_address[1]
    server.hosts = {f"127.0.0.1:{port}", f"localhost:{port}"}
    url = f"http://127.0.0.1:{port}/"
    print(f"Smitch Local is running at {url}\nKeep this window open. Press Ctrl+C to stop.", flush=True)
    if not args.no_browser:
        threading.Thread(target=webbrowser.open, args=(url,), daemon=True).start()
    with server:
        try:
            server.serve_forever()
        except KeyboardInterrupt:
            pass


if __name__ == "__main__":
    main()
