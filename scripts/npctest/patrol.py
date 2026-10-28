"""Patrol test on /lunatest patrol (the dev test mod): builds a flat arena, sets up the two test routes and
one patrol NPC per mode through the API, and samples the point each one heads to.

Usage: python scripts/npctest/patrol.py [seconds] [keep]
With "keep" the NPCs stay (e.g. to check the chunk loader after a restart); otherwise /lunatest clear runs.
"""
import re
import socket
import sys
import time

sys.path.insert(0, __file__.rsplit("npctest", 1)[0])
import rcon

props = rcon.read_properties()
sock = socket.create_connection(("127.0.0.1", int(props["rcon.port"])), timeout=15)
sock.sendall(rcon.packet(1, 3, props["rcon.password"]))
rcon.receive(sock)


def rc(command):
    sock.sendall(rcon.packet(2, 2, command))
    return rcon.receive(sock)[1].replace("§", "&")


seconds = float(sys.argv[1]) if len(sys.argv) > 1 else 30.0
keep = "keep" in sys.argv[2:]
X, Y, Z = 20100, 150, 20100
for command in [f"forceload add {X - 40} {Z - 10} {X + 40} {Z + 30}",
                f"fill {X - 35} {Y - 1} {Z - 5} {X + 35} {Y - 1} {Z + 25} minecraft:stone",
                f"fill {X - 35} {Y} {Z - 5} {X + 35} {Y + 4} {Z + 25} minecraft:air"]:
    rc(command)
print(rc(f"execute positioned {X}.5 {Y} {Z}.5 rotated 0 0 run lunatest patrol").replace("TestPatrol", "\nTestPatrol"))
points = {}
start = time.time()
while time.time() - start < seconds:
    for line in re.split(r"(?=TestPatrol\w+ @ )", rc("lunatest status")):
        if line.startswith("TestPatrol") and "| point " in line:
            name, point = line.split(" @ ")[0], line.rsplit("| point ", 1)[1].strip()
            visits = points.setdefault(name, [])
            if not visits or visits[-1] != point:
                visits.append(point)
    time.sleep(0.5)
for name, visits in points.items():
    print(name, "headed to points:", " ".join(visits))
if not keep:
    print(rc("lunatest clear"))
