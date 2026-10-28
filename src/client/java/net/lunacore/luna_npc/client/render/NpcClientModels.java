package net.lunacore.luna_npc.client.render;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.ambient.BatModel;
import net.minecraft.client.model.animal.allay.AllayModel;
import net.minecraft.client.model.animal.armadillo.AdultArmadilloModel;
import net.minecraft.client.model.animal.axolotl.AdultAxolotlModel;
import net.minecraft.client.model.animal.chicken.AdultChickenModel;
import net.minecraft.client.model.animal.cow.CowModel;
import net.minecraft.client.model.animal.feline.AdultCatModel;
import net.minecraft.client.model.animal.fox.AdultFoxModel;
import net.minecraft.client.model.animal.frog.FrogModel;
import net.minecraft.client.model.animal.goat.GoatModel;
import net.minecraft.client.model.animal.golem.IronGolemModel;
import net.minecraft.client.model.animal.golem.SnowGolemModel;
import net.minecraft.client.model.animal.panda.PandaModel;
import net.minecraft.client.model.animal.pig.PigModel;
import net.minecraft.client.model.animal.polarbear.PolarBearModel;
import net.minecraft.client.model.animal.rabbit.AdultRabbitModel;
import net.minecraft.client.model.animal.sheep.SheepModel;
import net.minecraft.client.model.animal.wolf.AdultWolfModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.monster.blaze.BlazeModel;
import net.minecraft.client.model.monster.creeper.CreeperModel;
import net.minecraft.client.model.monster.illager.IllagerModel;
import net.minecraft.client.model.monster.slime.MagmaCubeModel;
import net.minecraft.client.model.monster.slime.SlimeModel;
import net.minecraft.client.model.monster.spider.SpiderModel;
import net.minecraft.client.model.monster.vex.VexModel;
import net.minecraft.client.model.monster.warden.WardenModel;
import net.minecraft.client.model.monster.witch.WitchModel;
import net.minecraft.client.model.npc.VillagerModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.AllayRenderState;
import net.minecraft.client.renderer.entity.state.ArmadilloRenderState;
import net.minecraft.client.renderer.entity.state.AxolotlRenderState;
import net.minecraft.client.renderer.entity.state.BatRenderState;
import net.minecraft.client.renderer.entity.state.CatRenderState;
import net.minecraft.client.renderer.entity.state.ChickenRenderState;
import net.minecraft.client.renderer.entity.state.CreeperRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.FoxRenderState;
import net.minecraft.client.renderer.entity.state.FrogRenderState;
import net.minecraft.client.renderer.entity.state.GoatRenderState;
import net.minecraft.client.renderer.entity.state.IllagerRenderState;
import net.minecraft.client.renderer.entity.state.IronGolemRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.entity.state.PandaRenderState;
import net.minecraft.client.renderer.entity.state.PolarBearRenderState;
import net.minecraft.client.renderer.entity.state.RabbitRenderState;
import net.minecraft.client.renderer.entity.state.SheepRenderState;
import net.minecraft.client.renderer.entity.state.SlimeRenderState;
import net.minecraft.client.renderer.entity.state.VexRenderState;
import net.minecraft.client.renderer.entity.state.VillagerRenderState;
import net.minecraft.client.renderer.entity.state.WardenRenderState;
import net.minecraft.client.renderer.entity.state.WitchRenderState;
import net.minecraft.client.renderer.entity.state.WolfRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.monster.illager.AbstractIllager;

public final class NpcClientModels {

