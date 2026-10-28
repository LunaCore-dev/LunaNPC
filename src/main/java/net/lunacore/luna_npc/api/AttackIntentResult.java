package net.lunacore.luna_npc.api;

// The outcome of LunaNpcApi.requestAttack(source, target). Normal callers can ignore anything but
// ACCEPTED; addons and debug tools can inspect the reason it was refused.
public enum AttackIntentResult {

    // The NPC took the target and will engage it.
    ACCEPTED,
    // The NPC has no melee or ranged attack enabled, so it cannot act on the request.
    UNSUPPORTED,
    // The target isn't a valid thing to attack (e.g. the NPC itself).
    INVALID_TARGET,
    // The source NPC is gone (removed/dead).
    SOURCE_REMOVED,
    // The target is gone (removed/dead).
    TARGET_REMOVED
}
