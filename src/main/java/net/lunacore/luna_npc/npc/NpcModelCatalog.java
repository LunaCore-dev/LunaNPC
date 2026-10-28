package net.lunacore.luna_npc.npc;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class NpcModelCatalog {

    public record NpcModelType(String id, List<String> modelIds, boolean stretchable) {
    }

    // Where a model shows equipment. HEAD on a non-humanoid takes heads and blocks only, never armor:
    // vanilla draws no armor on those mobs.
    public enum GearSlot { HEAD, ARMOR, MAIN_HAND, OFF_HAND }

    // Non-humanoid types get exactly the slots their vanilla mob renders; humanoids get all of them.
    private static final Map<String, Set<GearSlot>> MOB_GEAR = Map.of(
            "illager", EnumSet.of(GearSlot.HEAD, GearSlot.MAIN_HAND, GearSlot.OFF_HAND),
            "villager", EnumSet.of(GearSlot.HEAD, GearSlot.MAIN_HAND),
            "vex", EnumSet.of(GearSlot.MAIN_HAND, GearSlot.OFF_HAND),
            "allay", EnumSet.of(GearSlot.MAIN_HAND),
            "witch", EnumSet.of(GearSlot.MAIN_HAND),
            "fox", EnumSet.of(GearSlot.MAIN_HAND));

    // Every modelId is backed by a full-body vanilla texture verified to exist in the 26.3 client
    // assets and to use the same UV layout as the type's model. Ids not in NpcClientModels'
    // texture map resolve to custom skins under assets/luna_npc/textures/entity/npc/<type>/<id>.png.
    public static final List<NpcModelType> TYPES = List.of(
            new NpcModelType("player", List.of("steve", "alex", "ari", "efe", "kai", "makena", "noor",
                    "sunny", "zuri", "corbin", "arcade"), true),
            new NpcModelType("zombie", List.of("zombie", "husk", "drowned", "zombie_wushuangcb", "zombie_herobrine"), true),
            new NpcModelType("skeleton", List.of("skeleton", "stray", "wither_skeleton", "bogged", "parched"), true),
            new NpcModelType("enderman", List.of("enderman", "inverted", "newtex", "white", "enderman_moose", "enderman_reforged"), true),
            new NpcModelType("piglin", List.of("piglin", "zombified_piglin", "piglin_brute", "piglin_cuter"), true),
            new NpcModelType("villager", List.of("villager", "wandering_trader", "villager_noseless"), false),
            new NpcModelType("witch", List.of("witch", "dray", "noseless", "witch_moose", "witch_reforged"), false),
            new NpcModelType("illager", List.of("pillager", "vindicator", "evoker", "illusioner", "pillager_paraider", "vindicator_paraider", "evoker_paraider", "pillager_french", "pillager_realistic"), false),
            new NpcModelType("iron_golem", List.of("iron_golem", "iron_golem_cherry_blossom", "iron_golem_flowerier", "iron_golem_stronk", "iron_golem_movie"), false),
            new NpcModelType("snow_golem", List.of("snow_golem"), false),
            new NpcModelType("cow", List.of("cow", "cow_warm", "cow_cold", "mooshroom_red", "mooshroom_brown"), false),
            new NpcModelType("pig", List.of("pig", "pig_warm", "pig_cold", "alignment_pigs", "cursed-mobs_cold", "cursed-mobs_warm", "mizunos-pig-variants-x-fa", "mizunos-pig-variants-x-fa_cold", "mizunos-pig-variants-x-fa_warm", "mizunos-pig-variants", "mizunos-pig-variants_cold", "mizunos-pig-variants_warm", "technoblade-pig-crown"), false),
            new NpcModelType("sheep", List.of("sheep", "bare-bones-x-fresh-animations", "bta-pack", "fantasy-tools", "fresh-animations-_-minecraft-earth", "goofymobs", "mineshield-3", "papyrux"), false),
            new NpcModelType("chicken", List.of("chicken", "chicken_warm", "chicken_cold", "cursed-mobs", "hermit-chickens", "hermit-chickens_cold", "hermit-chickens_warm", "renewed-animals_4", "renewed-animals_cold_2", "renewed-animals_warm_2"), false),
            new NpcModelType("wolf", List.of("wolf", "wolf_ashen", "wolf_black", "wolf_chestnut", "wolf_rusty",
                    "wolf_snowy", "wolf_spotted", "wolf_striped", "wolf_woods"), false),
            new NpcModelType("cat", List.of("cat", "cat_black", "cat_red", "cat_siamese",
                    "cat_british_shorthair", "cat_calico", "cat_persian", "cat_ragdoll", "cat_white",
                    "cat_jellie", "cat_all_black", "ocelot"), false),
            new NpcModelType("fox", List.of("fox", "fox_snow", "fox_sleep", "fox_snow_sleep", "all-foxes-need-thigh-highs", "bare-bones-x-fresh-animations_3", "better-vanilla-animals_3", "bta-pack_4", "fox-thigh-highs", "goofymobs_4", "mineshield-3_4", "mizunos-fox-variants", "mizunos-fox-variants_snow", "programmer-art-plus", "renewed-animals_5", "renewed-animals_snow", "robin-hood", "smart-foxes", "tombino-mobs_2"), false),
            new NpcModelType("rabbit", List.of("rabbit", "rabbit_white", "rabbit_black",
                    "rabbit_white_splotched", "rabbit_gold", "rabbit_salt", "rabbit_caerbannog",
                    "rabbit_toast"), false),
            new NpcModelType("goat", List.of("goat", "bare-bones-x-fresh-animations_2", "better-vanilla-animals", "bta-pack_2", "fantasy-tools_2", "goofymobs_2", "mineshield-3_2", "renewed-animals"), false),
            new NpcModelType("panda", List.of("panda", "panda_brown", "panda_aggressive", "panda_lazy",
                    "panda_playful", "panda_weak", "panda_worried"), false),
            new NpcModelType("polar_bear", List.of("polar_bear", "better-vanilla-animals_2", "bta-pack_3", "fantasy-tools_3", "goofymobs_3", "renewed-animals_2", "the-ck-pack", "tombino-mobs"), false),
            new NpcModelType("armadillo", List.of("armadillo", "mud-and-dirt-armadillos"), false),
            new NpcModelType("axolotl", List.of("axolotl", "axolotl_wild", "axolotl_gold", "axolotl_cyan",
                    "axolotl_blue"), false),
            new NpcModelType("frog", List.of("frog", "frog_warm", "frog_cold", "renewed-animals_6", "renewed-animals_cold_3", "renewed-animals_warm_3"), false),
            new NpcModelType("allay", List.of("allay", "mizuno", "fae", "golden", "allay_moose"), false),
            new NpcModelType("vex", List.of("vex", "vex_charging", "moose_charging", "vex_moose"), false),
            new NpcModelType("bat", List.of("bat", "barebones"), false),
            new NpcModelType("creeper", List.of("creeper", "moose", "balrog", "creepingly", "creeper_reforged"), false),
            new NpcModelType("spider", List.of("spider", "cave_spider"), false),
            new NpcModelType("slime", List.of("slime", "magma_cube"), false),
            new NpcModelType("blaze", List.of("blaze", "reforged", "refreshed", "blaze_moose"), false),
            new NpcModelType("warden", List.of("warden", "conquest", "caseoh", "warden_moose"), false));

    private NpcModelCatalog() {
    }

    public static Optional<NpcModelType> byId(String typeId) {
        return TYPES.stream().filter(type -> type.id().equals(typeId)).findFirst();
    }

    // Imported skins are client-side files the server cannot know about, so any sanely named id is
    // accepted for a known type; clients missing the file just render the fallback texture. A custom
    // category is a user-named model type the server likewise cannot see, so it is validated by name
    // only (same trust model as imported skins) — the geometry and its box live on the client.
    public static boolean isValidModel(String typeId, String modelId) {
        Optional<NpcModelType> type = byId(typeId);
        if (type.isPresent()) {
            return type.get().modelIds().contains(modelId) || NpcValidation.isValidCustomModelId(modelId);
        }
        return isCustomCategory(typeId) && NpcValidation.isValidCustomModelId(modelId);
    }

    public static boolean isCustomCategory(String typeId) {
        return byId(typeId).isEmpty() && NpcValidation.isValidModelCategory(typeId);
    }

    public static boolean isStretchable(String typeId) {
        return byId(typeId).map(NpcModelType::stretchable).orElse(false);
    }

    public static boolean showsGear(String typeId, GearSlot slot) {
        return isStretchable(typeId) || MOB_GEAR.getOrDefault(typeId, Set.of()).contains(slot);
    }

    // Model-space leg length below the hip pivot in pixels (16 px = 1 block). Used by both the
    // server hitbox math and the client render lift so the physical box always matches the visible
    // skin. Standard humanoids (player, zombie, skeleton, piglin) have 12 px legs; the enderman
    // layer has 2x30x2 legs pivoted at y=-5 with feet on the ground plane at y=24, i.e. 29 px.
    public static float legPixels(String typeId) {
        return "enderman".equals(typeId) ? 29.0F : 12.0F;
    }

    // How far a hanging arm's tip ends up below the ground plane (model y=24) after the leg lift,
    // in pixels; 0 when it stays above ground. Arms scale downward from the shoulder pivot, so a
    // big arm can reach below the feet — the renderer lifts the whole body by this overhang (feet
    // hover, arm tip rests on the ground) and the hitbox grows by the same amount, keeping the box
    // on the visible skin. Standard humanoid arms pivot at y=2 and drop 10 px (box y -2..10);
    // enderman arms pivot at y=-12 and drop 28 px (box y -2..28).
    public static float armOverhangPixels(String typeId, float maxArmScale, float maxLegScale) {
        boolean enderman = "enderman".equals(typeId);
        float armPivot = enderman ? -12.0F : 2.0F;
        float armDrop = enderman ? 28.0F : 10.0F;
        float legLift = (maxLegScale - 1.0F) * legPixels(typeId);
        return Math.max(0.0F, armPivot + armDrop * maxArmScale - legLift - 24.0F);
    }
}