    // All paths verified against the 26.3 client assets jar. Model keys missing here resolve to
    // custom skins under assets/luna_npc/textures/entity/npc/<type>/<id>.png (see textureFor).
    private static final Map<String, Identifier> TEXTURES = Map.ofEntries(
            Map.entry("player/steve", entityTexture("player/wide/steve")),
            Map.entry("player/alex", entityTexture("player/slim/alex")),
            Map.entry("player/ari", entityTexture("player/slim/ari")),
            Map.entry("player/efe", entityTexture("player/slim/efe")),
            Map.entry("player/kai", entityTexture("player/wide/kai")),
            Map.entry("player/makena", entityTexture("player/slim/makena")),
            Map.entry("player/noor", entityTexture("player/slim/noor")),
            Map.entry("player/sunny", entityTexture("player/wide/sunny")),
            Map.entry("player/zuri", entityTexture("player/wide/zuri")),
            Map.entry("zombie/zombie", entityTexture("zombie/zombie")),
            Map.entry("zombie/husk", entityTexture("zombie/husk")),
            Map.entry("zombie/drowned", entityTexture("zombie/drowned")),
            Map.entry("skeleton/skeleton", entityTexture("skeleton/skeleton")),
            Map.entry("skeleton/stray", entityTexture("skeleton/stray")),
            Map.entry("skeleton/wither_skeleton", entityTexture("skeleton/wither_skeleton")),
            Map.entry("skeleton/bogged", entityTexture("skeleton/bogged")),
            Map.entry("skeleton/parched", entityTexture("skeleton/parched")),
            Map.entry("enderman/enderman", entityTexture("enderman/enderman")),
            Map.entry("piglin/piglin", entityTexture("piglin/piglin")),
            Map.entry("piglin/zombified_piglin", entityTexture("piglin/zombified_piglin")),
            Map.entry("piglin/piglin_brute", entityTexture("piglin/piglin_brute")),
            Map.entry("villager/villager", entityTexture("villager/villager")),
            Map.entry("villager/wandering_trader", entityTexture("wandering_trader/wandering_trader")),
            Map.entry("witch/witch", entityTexture("witch/witch")),
            Map.entry("illager/pillager", entityTexture("illager/pillager")),
            Map.entry("illager/vindicator", entityTexture("illager/vindicator")),
            Map.entry("illager/evoker", entityTexture("illager/evoker")),
            Map.entry("illager/illusioner", entityTexture("illager/illusioner")),
            Map.entry("iron_golem/iron_golem", entityTexture("iron_golem/iron_golem")),
            Map.entry("snow_golem/snow_golem", entityTexture("snow_golem/snow_golem")),
            Map.entry("cow/cow", entityTexture("cow/cow_temperate")),
            Map.entry("cow/cow_warm", entityTexture("cow/cow_warm")),
            Map.entry("cow/cow_cold", entityTexture("cow/cow_cold")),
            Map.entry("cow/mooshroom_red", entityTexture("cow/mooshroom_red")),
            Map.entry("cow/mooshroom_brown", entityTexture("cow/mooshroom_brown")),
            Map.entry("pig/pig", entityTexture("pig/pig_temperate")),
            Map.entry("pig/pig_warm", entityTexture("pig/pig_warm")),
            Map.entry("pig/pig_cold", entityTexture("pig/pig_cold")),
            Map.entry("sheep/sheep", entityTexture("sheep/sheep")),
            Map.entry("chicken/chicken", entityTexture("chicken/chicken_temperate")),
            Map.entry("chicken/chicken_warm", entityTexture("chicken/chicken_warm")),
            Map.entry("chicken/chicken_cold", entityTexture("chicken/chicken_cold")),
            Map.entry("wolf/wolf", entityTexture("wolf/wolf")),
            Map.entry("wolf/wolf_ashen", entityTexture("wolf/wolf_ashen")),
            Map.entry("wolf/wolf_black", entityTexture("wolf/wolf_black")),
            Map.entry("wolf/wolf_chestnut", entityTexture("wolf/wolf_chestnut")),
            Map.entry("wolf/wolf_rusty", entityTexture("wolf/wolf_rusty")),
            Map.entry("wolf/wolf_snowy", entityTexture("wolf/wolf_snowy")),
            Map.entry("wolf/wolf_spotted", entityTexture("wolf/wolf_spotted")),
            Map.entry("wolf/wolf_striped", entityTexture("wolf/wolf_striped")),
            Map.entry("wolf/wolf_woods", entityTexture("wolf/wolf_woods")),
            Map.entry("cat/cat", entityTexture("cat/cat_tabby")),
            Map.entry("cat/cat_black", entityTexture("cat/cat_black")),
            Map.entry("cat/cat_red", entityTexture("cat/cat_red")),
            Map.entry("cat/cat_siamese", entityTexture("cat/cat_siamese")),
            Map.entry("cat/cat_british_shorthair", entityTexture("cat/cat_british_shorthair")),
            Map.entry("cat/cat_calico", entityTexture("cat/cat_calico")),
            Map.entry("cat/cat_persian", entityTexture("cat/cat_persian")),
            Map.entry("cat/cat_ragdoll", entityTexture("cat/cat_ragdoll")),
            Map.entry("cat/cat_white", entityTexture("cat/cat_white")),
            Map.entry("cat/cat_jellie", entityTexture("cat/cat_jellie")),
            Map.entry("cat/cat_all_black", entityTexture("cat/cat_all_black")),
            Map.entry("cat/ocelot", entityTexture("cat/ocelot")),
            Map.entry("fox/fox", entityTexture("fox/fox")),
            Map.entry("fox/fox_snow", entityTexture("fox/fox_snow")),
            Map.entry("fox/fox_sleep", entityTexture("fox/fox_sleep")),
            Map.entry("fox/fox_snow_sleep", entityTexture("fox/fox_snow_sleep")),
            Map.entry("rabbit/rabbit", entityTexture("rabbit/rabbit_brown")),
            Map.entry("rabbit/rabbit_white", entityTexture("rabbit/rabbit_white")),
            Map.entry("rabbit/rabbit_black", entityTexture("rabbit/rabbit_black")),
            Map.entry("rabbit/rabbit_white_splotched", entityTexture("rabbit/rabbit_white_splotched")),
            Map.entry("rabbit/rabbit_gold", entityTexture("rabbit/rabbit_gold")),
            Map.entry("rabbit/rabbit_salt", entityTexture("rabbit/rabbit_salt")),
            Map.entry("rabbit/rabbit_caerbannog", entityTexture("rabbit/rabbit_caerbannog")),
            Map.entry("rabbit/rabbit_toast", entityTexture("rabbit/rabbit_toast")),
            Map.entry("goat/goat", entityTexture("goat/goat")),
            Map.entry("panda/panda", entityTexture("panda/panda")),
            Map.entry("panda/panda_brown", entityTexture("panda/panda_brown")),
            Map.entry("panda/panda_aggressive", entityTexture("panda/panda_aggressive")),
            Map.entry("panda/panda_lazy", entityTexture("panda/panda_lazy")),
            Map.entry("panda/panda_playful", entityTexture("panda/panda_playful")),
            Map.entry("panda/panda_weak", entityTexture("panda/panda_weak")),
            Map.entry("panda/panda_worried", entityTexture("panda/panda_worried")),
            Map.entry("polar_bear/polar_bear", entityTexture("bear/polarbear")),
            Map.entry("armadillo/armadillo", entityTexture("armadillo/armadillo")),
            Map.entry("axolotl/axolotl", entityTexture("axolotl/axolotl_lucy")),
            Map.entry("axolotl/axolotl_wild", entityTexture("axolotl/axolotl_wild")),
            Map.entry("axolotl/axolotl_gold", entityTexture("axolotl/axolotl_gold")),
            Map.entry("axolotl/axolotl_cyan", entityTexture("axolotl/axolotl_cyan")),
            Map.entry("axolotl/axolotl_blue", entityTexture("axolotl/axolotl_blue")),
            Map.entry("frog/frog", entityTexture("frog/frog_temperate")),
            Map.entry("frog/frog_warm", entityTexture("frog/frog_warm")),
            Map.entry("frog/frog_cold", entityTexture("frog/frog_cold")),
            Map.entry("allay/allay", entityTexture("allay/allay")),
            Map.entry("vex/vex", entityTexture("illager/vex")),
            Map.entry("vex/vex_charging", entityTexture("illager/vex_charging")),
            Map.entry("bat/bat", entityTexture("bat/bat")),
            Map.entry("creeper/creeper", entityTexture("creeper/creeper")),
            Map.entry("spider/spider", entityTexture("spider/spider")),
            Map.entry("spider/cave_spider", entityTexture("spider/cave_spider")),
            Map.entry("slime/slime", entityTexture("slime/slime")),
            Map.entry("slime/magma_cube", entityTexture("slime/magmacube")),
            Map.entry("blaze/blaze", entityTexture("blaze/blaze")),
            Map.entry("warden/warden", entityTexture("warden/warden")));

