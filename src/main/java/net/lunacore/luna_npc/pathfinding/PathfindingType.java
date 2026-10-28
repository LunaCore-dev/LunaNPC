package net.lunacore.luna_npc.pathfinding;

// The selectable path planners. The star ratings (0-5) are what the picker UI shows: quality is
// how close the found route gets to the ideal one, cost is how much compute one plan burns.
public enum PathfindingType {

    VANILLA("vanilla", 2, 2),
    ENHANCED("enhanced", 4, 3),
    THETA_STAR("theta_star", 5, 4),
    HIERARCHICAL("hierarchical", 3, 1);

    public static final int MAX_STARS = 5;

    private final String id;
    private final int qualityStars;
    private final int costStars;

    PathfindingType(String id, int qualityStars, int costStars) {
        this.id = id;
        this.qualityStars = qualityStars;
        this.costStars = costStars;
    }

    public String id() {
        return id;
    }

    public int qualityStars() {
        return qualityStars;
    }

    public int costStars() {
        return costStars;
    }

    public PathPlanner createPlanner() {
        return switch (this) {
            case VANILLA -> AStarPlanner.vanillaStyle();
            case ENHANCED -> AStarPlanner.enhanced();
            case THETA_STAR -> new ThetaStarPlanner();
            case HIERARCHICAL -> new HierarchicalPlanner();
        };
    }

    // The 3D counterpart used by swimming and flying NPCs (see VolumePlanner). The four tiers vary the
    // search (heuristic weight + node budget) exactly as on land, but ALL of them string-pull the route
    // into smooth any-angle lines: through open air or water the six-connected grid is only there to
    // route around obstacles, never to be flown corner-to-corner, so an unsmoothed staircase (fly up,
    // fly straight, stop, fly up) looks nothing like how anything actually moves. Smoothing is cheap, so
    // even the cheap Vanilla tier keeps it — the tiers differ only in how hard they search for a route.
    public VolumePlanner createVolumePlanner() {
        return switch (this) {
            case VANILLA -> new VolumePlanner(1.5F, 1536, true);
            case ENHANCED -> new VolumePlanner(1.2F, 6144, true);
            case THETA_STAR -> new VolumePlanner(1.0F, 6144, true);
            case HIERARCHICAL -> new VolumePlanner(1.35F, 1024, true);
        };
    }

    // For the walk command's comparison print: effectively uncapped budgets, bounded only by a
    // safety valve so a truly unreachable target cannot freeze the server. Display only — real
    // NPCs always run the capped planners above.
    public PathPlanner createDiagnosticPlanner() {
        int budget = 250_000;
        return switch (this) {
            case VANILLA -> new AStarPlanner(false, 1.5F, budget);
            case ENHANCED -> new AStarPlanner(true, 1.0F, budget);
            case THETA_STAR -> new ThetaStarPlanner(new AStarPlanner(true, 1.0F, budget));
            case HIERARCHICAL -> new HierarchicalPlanner(budget / 10, 4096);
        };
    }

    public static PathfindingType byId(String id) {
        for (PathfindingType type : values()) {
            if (type.id.equals(id)) {
                return type;
            }
        }
        return null;
    }
}
