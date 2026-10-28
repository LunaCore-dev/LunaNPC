package net.lunacore.luna_npc.entity;

import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.lunacore.luna_npc.LunaNpc;

public final class LunaNpcEntities {

    private static final ResourceKey<EntityType<?>> NPC_KEY = ResourceKey.create(Registries.ENTITY_TYPE,
            Identifier.fromNamespaceAndPath(LunaNpc.MOD_ID, "npc"));
    private static final ResourceKey<EntityType<?>> PROJECTILE_KEY = ResourceKey.create(Registries.ENTITY_TYPE,
            Identifier.fromNamespaceAndPath(LunaNpc.MOD_ID, "projectile"));

    public static final EntityType<NpcEntity> NPC = Registry.register(BuiltInRegistries.ENTITY_TYPE, NPC_KEY,
            EntityType.Builder.of(NpcEntity::new, MobCategory.MISC).sized(0.6F, 1.8F).build(NPC_KEY));
    public static final EntityType<NpcProjectileEntity> PROJECTILE = Registry.register(
            BuiltInRegistries.ENTITY_TYPE, PROJECTILE_KEY,
            EntityType.Builder.<NpcProjectileEntity>of(NpcProjectileEntity::new, MobCategory.MISC)
                    .sized(0.25F, 0.25F).clientTrackingRange(8).updateInterval(2).build(PROJECTILE_KEY));

    private static final ResourceKey<EntityType<?>> ARROW_KEY = ResourceKey.create(Registries.ENTITY_TYPE,
            Identifier.fromNamespaceAndPath(LunaNpc.MOD_ID, "arrow"));
    public static final EntityType<NpcArrowEntity> ARROW = Registry.register(
            BuiltInRegistries.ENTITY_TYPE, ARROW_KEY,
            EntityType.Builder.<NpcArrowEntity>of(NpcArrowEntity::new, MobCategory.MISC)
                    .sized(0.5F, 0.5F).clientTrackingRange(8).updateInterval(2).build(ARROW_KEY));

    private LunaNpcEntities() {
    }

    public static void initialize() {
        FabricDefaultAttributeRegistry.register(NPC, LivingEntity.createLivingAttributes());
    }
}