    private static final Map<String, Identifier> CUSTOM_TEXTURES = new ConcurrentHashMap<>();

    private NpcClientModels() {
    }

    // Unknown model keys resolve first to the server's downloaded skins, then to player-imported skin files
    // (LunaNPC/assets/npc_models), then to skins bundled with the mod itself
    // (assets/luna_npc/textures/entity/npc/<type>/<id>.png).
    public static Identifier textureFor(String modelKey, Identifier fallback) {
        Identifier known = TEXTURES.get(modelKey);
        if (known != null) {
            return known;
        }
        int slash = modelKey.indexOf('/');
        if (slash > 0) {
            Identifier downloaded = net.lunacore.luna_npc.client.ClientSkins.textureFor(
                    modelKey.substring(0, slash), modelKey.substring(slash + 1));
            if (downloaded != null) {
                return downloaded;
            }
            Identifier imported = NpcCustomSkins.textureFor(modelKey.substring(0, slash), modelKey.substring(slash + 1));
            if (imported != null) {
                return imported;
            }
        }
        Identifier custom = CUSTOM_TEXTURES.computeIfAbsent(modelKey, key -> {
            Identifier id = Identifier.fromNamespaceAndPath("luna_npc", "textures/entity/npc/" + key + ".png");
            boolean present = Minecraft.getInstance().getResourceManager().getResource(id).isPresent();
            return present ? id : fallback;
        });
        return custom;
    }

