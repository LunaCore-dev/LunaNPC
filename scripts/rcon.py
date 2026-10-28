"""Send console commands to the local dev server over RCON: python scripts/rcon.py "<cmd>" ["<cmd>" ...]

Needs enable-rcon=true and an rcon.password in run/server.properties (see the dev-testing note in README.md).
"""
import os
import socket
import struct
import sys

PROPERTIES = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "run", "server.properties")


def read_properties():
    props = {}
    with open(PROPERTIES, encoding="utf-8") as file:
        for line in file:
            if "=" in line and not line.startswith("#"):
                key, value = line.rstrip("\n").split("=", 1)
                props[key] = value
    return props


def packet(request_id, kind, body):
    data = struct.pack("<ii", request_id, kind) + body.encode() + b"\x00\x00"
    return struct.pack("<i", len(data)) + data


def receive(sock):
    length = struct.unpack("<i", sock.recv(4))[0]
    data = b""
    while len(data) < length:
        data += sock.recv(length - len(data))
    return struct.unpack("<i", data[:4])[0], data[8:-2].decode("utf-8", "replace")


def main():
    props = read_properties()
    if props.get("enable-rcon") != "true" or not props.get("rcon.password"):
        sys.exit("RCON is off: set enable-rcon=true and rcon.password in run/server.properties, then restart the server.")
    sock = socket.create_connection(("127.0.0.1", int(props.get("rcon.port", "25575"))), timeout=10)
    sock.sendall(packet(1, 3, props["rcon.password"]))
    if receive(sock)[0] == -1:
        sys.exit("RCON login failed: wrong password.")
    for command in sys.argv[1:]:
        sock.sendall(packet(2, 2, command))
        print(">", command)
        print(receive(sock)[1])


if __name__ == "__main__":
    main()
