package net.lunacore.luna_npc.pathfinding;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

// Any-angle planner (lazy Theta*): runs the enhanced A* and then pulls the route tight with
// line-of-sight shortcuts, so NPCs walk straight diagonals instead of grid staircases. The
// smoothing pass is what buys the extra path quality — and what costs the extra compute.
public class ThetaStarPlanner implements PathPlanner {

    private final AStarPlanner base;

    public ThetaStarPlanner() {
        this(AStarPlanner.enhanced());
    }

    public ThetaStarPlanner(AStarPlanner base) {
        this.base = base;
    }

    @Override
    public NpcPath plan(PathWorldView world, BlockPos start, BlockPos goal) {
        NpcPath raw = base.plan(world, start, goal);
        if (raw == null) {
            return null;
        }
        return new NpcPath(smooth(world, raw.waypoints()), raw.reachedGoal());
    }

    // Greedy string-pulling: from each kept waypoint, jump to the furthest later waypoint that
    // is reachable over a straight flat line. Elevation changes always keep their grid steps.
    private static List<Vec3> smooth(PathWorldView world, List<Vec3> waypoints) {
        if (waypoints.size() < 3) {
            return waypoints;
        }
        List<Vec3> smoothed = new ArrayList<>();
        int index = 0;
        smoothed.add(waypoints.get(0));
        while (index < waypoints.size() - 1) {
            int furthest = index + 1;
            BlockPos from = BlockPos.containing(waypoints.get(index));
            for (int candidate = waypoints.size() - 1; candidate > index + 1; candidate--) {
                if (world.isLineWalkable(from, BlockPos.containing(waypoints.get(candidate)))) {
                    furthest = candidate;
                    break;
                }
            }
            smoothed.add(waypoints.get(furthest));
            index = furthest;
        }
        return smoothed;
    }
}