    public static Map<String, EntityModel<NpcRenderState>> buildModels(EntityRendererProvider.Context context) {
        Map<String, EntityModel<NpcRenderState>> models = new HashMap<>();
        // One wide and one slim humanoid instance cover all default player skins; the bare type
        // entry is the fallback model for bundled and imported player skins (wide layout).
        StretchableHumanoidModel playerWide = new StretchableHumanoidModel(context.bakeLayer(ModelLayers.PLAYER));
        StretchableHumanoidModel playerSlim = new StretchableHumanoidModel(context.bakeLayer(ModelLayers.PLAYER_SLIM));
        models.put("player", playerWide);
        models.put("player/steve", playerWide);
        models.put("player/kai", playerWide);
        models.put("player/sunny", playerWide);
        models.put("player/zuri", playerWide);
        models.put("player/alex", playerSlim);
        models.put("player/ari", playerSlim);
        models.put("player/efe", playerSlim);
        models.put("player/makena", playerSlim);
        models.put("player/noor", playerSlim);
        models.put("zombie", new StretchableHumanoidModel(context.bakeLayer(ModelLayers.ZOMBIE), true));
        models.put("skeleton", new StretchableHumanoidModel(context.bakeLayer(ModelLayers.SKELETON)));
        // Wither skeletons are 1.2x scaled; baking their own layer keeps the visual matched to the
        // taller wither-skeleton hitbox instead of a normal-size skeleton under a floating tag.
        models.put("skeleton/wither_skeleton", new StretchableHumanoidModel(context.bakeLayer(ModelLayers.WITHER_SKELETON)));
        models.put("enderman", new StretchableHumanoidModel(context.bakeLayer(ModelLayers.ENDERMAN)));
        models.put("piglin", new StretchableHumanoidModel(context.bakeLayer(ModelLayers.PIGLIN)));

        // Pillagers dereference the arm pose in setupAnim, so every illager state gets one up
        // front. NEUTRAL is the weaponless idle — CROSSED made unarmed pillagers look broken.
        Consumer<NpcIllagerRenderState> illagerInit = state -> {
            state.armPose = AbstractIllager.IllagerArmPose.NEUTRAL;
            state.mainArm = HumanoidArm.RIGHT;
        };
        BiConsumer<NpcRenderState, NpcIllagerRenderState> illagerPose = (npc, illager) -> {
            illager.mainHand = npc.getMainHandItemState();
            illager.mainArm = npc.mainArm;
            illager.isAggressive = npc.aggressive;
            illager.ticksUsingItem = npc.ticksUsingItem;
            illager.maxCrossbowChargeDuration = (int) npc.maxCrossbowChargeDuration;
            illager.armPose = illagerArmPose(npc);
        };

        add(models, context, "villager", ModelLayers.VILLAGER, VillagerModel::new, VillagerRenderState::new, null);
        add(models, context, "witch", ModelLayers.WITCH, WitchModel::new, WitchRenderState::new, null);
        add(models, context, "illager/pillager", ModelLayers.PILLAGER, IllagerModel::new, NpcIllagerRenderState::new,
                illagerInit, illagerPose);
        add(models, context, "illager/vindicator", ModelLayers.VINDICATOR, IllagerModel::new, NpcIllagerRenderState::new,
                illagerInit, illagerPose);
        add(models, context, "illager/evoker", ModelLayers.EVOKER, IllagerModel::new, NpcIllagerRenderState::new,
                illagerInit, illagerPose);
        add(models, context, "illager/illusioner", ModelLayers.ILLUSIONER, IllagerModel::new, NpcIllagerRenderState::new,
                illagerInit, illagerPose);
        add(models, context, "iron_golem", ModelLayers.IRON_GOLEM, IronGolemModel::new, IronGolemRenderState::new, null,
                (npc, golem) -> golem.attackTicksRemaining = npc.attackTicksRemaining);
        add(models, context, "snow_golem", ModelLayers.SNOW_GOLEM, SnowGolemModel::new, LivingEntityRenderState::new, null);
        add(models, context, "cow", ModelLayers.COW, CowModel::new, LivingEntityRenderState::new, null);
        add(models, context, "pig", ModelLayers.PIG, PigModel::new, LivingEntityRenderState::new, null);
        add(models, context, "sheep", ModelLayers.SHEEP, SheepModel::new, SheepRenderState::new, null);
        add(models, context, "chicken", ModelLayers.CHICKEN, AdultChickenModel::new, ChickenRenderState::new, null);
        add(models, context, "wolf", ModelLayers.WOLF, AdultWolfModel::new, WolfRenderState::new, null);
        add(models, context, "cat", ModelLayers.CAT, AdultCatModel::new, CatRenderState::new, null);
        add(models, context, "fox", ModelLayers.FOX, AdultFoxModel::new, FoxRenderState::new, null);
        add(models, context, "rabbit", ModelLayers.RABBIT, AdultRabbitModel::new, RabbitRenderState::new, null);
        add(models, context, "goat", ModelLayers.GOAT, GoatModel::new, GoatRenderState::new, null);
        add(models, context, "panda", ModelLayers.PANDA, PandaModel::new, PandaRenderState::new, null);
        add(models, context, "polar_bear", ModelLayers.POLAR_BEAR, PolarBearModel::new, PolarBearRenderState::new, null);
        add(models, context, "armadillo", ModelLayers.ARMADILLO, AdultArmadilloModel::new, ArmadilloRenderState::new, null);
        add(models, context, "axolotl", ModelLayers.AXOLOTL, AdultAxolotlModel::new, AxolotlRenderState::new, null);
        add(models, context, "frog", ModelLayers.FROG, FrogModel::new, FrogRenderState::new, null);
        add(models, context, "allay", ModelLayers.ALLAY, AllayModel::new, AllayRenderState::new, null);
        add(models, context, "vex", ModelLayers.VEX, VexModel::new, VexRenderState::new, null);
        add(models, context, "bat", ModelLayers.BAT, BatModel::new, BatRenderState::new, null);
        add(models, context, "creeper", ModelLayers.CREEPER, CreeperModel::new, CreeperRenderState::new, null);
        add(models, context, "spider/spider", ModelLayers.SPIDER, SpiderModel::new, LivingEntityRenderState::new, null);
        add(models, context, "spider/cave_spider", ModelLayers.CAVE_SPIDER, SpiderModel::new, LivingEntityRenderState::new, null);
        add(models, context, "slime/slime", ModelLayers.SLIME, SlimeModel::new, SlimeRenderState::new, null);
        add(models, context, "slime/magma_cube", ModelLayers.MAGMA_CUBE, MagmaCubeModel::new, SlimeRenderState::new, null);
        add(models, context, "blaze", ModelLayers.BLAZE, BlazeModel::new, LivingEntityRenderState::new, null);
        add(models, context, "warden", ModelLayers.WARDEN, WardenModel::new, WardenRenderState::new, null);
        return models;
    }

