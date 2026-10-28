"""Bodyguard test on /lunatest bodyguard (the dev test mod): builds a flat arena, sets up the VIP and the
guard variants through the API, then reports the guards' locks while the VIP is attacked, carried out of
range, brought back and killed. Ends with /lunatest clear.

Usage: python scripts/npctest/bodyguard.py
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


def status(label):
    print("--", label)
    for line in re.split(r"(?=TestBg\w+ @ )", rc("lunatest status")):
        if line.startswith("TestBg"):
            print("  ", line.strip())


X, Y, Z = 20100, 150, 20000
for command in [f"forceload add {X - 40} {Z - 20} {X + 60} {Z + 40}",
                f"fill {X - 40} {Y - 1} {Z - 15} {X + 60} {Y - 1} {Z + 30} minecraft:stone",
                f"fill {X - 40} {Y} {Z - 15} {X + 60} {Y + 4} {Z + 30} minecraft:air",
                "kill @e[tag=bgpig]"]:
    rc(command)
time.sleep(1)
print(rc(f"execute positioned {X}.5 {Y} {Z}.5 rotated 0 0 run lunatest bodyguard").replace("TestBg", "\nTestBg"))
time.sleep(6)
status("after 6 s: the VIP guards lock on, the hire guards wait")
vip = "@e[type=luna_npc:npc,name=TestBgVip,limit=1]"
rc(f"summon minecraft:pig {X + 2}.5 {Y} {Z + 9}.5 {{NoAI:1b,Tags:[bgpig]}}")
time.sleep(0.5)
rc(f"damage {vip} 1 minecraft:mob_attack by @e[tag=bgpig,limit=1]")
start = time.time()
while time.time() - start < 12:
    if not rc("data get entity @e[tag=bgpig,limit=1] Health").strip().startswith("Pig"):
        print("pig that hit the VIP killed after", round(time.time() - start, 1), "s")
        break
    time.sleep(0.5)
else:
    print("pig still alive")
time.sleep(3)
rc(f"tp {vip} {X + 45}.5 {Y} {Z + 8}.5")
time.sleep(3)
status("VIP carried 45 blocks off (range 24, no teleport): released")
rc(f"tp {vip} {X}.5 {Y} {Z + 8}.5")
time.sleep(4)
status("VIP back: locked again")
noff = "@e[type=luna_npc:npc,name=TestBgNoFFVip,limit=1]"


def noff_health():
    return rc(f"data get entity {noff} Health").strip().rsplit(" ", 1)[-1]


before = noff_health()
rc(f"damage {noff} 5 minecraft:mob_attack by {vip}")
print("TestBgNoFFVip hit by the VIP it guards:", before, "->", noff_health())
rc(f"summon minecraft:pig {X - 2}.5 {Y} {Z + 9}.5 {{NoAI:1b,Tags:[bgpig]}}")
time.sleep(0.5)
before = noff_health()
rc(f"damage {noff} 5 minecraft:mob_attack by @e[tag=bgpig,limit=1]")
print("TestBgNoFFVip hit by a pig:", before, "->", noff_health())
rc("kill @e[tag=bgpig]")
rc(f"kill {vip}")
time.sleep(3)
status("VIP killed: released")
print(rc("lunatest clear"))
