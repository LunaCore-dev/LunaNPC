package net.lunacore.luna_npc.api;

import net.lunacore.luna_npc.api.NpcReactionSettings;

// Type-safe temperament for NpcReactionSettings.withMode(ReactionMode) — an alternative to the string
// constants. AGGRESSIVE hunts enemies on sight, defends mates and fights back; DEFENSIVE skips the
// hunt (defends and retaliates); PASSIVE only defends itself; FEARFUL flees any attacker.
public enum ReactionMode {

    AGGRESSIVE(NpcReactionSettings.AGGRESSIVE),
    DEFENSIVE(NpcReactionSettings.DEFENSIVE),
    PASSIVE(NpcReactionSettings.PASSIVE),
    FEARFUL(NpcReactionSettings.FEARFUL);

    private final String id;

    ReactionMode(String id) {
        this.id = id;
    }

    // The stored string form used by the settings record and its network/disk codecs.
    public String id() {
        return id;
    }

    // The enum for a stored id, defaulting to AGGRESSIVE for unknown/legacy values.
    public static ReactionMode fromId(String id) {
        for (ReactionMode mode : values()) {
            if (mode.id.equals(id)) {
                return mode;
            }
        }
        return AGGRESSIVE;
    }
}