    // The illager model takes its pose from its own arm-pose enum instead of the humanoid arm poses the
    // NPC renderer sets, so the weapon poses are translated; any other armed fight pose swings the weapon.
    private static AbstractIllager.IllagerArmPose illagerArmPose(NpcRenderState npc) {
        HumanoidModel.ArmPose pose = npc.getArmPose(npc.mainArm);
        if (pose == HumanoidModel.ArmPose.CROSSBOW_CHARGE) {
            return AbstractIllager.IllagerArmPose.CROSSBOW_CHARGE;
        }
        if (pose == HumanoidModel.ArmPose.CROSSBOW_HOLD) {
            return AbstractIllager.IllagerArmPose.CROSSBOW_HOLD;
        }
        if (pose == HumanoidModel.ArmPose.BOW_AND_ARROW) {
            return AbstractIllager.IllagerArmPose.BOW_AND_ARROW;
        }
        return npc.aggressive ? AbstractIllager.IllagerArmPose.ATTACKING : AbstractIllager.IllagerArmPose.NEUTRAL;
    }

    // The illager model picks its swing-or-reach attack from whether the main hand is empty; the NPC's
    // own item state is the one that renders, so the adapter's state answers from it.
    private static final class NpcIllagerRenderState extends IllagerRenderState {
        private ItemStackRenderState mainHand = new ItemStackRenderState();

