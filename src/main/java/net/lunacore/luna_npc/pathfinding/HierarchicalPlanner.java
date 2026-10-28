package net.lunacore.luna_npc.pathfinding;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

// Two-level planner: a coarse A* on a widely spaced grid finds the corridor, then short enhanced
// A* runs stitch the corridor cells together. Cheap on long distances because the expensive fine
// search only ever runs on short segments; the corridor choice costs a little path quality when
// the coarse grid skips over the true best route.
public class HierarchicalPlanner implements PathPlanner {

    private static final int STRIDE = 4;
    private static final int DEFAULT_COARSE_BUDGET = 1024;
    private static final int DEFAULT_SEGMENT_BUDGET = 256;
    private static final int[] STEP_X = {1, -1, 0, 0, 1, 1, -1, -1};
    private static final int[] STEP_Z = {0, 0, 1, -1, 1, -1, 1, -1};

    private final int coarseBudget;
    private final AStarPlanner segmentPlanner;

    public HierarchicalPlanner() {
        this(DEFAULT_COARSE_BUDGET, DEFAULT_SEGMENT_BUDGET);
    }

    public HierarchicalPlanner(int coarseBudget, int segmentBudget) {
        this.coarseBudget = coarseBudget;
        this.segmentPlanner = new AStarPlanner(true, 1.0F, segmentBudget);
    }

    @Override
    public NpcPath plan(PathWorldView world, BlockPos start, BlockPos goal) {
        int distX = Math.abs(goal.getX() - start.getX());
        int distZ = Math.abs(goal.getZ() - start.getZ());
        if (Math.max(distX, distZ) <= STRIDE * 2) {
            return segmentPlanner.plan(world, start, goal);
        }
        List<BlockPos> corridor = coarseCorridor(world, start, goal);
        if (corridor == null) {
            return null;
        }
        boolean reachedGoal = corridor.getLast().equals(goal);
        List<Vec3> waypoints = new ArrayList<>();
        BlockPos from = start;
        for (int index = 1; index < corridor.size(); index++) {
            NpcPath segment = segmentPlanner.plan(world, from, corridor.get(index));
            if (segment == null) {
                break;
            }
            List<Vec3> segmentWaypoints = segment.waypoints();
            // Segments share their endpoints, so the joint cell is only added once.
            waypoints.addAll(segmentWaypoints.subList(waypoints.isEmpty() ? 0 : 1, segmentWaypoints.size()));
            if (!segment.reachedGoal()) {
                reachedGoal = false;
                break;
            }
            from = corridor.get(index);
        }
        if (waypoints.isEmpty()) {
            return null;
        }
        return new NpcPath(waypoints, reachedGoal && from.equals(corridor.getLast()));
    }

    private static final class CoarseNode {
        final BlockPos pos;
        float g;
        float f;
        CoarseNode parent;
        boolean closed;

        CoarseNode(BlockPos pos) {
            this.pos = pos;
        }
    }

    // Coarse A*: nodes are standable cells roughly STRIDE apart, snapped to the ground near each
    // jump. Returns the corridor to the goal, or to the closest reachable cell on budget/blocked.
    private List<BlockPos> coarseCorridor(PathWorldView world, BlockPos start, BlockPos goal) {
        Map<Long, CoarseNode> nodes = new HashMap<>();
        PriorityQueue<CoarseNode> open = new PriorityQueue<>((a, b) -> Float.compare(a.f, b.f));
        CoarseNode startNode = new CoarseNode(start);
        startNode.f = distance(start, goal);
        nodes.put(start.asLong(), startNode);
        open.add(startNode);

        CoarseNode best = startNode;
        float bestH = distance(start, goal);
        int visited = 0;

        while (!open.isEmpty() && visited < coarseBudget) {
            CoarseNode current = open.poll();
            if (current.closed) {
                continue;
            }
            current.closed = true;
            visited++;
            float h = distance(current.pos, goal);
            if (h <= STRIDE * 1.5F) {
                return corridorOf(current, goal);
            }
            if (h < bestH) {
                bestH = h;
                best = current;
            }
            for (int direction = 0; direction < STEP_X.length; direction++) {
                int nx = current.pos.getX() + STEP_X[direction] * STRIDE;
                int nz = current.pos.getZ() + STEP_Z[direction] * STRIDE;
                int ny = world.snapToGround(nx, current.pos.getY(), nz, STRIDE, STRIDE + 2);
                if (ny == PathWorldView.NO_STEP) {
                    continue;
                }
                BlockPos neighborPos = new BlockPos(nx, ny, nz);
                float g = current.g + distance(current.pos, neighborPos);
                CoarseNode neighbor = nodes.get(neighborPos.asLong());
                if (neighbor == null) {
                    neighbor = new CoarseNode(neighborPos);
                    neighbor.g = g;
                    neighbor.f = g + distance(neighborPos, goal);
                    neighbor.parent = current;
                    nodes.put(neighborPos.asLong(), neighbor);
                    open.add(neighbor);
                } else if (!neighbor.closed && g < neighbor.g) {
                    neighbor.g = g;
                    neighbor.f = g + distance(neighborPos, goal);
                    neighbor.parent = current;
                    open.add(neighbor);
                }
            }
        }
        if (best == startNode) {
            return null;
        }
        return corridorOf(best, null);
    }

    private static List<BlockPos> corridorOf(CoarseNode end, BlockPos goal) {
        List<BlockPos> corridor = new ArrayList<>();
        for (CoarseNode node = end; node != null; node = node.parent) {
            corridor.add(node.pos);
        }
        List<BlockPos> ordered = new ArrayList<>(corridor.reversed());
        if (goal != null && !ordered.getLast().equals(goal)) {
            ordered.add(goal);
        }
        return ordered;
    }

    private static float distance(BlockPos from, BlockPos to) {
        float dx = to.getX() - from.getX();
        float dy = to.getY() - from.getY();
        float dz = to.getZ() - from.getZ();
        return (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}
