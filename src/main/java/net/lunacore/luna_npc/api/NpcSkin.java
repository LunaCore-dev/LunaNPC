package net.lunacore.luna_npc.api;

// One downloaded player skin, stored with the world and sent to every player. id is fixed and is what an
// NPC's model refers to (model type "player", model id = id); name is the renamable label shown in the
// model picker. source is the player name or URL it came from.
public record NpcSkin(String id, String name, NpcSkinArms arms, String source) {

    public static final String MODEL_TYPE = "player";

    // Default model settings wearing this skin, ready for NpcDefinition.setModelSettings.
    public NpcModelSettings asModel() {
        return NpcModelSettings.defaults().withModel(MODEL_TYPE, id);
    }
}
