package net.lunacore.luna_npc.api;

// The outcome of a skin download: the new skin, or why there is none (exactly one of the two is set).
public record NpcSkinResult(NpcSkin skin, NpcSkinFailure failure) {

    public static NpcSkinResult success(NpcSkin skin) {
        return new NpcSkinResult(skin, null);
    }

    public static NpcSkinResult failed(NpcSkinFailure failure) {
        return new NpcSkinResult(null, failure);
    }

    public boolean succeeded() {
        return skin != null;
    }
}
