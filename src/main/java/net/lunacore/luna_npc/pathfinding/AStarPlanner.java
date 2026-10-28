package net.lunacore.luna_npc.pathfinding;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

// Grid A* over standable cells. The vanilla-style profile mirrors how Minecraft's own pathfinder
// feels: cardinal moves only, an overweighted heuristic and a small node budget — fast, but the
// routes wobble and long distances get cut off. The enhanced profile adds diagonal moves, an
// honest octile heuristic and a much larger budget, which is what makes 64-block targets viable.
public class AStarPlanner implements PathPlanner {

    private static final int[] STEP_X = {1, -1, 0, 0, 1, 1, -1, -1};
    private static final int[] STEP_Z = {0, 0, 1, -1, 1, -1, 1, -1};
    private static final float DIAGONAL_COST = 1.4142F;
    private static final float VERTICAL_COST = 0.5F;

    private final boolean allowDiagonals;
    private final float heuristicWeight;
    private final int maxVisitedNodes;

    public AStarPlanner(boolean allowDiagonals, float heuristicWeight, int maxVisitedNodes) {
        this.allowDiagonals = allowDiagonals;
        this.heuristicWeight = heuristicWeight;
        this.maxVisitedNodes = maxVisitedNodes;
    }

    public static AStarPlanner vanillaStyle() {
        return new AStarPlanner(false, 1.5F, 1536);
    }

    public static AStarPlanner enhanced() {
        return new AStarPlanner(true, 1.0F, 8192);
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

    @Override
    public NpcPath plan(PathWorldView world, BlockPos start, BlockPos goal) {
        Map<Long, Node> nodes = new HashMap<>();
        PriorityQueue<Node> open = new PriorityQueue<>((a, b) -> Float.compare(a.f, b.f));
        Node startNode = new Node(start.getX(), start.getY(), start.getZ());
        startNode.f = heuristicWeight * heuristic(startNode.x, startNode.y, startNode.z, goal);
        nodes.put(BlockPos.asLong(startNode.x, startNode.y, startNode.z), startNode);
        open.add(startNode);

        Node best = startNode;
        float bestH = heuristic(startNode.x, startNode.y, startNode.z, goal);
        int visited = 0;
        int directions = allowDiagonals ? STEP_X.length : 4;
        // Long routes get proportionally more budget, and the search stays inside an ellipse
        // around the start-goal line — without the corridor, cave systems near the route soak
        // up the entire budget and far targets read as unreachable.
        float directDistance = distance(start, goal);
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
                return buildPath(current, true);
            }
            float h = heuristic(current.x, current.y, current.z, goal);
            if (h < bestH) {
                bestH = h;
                best = current;
            }
            for (int direction = 0; direction < directions; direction++) {
                int dx = STEP_X[direction];
                int dz = STEP_Z[direction];
                boolean diagonal = dx != 0 && dz != 0;
                // Diagonals must not cut corners: both cardinal columns beside the move stay clear.
                if (diagonal && !(world.isPassable(current.x + dx, current.y, current.z)
                        && world.isPassable(current.x + dx, current.y + 1, current.z)
                        && world.isPassable(current.x, current.y, current.z + dz)
                        && world.isPassable(current.x, current.y + 1, current.z + dz))) {
                    continue;
                }
                int ny = world.stepTarget(current.x, current.y, current.z, dx, dz);
                if (ny == PathWorldView.NO_STEP) {
                    continue;
                }
                if (distance(start, current.x + dx, ny, current.z + dz)
                        + distance(goal, current.x + dx, ny, current.z + dz) > corridorLimit) {
                    continue;
                }
                float g = current.g + (diagonal ? DIAGONAL_COST : 1.0F)
                        + Math.abs(ny - current.y) * VERTICAL_COST
                        + world.movementPenalty(current.x + dx, ny, current.z + dz);
                long key = BlockPos.asLong(current.x + dx, ny, current.z + dz);
                Node neighbor = nodes.get(key);
                if (neighbor == null) {
                    neighbor = new Node(current.x + dx, ny, current.z + dz);
                    neighbor.g = g;
                    neighbor.f = g + heuristicWeight * heuristic(neighbor.x, neighbor.y, neighbor.z, goal);
                    neighbor.parent = current;
                    nodes.put(key, neighbor);
                    open.add(neighbor);
                } else if (!neighbor.closed && g < neighbor.g) {
                    neighbor.g = g;
                    neighbor.f = g + heuristicWeight * heuristic(neighbor.x, neighbor.y, neighbor.z, goal);
                    neighbor.parent = current;
                    // Re-add instead of decrease-key; the stale queue entry is skipped via closed.
                    open.add(neighbor);
                }
            }
        }
        if (best == startNode) {
            return null;
        }
        return buildPath(best, false);
    }

    private static float distance(BlockPos from, BlockPos to) {
        return distance(from, to.getX(), to.getY(), to.getZ());
    }

    private static float distance(BlockPos from, int x, int y, int z) {
        float dx = x - from.getX();
        float dy = y - from.getY();
        float dz = z - from.getZ();
        return (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private float heuristic(int x, int y, int z, BlockPos goal) {
        float dx = Math.abs(x - goal.getX());
        float dz = Math.abs(z - goal.getZ());
        float dy = Math.abs(y - goal.getY());
        float horizontal = allowDiagonals
                ? Math.max(dx, dz) + (DIAGONAL_COST - 1.0F) * Math.min(dx, dz)
                : dx + dz;
        return horizontal + dy * VERTICAL_COST;
    }

    private static NpcPath buildPath(Node end, boolean reachedGoal) {
        List<Vec3> waypoints = new ArrayList<>();
        for (Node node = end; node != null; node = node.parent) {
            waypoints.add(NpcPath.center(new BlockPos(node.x, node.y, node.z)));
        }
        return new NpcPath(waypoints.reversed(), reachedGoal);
    }
}
