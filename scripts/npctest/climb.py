"""Climb test: an NPC must leave a 2-deep pit by its staircase, or step up three 1-block ledges, to reach a target.

Usage: python scripts/npctest/climb.py <lid> <pit|ledges> [target_mob]
"""
import math, os, re, socket, sys, time
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))
import rcon

props = rcon.read_properties()
sock = socket.create_connection(("127.0.0.1", int(props["rcon.port"])), timeout=10)
sock.sendall(rcon.packet(1, 3, props["rcon.password"])); rcon.receive(sock)

def rc(cmd):
    sock.sendall(rcon.packet(2, 2, cmd)); return rcon.receive(sock)[1]

X0, Y, Z0 = 20000, 150, 20000
lid = int(sys.argv[1])
mode = sys.argv[2]  # pit | ledges
BAIT = sys.argv[3] if len(sys.argv) > 3 else "minecraft:villager"
for c in [f"forceload add {X0-10} {Z0-20} {X0+80} {Z0+20}",
          f"fill {X0-10} {Y-4} {Z0-12} {X0+40} {Y-1} {Z0+12} minecraft:stone",
          f"fill {X0-10} {Y} {Z0-12} {X0+40} {Y+6} {Z0+12} minecraft:air",
          "kill @e[tag=bait]", f"kill @e[type=luna_npc:npc,x={X0-10},y={Y-5},z={Z0-12},dx=90,dy=20,dz=24]"]:
    rc(c)
if mode == "pit":
    # 5x5 pit, 2 deep, with a one-wide staircase out on the far side from the target
    rc(f"fill {X0-2} {Y-2} {Z0-2} {X0+2} {Y-1} {Z0+2} minecraft:air")
    rc(f"setblock {X0-2} {Y-2} {Z0+2} minecraft:stone")
    start, bait = (X0, Y - 2, Z0), (X0 + 6, Y, Z0)
else:
    # three one-block ledges up, then a two-block cliff with a step
    for i, x in enumerate((X0 + 4, X0 + 8, X0 + 12)):
        rc(f"fill {x} {Y} {Z0-12} {X0+40} {Y+i} {Z0+12} minecraft:stone")
    start, bait = (X0, Y, Z0), (X0 + 18, Y + 3, Z0)
time.sleep(0.5)
rc(f"summon {BAIT} {bait[0]}.5 {bait[1]} {bait[2]}.5 {{NoAI:1b,Silent:1b,Tags:[bait],PersistenceRequired:1b,Health:1000f,attributes:[{{id:'minecraft:max_health',base:1000}}]}}")
rc(f"summon luna_npc:npc {start[0]}.5 {start[1]} {start[2]}.5 {{luna_id:{lid}L,Tags:[chaser]}}")
npc = "@e[type=luna_npc:npc,tag=chaser,limit=1]"
t0 = time.time()
reached = None
while time.time() - t0 < 15:
    out = rc(f"data get entity {npc} Pos")
    m = re.findall(r"(-?[\d.]+)d", out)
    if len(m) >= 3:
        p = [float(v) for v in m[:3]]
        d = math.dist(p, (bait[0] + 0.5, bait[1], bait[2] + 0.5))
        if d < 4.5 and abs(p[1] - bait[1]) < 1.0:
            reached = round(time.time() - t0, 1); break
    time.sleep(0.25)
print(f"LID{lid} {mode}: reached in {reached}s" if reached else f"LID{lid} {mode}: NOT reached, last {p}")
rc("kill @e[tag=bait]"); rc(f"kill {npc}")
