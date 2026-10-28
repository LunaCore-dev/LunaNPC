package net.lunacore.luna_npc.pathfinding;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

// An ordered list of cell-center waypoints. reachedGoal is false when the planner ran out of
// budget or hit a wall and returned its best partial route instead of the full one.
public class NpcPath {

    private final List<Vec3> waypoints;
    private final boolean reachedGoal;
    private int index;

    public NpcPath(List<Vec3> waypoints, boolean reachedGoal) {
        this.waypoints = waypoints;
        this.reachedGoal = reachedGoal;
    }

    public static Vec3 center(BlockPos pos) {
        return new Vec3(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
    }

    public List<Vec3> waypoints() {
        return waypoints;
    }

    public boolean reachedGoal() {
        return reachedGoal;
    }

    public boolean isDone() {
        return index >= waypoints.size();
    }

    public Vec3 currentWaypoint() {
        return waypoints.get(index);
    }

    public void advance() {
        index++;
    }

    public int currentIndex() {
        return index;
    }
}
