package net.lunacore.luna_npc.pathfinding;

import net.minecraft.core.BlockPos;

public interface PathPlanner {

    // Both endpoints are standable cells (NpcNavigation snaps them before planning). Returns
    // null when no route was found at all; a non-null path with reachedGoal() == false is the
    // best partial route the budget allowed.
    NpcPath plan(PathWorldView world, BlockPos start, BlockPos goal);
}
