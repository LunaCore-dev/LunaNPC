package net.lunacore.luna_npc.api;

// Why a bodyguard let go of its protectee: dismissed (by its hirer, a command or the API), its hire time
// ran out, the protectee died (and the guard doesn't keep protecting after death), the protectee is gone
// (logged off, despawned or unloaded), it left the guard's range or dimension, or another protectee took
// its place (REPLACED, the only reason that skips the give-up action).
public enum NpcBodyguardRelease {
    DISMISSED,
    EXPIRED,
    DIED,
    GONE,
    OUT_OF_RANGE,
    REPLACED
}
