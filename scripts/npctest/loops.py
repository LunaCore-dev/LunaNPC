"""Summarises a chase.py trace read from stdin: loops (heading turns over 300 degrees within 1.5 s), distance
walked, and the share of samples spent nearly still while the target is out of reach."""
import sys, ast, math
rows = [ast.literal_eval(l) for l in (x.strip() for x in sys.stdin) if l.startswith('(')]
heads = []
for a, b in zip(rows, rows[1:]):
    dx, dz = b[1] - a[1], b[2] - a[2]
    heads.append(math.atan2(dz, dx) if math.hypot(dx, dz) > 0.08 else None)
turn = []
prev = None
for h in heads:
    if h is None or prev is None:
        turn.append(0.0)
    else:
        d = (h - prev + math.pi) % (2 * math.pi) - math.pi
        turn.append(d)
    if h is not None:
        prev = h
loops = []
i = 0
while i < len(turn):
    acc = 0.0
    for j in range(i, min(len(turn), i + 15)):
        acc += turn[j]
        if abs(acc) > math.radians(300):
            loops.append(rows[i][0]); i = j; break
    i += 1
plen = sum(math.hypot(b[1] - a[1], b[2] - a[2]) for a, b in zip(rows, rows[1:]))
print(f"loops={len(loops)} at {loops} pathlen={plen:.1f} net_x={rows[-1][1]-rows[0][1]:.1f} final_gap={rows[-1][3]}")
slow = sum(1 for a, b in zip(rows, rows[1:]) if b[3] > 4.0 and math.hypot(b[1] - a[1], b[2] - a[2]) < 0.3)
print(f"  slow-while-out-of-reach: {slow}/{len(rows)-1} samples ({slow/(len(rows)-1)*100:.0f}%)")
