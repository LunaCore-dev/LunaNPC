package net.lunacore.luna_npc.pathfinding;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

// Free 3D A* for swimming and flying NPCs, over a VolumeWorldView. Unlike the ground planner, y is a
// full search axis (no gravity, no step-up or floor rules), so the route can dive, climb and breach
// the surface as freely as it moves sideways — which is what lets a water NPC swim around a wall or
// up out of the water to reach a target the old beeline mover could only shove itself against.
//
// The search is six-connected to keep the node count sane in a large water volume; an optional
// string-pulling pass afterwards straightens the staircase into smooth any-angle diagonals. Its
// three knobs (heuristic weight, node budget, smoothing) are what the four selectable pathfinding
// types dial in — see PathfindingType.createVolumePlanner — so a water NPC honours the very same
// quality/cost choice a ground NPC does.
public class VolumePlanner {

    private static final int[][] STEPS = {
        {1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}
    };

    private final float heuristicWeight;
    private final int maxVisitedNodes;
    private final boolean smooth;

    public VolumePlanner(float heuristicWeight, int maxVisitedNodes, boolean smooth) {
        this.heuristicWeight = heuristicWeight;
        this.maxVisitedNodes = maxVisitedNodes;
        this.smooth = smooth;
    }

    private static final class Node {
        final int x;
        final int y;
        final int z;
        float g;
        float f;
        Node parent;
        boolean closed;

        Node(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    public NpcPath plan(VolumeWorldView world, BlockPos start, BlockPos goal) {
        Map<Long, Node> nodes = new HashMap<>();
        PriorityQueue<Node> open = new PriorityQueue<>((a, b) -> Float.compare(a.f, b.f));
        Node startNode = new Node(start.getX(), start.getY(), start.getZ());
        startNode.f = heuristicWeight * heuristic(startNode.x, startNode.y, startNode.z, goal);
        nodes.put(BlockPos.asLong(startNode.x, startNode.y, startNode.z), startNode);
        open.add(startNode);

        Node best = startNode;
        float bestH = heuristic(startNode.x, startNode.y, startNode.z, goal);
        int visited = 0;
        // Long routes get proportionally more budget, and the search stays inside an ellipsoid
        // around the start-goal line so a sprawling ocean nearby can't soak up the whole budget and
        // make a reachable target read as unreachable.
        float directDistance = distance(start.getX(), start.getY(), start.getZ(), goal);
        int budget = (int) Math.min(maxVisitedNodes * 4L,
                Math.max(maxVisitedNodes, (long) (maxVisitedNodes * directDistance / 24.0F)));
        float corridorLimit = directDistance * 2.0F + 24.0F;

        while (!open.isEmpty() && visited < budget) {
            Node current = open.poll();
            if (current.closed) {
                continue;
            }
            current.closed = true;
            visited++;
            if (current.x == goal.getX() && current.y == goal.getY() && current.z == goal.getZ()) {
                return build(world, current, true);
            }
            float h = heuristic(current.x, current.y, current.z, goal);
            if (h < bestH) {
                bestH = h;
                best = current;
            }
            for (int[] step : STEPS) {
                int nx = current.x + step[0];
                int ny = current.y + step[1];
                int nz = current.z + step[2];
                if (!world.fits(nx, ny, nz)) {
                    continue;
                }
                if (distance(nx, ny, nz, goal)
                        + distance(nx, ny, nz, start.getX(), start.getY(), start.getZ()) > corridorLimit) {
                    continue;
                }
                float g = current.g + 1.0F + world.movementPenalty(nx, ny, nz);
                long key = BlockPos.asLong(nx, ny, nz);
                Node neighbor = nodes.get(key);
                if (neighbor == null) {
                    neighbor = new Node(nx, ny, nz);
                    neighbor.g = g;
                    neighbor.f = g + heuristicWeight * heuristic(nx, ny, nz, goal);
                    neighbor.parent = current;
                    nodes.put(key, neighbor);
                    open.add(neighbor);
                } else if (!neighbor.closed && g < neighbor.g) {
                    neighbor.g = g;
                    neighbor.f = g + heuristicWeight * heuristic(nx, ny, nz, goal);
                    neighbor.parent = current;
                    // Re-add instead of decrease-key; the stale queue entry is skipped via closed.
                    open.add(neighbor);
                }
            }
        }
        if (best == startNode) {
            return null;
        }
        return build(world, best, false);
    }

    private NpcPath build(VolumeWorldView world, Node end, boolean reachedGoal) {
        List<BlockPos> raw = new ArrayList<>();
        for (Node node = end; node != null; node = node.parent) {
            raw.add(new BlockPos(node.x, node.y, node.z));
        }
        raw = raw.reversed();
        List<Vec3> waypoints = new ArrayList<>();
        if (!smooth || raw.size() <= 2) {
            for (BlockPos pos : raw) {
                waypoints.add(NpcPath.center(pos));
            }
            return new NpcPath(waypoints, reachedGoal);
        }
        // String-pulling: from each anchor, reach for the furthest later cell still on a clear
        // straight line, collapsing the 6-connected staircase into a few any-angle diagonals.
        int anchor = 0;
        waypoints.add(NpcPath.center(raw.get(0)));
        while (anchor < raw.size() - 1) {
            int next = anchor + 1;
            for (int probe = raw.size() - 1; probe > anchor + 1; probe--) {
                if (world.isLineClear(raw.get(anchor), raw.get(probe))) {
                    next = probe;
                    break;
                }
            }
            waypoints.add(NpcPath.center(raw.get(next)));
            anchor = next;
        }
        return new NpcPath(waypoints, reachedGoal);
    }

    private float heuristic(int x, int y, int z, BlockPos goal) {
        // Manhattan is the exact minimum for six-connected moves and never overestimates, so the
        // route stays admissible; the weight (per pathfinding type) trades a little optimality for
        // a much smaller search, and the smoothing pass hides the resulting wobble.
        return Math.abs(x - goal.getX()) + Math.abs(y - goal.getY()) + Math.abs(z - goal.getZ());
    }

    private static float distance(int x, int y, int z, BlockPos to) {
        return distance(x, y, z, to.getX(), to.getY(), to.getZ());
    }

    private static float distance(int x, int y, int z, int tx, int ty, int tz) {
        float dx = x - tx;
        float dy = y - ty;
        float dz = z - tz;
        return (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}