        @Override
        public ItemStackRenderState getMainHandItemState() {
            return mainHand;
        }
    }

    // The visual lift must use the same per-type geometry as the server hitbox math, so both stay
    // glued to the visible skin (enderman legs and arms are much longer than player ones).

    private static <S extends EntityRenderState> void add(Map<String, EntityModel<NpcRenderState>> models,
                                                          EntityRendererProvider.Context context, String key,
                                                          ModelLayerLocation layer,
                                                          Function<ModelPart, EntityModel<? super S>> modelFactory,
                                                          Supplier<S> stateFactory, Consumer<S> stateInitializer) {
        add(models, context, key, layer, modelFactory, stateFactory, stateInitializer, null);
    }

    private static <S extends EntityRenderState> void add(Map<String, EntityModel<NpcRenderState>> models,
                                                          EntityRendererProvider.Context context, String key,
                                                          ModelLayerLocation layer,
                                                          Function<ModelPart, EntityModel<? super S>> modelFactory,
                                                          Supplier<S> stateFactory, Consumer<S> stateInitializer,
                                                          BiConsumer<NpcRenderState, S> animator) {
        models.put(key, new AdaptedEntityModel<>(context.bakeLayer(layer), modelFactory, stateFactory,
                stateInitializer, animator));
    }

    private static Identifier entityTexture(String path) {
        return Identifier.withDefaultNamespace("textures/entity/" + path + ".png");
    }
}
