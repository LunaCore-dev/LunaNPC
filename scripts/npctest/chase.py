"""Chase test: an NPC chases a NoAI target teleported away from it, through obstacles.

Usage: python scripts/npctest/chase.py <lid> <target_bps> <none|pillars|wall|walls> <seconds> [target_mob] [straight|zigzag|stopgo|two]
Prints one (t, x, z, gap, flag) row per 0.1 s; pipe it into loops.py for a summary. Arena: x/z 20000, y 150.
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
lid = int(sys.argv[1]) if len(sys.argv) > 1 else 24
speed = float(sys.argv[2]) if len(sys.argv) > 2 else 4.0   # bait blocks/second
pillars = sys.argv[3] if len(sys.argv) > 3 else "pillars"
seconds = float(sys.argv[4]) if len(sys.argv) > 4 else 14
BAIT = sys.argv[5] if len(sys.argv) > 5 else "minecraft:villager"
PATTERN = sys.argv[6] if len(sys.argv) > 6 else "straight"

for c in [f"forceload add {X0-10} {Z0-20} {X0+80} {Z0+20}",
          f"fill {X0-10} {Y-1} {Z0-12} {X0+80} {Y-1} {Z0+12} minecraft:stone",
          f"fill {X0-10} {Y} {Z0-12} {X0+80} {Y+4} {Z0+12} minecraft:air",
          "kill @e[tag=bait]", f"kill @e[type=luna_npc:npc,x={X0-10},y={Y-5},z={Z0-12},dx=90,dy=20,dz=24]"]:
    rc(c)
if pillars == "pillars":
    for px in range(X0 + 6, X0 + 70, 5):
        for pz in (-1, 0, 1):
            if (px // 5 + pz) % 2 == 0:
                rc(f"fill {px} {Y} {Z0+pz*3} {px} {Y+2} {Z0+pz*3} minecraft:stone")
if pillars == "wall":
    rc(f"fill {X0+8} {Y} {Z0-3} {X0+8} {Y+2} {Z0+3} minecraft:stone")
if pillars == "walls":
    for wx in (X0 + 8, X0 + 20, X0 + 32):
        rc(f"fill {wx} {Y} {Z0-3} {wx} {Y+2} {Z0+3} minecraft:stone")
time.sleep(0.5)
rc(f"summon {BAIT} {X0+4}.5 {Y} {Z0}.5 {{NoAI:1b,Silent:1b,Tags:[bait],PersistenceRequired:1b,Health:1000f,attributes:[{{id:'minecraft:max_health',base:1000}}]}}")
if PATTERN.startswith("two"):
    rc(f"summon {BAIT} {X0-5}.5 {Y} {Z0}.5 {{NoAI:1b,Silent:1b,Tags:[bait,bait2],PersistenceRequired:1b,Health:1000f,attributes:[{{id:'minecraft:max_health',base:1000}}]}}")
rc(f"summon luna_npc:npc {X0}.5 {Y} {Z0}.5 {{luna_id:{lid}L,Tags:[chaser]}}")
time.sleep(1.5)
npc = f"@e[type=luna_npc:npc,tag=chaser,limit=1]"
print(rc(f"damage {npc} 1 minecraft:mob_attack by @e[tag=bait,limit=1]").strip()[:120])

bx = X0 + 4.5
bz = Z0 + 0.5
dt = 0.1
prev = None
away = 0
log = []
t = 0.0
while t < seconds:
    move = speed * dt
    if PATTERN == "stopgo" and int(t / 1.5) % 2 == 1:
        move = 0.0
    bx += move
    if PATTERN == "zigzag":
        bz = Z0 + 0.5 + 3.0 * math.sin(t * 1.6)
    rc(f"tp @e[tag=bait,tag=!bait2,limit=1] {bx:.2f} {Y} {bz:.2f}")
    out = rc(f"data get entity {npc} Pos")
    m = re.findall(r"(-?[\d.]+)d", out)
    if len(m) >= 3:
        p = tuple(float(v) for v in m[:3])
        gx, gz = bx - p[0], bz - p[2]
        gap = math.hypot(gx, gz)
        if prev:
            vx, vz = p[0] - prev[0], p[2] - prev[2]
            if gap > 3 and math.hypot(vx, vz) > 0.05 and (vx * gx + vz * gz) < 0:
                away += 1
        log.append((round(t, 1), round(p[0] - X0, 2), round(p[2] - Z0, 2), round(gap, 2), "AWAY" if prev and gap > 3 and math.hypot(p[0]-prev[0], p[2]-prev[2]) > 0.05 and ((p[0]-prev[0])*gx + (p[2]-prev[2])*gz) < 0 else ""))
        prev = p
    time.sleep(dt)
    t += dt
for row in log:
    print(row)
print("ticks moving away from target:", away, " final gap:", log[-1][3] if log else None)
rc("kill @e[tag=bait]"); rc(f"kill {npc}")
